(ns oc.web.components.ui.loading
  (:require [om.core :as om]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [rum.core :as rum]
            [oc.web.lib.utils :as utils]))

(defcomponent loading [data owner]
  (render [_]
    (dom/div {:class (utils/class-set {:oc-loading true
                                       :active (:loading data)})}
      (dom/i {:class "fa fa-circle-o-notch fa-spin"}))))

(rum/defc rloading < rum/static
  [data]
  [:div.oc-loading
    {:class (if (:loading data) "active" "")}
    [:i.fa.fa-circle-o-notch.fa-spin]])