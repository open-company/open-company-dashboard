(ns oc.web.utils.user
  (:require [clojure.edn :as edn]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.lib.user :as user-lib]
            [oc.lib.oauth :as oauth]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.utils.ui :refer (ui-compose)]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.cmail :as cmail-actions]
            [oc.web.utils.activity :as activity-utils]))

(def user-avatar-filestack-config
  {:accept "image/*"
   :fromSources ["local_file_system"]
   :transformations {
     :crop {
       :aspectRatio 1}}})

(defn notification-title [notification]
  (let [mention? (:mention? notification)
        reminder? (:reminder? notification)
        follow-up? (:follow-up? notification)
        follow-up-data (when follow-up?
                         (:follow-up notification))
        author (:author notification)
        first-name (or (:first-name author) (first (clojure.string/split (:name author) #"\s")))
        reminder (when reminder?
                    (:reminder notification))
        notification-type (when reminder?
                            (:notification-type reminder))
        reminder-assignee (when reminder?
                            (:assignee reminder))
        entry-publisher (:entry-publisher notification)
        user-id (:user-id notification)]
    (cond
      ;; A follow-up was created for the current user
      (and follow-up?
           follow-up-data
           (not= (-> follow-up-data :author :user-id) (jwt/user-id))
           (not (:completed? follow-up-data)))
      (str (user-lib/name-for (:author follow-up-data)) " created a follow-up for you")
      ;; A reminder was created for current user
      (and reminder
           (= notification-type "reminder-notification"))
      (str first-name " created a new reminder for you")
      ;; A reminder has been triggered for the current user
      (and reminder
           (= notification-type "reminder-alert"))
      (str "Hi " (first (clojure.string/split (:name reminder-assignee) #"\s")) ", it's time to update your team")
      ;; Current user was mentioned in a post or comment, for comment check (seq (:interaction-id notification))
      mention?
      (str first-name " mentioned you")
      ;; A comment was added to a post the current is involved in
      (and ;; if is a commnet
           (:interaction-id notification)
           ;; And the recipient of the notification is different than the
           ;; author fo the post it means we are just following this post
           ;; because we commented too
           (not= (:user-id entry-publisher) user-id))
      (str first-name " replied to a thread")
      ;; A comment was added to a post the current user published
      (:interaction-id notification)
      (str first-name " commented on your post")
      :else
      nil)))

(defn notification-content [notification]
  (let [reminder? (:reminder? notification)
        reminder (when reminder?
                   (:reminder notification))
        notification-type (when reminder?
                            (:notification-type reminder))]
    (cond
      (and reminder
           (= notification-type "reminder-notification"))
      (str
       (:headline reminder) ": "
       (:frequency reminder) " starting "
       (activity-utils/post-date (:next-send reminder)))
      (and reminder
           (= notification-type "reminder-alert"))
      (:headline reminder)
      :else
      (:content notification))))

(defn- load-item-if-needed [board-slug entry-uuid interaction-uuid]
  (when (and board-slug
             entry-uuid)
    (let [url (if interaction-uuid
                (oc-urls/comment-url board-slug entry-uuid interaction-uuid)
                (oc-urls/entry board-slug entry-uuid))]
      #(if (seq (get (dis/posts-data) entry-uuid))
         (router/nav! url)
         (cmail-actions/get-entry-with-uuid board-slug entry-uuid
          (fn [s] (when s (router/nav! url))))))))

(defn fix-notification [notification & [unread]]
  (let [board-id (:board-id notification)
        board-data (activity-utils/board-by-uuid board-id)
        is-interaction (seq (:interaction-id notification))
        created-at (:notify-at notification)
        title (notification-title notification)
        reminder-data (:reminder notification)
        reminder? (:reminder? notification)
        entry-uuid (:entry-id notification)
        interaction-uuid (:interaction-id notification)
        follow-up? (:follow-up? notification)
        follow-up-data (:follow-up notification)]
    (when (seq title)
      {:uuid entry-uuid
       :board-slug (:slug board-data)
       :interaction-id interaction-uuid
       :is-interaction is-interaction
       :unread unread
       :mention? (:mention? notification)
       :reminder? reminder?
       :reminder reminder-data
       :follow-up? follow-up?
       :follow-up follow-up-data
       :created-at (:notify-at notification)
       :body (notification-content notification)
       :title title
       :author (:author notification)
       :click (if follow-up?
                (load-item-if-needed (or (:slug board-data) board-id) entry-uuid interaction-uuid)
                (if reminder?
                  (when-not (responsive/is-mobile-size?)
                    (if (and reminder-data
                             (= (:notification-type reminder-data) "reminder-notification"))
                      #(oc.web.actions.nav-sidebar/show-reminders)
                      #(ui-compose)))
                  (load-item-if-needed (or (:slug board-data) board-id) entry-uuid interaction-uuid)))})))

(defn sorted-notifications [notifications]
  (vec (reverse (sort-by :created-at notifications))))

(defn fix-notifications [notifications]
  (sorted-notifications
   (remove nil?
    (map fix-notification
     notifications))))

(def user-name-max-lenth 64)

;; Associated Slack user check

(defn user-has-slack-with-bot?
  "Check if the current user has an associated Slack user under a team that has the bot."
  [current-user-data bots-data team-roster]
  (let [slack-orgs-with-bot (map :slack-org-id bots-data)
        slack-users (:slack-users (first (filter #(= (:user-id %) (:user-id current-user-data)) (:users team-roster))))]
    (some #(contains? slack-users (keyword %)) slack-orgs-with-bot)))

(defn user-has-push-token?
  [current-user-data push-token]
  (let [current-push-tokens (set (:expo-push-tokens current-user-data))]
    (current-push-tokens push-token)))

(defn auth-link-with-state [original-url {:keys [user-id team-id redirect redirect-origin] :as state}]
  (let [parsed-url       (js/URL. original-url)
        old-state-string (.. parsed-url -searchParams (get "state"))
        decoded-state    (oauth/decode-state-string old-state-string)
        combined-state   (merge decoded-state state)
        new-state-string (oauth/encode-state-string combined-state)]
    (.. parsed-url -searchParams (set "state" new-state-string))
    (str parsed-url)))
