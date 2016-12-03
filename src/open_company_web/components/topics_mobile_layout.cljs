(ns open-company-web.components.topics-mobile-layout
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :refer-macros (sel1 sel)]
            [open-company-web.router :as router]
            [open-company-web.dispatcher :as dis]
            [open-company-web.lib.utils :as utils]
            [open-company-web.lib.responsive :as responsive]
            [open-company-web.components.topic :refer (topic)]))

(def mobile-topic-margins 3)

(defcomponent topics-mobile-layout [{:keys [columns-num
                                            content-loaded
                                            total-width
                                            card-width
                                            topics
                                            company-data
                                            topics-data
                                            foce-key
                                            foce-data
                                            is-dashboard
                                            is-stakeholder-update] :as data} owner options]
  (render [_]
    (dom/div {:class "topics-mobile-layout"}
      (for [idx (range (count topics))
            :let [section-kw (get topics idx)
                  section-name (name section-kw)
                  sd (->> section-name keyword (get company-data))
                  topic-data {:loading (:loading company-data)
                              :section section-name
                              :is-stakeholder-update is-stakeholder-update
                              :section-data sd
                              :card-width card-width
                              :foce-data-editing? (:foce-data-editing? data)
                              :read-only-company (:read-only company-data)
                              :currency (:currency company-data)
                              :foce-key foce-key
                              :foce-data foce-data
                              :is-dashboard is-dashboard
                              :topic-flex-num idx}
                  topic-opts {:opts {:section-name section-name
                                     :topic-click (partial (:topic-click options) section-name)}}]]
        (dom/div {:class "topics-mobile-item" :style {:width (str card-width "px")}}
          (om/build topic topic-data topic-opts))))))