(ns oc.web.actions.nav-sidebar
  (:require-macros [if-let.core :refer (when-let*)])
  (:require [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.shared.useragent :as ua]
            [oc.web.local-settings :as ls]
            [oc.web.utils.dom :as dom-utils]
            [oc.web.actions.nux :as nux-actions]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.user :as user-actions]
            [oc.web.actions.cmail :as cmail-actions]
            [oc.web.actions.routing :as routing-actions]
            [oc.web.actions.section :as section-actions]
            [oc.web.actions.activity :as activity-actions]
            [oc.web.actions.contributions :as contributions-actions]
            [oc.web.components.ui.alert-modal :as alert-modal]))

;; Panels
;; :menu
;; :org
;; :integrations
;; :team
;; :invite-picker
;; :invite-email
;; :invite-slack
;; :payments
;; :profile
;; :notifications
;; :reminders
;; :reminder-{uuid}/:reminder-new
;; :section-add
;; :section-edit
;; :wrt-{uuid}
;; :theme
;; :user-info-{uuid}
;; :follow-picker

(defn- refresh-contributions-data [author-uuid]
  (when author-uuid
    (contributions-actions/contributions-get author-uuid)))

(defn nav-to-author!
  ([e author-uuid url]
  (nav-to-author! e author-uuid url (or (:back-y @router/path) (utils/page-scroll-top)) true))

  ([e author-uuid url back-y refresh?]
  (when (and e
             (.-preventDefault e))
    (.preventDefault e))
  (when ua/mobile?
    (dis/dispatch! [:input [:mobile-navigation-sidebar] false]))
  (utils/after 0 (fn []
   (let [current-path (str (.. js/window -location -pathname) (.. js/window -location -search))
         org-slug (router/current-org-slug)
         sort-type (activity-actions/saved-sort-type org-slug author-uuid)
         org-data (dis/org-data)]
     (if (= current-path url)
       (do ;; In case user is clicking on the currently highlighted section
           ;; let's refresh the posts list only
         (routing-actions/routing @router/path)
         (user-actions/initial-loading refresh?))
       (do ;; If user clicked on a different section/container
           ;; let's switch to it using pushState and changing
           ;; the internal router state
         (router/set-route! [org-slug author-uuid sort-type "dashboard"]
          {:org org-slug
           :contributions author-uuid
           :sort-type sort-type
           :scroll-y back-y
           :query-params (router/query-params)})
         (.pushState (.-history js/window) #js {} (.-title js/document) url)
         (set! (.. js/document -scrollingElement -scrollTop) (utils/page-scroll-top))
         (when refresh?
           (utils/after 0 #(refresh-contributions-data author-uuid))))))))))

(defn- container-data [back-to]
  (cond
   (or (:replies back-to)
       (:topics back-to))
   nil
   (contains? back-to :contributions)
   (dis/contributions-data @dis/app-state (router/current-org-slug) (:contributions back-to))
   (dis/is-container? (:board back-to))
   (dis/contributions-data @dis/app-state (router/current-org-slug) (:board back-to))
   :else
   (dis/board-data @dis/app-state (router/current-org-slug) (:board back-to))))

(defn- refresh-board-data [board-slug]
  (when (and (not (router/current-activity-id))
             board-slug)
    (let [org-data (dis/org-data)
          board-data (container-data {:board board-slug})]
       (cond

        (= board-slug "inbox")
        (activity-actions/inbox-get org-data)

        (= board-slug "replies")
        (activity-actions/replies-get org-data)

        (and (= board-slug "all-posts")
             (= (router/current-sort-type) dis/recently-posted-sort))
        (activity-actions/all-posts-get org-data)

        (and (= board-slug "all-posts")
             (= (router/current-sort-type) dis/recent-activity-sort))
        (activity-actions/recent-all-posts-get org-data)

        (= board-slug "bookmarks")
        (activity-actions/bookmarks-get org-data)

        (and (= board-slug "following")
             (= (router/current-sort-type) dis/recently-posted-sort))
        (activity-actions/following-get org-data)

        (and (= board-slug "following")
             (= (router/current-sort-type) dis/recent-activity-sort))
        (activity-actions/recent-following-get org-data)

        (and (= board-slug "unfollowing")
             (= (router/current-sort-type) dis/recently-posted-sort))
        (activity-actions/unfollowing-get org-data)

        (and (= board-slug "unfollowing")
             (= (router/current-sort-type) dis/recent-activity-sort))
        (activity-actions/recent-unfollowing-get org-data)

        :default
        (let [fixed-board-data (or board-data
                                   (some #(when (= (:slug %) board-slug) %) (:boards org-data)))
              board-link (utils/link-for (:links fixed-board-data) ["item" "self"] "GET")]
          (when board-link
            (section-actions/section-get (:slug fixed-board-data) board-link)))))))

(defn nav-to-url!
  ([e board-slug url]
  (nav-to-url! e board-slug url (or (:back-y @router/path) (utils/page-scroll-top)) true))

  ([e board-slug url back-y refresh?]
  (when (and e
             (.-preventDefault e))
    (.preventDefault e))
  (when ua/mobile?
    (dis/dispatch! [:input [:mobile-navigation-sidebar] false]))
  (utils/after 0 (fn []
   (let [current-path (str (.. js/window -location -pathname) (.. js/window -location -search))
         org-slug (router/current-org-slug)
         sort-type (activity-actions/saved-sort-type org-slug board-slug)
         is-drafts-board? (= board-slug utils/default-drafts-board-slug)
         is-container? (dis/is-container? board-slug)
         org-data (dis/org-data)]
     (if (= current-path url)
       (do ;; If refreshing Home or Replies let's send a container seen first
         (when (#{:following :replies} (keyword board-slug))
           (activity-actions/send-container-seen))
         ;; In case user clicked on the current
         ;; location let's refresh it
         (routing-actions/routing @router/path)
         (user-actions/initial-loading refresh?))
       (do ;; If user clicked on a different section/container
           ;; let's switch to it using pushState and changing
           ;; the internal router state
         (router/set-route! [org-slug board-slug (if is-container? "dashboard" board-slug) sort-type]
          {:org org-slug
           :board board-slug
           :sort-type sort-type
           :scroll-y back-y
           :query-params (router/query-params)})
         (.pushState (.-history js/window) #js {} (.-title js/document) url)
         (dis/dispatch! [:force-list-update])
         (set! (.. js/document -scrollingElement -scrollTop) (utils/page-scroll-top))
         ;; Let's change the QP section if it's not active and going to an editable section
         (when (and (not is-container?)
                    (not is-drafts-board?)
                    (-> @dis/app-state :cmail-state :collapsed))
           (when-let* [nav-to-board-data (some #(when (= (:slug %) board-slug) %) (:boards org-data))
                       edit-link (utils/link-for (:links nav-to-board-data) "create" "POST")]
             (dis/dispatch! [:input [:cmail-data] {:board-slug (:slug nav-to-board-data)
                                                   :board-name (:name nav-to-board-data)
                                                   :publisher-board (:publisher-board nav-to-board-data)}])
             (dis/dispatch! [:input [:cmail-state :key] (utils/activity-uuid)])))
         (when refresh?
           (utils/after 0 #(refresh-board-data board-slug))))))))))

(defn dismiss-post-modal [e]
  (let [org-data (dis/org-data)
        ;; Go back to
        back-to (utils/back-to org-data)
        is-replies? (:replies back-to)
        is-topics? (:topics back-to)
        is-following? (:following back-to)
        is-contributions? (:contributions back-to)
        to-url (cond
                 is-following?
                 (oc-urls/following)
                 is-replies?
                 (oc-urls/replies)
                 is-topics?
                 (oc-urls/topics)
                 is-contributions?
                 (oc-urls/contributions (:contributions back-to))
                 :else
                 (oc-urls/board (:board back-to)))
        cont-data (container-data back-to)
        should-refresh-data? (or ; Force refresh of activities if user did an action that can resort posts
                                 (:refresh @router/path)
                                 (not cont-data))
        ;; Get the previous scroll top position
        default-back-y (or (:back-y @router/path) (utils/page-scroll-top))
        ;; Scroll back to the previous scroll position only if the posts are
        ;; not going to refresh, if they refresh the old scroll position won't be right anymore
        back-y (if should-refresh-data?
                 (utils/page-scroll-top)
                 default-back-y)]
    (cond
      is-contributions?
      (nav-to-author! e (:contributions back-to) to-url back-y should-refresh-data?)
      is-replies?
      (nav-to-url! e "replies" to-url back-y should-refresh-data?)
      is-topics?
      (nav-to-url! e "topics" to-url back-y should-refresh-data?)
      is-following?
      (nav-to-url! e "following" to-url back-y should-refresh-data?)
      :else
      (nav-to-url! e (:board back-to) to-url back-y should-refresh-data?))))

(defn open-post-modal
  ([activity-data dont-scroll]
   (open-post-modal activity-data dont-scroll nil))

  ([activity-data dont-scroll comment-uuid]
  (let [org (router/current-org-slug)
        board (:board-slug activity-data)
        sort-type (activity-actions/saved-sort-type org board)
        back-to (cond
                  (and (seq (router/current-contributions-id))
                       (not (seq (router/current-board-slug))))
                  {:contributions (router/current-contributions-id)}
                  (= (router/current-board-slug) "replies")
                  {:replies true}
                  (= (router/current-board-slug) "topics")
                  {:topics true}
                  (= (router/current-board-slug) "following")
                  {:following true}
                  :else
                  {:board board})
        activity (:uuid activity-data)
        post-url (if comment-uuid
                   (oc-urls/comment-url org board activity comment-uuid)
                   (oc-urls/entry org board activity))
        query-params (router/query-params)
        route [org board sort-type activity "activity"]
        scroll-y-position (.. js/document -scrollingElement -scrollTop)
        route-path* {:org org
                     :board board
                     :sort-type sort-type
                     :activity activity
                     :query-params query-params
                     :back-to back-to
                     :back-y scroll-y-position}
        route-path (if comment-uuid
                     (assoc route-path* :comment comment-uuid)
                     route-path*)]
    (router/set-route! route route-path)
    (cmail-actions/cmail-hide)
    (when-not dont-scroll
      (if ua/mobile?
        (utils/after 10 #(utils/scroll-to-y 0 0))
        (utils/scroll-to-y 0 0)))
    (.pushState (.-history js/window) #js {} (.-title js/document) post-url))))

;; Push panel

(defn- push-panel
  "Push a panel at the top of the stack."
  [panel]
  (when (zero? (count (get @dis/app-state :panel-stack [])))
    (dom-utils/lock-page-scroll))
  (dis/dispatch! [:update [:panel-stack] #(vec (conj (or % []) panel))]))

(defn- pop-panel
  "Pop the panel at the top of stack from it and return it."
  []
  (let [panel-stack (:panel-stack @dis/app-state)]
    (when (pos? (count panel-stack))
      (dis/dispatch! [:update [:panel-stack] pop]))
    (when (= (count panel-stack) 1)
      (dom-utils/unlock-page-scroll))
    (peek panel-stack)))

(defn close-all-panels
  "Remove all the panels from the stack and return the stack itself.
   The return value is never used, but it's being returned to be consistent with
   the pop-panel function."
  []
  (let [panel-stack (:panel-stack @dis/app-state)]
    (dis/dispatch! [:input [:panel-stack] []])
    (dom-utils/unlock-page-scroll)
    panel-stack))

;; Section settings

(defn show-section-editor [section-slug]
  (section-actions/setup-section-editing section-slug)
  (push-panel :section-edit))

(defn hide-section-editor []
  (pop-panel))

(defn show-section-add []
  (dis/dispatch! [:input [:show-section-add-cb]
   (fn [sec-data note dismiss-action]
     (if sec-data
       (section-actions/section-save sec-data note dismiss-action)
       (when (fn? dismiss-action)
        (dismiss-action))))])
  (push-panel :section-add))

(defn show-section-add-with-callback [callback]
  (dis/dispatch! [:input [:show-section-add-cb]
   (fn [sec-data note dismiss-action]
     (callback sec-data note dismiss-action)
     (dis/dispatch! [:input [:show-section-add-cb] nil])
     (pop-panel))])
  (push-panel :section-add))

(defn hide-section-add []
  (pop-panel))

;; Reminders

(defn show-reminders []
  (push-panel :reminders))

(defn show-new-reminder []
  (push-panel :reminder-new))

(defn edit-reminder [reminder-uuid]
  (push-panel (keyword (str "reminder-" reminder-uuid))))

(defn close-reminders []
  (pop-panel))

;; Menu

(defn menu-toggle []
  (let [panel-stack (:panel-stack @dis/app-state)]
    (if (= (peek panel-stack) :menu)
      (pop-panel)
      (push-panel :menu))))

(defn menu-close []
  (pop-panel))

;; Show panels

(defn show-org-settings [panel]
  (if panel
    (when (or (not= panel :payments)
              ls/payments-enabled)
      (push-panel panel))
    (pop-panel)))

(defn show-user-settings [panel]
  (if panel
    (push-panel panel)
    (pop-panel)))

;; WRT

(defn show-wrt [activity-uuid]
  (push-panel (keyword (str "wrt-" activity-uuid))))

(defn hide-wrt []
  (pop-panel))

;; Integrations

(defn open-integrations-panel [e]
  (when e
    (.preventDefault e)
    (.stopPropagation e))
  (if (responsive/is-mobile-size?)
    (let [alert-data {:action "mobile-integrations-link"
                      :message "Wut integrations need to be configured in a desktop browser."
                      :solid-button-style :green
                      :solid-button-title "OK, got it"
                      :solid-button-cb #(alert-modal/hide-alert)}]
      (alert-modal/show-alert alert-data))
    (show-org-settings :integrations)))

(set! (.-OCWebStaticOpenIntegrationsPanel js/window) open-integrations-panel)

;; Theme

(defn show-theme-settings []
  (push-panel :theme))

(defn hide-theme-settings []
  (pop-panel))

;; User info modal

(defn show-user-info [user-id]
  (push-panel (str "user-info-" user-id)))

(defn hide-user-info []
  (pop-panel))

;; Follow picker

(defn show-follow-picker []
  (push-panel :follow-picker))

(defn hide-follow-picker []
  (pop-panel))
