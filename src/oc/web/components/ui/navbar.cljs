(ns oc.web.components.ui.navbar
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [dommy.core :as dommy :refer-macros (sel1)]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.lib.user :as lib-user]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.mixins.ui :as ui-mixins]
            [oc.web.stores.search :as search]
            [oc.web.components.ui.menu :as menu]
            [oc.web.utils.ui :refer (ui-compose)]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.search :as search-actions]
            [oc.web.actions.nav-sidebar :as nav-actions]
            [oc.web.components.search :refer (search-box)]
            [oc.web.components.ui.user-avatar :refer (user-avatar)]
            [oc.web.components.ui.login-button :refer (login-button)]
            [oc.web.components.ui.orgs-dropdown :refer (orgs-dropdown)]
            [oc.web.components.navigation-sidebar :as navigation-sidebar]
            [oc.web.components.ui.login-overlay :refer (login-overlays-handler)])
  (:import [goog.async Throttle]))

(defn- mobile-nav! [e board-slug]
  (router/nav! (oc-urls/board board-slug)))

(def scroll-top-offset 46)

(defn- check-scroll [s e]
  (when (and (> (.. js/document -scrollingElement -scrollTop) scroll-top-offset)
             (not @(::scrolled s)))
    (reset! (::scrolled s) true))
  (when (and (<= (.. js/document -scrollingElement -scrollTop) scroll-top-offset)
             @(::scrolled s))
    (reset! (::scrolled s) false)))

(rum/defcs navbar < rum/reactive
                    (drv/drv :navbar-data)
                    (drv/drv :cmail-state)
                    (drv/drv :show-add-post-tooltip)
                    (ui-mixins/render-on-resize nil)
                    (rum/local nil ::throttled-scroll-check)
                    (rum/local false ::scrolled)
                    (ui-mixins/on-window-scroll-mixin (fn [s e]
                     (when @(::throttled-scroll-check s)
                       (.fire @(::throttled-scroll-check s)))))
                    (drv/drv search/search-active?)
                    {:will-mount (fn [s]
                      (reset! (::throttled-scroll-check s) (Throttle. (partial check-scroll s) 500))
                     s)
                     :did-mount (fn [s]
                      (.fire @(::throttled-scroll-check s))
                     s)
                     :will-unmount (fn [s]
                      (when @(::throttled-scroll-check s)
                        (.dispose @(::throttled-scroll-check s)))
                     s)}
  [s]
  (let [{:keys [org-data
                board-data
                contributions-user-data
                show-login-overlay
                orgs-dropdown-visible
                search-active
                show-whats-new-green-dot
                panel-stack]
         :as navbar-data} (drv/react s :navbar-data)
         is-mobile? (responsive/is-mobile-size?)
         current-panel (last panel-stack)
         expanded-user-menu (= current-panel :menu)
         cmail-state (drv/react s :cmail-state)
         mobile-title (cond
                       (= (router/current-board-slug) "replies")
                       "Replies"
                       (= (router/current-board-slug) "inbox")
                       "Unread"
                       (= (router/current-board-slug) "all-posts")
                       "All"
                       (= (router/current-board-slug) "bookmarks")
                       "Bookmarks"
                       (= (router/current-board-slug) "following")
                       "Home"
                       (= (router/current-board-slug) "topics")
                       "Topics"
                       (and (router/current-contributions-id) (:self? contributions-user-data))
                       "You"
                       (and (router/current-contributions-id) (map? contributions-user-data))
                       (:name contributions-user-data)
                       :else
                       (:name board-data))
         search-active? (drv/react s search/search-active?)
         org-slug (router/current-org-slug)]
    [:nav.oc-navbar.group
      {:class (utils/class-set {:show-login-overlay show-login-overlay
                                :expanded-user-menu expanded-user-menu
                                :has-prior-updates (and org-slug
                                                        (pos?
                                                         (:count
                                                          (utils/link-for (:links org-data) "collection" "GET"))))
                                :showing-orgs-dropdown orgs-dropdown-visible

                                :can-edit-board (and org-slug
                                                     (not (:read-only org-data)))})}
      (when-not (utils/is-test-env?)
        (login-overlays-handler))
      (if (and is-mobile?
               search-active?)
        [:div.mobile-header
          [:button.mlb-reset.search-close-bt
            {:on-click (fn [e]
                         (utils/event-stop e)
                         (search-actions/reset)
                         (search-actions/inactive))}]
          [:div.mobile-header-title
            "Search"]]
        [:div.oc-navbar-header.group
          [:div.oc-navbar-header-container.group
            [:div.navbar-left
              (if is-mobile?
                [:button.mlb-reset.mobile-ham-menu
                  {:on-click #(dis/dispatch! [:update [:mobile-navigation-sidebar] not])}]
                (orgs-dropdown))]
            (if is-mobile?
              [:div.navbar-center
                [:div.navbar-mobile-title
                  mobile-title]]
              [:div.navbar-center
                {:class (when search-active "search-active")}
                (search-box)])
            (if (jwt/jwt)
              [:div.navbar-right.group
                [:div.user-menu
                  [:div.user-menu-button
                    {:ref "user-menu"
                     :class (when show-whats-new-green-dot "green-dot")
                     :data-toggle (when-not is-mobile? "tooltip")
                     :data-placement "bottom"
                     :title "Menu"}
                    (user-avatar
                     {:click-cb #(nav-actions/menu-toggle)})]]]
              [:div.navbar-right.anonymous-user
                (login-button)])]])]))
