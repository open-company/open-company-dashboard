(ns open-company-web.components.section-selector
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [open-company-web.router :as router]
            [open-company-web.components.finances-pieces.finances :refer (finances finances-edit)]
            [open-company-web.components.simple-section :refer (simple-section)]))

(defcomponent section-selector [data owner]
  (render [_]
    (let [section (:section data)
          read-only (:read-only data)
          tab (:tab @router/path)
          company-data (:data data)]
      (cond
        ; finances edit
        (and (= section :finances) (= tab "edit"))
        (om/build finances-edit {:company-data company-data
                                 :section :finances})
        ; finances
        (= section :finances)
        (om/build finances {:read-only read-only
                            :section :finances
                            :company-data company-data})
        ; else it is a simple section
        (contains? company-data section)
        (om/build simple-section {:read-only read-only
                                  :section section
                                  :company-data company-data})
        ; section not found
        :else
        (dom/h4 {} (str "Section " (name section) " not found"))))))