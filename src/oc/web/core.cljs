(ns oc.web.core
  (:require [om.core :as om :include-macros true]
            [secretary.core :as secretary :refer-macros (defroute)]
            [dommy.core :as dommy :refer-macros (sel1)]
            [taoensso.timbre :as timbre]
            [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [cuerdas.core :as s]
            [oc.web.lib.medium-editor-exts]
            [oc.web.rum-utils :as ru]
            [oc.web.actions]
            [oc.web.api :as api]
            [oc.web.urls :as urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.local-settings :as ls]
            [oc.web.lib.jwt :as jwt]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.lib.raven :as sentry]
            [oc.web.lib.logging :as logging]
            [oc.web.lib.responsive :as responsive]
            [oc.web.lib.prevent-route-dispatch :refer (prevent-route-dispatch)]
            [oc.web.components.ui.loading :refer (rloading)]
            [oc.web.components.org-dashboard :refer (org-dashboard)]
            [oc.web.components.user-profile :refer (user-profile)]
            [oc.web.components.about :refer (about)]
            [oc.web.components.home-page :refer (home-page)]
            [oc.web.components.pricing :refer (pricing)]
            [oc.web.components.features :refer (features)]
            [oc.web.components.org-editor :refer (org-editor)]
            ; [oc.web.components.org-settings :refer (org-settings)]
            [oc.web.components.mobile-boards-list :refer (mobile-boards-list)]
            [oc.web.components.error-banner :refer (error-banner)]
            [oc.web.components.secure-activity :refer (secure-activity)]
            [oc.web.components.ui.onboard-wrapper :refer (onboard-wrapper)]))

(enable-console-print!)

(defn drv-root [om-component target]
  (swap! dis/app-state assoc :router-path @router/path)
  (ru/drv-root {:state dis/app-state
                :drv-spec (dis/drv-spec dis/app-state router/path)
                :component om-component
                :target target}))

;; setup Sentry error reporting
(defonce raven (sentry/raven-setup))

;; Avoid warnings
(declare route-dispatch!)

(defn check-get-params [query-params]
  (when (contains? query-params :browser-type)
    ; if :browser-type is "mobile" the mobile site is forced
    ; any other value will be set as big web
    ; remove the cookie to let it calculate the type of site
    ; Rules set via css won't be affected by this
    (cook/set-cookie! :force-browser-type (:browser-type query-params) (* 60 60 24 6))))

