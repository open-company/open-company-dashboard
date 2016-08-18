(ns open-company-web.components.topic
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :refer-macros (sel1)]
            [open-company-web.api :as api]
            [open-company-web.caches :as caches]
            [open-company-web.router :as router]
            [open-company-web.dispatcher :as dis]
            [open-company-web.local-settings :as ls]
            [open-company-web.lib.utils :as utils]
            [open-company-web.lib.oc-colors :as oc-colors]
            [open-company-web.lib.responsive :as responsive]
            [open-company-web.components.growth.utils :as growth-utils]
            [open-company-web.components.growth.topic-growth :refer (topic-growth)]
            [open-company-web.components.finances.topic-finances :refer (topic-finances)]
            [open-company-web.components.topic-edit :refer (topic-edit)]
            [open-company-web.components.topic-attribution :refer (topic-attribution)]
            [open-company-web.components.ui.icon :as i]
            [open-company-web.components.ui.topic-read-more :refer (topic-read-more)]
            [goog.fx.dom :refer (Fade)]
            [goog.fx.dom :refer (Resize)]
            [goog.fx.Animation.EventType :as EventType]
            [goog.events :as events]
            [goog.style :as gstyle]
            [cljsjs.react.dom]))

(defcomponent topic-image-header [{:keys [image-header image-size]} owner options]
  (render [_]
    (dom/img {:src image-header
              :class "topic-header-img"})))

(defcomponent topic-headline [data owner]
  (render [_]
    (dom/div #js {:className (str "topic-headline-inner" (when (:placeholder data) " italic"))
                  :dangerouslySetInnerHTML (utils/emojify (:headline data))})))

(defn fullscreen-topic [owner selected-metric force-editing & [e]]
  (when (not (om/get-props owner :foce-active))
    (when (and e (not= (.-tagName (.-target e)) "A"))
      (utils/event-stop e))
    ((om/get-props owner :topic-click) selected-metric force-editing)))


(defn start-foce-click [owner]
  (let [section-kw (keyword (om/get-props owner :section))
        company-data (dis/company-data)
        section-data (get company-data section-kw)]
    (dis/dispatch! [:start-foce section-kw section-data])))

