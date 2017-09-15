(ns oc.web.components.entries-layout
  (:require [rum.core :as rum]
            [cuerdas.core :as s]
            [oc.web.router :as router]
            [oc.web.urls :as oc-urls]
            [oc.web.components.activity-card :refer (activity-card activity-card-empty)]))

(rum/defc entries-layout
  [board-data layout-type]
  [:div.entries-layout
    (cond
      ;; :by-topic
      (= layout-type :by-topic)
      (let [entries (vals (:fixed-items board-data))
            grouped-entries (apply merge (map (fn [[k v]] (hash-map k (vec (reverse (sort-by :created-at v))))) (group-by :topic-slug entries)))
            sorted-topics (vec (reverse (sort #(compare (:created-at (first (get grouped-entries %1))) (:created-at (first (get grouped-entries %2)))) (keys grouped-entries))))]
        (for [topic sorted-topics
              :let [entries-group (get grouped-entries topic)
                    topic-name (:topic-name (first entries-group))
                    topic-slug (:topic-slug (first entries-group))
                    first-line-entries (take 2 entries-group)
                    first-has-headline (some #(not (empty? (:headline %))) first-line-entries)
                    first-has-body (some #(not (empty? (:body %))) first-line-entries)
                    second-line-entries (if (> (count entries-group) 2) (subvec entries-group 2 (min 4 (count entries-group))) [])
                    second-has-headline (some #(not (empty? (:headline %))) second-line-entries)
                    second-has-body (some #(not (empty? (:body %))) second-line-entries)]]
          [:div.entry-cards-container.by-topic.group
            {:key (str "entries-topic-group-" (or topic "uncategorized"))}
            ; Title of the topic group
            [:div.by-topic-header.group
              [:div.by-topic-header-title
                (or topic-name
                    (s/capital topic-slug)
                    "Uncategorized")]
              ; If there are more than 4 add the button to show all of them
              (when (> (count entries-group) 4)
                [:button.view-all-updates.mlb-reset
                  {:on-click #(router/nav! (oc-urls/board-filter-by-topic topic-slug))}
                  "VIEW " (count entries-group) " UPDATES"])]
            ;; First row:
            [:div.entries-cards-container-row.group
              ; Render the first 2 entries
              (for [entry first-line-entries]
                (rum/with-key (activity-card entry first-has-headline first-has-body) (str "entry-by-topic-" topic "-" (:uuid entry))))
              ; If there is only 1 add the empty placeholder
              (when (= (count entries-group) 1)
                (if (not (empty? topic-slug))
                  (activity-card-empty (:read-only board-data))
                  [:div.entry-card.entry-card-placeholder]))]
            ; If there are more than 2 entries, render the second row
            (when (> (count entries-group) 2)
              [:div.entries-cards-container-row.group
                ; Render the second 2 entries
                (when (> (count entries-group) 2)
                  (for [entry (subvec entries-group 2 (min 4 (count entries-group)))]
                    (rum/with-key (activity-card entry second-has-headline second-has-body) (str "entry-by-topic-" topic "-" (:uuid entry)))))
                ; If the total entries are 3 add a placeholder to avoid taking the full width
                (when (= (count entries-group) 3)
                  [:div.entry-card.entry-card-placeholder])])]))
      ;; by specific topic
      (string? layout-type)
      (let [entries (vals (:fixed-items board-data))
            filtered-entries (if (= layout-type "uncategorized")
                                (vec (filter #(empty? (:topic-slug %)) entries))
                                (vec (filter #(= (:topic-slug %) layout-type) entries)))
            sorted-entries (vec (reverse (sort-by :created-at filtered-entries)))]
        [:div.entry-cards-container.by-specific-topic.group
          ; Calc the number of pairs
          (let [top-index (js/Math.ceil (/ (count sorted-entries) 2))]
            ; For each pari
            (for [idx (range top-index)
                  ; Calc the entries in the row
                  :let [start (* idx 2)
                        end (min (+ start 2) (count sorted-entries))
                        entries (subvec sorted-entries start end)
                        has-headline (some #(not (empty? (:headline %))) entries)
                        has-body (some #(not (empty? (:body %))) entries)]]
              [:div.entries-cards-container-row.group
                {:key (str "entries-row-" idx)}
                ; Render the entries in the row
                (for [entry entries]
                  (rum/with-key (activity-card entry has-headline has-body) (str "entry-topic-" (:topic-slug entry) "-" (:uuid entry))))
                ; If there is only one entry add the empty card placeholder
                (if (= (count sorted-entries) 1)
                  (activity-card-empty (:read-only board-data))
                  ; If there is only one entry in this row, but it's not the first add the placheolder
                  (when (= (count entries) 1)
                    [:div.entry-card.entry-card-placeholder]))]))])
      ;; :latest layout
      :else
      (let [entries (vals (:fixed-items board-data))
            sorted-entries (vec (reverse (sort-by :created-at entries)))]
        [:div.entry-cards-container.group
          ; Get the max number of pairs
          (let [top-index (js/Math.ceil (/ (count sorted-entries) 2))]
            ; For each pair
            (for [idx (range top-index)
                  ; calc the entries that needs to render in this row
                  :let [start (* idx 2)
                        end (min (+ start 2) (count sorted-entries))
                        entries (subvec sorted-entries start end)
                        has-headline (some #(not (empty? (:headline %))) entries)
                        has-body (some #(not (empty? (:body %))) entries)]]
              ; Renteder the entries in thisnrow
              [:div.entries-cards-container-row.group
                {:key (str "entries-row-" idx)}
                (for [entry entries]
                  (rum/with-key (activity-card entry has-headline has-body) (str "entry-latest-" (:uuid entry))))
                ; If the row contains less than 2, add a placeholder div to avoid having the first cover the full width
                (when (= (count entries) 1)
                  [:div.entry-card.entry-card-placeholder])]))]))])