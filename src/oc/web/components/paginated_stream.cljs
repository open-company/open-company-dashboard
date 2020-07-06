(ns oc.web.components.paginated-stream
  (:require [rum.core :as rum]
            [dommy.core :as dommy :refer-macros (sel1)]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.react-utils :as rutils]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.mixins.ui :as mixins]
            [oc.web.utils.dom :as dom-utils]
            [oc.web.mixins.seen :as seen-mixins]
            [oc.web.lib.responsive :as responsive]
            [oc.web.utils.activity :as activity-utils]
            [oc.web.mixins.section :as section-mixins]
            [oc.web.actions.section :as section-actions]
            [oc.web.actions.activity :as activity-actions]
            [oc.web.components.stream-item :refer (stream-item)]
            [oc.web.components.replies-list :refer (replies-list)]
            [oc.web.actions.contributions :as contributions-actions]
            [oc.web.components.ui.all-caught-up :refer (caught-up-line)]
            [oc.web.components.ui.refresh-button :refer (refresh-button)]
            [oc.web.components.stream-collapsed-item :refer (stream-collapsed-item)]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            cljsjs.react-virtualized))

(def virtualized-list (partial rutils/build js/ReactVirtualized.List))
(def window-scroller (partial rutils/build js/ReactVirtualized.WindowScroller))

;; 800px from the end of the current rendered results as point to add more items in the batch
(def scroll-card-threshold 1)
(def scroll-card-threshold-collapsed 5)
(def collapsed-foc-height 56)
(def closing-item-height 60)
(def foc-height 204)
(def mobile-foc-height 166)
(def foc-separators-height 58)
(def caught-up-line-height 64)

(defn- calc-card-height [mobile? foc-layout]
  (cond
    mobile?
    mobile-foc-height
    (= foc-layout dis/other-foc-layout)
    collapsed-foc-height
    :else
    foc-height))

