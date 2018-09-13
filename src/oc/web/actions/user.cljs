(ns oc.web.actions.user
  (:require [taoensso.timbre :as timbre]
            [oc.web.api :as api]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.local_settings :as ls]
            [oc.web.stores.user :as user-store]
            [oc.web.actions.org :as org-actions]
            [oc.web.actions.nux :as nux-actions]
            [oc.web.lib.json :refer (json->cljs)]
            [oc.web.actions.team :as team-actions]
            [oc.web.actions.notifications :as notification-actions]))

;; Logout

(defn logout []
  (cook/remove-cookie! :jwt)
  (router/redirect! "/")
  (dis/dispatch! [:logout]))

;; JWT

(defn update-jwt-cookie [jwt]
  (cook/set-cookie! :jwt jwt (* 60 60 24 60) "/" ls/jwt-cookie-domain ls/jwt-cookie-secure))

(defn dispatch-jwt []
  (when (and (cook/get-cookie :show-login-overlay)
             (not= (cook/get-cookie :show-login-overlay) "collect-name-password")
             (not= (cook/get-cookie :show-login-overlay) "collect-password"))
    (cook/remove-cookie! :show-login-overlay))
  (utils/after 1 #(dis/dispatch! [:jwt (jwt/get-contents)])))

(defn update-jwt [jbody]
  (timbre/info jbody)
  (when jbody
    (update-jwt-cookie jbody)
    (dispatch-jwt)))

(defn jwt-refresh []
  (api/jwt-refresh update-jwt logout))

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
          (router/nav! oc-urls/sign-up-team))))))

;; API Entry point
(defn entry-point-get-finished
  ([success body] (entry-point-get-finished success body nil))

  ([success body callback]
  (let [collection (:collection body)]
    (if success
      (let [orgs (:items collection)]
        (when (fn? callback)
          (callback orgs collection))
        (dis/dispatch! [:entry-point orgs collection])
        (check-user-walls))
      (notification-actions/show-notification (assoc utils/network-error :expire 0))))))

