(ns rekry.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :name
 (fn [db]
   (:name db)))

(re-frame/reg-sub
 :active-panel
 (fn [db _]
   (:active-panel db)))

(re-frame/reg-sub
 :repos
 (fn [db _]
   (:repos db)))

(re-frame/reg-sub
 :show-bouncer
 (fn [db _]
   (:show-bouncer db)))
