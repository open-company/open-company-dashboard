(ns oc.web.lib.growth-utils
  (:require [oc.web.lib.utils :as utils]
            [oc.web.lib.oc-colors :as occ]
            [cljs-time.core :as t]
            [cljs-time.format :as f]
            [cuerdas.core :as s]))

(def new-metric-slug-placeholder "new-metric-slug-placeholder")

(def columns-num 6)

(defn get-noise []
  (int (* (rand 4) 1000)))

(defn create-slug [presets metric-name & [add-noise]]
  (let [metrics (:metrics presets)
        metric (filter #(= (s/lower (:name %)) (s/lower metric-name)) metrics)
        slug (if (empty? metric)
               (s/slug metric-name)
               (:slug (first metric)))]
    (if add-noise
      (str slug "-" (get-noise))
      slug)))

(defn get-slug [slugs presets metric-name]
  (let [slug-atom (atom "")]
    (swap! slug-atom #(create-slug presets metric-name))
    (while (utils/in? slugs @slug-atom)
      (swap! slug-atom #(create-slug presets metric-name true)))
    @slug-atom))

(defn get-minus [diff interval]
  (case interval
    "quarterly" (t/months (* diff 3))
    "weekly" (t/weeks diff)
    ;; default to monthly
    (t/months diff)))

(defn get-past-period [period diff interval]
  (let [period-date (utils/date-from-period period interval)
        past-date (t/minus period-date (get-minus diff interval))
        formatter (utils/get-formatter interval)]
    (f/unparse formatter past-date)))

(def metric-placeholder
  {:slug nil
   :name nil
   :interval "monthly"
   :goal nil})

(defn placeholder-data [period slug custom-map]
  (merge custom-map
   {:period period
     :slug slug
     :value nil}))

(defn chart-placeholder-data [initial-data slug interval]
  (let [first-period (:period (last initial-data))
        last-period (:period (first initial-data))
        period-diff (utils/periods-diff first-period last-period interval)]
    (vec
      (for [idx (range 0 (inc period-diff))]
        (let [prev-period (get-past-period last-period idx interval)
              period-exists (utils/period-exists prev-period initial-data)]
          (if period-exists
            (some #(when (= (:period %) prev-period) %) initial-data)
            (placeholder-data prev-period slug {:new true})))))))

(defn edit-placeholder-data [initial-data slug interval]
  (let [current-period (utils/current-growth-period interval)
        first-period (if (last initial-data)
                      (:period (last initial-data))
                      (get-past-period current-period 12 interval))
        diff (utils/periods-diff first-period current-period interval)
        data-count (max columns-num (inc diff))]
    (let [fixed-data (for [idx (range data-count)]
                       (let [prev-period (get-past-period current-period idx interval)
                             period-exists (utils/period-exists prev-period initial-data)]
                         (if period-exists
                           (some #(when (= (:period %) prev-period) %) initial-data)
                           (placeholder-data prev-period slug {:new true}))))]
      (vec fixed-data))))

(defn get-graph-tooltip [label prefix value suffix]
  (str label
       ": "
       (or prefix "")
       (if value (utils/thousands-separator value) "")
       (if suffix (str " " suffix) "")))

(defn chart-data-at-index [data column-name prefix suffix interval idx]
  (let [data (to-array data)
        obj (get (vec (reverse data)) idx)
        value (or (:value obj) 0)
        label (get-graph-tooltip column-name prefix value suffix)
        period (utils/get-period-string (:period obj) interval [:short])
        values [period
                value
                (occ/fill-color :oc-blue-light)
                label]]
    values))

(defn- get-chart-data
  "Vector of max *columns elements of [:Label value]"
  [data prefix slug column-name tooltip-suffix interval]
  (let [fixed-data (chart-placeholder-data data slug interval)
        chart-data (partial chart-data-at-index fixed-data column-name prefix tooltip-suffix interval)
        columns [["string" column-name]
                 ["number" column-name]
                 #js {"type" "string" "role" "style"}
                 #js {"type" "string" "role" "tooltip"}]
        mapper (vec (range (count fixed-data)))
        values (vec (map chart-data mapper))]
    { :prefix (if prefix prefix "")
      :columns columns
      :max-show columns-num
      :values values
      :pattern "###,###.##"
      :column-thickness "14"}))

(defn metrics-as-sequence [metric-map metric-slugs]
  (map metric-map metric-slugs))

(defn get-actual [metrics]
  (some #(when (:value (metrics %)) %) (vec (range (count metrics)))))

(defn growth-data-map [growth-data-coll]
  (apply merge (map #(hash-map (str (:period %) (:slug %)) %) growth-data-coll)))

(defn fill-gap-months [growth-data slug interval]
  (let [data-map (growth-data-map growth-data)
        sort-pred (fn [a b] (compare (:period a) (:period b)))
        sorted-data (vec (sort sort-pred growth-data))
        first-data (first sorted-data)
        last-data (last sorted-data)
        period (:period last-data)]
    (loop [current-period (:period last-data)
           filled-data data-map
           idx 1]
      (let [data (get data-map (str period slug))
            k (str current-period slug)
            next-data (if (contains? filled-data k)
                        filled-data
                        (assoc filled-data k (placeholder-data current-period slug {:placeholder-data true})))]
        (if (not= current-period (:period first-data))
          (recur (get-past-period period idx interval)
                 next-data
                 (inc idx))
          next-data)))))