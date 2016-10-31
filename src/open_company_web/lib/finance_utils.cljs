(ns open-company-web.lib.finance-utils
  (:require [open-company-web.lib.utils :as utils]
            [open-company-web.router :as router]
            [open-company-web.dispatcher :as dispatcher]
            [open-company-web.lib.oc-colors :as occ]
            [goog.string :as gstring]
            [cljs-time.core :as t]
            [cljs-time.format :as f]))

(def columns-num 6)

(defn chart-data-at-index [data keyw column-name prefix suffix idx]
  (let [data (to-array data)
        obj (get (vec (reverse data)) idx)
        has-value (and (contains? obj keyw) (not (nil? (keyw obj))))
        value (keyw obj)
        label (if has-value
                (if (and (= keyw :runway) (zero? value))
                  "-"
                  (str (utils/get-period-string (:period obj)) " " column-name ": " prefix (utils/thousands-separator (keyw obj)) suffix))
                "-")
        period (utils/get-period-string (:period obj) "monthly" [:short])]
    [period
     value
     label]))

(defn- get-past-period [period diff]
  (let [period-date (utils/date-from-period period)
        past-date (t/minus period-date (t/months diff))]
    (utils/period-from-date past-date)))

(defn placeholder-data [period custom-map]
  (merge custom-map
   {:period period
    :cash nil
    :costs nil
    :revenue nil
    :burn-rate nil
    :runway nil}))

