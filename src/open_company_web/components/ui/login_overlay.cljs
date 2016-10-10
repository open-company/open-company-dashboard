(ns open-company-web.components.ui.login-overlay
  (:require [rum.core :as rum]
            [dommy.core :as dommy :refer-macros (sel1)]
            [clojure.string :as s]
            [goog.object :as gobj]
            [goog.style :as gstyle]
            [open-company-web.urls :as oc-url]
            [open-company-web.dispatcher :as dis]
            [open-company-web.lib.utils :as utils]
            [open-company-web.lib.oc-colors :as occ]
            [open-company-web.lib.responsive :as responsive]
            [open-company-web.components.ui.icon :as i]
            [open-company-web.components.ui.small-loading :refer (small-loading)]))

(defn close-overlay [e]
  (utils/event-stop e)
  (dis/dispatch! [:show-login-overlay false]))

(def dont-scroll
  {:before-render (fn [s]
                    (if (responsive/is-mobile?)
                      (let [display-none #js {:display "none"}]
                        (when (sel1 [:div.main])
                          (gstyle/setStyle (sel1 [:div.main]) display-none))
                        (when (sel1 [:nav.navbar-bottom])
                          (gstyle/setStyle (sel1 [:nav.navbar-bottom]) display-none))
                        (when (sel1 [:nav.navbar-static-top])
                          (gstyle/setStyle (sel1 [:nav.navbar-static-top]) display-none))
                        (when (sel1 [:div.fullscreen-page])
                          (gstyle/setStyle (sel1 [:div.fullscreen-page]) display-none)))
                      (dommy/add-class! (sel1 [:body]) :no-scroll))
                    (when-not (:auth-settings @dis/app-state)
                      (dis/dispatch! [:get-auth-settings]))
                    s)
   :will-unmount (fn [s]
                   (if (responsive/is-mobile?)
                    (let [display-block #js {:display "block"}]
                      (when (sel1 [:div.main])
                        (gstyle/setStyle (sel1 [:div.main]) display-block))
                      (when (sel1 [:nav.navbar-bottom])
                        (gstyle/setStyle (sel1 [:nav.navbar-bottom]) display-block))
                      (when (sel1 [:nav.navbar-static-top])
                        (gstyle/setStyle (sel1 [:nav.navbar-static-top]) display-block))
                      (when (sel1 [:div.fullscreen-page])
                          (gstyle/setStyle (sel1 [:div.fullscreen-page]) display-block)))
                    (dommy/remove-class! (sel1 [:body]) :no-scroll))
                   s)})

(rum/defc close-button
  []
  [:button.close {:on-click (partial close-overlay)}
    (let [close-color (if (responsive/is-mobile?) (occ/get-color-by-kw :oc-gray-5) "white")]
      (i/icon :simple-remove {:class "inline mr1" :stroke "4" :color close-color :accent-color close-color}))])

(rum/defcs login-signup-with-slack < rum/reactive
                                     dont-scroll
  [state]
  [:div.login-overlay-container.group
    {:on-click (partial close-overlay)}
    (close-button)
    [:div.login-overlay.login-with-slack
      {:on-click #(utils/event-stop %)}
      [:div.login-overlay-cta.pl2.pr2.group
        (cond
          (= (:show-login-overlay (rum/react dis/app-state)) :signup-with-slack)
          [:div.sign-in-cta.left "Sign Up"]
          :else
          [:div.sign-in-cta.left "Sign In"])]
      [:div.pt2.pl3.pr3.group.center
        (cond
          (= (:slack-access (rum/react dis/app-state)) "denied")
          [:div.block.red
            "OpenCompany requires verification with your Slack team. Please allow access."
            [:p.my2.h5 "If Slack did not allow you to authorize OpenCompany, try "
              [:button.p0.btn-reset.underline
                {:on-click #(do (utils/event-stop %)
                                (dis/dispatch! [:login-with-slack false]))}
                "this link instead."]]]
          (:slack-access (rum/react dis/app-state))
          [:span.block.red
            "There is a temporary error validating with Slack. Please try again later."])
        [:button.btn-reset.mt2.login-button
          {:on-click #(do
                        (.preventDefault %)
                        (when (:auth-settings @dis/app-state)
                          (dis/dispatch! [:login-with-slack true])))}
          [:img {:src "https://api.slack.com/img/sign_in_with_slack.png"}]
          (when-not (:auth-settings (rum/react dis/app-state))
            (small-loading))]
        [:div.login-with-email.domine.underline.bold
          [:a {:on-click #(do (utils/event-stop %)
                              (dis/dispatch! [:show-login-overlay (if (= (:show-login-overlay @dis/app-state) :signup-with-slack) :signup-with-email :login-with-email)]))}
            (cond
              (= (:show-login-overlay (rum/react dis/app-state)) :signup-with-slack)
              "OR SIGN UP VIA EMAIL"
              :else
              "OR SIGN IN VIA EMAIL")]]]
        [:div.login-overlay-footer.py2.px3.mt1.group
          (cond
              (= (:show-login-overlay (rum/react dis/app-state)) :signup-with-slack)
              [:a.left {:on-click #(dis/dispatch! [:show-login-overlay :login-with-email])}
                "Already have an account? "
                 [:span.underline "SIGN IN NOW."]]
              :else
              [:a.left {:on-click #(dis/dispatch! [:show-login-overlay :signup-with-email])}
                "Don't have an account? "
                 [:span.underline "SIGN UP NOW."]])]]])

(rum/defcs login-with-email < rum/reactive
                              (merge dont-scroll
                                {:did-mount (fn [s] (.focus (sel1 [:input.email])) s)})
  [state]
  [:div.login-overlay-container.group
    {:on-click (partial close-overlay)}
    (close-button)
    [:div.login-overlay.login-with-email.group
      {:on-click #(utils/event-stop %)}
      [:div.login-overlay-cta.pl2.pr2.group
        [:div.sign-in-cta "Sign In"
          (when-not (:auth-settings (rum/react dis/app-state))
            (small-loading))]]
      [:div.pt2.pl3.pr3.pb2.group
        (when-not (nil? (:login-with-email-error (rum/react dis/app-state)))
          (cond
            (= (:login-with-email-error (rum/react dis/app-state)) 401)
            [:span.small-caps.red
              "The email or password you entered is incorrect."
              [:br]
              "Please try again, or "
              [:a.underline.red {:on-click #(dis/dispatch! [:show-login-overlay :password-reset])} "reset your password"]
              "."]
            :else
            [:span.small-caps.red
              "System troubles logging in."
              [:br]
              "Please try again, then "
              [:a.underline.red {:href oc-url/contact-mail-to} "contact support"]
              "."]))
        [:form.sign-in-form
          [:div.sign-in-label-container
            [:label.sign-in-label "EMAIL"]]
          [:div.sign-in-field-container
            [:input.sign-in-field.email
              {:value (:email (:login-with-email (rum/react dis/app-state)))
               :on-change #(dis/dispatch! [:login-with-email-change :email (.-value (sel1 [:input.email]))])
               :type "email"
               :tabIndex 1
               :autoCapitalize "none"
               :name "email"}]]
          [:div.sign-in-label-container
            [:label.sign-in-label "PASSWORD"]]
          [:div.sign-in-field-container
            [:input.sign-in-field.pswd
              {:value (:pswd (:login-with-email (rum/react dis/app-state)))
               :on-change #(dis/dispatch! [:login-with-email-change :pswd (.-value (sel1 [:input.pswd]))])
               :type "password"
               :tabIndex 2
               :name "pswd"}]]
          [:div.group.pb3.mt3
            [:div.left.forgot-password
              [:a {:on-click #(dis/dispatch! [:show-login-overlay :password-reset])} "FORGOT PASSWORD?"]]
            [:div.right
              [:button.btn-reset.btn-solid
                {:disabled (nil? (:email (:auth-settings (rum/react dis/app-state))))
                 :on-click #(do
                              (.preventDefault %)
                              (dis/dispatch! [:login-with-email]))}
                "SIGN IN"]]]]]
      [:div.login-overlay-footer.py2.px3.mt1.group
        [:a.left {:on-click #(do (utils/event-stop %) (dis/dispatch! [:show-login-overlay :signup-with-slack]))}
          "Don't have an account? "
          [:span.underline "SIGN UP NOW."]]]]])

(rum/defcs signup-with-email < rum/reactive
                               (merge dont-scroll
                                 {:did-mount (fn [s] (.focus (sel1 [:input.firstname])) s)})
  [state]
  [:div.login-overlay-container.group
    {:on-click (partial close-overlay)}
    (close-button)
    [:div.login-overlay.signup-with-email.group
      {:on-click #(utils/event-stop %)}
      [:div.login-overlay-cta.pl2.pr2.group
        [:div.sign-in-cta "Sign Up"
          (when-not (:auth-settings (rum/react dis/app-state))
            (small-loading))]]
      [:div.pt2.pl3.pr3.pb2.group
        (when-not (nil? (:signup-with-email-error (rum/react dis/app-state)))
          (cond
            (= (:signup-with-email-error (rum/react dis/app-state)) 409)
            [:span.small-caps.red
              "The email or password you entered is incorrect."
              [:br]
              "Please try again, or "
              [:a.underline.red {:on-click #(dis/dispatch! [:show-login-overlay :password-reset])} "reset your password"]
              "."]
            (= (:signup-with-email-error (rum/react dis/app-state)) 400)
            [:span.small-caps.red
              "An error occurred while processing your data, please check the fields and try again."]
            :else
            [:span.small-caps.red
              "System troubles logging in."
              [:br]
              "Please try again, then "
              [:a.underline.red {:href oc-url/contact-mail-to} "contact support"]
              "."]))
        [:form.sign-in-form
          [:div.sign-in-label-container
            [:label.sign-in-label {:for "signup-firstname"} "YOUR NAME"]]
          [:div.sign-in-field-container.group
            [:input.sign-in-field.firstname.half.left
              {:value (:firstname (:signup-with-email (rum/react dis/app-state)))
               :id "signup-firstname"
               :on-change #(dis/dispatch! [:signup-with-email-change :firstname (.-value (sel1 [:input.firstname]))])
               :placeholder "First name"
               :type "text"
               :tabIndex 1
               :name "firstname"}]
            [:input.sign-in-field.lastname.half.right
              {:value (:lastname (:signup-with-email (rum/react dis/app-state)))
               :id "signup-lastname"
               :on-change #(dis/dispatch! [:signup-with-email-change :lastname (.-value (sel1 [:input.lastname]))])
               :placeholder "Last name"
               :type "text"
               :tabIndex 2
               :name "lastname"}]]
          [:div.sign-in-label-container
            [:label.sign-in-label {:for "signup-email"} "EMAIL"]]
          [:div.sign-in-field-container
            [:input.sign-in-field.email
              {:value (:email (:signup-with-email (rum/react dis/app-state)))
               :id "signup-email"
               :on-change #(dis/dispatch! [:signup-with-email-change :email (.-value (sel1 [:input.email]))])
               :pattern "[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,4}$"
               :placeholder "email@example.com"
               :type "email"
               :tabIndex 3
               :autoCapitalize "none"
               :name "email"}]]
          [:div.sign-in-label-container
            [:label.sign-in-label {:for "signup-pswd"} "PASSWORD"]]
          [:div.sign-in-field-container
            [:input.sign-in-field.pswd
              {:value (:pswd (:signup-with-email (rum/react dis/app-state)))
               :id "signup-pswd"
               :on-change #(dis/dispatch! [:signup-with-email-change :pswd (.-value (sel1 [:input.pswd]))])
               :pattern ".{4,}"
               :placeholder "at least 5 characters"
               :type "password"
               :tabIndex 4
               :name "pswd"}]]
          [:div.group.pb3.mt3
            [:div.left.forgot-password
              [:a {:on-click #(dis/dispatch! [:show-login-overlay :password-reset])} "FORGOT PASSWORD?"]]
            [:div.right
              [:button.btn-reset.btn-solid
                {:disabled (or (and (s/blank? (:firstname (:signup-with-email (rum/react dis/app-state))))
                                    (s/blank? (:lastname (:signup-with-email (rum/react dis/app-state)))))
                               (gobj/get (gobj/get (sel1 [:input.email]) "validity") "patternMismatch")
                               (< (count (:pswd (:signup-with-email (rum/react dis/app-state)))) 5))
                 :on-click #(do
                              (utils/event-stop %)
                              (dis/dispatch! [:signup-with-email]))}
                "SIGN UP"]]]]]
      [:div.login-overlay-footer.py2.px3.mt1.group
        [:a.left {:on-click #(do (utils/event-stop %) (dis/dispatch! [:show-login-overlay :login-with-slack]))}
          "Already have an account? "
          [:span.underline "SIGN IN NOW."]]]]])

(rum/defcs password-reset < rum/reactive
                            (merge dont-scroll
                              {:did-mount (fn [s] (.focus [:div.sign-in-field-container.email]) s)})
  [state]
  [:div.login-overlay-container.group
    {:on-click (partial close-overlay)}
    (close-button)
    [:div.login-overlay.password-reset
      {:on-click #(utils/event-stop %)}
      [:div.login-overlay-cta.pl2.pr2.group
        [:div.sign-in-cta "Password Reset"
          (when-not (:auth-settings (rum/react dis/app-state))
            (small-loading))]]
      [:div.pt2.pl3.pr3.pb2.group
        [:form.sign-in-form
          [:div.sign-in-label-container
            [:label.sign-in-label "PLEASE ENTER YOUR EMAIL ADDRESS"]]
          [:div.sign-in-field-container.email
            [:input.sign-in-field {:value "" :tabIndex 1 :type "email" :autoCapitalize "none" :name "email"}]]
          [:div.group.pb3.mt3
            [:div.right.ml1
              [:button.btn-reset.btn-solid
                "RESET PASSWORD"]]
            [:div.right
              [:button.btn-reset.btn-outline
                {:on-click #(dis/dispatch! [:show-login-overlay nil])}
                "CANCEL"]]]]]]])

(rum/defcs collect-name-password < rum/reactive
                                   (merge
                                     dont-scroll
                                     {:did-mount (fn [s]
                                                   ; initialise the keys to string to avoid jumps in UI focus
                                                   (dis/dispatch! [:input [:collect-name-pswd :firstname] (or (:firstname (:collect-name-pswd @dis/app-state)) "")])
                                                   (dis/dispatch! [:input [:collect-name-pswd :lastname] (or (:lastname (:collect-name-pswd @dis/app-state)) "")])
                                                   (dis/dispatch! [:input [:collect-name-pswd :pswd] (or (:pswd (:collect-name-pswd @dis/app-state)) "")])
                                                   (utils/after 100 #(.focus (sel1 [:input.firstname])))
                                                   s)})
  [state]
  [:div.login-overlay-container.group
    {:on-click #(utils/event-stop %)}
    [:div.login-overlay.collect-name-pswd.group
      [:div.login-overlay-cta.pl2.pr2.group
        [:div.sign-in-cta "Provide Your Name and a Password"
          (when-not (:auth-settings (rum/react dis/app-state))
            (small-loading))]]
      [:div.pt2.pl3.pr3.pb2.group
        (when-not (nil? (:collect-name-pswd-error (rum/react dis/app-state)))
          (cond
            (= (:collect-name-password-error (rum/react dis/app-state)) 409)
            [:span.small-caps.red
              "The email or password you entered is incorrect."
              [:br]
              "Please try again, or "
              [:a.underline.red {:on-click #(dis/dispatch! [:show-login-overlay :password-reset])} "reset your password"]
              "."]
            :else
            [:span.small-caps.red
              "System troubles logging in."
              [:br]
              "Please try again, then "
              [:a.underline.red {:href oc-url/contact-mail-to} "contact support"]
              "."]))
        [:form.sign-in-form
          [:div.sign-in-label-container
            [:label.sign-in-label {:for "collect-name-pswd-firstname"} "YOUR NAME"]]
          [:div.sign-in-field-container.group
            [:input.sign-in-field.firstname.half.left
              {:value (:firstname (:collect-name-pswd (rum/react dis/app-state)))
               :id "collect-name-pswd-firstname"
               :on-change #(dis/dispatch! [:input [:collect-name-pswd :firstname] (.-value (sel1 [:input.firstname]))])
               :placeholder "First name"
               :type "text"
               :tabIndex 1
               :name "firstname"}]
            [:input.sign-in-field.lastname.half.right
              {:value (:lastname (:collect-name-pswd (rum/react dis/app-state)))
               :id "collect-name-pswd-lastname"
               :on-change #(dis/dispatch! [:input [:collect-name-pswd :lastname] (.-value (sel1 [:input.lastname]))])
               :placeholder "Last name"
               :type "text"
               :tabIndex 2
               :name "lastname"}]]
          [:div.sign-in-label-container
            [:label.sign-in-label {:for "signup-pswd"} "PASSWORD"]]
          [:div.sign-in-field-container
            [:input.sign-in-field.pswd
              {:value (:pswd (:collect-name-pswd (rum/react dis/app-state)))
               :id "collect-name-pswd-pswd"
               :on-change #(dis/dispatch! [:input [:collect-name-pswd :pswd] (.-value (sel1 [:input.pswd]))])
               :pattern ".{4,}"
               :placeholder "at least 5 characters"
               :type "password"
               :tabIndex 4
               :name "pswd"}]]
          [:div.group.my3
            [:div.right
              [:button.btn-reset.btn-solid
                {:disabled (or (and (s/blank? (:firstname (:collect-name-pswd (rum/react dis/app-state))))
                                    (s/blank? (:lastname (:collect-name-pswd (rum/react dis/app-state)))))
                               (< (count (:pswd (:collect-name-pswd (rum/react dis/app-state)))) 5))
                 :on-click #(do
                              (utils/event-stop %)
                              (dis/dispatch! [:collect-name-pswd]))}
                "LET ME IN"]]]]]]])

(defn login-overlays-handler [s]
  (when (:show-login-overlay s)
    (cond
      ; login via email
      (= (:show-login-overlay s) :login-with-email)
      (login-with-email)
      ; signup via email
      (= (:show-login-overlay s) :signup-with-email)
      (signup-with-email)
      ; password reset
      (= (:show-login-overlay s) :password-reset)
      (password-reset)
      ; form to collect name and password
      (= (:show-login-overlay s) :collect-name-password)
      (collect-name-password)
      ; login via slack as default
      :else
      (login-signup-with-slack))))