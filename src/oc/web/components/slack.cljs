(ns oc.web.components.slack
  (:require [rum.core :as rum]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.user :as user-actions]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.components.ui.shared-misc :as shared-misc]
            [oc.web.components.ui.site-header :refer (site-header)]
            [oc.web.components.ui.site-footer :refer (site-footer)]
            [oc.web.components.ui.site-mobile-menu :refer (site-mobile-menu)]
            [oc.web.components.ui.carrot-box-thanks :refer (carrot-box-thanks)]
            [oc.web.components.ui.login-overlay :refer (login-overlays-handler)]))

(defn get-started-button [auth-settings]
  (when-not (jwt/jwt)
    [:div
      [:button.signin-with-slack.mlb-reset
        {:on-click #(do
                     (.preventDefault %)
                     (when-let [auth-link (utils/link-for (:links auth-settings) "authenticate" "GET"
                                           {:auth-source "slack"})]
                       (user-actions/login-with-slack auth-link)))}
        [:span.slack-white-icon]
        [:span.slack-copy "Add to Slack"]]]))

(rum/defcs slack < rum/static
                   rum/reactive
                   (drv/drv :auth-settings)
  [s]
  (let [auth-settings (drv/react s :auth-settings)]
    [:div
      [:div.slack-wrap
        {:id "wrap"}
        (site-header auth-settings true)
        (site-mobile-menu)
        (login-overlays-handler)

        [:div.main.slack
          ; Hope page header
          [:section.carrot-plus-slack.group
            shared-misc/animation-lightbox

            [:a.carrot-logo
              {:href "/?no_redirect=1"}]

            [:h1.slack-headline
              "Clarity for Slack teams"]

            [:div.slack-subline
              (str
               "Carrot makes sure key leadership communication "
               "doesn't get lost in fast-moving conversations.")]

            ; (try-it-form "try-it-form-central" "try-it-combo-field-top")
            [:div.slack-button-container.group
              shared-misc/show-animation-button
              [:button.mlb-reset.add-to-slack-button
                {:on-click #(do
                             (.preventDefault %)
                             (when-let [auth-link (utils/link-for (:links auth-settings) "authenticate" "GET"
                                                   {:auth-source "slack"})]
                               (user-actions/login-with-slack auth-link)))}]]
            shared-misc/no-credit-card
            (carrot-box-thanks "carrot-box-thanks-top")
            [:div.carrot-box-container.confirm-thanks.group
              {:style {:display "none"}}
              [:div.carrot-box-thanks
                [:div.thanks-headline "You are Confirmed!"]
                [:div.thanks-subheadline "Thank you for subscribing."]]]

            [:div.main-animation-container
              [:img.main-animation
                {:src (utils/cdn "/img/ML/slack_screenshot.png")
                 :srcSet (str (utils/cdn "/img/ML/slack_screenshot@2x.png") " 2x")}]]]

          shared-misc/keep-aligned-section

          shared-misc/testimonials-section

          shared-misc/keep-aligned-bottom]]

        (site-footer)]))