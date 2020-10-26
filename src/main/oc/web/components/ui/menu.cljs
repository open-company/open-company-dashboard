(ns oc.web.components.ui.menu
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [dommy.core :as dommy :refer-macros (sel sel1)]
            [oc.web.expo :as expo]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.lib.chat :as chat]
            [oc.web.dispatcher :as dis]
            [oc.lib.cljs.useragent :as ua]
            [oc.web.lib.utils :as utils]
            [oc.web.mixins.ui :as mixins]
            [oc.web.local-settings :as ls]
            [oc.web.actions.jwt :as jwt-actions]
            [oc.web.lib.whats-new :as whats-new]
            [oc.web.actions.user :as user-actions]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.nav-sidebar :as nav-actions]
            [oc.web.components.ui.carrot-switch :refer (carrot-switch)]
            [oc.web.components.ui.user-avatar :refer (user-avatar-image)]))

(defn menu-close [& [s]]
  (nav-actions/menu-close))

(defn logout-click [s e]
  (.preventDefault e)
  (menu-close s)
  (jwt-actions/logout))

(defn profile-edit-click [s e]
  (.preventDefault e)
  ; (nav-actions/nav-to-author! e user-id (oc-urls/contributions user-id))
  (nav-actions/show-user-settings :profile))

(defn my-profile [s cur-user-id e]
  (.preventDefault e)
  (nav-actions/nav-to-author! e cur-user-id (oc-urls/contributions cur-user-id)))

(defn my-posts-click [s cur-user-id e]
  (.preventDefault e)
  (menu-close s)
  (nav-actions/nav-to-author! e cur-user-id (oc-urls/contributions cur-user-id)))

(defn notifications-settings-click [s e]
  (.preventDefault e)
  (nav-actions/show-user-settings :notifications))

(defn team-settings-click [s e]
  (.preventDefault e)
  (nav-actions/show-org-settings :org))

(defn manage-team-click [s e]
  (.preventDefault e)
  (nav-actions/show-org-settings :team))

(defn invite-team-click [s e]
  (.preventDefault e)
  (nav-actions/show-org-settings :invite-picker))

(defn integrations-click [s e]
  (.preventDefault e)
  (nav-actions/show-org-settings :integrations))

(defn sign-in-sign-up-click [s e]
  (menu-close s)
  (.preventDefault e)
  (user-actions/show-login :login-with-slack))

(defn whats-new-click [s e]
  (.preventDefault e)
  (whats-new/show))

(defn reminders-click [s e]
  (.preventDefault e)
  (nav-actions/show-reminders))

(defn payments-click [e]
  (.preventDefault e)
  (nav-actions/show-org-settings :payments))

(defn- detect-desktop-app
  []
  (when-not ua/desktop-app?
    (cond
      ua/mac? {:title "Download Mac app"
               :href "https://github.com/open-company/open-company-web/releases/latest/download/Carrot.dmg"}
      ua/windows? {:title "Download Windows app"
                   :href "https://github.com/open-company/open-company-web/releases/latest/download/Carrot.exe"}
      :default nil)))

(defn- get-desktop-version
  []
  (if (.-getElectronAppVersion js/OCCarrotDesktop)
    (str "Version " (.getElectronAppVersion js/OCCarrotDesktop))
    ""))

(defn- theme-settings-click [s e]
  (.preventDefault e)
  (nav-actions/show-theme-settings))

