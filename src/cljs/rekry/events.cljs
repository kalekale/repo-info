(ns rekry.events
    (:require [re-frame.core :as re-frame]
              [rekry.db :as db]
              [day8.re-frame.http-fx]
              [ajax.core :as ajax]))

(re-frame/reg-event-db
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/reg-event-db
 :set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(re-frame/reg-event-db
 :org-name-changed
 (fn [db [_ org-name]]
   (assoc db :org-name org-name)))

(re-frame/reg-event-db
 :success-response
 (fn [db [_ res]]
   (assoc (assoc db :repos res) :show-bouncer false)))

(re-frame/reg-event-db
 :failed-response
 (fn [db [_ res]]
   (print res)
   db))

(re-frame/reg-event-fx
 :submit-clicked
 (fn [{:keys [db]} [_ a]]
   {:http-xhrio {:method :get
                 :uri    (str "/repos?" (:org-name db))
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success  [:success-response]
                 :on-failure     [:failed-response]}
    :db   (assoc db :show-bouncer true)}))
