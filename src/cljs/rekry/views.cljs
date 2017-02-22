(ns rekry.views
    (:require [re-frame.core :as re-frame]))


;; home

(defn repo-component [repo]
  [:ul
   (for [entry repo]
     ^{:key entry}
     [:li [:b (str (name (key entry)) ": ")] (val entry)])])

(defn repos-component []
  (let [repo-list (re-frame/subscribe [:repos])]
    (fn []
      [:div
       (for [repo @repo-list]
         ^{:key repo}
         [repo-component repo])])))

(defn bouncer []
  (let [show-bouncer (re-frame/subscribe [:show-bouncer])]
    (fn []
      (if @show-bouncer
      [:div {:class "spinner center-td"}
       [:div {:class "double-bounce1"}]
       [:div {:class "double-bounce2"}]]))))

(defn home-panel []
    (fn []
      [:div
       [:p "Enter valid organisation name and submit"]
       [:input {:type "text"
                :placeholder "organisation name"
                :on-change #(re-frame/dispatch [:org-name-changed (-> % .-target .-value)])}]
       [:div [:input {:type "submit"
                      :on-click #(re-frame/dispatch [:submit-clicked])
                      :value "Submit"}]]
       [bouncer]
       [repos-component]]))


;; about

(defn about-panel []
  (fn []
    [:div "This is the About Page."
     [:div [:a {:href "#/"} "go to Home Page"]]]))


;; main

(defn- panels [panel-name]
  (case panel-name
    :home-panel [home-panel]
    :about-panel [about-panel]
    [:div]))

(defn show-panel [panel-name]
  [panels panel-name])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (fn []
      [show-panel @active-panel])))
