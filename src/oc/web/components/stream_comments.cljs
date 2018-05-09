(ns oc.web.components.stream-comments
  (:require [rum.core :as rum]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.lib.utils :as utils]
            [oc.web.utils.comment :as cu]
            [oc.web.actions.comment :as comment-actions]
            [oc.web.components.ui.alert-modal :as alert-modal]))

(defn stop-editing [s]
  (let [medium-editor @(::medium-editor s)]
    (.destroy medium-editor)
    (when @(::esc-key-listener s)
      (events/unlistenByKey @(::esc-key-listener s))
      (reset! (::esc-key-listener s) nil))
    (reset! (::medium-editor s) nil)
    (reset! (::editing? s) false)))

(defn cancel-edit
  [e s c]
  (.stopPropagation e)
  (let [comment-field (rum/ref-node s (str "comment-body-" (:uuid c)))]
    (set! (.-innerHTML comment-field) (:body c))
    (stop-editing s)))

(defn edit-finished
  [e s c]
  (let [activity-data (first (:rum/args s))
        new-comment (rum/ref-node s (str "comment-body-" (:uuid c)))
        comment-text (cu/add-comment-content new-comment)]
    (if (pos? (count comment-text))
      (do
        (stop-editing s)
        (set! (.-innerHTML new-comment) comment-text)
        (comment-actions/save-comment activity-data c comment-text))
      (cancel-edit e s c))))

