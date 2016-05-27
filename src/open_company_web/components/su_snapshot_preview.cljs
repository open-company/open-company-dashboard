(ns open-company-web.components.su-snapshot-preview
  (:require-macros [cljs.core.async.macros :refer (go)])
  (:require [cljs.core.async :refer (chan <!)]
            [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :as dommy :refer-macros (sel1)]
            [open-company-web.router :as router]
            [open-company-web.dispatcher :as dis]
            [open-company-web.lib.utils :as utils]
            [open-company-web.lib.responsive :as responsive]
            [open-company-web.components.menu :refer (menu)]
            [open-company-web.components.navbar :refer (navbar)]
            [open-company-web.components.footer :refer (footer)]
            [open-company-web.components.topics-columns :refer (topics-columns)]
            [open-company-web.components.company-header :refer (company-header)]
            [open-company-web.components.fullscreen-topic :refer (fullscreen-topic)]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [goog.fx.Animation.EventType :as AnimationEventType]
            [goog.fx.dom :refer (Fade)]
            [cljsjs.hammer]))

(defn close-overlay-cb [owner]
  (om/set-state! owner :transitioning false)
  (om/set-state! owner :selected-topic nil)
  (om/set-state! owner :selected-metric nil))

(defn topic-click [owner topic selected-metric]
  (om/set-state! owner :selected-topic topic)
  (om/set-state! owner :selected-metric selected-metric))

(defn switch-topic [owner is-left?]
  (when (and (om/get-state owner :topic-navigation)
             (om/get-state owner :selected-topic)
             (nil? (om/get-state owner :tr-selected-topic)))
    (let [selected-topic (om/get-state owner :selected-topic)
          company-data   (dis/company-data (om/get-props owner))
          topics         (:sections (:stakeholder-update company-data))
          current-idx    (.indexOf (vec topics) selected-topic)]
      (if is-left?
        ;prev
        (let [prev-idx (mod (dec current-idx) (count topics))
              prev-topic (get (vec topics) prev-idx)]
          (om/set-state! owner :tr-selected-topic prev-topic))
        ;next
        (let [next-idx (mod (inc current-idx) (count topics))
              next-topic (get (vec topics) next-idx)]
          (om/set-state! owner :tr-selected-topic next-topic))))))

(defn kb-listener [owner e]
  (let [key-code (.-keyCode e)]
    (when (= key-code 39)
      ;next
      (switch-topic owner false))
    (when (= key-code 37)
      (switch-topic owner true))))

(defn animation-finished [owner]
  (let [cur-state (om/get-state owner)]
    (om/set-state! owner (merge cur-state {:selected-topic (:tr-selected-topic cur-state)
                                           :transitioning true
                                           :tr-selected-topic nil}))))

