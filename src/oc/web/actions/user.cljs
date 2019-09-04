(ns oc.web.actions.user
  (:require-macros [if-let.core :refer (when-let*)])
  (:require [cljsjs.moment-timezone]
            [taoensso.timbre :as timbre]
            [oc.web.api :as api]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.local-settings :as ls]
            [oc.web.utils.user :as user-utils]
            [oc.web.stores.user :as user-store]
            [oc.web.ws.notify-client :as ws-nc]
            [oc.web.lib.fullstory :as fullstory]
            [oc.web.actions.org :as org-actions]
            [oc.web.actions.nux :as nux-actions]
            [oc.web.actions.jwt :as jwt-actions]
            [oc.web.lib.json :refer (json->cljs)]
            [oc.web.actions.team :as team-actions]
            [oc.web.utils.comment :as comment-utils]
            [oc.web.actions.routing :as routing-actions]
            [oc.web.actions.activity :as activity-actions]
            [oc.web.actions.notifications :as notification-actions]))

;;User walls
(defn- check-user-walls
  "Check if one of the following is present and redirect to the proper wall if needed:
  :password-required redirect to password collect
  :name-required redirect to first and last name collect

  Use the orgs value to determine if the user has already at least one org set"
  ([]
    ; Delay to let the last api request set the app-state data
    (when (jwt/jwt)
      (utils/after 100
        #(when (and (user-store/orgs?)
                    (user-store/auth-settings?)
                    (user-store/auth-settings-status?))
            (check-user-walls (dis/auth-settings) (dis/orgs-data))))))
  ([auth-settings orgs]
    (let [status-response (set (map keyword (:status auth-settings)))
          has-orgs (pos? (count orgs))]
      (cond
        (status-response :password-required)
        (router/nav! oc-urls/confirm-invitation-password)

        (status-response :name-required)
        (if has-orgs
          (router/nav! oc-urls/confirm-invitation-profile)
          (router/nav! oc-urls/sign-up-profile))

        :else
        (when-not has-orgs
          (router/nav! oc-urls/sign-up-profile))))))

;; API Entry point
(defn entry-point-get-finished
  ([success body] (entry-point-get-finished success body nil))

  ([success body callback]
  (let [collection (:collection body)]
    (if success
      (let [orgs (:items collection)]
        (dis/dispatch! [:entry-point orgs collection])
        (check-user-walls)
        (when (fn? callback)
          (callback orgs collection)))
      (notification-actions/show-notification (assoc utils/network-error :expire 0))))))