(defn start-editing [s comment-data]
  (let [comment-node (rum/ref-node s (str "comment-body-" (:uuid comment-data)))
        medium-editor (cu/setup-medium-editor comment-node)]
    (reset! (::esc-key-listener s)
     (events/listen
      js/window
      EventType/KEYDOWN
      (fn [e]
        (when (and (= "Enter" (.-key e))
                   (not (.-shiftKey e)))
          (edit-finished e s comment-data)
          (.preventDefault e))
        (when (= "Escape" (.-key e))
          (cancel-edit e s comment-data)))))
    (reset! (::medium-editor s) medium-editor)
    (reset! (::editing? s) (:uuid comment-data))
    (utils/after 600 #(utils/to-end-of-content-editable (rum/ref-node s (str "comment-body-" (:uuid comment-data)))))))

(defn delete-clicked [e activity-data comment-data]
  (let [alert-data {:icon "/img/ML/trash.svg"
                    :action "delete-comment"
                    :message (str "Delete this comment?")
                    :link-button-title "No"
                    :link-button-cb #(alert-modal/hide-alert)
                    :solid-button-style :red
                    :solid-button-title "Yes"
                    :solid-button-cb (fn []
                                       (comment-actions/delete-comment activity-data comment-data)
                                       (alert-modal/hide-alert))
                    }]
    (alert-modal/show-alert alert-data)))

(defn scroll-to-bottom [s]
  (let [scrolling-node (rum/ref-node s "stream-comments-inner")]
    (set! (.-scrollTop scrolling-node) (.-scrollHeight scrolling-node))))

(rum/defcs stream-comments < rum/reactive
                             (drv/drv :add-comment-focus)
                             (rum/local false ::bottom-gradient)
                             (rum/local false ::last-focused-state)
                             (rum/local false ::showing-menu)
                             (rum/local nil ::click-listener)
                             (rum/local false ::editing?)
                             (rum/local false ::medium-editor)
                             (rum/local nil ::esc-key-listener)
                             {:will-mount (fn [s]
                               (reset! (::click-listener s)
                                 (events/listen js/window EventType/CLICK
                                  #(when (and @(::showing-menu s)
                                              (not (utils/event-inside? %
                                                    (rum/ref-node s (str "comment-more-menu-" @(::showing-menu s))))))
                                     (reset! (::showing-menu s) false))))
                               s)
                              :after-render (fn [s]
                               (let [scrolling-node (rum/ref-node s "stream-comments-inner")
                                     scrolls (> (.-scrollHeight scrolling-node) (.-clientHeight scrolling-node))]
                                 (compare-and-set! (::bottom-gradient s) (not scrolls) scrolls))
                               (let [activity-uuid (:uuid (first (:rum/args s)))
                                     focused-uuid @(drv/get-ref s :add-comment-focus)
                                     current-local-state @(::last-focused-state s)
                                     is-self-focused? (= focused-uuid activity-uuid)]
                                  (when (not= current-local-state is-self-focused?)
                                    (reset! (::last-focused-state s) is-self-focused?)
                                    (when is-self-focused?
                                      (scroll-to-bottom s))))
                               s)
                              :will-unmount (fn [s]
                               (when @(::click-listener s)
                                 (events/unlistenByKey @(::click-listener s))
                                 (reset! (::click-listener s) nil))
                               s)}
  [s activity-data comments-data]
  [:div.stream-comments
    {:class (utils/class-set {:bottom-gradient @(::bottom-gradient s)})}
    [:div.stream-comments-inner
      {:ref "stream-comments-inner"}
      (when (pos? (count comments-data))
        [:div.stream-comments-title
          (str (count comments-data) " Comment" (when (> (count comments-data) 1) "s"))])
      (if (pos? (count comments-data))
        (for [comment-data comments-data
              :let [is-editing? (= @(::editing? s) (:uuid comment-data))
                    reaction-data (first (:reactions comment-data))]]
          [:div.stream-comment
            {:key (str "stream-comment-" (:created-at comment-data))}
            [:div.stream-comment-header.group.fs-hide
              [:div.stream-comment-author-avatar
                {:style {:background-image (str "url(" (:avatar-url (:author comment-data)) ")")}}]
              [:div.stream-comment-author-right
                [:div.stream-comment-author-name
                  (:name (:author comment-data))]
                [:div.stream-comment-author-timestamp
                  (utils/time-since (:created-at comment-data))]]
              (when (or (and (:can-edit comment-data)
                             (not (:is-emoji comment-data)))
                        (:can-delete comment-data))
                (let [showing-more-menu (= @(::showing-menu s) (:uuid comment-data))]
                  [:div.stream-comment-more-menu-container
                    {:ref (str "comment-more-menu-" (:uuid comment-data))}
                    [:button.comment-more-menu.mlb-reset
                      {:class (when showing-more-menu "active")
                       :on-click (fn [e]
                                  (utils/event-stop e)
                                  (reset! (::showing-menu s) (:uuid comment-data)))}]
                    (when showing-more-menu
                      [:div.stream-comment-more-menu
                        (when (and (:can-edit comment-data)
                                   (not (:is-emoji comment-data)))
                          [:div.stream-comment-more-menu-item.edit
                            {:on-click #(do
                                          (reset! (::showing-menu s) false)
                                          (start-editing s comment-data))}
                            "Edit"])
                        (when (:can-delete comment-data)
                          [:div.stream-comment-more-menu-item.delete
                            {:on-click #(do
                                         (reset! (::showing-menu s) false)
                                         (delete-clicked % activity-data comment-data))}
                            "Delete"])])]))]
            [:div.stream-comment-content
              [:div.stream-comment-body.fs-hide
                {:dangerouslySetInnerHTML (utils/emojify (:body comment-data))
                 :ref (str "comment-body-" (:uuid comment-data))
                 :class (utils/class-set {:emoji-comment (:is-emoji comment-data)})}]]
            (when (or is-editing?
                      (and (not is-editing?)
                           (not (:is-emoji comment-data))
                           (or (:can-react comment-data)
                               (pos? (:count reaction-data)))))
              [:div.stream-comment-footer.group
                (when (and (not is-editing?)
                           (not (:is-emoji comment-data))
                           (or (:can-react comment-data)
                               (pos? (:count reaction-data))))
                  [:div.stream-comment-reaction
                    {:class (utils/class-set {:reacted (:reacted reaction-data)
                                              :can-react (:can-react comment-data)})}
                      (when (or (pos? (:count reaction-data))
                                (:can-react comment-data))
                        [:div.stream-comment-reaction-icon
                          {:on-click #(comment-actions/comment-reaction-toggle activity-data comment-data
                            reaction-data (not (:reacted reaction-data)))}])
                      (when (pos? (:count reaction-data))
                        [:div.stream-comment-reaction-count
                          (:count reaction-data)])])
                  (when is-editing?
                    [:div.save-cancel-edit-buttons
                      [:button.mlb-reset.mlb-link-green
                        {:on-click #(edit-finished % s comment-data)}
                        "Save"]
                      [:button.mlb-reset.mlb-link-black
                        {:on-click #(cancel-edit % s comment-data)}
                        "Cancel"]])])])
        [:div.stream-comments-empty])]])