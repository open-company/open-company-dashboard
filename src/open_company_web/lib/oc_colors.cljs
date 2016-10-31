(ns open-company-web.lib.oc-colors)

(def oc-colors {
  :yellow "#f9d748" ; rgb(249,215,72)
  :green "#26C485" ; rgb(53,188,46)
  :red "#E04B53"
  :blue "#007A9D" ;"#004E64" ;"#109DB7"
  :gray "#D8D8D8" ;"#ADADAD"
  :black "#000000"
  :white "#FCFCFC"
  ;; Charts
  :oc-chart-blue "#4EE4D6"
  :oc-chart-green "#4de148"
  :oc-chart-red "#D72A46"
  ;; Greens
  :oc-green-dark "#35bc2e"
  :oc-green-regular "#26C485" ; :green
  :oc-green-light "#4DE249" ; rgb(77,226,73)
  ;; Blues
  :oc-blue-dark "#1E81C4"
  :oc-blue-regular "#004E64"
  :oc-blue-light "#007A9D"
  ;; Grays
  :oc-gray-0 "#FEFEFE"
  :oc-gray-1 "#F1F1F1"
  :oc-gray-6 "#E0E0E0"
  :oc-gray-2 "#D8D8D8"
  :oc-gray-3 "#8A8A8A"
  :oc-gray-4 "#5B5B5B"
  :oc-gray-5 "#4E5A6B" ;rgb(78,90,107)
  :oc-gray-5-half "rgba(78,90,107,0.5)"
  :oc-gray-5-3-quarter "rgba(78,90,107,0.75)"
  :oc-gray-7 "#0A0A0A"
  ;; Reds
  :oc-red-dark "#9E001A"
  :oc-red-regular "#E04B53" ; :red
  :oc-red-light "#EC7A8D"})

(defn get-color-by-kw [kw]
  (if (contains? oc-colors kw)
    (kw oc-colors)
    (:black oc-colors)))

(defn fill-color [color-key]
  (str "fill-color: " (get-color-by-kw color-key)))