(defn chart-placeholder-data [initial-data]
  (when (seq initial-data)
    (let [first-period (:period (last initial-data))
          last-period (:period (first initial-data))
          months-diff (utils/periods-diff first-period last-period)]
      (vec
        (for [idx (range 0 (inc months-diff))]
          (let [prev-period (get-past-period last-period idx)
                period-exists (utils/period-exists prev-period initial-data)]
            (if period-exists
              (some #(when (= (:period %) prev-period) %) initial-data)
              (placeholder-data prev-period {:new true}))))))))

(defn edit-placeholder-data [initial-data]
  (let [current-period (utils/current-finance-period)
        last-period (if (last initial-data)
                      (:period (last initial-data))
                      (get-past-period current-period 12))
        diff (utils/periods-diff last-period current-period)
        data-count (max 13 (inc diff))]
    (let [fixed-data (for [idx (range 1 data-count)]
                       (let [prev-period (get-past-period current-period idx)
                             period-exists (utils/period-exists prev-period initial-data)]
                         (if period-exists
                           (some #(when (= (:period %) prev-period) %) initial-data)
                           (placeholder-data prev-period {:new true}))))]
      (vec fixed-data))))

(defn- get-chart-data
  "Vector of max *(count fixed-data) elements of [:Label value]"
  [data prefix keyw column-name & [style fill-color pattern tooltip-suffix]]
  (let [fixed-data (chart-placeholder-data data)
        chart-data (partial chart-data-at-index fixed-data keyw column-name prefix tooltip-suffix)
        placeholder-vect (vec (range (count fixed-data)))
        columns [["string" column-name]
                 ["number" (utils/camel-case-str (name keyw))]
                 #js {"type" "string" "role" "tooltip"}]
        columns (if style (conj columns style) columns)
        values (vec (map chart-data placeholder-vect))
        values-with-color (if fill-color
                            (map #(assoc % 3 fill-color) values)
                            values)]
    { :prefix prefix
      :columns columns
      :values values-with-color
      :max-show columns-num
      :pattern (if pattern pattern "###,###.##")}))

(defn get-as-of-string [period]
  (when period
    (let [period-date (utils/date-from-period period)
          month-name (utils/month-string (t/month period-date))]
      (str month-name ", " (t/year period-date)))))

(defn map-placeholder-data [data]
  (let [fixed-data (edit-placeholder-data data)]
    (apply merge (map #(hash-map (:period %) %) fixed-data))))

(defn fix-runway [runway]
  (if (neg? runway)
    (utils/abs runway)
    0))

(defn remove-trailing-zero
  "Remove the last zero(s) in a numeric string only after the dot.
   Remote the dot too if it is the last char after removing the zeros"
  [string]
  (cond

    (and (not= (.indexOf string ".") -1) (= (last string) "0"))
    (remove-trailing-zero (subs string 0 (dec (count string))))

    (= (last string) ".")
    (subs string 0 (dec (count string)))

    :else
    string))

(defn day-label [& [flags]]
  "day")

(defn week-label [flags]
  (if (utils/in? flags :short)
    "wk"
    "week"))

(defn month-label [flags]
 (if (utils/in? flags :short)
    "mo"
    "month"))

(defn year-label [flags]
 (if (utils/in? flags :short)
    "yr"
    "year"))

(defn pluralize [n]
  (if (> n 1)
    "s"
    ""))

(defn get-rounded-runway [runway-days & [flags]]
  (let [spacer (if (utils/in? flags :short) "" " ")
        abs-runway-days (utils/abs runway-days)]
    (cond
      ; days
      (< abs-runway-days 7)
      (let [days (int abs-runway-days)]
        (str days spacer (day-label flags) (pluralize days)))
      ; weeks
      (< abs-runway-days (* 30 3))
      (let [weeks (int (/ abs-runway-days 7))]
        (str weeks spacer (week-label flags) (pluralize weeks)))
      ; months
      (< abs-runway-days (* 30 12))
      (if (utils/in? flags :round)
        (let [months (int (/ abs-runway-days 30))
              fixed-months (if (utils/in? flags :remove-trailing-zero)
                             (remove-trailing-zero (str months))
                             (str months))]
          (str fixed-months spacer (month-label flags) (pluralize months)))
        (let [months (quot abs-runway-days 30)]
          (str months spacer (month-label flags) (pluralize months))))
      ; years
      :else
      (if (utils/in? flags :round)
        (let [years (int (/ abs-runway-days (* 30 12)))
              fixed-years (if (utils/in? flags :remove-trailing-zero)
                            (remove-trailing-zero (str years))
                            (str years))]
          (str fixed-years spacer (year-label flags) (pluralize years)))
        (let [years (quot abs-runway-days (* 30 12))]
          (str years spacer (year-label flags) (pluralize years)))))))

(defn finances-data-map [finances-data]
  (apply merge (map #(hash-map (:period %) %) finances-data)))

(defn fill-gap-months [finances-data]
  (let [data-map (finances-data-map finances-data)
        sort-pred (utils/sort-by-key-pred :period)
        sorted-data (vec (sort sort-pred finances-data))
        first-data (first sorted-data)
        last-data (last sorted-data)
        period (:period last-data)]
    (loop [current-period (:period last-data)
           filled-data data-map
           idx 1]
      (let [data (get data-map period)
            next-data (if (contains? filled-data current-period)
                        filled-data
                        (assoc filled-data current-period (placeholder-data current-period {:placeholder-data true})))]
        (if (not= current-period (:period first-data))
          (recur (get-past-period period idx)
                 next-data
                 (inc idx))
          next-data)))))

(defn fake-chart-placeholder-data []
  (let [current-period   (utils/current-finance-period)]
    (loop [idx 0
           period current-period
           d   []]
      (if (< idx (- columns-num 2))
        (let [row {:period period
                   :cash (- (* 1000 6) (* (inc idx) 1000))
                   :costs (- (* 100 6) (* (inc idx) 100))
                   :revenue (* (inc idx) 200)}]
          (recur (inc idx)
                 (get-past-period period 1)
                 (assoc d idx row)))
        d))))

(defn color-for-metric [k]
  (occ/get-color-by-kw (cond (= k :revenue) :oc-green-dark
                             (= k :costs) :red
                             :else :oc-gray-7)))

(defn finances-key-colors []
  {:revenue (color-for-metric :revenue)
   :costs  (color-for-metric :costs)
   :cash  (color-for-metric :cash)})