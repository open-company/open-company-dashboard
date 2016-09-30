(ns open-company-web.components.ui.login-button
  (:require [rum.core :as rum]
            [open-company-web.dispatcher :as dis]
            [open-company-web.lib.utils :as utils]
            [open-company-web.components.ui.login-overlay :refer (login-overlays-handler)]))

(rum/defcs login-button < rum/reactive
                          {:will-mount (fn [s]
                                        (when-not (utils/is-test-env?)
                                          (dis/dispatch! [:get-auth-settings]))
                                        s)}
  [s {:keys [button-classes]}]
  [:div.login-button
    (login-overlays-handler (rum/react dis/app-state))
    [:button
      {:class (str "btn-reset signup-signin " (when button-classes button-classes))
       :on-click #(dis/dispatch! [:show-login-overlay :login-with-slack])}
      "Sign In / Sign Up"]])