(ns open-company-web.components.add-topic
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [open-company-web.lib.utils :as utils]
            [open-company-web.router :as router]
            [open-company-web.dispatcher :as dis]
            [open-company-web.caches :as caches]
            [open-company-web.components.ui.icon :as i]))

;; This should be defined as a derivative specifically suited to rendering the
;; topic adding component
(defn get-all-sections
  "Return all sections for the current company no matter it's state (archived, active, inactive)
   e.g {:section a-string :title a-string}"
  []
  (let [company-data (dis/company-data)
        slug         (keyword (router/current-company-slug))]
    (into
      (:archived company-data)
      (for [sec (:new-sections (get @caches/new-sections slug))]
        (-> sec
            (assoc :section (:section-name sec))
            (select-keys [:title :section]))))))

(rum/defcs custom-topic-input
  < (rum/local "" ::topic-title)
  [s submit-fn]
  (let [add-disabled (clojure.string/blank? @(::topic-title s))]
    [:div.mt1.flex
     [:input.npt.mr1.p1.flex-auto
      {:type "text",
       :value @(::topic-title s)
       :max-length 20
       :on-change #(reset! (::topic-title s) (.. % -target -value))
       :style {:font-size "16px"}
       :placeholder "Custom topic"}]
     [:button
      {:class (str "btn-reset" (if add-disabled " btn-outline" " btn-solid"))
       :disabled add-disabled
       :on-click #(let [topic-name     (str "custom-" (utils/my-uuid))
                        new-topic-data {:title @(::topic-title s)
                                        :section topic-name
                                        :placeholder true}]
                    (submit-fn topic-name new-topic-data))} "Add"]]))

(defn chunk-topics
  "Partition the provided sequences as if they were one with `::archived` inbetween"
  [inactive archived]
  (let [w-marker   (cond-> inactive (seq archived) (conj ::archived))
        items      (into w-marker archived)
        chunk-size (inc (quot (count items) 2))]
    (partition-all chunk-size items)))

(rum/defcs add-topic < (drv/drv :company-data)
                       rum/static
                       rum/reactive
  [s update-active-topics-cb]
  (let [company-data (drv/react s :company-data)
        active-topics (vec (map keyword (:sections company-data)))
        archived-topics (:archived company-data)
        all-sections (into {} (for [s (get-all-sections)]
                                [(keyword (:section s)) s]))
        slug (keyword (router/current-company-slug))
        topic-order (map keyword (:new-section-order (get @caches/new-sections slug)))
        inactive-not-archived (filterv (complement (clojure.set/union (set active-topics) (set archived-topics)))
                                       topic-order)
        chunked (chunk-topics inactive-not-archived archived-topics)]
      [:div.add-topic.group
       [:div.open-sans.small-caps.bold.mb2.gray5
        [:span.mr1 "Suggested Topics"]
        [:span.dimmed-gray.btn-reset.right
         {:on-click #(dis/dispatch! [:show-add-topic false])}
          (i/icon :simple-remove {:color "rgba(78, 90, 107, 0.8)" :size 16 :stroke 8 :accent-color "rgba(78, 90, 107, 1.0)"})]]
       [:div.mxn2.clearfix
        (for [col chunked]
          [:div.col.col-6.px2
           {:key (first col)}
           (for [topic col]
             (if (= ::archived topic)
               [:span.block.open-sans.small-caps.bold.mb1.mt2 {:key topic} "Archived"]
               (let [topic-full (get all-sections topic)]
                 [:div.mb1.btn-reset.yellow-line-hover-child
                  {:key topic
                   :on-click #(update-active-topics-cb (:section topic-full))}
                  [:span.child
                   (str (:title topic-full) (when (#{:finances :growth} topic) " "))
                   (when (#{:finances :growth} topic)
                      [:i.fa.fa-line-chart])
                   (:section-name topic-full)]])))])]
       (custom-topic-input #(update-active-topics-cb %1 %2))]))