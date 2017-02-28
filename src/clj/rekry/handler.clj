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
            [config.core :refer [env]]
            [manifold.deferred :as d]))


(def auth-str (str "?" "client_id=" (env :client-id) "&" "client_secret=" (env :client-secret)))

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

(defn parse-body-from-req [req]
  (json/read-json (:body req)))

(defn repo-downloads [entry clojars-res]
  (:downloads
   (first (filter #(= (:name entry) (:jar_name %))
                  clojars-res))))

(defn link-downloads-to-repos [repos clojars-body]
  (mapv #(assoc % :downloads (repo-downloads % clojars-body)) repos))

(defn add-downloads [org repos]
  (d/chain (http/get (str "https://clojars.org/api/groups/" org))
           parse-body-from-req
           (partial link-downloads-to-repos repos)))


(defn rest-pages [first-page-res base-url]
  (if (has-link-headers? first-page-res)
    (let [page-count (last-page-number first-page-res)
          urls (create-page-urls base-url page-count)]
      (apply d/zip (map #(http/get %) urls)))
    (seq [first-page-res])))

(defn count-objects [promises]
  (reduce #(+ %1 (count (json/read-json (:body %2))))
          0
          promises))

(defn all-pages [base-url]
  (let [first-page (str base-url "&per_page=100" "&page=1")]
    (d/chain (http/get first-page)
             #(rest-pages % base-url))))

(defn contr-url [repo]
  (str (:contributors_url repo) auth-str))

(defn commit-url [repo]
  (str (clojure.string/replace (:commits_url repo)  #"\{/sha\}" "") auth-str))

(defn repo-data [repo]
  (d/let-flow [contributors (all-pages (contr-url repo))
               commits      (all-pages (commit-url repo))
               name         (:name repo)
               language     (:language repo)
               stargazers   (:stargazers_count repo)]
    {:name             name
     :language         language
     :stargazers-count stargazers
     :commits          (count-objects commits)
     :contributors     (count-objects contributors)}))

(defn data-to-repos [repos]
  (apply d/zip (map #(repo-data %) repos)))

(defn repos-by-org [org]
  (http/get (str "https://api.github.com/orgs/" org "/repos" auth-str "&page=1" "&per_page=100")))

(defn d-handler []
  (fn [req res raise]
    (res
     (d/chain
      (repos-by-org (:query-string req))
      parse-body-from-req
      data-to-repos
      (partial add-downloads (:query-string req))
      #(hash-map :status 200 :body (json/write-str %))))))

(defroutes routes
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  (resources "/")
  (GET "/repos" [] (d-handler))
  (GET "/asd" [] "moin"))


(def handler #'routes)
