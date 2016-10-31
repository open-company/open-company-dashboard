(ns open-company-web.components.topic-list
  "
  Display either a dashboard listing of topics in 1-3 columns, or a selected topic full-screen.

  Handle topic selection, topic navigation, and share initiation.
  "
  (:require-macros [if-let.core :refer (when-let*)])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :as dommy :refer-macros (sel sel1)]
            [open-company-web.api :as api]
            [open-company-web.urls :as oc-urls]
            [open-company-web.caches :as caches]
            [open-company-web.router :as router]
            [open-company-web.lib.raven :as sentry]
            [open-company-web.dispatcher :as dispatcher]
            [open-company-web.lib.jwt :as jwt]
            [open-company-web.lib.utils :as utils]
            [open-company-web.lib.responsive :as responsive]
            [open-company-web.components.fullscreen-topic :refer (fullscreen-topic)]
            [open-company-web.components.topics-columns :refer (topics-columns)]
            [open-company-web.components.ui.onboard-tip :refer (onboard-tip)]
            [goog.events :as events]
            [goog.object :as gobj]
            [goog.events.EventType :as EventType]
            [goog.fx.Animation.EventType :as AnimationEventType]
            [goog.fx.dom :refer (Fade Slide)]
            [cljsjs.hammer]))

(def scrolled-to-top (atom false))

;; ===== Utility functions =====

(defn- get-active-topics [company-data]
  (:sections company-data))

(defn- get-topics [company-data active-topics]
  (map keyword active-topics))

;; ===== Events =====

