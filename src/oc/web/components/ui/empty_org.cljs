(ns oc.web.components.ui.empty-org
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.dispatcher :as dis]))

(rum/defcs empty-org < rum/reactive
                       (drv/drv :org-data)
  [s]
  (let [org-data (drv/react s :org-data)]
    [:div.empty-org.group
      (if (:read-only org-data)
        [:div.empty-org-headline
          (str "There aren't sections in " (:name org-data) " yet. ")]
        [:div.empty-org-headline
          (str "You don’t have any sections yet. ")
          [:button.mlb-reset
            {:on-click #(dis/dispatch! [:input [:show-section-add] true])}
            "Add one?"]])
      [:div.empty-org-image]]))