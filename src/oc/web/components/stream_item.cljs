(ns oc.web.components.stream-item
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [dommy.core :refer-macros (sel1)]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [oc.web.lib.jwt :as jwt]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.local-settings :as ls]
            [oc.web.utils.activity :as au]
            [oc.web.mixins.activity :as am]
            [oc.web.mixins.ui :as ui-mixins]
            [oc.web.actions.nux :as nux-actions]
            [oc.web.utils.draft :as draft-utils]
            [oc.web.lib.responsive :as responsive]
            [oc.web.components.ui.wrt :refer (wrt)]
            [oc.web.mixins.mention :as mention-mixins]
            [oc.web.actions.comment :as comment-actions]
            [oc.web.actions.activity :as activity-actions]
            [oc.web.components.reactions :refer (reactions)]
            [oc.web.components.ui.more-menu :refer (more-menu)]
            [oc.web.components.ui.add-comment :refer (add-comment)]
            [oc.web.components.stream-comments :refer (stream-comments)]
            [oc.web.components.ui.user-avatar :refer (user-avatar-image)]
            [oc.web.components.ui.comments-summary :refer (comments-summary)]
            [oc.web.components.ui.stream-attachments :refer (stream-attachments)]
            [oc.web.components.ui.ziggeo :refer (ziggeo-player)]))

