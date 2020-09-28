(ns oc.web.components.user-notifications
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.lib.utils :as utils]
            [oc.web.utils.dom :as dom-utils]
            [oc.web.mixins.ui :as ui-mixins]
            [oc.web.utils.ui :refer (ui-compose)]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.user :as user-actions]
            [oc.web.actions.notifications :as notif-actions]
            [oc.web.actions.nav-sidebar :as nav-actions]
            [oc.web.components.ui.all-caught-up :refer (all-caught-up)]
            [oc.web.components.ui.user-avatar :refer (user-avatar-image)]))

(defn has-new-content? [notifications-data]
  (some :unread notifications-data))

(defn- close-tray [s]
  (reset! (::tray-open s) false)
  (user-actions/read-notifications))

(rum/defc user-notification-item < rum/static
  [{entry-uuid        :uuid
    board-slug        :board-slug
    reminder?         :reminder?
    reminder          :reminder
    notification-type :notification-type
    created-at        :created-at
    :as n}]
  [:div.user-notification.group
   {:class    (utils/class-set {:unread (:unread n)})
    :on-click (fn [e]
                (when (fn? (:click n))
                  ((:click n)))
                (notif-actions/hide-mobile-user-notifications))}
   (user-avatar-image (:author n))
   [:div.user-notification-title
    (:title n)]
   [:div.user-notification-time-since
    [:time
     {:date-time      (:created-at n)
      :data-toggle    "tooltip"
      :data-placement "top"
      :data-delay     "{\"show\":\"1000\", \"hide\":\"0\"}"
      :data-container "body"
      :data-title     (utils/tooltip-date (:created-at n))}
     (utils/time-since (:created-at n) [:short])]]
   [:div.user-notification-body.oc-mentions.oc-mentions-hover
    {:dangerouslySetInnerHTML (utils/emojify (:body n))}]])

(rum/defcs user-notifications < rum/static
  rum/reactive
  (drv/drv :user-notifications)
  (drv/drv :unread-notifications-count) ;; required by desktop app for dock badge count
  (drv/drv :show-add-post-tooltip)
  (rum/local false ::tray-open)
  ui-mixins/refresh-tooltips-mixin
  (ui-mixins/on-window-click-mixin (fn [s e]
                                     (when (and @(::tray-open s)
                                                (not (utils/event-inside? e (rum/ref-node s :read-bt))))
                                       (close-tray s))))
  {:will-mount (fn [s]
                 (when (responsive/is-mobile-size?)
                   (dom-utils/lock-page-scroll))
                 s)
   :will-unmount (fn [s]
                   (when (responsive/is-mobile-size?)
                     (dom-utils/unlock-page-scroll))
                   s)}
  [s]
  (let [user-notifications-data (drv/react s :user-notifications)
        has-new-content (has-new-content? user-notifications-data)
        is-mobile? (responsive/is-mobile-size?)]
    [:div.user-notifications
     [:button.mlb-reset.notification-bell-bt
      {:class (utils/class-set {:new has-new-content
                                :active @(::tray-open s)})
       :data-toggle (when-not is-mobile? "tooltip")
       :data-placement "bottom"
       :title "Alerts"
       :on-click #(if @(::tray-open s)
                    (close-tray s)
                    (reset! (::tray-open s) true))}
      [:span.bell-icon]]
     [:div.user-notifications-tray
      {:class (utils/class-set {:hidden-tray (not @(::tray-open s))})}
      [:div.user-notifications-tray-header.group
       (when-not has-new-content
         [:button.mlb-reset.all-read-bt
          {:on-click #(user-actions/read-notifications)
           :data-toggle (when-not is-mobile? "tooltip")
           :data-placement "top"
           :data-container "body"
           :title "Mark all as read"}])
       [:div.title "Alerts"]
       (if is-mobile?
         [:button.mlb-reset.user-notifications-tray-mobile-close
          {:on-click #(notif-actions/hide-mobile-user-notifications)}]
         [:button.mlb-reset.notification-settings-bt
          {:on-click #(do
                        (close-tray s)
                        (nav-actions/show-user-settings :notifications))
           :data-toggle (when-not is-mobile? "tooltip")
           :data-placement "top"
           :data-container "body"
           :title "Notification settings"}])]
      [:div.user-notifications-tray-list
       (if (empty? user-notifications-data)
         [:div.user-notifications-tray-empty
          (all-caught-up)]
         (for [n user-notifications-data
               :let [entry-uuid (:uuid n)
                     board-slug (:board-slug n)
                     reminder? (:reminder? n)
                     reminder (when reminder?
                                (:reminder n))
                     notification-type (when reminder?
                                         (:notification-type reminder))
                        ;; Base string for the key of the React child
                     children-key-base (str "user-notification-" (:created-at n) "-")
                        ;; add a unique part to the key to make sure the children are rendered
                     children-key (str children-key-base
                                       (if (seq entry-uuid)
                                         entry-uuid
                                         (if (and reminder?
                                                  (seq (:uuid reminder)))
                                           (:uuid reminder)
                                           (rand 1000))))]]
           (rum/with-key (user-notification-item n) (str "user-notification-" (:created-at n)))))]]]))