(rum/defc wrapped-stream-item < rum/static
  [{:keys [style] :as row-props}
   {:keys [entry
           read-data
           org-data
           comments-data
           editable-boards
           foc-layout
           is-mobile] :as props}]
  (let [member? (:member? org-data)
        publisher? (:publisher? entry)
        show-wrt? member?
        collapsed-item? (and (= foc-layout dis/other-foc-layout)
                             (not is-mobile))]
   [:div.virtualized-list-row
     {:class (utils/class-set {:collapsed-item collapsed-item?
                               :open-item (:open-item entry)
                               :close-item (:close-item entry)})
      :style style}
     (if collapsed-item?
       (stream-collapsed-item {:activity-data entry
                               :comments-data comments-data
                               :read-data read-data
                               :show-wrt? show-wrt?
                               :member? member?
                               :editable-boards editable-boards})
       (stream-item {:activity-data entry
                     :comments-data comments-data
                     :read-data read-data
                     :show-wrt? show-wrt?
                     :member? member?
                     :publisher? publisher?
                     :editable-boards editable-boards
                     :boards-count (count (filter #(not= (:slug %) utils/default-drafts-board-slug) (:boards org-data)))}))]))

(rum/defc load-more < rum/static
  [{:keys [style item]}]
  [:div.loading-updates.bottom-loading
    {:style style}
    (:message item)])

(rum/defc closing-item < rum/static
  [{:keys [style item]}]
  [:div.closing-item
    {:style style}
    (:message item)])

(rum/defc separator-item < rum/static
  [{:keys [style foc-layout] :as row-props} {:keys [label] :as props}]
  [:div.virtualized-list-separator
    {:style style
     :class (when (= foc-layout dis/default-foc-layout) "expanded-list")}
    label])

(rum/defc caught-up-wrapper < rum/static
  [{:keys [style item]}]
  [:div.caught-up-wrapper
    {:style style}
    (caught-up-line item)])

(rum/defcs virtualized-stream < rum/static
                                rum/reactive
                                (rum/local nil ::last-force-list-update)
                                (rum/local false ::mounted)
                                (seen-mixins/container-nav-mixin)
                                {:will-mount (fn [s]
                                   (reset! (::last-force-list-update s) (-> s :rum/args first :force-list-update))
                                   s)
                                 :did-mount (fn [s]
                                   (reset! (::mounted s) true)
                                   s)
                                 :did-remount (fn [o s]
                                   (when @(::mounted s)
                                     (when-let [force-list-update (-> s :rum/args first :force-list-update)]
                                       (when-not (= @(::last-force-list-update s) force-list-update)
                                         (reset! (::last-force-list-update s) force-list-update)
                                         (utils/after 180
                                          #(when @(::mounted s)
                                             (.recomputeRowHeights (rum/ref s :virtualized-list-comp)))))))
                                   s)
                                 :will-unmount (fn [s]
                                   (reset! (::mounted s) false)
                                   s)}
  [s {:keys [items
             activities-read
             foc-layout
             is-mobile?
             force-list-update
             container-data
             following-badge]
      :as derivatives}
     virtualized-props]
  (let [{:keys [height
                isScrolling
                onChildScroll
                scrollTop
                registerChild]} (js->clj virtualized-props :keywordize-keys true)
        key-prefix (if is-mobile? "mobile" foc-layout)
        rowHeight (fn [row-props]
                    (let [{:keys [index]} (js->clj row-props :keywordize-keys true)
                          litems (-> s :rum/args first :items)
                          item (get litems index)]
                      (case (:resource-type item)
                        :caught-up
                        caught-up-line-height
                        :separator
                        (if (= foc-layout dis/other-foc-layout)
                          foc-separators-height
                          (- foc-separators-height 8))
                        :loading-more
                        (if is-mobile? 44 60)
                        :closing-item
                        closing-item-height
                        ; else
                        (calc-card-height is-mobile? foc-layout))))
        row-renderer (fn [row-props]
                       (let [{:keys [key
                                     index
                                     isScrolling
                                     isVisible
                                     style] :as row-props} (js->clj row-props :keywordize-keys true)
                             litems (-> s :rum/args first :items)
                             item (get litems index)
                             read-data (when (= (:resource-type item) :entry)
                                         (get activities-read (:uuid item)))
                             row-key (str key-prefix "-" key)
                             next-item (get litems (inc index))
                             prev-item (get litems (dec index))]
                         (case (:resource-type item)
                           :caught-up
                           (rum/with-key (caught-up-wrapper {:item item :style style}) (str "caught-up-" (:last-activity-at item)))
                           :closing-item
                           (rum/with-key (closing-item {:item item :style style}) (str "closing-item-" row-key))
                           :loading-more
                           (rum/with-key (load-more {:item item :style style}) (str "loading-more-" row-key))
                           :separator
                           (rum/with-key (separator-item (assoc row-props :foc-layout foc-layout) item) (str "separator-item-" row-key))
                           ; else
                           (rum/with-key
                            (wrapped-stream-item row-props (merge derivatives
                                                                 {:entry item
                                                                  :read-data read-data
                                                                  :foc-layout foc-layout
                                                                  :is-mobile is-mobile?}))
                            row-key))))]
    [:div.virtualized-list-container
      {:ref registerChild
       :key (str "virtualized-list-" key-prefix)}
      (virtualized-list {:autoHeight true
                         :ref :virtualized-list-comp
                         :height height
                         :width (if is-mobile?
                                  js/window.innerWidth
                                  620)
                         :isScrolling isScrolling
                         :onScroll onChildScroll
                         :rowCount (count items)
                         :rowHeight rowHeight
                         :rowRenderer row-renderer
                         :scrollTop scrollTop
                         :overscanRowCount 20
                         :style {:outline "none"}})
      (when (and (= (keyword (:container-slug container-data)) :following)
                 following-badge)
        (refresh-button {:message "New updates available"}))]))

(defonce last-scroll-top (atom 0))

(defn did-scroll
  "Scroll listener, load more activities when the scroll is close to a margin."
  [s e]
  (let [scroll-top (or (.-pageYOffset js/window) (.. js/document -scrollingElement -scrollTop))
        direction (if (> @last-scroll-top scroll-top)
                    :up
                    (if (< @last-scroll-top scroll-top)
                      :down
                      :stale))
        max-scroll (- (.-scrollHeight (.-scrollingElement js/document)) (.-innerHeight js/window))
        card-height (calc-card-height (responsive/is-mobile-size?) @(drv/get-ref s :foc-layout))
        scroll-threshold (if (= card-height collapsed-foc-height) scroll-card-threshold-collapsed scroll-card-threshold)
        current-board-slug (router/current-board-slug)]
    ;; scrolling down
    (when (and ;; not already loading more
               (not @(::bottom-loading s))
               ;; has a link to load more that can be used
               @(::has-next s)
               ;; scroll is moving down
               (or (= direction :down)
                   (= direction :stale))
               ;; and the threshold point has been reached
               (>= scroll-top (- max-scroll (* scroll-threshold card-height))))
      ;; Show a spinner at the bottom
      (reset! (::bottom-loading s) true)
      ;; if the user is close to the bottom margin, load more results if there is a link
      (cond
        (= current-board-slug "replies")
        (activity-actions/replies-more @(::has-next s) :down)
        (= current-board-slug "following")
        (activity-actions/following-more @(::has-next s) :down)
        (seq (router/current-contributions-id))
        (contributions-actions/contributions-more @(::has-next s) :down)
        (= current-board-slug "inbox")
        (activity-actions/inbox-more @(::has-next s) :down)
        (= current-board-slug "all-posts")
        (activity-actions/all-posts-more @(::has-next s) :down)
        (= current-board-slug "bookmarks")
        (activity-actions/bookmarks-more @(::has-next s) :down)
        (= current-board-slug "unfollowing")
        (activity-actions/unfollowing-more @(::has-next s) :down)
        (not (dis/is-container? current-board-slug))
        (section-actions/section-more @(::has-next s) :down)))
    ;; Save the last scrollTop value
    (reset! last-scroll-top (max 0 scroll-top))))

(defn check-pagination [s]
  (let [container-data @(drv/get-ref s :container-data)
        next-link (utils/link-for (:links container-data) "next")]
    (reset! (::has-next s) next-link)
    (did-scroll s nil)))

(rum/defcs paginated-stream  <
                        rum/static
                        rum/reactive
                        ;; Derivatives
                        (drv/drv :org-data)
                        (drv/drv :items-to-render)
                        (drv/drv :container-data)
                        (drv/drv :activities-read)
                        (drv/drv :comments-data)
                        (drv/drv :editable-boards)
                        (drv/drv :foc-layout)
                        (drv/drv :current-user-data)
                        (drv/drv :force-list-update)
                        (drv/drv :following-badge)
                        ;; Locals
                        (rum/local nil ::scroll-listener)
                        (rum/local false ::has-next)
                        (rum/local nil ::bottom-loading)
                        (rum/local nil ::last-foc-layout)
                        ;; Mixins
                        mixins/first-render-mixin
                        section-mixins/container-nav-in
                        ; section-mixins/window-focus-auto-loader

                        {:will-mount (fn [s]
                          (reset! (::last-foc-layout s) @(drv/get-ref s :foc-layout))
                          (reset! last-scroll-top (.. js/document -scrollingElement -scrollTop))
                          (reset! (::scroll-listener s)
                           (events/listen js/window EventType/SCROLL #(did-scroll s %)))
                          (check-pagination s)
                          s)
                         :did-remount (fn [_ s]
                          (check-pagination s)
                         s)
                         :did-mount (fn [s]
                          (reset! last-scroll-top (.. js/document -scrollingElement -scrollTop))
                          (check-pagination s)
                          s)
                         :before-render (fn [s]
                          (let [container-data @(drv/get-ref s :container-data)]
                            (when (and (not (:loading-more container-data))
                                       @(::bottom-loading s))
                              (reset! (::bottom-loading s) false)
                              (check-pagination s)))
                          s)
                         :after-render (fn [s]
                          (let [foc-layout @(drv/get-ref s :foc-layout)]
                            (when (not= @(::last-foc-layout s) foc-layout)
                              (reset! (::last-foc-layout s) foc-layout)
                              (check-pagination s)))
                          s)
                         :will-unmount (fn [s]
                          (when @(::scroll-listener s)
                            (events/unlistenByKey @(::scroll-listener s)))
                          s)}
  [s]
  (let [org-data (drv/react s :org-data)
        comments-data (drv/react s :comments-data)
        editable-boards (drv/react s :editable-boards)
        container-data (drv/react s :container-data)
        items (drv/react s :items-to-render)
        activities-read (drv/react s :activities-read)
        foc-layout (drv/react s :foc-layout)
        current-user-data (drv/react s :current-user-data)
        force-list-update (drv/react s :force-list-update)
        viewport-height (dom-utils/viewport-height)
        is-mobile? (responsive/is-mobile-size?)
        card-height (calc-card-height is-mobile? foc-layout)
        member? (:member? org-data)
        following-badge (drv/react s :following-badge)]
    [:div.paginated-stream.group
      [:div.paginated-stream-cards
        [:div.paginated-stream-cards-inner.group
         (if (:no-virtualized-steam container-data)
           (replies-list {:items-to-render items
                          :org-data org-data
                          :container-data container-data
                          :force-list-update force-list-update
                          :current-user-data current-user-data})
           (window-scroller
            {}
            (partial virtualized-stream {:org-data org-data
                                         :comments-data comments-data
                                         :items items
                                         :container-data container-data
                                         :is-mobile? is-mobile?
                                         :force-list-update force-list-update
                                         :activities-read activities-read
                                         :editable-boards editable-boards
                                         :foc-layout foc-layout
                                         :following-badge following-badge})))]]]))