(defn entry-point-get [org-slug]
  (api/get-entry-point (:org @router/path)
   (fn [success body]
     (entry-point-get-finished success body
       (fn [orgs collection]
         (if org-slug
           (if-let [org-data (first (filter #(= (:slug %) org-slug) orgs))]
             ;; We got the org we were looking for
             (org-actions/get-org org-data)
             (if (router/current-secure-activity-id)
               (activity-actions/secure-activity-get
                #(comment-utils/get-comments-if-needed (dis/secure-activity-data) (dis/comments-data)))
               (do
                 ;; avoid infinite loop of the Go to digest button
                 ;; by changing the value of the last visited slug
                 (if (pos? (count orgs))
                   ;; we got at least one org, redirect to it next time
                   (cook/set-cookie! (router/last-org-cookie) (:slug (first orgs)) cook/default-cookie-expire)
                   ;; no orgs present, remove the last org cookie to avoid infinite loops
                   (cook/remove-cookie! (router/last-org-cookie)))
                 (when-not (router/current-secure-activity-id)
                   ;; 404: secure entry can't 404 here since the org response is included in the
                   ;; secure entry response and not in the entry point response
                   (routing-actions/maybe-404)))))
           ;; If user is on login page and he's logged in redirect to the org page
           (when (and (jwt/jwt)
                      (utils/in? (:route @router/path) "login")
                      (pos? (count orgs)))
             (router/nav! (oc-urls/org (:slug (first orgs)))))))))))

(defn save-login-redirect [& [url]]
  (let [url (or url (.. js/window -location -href))]
    (when url
      (cook/set-cookie! router/login-redirect-cookie url))))

(defn maybe-save-login-redirect []
  (let [url-pathname (.. js/window -location -pathname)
        is-login-route? (or (= url-pathname oc-urls/login-wall)
                            (= url-pathname oc-urls/login)
                            (= url-pathname oc-urls/native-login))]
    (cond
      (and is-login-route?
           (:login-redirect (:query-params @dis/app-state)))
      (save-login-redirect (:login-redirect (:query-params @dis/app-state)))
      (not is-login-route?)
      (save-login-redirect))))

(defn login-redirect []
  (let [redirect-url (cook/get-cookie router/login-redirect-cookie)
        orgs (dis/orgs-data)]
    (cook/remove-cookie! router/login-redirect-cookie)
    (if redirect-url
      (router/redirect! redirect-url)
      (router/nav! (oc-urls/all-posts (:slug (utils/get-default-org orgs)))))))

(defn lander-check-team-redirect []
  (utils/after 100 #(api/get-entry-point (:org @router/path)
    (fn [success body]
      (entry-point-get-finished success body
        (fn [orgs collection]
          (if (zero? (count orgs))
            (router/nav! oc-urls/sign-up-profile)
            (login-redirect))))))))

;; Login
(defn login-with-email-finish
  [user-email success body status]
  (if success
    (do
      (if (empty? body)
        (utils/after 10 #(router/nav! (str oc-urls/email-wall "?e=" user-email)))
        (do
          (jwt-actions/update-jwt-cookie body)
          (api/get-entry-point (:org @router/path)
           (fn [success body] (entry-point-get-finished success body login-redirect)))))
      (dis/dispatch! [:login-with-email/success body]))
    (cond
     (= status 401)
     (dis/dispatch! [:login-with-email/failed 401])
     :else
     (dis/dispatch! [:login-with-email/failed 500]))))

(defn login-with-email [email pswd]
  (let [email-links (:links (dis/auth-settings))
        auth-link (utils/link-for email-links "authenticate" "GET" {:auth-source "email"})]
    (api/auth-with-email auth-link email pswd (partial login-with-email-finish email))
    (dis/dispatch! [:login-with-email])))

(defn login-with-slack [auth-url]
  (let [auth-url-with-redirect (user-utils/auth-link-with-state
                                 (:href auth-url)
                                 {:team-id "open-company-auth"
                                  :redirect oc-urls/slack-lander-check})]
    (router/redirect! auth-url-with-redirect)
    (dis/dispatch! [:login-with-slack])))

(defn login-with-google [auth-url]
  (let [auth-url-with-redirect (user-utils/auth-link-with-state
                                (:href auth-url)
                                {})]
    (router/redirect! auth-url-with-redirect)
    (dis/dispatch! [:login-with-google])))

(defn refresh-slack-user []
  (let [refresh-link (utils/link-for (:links (dis/auth-settings)) "refresh")]
    (api/refresh-slack-user refresh-link
     (fn [status body success]
      (if success
        (jwt-actions/update-jwt body)
        (router/redirect! oc-urls/logout))))))

(defn show-login [login-type]
  (dis/dispatch! [:login-overlay-show login-type]))

;; User Timezone preset

(defn- patch-timezone-if-needed [user-map]
  (when-let* [_notz (clojure.string/blank? (:timezone user-map))
              user-profile-link (utils/link-for (:links user-map) "partial-update" "PATCH")
              guessed-timezone (.. js/moment -tz guess)]
    (api/patch-user user-profile-link {:timezone guessed-timezone}
     (fn [status body success]
       (when success
        (dis/dispatch! [:user-data (json->cljs body)]))))))

;; Get user

(defn get-user [user-link]
  (when-let [fixed-user-link (or user-link (utils/link-for (:links (dis/auth-settings)) "user" "GET"))]
    (api/get-user fixed-user-link (fn [success data]
     (let [user-map (when success (json->cljs data))]
       (dis/dispatch! [:user-data user-map])
       (utils/after 100 nux-actions/check-nux)
       (patch-timezone-if-needed user-map))))))

;; Auth

(defn auth-settings-get
  "Entry point call for auth service."
  []
  (api/get-auth-settings (fn [body]
    (when body
      ;; auth settings loaded
      (when-let [user-link (utils/link-for (:links body) "user" "GET")]
        (get-user user-link))
      (dis/dispatch! [:auth-settings body])
      (check-user-walls)
      ;; Start teams retrieve if we have a link
      (team-actions/teams-get)))))

(defn auth-with-token-failed [error]
  (dis/dispatch! [:auth-with-token/failed error]))

;;Invitation
(defn invitation-confirmed [status body success]
 (when success
    (jwt-actions/update-jwt body)
    (when (= status 201)
      (nux-actions/new-user-registered "email")
      (api/get-entry-point (:org @router/path) entry-point-get-finished)
      (auth-settings-get))
    ;; Go to password setup
    (router/nav! oc-urls/confirm-invitation-password))
  (dis/dispatch! [:invitation-confirmed success]))

(defn confirm-invitation [token]
  (let [auth-link (utils/link-for (:links (dis/auth-settings)) "authenticate" "GET"
                   {:auth-source "email"})]
    (api/confirm-invitation auth-link token invitation-confirmed)))

;; Token authentication
(defn auth-with-token-success [token-type jwt]
  (api/get-auth-settings
   (fn [auth-body]
     (api/get-entry-point (:org @router/path)
      (fn [success body]
        (entry-point-get-finished success body)
        (let [orgs (:items (:collection body))
              to-org (utils/get-default-org orgs)]
          (router/redirect! (if to-org (oc-urls/all-posts (:slug to-org)) oc-urls/sign-up-profile)))))))
  (when (= token-type :password-reset)
    (cook/set-cookie! :show-login-overlay "collect-password"))
  (dis/dispatch! [:auth-with-token/success jwt]))

(defn auth-with-token-callback
  [token-type success body status]
  (if success
    (do
      (jwt-actions/update-jwt body)
      (when (and (not= token-type :password-reset)
                 (empty? (jwt/get-key :name)))
        (nux-actions/new-user-registered "email"))
      (auth-with-token-success token-type body))
    (cond
      (= status 401)
      (auth-with-token-failed 401)
      :else
      (auth-with-token-failed 500))))

(defn auth-with-token [token-type]
  (let [token-links (:links (dis/auth-settings))
        auth-url (utils/link-for token-links "authenticate" "GET" {:auth-source "email"})
        token (:token (:query-params @router/path))]
    (api/auth-with-token auth-url token (partial auth-with-token-callback token-type))
    (dis/dispatch! [:auth-with-token token-type])))

;; Signup

(defn signup-with-email-failed [status]
  (dis/dispatch! [:signup-with-email/failed status]))

(defn signup-with-email-success
  [user-email status jwt]
  (cond
    (= status 204) ;; Email wall since it's a valid signup w/ non verified email address
    (utils/after 10 #(router/nav! (str oc-urls/email-wall "?e=" user-email)))
    (= status 200) ;; Valid login, not signup, redirect to home
    (if (or
          (and (empty? (:first-name jwt)) (empty? (:last-name jwt)))
          (empty? (:avatar-url jwt)))
      (do
        (utils/after 200 #(router/nav! oc-urls/sign-up-profile))
        (api/get-entry-point (:org @router/path) entry-point-get-finished))
      (api/get-entry-point (:org @router/path)
       (fn [success body]
         (entry-point-get-finished success body
           (fn [orgs collection]
             (when (pos? (count orgs))
               (router/nav! (oc-urls/all-posts (:slug (utils/get-default-org orgs))))))))))
    :else ;; Valid signup let's collect user data
    (do
      (jwt-actions/update-jwt-cookie jwt)
      (nux-actions/new-user-registered "email")
      (utils/after 200 #(router/nav! oc-urls/sign-up-profile))
      (api/get-entry-point (:org @router/path) entry-point-get-finished)
      (dis/dispatch! [:signup-with-email/success]))))

(defn signup-with-email-callback
  [user-email success body status]
  (if success
    (signup-with-email-success user-email status body)
    (signup-with-email-failed status)))

(defn signup-with-email [signup-data]
  (let [email-links (:links (dis/auth-settings))
        auth-link (utils/link-for email-links "create" "POST" {:auth-source "email"})]
    (api/signup-with-email auth-link
     (or (:firstname signup-data) "")
     (or (:lastname signup-data) "")
     (:email signup-data)
     (:pswd signup-data)
     (.. js/moment -tz guess)
     (partial signup-with-email-callback (:email signup-data)))
    (dis/dispatch! [:signup-with-email])))

(defn signup-with-email-reset-errors []
  (dis/dispatch! [:input [:signup-with-email] {}]))

(defn pswd-collect [form-data password-reset?]
  (let [update-link (utils/link-for (:links (:current-user-data @dis/app-state)) "partial-update" "PATCH")]
    (api/collect-password update-link (:pswd form-data)
      (fn [status body success]
        (when success
          (dis/dispatch! [:user-data (json->cljs body)]))
        (when (and (>= status 200)
                   (<= status 299))
          (if password-reset?
            (do
              (cook/remove-cookie! :show-login-overlay)
              (utils/after 200 #(router/nav! oc-urls/login)))
            (do
              (nux-actions/new-user-registered "email")
              (router/nav! oc-urls/confirm-invitation-profile))))
        (dis/dispatch! [:pswd-collect/finish status]))))
  (dis/dispatch! [:pswd-collect password-reset?]))

(defn password-reset [email]
  (let [reset-link (utils/link-for (:links (dis/auth-settings)) "reset")]
    (api/password-reset reset-link email
     #(dis/dispatch! [:password-reset/finish %]))
    (dis/dispatch! [:password-reset])))

;; User Profile

(defn user-profile-save
  ([current-user-data edit-data]
   (user-profile-save current-user-data edit-data nil))
  ([current-user-data edit-data org-editing]
    (let [edit-user-profile (or (:user-data edit-data) edit-data)
          new-password (:password edit-user-profile)
          password-did-change (pos? (count new-password))
          with-pswd (if (and password-did-change
                             (>= (count new-password) 8))
                      edit-user-profile
                      (dissoc edit-user-profile :password))
          new-email (:email edit-user-profile)
          email-did-change (not= new-email (:email current-user-data))
          with-email (if (and email-did-change
                              (utils/valid-email? new-email))
                       (assoc with-pswd :email new-email)
                       (assoc with-pswd :email (:email current-user-data)))
          timezone (or (:timezone edit-user-profile) (:timezone current-user-data) (.. js/moment -tz guess))
          with-timezone (assoc with-email :timezone timezone)
          user-profile-link (utils/link-for (:links current-user-data) "partial-update" "PATCH")]
      (dis/dispatch! [:user-profile-save])
      (api/patch-user user-profile-link with-timezone
       (fn [status body success]
         (if (= status 422)
           (dis/dispatch! [:user-profile-update/failed])
           (when success
             ;; If user is not creating a new company let's show the spinner
             ;; then we will delay the redirect to AP to show the carrot more
             (when-not org-editing
               (dis/dispatch! [:input [:ap-loading] true]))
             (utils/after 100
              (fn []
                (jwt-actions/jwt-refresh
                 #(if org-editing
                    (org-actions/create-or-update-org org-editing)
                    (utils/after 2000
                      (fn[] (router/nav! (oc-urls/all-posts (:slug (first (dis/orgs-data)))))))))))
             (dis/dispatch! [:user-data (json->cljs body)]))))))))

(defn user-avatar-save [avatar-url]
  (let [user-avatar-data {:avatar-url avatar-url}
        current-user-data (dis/current-user-data)
        user-profile-link (utils/link-for (:links current-user-data) "partial-update" "PATCH")]
    (api/patch-user user-profile-link user-avatar-data
     (fn [status body success]
       (if-not success
         (do
           (dis/dispatch! [:user-profile-avatar-update/failed])
           (notification-actions/show-notification
            {:title "Image upload error"
             :description "An error occurred while processing your image. Please retry."
             :expire 3
             :id :user-avatar-upload-failed
             :dismiss true}))
         (do
           (utils/after 1000 jwt-actions/jwt-refresh)
           (dis/dispatch! [:user-data (json->cljs body)])
           (notification-actions/show-notification
            {:title "Image update succeeded"
             :description "Your image was succesfully updated."
             :expire 3
             :dismiss true})))))))

(defn user-profile-reset []
  (dis/dispatch! [:user-profile-reset]))

(defn resend-verification-email []
  (let [user-data (dis/current-user-data)
        resend-link (utils/link-for (:links user-data) "resend-verification" "POST")]
    (when resend-link
      (api/resend-verification-email resend-link
       (fn [success]
         (notification-actions/show-notification
          {:title (if success "Verification email re-sent!" "An error occurred")
           :description (when-not success "Please try again.")
           :expire 3
           :primary-bt-title "OK"
           :primary-bt-dismiss true
           :id (keyword (str "resend-verification-" (if success "ok" "failed")))}))))))

;; Mobile push notifications

(def ^:private expo-push-token-expiry (* 60 60 24 352 10)) ;; 10 years (infinite)

(defn dispatch-expo-push-token
  "Save the expo push token in a cookie (or re-save to extend the cookie expire time)
   and dispatch the value into the app-state."
  [push-token]
  (when push-token
    ;; A blank push-token indicates that the user was prompted, but
    ;; denied the push notification permission.
    (cook/set-cookie! router/expo-push-token-cookie push-token expo-push-token-expiry)
    (dis/dispatch! [:expo-push-token push-token])))

(defn recall-expo-push-token
  []
  (dispatch-expo-push-token (cook/get-cookie router/expo-push-token-cookie)))

(defn add-expo-push-token [push-token]
  (let [user-data            (dis/current-user-data)
        add-token-link       (utils/link-for (:links user-data) "add-expo-push-token" "POST")
        need-to-add?         (not (user-utils/user-has-push-token? user-data push-token))]
    (if-not need-to-add?
      ;; Push token already known, dispatch it to app-state immediately
      (dispatch-expo-push-token push-token)
      ;; Novel push token, add it to the Auth service for storage
      (when (and add-token-link push-token)
        (api/add-expo-push-token
         add-token-link
         push-token
         (fn [success]
           (dispatch-expo-push-token push-token)
           (timbre/info "Successfully saved Expo push notification token")))))))

(defn deny-push-notification-permission
  "Push notification permission was denied."
  []
  (dispatch-expo-push-token ""))

;; Initial loading

(defn initial-loading [& [force-refresh]]
  (let [force-refresh (or force-refresh
                          (utils/in? (:route @router/path) "org")
                          (utils/in? (:route @router/path) "login"))
        latest-entry-point (if (or force-refresh
                                   (nil? (:latest-entry-point @dis/app-state)))
                             0
                             (:latest-entry-point @dis/app-state))
        latest-auth-settings (if (or force-refresh
                                     (nil? (:latest-auth-settings @dis/app-state)))
                               0
                               (:latest-auth-settings @dis/app-state))
        now (.getTime (js/Date.))
        reload-time (* 1000 60 20)] ; every 20m
    (when (or (> (- now latest-entry-point) reload-time)
              (and (router/current-org-slug)
                   (nil? (dis/org-data))))
      (entry-point-get (router/current-org-slug)))
    (when (> (- now latest-auth-settings) reload-time)
      (auth-settings-get))))

;; User notifications

(defn read-notifications []
  (dis/dispatch! [:user-notifications/read (router/current-org-slug)]))

(defn show-mobile-user-notifications []
  (dis/dispatch! [:input [:mobile-user-notifications] true]))

(defn hide-mobile-user-notifications []
  (dis/dispatch! [:input [:mobile-user-notifications] false]))

;; subscribe to websocket events
(defn subscribe []
  (ws-nc/subscribe :user/notifications
    (fn [{:keys [_ data]}]
      (let [fixed-notifications (user-utils/fix-notifications (:notifications data))]
        (dis/dispatch! [:user-notifications (router/current-org-slug) fixed-notifications]))))
  (ws-nc/subscribe :user/notification
    (fn [{:keys [_ data]}]
      (when-let [fixed-notification (user-utils/fix-notification data true)]
        (dis/dispatch! [:user-notification (router/current-org-slug) fixed-notification])
        (notification-actions/show-notification
         {:title (:title fixed-notification)
          :mention true
          :dismiss true
          :click (:click fixed-notification)
          :mention-author (:author fixed-notification)
          :description (:body fixed-notification)
          :id (str "notif-" (:created-at fixed-notification))
          :expire 3})))))

(defn read-notification [notification]
  (dis/dispatch! [:user-notification/read (router/current-org-slug) notification]))

;; Debug

(defn force-jwt-refresh []
  (when (jwt/jwt) (jwt-actions/jwt-refresh)))

(set! (.-OCWebForceRefreshToken js/window) force-jwt-refresh)
