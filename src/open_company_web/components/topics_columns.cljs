(ns open-company-web.components.topics-columns
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :refer-macros (sel1 sel)]
            [open-company-web.router :as router]
            [open-company-web.dispatcher :as dis]
            [open-company-web.lib.responsive :as responsive]
            [open-company-web.lib.utils :as utils]
            [open-company-web.components.topic :refer (topic)]
            [open-company-web.components.topic-view :refer (topic-view)]
            [open-company-web.components.add-topic :as at]
            [open-company-web.components.bw-topics-list :refer (bw-topics-list)]))

;; Calc best topics layout based on heights

(defn add-topic? [owner]
  (let [data (om/get-props owner)
        company-data (:company-data data)
        foce-active  (dis/foce-section-key)]
    (and (not (:hide-add-topic data))
         (responsive/can-edit?)
         (not (:read-only company-data))
         (not foce-active))))

(def inter-topic-gap 22)
(def add-a-topic-height 95)

(def topic-default-height 70)
(def data-topic-default-zero-height 74)
(def data-topic-default-one-height 251)
(def data-topic-default-more-height 357)
(def topic-body-height 29)
(def add-topic-height 94)
(def read-more-height 15)

(def topic-margins 20)
(def mobile-topic-margins 3)

(defn headline-body-height [headline body card-width]
  (let [$headline (js/$ (str "<div class=\"topic\">"
                                "<div>"
                                  "<div class=\"topic-internal\">"
                                    (when-not (clojure.string/blank? headline)
                                      (str "<div class=\"topic-headline-inner\" style=\"width: " (+ card-width (if (responsive/is-mobile-size?) mobile-topic-margins topic-margins)) "px;\">"
                                             (utils/emojify headline true)
                                           "</div>"))
                                    "<div class=\"topic-body\" style=\"width: " (+ card-width (if (responsive/is-mobile-size?) mobile-topic-margins topic-margins)) "px;\">"
                                      (utils/emojify body true)
                                    "</div>"
                                  "</div>"
                                "</div>"
                              "</div>"))]
    (.appendTo $headline (.-body js/document))
    (let [height (.height $headline)]
      (.detach $headline)
      height)))