(defn animate-selected-topic-transition [owner]
  (let [selected-topic (om/get-ref owner "selected-topic")
        tr-selected-topic (om/get-ref owner "tr-selected-topic")
        fade-anim (new Fade selected-topic 1 0 utils/oc-animation-duration)
        cur-state (om/get-state owner)]
    (.play (new Fade tr-selected-topic 0 1 utils/oc-animation-duration))
    (doto fade-anim
      (.listen AnimationEventType/FINISH #(animation-finished owner))
      (.play))))

(defn stakeholder-update-data [owner]
  (let [props (om/get-props owner)]
    (if (om/get-state owner :su-preview)
      (:stakeholder-update (dis/company-data props))
      (dis/stakeholder-update-data))))

(defcomponent su-snapshot-preview [data owner options]

  (init-state [_]
    (utils/add-channel "fullscreen-topic-save" (chan))
    (utils/add-channel "fullscreen-topic-cancel" (chan))
    {:selected-topic nil
     :su-preview (utils/in? (:route @router/path) "su-snapshot-preview")
     :selected-metric nil
     :topic-navigation true
     :transitioning false})

  (did-mount [_]
    (when-not (utils/is-test-env?)
      (let [kb-listener (events/listen js/window EventType/KEYDOWN (partial kb-listener owner))
            swipe-listener (js/Hammer (sel1 [:div#app]))];(.-body js/document))]
        (om/set-state! owner :kb-listener kb-listener)
        (om/set-state! owner :swipe-listener swipe-listener)
        (.on swipe-listener "swipeleft" (fn [e] (switch-topic owner true)))
        (.on swipe-listener "swiperight" (fn [e] (switch-topic owner false))))))

  (will-unmount [_]
    (utils/remove-channel "fullscreen-topic-save")
    (utils/remove-channel "fullscreen-topic-cancel")
    (when-not (utils/is-test-env?)
      (events/unlistenByKey (om/get-state owner :kb-listener))
      (let [swipe-listener (om/get-state owner :swipe-listener)]
        (.off swipe-listener "swipeleft")
        (.off swipe-listener "swiperight"))))

  (did-update [_ _ _]
    (when (om/get-state owner :tr-selected-topic)
      (animate-selected-topic-transition owner)))

  (render-state [_ {:keys [selected-topic tr-selected-topic selected-metric transitioning su-preview]}]
    (let [company-data (dis/company-data data)
          su-data      (stakeholder-update-data owner)
          columns-num  (responsive/columns-num)
          card-width   (responsive/calc-card-width)
          ww           (.-clientWidth (sel1 js/document :body))
          total-width  (case columns-num
                         3 (str (+ (* card-width 3) 40 60) "px")
                         2 (str (+ (* card-width 2) 20 60) "px")
                         1 (if (> ww 413) (str card-width "px") "auto"))
          su-subtitle  (str "- " (utils/date-string (js/Date.) true))]
      (dom/div {:class (utils/class-set {:su-snapshot-preview true
                                         :main-scroll true
                                         :navbar-offset (not (responsive/is-mobile))})}
        (om/build menu data)
        (dom/div {:class "page"}
          ;; Navbar
          (when company-data
            (om/build navbar {:company-data company-data
                              :card-width card-width
                              :sharing-mode false
                              :su-preview su-preview
                              :hide-right-menu true
                              :columns-num columns-num
                              :auth-settings (:auth-settings data)}))
          ;; SU Snapshot Preview
          (when company-data
            (dom/div {:class "su-sp-content"}
              ;; Fullscreen topic
              (when selected-topic
                (dom/div {:class "selected-topic-container"
                          :style #js {:opacity (if selected-topic 1 0)}}
                  (dom/div #js {:className "selected-topic"
                                :key (str "transition-" selected-topic)
                                :ref "selected-topic"
                                :style #js {:opacity 1 :backgroundColor "rgba(255, 255, 255, 0.98)"}}
                    (om/build fullscreen-topic {:section selected-topic
                                                :section-data (->> selected-topic keyword (get company-data))
                                                :selected-metric selected-metric
                                                :read-only true
                                                :card-width card-width
                                                :currency (:currency company-data)
                                                :hide-history-navigation true
                                                :animate (not transitioning)}
                                               {:opts {:close-overlay-cb #(close-overlay-cb owner)
                                                       :topic-navigation #(om/set-state! owner :topic-navigation %)}}))
                  ;; Fullscreen topic for transition
                  (when tr-selected-topic
                    (dom/div #js {:className "tr-selected-topic"
                                  :key (str "transition-" tr-selected-topic)
                                  :ref "tr-selected-topic"
                                  :style #js {:opacity (if tr-selected-topic 0 1)}}
                    (om/build fullscreen-topic {:section tr-selected-topic
                                                :section-data (->> tr-selected-topic keyword (get company-data))
                                                :selected-metric selected-metric
                                                :read-only (:read-only company-data)
                                                :card-width card-width
                                                :currency (:currency company-data)
                                                :hide-history-navigation true
                                                :animate false}
                                               {:opts {:close-overlay-cb #(close-overlay-cb owner)
                                                       :topic-navigation #(om/set-state! owner :topic-navigation %)}})))))
              (when (:title su-data)
                (dom/div {:class "preview-title"} (:title su-data)))
              (dom/div {:class "preview-subtitle"} su-subtitle)
              (om/build topics-columns {:columns-num columns-num
                                        :card-width card-width
                                        :total-width total-width
                                        :content-loaded (not (:loading data))
                                        :topics (:sections su-data)
                                        :su-preview true
                                        :company-data company-data
                                        :hide-add-topic true}
                                       {:opts {:topic-click (partial topic-click owner)}})))
          ;;Footer
          (when company-data
            (om/build footer {:columns-num columns-num
                              :su-preview true
                              :card-width card-width})))))))