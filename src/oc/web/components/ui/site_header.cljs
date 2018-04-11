(ns oc.web.components.ui.site-header
  "Component for the site header. This is copied into oc.core/nav
   and every change here should be reflected there and vice versa."
  (:require [rum.core :as rum]
            [dommy.core :as dommy :refer-macros (sel1)]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.actions.user :as user]
            [oc.web.local-settings :as ls]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.user :as user-actions]
            [oc.web.components.ui.site-mobile-menu :as site-mobile-menu]
            [oc.web.components.ui.try-it-form :refer (get-started-button)]))

;; NB: this has a clone in oc.core/nav, every change should be reflected there and vice-versa

(defn nav! [uri e]
  (.preventDefault e)
  (when (responsive/is-mobile-size?)
    (site-mobile-menu/site-menu-toggle true))
  (user/show-login nil)
  (router/nav! uri))

(rum/defc site-header < rum/static
  [auth-settings use-slack-signup-button]
  ; <!-- Nav Bar -->
  (let [logged-in (jwt/jwt)
        your-digest (when logged-in (utils/your-digest-url))
        slack-auth-link (utils/link-for (:links auth-settings) "authenticate" "GET"
                         {:auth-source "slack"})]
    [:nav.site-navbar
      [:div.site-navbar-container
        [:a.navbar-brand-left
          {:href oc-urls/home
           :on-click (partial nav! oc-urls/home)}]
        [:div.site-navbar-right.big-web-only
          (when-not logged-in
            [:a.login
              {:href (utils/your-digest-url)
               :class (when logged-in "your-digest")
               :on-click (fn [e]
                           (.preventDefault e)
                           (if logged-in
                             (nav! (utils/your-digest-url) e)
                             (nav! oc-urls/login e))
                           (user/show-login :login-with-slack))}
                "Log in"])
          [:a.start
            {:href (if logged-in
                    your-digest
                    oc-urls/sign-up)
             :class (utils/class-set {:your-digest logged-in
                                      :slack-get-started use-slack-signup-button})
             :on-click (fn [e]
                         (.preventDefault e)
                         (if logged-in
                          (nav! your-digest e)
                          (if use-slack-signup-button
                            (user-actions/login-with-slack slack-auth-link)
                            (nav! oc-urls/sign-up e))))}
            (if logged-in
              [:span.go-to-digest
                "Go to digest"]
              (if use-slack-signup-button
                [:span
                  "Sign up with "
                  [:span.slack-orange-icon]]
                "Start"))]]
        [:div.site-navbar-right.mobile-only
          (if logged-in
            [:a.mobile-your-digest
              {:href your-digest
               :on-click (partial nav! your-digest)}
              [:span.go-to-digest
                "Go to digest"]]
            [:a.start
              {:href oc-urls/sign-up
               :on-click (fn [e]
                           (.preventDefault e)
                           (if logged-in
                             (nav! your-digest e)
                             (if use-slack-signup-button
                               (user-actions/login-with-slack slack-auth-link)
                               (nav! oc-urls/sign-up e))))
               :class (when use-slack-signup-button "slack-get-started")}
                (if use-slack-signup-button
                  [:span
                    "Sign up with "
                    [:span.slack-orange-icon]]
                  "Start")])]
        [:div.mobile-ham-menu.mobile-only
          {:on-click #(site-mobile-menu/site-menu-toggle)}]]]))