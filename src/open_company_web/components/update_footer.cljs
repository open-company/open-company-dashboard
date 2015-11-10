(ns open-company-web.components.update-footer
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [open-company-web.lib.utils :as utils]))

(defn get-footer-id [data]
  (let [section-name (name (:section data))
        notes (if (:notes data)
                "-notes"
                "")]
    (str "update-footer-" section-name notes)))

(defcomponent update-footer [data owner]
  (init-state [_]
    {:hover false})
  (did-mount [_]
    (when (.-$ js/window)
      (let [component-id (get-footer-id data)]
        (.hover (.$ js/window (str "#" component-id ", #" component-id "-hover"))
                #(om/update-state! owner :hover (fn [_]true))
                #(om/update-state! owner :hover (fn [_]false))))))
  (render [_]
    (dom/div {:class "update-footer"}
      (dom/div {:class "timeago"
                :id (get-footer-id data)}
               (utils/time-since (:updated-at data)))
      (dom/div {:class (utils/class-set {:update-footer-hover true
                                         :show (om/get-state owner :hover)})
                :id (str (get-footer-id data) "-hover")}
        (dom/img {:src (:image (:author data)) :title (:name (:author data)) :class "author-image"})
          (dom/div {:class "author"} (:name (:author data)))))))