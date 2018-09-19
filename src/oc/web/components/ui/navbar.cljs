(ns oc.web.components.ui.navbar
  (:require [rum.core :as rum]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.lib.jwt :as jwt]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.mixins.ui :as ui-mixins]
            [oc.web.components.ui.menu :as menu]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.search :as search-actions]
            [oc.web.components.search :refer (search-box)]
            [oc.web.components.ui.login-button :refer (login-button)]
            [oc.web.components.ui.orgs-dropdown :refer (orgs-dropdown)]
            [oc.web.components.user-notifications :refer (user-notifications)]
            [oc.web.components.ui.login-overlay :refer (login-overlays-handler)]
            [oc.web.components.ui.user-avatar :refer (user-avatar user-avatar-image)]))

(rum/defcs navbar < rum/reactive
                    (drv/drv :navbar-data)
                    (ui-mixins/render-on-resize nil)
                    (rum/local false ::expanded-user-menu)
                    (rum/local nil ::window-click)
                    {:will-mount (fn [s]
                      (reset! (::window-click s)
                       (events/listen js/window EventType/CLICK
                        #(when-not (utils/event-inside? % (rum/ref-node s "user-menu"))
                           (reset! (::expanded-user-menu s) false))))
                      s)
                     :did-mount (fn [s]
                     (when-not (utils/is-test-env?)
                       (when-not (responsive/is-tablet-or-mobile?)
                         (.tooltip (js/$ "[data-toggle=\"tooltip\"]"))))
                     s)
                     :will-unmount (fn [s]
                      (when @(::window-click s)
                        (events/unlistenByKey @(::window-click s))
                        (reset! (::window-click s) nil))
                      s)}
  [s]
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
                                               (utils/in? (:route @router/path) "must-see")
                                               (utils/in? (:route @router/path) "dashboard"))
                                :showing-orgs-dropdown orgs-dropdown-visible
                                :can-edit-board (and (router/current-org-slug)
                                                     (not (:read-only org-data)))})}
      [:div.mobile-bottom-line
        {:class (utils/class-set {:search search-active
                                  :user-menu (or mobile-menu-open
                                                 (and is-mobile?
                                                      (or user-settings
                                                          org-settings)))})}
        [:div.orange-active-tab]]
      (when-not (utils/is-test-env?)
        (login-overlays-handler))
      [:div.oc-navbar-header.group
        [:div.oc-navbar-header-container.group
          [:div.navbar-left
            [:button.mlb-reset.mobile-navigation-sidebar-ham-bt
              {:class (utils/class-set {:active active?})
               :on-click #(do
                            (search-actions/inactive)
                            (menu/mobile-menu-close)
                            (if (and is-mobile?
                                     (or org-settings
                                         user-settings))
                              (do
                                (dis/dispatch! [:input [:user-settings] nil])
                                (dis/dispatch! [:input [:org-settings] nil]))))}]
           (if is-mobile?
             [:button.mlb-reset.search-bt
               {:on-click #(do
                             (menu/mobile-menu-close)
                             (search-actions/active)
                             (utils/after 500 search-actions/focus))
                :class (when search-active "active")}]
             (orgs-dropdown))]
          [:div.navbar-center
            {:class (when search-active "search-active")}
            (if is-mobile?
             (orgs-dropdown)
             (search-box))]
          [:div.navbar-right
            (if is-mobile?
              [:button.btn-reset.mobile-menu.group
                {:on-click #(do
                             (search-actions/inactive)
                             (when is-mobile?
                               (dis/dispatch! [:input [:user-settings] nil])
                               (dis/dispatch! [:input [:org-settings] nil]))
                             (dis/dispatch! [:input [:mobile-navigation-sidebar] false])
                             (menu/mobile-menu-toggle))}
                (user-avatar-image current-user-data)]
              (if (jwt/jwt)
                [:div.group
                  (user-notifications)
                  [:div.user-menu
                    {:ref "user-menu"}
                    (user-avatar
                     {:click-cb #(swap! (::expanded-user-menu s) not)})
                    (when @(::expanded-user-menu s)
                      (menu/menu))]]
                (login-button)))]]]
      (when is-mobile?
        ;; Render the menu here only on mobile so it can expand the navbar
        (menu/menu))]))