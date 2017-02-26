(ns rekry.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]
            [ring.core.protocols :refer :all]
            [ring.util.response :refer [resource-response response]]
            [ring.middleware.reload :refer [wrap-reload]]
            [hiccup.page :refer [include-js include-css html5]]
            [clojure.core.async :as a :refer [>! <! >!! <!! go close!]]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [config.core :refer [env]]))


(def auth-str (str "?" "client_id=" (env :client-id) "&" "client_secret=" (env :client-secret)))

(extend-type clojure.core.async.impl.channels.ManyToManyChannel
  StreamableResponseBody
  (write-body-to-stream [channel response output-stream]
    (a/go (with-open [writer (io/writer output-stream)]
                (a/loop []
                  (when-let [msg (a/<! channel)]
                    (doto writer (.write msg) (.flush))
                    (recur)))))))

(defn parse-link-string [link-str]
  (subs link-str 1 (dec (count link-str))))

(defn parse-relation [rel-str]
  (keyword (subs rel-str 6 (dec (count rel-str)))))

(defn parse-link-headers [link-headers-str]
  (reduce #(assoc %1 (parse-relation (get %2 1)) (parse-link-string (get %2 0)))
          {}
          (mapv #(clojure.string/split % #";")
                (clojure.string/split link-headers-str #","))))

(defn has-link-headers? [res]
  (not (nil? (:link (:headers res)))))

(defn last-page-number [res]
  (let [last (:last (parse-link-headers (:link (:headers res))))]
    (Integer/parseInt (apply str
                             (filter #(Character/isDigit %)
                                     (subs last (- (count last) 2) (count last)))))))

(defn create-page-urls [url page-count]
  (doall
   (for [page-number (range 1 (inc page-count))]
     (str url "&per_page=100" "&page=" page-number))))

(defn all-pages [base-url]
  (let [first-page (str base-url "&per_page=100" "&page=1")
        res (http/get first-page)]
    (if (has-link-headers? @res)
      (let [page-count (last-page-number @res)
            urls (create-page-urls base-url page-count)]
        (doall (map http/get urls)))
      [res])))

(defn contr-url [repo]
  (str (:contributors_url repo) auth-str))

(defn commit-url [repo]
  (str (clojure.string/replace (:commits_url repo)  #"\{/sha\}" "") auth-str))

(defn count-objects [promises]
  (reduce #(+ %1 (count (json/read-json (:body @%2))))
          0
          promises))

(defn repo-data-to-chan [repo ch]
  (let [name (get repo :name)
        language (get repo :language)
        stargazers-count (get repo :stargazers_count)
        cont-promises (all-pages (contr-url repo))
        commit-promises (all-pages (commit-url repo))
        contributors (count-objects cont-promises)
        commits (count-objects commit-promises)]
    (go (a/>! ch {:name name
                  :language language
                  :stargazers-count stargazers-count
                  :contributors contributors
                  :commits commits}))))

(defn repos-by-org [org]
  (http/get (str "https://api.github.com/orgs/" org "/repos" auth-str "&page=1" "&per_page=100")))

(defn repo-downloads [entry clojars-res]
  (:downloads
   (first (filter #(= (:name entry) (:jar_name %))
                  clojars-res))))

(defn add-downloads [body]
  (let [res (json/read-json (:body @(http/get "https://clojars.org/api/groups/metosin")))]
    (mapv
     #(assoc % :downloads (repo-downloads % res))
     body)))

(defn async-repos-handler []
  (fn [req res raise]
    (let [ch1 (a/chan)
          ch2 (a/chan)
          body (atom [])]
      (res {:status 200, :headers {}, :body ch2})
      (let [repos (repos-by-org (:query-string req))]
        (doseq [repo (json/read-json (:body @repos))]
          (future (repo-data-to-chan repo ch1)))
        (doseq [_ (json/read-json (:body @repos))]
          (swap! body conj (a/<!! ch1)))
        (swap! body add-downloads)
        (a/>!! ch2 (json/write-str @body))
        (a/close! ch2)))))

(defroutes routes
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  (resources "/")
  (GET "/repos" [] (async-repos-handler)))

(def handler routes)