(rum/defcs menu < rum/reactive
                  (drv/drv :navbar-data)
                  (drv/drv :current-user-data)
                  (drv/drv :expo-app-version)
  mixins/refresh-tooltips-mixin
  {:did-mount (fn [s]
   (when (responsive/is-mobile-size?)
     (whats-new/check-whats-new-badge))
   s)
   :did-remount (fn [_ s]
   (when (responsive/is-mobile-size?)
     (whats-new/check-whats-new-badge))
    s)}
  [s]
  (let [{:keys [panel-stack org-data board-data current-org-slug]} (drv/react s :navbar-data)
        current-user-data (drv/react s :current-user-data)
        is-mobile? (responsive/is-mobile-size?)
        show-reminders? (when ls/reminders-enabled?
                          (utils/link-for (:links org-data) "reminders"))
        expanded-user-menu (= (last panel-stack) :menu)
        is-admin-or-author? (#{:admin :author} (:role current-user-data))
        expo-app-version (drv/react s :expo-app-version)
        show-invite-people? (and current-org-slug
                                 is-admin-or-author?)
        desktop-app-data (detect-desktop-app)
        web-app-main-version "3.0"
        web-app-version (str ls/product-name " " web-app-main-version (when (seq ls/sentry-release-deploy) (str " build: " ls/sentry-release-deploy)))
        app-version (cond
                      ua/mobile-app? (str "Version " expo-app-version " (" web-app-version ")")
                      ua/desktop-app? (str (get-desktop-version) " (" web-app-version ")")
                      :else web-app-version)
        env-endpoint (when (not= ls/sentry-env "production")
                       (str "Endpoint: " ls/web-hostname))
        show-billing? (and ls/payments-enabled
                           (= (:role current-user-data) :admin)
                           current-org-slug)]
    [:div.menu
      {:class (utils/class-set {:expanded-user-menu expanded-user-menu})
       :on-click #(when-not (utils/event-inside? % (rum/ref-node s :menu-container))
                    (menu-close s))}
      [:button.mlb-reset.modal-close-bt
        {:on-click #(menu-close s)}]
      [:div.menu-container-outer
        [:div.menu-container
          {:ref :menu-container}
          [:div.menu-header.group
            [:button.mlb-reset.mobile-close-bt
              {:on-click #(menu-close s)}]
            (when is-mobile?
              (user-avatar-image current-user-data))
            [:div.user-name
              {:class utils/hide-class}
              (str (jwt/get-key :first-name) " " (jwt/get-key :last-name))]
            (when-not is-mobile?
              (user-avatar-image current-user-data))]
          ;; Profile
          (when (and (jwt/jwt)
                    current-user-data)
            [:a
              {:href "#"
              :on-click (partial profile-edit-click s)}
              [:div.oc-menu-item.personal-profile
                "My profile"]])
          ;; Show user's posts link
          ; (when (and (jwt/jwt)
          ;            current-user-data)
          ;   [:a
          ;     {:href (oc-urls/contributions (:user-id current-user-data))
          ;      :on-click (partial my-posts-click s (:user-id current-user-data))}
          ;     [:div.oc-menu-item.my-posts.group
          ;       [:span.oc-menu-item-label
          ;         (if (pos? (:contributions-count org-data))
          ;           "My updates"
          ;           "My profile")]
          ;       (when (pos? (:contributions-count org-data))
          ;         [:span.count
          ;           (:contributions-count org-data)])]])
          ;; Notifications
          (when (and (jwt/jwt)
                    (not is-mobile?))
            [:a
              {:href "#"
              :on-click (partial notifications-settings-click s)}
              [:div.oc-menu-item.notifications-settings
                "Notifications"]])
          ;; Theme switcher separator
          (when (jwt/jwt)
            [:div.oc-menu-separator])
          ;; Theme switcher
          [:a
            {:href "#"
            :on-click (partial theme-settings-click s)}
            "Dark mode"]
          ;; Reminders separator
          (when (and show-reminders?
                    (not is-mobile?))
            [:div.oc-menu-separator])
          ;; Reminders
          (when (and show-reminders?
                    (not is-mobile?))
            [:a
              {:href "#"
              :on-click #(reminders-click s %)}
              [:div.oc-menu-item.reminders
                "Recurring updates"]])
          ;; Settings separator
          (when (or current-org-slug
                    show-invite-people?
                    show-billing?)
            [:div.oc-menu-separator])
          ;; Admin settings
          (when (and (not is-mobile?)
                    (= (:role current-user-data) :admin)
                    current-org-slug)
            [:a
              {:href "#"
              :on-click #(team-settings-click s %)}
              [:div.oc-menu-item.digest-settings
                "Admin settings"]])
          ;; Invite
          (when show-invite-people?
            [:a
              {:href "#"
              :on-click #(invite-team-click s %)}
              [:div.oc-menu-item.invite-team
                "Invite people"]])
          ;; Manage team
          (when (and (not is-mobile?)
                    current-org-slug)
            [:a
              {:href "#"
              :on-click #(manage-team-click s %)}
              [:div.oc-menu-item.manage-team
                (if (= (:role current-user-data) :admin)
                  "Manage team"
                  "View team")]])
          ;; Integrations
          (when (and (not is-mobile?)
                    current-org-slug
                    (= (:role current-user-data) :admin))
            [:a
              {:href "#"
              :on-click #(integrations-click s %)}
              [:div.oc-menu-item.team-integrations
                "Integrations"]])
          ;; Billing
          (when (and (not is-mobile?)
                    show-billing?)
            [:a.payments
              {:href "#"
              :on-click payments-click}
              [:div.oc-menu-item
                "Billing"]])
          ;; What's new & Support separator
          [:div.oc-menu-separator]
          ;; What's new
          [:a.whats-new-link
            (if ua/mobile?
              {:href oc-urls/what-s-new
              :target "_blank"}
              {:on-click (partial whats-new-click s)})
            [:div.oc-menu-item.whats-new
              "What’s new"]]
          ;; Support
          [:a
            {:class "intercom-chat-link"
            :href oc-urls/contact-mail-to}
            [:div.oc-menu-item.support
              "Get support"]]
          ;; Mobile billing
          (when (and is-mobile?
                    show-billing?)
            [:a.payments
              {:href "#"
              :on-click payments-click}
              [:div.oc-menu-item
                "Billing"]])
          ;; Desktop app
          (when desktop-app-data
            [:a
              {:href (:href desktop-app-data)
              :target "_blank"}
              [:div.oc-menu-item.native-app
                (:title desktop-app-data)
                [:span.beta "BETA"]]])
          ;; Logout separator
          [:div.oc-menu-separator]
          ;; Logout
          (if (jwt/jwt)
            [:a.sign-out
              {:href oc-urls/logout :on-click (partial logout-click s)}
              [:div.oc-menu-item.logout
                "Sign out"]]
            [:a {:href "" :on-click (partial sign-in-sign-up-click s)}
              [:div.oc-menu-item
                "Sign in / Sign up"]])
          ;; Version separator
          (when ua/pseudo-native?
            [:div.oc-menu-separator])]
        ;; Version
        (when (seq app-version)
          [:div.app-info.app-version
             app-version])
        (when (seq env-endpoint)
          [:div.app-info.env-endpoint
            env-endpoint])]]))