(defn- update-active-topics [owner new-topic & [section-data]]
  (let [company-data (om/get-props owner :company-data)
        old-topics (get-active-topics company-data)
        new-topics (concat old-topics [new-topic])
        new-topic-kw (keyword new-topic)]
    (if section-data
      (dispatcher/dispatch! [:start-foce new-topic-kw (or section-data {:section new-topic :placeholder true :body-placeholder "What would you like to say about this?"})])
      (when-not (some #(= (:section %) (name new-topic)) (:archived (dispatcher/company-data)))
        (om/set-state! owner :new-topic-foce new-topic-kw)))
    (if section-data
      (api/patch-sections new-topics section-data new-topic)
      (api/patch-sections new-topics))))

(defn- topic-click [owner topic selected-metric & [force-edit]]
  (let [company-slug (router/current-company-slug)]
    (.pushState js/history nil (name topic) (oc-urls/company-section company-slug (name topic))))
  (om/set-state! owner :selected-topic topic)
  (om/set-state! owner :selected-metric selected-metric))

(defn- close-overlay-cb [owner]
  (.pushState js/history nil "Dashboard" (oc-urls/company (router/current-company-slug)))
  (om/set-state! owner (merge (om/get-state owner) {:transitioning false
                                                    :selected-topic nil
                                                    :selected-metric nil})))

(defn- switch-topic [owner is-left?]
  (when (and (om/get-state owner :topic-navigation)
             (om/get-state owner :selected-topic)
             (nil? (om/get-state owner :tr-selected-topic)))
    (let [selected-topic (om/get-state owner :selected-topic)
          active-topics (om/get-state owner :active-topics)
          topics-list (keys active-topics)
          current-idx (.indexOf (vec topics-list) selected-topic)]
      (om/set-state! owner :animation-direction is-left?)
      (if is-left?
        ;prev
        (let [prev-idx (mod (dec current-idx) (count topics-list))
              prev-topic (get (vec topics-list) prev-idx)]
          (om/set-state! owner :tr-selected-topic prev-topic))
        ;next
        (let [next-idx (mod (inc current-idx) (count topics-list))
              next-topic (get (vec topics-list) next-idx)]
          (om/set-state! owner :tr-selected-topic next-topic))))))

(defn- kb-listener [owner e]
  (let [key-code (.-keyCode e)]
    (when (= key-code 39)
      ;next
      (switch-topic owner false))
    (when (= key-code 37)
      (switch-topic owner true))))

;; ===== Animation =====

(defn- animation-finished [owner]
  (let [cur-state (om/get-state owner)]
    (.pushState js/history nil (name (:tr-selected-topic cur-state)) (oc-urls/company-section (router/current-company-slug) (:tr-selected-topic cur-state)))
    (om/set-state! owner (merge cur-state {:selected-topic (:tr-selected-topic cur-state)
                                           :transitioning true
                                           :tr-selected-topic nil}))))

(defn- animate-selected-topic-transition [owner left?]
  (let [selected-topic (om/get-ref owner "selected-topic")
        tr-selected-topic (om/get-ref owner "tr-selected-topic")
        width (responsive/fullscreen-topic-width (om/get-state owner :card-width))
        fade-anim (new Slide selected-topic #js [0 0] #js [(if left? width (* width -1)) 0] utils/oc-animation-duration)
        cur-state (om/get-state owner)]
    (doto fade-anim
      (.listen AnimationEventType/FINISH #(animation-finished owner))
      (.play))
    (.play (new Fade selected-topic 1 0 utils/oc-animation-duration))
    (.play (new Slide tr-selected-topic #js [(if left? (* width -1) width) 0] #js [0 0] utils/oc-animation-duration))
    (.play (new Fade tr-selected-topic 0 1 utils/oc-animation-duration))))

;; ===== Onboarding Tips =====

(defn- show-add-topic-tip?
  "Show initial welcome tooltip to a r/w user with no topics."
  [company-data active-topics]
  (when (and (jwt/jwt) (not (:read-only company-data)))
    (let [all-topics (get-topics company-data active-topics)]
      (empty? all-topics))))

(defn- show-first-edit-tip?
  "Show first edit tooltip to a r/w user if there is only one content section (not data),
  and it is a placeholder section."
  [company-data company-topics]
  (when (and (jwt/jwt) (not (:read-only company-data)))
    (let [filtered-topics (filter #(and (not= % :growth) (not= % :finances)) company-topics)]
      (and (= (count filtered-topics) 1)
           (->> filtered-topics first keyword (get company-data) :placeholder)))))

(defn- show-data-first-edit-tip? [company-data selected-topic]
  "Show data first edit tooltip to a r/w user if the selected topic is a data topic,
  and it is a placeholder section."
  (when (and (jwt/jwt) (not (:read-only company-data)))
    (when (or (= selected-topic "growth") (= selected-topic "finances"))
      (:placeholder (company-data (keyword selected-topic))))))

;; ===== Topic List Component =====

(defn- get-state [owner data current-state]
  ; get internal component state
  (let [company-data (:company-data data)
        active-topics (apply merge (map #(hash-map (keyword %) (->> % keyword (get company-data))) (get-active-topics company-data)))
        show-add-topic-tip (show-add-topic-tip? company-data active-topics)
        selected-topic (if (nil? current-state) (router/current-section) (:selected-topic current-state))]
    {; initial active topics to check with the updated active topics
     :initial-active-topics active-topics
     ; actual active topics possibly changed by the user
     :active-topics active-topics
     ; card with
     :card-width (:card-width data)
     ; remember if the /slug/new call was already started
     :new-sections-requested (or (:new-sections-requested current-state) false)
     ; selected topic for fullscreen
     :selected-topic selected-topic
     ; transitioning btw fullscreen topics, navigated with kb arrows or swipe on mobile
     :tr-selected-topic nil
     ; enamble/disable fullscreen topic navigation
     :topic-navigation (or (:topic-navigation current-state) true)
     ; share selected topics
     :share-selected-topics (:sections (:stakeholder-update company-data))
     ; transitioning btw fullscreen topics
     :transitioning false
     ; redirect the user to the updates preview page
     :redirect-to-preview (or (:redirect-to-preview current-state) false)
     ; remember if add topic tooltip was shown
     :show-add-topic-tip show-add-topic-tip
     ; remember if second add topic tooltip was shown
     :show-second-add-topic-tooltip (or (:show-second-add-topic-tooltip current-state) false)
     ; showremember if share tooltip was shown
     :show-share-tooltip (or (:show-share-tooltip current-state) false)
     ; remember if second pin tip was shown
     :show-second-pin-tip (or (:show-second-pin-tip current-state) false)
     ; this is used to foce the rerender of the component when the user drag a topic
     ; but it's dropped in a no action spot
     :rerender (rand 4)}))

(defn save-sections-order [owner]
  (let [col1-pinned-topics (sel [:div.col-1 :div.topic.draggable-topic])
        col1-pinned-topics-list (vec (for [topic col1-pinned-topics] (.data (js/$ topic) "section")))
        col2-pinned-topics (sel [:div.col-2 :div.topic.draggable-topic])
        col2-pinned-topics-list (vec (for [topic col2-pinned-topics] (.data (js/$ topic) "section")))
        col3-pinned-topics (sel [:div.col-3 :div.topic.draggable-topic])
        col3-pinned-topics-list (vec (for [topic col3-pinned-topics] (.data (js/$ topic) "section")))
        max-count (max (count col1-pinned-topics-list) (count col2-pinned-topics-list) (count col3-pinned-topics-list))
        pinned-topics-list (loop [topics []
                                  idx 0]
                             (if (<= (inc idx) max-count)
                               (recur (vec (remove nil? (conj topics (get col1-pinned-topics-list idx) (get col2-pinned-topics-list idx) (get col3-pinned-topics-list idx))))
                                      (inc idx))
                               topics))
        other-topics (sel [:div.topic.not-draggable-topic])
        other-topics-list (vec (for [topic other-topics] (.data (js/$ topic) "section")))]
    (dispatcher/dispatch! [:new-sections (vec (concat pinned-topics-list other-topics-list))])))

(defn pinned-count
  "Return the count of pinned topics in the current company"
  [data]
  (let [company-data (:company-data data)
        {:keys [pinned]} (utils/get-pinned-other-keys (:sections company-data) company-data)]
    (count pinned)))

;; ----- DnD ---------------------------------------

(defn coord-inside
  "Given the current left and top drag coordinate and a topic return a map saying if the coords
  are over it and if it's on the left or right side"
  [left top topic]
  (if topic
    (when-let [dragging-topic (.data (js/$ ".dragging-topic") "topic")]
      (let [topic-el (js/$ (str ".topic-row[data-topic=" (name topic) "]"))
            topic-pos (.position topic-el)
            topic-posalter (utils/absolute-offset (.get topic-el 0))
            topic-position {:left (gobj/get topic-pos "left")
                            :top (gobj/get topic-pos "top")}
            topic-size {:width (.width topic-el) :height (.height topic-el)}
            target-css {:width (int (+ (:width topic-size) 20)) ; add the margin 6 * 2 and the border 4 * 2 of the topic
                        :height (int (:height topic-size))
                        :left (int (:left topic-position))
                        :top (int (+ (:top topic-position) 84))}]
        (if (and (not= (name topic) dragging-topic)
                   (>= left (:left target-css))
                   (>= top (:top target-css))
                   (< left (+ (:left target-css) (:width target-css)))
                   (< top (+ (:top target-css) (:height target-css))))
          (if (< left (+ (:left target-css) (/ (:width target-css) 2)))
            {:side "left"
             :topic-el topic-el
             :inside? true
             :topic topic}
            {:side "right"
             :topic-el topic-el
             :inside? true
             :topic topic})
          {:topic-el topic-el
           :topic topic
           :inside? false})))
    (sentry/capture-message (str "open-company-web.components.topic-list/coord-inside params, left:" left ", top:" top ", topic:" topic))))

(defn get-topic-at-position
  "Give left and top coordinates of the current drag position, show the yellow bar on left or right of the current hovering topic"
  [owner left top stop?]
  (let [company-data    (:company-data (om/get-props owner))
        company-topics  (vec (map keyword (:sections company-data)))]
    (doseq [topic company-topics]
      (let [in? (coord-inside left top topic)]
        (when (:topic-el in?)
          (.removeClass (:topic-el in?) "left-highlight")
          (.removeClass (:topic-el in?) "right-highlight"))
        (if-not stop?
          (when (:inside? in?)
            (cond
              (= (:side in?) "left")
              (.addClass (:topic-el in?) "left-highlight")
              (= (:side in?) "right")
              (.addClass (:topic-el in?) "right-highlight")))
          ; reorder topics
          (when (:inside? in?)
            (let [dragged-topic (keyword (.data (js/$ ".dragging-topic") "topic"))
                  {:keys [pinned other]} (utils/get-pinned-other-keys (:sections company-data) company-data)
                  pinned-kw (map keyword pinned)
                  other-kw (map keyword other)
                  all-but-dragged (concat (utils/vec-dissoc pinned-kw dragged-topic) other-kw)
                  idx (.indexOf (utils/vec-dissoc pinned-kw dragged-topic) (:topic in?))
                  fixed-idx (if (= (:side in?) "left") idx (inc idx))
                  new-sections (let [[before after] (split-at fixed-idx all-but-dragged)]
                                 (vec (concat before [dragged-topic] after)))]
              (if (>= idx 0)
                ; dropped in a good spot
                (dispatcher/dispatch! [:new-sections new-sections])
                ;dropped in a not good spot, reset the order to the original
                (dispatcher/dispatch! [:new-sections (:sections company-data)])))))))))

(defn inside-position-from-event [e]
  (let [tcc-offset (.offset (js/$ ".topics-column-container"))]
    {:left (gobj/get e "clientX")
     :top (+ (gobj/get e "clientY") (gobj/get (gobj/get js/document "body") "scrollTop"))}))

(defn dragging [owner e stop?]
  (let [inside-pos (inside-position-from-event e)]
    (get-topic-at-position owner (:left inside-pos) (:top inside-pos) stop?))
  (when stop?
    (om/set-state! owner :rerender (rand 4))))

(defn setup-draggable [owner]
  (when-let [list-node (js/$ "div.topic-row.draggable-topic")]
    (when-not (.draggable list-node "instance")
      (.draggable list-node #js {:addClasses true
                                 :drag #(dragging owner % false)
                                 :handle ".topic-dnd-handle"
                                 :scroll true
                                 :start #(do
                                           (.addClass (js/$ (gobj/get % "target")) "dragging-topic")
                                           (.addClass (js/$ (sel1 [:div.topics-columns])) "dnd-active"))
                                 :stop #(do
                                          (dragging owner % true)
                                          (.removeClass (js/$ (sel1 [:div.topics-columns])) "dnd-active")
                                          (.removeClass (js/$ (gobj/get % "target")) "dragging-topic")(dragging owner % false))}))))

(defn destroy-draggable []
  (when-let [list-node (js/$ "div.topic-row.draggable-topic")]
    (when (.draggable list-node "instance")
      (try (.draggable list-node "destroy") (catch :default e (sentry/capture-error e))))))

(defn manage-draggable [owner]
  (when-not (utils/is-test-env?)
    (if (> (pinned-count (om/get-props owner)) 1)
      (do (destroy-draggable) (utils/after 1 #(setup-draggable owner)))
      (destroy-draggable))))

;; -------------------------------------------------

(def card-x-margins 20)
(def columns-layout-padding 20)

(defcomponent topic-list [data owner options]

  (init-state [_]
    (when-not (utils/is-test-env?)
      ;; make sure when topic-list component is initialized that there is no foce active
      (dispatcher/dispatch! [:start-foce nil]))
    (get-state owner data nil))

  (did-mount [_]
    ; scroll to top when the component is initially mounted to
    ; make sure the calculation for the fixed navbar are correct
    (when-not @scrolled-to-top
      (set! (.-scrollTop (.-body js/document)) 0)
      (reset! scrolled-to-top true))
    (when (not (utils/is-test-env?))
      (when-not (responsive/user-agent-mobile?)
        (let [kb-listener (events/listen js/window EventType/KEYDOWN (partial kb-listener owner))]
          (om/set-state! owner :kb-listener kb-listener)))
      (when (utils/can-edit-sections? (:company-data data))
        (manage-draggable owner))))

  (will-unmount [_]
    (when (and (not (utils/is-test-env?))
               (not (responsive/user-agent-mobile?)))
      (events/unlistenByKey (om/get-state owner :kb-listener))))

  (will-receive-props [_ next-props]
    (when-let* [new-topic-foce (om/get-state owner :new-topic-foce)
                new-topic-data (-> next-props :company-data new-topic-foce)]
      (dispatcher/dispatch! [:start-foce new-topic-foce new-topic-data]))
    (when (om/get-state owner :redirect-to-preview)
      (utils/after 100 #(router/nav! (oc-urls/stakeholder-update-preview))))
    (when-not (= (:company-data next-props) (:company-data data))
      (om/set-state! owner (get-state owner next-props (om/get-state owner))))
    (let [company-data            (:company-data next-props)
          topics                  (vec (:sections company-data))
          no-placeholder-sections (utils/filter-placeholder-sections topics company-data)]
      (when (and (:force-edit-topic next-props) (contains? company-data (keyword (:force-edit-topic next-props))))
        (om/set-state! owner :selected-topic (dispatcher/force-edit-topic)))
      ; show second tooltip if needed
      (when (= (count no-placeholder-sections) 1)
        (om/set-state! owner :show-second-add-topic-tooltip true))
      ; show share tooltip if needed
      (when (= (count no-placeholder-sections) 2)
        (om/set-state! owner :show-share-tooltip true))
      (when (= (count (filter #(->> % keyword (get company-data) :pin) no-placeholder-sections)) 2)
        (om/set-state! owner :show-second-pin-tip true))))

  (did-update [_ prev-props _]
    (when-not (utils/is-test-env?)
      (when (om/get-state owner :tr-selected-topic)
        (animate-selected-topic-transition owner (om/get-state owner :animation-direction)))
      (if (not (nil? (:foce-key data)))
        ;; if FoCE is starting we need to destroy the draggable or the medium editor
        ;; will conflict with it
        (destroy-draggable)
        ;; else setup draggable as usuale
        (when (utils/can-edit-sections? (:company-data data))
          (manage-draggable owner)))))

  (render-state [_ {:keys [active-topics
                           selected-topic
                           selected-metric
                           tr-selected-topic
                           transitioning
                           share-selected-topics
                           redirect-to-preview
                           show-add-topic-tip
                           show-second-add-topic-tooltip
                           show-share-tooltip
                           show-second-pin-tip
                           dragging
                           rerender]}]
    (let [company-slug    (router/current-company-slug)
          company-data    (:company-data data)
          company-topics  (vec (map keyword (:sections company-data)))
          card-width      (:card-width data)
          columns-num     (:columns-num data)
          ww              (.-clientWidth (sel1 js/document :body))
          total-width     (case columns-num
                            3 (str (+ (* card-width 3) (* card-x-margins 3) (* columns-layout-padding 2) 1) "px")
                            2 (str (+ (* card-width 2) (* card-x-margins 2) (* columns-layout-padding 2) 1) "px")
                            1 (if (>= ww responsive/c1-min-win-width) (str card-width "px") "auto"))
          can-edit-secs   (utils/can-edit-sections? company-data)]
      (dom/div {:class (utils/class-set {:topic-list true
                                         :group true
                                         :dragging dragging
                                         :editable can-edit-secs})
                :style {:margin-top (if selected-topic "0px" "84px")}
                :data-rerender rerender
                :key (str "topic-list-" rerender)}
        ;; Fullscreen topic
        (when selected-topic
          (dom/div {:class "selected-topic-container"
                    :style #js {:opacity (if selected-topic 1 0)}}
              (dom/div #js {:className "selected-topic"
                            :key (str "transition-" selected-topic)
                            :ref "selected-topic"
                            :style #js {:opacity 1}}
                (om/build fullscreen-topic {:section selected-topic
                                            :section-data (->> selected-topic keyword (get company-data))
                                            :revision-updates (dispatcher/section-revisions company-slug (router/current-section))
                                            :selected-metric selected-metric
                                            :read-only (:read-only company-data)
                                            :card-width card-width
                                            :currency (:currency company-data)
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
                                          :animate false}
                                         {:opts {:close-overlay-cb #(close-overlay-cb owner)
                                                 :topic-navigation #(om/set-state! owner :topic-navigation %)}})))))
        ;; Topics list columns
        (om/build topics-columns {:columns-num columns-num
                                  :card-width card-width
                                  :selected-metric selected-metric
                                  :total-width total-width
                                  :content-loaded (:content-loaded data)
                                  :loading (:loading data)
                                  :topics company-topics
                                  :foce-data-editing? (:foce-data-editing? data)
                                  :new-sections (:new-sections data)
                                  :company-data company-data
                                  :topics-data company-data
                                  :foce-key (:foce-key data)
                                  :foce-data (:foce-data data)
                                  :share-selected-topics share-selected-topics
                                  :show-first-edit-tip (show-first-edit-tip? company-data company-topics)}
                                 {:opts {:topic-click (partial topic-click owner)
                                         :update-active-topics (partial update-active-topics owner)}})
        
        ;; Onboarding tooltips
        
        ;; Desktop only welcom
        (when (and show-add-topic-tip (not selected-topic) (nil? (:show-login-overlay data)))

          (onboard-tip
            {:id (str "welcome-" company-slug "-desktop")
             :once-only false
             :mobile false
             :desktop (str "Hi " (jwt/get-key :name) ", welcome to OpenCompany! Choose a topic to get started.")}))

        ;; Mobile only welcome
        (when (and show-add-topic-tip (nil? (:show-login-overlay data)))
          (onboard-tip
            {:id (str "welcome-" company-slug "-mobile")
             :once-only false
             :mobile (str "Hi " (jwt/get-key :name) ", your dashboard can be viewed after it's been created on a desktop browser.")
             :desktop false}))
        
        ;; After 1st topic
        (when (and show-second-add-topic-tooltip (not selected-topic))                   
          (onboard-tip
            {:id (str "first-topic-" company-slug)
             :once-only true
             :mobile false
             :desktop "Add another topic and you'll see how quickly the big picture comes together."}))

        ;; After 2nd topic
        (when (and show-share-tooltip (not selected-topic))
          (onboard-tip
            {:id (str "second-topic-" company-slug)
             :once-only true
             :mobile false
             :desktop "It's easy to share information with your employees, investors and customers. Click on \"SHARE AN UPDATE\" above to try it."
             :css-class "large"}))

        ;; After 2nd topic is pinned show tooltip
        (when (and show-second-pin-tip (not selected-topic))
          (onboard-tip
            {:id (str "second-pin-" company-slug)
             :once-only true
             :mobile false
             :desktop "YOU CAN DRAG AND DROP PINNED ITEMS TO REORDER THEM"}))))))