(ns oc.web.rum-utils
  (:require [org.martinklepsch.derivatives :as drv]
            [rum.core :as rum]
            [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]))

(let [get-k "org.martinklepsch.derivatives/get"
      release-k "org.martinklepsch.derivatives/release"]
  (defn om-derivatives [mngr]
    (->> {:childContextTypes {get-k js/React.PropTypes.func
                              release-k js/React.PropTypes.func}
          :getChildContext (fn [] (let [{:keys [release! get!]} mngr]
                                    (clj->js {get-k get!
                                              release-k release!})))}
         (merge om/pure-methods)
         clj->js
         om/specify-state-methods!)))

(defn drv-root [{:keys [state drv-spec target component]}]
  ; unmount rum component if mounted to the same node
  (rum/unmount target)
  ; mount component
  (om/root component
           state
           {:target target
            :descriptor (om-derivatives (drv/derivatives-pool drv-spec))}))