(defn expand [s expand? & [scroll-to-comments?]]
  (reset! (::expanded s) expand?)
  (when (and expand?
             scroll-to-comments?)
    (reset! (::should-scroll-to-comments s) true))
  (when-not expand?
    (utils/after 150 #(utils/scroll-to-y (- (.-top (.offset (js/$ (rum/dom-node s)))) 70) 0)))
  (when expand?
    ;; When expanding a post send the WRT read
    (activity-actions/send-item-read (:uuid (first (:rum/args s))))))

(defn should-show-continue-reading? [s]
  (let [activity-data (first (:rum/args s))
        $item-body (js/$ (rum/ref-node s "activity-body"))
        comments-data (au/get-comments activity-data @(drv/get-ref s :comments-data))]
    (when (or (.hasClass $item-body "ddd-truncated")
              (> (count (:attachments activity-data)) 3)
              (pos? (count comments-data))
              (:body-has-images activity-data)
              (:fixed-video-id activity-data))
      (reset! (::truncated s) true))
    (reset! (::item-ready s) true)))

(defn win-width []
  (or (.-clientWidth (.-documentElement js/document))
      (.-innerWidth js/window)))

(defn calc-video-height [s]
  (when (responsive/is-tablet-or-mobile?)
    (reset! (::mobile-video-height s) (utils/calc-video-height (win-width)))))

(rum/defcs stream-item < rum/reactive
                         ;; Derivatives
                         (drv/drv :org-data)
                         (drv/drv :add-comment-focus)
                         (drv/drv :comments-data)
                         ;; Locals
                         (rum/local false ::expanded)
                         (rum/local false ::truncated)
                         (rum/local false ::item-ready)
                         (rum/local false ::should-scroll-to-comments)
                         (rum/local false ::more-menu-open)
                         (rum/local false ::hovering-tile)
                         (rum/local 0 ::mobile-video-height)
                         ;; Mixins
                         (ui-mixins/render-on-resize calc-video-height)
                         (am/truncate-element-mixin "activity-body" (* 30 3))
                         am/truncate-comments-mixin
                         (mention-mixins/oc-mentions-hover)
                         {:will-mount (fn [s]
                           (calc-video-height s)
                           (let [single-post-view (boolean (seq (router/current-activity-id)))]
                             (reset! (::expanded s) single-post-view))
                           s)
                          :did-mount (fn [s]
                           (should-show-continue-reading? s)
                           (let [activity-uuid (:uuid (first (:rum/args s)))]
                             (when (= (router/current-activity-id) activity-uuid)
                               (activity-actions/send-item-read activity-uuid)))
                           s)
                          :after-render (fn [s]
                           (let [activity-data (first (:rum/args s))
                                 comments-data @(drv/get-ref s :comments-data)]
                             (comment-actions/get-comments-if-needed activity-data comments-data)
                             (when @(::should-scroll-to-comments s)
                               (utils/after 180
                                #(let [actual-comments-count (count (au/get-comments activity-data comments-data))
                                       dom-node (rum/dom-node s)]
                                  ;; Commet out the scroll to comments for the moment
                                  ; (utils/scroll-to-y
                                  ;  (- (.-top (.offset (js/$ (rum/ref-node s "stream-item-reactions")))) 30 (when (zero? actual-comments-count) 40)))
                                  (when (zero? actual-comments-count)
                                    (.focus (.find (js/$ dom-node) "div.add-comment")))))
                               (reset! (::should-scroll-to-comments s) false)))
                           s)}
  [s activity-data read-data]
  (let [single-post-view (boolean (seq (router/current-activity-id)))
        org-data (drv/react s :org-data)
        is-mobile? (responsive/is-tablet-or-mobile?)
        truncated? @(::truncated s)
        expanded? @(::expanded s)
        ;; Fallback to the activity inline comments if we didn't load
        ;; the full comments just yet
        comments-drv (drv/react s :comments-data)
        comments-data (au/get-comments activity-data comments-drv)
        activity-attachments (:attachments activity-data)
        is-drafts-board (= (router/current-board-slug) utils/default-drafts-board-slug)
        is-all-posts (or (:from-all-posts @router/path)
                         (= (router/current-board-slug) "all-posts"))
        is-must-see (= (router/current-board-slug) "must-see")
        dom-element-id (str "stream-item-" (:uuid activity-data))
        publisher (if is-drafts-board
                    (first (:author activity-data))
                    (:publisher activity-data))
        is-publisher? (= (:user-id publisher) (jwt/user-id))
        dom-node-class (str "stream-item-" (:uuid activity-data))
        has-video (seq (:fixed-video-id activity-data))
        uploading-video (dis/uploading-video-data (:video-id activity-data))
        video-player-show (and is-publisher? uploading-video)
        video-size (when has-video
                     (if is-mobile?
                       {:width (win-width)
                        :height @(::mobile-video-height s)}
                       {:width (if expanded? 638 136)
                        :height (if expanded? (utils/calc-video-height 638) (utils/calc-video-height 136))}))
        user-is-part-of-the-team (jwt/user-is-part-of-the-team (:team-id org-data))]
    [:div.stream-item
      {:class (utils/class-set {dom-node-class true
                                :show-continue-reading truncated?
                                :draft is-drafts-board
                                :must-see-item (:must-see activity-data)
                                :new-item (:new activity-data)
                                :single-post-view single-post-view})
       :on-mouse-enter #(reset! (::hovering-tile s) true)
       :on-mouse-leave #(reset! (::hovering-tile s) false)
       :id dom-element-id}
      [:div.activity-share-container]
      [:div.stream-item-header.group
        [:div.stream-header-head-author
          (user-avatar-image publisher)
          [:div.name.fs-hide
            (str
             (:name publisher)
             " in "
             (:board-name activity-data))
            [:div.must-see-tag "Must see"]
            [:div.new-tag "NEW"]]
          [:div.time-since
            (let [t (or (:published-at activity-data) (:created-at activity-data))]
              [:time
                {:date-time t
                 :data-toggle (when-not is-mobile? "tooltip")
                 :data-placement "top"
                 :data-delay "{\"show\":\"1000\", \"hide\":\"0\"}"
                 :data-title (utils/activity-date-tooltip activity-data)}
                (utils/time-since t)])]
          (when user-is-part-of-the-team
            [:div.separator])
          (when user-is-part-of-the-team
            [:div.stream-item-wrt
              (wrt activity-data read-data)])]
        (when (and (not is-drafts-board)
                   (or @(::hovering-tile s)
                       @(::more-menu-open s)
                       is-mobile?))
          (more-menu activity-data dom-element-id
           {:will-open #(reset! (::more-menu-open s) true)
            :will-close #(reset! (::more-menu-open s) false)
            :external-share (not is-mobile?)}))]
      [:div.stream-item-body-ext.group
        {:class (when expanded? "expanded")}
        [:div.thumbnail-container.group
          {:on-click #(when (and ;; it's truncated
                                 truncated?
                                 ;; it's not already expanded
                                 (not expanded?)
                                 ;; click is not on a Ziggeo video to play it inline
                                 (not (utils/event-inside? % (rum/ref-node s :ziggeo-player))))
                       (expand s true))}
          (if has-video
            [:div.group
             {:key (str "ziggeo-player-" (:fixed-video-id activity-data) "-" (if expanded? "exp" ""))
              :ref :ziggeo-player}
             (ziggeo-player {:video-id (:fixed-video-id activity-data)
                             :width (:width video-size)
                             :height (:height video-size)
                             :lazy (not video-player-show)
                             :video-image (:video-image activity-data)
                             :video-processed (:video-processed activity-data)
                             :playing-cb #(activity-actions/send-item-read (:uuid activity-data))})]
            (when (:body-thumbnail activity-data)
              [:div.body-thumbnail
                {:class (:type (:body-thumbnail activity-data))
                 :data-image (:thumbnail (:body-thumbnail activity-data))
                 :style {:background-image (str "url(\"" (:thumbnail (:body-thumbnail activity-data)) "\")")}}]))
          [:div.stream-body-left.group.fs-hide
            {:class (utils/class-set {:has-thumbnail (and (:has-thumbnail activity-data) (not expanded?))
                                      :has-video (:fixed-video-id activity-data)})}
            [:div.stream-item-headline.ap-seen-item-headline
              {:ref "activity-headline"
               :data-itemuuid (:uuid activity-data)
               :dangerouslySetInnerHTML (utils/emojify (:headline activity-data))}]
            [:div.stream-item-body-container
              [:div.stream-item-body
                {:class (utils/class-set {:expanded expanded?
                                          :wrt-item-ready @(::item-ready s)})}
                [:div.stream-item-body-inner.to-truncate.oc-mentions.oc-mentions-hover
                  {:ref "activity-body"
                   :data-itemuuid (:uuid activity-data)
                   :class (utils/class-set {:hide-images (and truncated? (not expanded?))
                                            :wrt-truncated truncated?
                                            :wrt-expanded expanded?})
                   :dangerouslySetInnerHTML (utils/emojify (:stream-view-body activity-data))}]
                [:div.stream-item-body-inner.no-truncate.oc-mentions.oc-mentions-hover
                  {:ref "full-activity-body"
                   :data-itemuuid (:uuid activity-data)
                   :class (utils/class-set {:wrt-truncated truncated?
                                            :wrt-expanded expanded?})
                   :dangerouslySetInnerHTML (utils/emojify (:body activity-data))}]]]]
          (when (and ls/oc-enable-transcriptions
                     expanded?
                     (:video-transcript activity-data))
            [:div.stream-item-transcript
              [:div.stream-item-transcript-header
                "This transcript was automatically generated and may not be accurate"]
              [:div.stream-item-transcript-content
                (:video-transcript activity-data)]])]
          (when (or (not is-mobile?) expanded?)
            (stream-attachments activity-attachments
             (when (and truncated? (not expanded?))
               #(expand s true))))
          (if is-drafts-board
            [:div.stream-item-footer.group
              [:div.stream-body-draft-edit
                [:button.mlb-reset.edit-draft-bt
                  {:on-click #(do
                               (nux-actions/maybe-dismiss-draft-post-tooltip activity-data)
                               (activity-actions/activity-edit activity-data))}
                  "Continue editing"]]
              [:div.stream-body-draft-delete
                [:button.mlb-reset.delete-draft-bt
                  {:on-click #(draft-utils/delete-draft-clicked activity-data %)}
                  "Delete draft"]]]
            [:div.stream-item-footer.group
              {:ref "stream-item-reactions"}
              [:button.mlb-reset.expand-button
                {:class (when expanded? "expanded")
                 :ref :expand-button
                 :on-click #(expand s (not expanded?))}
                (if expanded?
                  "Show less"
                  "Show more")]
              (when-not is-mobile?
                (reactions activity-data))
              (if is-mobile?
                (if expanded?
                  (reactions activity-data)
                  [:div.mobile-summary.group
                    {:on-click #(expand s true)}
                    [:div.group
                      [:div.mobile-comments-summary.group
                        [:div.mobile-comments-summary-icon]
                        [:span
                          (if (zero? (count comments-data))
                            "Add a comment"
                            (count comments-data))]]
                      (let [max-reaction (first (sort-by :count (:reactions activity-data)))]
                        (when (pos? (:count max-reaction))
                          [:div.mobile-summary-reaction
                            [:span.reaction
                              (:reaction max-reaction)]
                            [:span.count
                              (:count max-reaction)]]))]
                    (when (pos? (count (:attachments activity-data)))
                      [:div.mobile-summary-attachments
                        [:span.attachments-icon]
                        [:span.attachments-count (count (:attachments activity-data))]])])
                [:div.stream-item-comments-summary
                  {:on-click #(expand s true true)}
                  (comments-summary activity-data true)])])]
        (when (and expanded?
                   (:has-comments activity-data))
          [:div.stream-body-right
            [:div.stream-body-comments
              {:class (when (drv/react s :add-comment-focus) "add-comment-expanded")}
              (when (pos? (count comments-data))
                [:div.stream-comments-title.mobile-only
                  (str (count comments-data) " Comment" (when (not= (count comments-data) 1) "s"))])
              (when (:can-comment activity-data)
                (rum/with-key (add-comment activity-data) (str "add-comment-" (:uuid activity-data))))
              (stream-comments activity-data comments-data true)]])]))