(defn pencil-click [owner e]
  (utils/event-stop e)
  (let [section (om/get-props owner :section)]
    (if (#{:growth :finances} (keyword section))
      (fullscreen-topic owner nil true)
      (start-foce-click owner))))

(defn block-a-expand []
  (when-not (utils/is-test-env?)
    (.on (js/$ "div.topic-body a") "click" #(.stopPropagation %))))

(defcomponent topic-internal [{:keys [topic-data
                                      section
                                      currency
                                      card-width
                                      prev-rev
                                      next-rev
                                      sharing-mode
                                      show-fast-editing] :as data} owner options]

  (did-mount [_]
    (block-a-expand))

  (did-update [_ _ _]
    (block-a-expand))

  (render [_]
    (let [section-kw          (keyword section)
          chart-opts          {:chart-size {:width  260
                                            :height 196}
                               :hide-nav false}
          is-growth-finances? (#{:growth :finances} section-kw)
          gray-color          (oc-colors/get-color-by-kw :oc-gray-5)
          finances-row-data   (:data topic-data)
          growth-data         (growth-utils/growth-data-map (:data topic-data))
          no-data             (or (and (= section-kw :finances)
                                       (or (empty? finances-row-data)
                                        (utils/no-finances-data? finances-row-data)))
                                  (and (= section-kw :growth)
                                       (utils/no-growth-data? growth-data)))
          image-header        (:image-url topic-data)
          image-header-size   {:width (:image-width topic-data)
                               :height (:image-height topic-data)}
          topic-body          (:body topic-data)
          truncated-body      (if (utils/is-test-env?) topic-body (.truncate js/$ topic-body (clj->js {:length 500 :words true})))]
      (dom/div #js {:className "topic-internal group"
                    :onClick (partial fullscreen-topic owner nil false)
                    :ref "topic-internal"}
        (when (or is-growth-finances?
                  image-header)
          (dom/div {:class (utils/class-set {:card-header true
                                             :card-image (not is-growth-finances?)})}
            (cond
              (= section "finances")
              (om/build topic-finances {:section-data (utils/fix-finances topic-data)
                                        :section section
                                        :currency currency
                                        :topic-click (partial fullscreen-topic owner nil false)} {:opts chart-opts})
              (= section "growth")
              (om/build topic-growth {:section-data topic-data :section section :currency currency} {:opts chart-opts})
              :else
              (om/build topic-image-header {:image-header image-header :image-size image-header-size} {:opts options}))))
        ;; Topic title
        (dom/div {:class "group"}
          (dom/div {:class "topic-title"} (:title topic-data))
          (when (and show-fast-editing
                   (responsive/can-edit?)
                   (not (responsive/is-mobile))
                   (not (:read-only topic-data))
                   (not sharing-mode)
                   (not (:foce-active data)))
            (dom/button {:class (str "topic-pencil-button btn-reset")
                         :on-click #(pencil-click owner %)}
              (dom/i {:class "fa fa-pencil"
                      :title "Edit"
                      :data-toggle "tooltip"
                      :data-placement "top"}))))
        ;; Topic headline
        (when-not (clojure.string/blank? (:headline topic-data))
          (om/build topic-headline topic-data))
        (dom/div #js {:className (str "topic-body" (when (:placeholder topic-data) " italic"))
                      :ref "topic-body"
                      :dangerouslySetInnerHTML (utils/emojify truncated-body)})
        ; if it's SU preview or SU show only read-more
        (if (or (utils/in? (:route @router/path) "su-snapshot-preview")
                (utils/in? (:route @router/path) "su-snapshot"))
          (dom/div {:class "left"
                    :style {:margin-bottom "28px"}}
            (om/build topic-read-more (assoc data :read-more-cb (partial fullscreen-topic owner nil false))))
          (om/build topic-attribution (assoc data :read-more-cb (partial fullscreen-topic owner nil false)) {:opts options}))))))

(defn animate-revision-navigation [owner]
  (let [cur-topic (om/get-ref owner "cur-topic")
        tr-topic (om/get-ref owner "tr-topic")
        current-state (om/get-state owner)
        anim-duration utils/oc-animation-duration
        appear-animation (Fade. tr-topic 0 1 anim-duration)
        cur-size (js/getComputedStyle cur-topic)
        tr-size (js/getComputedStyle tr-topic)
        topic (om/get-ref owner "topic-anim")
        topic-size (js/getComputedStyle topic)]
    ; resize the light box
    (.play (Resize. topic
                    #js [(js/parseFloat (.-width topic-size)) (js/parseFloat (.-height cur-size))]
                    #js [(js/parseFloat (.-width topic-size)) (js/parseFloat (.-height tr-size))]
                    anim-duration))
    ; make the current topic disappear
    (.play (Fade. cur-topic 1 0 anim-duration))
    ; appear the new topic
    (doto appear-animation
      (events/listen
        EventType/FINISH
        #(om/set-state! owner (merge current-state
                                    {:as-of (:transition-as-of current-state)
                                     :transition-as-of nil})))
      (.play))))


(defn get-all-sections [slug]
  (let [categories-data (:categories (slug @caches/new-sections))
        all-category-sections (apply concat
                                     (for [category categories-data]
                                       (let [cat-name (:name category)
                                             sections (:sections category)]
                                         (map #(assoc % :category cat-name) sections))))]
    (apply merge
           (map #(hash-map (keyword (:section-name %)) %) all-category-sections))))

(defcomponent topic [{:keys [active-topics
                             section-data
                             section
                             currency
                             column
                             card-width
                             sharing-mode
                             share-selected
                             archived-topics
                             show-share-remove] :as data} owner options]

  (init-state [_]
    {:as-of (:updated-at section-data)
     :actual-as-of (:updated-at section-data)
     :transition-as-of nil})

  (will-update [_ next-props _]
    (let [new-as-of (:updated-at (:section-data next-props))
          current-as-of (om/get-state owner :as-of)
          old-as-of (:updated-at section-data)]
      (when (and (not= old-as-of new-as-of)
                 (not= current-as-of new-as-of))
        (om/set-state! owner :as-of new-as-of)
        (om/set-state! owner :actual-as-of new-as-of))))

  (did-update [_ prev-props _]
    (when (om/get-state owner :transition-as-of)
      (animate-revision-navigation owner)))

  (render-state [_ {:keys [editing as-of actual-as-of transition-as-of] :as state}]
    (let [section-kw (keyword section)
          revisions (utils/sort-revisions (:revisions section-data))
          prev-rev (utils/revision-prev revisions as-of)
          next-rev (utils/revision-next revisions as-of)
          slug (keyword (router/current-company-slug))
          all-revisions (slug @caches/revisions)
          revisions-list (section-kw all-revisions)
          topic-data (utils/select-section-data section-data section-kw as-of)
          rev-cb (fn [e rev]
                  (om/set-state! owner :transition-as-of (:updated-at rev))
                  (utils/event-stop e))
          foce-active (not (nil? (dis/foce-section-key)))
          is-foce (= (dis/foce-section-key) section-kw)]
      ;; preload previous revision
      (when (and prev-rev (not (contains? revisions-list (:updated-at prev-rev))))
        (api/load-revision prev-rev slug section-kw))
      ;; preload next revision as it can be that it's missing (ie: user jumped to the first rev then went forward)
      (when (and (not= (:updated-at next-rev) actual-as-of)
                  next-rev
                  (not (contains? revisions-list (:updated-at next-rev))))
        (api/load-revision next-rev slug section-kw))
      (dom/div #js {:className (utils/class-set {:topic true
                                                 :group true
                                                 :no-foce (and foce-active (not is-foce))
                                                 :sharing-selected (and sharing-mode share-selected)})
                    :ref "topic"
                    :id (str "topic-" (name section))
                    :onClick #(when (and (:topic-click options) (not foce-active))
                                ((:topic-click options) nil false))}
        (when show-share-remove
          (dom/div {:class "share-remove-container"
                    :id (str "share-remove-" (name section))}
            (dom/button {:class "btn-reset share-remove"
                         :on-click #(when (contains? options :share-remove-click) ((:share-remove-click options) (name section)))}
              (i/icon :simple-remove {:color "rgba(78, 90, 107, 0.5)" :size 12 :stroke 4 :accent-color "rgba(78, 90, 107, 0.5)"}))))
        (dom/div #js {:className "topic-anim group"
                      :key (str "topic-anim-" as-of "-" transition-as-of)
                      :ref "topic-anim"}
          (dom/div #js {:className "topic-as-of group"
                        :key (str "cur-" as-of)
                        :style #js {:opacity 1 :width "100%" :height "auto"}}
            (dom/div #js {:className "topic-cur-as-of"
                          :ref "cur-topic"
                          :style #js {:opacity 1}}
              (if is-foce
                (om/build topic-edit {:section section
                                      :topic-data topic-data
                                      :sharing-mode sharing-mode
                                      :show-fast-editing (:show-fast-editing data)
                                      :currency currency
                                      :card-width card-width
                                      :read-only-company (:read-only-company data)
                                      :foce-key (:foce-key data)
                                      :foce-data (:foce-data data)
                                      :prev-rev prev-rev
                                      :next-rev next-rev}
                                     {:opts (merge options {:rev-click rev-cb})
                                      :key (str "topic-foce-" section)})
                (om/build topic-internal {:section section
                                          :topic-data topic-data
                                          :sharing-mode sharing-mode
                                          :show-fast-editing (:show-fast-editing data)
                                          :currency currency
                                          :card-width card-width
                                          :read-only-company (:read-only-company data)
                                          :topic-click (:topic-click options)
                                          :is-foce is-foce
                                          :foce-active foce-active
                                          :prev-rev prev-rev
                                          :next-rev next-rev}
                                         {:opts (merge options {:rev-click rev-cb})
                                          :key (str "topic-" section)})))
            (when transition-as-of
              (dom/div #js {:className "topic-tr-as-of group"
                            :ref "tr-topic"
                            :key (str "tr-" transition-as-of "-expanded")
                            :style #js {:opacity 1}}
                (let [tr-topic-data (utils/select-section-data section-data section-kw transition-as-of)
                      tr-prev-rev (utils/revision-prev revisions transition-as-of)
                      tr-next-rev (utils/revision-next revisions transition-as-of)]
                  (om/build topic-internal {:section section
                                            :topic-data tr-topic-data
                                            :sharing-mode sharing-mode
                                            :currency currency
                                            :read-only-company (:read-only-company data)
                                            :prev-rev tr-prev-rev
                                            :next-rev tr-next-rev}
                                           {:opts (merge options {:rev-click rev-cb})}))))))))))