(defn data-topic-height [owner topic topic-data]
  (if (= topic :finances)
    (cond
      (= (count (:data topic-data)) 0)
      data-topic-default-zero-height
      (= (count (:data topic-data)) 1)
      data-topic-default-one-height
      (> (count (:data topic-data)) 1)
      data-topic-default-more-height)
    (let [data (:data topic-data)
          selected-metric (or (om/get-props owner :selected-metric) (:slug (first (:metrics topic-data))))
          metric-data (filter #(= (:slug %) selected-metric) data)]
      (cond
        (= (count metric-data) 0)
        data-topic-default-zero-height
        (= (count metric-data) 1)
        data-topic-default-one-height
        (> (count metric-data) 1)
        data-topic-default-more-height))))

(defn calc-column-height [owner data topics clmn]
  (let [card-width (om/get-props owner :card-width)
        topics-data (:topics-data data)]
    (for [topic topics
          :let [topic-kw (keyword topic)
                topic-data (get topics-data topic-kw)
                is-data-topic (#{:finances :growth} topic-kw)
                topic-body (:body topic-data)]]
      (cond
        (= topic "add-topic")
        add-topic-height
        (#{:finances :growth} topic-kw)
        (let [headline-height (headline-body-height (:headline topic-data) topic-body card-width)
              start-height (data-topic-height owner topic topic-data)
              read-more    (if (clojure.string/blank? (utils/strip-HTML-tags (:body topic-data))) 0 read-more-height)]
          (+ start-height headline-height read-more))
        :else
        (let [topic-image-height      (if (:image-url topic-data)
                                        (utils/aspect-ration-image-height (:image-width topic-data) (:image-height topic-data) card-width)
                                        0)
              headline-body-height (headline-body-height (:headline topic-data) topic-body card-width)
              read-more               (if (clojure.string/blank? (utils/strip-HTML-tags (:body topic-data))) 0 read-more-height)]
          (+ topic-default-height headline-body-height topic-image-height read-more))))))

(defn get-shortest-column [owner data current-layout]
  (let [columns-num (:columns-num data)
        frst-clmn (apply + (calc-column-height owner data (:1 current-layout) 1))
        scnd-clmn (apply + (calc-column-height owner data (:2 current-layout) 2))
        thrd-clmn (apply + (calc-column-height owner data (:3 current-layout) 3))
        min-height (if (= columns-num 3)
                    (min frst-clmn scnd-clmn thrd-clmn)
                    (min frst-clmn scnd-clmn))]
    (cond
      (= min-height frst-clmn)
      :1
      (= min-height scnd-clmn)
      :2
      (= min-height thrd-clmn)
      :3)))

(defn get-initial-layout [columns-num]
  (cond
    ; 2 columns empty layout
    (= columns-num 2)
    {:1 [] :2 []}
    ; 3 columns empty layout
    (= columns-num 3)
    {:1 [] :2 [] :3 []}))

(defn calc-layout
  "Calculate the best layout given the list of topics and the number of columns to layout to"
  [owner data]
  (cond
    ; avoid to crash tests
    (utils/is-test-env?)
    (om/get-props owner :topics)
    ; for mobile just layout the sections in :sections order
    ; w/o caring about the height it might be
    (responsive/is-mobile-size?)
    (let [sections (:sections (:company-data data))]
      (loop [idx 0
             layout {:1 [] :2 []}]
        (if (= idx (count sections))
          layout
          (let [topic (get sections idx)]
            (recur (inc idx)
                   (if (even? idx)
                      (assoc layout :1 (conj (:1 layout) topic))
                      (assoc layout :2 (conj (:2 layout) topic))))))))
    ; on big web guess what the topic height will be and layout the topics in
    ; the best order possible
    :else
    (let [columns-num (:columns-num data)
          company-data (:company-data data)
          show-add-topic (add-topic? owner)
          topics-list (:topics data)
          final-layout (loop [idx 0
                              layout (get-initial-layout columns-num)]
                          (let [shortest-column (get-shortest-column owner data layout)
                                new-column (conj (get layout shortest-column) (get topics-list idx))
                                new-layout (assoc layout shortest-column new-column)]
                            (if (<= (inc idx) (count topics-list))
                              (recur (inc idx)
                                     new-layout)
                              new-layout)))
          clean-layout (apply merge (for [[k v] final-layout] {k (vec (remove nil? v))}))]
      clean-layout)))

(defn render-topic [owner options section-name & [column]]
  (when section-name
    (let [props                 (om/get-props owner)
          company-data          (:company-data props)
          topics-data           (:topics-data props)
          topics                (:topics props)
          topic-click           (or (:topic-click options) identity)
          update-active-topics  (or (:update-active-topics options) identity)
          slug                  (keyword (router/current-company-slug))
          window-scroll         (.-scrollTop (.-body js/document))
          is-dashboard          (:is-dashboard props)]
      (if (= section-name "add-topic")
        (at/add-topic {:column column
                       :archived-topics (mapv (comp keyword :section) (:archived company-data))
                       :active-topics (vec topics)
                       :initially-expanded (and (not (om/get-props owner :loading))
                                                (om/get-props owner :new-sections)
                                                (zero? (count topics)))
                       :update-active-topics update-active-topics})
        (let [sd (->> section-name keyword (get topics-data))
              topic-row-style (if (or (utils/in? (:route @router/path) "su-snapshot-preview")
                                      (utils/in? (:route @router/path) "su-list"))
                                #js {}
                                #js {:width (if is-dashboard
                                              (if (responsive/window-exceeds-breakpoint)
                                                (str (:card-width props) "px")
                                                "auto")
                                              (if (responsive/is-mobile-size?)
                                                "auto"
                                                (str (:card-width props) "px")))})]
          (when-not (and (:read-only company-data) (:placeholder sd))
            (dom/div #js {:className "topic-row"
                          :data-topic (name section-name)
                          :style topic-row-style
                          :ref section-name
                          :key (str "topic-row-" (name section-name))}
              (om/build topic {:loading (:loading company-data)
                               :section section-name
                               :is-stakeholder-update (:is-stakeholder-update props)
                               :section-data sd
                               :card-width (:card-width props)
                               :foce-data-editing? (:foce-data-editing? props)
                               :read-only-company (:read-only company-data)
                               :currency (:currency company-data)
                               :foce-key (:foce-key props)
                               :foce-data (:foce-data props)
                               :is-dashboard is-dashboard}
                               {:opts {:section-name section-name
                                       :topic-click (partial topic-click section-name)}}))))))))

(defcomponent topics-columns [{:keys [columns-num
                                      content-loaded
                                      total-width
                                      card-width
                                      topics
                                      company-data
                                      topics-data
                                      is-dashboard
                                      is-stakeholder-update] :as data} owner options]

  (did-mount [_]
    (when (> columns-num 1)
      (om/set-state! owner :best-layout (calc-layout owner data))))

  (will-receive-props [_ next-props]
    (when (and (> (:columns-num next-props) 1)
               (or (not= (:topics next-props) (:topics data))
                   (not= (:columns-num next-props) (:columns-num data))))
      (om/set-state! owner :best-layout (calc-layout owner next-props))))

  (render-state [_ {:keys [best-layout]}]
    (let [selected-topic-view   (:selected-topic-view data)
          show-add-topic        (add-topic? owner)
          partial-render-topic  (partial render-topic owner options)
          columns-container-key (apply str topics)
          topics-column-conatiner-style (if is-dashboard
                                          (if (responsive/window-exceeds-breakpoint)
                                            #js {:width total-width}
                                            #js {:margin "0px 9px"
                                                 :width "auto"})
                                          (if (responsive/is-mobile-size?)
                                            #js {:margin "0px 9px"
                                                 :width "auto"}
                                            #js {:width total-width}))]
      ;; Topic list
      (dom/div {:class (utils/class-set {:topics-columns true
                                         :overflow-visible true
                                         :group true
                                         :content-loaded content-loaded})}
        (cond
          ;; render 2 or 3 column layout
          (> columns-num 1)
          (dom/div {:class (utils/class-set {:topics-column-container true
                                             :group true
                                             :tot-col-3 (and is-dashboard
                                                             (= columns-num 3))
                                             :tot-col-2 (and is-dashboard
                                                             (= columns-num 2))})
                    :style topics-column-conatiner-style
                    :key columns-container-key}
            (when-not (responsive/is-tablet-or-mobile?)
              (om/build bw-topics-list data))
            (if (and is-dashboard
                     (not (responsive/is-tablet-or-mobile?))
                     selected-topic-view)
              (om/build topic-view {:card-width card-width
                                    :columns-num columns-num
                                    :company-data company-data
                                    :foce-key (:foce-key data)
                                    :foce-data (:foce-data data)
                                    :foce-data-editing? (:foce-data-editing? data)
                                    :selected-topic-view selected-topic-view})
              ; for each column key contained in best layout
              (dom/div {:class "right" :style {:width (str (- (int total-width) responsive/left-topics-list-width) "px")}}
                (for [kw (if (= columns-num 3) [:1 :2 :3] [:1 :2])]
                  (let [column (get best-layout kw)]
                    (dom/div {:class (str "topics-column col-" (name kw))
                              :style #js {:width (str (+ card-width (if (responsive/is-mobile-size?) mobile-topic-margins topic-margins)) "px")}}
                      ; render the topics
                      (when (pos? (count column))
                        (for [idx (range (count column))
                              :let [section-kw (get column idx)
                                    section-name (name section-kw)]]
                          (partial-render-topic section-name
                                                (when (= section-name "add-topic") (int (name kw))))))
                      ; render the add topic in the correct column
                      (when (and show-add-topic
                                 (= kw :1)
                                 (= (count topics) 0))
                        (partial-render-topic "add-topic" 1))
                      (when (and show-add-topic
                                 (= kw :2)
                                 (or (and (= (count topics) 1)
                                          (= columns-num 3))
                                     (and (>= (count topics) 1)
                                          (= columns-num 2))))
                        (partial-render-topic "add-topic" 2))
                      (when (and show-add-topic
                                 (= kw :3)
                                 (>= (count topics) 2))
                        (partial-render-topic "add-topic" 3))))))))
          ;; 1 column or default
          :else
          (dom/div {:class "topics-column-container columns-1 group"
                    :style topics-column-conatiner-style
                    :key columns-container-key}
            (dom/div {:class "topics-column"}
              (for [section topics]
                (partial-render-topic (name section)))
              (when show-add-topic
                (partial-render-topic "add-topic" 1)))))))))