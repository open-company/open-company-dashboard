(ns open-company-web.components.topic
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :refer-macros (sel1)]
            [open-company-web.router :as router]
            [open-company-web.caches :as cache]
            [open-company-web.api :as api]
            [open-company-web.dispatcher :as dis]
            [open-company-web.lib.utils :as utils]
            [open-company-web.components.ui.icon :as i]
            [open-company-web.components.finances.utils :as finances-utils]
            [open-company-web.components.growth.topic-growth :refer (topic-growth)]
            [open-company-web.components.finances.topic-finances :refer (topic-finances)]
            [open-company-web.local-settings :as ls]
            [goog.fx.dom :refer (Fade)]
            [goog.fx.dom :refer (Resize)]
            [goog.fx.Animation.EventType :as EventType]
            [goog.events :as events]
            [goog.style :as gstyle]))

(defcomponent topic-headline [data owner]
  (render [_]
    (dom/div {:class "topic-headline-inner"
              :dangerouslySetInnerHTML (utils/emojify (:headline data))})))

(defn scroll-to-topic-top [topic]
  (let [body-scroll (.-scrollTop (.-body js/document))
        topic-scroll-top (utils/offset-top topic)]
    (utils/scroll-to-y (- (+ topic-scroll-top body-scroll) 90))))

(defn pillbox-click-cb [owner metric-slug e]
  (.stopPropagation e)
  (.preventDefault e)
  (om/set-state! owner :selected-metric metric-slug)
  (let [section (om/get-props owner :section)
        topic-click-cb (om/get-props owner :topic-click)]
    (topic-click-cb metric-slug)))

(defn setup-card! [owner section]
  (when-not (utils/is-test-env?)
    (let [section-kw (keyword section)]
      (when (and (not (om/get-state owner :image-header))
                 (not (#{:finances :growth} section-kw)))
        (when-let [body (om/get-ref owner "topic-body")]
          (js/$clamp body #js {"clamp" 2})
          (when-let [first-image (sel1 body [:img])]
            (om/set-state! owner :image-header (.-src first-image))))))))

(defcomponent topic-internal [{:keys [topic-data section currency prev-rev next-rev] :as data} owner options]
  (init-state [_]
    {:image-header nil})

  (did-mount [_]
    (setup-card! owner section))

  (did-update [_ _ _]
    (setup-card! owner section))

  (render-state [_ {:keys [image-header]}]
    (let [section-kw (keyword section)
          topic-body (utils/get-topic-body topic-data section-kw)
          chart-opts {:chart-size {:width  260
                                   :height 196}
                      :pillboxes-first false
                      :topic-click (:topic-click options)}
          is-growth-finances? (#{:growth :finances} section-kw)]
      (dom/div #js {:className "topic-internal group"
                    :ref "topic-internal"}

        (when (or is-growth-finances?
                  image-header)
          (dom/div {:class (utils/class-set {:card-header true
                                             :card-image (not is-growth-finances?)})}
            (cond
              (= section "finances")
              (om/build topic-finances {:section-data topic-data :section section} {:opts chart-opts})
              (= section "growth")
              (om/build topic-growth {:section-data topic-data :section section} {:opts chart-opts})
              :else
              (dom/img {:src image-header}))))
        ;; Topic title
        (dom/div {:class "topic-title"} (:title topic-data))
        ;; Topic headline
        (om/build topic-headline topic-data)
        ;; Topic body: first 2 lines
        (when topic-body
          (dom/div #js {:className "topic-body"
                        :ref "topic-body"
                        :dangerouslySetInnerHTML (utils/emojify topic-body)}))))))

(defn topic-click [options selected-metric]
  ((:topic-click options) selected-metric))

(defn animate-revision-navigation [owner]
  (let [cur-topic (om/get-ref owner "cur-topic")
        tr-topic (om/get-ref owner "tr-topic")
        current-state (om/get-state owner)
        appear-animation (Fade. tr-topic 0 1 utils/oc-animation-duration)
        cur-size (js/getComputedStyle cur-topic)
        tr-size (js/getComputedStyle tr-topic)
        topic (om/get-ref owner "topic-anim")
        topic-size (js/getComputedStyle topic)
        scroll-top (.-scrollTop topic)]
    ; resize the light box
    (.play (Resize. topic
                    #js [(js/parseFloat (.-width topic-size)) (js/parseFloat (.-height cur-size))]
                    #js [(js/parseFloat (.-width topic-size)) (js/parseFloat (.-height tr-size))]
                    utils/oc-animation-duration))
    ; disappear current topic
    (.play (Fade. cur-topic 1 0 utils/oc-animation-duration))
    ; appear the new topic
    (doto appear-animation
      (events/listen
        EventType/FINISH
        #(om/set-state! owner (merge current-state
                                    {:as-of (:transition-as-of current-state)
                                     :transition-as-of nil})))
      (.play))))

(defcomponent topic [{:keys [section-data section currency] :as data} owner options]

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
          revisions-list (section-kw (slug @cache/revisions))
          topic-data (utils/select-section-data section-data section-kw as-of)]
      ;; preload previous revision
      (when (and prev-rev (not (contains? revisions-list (:updated-at prev-rev))))
        (api/load-revision prev-rev slug section-kw))
      ;; preload next revision as it can be that it's missing (ie: user jumped to the first rev then went forward)
      (when (and (not= (:updated-at next-rev) actual-as-of)
                  next-rev
                  (not (contains? revisions-list (:updated-at next-rev))))
        (api/load-revision next-rev slug section-kw))
      (dom/div #js {:className "topic group"
                    :ref "topic"
                    :onClick #(topic-click options nil)}
        (dom/div #js {:className "topic-anim group"
                      :key (str "topic-anim-" as-of "-" transition-as-of)
                      :ref "topic-anim"}
          (dom/div #js {:className "topic-as-of group"
                        :ref "cur-topic"
                        :key (str "cur-" as-of)
                        :style #js {:opacity 1 :width "100%" :height "auto"}}
            (om/build topic-internal {:section section
                                      :topic-data topic-data
                                      :currency currency
                                      :topic-click (partial topic-click options)
                                      :prev-rev prev-rev
                                      :next-rev next-rev}
                                     {:opts (merge options {:rev-click (fn [e rev]
                                                                          (scroll-to-topic-top (om/get-ref owner "topic"))
                                                                          (om/set-state! owner :transition-as-of (:updated-at rev))
                                                                          (.stopPropagation e))})}))
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
                                            :currency currency
                                            :topic-click #()
                                            :prev-rev tr-prev-rev
                                            :next-rev tr-next-rev}
                                           {:opts (merge options {:rev-click #()})})))))))))