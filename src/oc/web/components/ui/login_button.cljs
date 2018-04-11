(ns oc.web.components.ui.login-button
  (:require-macros [dommy.core :refer (sel1)])
  (:require [rum.core :as rum]
            [oc.web.actions.user :as user]
            [oc.web.lib.utils :as utils]))

(rum/defcs login-button < rum/static
                          rum/reactive
                          {:will-mount (fn [s]
                                        (when-not (utils/is-test-env?)
                                          (user/auth-settings-get))
                                        s)}
  [s {:keys [button-classes]}]
  [:div.login-button
    [:button
      {:class (str "btn-reset signup-signin " (when button-classes button-classes))
       :on-click #(user/show-login :login-with-slack)
       :on-touch-start identity}
      "Sign In"]
    [:span.signup-signin " / "]
    [:button
      {:class (str "btn-reset signup-signin " (when button-classes button-classes))
       :on-click #(user/show-login :signup-with-slack)
       :on-touch-start identity}
      "Sign Up"]])