(defn inject-loading []
  (let [target (sel1 [:div#oc-loading])]
    (drv-root #(om/component (rloading {:nux (js/OCStaticGetCookie (js/OCStaticCookieName "nux"))})) target)))

(defn rewrite-url []
  (let [l (.-location js/window)
        rewrite-to (str (.-pathname l) (.-hash l))]
    ;; Push state only if the query string has parameters or the history will have duplicates.
    (when (seq (.-search l))
      (.pushState (.-history js/window) #js {} (.-title js/document) rewrite-to))))

(defn pre-routing [query-params & [should-rewrite-url]]
  ;; Setup timbre log level
  (when (:log-level query-params)
    (logging/config-log-level! (:log-level query-params)))
  ; make sure the menu is closed
  (let [pathname (.. js/window -location -pathname)]
    (when (not= pathname (s/lower pathname))
      (let [lower-location (str (s/lower pathname) (.. js/window -location -search) (.. js/window -location -hash))]
        (set! (.-location js/window) lower-location))))
  (swap! router/path {})
  (when (and (contains? query-params :jwt)
             (map? (js->clj (jwt/decode (:jwt query-params)))))
    ; contains :jwt, so saving it
    (cook/set-cookie! :jwt (:jwt query-params) (* 60 60 24 60) "/" ls/jwt-cookie-domain ls/jwt-cookie-secure))
  (check-get-params query-params)
  (when should-rewrite-url
    (rewrite-url))
  (inject-loading))

(defn post-routing []
  (utils/after 10 #(dis/dispatch! [:initial-loads])))

;; home
(defn home-handler [target params]
  (pre-routing (:query-params params) true)
  ;; save route
  (router/set-route! ["home"] {:query-params (:query-params params)})
  (post-routing)
  ;; render component
  (drv-root #(om/component (home-page)) target))

;; Company list
(defn org-handler [route target component params]
  (let [org (:org (:params params))
        board (:board (:params params))
        query-params (:query-params params)]
    (when org
      (cook/set-cookie! (router/last-org-cookie) org (* 60 60 24 6)))
    (when (= route "all-posts")
      (cook/set-cookie! (router/last-board-cookie org) "all-posts" (* 60 60 24 6)))
    (pre-routing query-params)
    ;; save route
    (router/set-route! [org route] {:org org :board board :query-params (:query-params params)})
    ;; load data from api
    (when-not (dis/org-data)
      (swap! dis/app-state merge {:loading true}))
    (post-routing)
    ;; render component
    (drv-root component target)))

(defn simple-handler [component route-name target params & [rewrite-url]]
  (pre-routing (:query-params params) rewrite-url)
  ;; save route
  (let [org (:org (:params params))]
    (router/set-route! (vec (remove nil? [route-name org])) {:org org :query-params (:query-params params)}))
  (post-routing)
  (when-not (contains? (:query-params params) :jwt)
    (when (contains? (:query-params params) :login-redirect)
      (cook/set-cookie!
       :login-redirect
       (:login-redirect (:query-params params))
       (* 60 60)
       "/"
       ls/jwt-cookie-domain ls/jwt-cookie-secure))
    ; remove om component if mounted to the same node
    (om/detach-root target)
    ;; render component
    (drv-root #(om/component (component)) target)))

;; Component specific to a board
(defn board-handler [route target component params & [board-sort-or-filter]]
  (let [org (:org (:params params))
        board (:board (:params params))
        entry (:entry (:params params))
        query-params (:query-params params)]
    (when org
      (cook/set-cookie! (router/last-org-cookie) org (* 60 60 24 6)))
    (when board
      (cook/set-cookie! (router/last-board-cookie org) board (* 60 60 24 6)))
    (pre-routing query-params true)
    ;; save the route
    (router/set-route!
     (vec
      (remove
       nil?
       [org board (when entry entry) route]))
     {:org org
      :board board
      :activity entry
      :query-params query-params
      :from-all-posts (contains? query-params :ap)})
    (when board-sort-or-filter
      (swap! dis/app-state assoc :board-filters board-sort-or-filter)
      (when (keyword? board-sort-or-filter)
        (cook/set-cookie!
         (router/last-board-filter-cookie org board)
         (name board-sort-or-filter)
         (* 60 60 24 30)
         "/"
         ls/jwt-cookie-domain
         ls/jwt-cookie-secure)))
    (let [nux-cookie (cook/get-cookie
                      (router/show-nux-cookie
                       (jwt/get-key :user-id)))
          show-nux (and (not (:show-login-overlay @dis/app-state))
                        (jwt/jwt)
                        (some #(= % nux-cookie) (vals router/nux-cookie-values)))
          loading (or (and ;; if is board page
                           (not (contains? query-params :ap))
                           ;; if the board data are not present
                           (not (:fixed-items (dis/board-data))))
                           ;; if the all-posts data are not preset
                      (and (contains? query-params :ap)
                           ;; this latter is used when displaying modal over AP
                           (not (:fixed-items (dis/all-posts-data)))))
          org-settings (if (and (contains? query-params :org-settings)
                                (#{:main :team :invite} (keyword (:org-settings query-params))))
                         (keyword (:org-settings query-params))
                         (when (contains? query-params :access)
                           :main))
          next-app-state {:nux (when show-nux :1)
                          :loading loading
                          :org-settings org-settings}]
      (utils/after 1 #(swap! dis/app-state merge next-app-state)))
    (post-routing)
    ;; render component
    (drv-root component target)))

;; Component specific to a secure activity
(defn secure-activity-handler [component route target params]
  (let [org (:org (:params params))
        secure-id (:secure-id (:params params))
        query-params (:query-params params)]
    (pre-routing query-params)
    ;; save the route
    (router/set-route!
     (vec
      (remove
       nil?
       [org route secure-id]))
     {:org org
      :secure-id secure-id
      :query-params query-params})
    ;; do we have the company data already?
    (when (or ;; if the company data are not present
              (not (dis/board-data))
              ;; or the entries key is missing that means we have only
              (not (:fixed-items (dis/board-data)))
              ;; a subset of the company data loaded with a SU
              (not (dis/secure-activity-data)))
      (swap! dis/app-state merge {:loading true}))
    (post-routing)
    ;; render component
    (drv-root component target)))

;; Component specific to a team settings
(defn team-handler [route target component params]
  (let [org (:org (:params params))
        query-params (:query-params params)]
    (pre-routing query-params true)
    ;; save the route
    (router/set-route! [org route] {:org org :query-params query-params})
    (post-routing)
    ;; render component
    (drv-root component target)))

(defn slack-lander-check [params]
  (pre-routing (:query-params params) true)
  (let [new-user (= (:new (:query-params params)) "true")]
    (when new-user
      (cook/set-cookie!
       (router/show-nux-cookie
        (jwt/get-key :user-id))
       (:new-user router/nux-cookie-values)
       (* 60 60 24 7)))
    (if new-user
      (utils/after 100 #(router/nav! urls/sign-up-profile))
      (dis/dispatch! [:entry-point-get {:slack-lander-check-team-redirect true}]))))

;; Routes - Do not define routes when js/document#app
;; is undefined because it breaks tests
(if-let [target (sel1 :div#app)]
  (do
    (defroute _loading_route "/__loading" {:as params}
      (timbre/info "Routing _loading_route __loading")
      (pre-routing (:query-params params)))

    (defroute login-route urls/login {:as params}
      (timbre/info "Routing login-route" urls/login)
      (when (and (not (contains? (:query-params params) :jwt))
                 (not (jwt/jwt)))
        (if (contains? (:query-params params) :slack)
          (swap! dis/app-state assoc :show-login-overlay :signup-with-slack)
          (swap! dis/app-state assoc :show-login-overlay :login-with-slack)))
      (simple-handler home-page "login" target params))

    (defroute signup-route urls/sign-up {:as params}
      (timbre/info "Routing signup-route" urls/sign-up)
      (simple-handler #(onboard-wrapper :lander) "sign-up" target params))

    (defroute signup-slash-route (str urls/sign-up "/") {:as params}
      (timbre/info "Routing signup-slash-route" (str urls/sign-up "/"))
      (simple-handler #(onboard-wrapper :lander) "sign-up" target params))

    (defroute signup-profile-route urls/sign-up-profile {:as params}
      (timbre/info "Routing signup-profile-route" urls/sign-up-profile)
      (when-not (jwt/jwt)
        (router/redirect! urls/sign-up))
      (simple-handler #(onboard-wrapper :lander-profile) "sign-up" target params))

    (defroute signup-profile-slash-route (str urls/sign-up-profile "/") {:as params}
      (timbre/info "Routing signup-profile-slash-route" (str urls/sign-up-profile "/"))
      (when-not (jwt/jwt)
        (router/redirect! urls/sign-up))
      (simple-handler #(onboard-wrapper :lander-profile) "sign-up" target params))

    (defroute signup-team-route urls/sign-up-team {:as params}
      (timbre/info "Routing signup-team-route" urls/sign-up-team)
      (when-not (jwt/jwt)
        (router/redirect! urls/sign-up))
      (simple-handler #(onboard-wrapper :lander-team) "sign-up" target params))

    (defroute signup-team-slash-route (str urls/sign-up-team "/") {:as params}
      (timbre/info "Routing signup-team-slash-route" (str urls/sign-up-team "/"))
      (when-not (jwt/jwt)
        (router/redirect! urls/sign-up))
      (simple-handler #(onboard-wrapper :lander-team) "sign-up" target params))

    (defroute slack-lander-check-route urls/slack-lander-check {:as params}
      (timbre/info "Routing slack-lander-check-route" urls/slack-lander-check)
      ;; Check if the user already have filled the needed data or if it needs to
      (slack-lander-check params))

    (defroute slack-lander-check-slash-route (str urls/slack-lander-check "/") {:as params}
      (timbre/info "Routing slack-lander-check-slash-route" (str urls/slack-lander-check "/"))
      ;; Check if the user already have filled the needed data or if it needs to
      (slack-lander-check params))

    (defroute about-route urls/about {:as params}
      (timbre/info "Routing about-route" urls/about)
      (simple-handler about "about" target params))

    (defroute features-route urls/features {:as params}
      (timbre/info "Routing features-route" urls/features)
      (simple-handler features "features" target params))

    (defroute pricing-route urls/pricing {:as params}
      (timbre/info "Routing pricing-route" urls/pricing)
      (simple-handler pricing "pricing" target params))

    (defroute email-confirmation-route urls/email-confirmation {:as params}
      (cook/remove-cookie! :jwt)
      (cook/remove-cookie! :login-redirect)
      (cook/remove-cookie! :show-login-overlay)
      (timbre/info "Routing email-confirmation-route" urls/email-confirmation)
      (simple-handler #(onboard-wrapper :email-verified) "email-verification" target params))

    (defroute password-reset-route urls/password-reset {:as params}
      (timbre/info "Routing password-reset-route" urls/password-reset)
      (when (jwt/jwt)
        (router/redirect! urls/home))
      (simple-handler #(onboard-wrapper :password-reset-lander) "password-reset" target params))

    (defroute confirm-invitation-route urls/confirm-invitation {:keys [query-params] :as params}
      (timbre/info "Routing confirm-invitation-route" urls/confirm-invitation)
      (when (empty? (:token query-params))
        (router/redirect! urls/home))
      (when (jwt/jwt)
        (cook/remove-cookie! :jwt)
        (cook/remove-cookie! :login-redirect)
        (cook/remove-cookie! :show-login-overlay))
      (simple-handler #(onboard-wrapper :invitee-lander) "confirm-invitation" target params))

    (defroute confirm-invitation-profile-route urls/confirm-invitation-profile {:as params}
      (timbre/info "Routing confirm-invitation-profile-route" urls/confirm-invitation-profile)
      (when-not (jwt/jwt)
        (router/redirect! urls/home))
      (simple-handler #(onboard-wrapper :invitee-lander-profile) "confirm-invitation" target params))

    ; (defroute subscription-callback-route urls/subscription-callback {}
    ;   (when-let [s (cook/get-cookie :subscription-callback-slug)]
    ;     (router/redirect! (urls/org-settings s))))

    (defroute email-wall-route urls/email-wall {:keys [query-params] :as params}
      (timbre/info "Routing email-wall-route" urls/email-wall)
      ; Email wall is shown only to not logged in users
      (when (jwt/jwt)
        (router/redirect! urls/home))
      (simple-handler #(onboard-wrapper :email-wall) "email-wall" target params true))

    (defroute email-wall-slash-route (str urls/email-wall "/") {:keys [query-params] :as params}
      (timbre/info "Routing email-wall-slash-route" (str urls/email-wall "/"))
      (when (jwt/jwt)
        (router/redirect! urls/home))
      (simple-handler #(onboard-wrapper :email-wall) "email-wall" target params true))

    (defroute home-page-route urls/home {:as params}
      (timbre/info "Routing home-page-route" urls/home)
      (home-handler target params))

    (defroute org-create-route urls/create-org {:as params}
      (timbre/info "Routing org-create-route" urls/create-org)
      (if (jwt/jwt)
        (do
          (pre-routing (:query-params params))
          (router/set-route! ["create-org"] {:query-params (:query-params params)})
          (api/get-entry-point)
          (api/get-auth-settings)
          (post-routing)
          (drv-root org-editor target))
        (router/redirect! urls/home)))

    (defroute logout-route urls/logout {:as params}
      (timbre/info "Routing logout-route" urls/logout)
      (cook/remove-cookie! :jwt)
      (cook/remove-cookie! :login-redirect)
      (cook/remove-cookie! :show-login-overlay)
      (router/redirect! urls/home))

    (defroute org-route (urls/org ":org") {:as params}
      (timbre/info "Routing org-route" (urls/org ":org"))
      (org-handler "org" target org-dashboard params))

    (defroute org-slash-route (str (urls/org ":org") "/") {:as params}
      (timbre/info "Routing org-slash-route" (str (urls/org ":org") "/"))
      (org-handler "org" target org-dashboard params))

    (defroute all-posts-route (urls/all-posts ":org") {:as params}
      (timbre/info "Routing all-posts-route" (urls/all-posts ":org"))
      (org-handler "all-posts" target org-dashboard (assoc-in params [:params :board] "all-posts")))

    (defroute all-posts-slash-route (str (urls/all-posts ":org") "/") {:as params}
      (timbre/info "Routing all-posts-slash-route" (str (urls/all-posts ":org") "/"))
      (org-handler "all-posts" target org-dashboard (assoc-in params [:params :board] "all-posts")))

    (defroute drafts-route (urls/drafts ":org") {:as params}
      (timbre/info "Routing board-route" (urls/drafts ":org"))
      (board-handler "dashboard" target org-dashboard (assoc-in params [:params :board] "drafts")))

    (defroute drafts-slash-route (str (urls/drafts ":org") "/") {:as params}
      (timbre/info "Routing board-slash-route" (str (urls/drafts ":org") "/"))
      (board-handler "dashboard" target org-dashboard (assoc-in params [:params :board] "drafts")))

    (defroute user-profile-route urls/user-profile {:as params}
      (timbre/info "Routing user-profile-route" urls/user-profile)
      (pre-routing (:query-params params))
      (router/set-route! ["user-profile"] {:query-params (:query-params params)})
      (post-routing)
      (if (jwt/jwt)
        (drv-root #(om/component (user-profile)) target)
        (router/redirect! urls/home)))

    (defroute secure-activity-route (urls/secure-activity ":org" ":secure-id") {:as params}
      (timbre/info "Routing secure-activity-route" (urls/secure-activity ":org" ":secure-id"))
      (secure-activity-handler #(om/component (secure-activity)) "secure-activity" target params))

    (defroute secure-activity-slash-route (str (urls/secure-activity ":org" ":secure-id") "/") {:as params}
      (timbre/info "Routing secure-activity-slash-route" (str (urls/secure-activity ":org" ":secure-id") "/"))
      (secure-activity-handler #(om/component (secure-activity)) "secure-activity" target params))

    (defroute boards-list-route (urls/boards ":org") {:as params}
      (timbre/info "Routing boards-list-route" (urls/boards ":org"))
      (swap! dis/app-state assoc :loading true)
      (if (responsive/is-mobile-size?)
        (simple-handler mobile-boards-list "boards-list" target params)
        (org-handler "boards-list" target #(om/component) params)))

    (defroute board-route (urls/board ":org" ":board") {:as params}
      (timbre/info "Routing board-route" (urls/board ":org" ":board"))
      (board-handler "dashboard" target org-dashboard params :latest))

    (defroute board-slash-route (str (urls/board ":org" ":board") "/") {:as params}
      (timbre/info "Routing board-route-slash" (str (urls/board ":org" ":board") "/"))
      (board-handler "dashboard" target org-dashboard params :latest))

    (defroute board-sort-by-topic-route (urls/board-sort-by-topic ":org" ":board") {:as params}
      (timbre/info "Routing board-sort-by-topic-route" (urls/board-sort-by-topic ":org" ":board"))
      (board-handler "dashboard" target org-dashboard params :by-topic))

    (defroute board-sort-by-topic-slash-route (str (urls/board-sort-by-topic ":org" ":board") "/") {:as params}
      (timbre/info "Routing board-sort-by-topic-slash-route" (str (urls/board-sort-by-topic ":org" ":board") "/"))
      (board-handler "dashboard" target org-dashboard params :by-topic))

    (defroute board-filter-by-topic-route (urls/board-filter-by-topic ":org" ":board" ":topic-filter") {:as params}
      (timbre/info "Routing board-filter-by-topic-route" (urls/board-filter-by-topic ":org" ":board" ":topic-filter"))
      (board-handler "dashboard" target org-dashboard params (:topic-filter (:params params))))

    (defroute
     board-filter-by-topic-slash-route
     (str (urls/board-filter-by-topic ":org" ":board" ":topic-filter") "/")
     {:as params}
      (timbre/info
       "Routing board-filter-by-topic-slash-route"
       (str (urls/board-filter-by-topic ":org" ":board" ":topic-filter") "/"))
      (board-handler "dashboard" target org-dashboard params (:topic-filter (:params params))))

    (defroute entry-route (urls/entry ":org" ":board" ":entry") {:as params}
      (timbre/info "Routing entry-route" (urls/entry ":org" ":board" ":entry"))
      (board-handler "activity" target org-dashboard params))

    (defroute entry-slash-route (str (urls/entry ":org" ":board" ":entry") "/") {:as params}
      (timbre/info "Routing entry-route" (str (urls/entry ":org" ":board" ":entry") "/"))
      (board-handler "activity" target org-dashboard params))

    (defroute not-found-route "*" []
      (timbre/info "Routing not-found-route" "*")
      ;; render component
      (router/redirect-404!))

    (def route-dispatch!
      (secretary/uri-dispatcher [_loading_route
                                 login-route
                                 ;; Signup email
                                 signup-profile-route
                                 signup-profile-slash-route
                                 signup-team-route
                                 signup-team-slash-route
                                 signup-route
                                 signup-slash-route
                                 ;; Signup slack
                                 slack-lander-check-route
                                 slack-lander-check-slash-route
                                 ;; Email wall
                                 email-wall-route
                                 email-wall-slash-route
                                 ;; Marketing site components
                                 about-route
                                 features-route
                                 pricing-route
                                 logout-route
                                 org-create-route
                                 email-confirmation-route
                                 confirm-invitation-route
                                 confirm-invitation-profile-route
                                 password-reset-route
                                 ;  ; subscription-callback-route
                                 ;; Home page
                                 home-page-route
                                 user-profile-route
                                 ;; Org routes
                                 org-route
                                 org-slash-route
                                 all-posts-route
                                 all-posts-slash-route
                                 ; org-settings-route
                                 ; org-settings-slash-route
                                 ; org-settings-team-route
                                 ; org-settings-team-slash-route
                                 ; org-settings-invite-route
                                 ; org-settings-invite-slash-route
                                 ; Drafts board
                                 drafts-route
                                 drafts-slash-route
                                 ; Secure activity route
                                 secure-activity-route
                                 secure-activity-slash-route
                                 ;; Boards
                                 boards-list-route
                                 board-route
                                 board-slash-route
                                 ; ;; Board sorting
                                 board-sort-by-topic-route
                                 board-sort-by-topic-slash-route
                                 ; Entry route
                                 entry-route
                                 entry-slash-route
                                 ; ;; Board filter
                                 board-filter-by-topic-route
                                 board-filter-by-topic-slash-route
                                 ;; Not found
                                 not-found-route]))

    (defn handle-url-change [e]
      (when-not @prevent-route-dispatch
        ;; we are checking if this event is due to user action,
        ;; such as click a link, a back button, etc.
        ;; as opposed to programmatically setting the URL with the API
        (when-not (.-isNavigation e)
          ;; in this case, we're setting it so
          ;; let's scroll to the top to simulate a navigation
          (js/window.scrollTo 0 0))
        ;; dispatch on the token
        (route-dispatch! (router/get-token))
        ; remove all the tooltips
        (utils/after 100 #(utils/remove-tooltips)))))
  (do
    (timbre/error "Error: div#app is not defined!")
    (sentry/capture-message "Error: div#app is not defined!")))

(defn init []
  ;; Setup timbre log level
  (logging/config-log-level! (or (:log-level (:query-params @router/path)) ls/log-level))
  ;; Persist JWT in App State
  (dis/dispatch! [:jwt (jwt/get-contents)])
  ;; on any click remove all the shown tooltips to make sure they don't get stuck
  (.click (js/$ js/window) #(utils/remove-tooltips))
  ; mount the error banner
  (drv-root #(om/component (error-banner)) (sel1 [:div#oc-error-banner]))
  ;; setup the router navigation only when handle-url-change and route-disaptch!
  ;; are defined, this is used to avoid crash on tests
  (when (and handle-url-change route-dispatch!)
    (router/setup-navigation! handle-url-change route-dispatch!)))

(defn on-js-reload []
  (.clear js/console)
  (route-dispatch! (router/get-token)))