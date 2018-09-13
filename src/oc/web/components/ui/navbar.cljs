(ns oc.web.components.ui.navbar
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.dispatcher :as dis]
            [oc.web.router :as router]
            [oc.web.lib.jwt :as jwt]
            [oc.web.lib.utils :as utils]
            [oc.web.mixins.ui :as ui-mixins]
            [oc.web.lib.responsive :as responsive]
            [oc.web.components.ui.menu :as menu]
            [oc.web.components.ui.user-avatar :refer (user-avatar user-avatar-image)]
            [oc.web.components.ui.login-button :refer (login-button)]
            [oc.web.components.ui.orgs-dropdown :refer (orgs-dropdown)]
            [oc.web.components.ui.login-overlay :refer (login-overlays-handler)]
            [oc.web.components.search :refer (search-box)]))

(rum/defcs navbar < rum/reactive
                    (drv/drv :navbar-data)
                    (ui-mixins/render-on-resize nil)
                    {:did-mount (fn [s]
                     (when-not (utils/is-test-env?)
                       (when-not (responsive/is-tablet-or-mobile?)
                         (.tooltip (js/$ "[data-toggle=\"tooltip\"]"))))
                     s)}
  [s disabled-user-menu]
  (let [{:keys [current-user-data
                org-data
                board-data
                show-login-overlay
                mobile-navigation-sidebar
                mobile-menu-open
                orgs-dropdown-visible
                user-settings
                org-settings
                search-active]
         :as navbar-data} (drv/react s :navbar-data)
         is-mobile? (responsive/is-mobile-size?)
         active? (and (not mobile-menu-open)
                      (not orgs-dropdown-visible)
                      (not org-settings)
                      (not user-settings)
                      (not search-active))]
    [:nav.oc-navbar.group
      {:class (utils/class-set {:show-login-overlay show-login-overlay
                                :mobile-menu-open mobile-menu-open
                                :has-prior-updates (and (router/current-org-slug)
                                                        (pos?
                                                         (:count
                                                          (utils/link-for (:links org-data) "collection" "GET"))))
                                :not-fixed (or (utils/in? (:route @router/path) "all-posts")
                                               (utils/in? (:route @router/path) "dashboard"))
                                :showing-orgs-dropdown orgs-dropdown-visible
                                :can-edit-board (and (router/current-org-slug)
                                                     (not (:read-only org-data)))
                                :jwt (jwt/jwt)})}
      (when-not (utils/is-test-env?)
        (login-overlays-handler))
      [:div.oc-navbar-header.group
        [:div.oc-navbar-header-container.group
          (when active?
            [:div.mobile-underline])
          [:div.navbar-left
            [:button.mlb-reset.mobile-navigation-sidebar-ham-bt
              {:class (utils/class-set {:close-bt (or mobile-menu-open
                                                      (and is-mobile?
                                                           (or user-settings
                                                               org-settings)))
                                        :active active?})
               :on-click #(do
                            (menu/mobile-menu-close)
                            (if (and is-mobile?
                                     (or org-settings
                                         user-settings))
                              (do
                                (dis/dispatch! [:input [:user-settings] nil])
                                (dis/dispatch! [:input [:org-settings] nil]))
                              (when mobile-menu-open
                                (dis/dispatch! [:input [:mobile-menu-open] (not mobile-menu-open)]))))}]
           (search-box)]
          [:div.navbar-center
            (orgs-dropdown)]
          [:div.navbar-right
            (if is-mobile?
              [:button.btn-reset.mobile-menu.group
                {:on-click #(do
                             (when is-mobile?
                               (dis/dispatch! [:input [:user-settings] nil])
                               (dis/dispatch! [:input [:org-settings] nil]))
                             (dis/dispatch! [:input [:mobile-navigation-sidebar] false])
                             (menu/mobile-menu-toggle))}
                (user-avatar-image current-user-data)]
              (if (jwt/jwt)
                [:div.user-menu
                  (user-avatar
                   {:classes (str "mlb-reset" (if disabled-user-menu " disabled-user-menu" " dropdown-toggle"))
                    :disable-menu disabled-user-menu})
                  (when-not disabled-user-menu
                    (menu/menu))]
                (login-button)))]]]
      (when is-mobile?
        ;; Render the menu here only on mobile so it can expand the navbar
        (menu/menu))]))