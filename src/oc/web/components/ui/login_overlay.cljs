(ns oc.web.components.ui.login-overlay
  (:require [rum.core :as rum]
            [dommy.core :as dommy :refer-macros (sel1)]
            [clojure.string :as s]
            [goog.object :as gobj]
            [goog.style :as gstyle]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.urls :as oc-url]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.oc-colors :as occ]
            [oc.web.lib.responsive :as responsive]
            [oc.web.components.ui.icon :as i]
            [oc.web.components.ui.small-loading :refer (small-loading)]))

(defn close-overlay [e]
  (utils/event-stop e)
  (dis/dispatch! [:show-login-overlay false]))

(def dont-scroll
  {:will-mount (fn [s]
                (when-not (contains? @dis/app-state :auth-settings)
                  (utils/after 100 #(dis/dispatch! [:get-auth-settings])))
                s)
   :before-render (fn [s]
                    (if (responsive/is-mobile-size?)
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
                    s)
   :will-unmount (fn [s]
                   (if (responsive/is-mobile-size?)
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
    (let [close-color (if (responsive/is-mobile-size?) (occ/get-color-by-kw :oc-gray-5) "white")]
      (i/icon :simple-remove {:class "inline mr1" :stroke "4" :color close-color :accent-color close-color}))])

(rum/defcs login-signup-with-slack < rum/reactive
                                     (rum/local false ::sign-up-slack-clicked)
                                     dont-scroll
  [state]
  (let [action-title (if (= (:show-login-overlay (rum/react dis/app-state)) :signup-with-slack) "Sign Up" "Sign In")
        slack-error [:span.block.red "There is a temporary error validating with Slack. Please try again later."]]
    [:div.login-overlay-container.group
      {:on-click (partial close-overlay)}
      (close-button)
      [:div.login-overlay.login-with-slack
        {:on-click #(utils/event-stop %)}
        [:div.login-overlay-cta.pl2.pr2.group
          [:div.sign-in-cta.left action-title]]
        [:div.login-overlay-content.pt2.pl3.pr3.group.center
          (if @(::sign-up-slack-clicked state)
            [:div
              [:div.slack-disclaimer "If you’re not signed in to Slack " [:span.bold "on the Web"] ", Slack will prompt you to " [:span.bold "sign in first"] "."]
              (when (:slack-access (rum/react dis/app-state)) slack-error)
              [:button.btn-reset.btn-solid.login-button
                {:on-click #(do
                              (.preventDefault %)
                              (when (:auth-settings @dis/app-state)
                                (dis/dispatch! [:login-with-slack])))
                 :disabled (not (:auth-settings (rum/react dis/app-state)))}
                "GOT IT"]]
            [:div
              [:div.slack-disclaimer [:span.bold "Slack sign up"] " makes it " [:span.bold "easy for your teammates"] " to signup to view your OpenCompany dashboard."]
              (when (:slack-access (rum/react dis/app-state)) slack-error)
              [:button.btn-reset.mt2.login-button.slack-button
                {:on-click #(reset! (::sign-up-slack-clicked state) true)}
                (str action-title " with ")
                [:span.slack "Slack"]
                (when-not (:auth-settings (rum/react dis/app-state))
                  (small-loading))]])
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
                   [:span.underline "SIGN UP NOW."]])]]]))

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
              ; [:br]
              ; "Please try again, or "
              ; [:a.underline.red {:on-click #(dis/dispatch! [:show-login-overlay :password-reset])} "reset your password"]
              ; "."
              ]
            :else
            [:span.small-caps.red
              "System troubles logging in."
              [:br]
              "Please try again, then "
              [:a.underline.red {:href oc-url/contact-mail-to} "contact support"]
              "."]))
        [:form.sign-in-form
          {:id "sign-in-form"}
          [:div.sign-in-label-container
            [:label.sign-in-label "EMAIL"]]
          [:div.sign-in-field-container
            [:input.sign-in-field.email
              {:value (:email (:login-with-email (rum/react dis/app-state)))
               :on-change #(dis/dispatch! [:login-with-email-change :email (.-value (sel1 [:input.email]))])
               :type "email"
               :id "sign-in-email"
               :auto-focus true
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
               :id "sign-in-pswd"
               :tabIndex 2
               :name "pswd"}]]
          [:div.group.pb3.mt3
            ;;[:div.left.forgot-password
            ;;  [:a {:on-click #(dis/dispatch! [:show-login-overlay :password-reset])} "FORGOT PASSWORD?"]]
            [:div.right
              [:button.btn-reset.btn-solid
                {:disabled (or (not (:auth-settings (rum/react dis/app-state)))
                               (nil? (utils/link-for (:links (:auth-settings (rum/react dis/app-state))) "authenticate" "GET" {:auth-source "email"})))
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
              "This email address already has an account. "
              [:a.underline.red {:on-click #(dis/dispatch! [:show-login-overlay :login-with-email])} "Would you like to sign in with that account?"]
              ; [:br]
              ; "Please try again, or "
              ; [:a.underline.red {:on-click #(dis/dispatch! [:show-login-overlay :password-reset])} "reset your password"]
              ; "."
              ]
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
          {:id "sign-up-form"
           :action ""
           :method "GET"}
          [:div.sign-in-label-container
            [:label.sign-in-label {:for "sign-up-firstname"} "YOUR NAME"]]
          [:div.sign-in-field-container.group
            [:input.sign-in-field.firstname.half.left
              {:value (:firstname (:signup-with-email (rum/react dis/app-state)))
               :id "sign-up-firstname"
               :auto-focus true
               :on-change #(dis/dispatch! [:signup-with-email-change :firstname (.-value (sel1 [:input.firstname]))])
               :placeholder "First name"
               :type "text"
               :tabIndex 1
               :name "firstname"}]
            [:input.sign-in-field.lastname.half.right
              {:value (:lastname (:signup-with-email (rum/react dis/app-state)))
               :id "sign-up-lastname"
               :on-change #(dis/dispatch! [:signup-with-email-change :lastname (.-value (sel1 [:input.lastname]))])
               :placeholder "Last name"
               :type "text"
               :tabIndex 2
               :name "lastname"}]]
          [:div.sign-in-label-container
            [:label.sign-in-label {:for "sign-up-email"} "EMAIL"]]
          [:div.sign-in-field-container
            [:input.sign-in-field.email
              {:value (:email (:signup-with-email (rum/react dis/app-state)))
               :id "sign-up-email"
               :on-change #(dis/dispatch! [:signup-with-email-change :email (.-value (sel1 [:input.email]))])
               :pattern "[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,4}$"
               :placeholder "email@example.com"
               :type "email"
               :tabIndex 3
               :autoCapitalize "none"
               :name "email"}]]
          [:div.sign-in-label-container
            [:label.sign-in-label {:for "sign-up-pswd"} "PASSWORD"]]
          [:div.sign-in-field-container
            [:input.sign-in-field.pswd
              {:value (:pswd (:signup-with-email (rum/react dis/app-state)))
               :id "sign-up-pswd"
               :on-change #(dis/dispatch! [:signup-with-email-change :pswd (.-value (sel1 [:input.pswd]))])
               :pattern ".{4,}"
               :placeholder "at least 5 characters"
               :type "password"
               :tabIndex 4
               :name "pswd"}]]
          [:div.group.pb3.mt3
            ;;[:div.left.forgot-password
            ;;  [:a {:on-click #(dis/dispatch! [:show-login-overlay :password-reset])} "FORGOT PASSWORD?"]]
            [:div.right
              [:button.btn-reset.btn-solid
                {:disabled (or (not (:auth-settings (rum/react dis/app-state)))
                               (and (s/blank? (:firstname (:signup-with-email (rum/react dis/app-state))))
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
                              {:did-mount (fn [s] (.focus (sel1 [:div.sign-in-field-container.email])) s)})
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
        (when (contains? (:password-reset (rum/react dis/app-state)) :success)
          (cond
            (:success (:password-reset (rum/react dis/app-state)))
            [:div.green "We sent you an email with the instructions to reset your account password."]
            :else
            [:div.red "An error occurred, please try again."]))
        [:form.sign-in-form
          [:div.sign-in-label-container
            [:label.sign-in-label "PLEASE ENTER YOUR EMAIL ADDRESS"]]
          [:div.sign-in-field-container.email
            [:input.sign-in-field
              {:value (:email (:password-reset (rum/react dis/app-state)))
               :tabIndex 1
               :type "email"
               :autoCapitalize "none"
               :auto-focus true
               :on-change #(dis/dispatch! [:input [:password-reset :email] (.. % -target -value )])
               :name "email"}]]
          [:div.group.pb3.mt3
            [:div.right.ml1
              [:button.btn-reset.btn-solid
                {:on-click #(dis/dispatch! [:password-reset])
                 :disabled (not (utils/valid-email? (:email (:password-reset @dis/app-state))))}
                "RESET PASSWORD"]]
            [:div.right
              [:button.btn-reset.btn-outline
                {:on-click #(dis/dispatch! [:show-login-overlay nil])
                 :disabled (not (:auth-settings (rum/react dis/app-state)))}
                "CANCEL"]]]]]]])

(rum/defcs collect-name-password < rum/reactive
                                   (merge
                                     dont-scroll
                                     {:did-mount (fn [s]
                                                   ; initialise the keys to string to avoid jumps in UI focus
                                                   (utils/after 500
                                                      #(dis/dispatch! [:input [:collect-name-pswd] {:firstname (or (:first-name (:current-user-data @dis/app-state)) "")
                                                                                                    :lastname (or (:last-name (:current-user-data @dis/app-state)) "")
                                                                                                    :pswd (or (:pswd (:collect-name-pswd @dis/app-state)) "")}]))
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
              ; [:br]
              ; "Please try again, or "
              ; [:a.underline.red {:on-click #(dis/dispatch! [:show-login-overlay :password-reset])} "reset your password"]
              ; "."
              ]
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

(rum/defcs login-overlays-handler < rum/static
                                    rum/reactive
                                    (drv/drv :show-login-overlay)
  [s]
  (cond
    ; login via email
    (= (drv/react s :show-login-overlay) :login-with-email)
    (login-with-email)
    ; signup via email
    (= (drv/react s :show-login-overlay) :signup-with-email)
    (signup-with-email)
    ; password reset
    (= (drv/react s :show-login-overlay) :password-reset)
    (password-reset)
    ; form to collect name and password
    (= (drv/react s :show-login-overlay) :collect-name-password)
    (collect-name-password)
    ; login via slack as default
    (or (= (drv/react s :show-login-overlay) :login-with-slack)
        (= (drv/react s :show-login-overlay) :signup-with-slack))
    (login-signup-with-slack)
    ; show nothing
    :else
    [:div.hidden]))