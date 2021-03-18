(ns oc.web.stores.cmail
  (:require [oc.web.dispatcher :as dispatcher]
            [oc.web.lib.utils :as utils]
            [oc.web.actions.cmail :as cmail-actions]))

(defmethod dispatcher/action :cmail-expand
  [db [_]]
  (update-in db dispatcher/cmail-state-key merge {:collapsed false
                                                  :labels-floating-view false
                                                  :labels-inline-view false}))

(defmethod dispatcher/action :cmail-collapse
  [db [_]]
  (update-in db dispatcher/cmail-state-key merge {:collapsed true
                                                  :fullscreen false
                                                  :labels-floating-view false
                                                  :labels-inline-view false}))
  
(defmethod dispatcher/action :cmail-reset
  [db [_]]
  (-> db
      (assoc-in dispatcher/cmail-data-key (cmail-actions/get-board-for-edit))
      (update-in dispatcher/cmail-state-key merge {:collapsed true
                                                   :key (utils/activity-uuid)
                                                   :labels-floating-view false
                                                   :labels-inline-view false})))

(defmethod dispatcher/action :cmail-state/update
  [db [_ cmail-state]]
  (assoc-in db dispatcher/cmail-state-key cmail-state))

(defmethod dispatcher/action :cmail-data/update
  [db [_ cmail-data]]
  (update-in db dispatcher/cmail-data-key merge cmail-data))

(defmethod dispatcher/action :cmail-data/replace
  [db [_ cmail-data]]
  (assoc-in db dispatcher/cmail-data-key cmail-data))

(defmethod dispatcher/action :cmail-data/remove-has-changes
  [db [_]]
  (update-in db dispatcher/cmail-data-key dissoc :has-changes))

(defmethod dispatcher/action :cmail-toggle-label
  [db [_ toggle-label]]
  (update-in db (concat dispatcher/cmail-data-key [:labels])
             (fn [labels]
               (let [cmail-labels-set (set (map :slug labels))]
                 (if (cmail-labels-set (:slug toggle-label))
                   (filterv #(not= (:slug %) (:slug toggle-label)) labels)
                   (vec (concat labels [(select-keys toggle-label [:uuid :name :color :slug])])))))))

(defn- labels-value-update [optional-value current-value]
  (if (boolean? optional-value) optional-value (not current-value)))

(defmethod dispatcher/action :toggle-cmail-floating-labels-view
  [db [_ optional-value]]
  (update-in db (concat dispatcher/cmail-state-key [:labels-floating-view]) (partial labels-value-update optional-value)))

(defmethod dispatcher/action :toggle-cmail-inline-labels-view
  [db [_ optional-value]]
  (update-in db (concat dispatcher/cmail-state-key [:labels-inline-view]) (partial labels-value-update optional-value)))

(defmethod dispatcher/action :toggle-cmail-labels-views
  [db [_ optional-value]]
  (-> db
      (update-in (concat dispatcher/cmail-state-key [:labels-floating-view]) (partial labels-value-update optional-value))
      (update-in (concat dispatcher/cmail-state-key [:labels-inline-view]) (partial labels-value-update optional-value))))

(defmethod dispatcher/action :cmail-label-remove-last-label
  [db [_]]
  (update-in db (conj dispatcher/cmail-data-key :labels) (comp vec butlast)))