(defn entry-point-get [org-slug]
  (api/web-app-version-check
    (fn [{:keys [success body status]}]
      (when (= status 404)
        (notification-actions/show-notification (assoc utils/app-update-error :expire 0)))))
  (api/get-entry-point
   (fn [success body]
     (entry-point-get-finished success body
       (fn [orgs collection]
         (if org-slug
           (if-let [org-data (first (filter #(= (:slug %) org-slug) orgs))]
             (org-actions/get-org org-data)
             (let [ap-initial-at (:ap-initial-at @dis/app-state)
                   currently-logged-in (jwt/jwt)]
               (when-not (or (router/current-activity-id)
                             ap-initial-at)
                 ;; 404 only if the user is not looking at a secure post page
                 ;; if so the entry point response can not include the specified org
                 (when-not (router/current-secure-activity-id)
                   ;; avoid infinite loop of the Go to digest button
                   ;; by changing the value of the last visited slug
                   (if (pos? (count orgs))
                     (cook/set-cookie! (router/last-org-cookie) (:slug (first orgs)) (* 60 60 24 6))
                     (cook/remove-cookie! (router/last-org-cookie)))
                   (router/redirect-404!)))))
           (when (and (jwt/jwt)
                      (utils/in? (:route @router/path) "login")
                      (pos? (count orgs)))
             (router/nav! (oc-urls/org (:slug (first orgs)))))))))))

(defn lander-check-team-redirect []
  (utils/after 100 #(api/get-entry-point
    (fn [success body]
      (entry-point-get-finished success body
        (fn [orgs collection]
          (if (zero? (count orgs))
            (router/nav! oc-urls/sign-up-team)
            (router/nav! (oc-urls/org (:slug (utils/get-default-org orgs)))))))))))

;; Login
(defn login-with-email-finish
  [user-email success body status]
  (if success
    (do
      (if (empty? body)
        (utils/after 10 #(router/nav! (str oc-urls/email-wall "?e=" user-email)))
        (do
          (update-jwt-cookie body)
          (api/get-entry-point
           (fn [success body] (entry-point-get-finished success body
             (fn [orgs collection]
               (when (pos? (count orgs))
                 (router/nav! (oc-urls/org (:slug (utils/get-default-org orgs)))))))))))
      (dis/dispatch! [:login-with-email/success body]))
    (cond
     (= status 401)
     (dis/dispatch! [:login-with-email/failed 401])
     :else
     (dis/dispatch! [:login-with-email/failed 500]))))

(defn login-with-email [email pswd]
  (api/auth-with-email email pswd (partial login-with-email-finish email))
  (dis/dispatch! [:login-with-email]))

(defn login-with-slack [auth-url]
  (let [auth-url-with-redirect (utils/slack-link-with-state
                                 (:href auth-url)
                                 nil
                                 "open-company-auth" oc-urls/slack-lander-check)]
    (router/redirect! auth-url-with-redirect)
    (dis/dispatch! [:login-with-slack])))

(defn login-with-google [auth-url]
  (router/redirect! (:href auth-url))
  (dis/dispatch! [:login-with-google]))

(defn refresh-slack-user []
  (api/refresh-slack-user (fn [status body success]
    (if success
      (update-jwt body)
      (router/redirect! oc-urls/logout)))))

(defn show-login [login-type]
  (dis/dispatch! [:login-overlay-show login-type]))

;; Auth

(defn auth-settings-get
  "Entry point call for auth service."
  []
  (api/get-auth-settings (fn [body]
    (when body
      ;; auth settings loaded
      (api/get-current-user body (fn [data]
        (dis/dispatch! [:user-data (json->cljs data)])
        (utils/after 100 nux-actions/check-nux)))
      (dis/dispatch! [:auth-settings body])
      (check-user-walls)
      ;; Start teams retrieve if we have a link
      (team-actions/teams-get)))))

(defn auth-with-token-failed [error]
  (dis/dispatch! [:auth-with-token/failed error]))

;;Invitation
(defn invitation-confirmed [status body success]
 (when success
    (update-jwt body)
    (when (= status 201)
      (nux-actions/new-user-registered "email")
      (api/get-entry-point entry-point-get-finished)
      (auth-settings-get))
    ;; Go to password setup
    (router/nav! oc-urls/confirm-invitation-password))
  (dis/dispatch! [:invitation-confirmed success]))

(defn confirm-invitation [token]
  (api/confirm-invitation token invitation-confirmed))

;; Token authentication
(defn auth-with-token-success [token-type jwt]
  (api/get-auth-settings
   (fn [auth-body]
     (api/get-entry-point
      (fn [success body]
        (entry-point-get-finished success body)
        (let [orgs (:items (:collection body))
              to-org (utils/get-default-org orgs)]
          (router/redirect! (if to-org (oc-urls/org (:slug to-org)) oc-urls/user-profile)))))))
  (when (= token-type :password-reset)
    (cook/set-cookie! :show-login-overlay "collect-password"))
  (dis/dispatch! [:auth-with-token/success jwt]))

(defn auth-with-token-callback
  [token-type success body status]
  (if success
    (do
      (update-jwt body)
      (when (not= token-type :password-reset)
        (nux-actions/new-user-registered "email"))
      (auth-with-token-success token-type body))
    (cond
      (= status 401)
      (auth-with-token-failed 401)
      :else
      (auth-with-token-failed 500))))

(defn auth-with-token [token-type]
  (api/auth-with-token (:token (:query-params @router/path)) (partial auth-with-token-callback token-type))
  (dis/dispatch! [:auth-with-token token-type]))

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
        (api/get-entry-point entry-point-get-finished))
      (api/get-entry-point
       (fn [success body]
         (entry-point-get-finished success body
           (fn [orgs collection]
             (when (pos? (count orgs))
               (router/nav! (oc-urls/org (:slug (utils/get-default-org orgs))))))))))
    :else ;; Valid signup let's collect user data
    (do
      (update-jwt-cookie jwt)
      (nux-actions/new-user-registered "email")
      (utils/after 200 #(router/nav! oc-urls/sign-up-profile))
      (api/get-entry-point entry-point-get-finished)
      (dis/dispatch! [:signup-with-email/success]))))

(defn signup-with-email-callback
  [user-email success body status]
  (if success
    (signup-with-email-success user-email status body)
    (signup-with-email-failed status)))

(defn signup-with-email [signup-data]
  (api/signup-with-email
   (or (:firstname signup-data) "")
   (or (:lastname signup-data) "")
   (:email signup-data)
   (:pswd signup-data)
   (partial signup-with-email-callback (:email signup-data)))
  (dis/dispatch! [:signup-with-email]))

(defn signup-with-email-reset-errors []
  (dis/dispatch! [:input [:signup-with-email] {}]))

(defn pswd-collect [form-data password-reset?]
  (api/collect-password (:pswd form-data)
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
      (dis/dispatch! [:pswd-collect/finish status])))
  (dis/dispatch! [:pswd-collect password-reset?]))

(defn password-reset [email]
  (api/password-reset email
                      #(dis/dispatch! [:password-reset/finish %]))
  (dis/dispatch! [:password-reset]))

;; User Profile

(defn user-profile-save [current-user-data edit-data]
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
        without-has-changes (dissoc with-email :has-changes :loading)]
    (api/patch-user-profile
     current-user-data
     without-has-changes
     (fn [status body success]
       (if (= status 422)
         (dis/dispatch! [:user-profile-update/failed])
         (when success
           (utils/after 1000 jwt-refresh)
           (dis/dispatch! [:user-data (json->cljs body)])))))
    (dis/dispatch! [:user-profile-save])))

(defn user-profile-reset []
  (dis/dispatch! [:user-profile-reset]))

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

;; User profile tab

(defn change-user-profile-panel [panel]
  (dis/dispatch! [:input [:user-settings] panel]))

;; Debug

(defn force-jwt-refresh []
  (when (jwt/jwt) (jwt-refresh)))

(set! (.-OCWebForceRefreshToken js/window) force-jwt-refresh)