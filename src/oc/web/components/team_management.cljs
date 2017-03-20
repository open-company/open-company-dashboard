(ns oc.web.components.team-management
  (:require [rum.core :as rum]
            [dommy.core :refer-macros (sel1)]
            [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.api :as api]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.jwt :as jwt]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.responsive :as responsive]
            [oc.web.components.users-list :refer (users-list)]
            [oc.web.components.ui.footer :as footer]
            [oc.web.components.ui.login-required :refer (login-required)]
            [oc.web.components.ui.user-type-picker :refer (user-type-picker)]
            [oc.web.components.ui.loading :refer (rloading)]
            [oc.web.components.ui.back-to-dashboard-btn :refer (back-to-dashboard-btn)]
            [goog.fx.dom :refer (Fade)]))

(rum/defcs team-management < rum/reactive
                             (drv/drv :team-management)
                             (drv/drv :current-user-data)
                             (drv/drv :org-data)
                             {:before-render (fn [s]
                                               (when (and (:auth-settings @dis/app-state)
                                                          (not (:teams-data-requested @dis/app-state)))
                                                 (dis/dispatch! [:get-teams]))
                                               (when (and (nil? (:user-type (:um-invite @(drv/get-ref s :team-management))))
                                                          (utils/valid-email? (:email (:um-invite @(drv/get-ref s :team-management)))))
                                                  (dis/dispatch! [:input [:um-invite :user-type] :viewer]))
                                               s)
                             :did-mount (fn [s]
                                          (when (not (empty? (:access (:query-params @router/path))))
                                            (utils/after 5000
                                             #(let [fade-animation (new Fade (sel1 [:div#result-message]) 1 0 utils/oc-animation-duration)]
                                               (.play fade-animation))))
                                          (when-not (utils/is-test-env?)
                                            (dis/dispatch! [:input [:um-invite] {:email ""
                                                                                 :user-type nil
                                                                                 :error nil
                                                                                 :domain ""}]))
                                          s)}
  [s]
  (let [{:keys [um-invite um-domain-invite teams-data add-email-domain-team-error query-params] :as user-man} (drv/react s :team-management)
        ro-user-man (drv/react s :team-management)
        org-data (drv/react s :org-data)
        user-type (:user-type um-invite)
        cur-user-data (drv/react s :current-user-data)
        valid-email? (utils/valid-email? (:email um-invite))
        valid-domain-email? (utils/valid-domain? (:domain um-domain-invite))
        team-id (:team-id org-data)
        team-data (get-in teams-data [team-id :data])]
    [:div.team-management.mx-auto.p3.my4.group
      (if-not team-data
        (rloading {:loading true})
        [:div
          [:div.um-invite.group.mb3
            [:div.um-invite-label
              "TEAM MEMBERS"]
            [:div
              [:div.group
                [:input.left.um-invite-field.email
                  {:name "um-invite"
                   :type "text"
                   :autoCapitalize "none"
                   :value (:email um-invite)
                   :on-change #(dis/dispatch! [:input [:um-invite :email] (.. % -target -value)])
                   :placeholder "Email address"}]
                (user-type-picker user-type valid-email? #(dis/dispatch! [:input [:um-invite :user-type] %]) true)
                [:button.right.btn-reset.btn-solid.um-invite-send
                  {:disabled (or (not valid-email?)
                                 (not user-type))
                   :on-click #(let [email (:email (:um-invite ro-user-man))]
                                (if (and (utils/valid-email? email)
                                         (not (nil? (:user-type (:um-invite ro-user-man)))))
                                  (dis/dispatch! [:invite-by-email])
                                  (dis/dispatch! [:input [:um-invite :error] true])))}
                 "SEND INVITE"]]
              (when (:error um-invite)
                [:div
                  (cond
                    (and (= (:error um-invite) :user-exists)
                         (:email um-invite))
                    [:span.small-caps.red.mt1.left (str (:email um-invite) " is already a user.")]
                    :else
                    [:span.small-caps.red.mt1.left "An error occurred, please try again."])])]]
              (when-not (responsive/is-mobile-size?)
                [:div.mb3.um-invite.group
                  (when team-data
                    (users-list team-id (:users team-data) (:authors org-data)))])
              (when-not (responsive/is-mobile-size?)
                [:div.mb3.um-invite.group
                  [:div.um-invite-label
                      "SLACK TEAMS"]
                  [:div.um-invite-label-2
                    "Anyone who signs up with your Slack team can view team boards."]
                  [:div.team-list
                    (for [team (:slack-orgs team-data)]
                      [:div.slack-domain.group
                        {:key (str "slack-org-" (:slack-org-id team))}
                        [:img.slack-logo {:src "/img/slack.png"}]
                        [:span (:name team)]
                        (when-not (contains? (jwt/get-key :bot) team-id)
                          (when-let [add-bot-link (utils/link-for (:links team) "bot" "GET" {:auth-source "slack"})]
                            (let [fixed-add-bot-link (utils/slack-link-with-state (:href add-bot-link) (:user-id cur-user-data) team-id (oc-urls/org-team-settings (:slug org-data)))]
                              [:button.btn-reset.btn-link
                                {:on-click #(router/redirect! fixed-add-bot-link)
                                 :title "The OpenCompany Slack bot enables Slack invites, assignments and sharing."
                                 :data-toggle "tooltip"
                                 :data-placement "top"
                                 :data-container "body"}
                                "Add OpenCompany Slack bot"])))
                        [:button.btn-reset
                          {:on-click #(api/user-action (utils/link-for (:links team) "remove" "DELETE") nil)
                           :title (str "Remove " (:name team) " Slack team")
                           :data-toggle "tooltip"
                           :data-placement "top"
                           :data-container "body"}
                          [:i.fa.fa-trash]]])]
                  [:div.group
                    (when (utils/link-for (:links team-data) "authenticate" "GET" {:auth-source "slack"})
                      (if (zero? (count (:slack-orgs team-data)))
                        [:button.btn-reset.mt2.add-slack-team.slack-button
                            {:on-click #(dis/dispatch! [:add-slack-team])}
                            "Add "
                            [:span.slack "Slack"]
                            " Team"]
                        [:button.btn-reset.btn-link.another-slack-team
                          {:on-click #(dis/dispatch! [:add-slack-team])}
                          "Add another Slack team"]))
                    (when (not (empty? (:access query-params)))
                      [:div#result-message
                        (cond
                          (= (:access query-params) "team-exists")
                          [:span.small-caps.red.mt1.left "This team was already added."]
                          (= (:access query-params) "team")
                          [:span.small-caps.green.mt1.left "Team successfully added."]
                          (= (:access query-params) "bot")
                          [:span.small-caps.green.mt1.left "Bot successfully added."]
                          :else
                          [:span.small-caps.red.mt1.left "An error occurred, please try again."])])]])
              (when-not (responsive/is-mobile-size?)
                [:div.mb3.um-invite.group
                  [:div.um-invite-label
                      "TEAM EMAIL DOMAINS"]
                  [:div.um-invite-label-2
                    "Anyone who signs up with this email domain can view team boards."]
                  [:div.team-list
                    (for [team (:email-domains team-data)]
                      [:div.email-domain.group
                        [:span (str "@" (:domain team))]
                        [:button.btn-reset
                          {:on-click #(api/user-action (utils/link-for (:links team) "remove" "DELETE") nil)
                           :title "Remove email domain team"}
                          [:i.fa.fa-trash]]])]
                  [:div.group
                    [:input.left.um-invite-field.email
                      {:name "um-domain-invite"
                       :type "text"
                       :autoCapitalize "none"
                       :value (:domain um-domain-invite)
                       :pattern "@?[a-z0-9.-]+\\.[a-z]{2,4}$"
                       :on-change #(dis/dispatch! [:input [:um-domain-invite :domain] (.. % -target -value)])
                       :placeholder "Domain, e.g. @acme.com"}]
                    [:button.right.btn-reset.btn-solid.um-invite-send
                      {:disabled (not valid-domain-email?)
                       :on-click #(let [domain (:domain (:um-domain-invite ro-user-man))]
                                    (if (utils/valid-domain? domain)
                                      (dis/dispatch! [:add-email-domain-team])
                                      (dis/dispatch! [:input [:add-email-domain-team-error] true])))}
                     "ADD"]
                    (when add-email-domain-team-error
                      [:div
                        (cond
                          (and (= add-email-domain-team-error :domain-exists)
                               (:domain um-domain-invite))
                          [:span.small-caps.red.mt1.left (str (:domain um-domain-invite) " was already added.")]
                          :else
                          [:span.small-caps.red.mt1.left "An error occurred, please try again."])])]])
          (comment
            [:div.my2.um-byemail-container.group
              [:div.group
                [:span.left.ml1.um-byemail-anyone-span
                  "Anyone who signs up with this email domain can view team boards."]]
              [:div.mt2.um-byemail.group
                [:span.left.um-byemail-at "@"]
                [:input.left.um-byemail-email
                  {:type "text"
                   :name "um-byemail-email"
                   :placeholder "your email domain without @"}]
                [:button.left.um-byemail-save.btn-reset.btn-outline "SAVE"]]])])]))

(defcomponent team-management-wrapper [data owner]
  (render [_]
    (let [org-data (dis/org-data data)]

      (when (:read-only org-data)
        (router/redirect! (oc-urls/org)))

      (dom/div {:class "main-company-settings fullscreen-page"}

        (cond
          ;; the data is still loading
          (:loading data)
          (dom/div (dom/h4 "Loading data..."))

          (get-in data (conj (dis/org-data-key (router/current-org-slug)) :error))
          (login-required)

          ;; Company profile
          :else
          (dom/div {}
            (back-to-dashboard-btn {:title (if (responsive/is-mobile-size?) "Invite" "Invite and Manage Team") :click-cb #(router/nav! (oc-urls/org))})
            (dom/div {:class "company-settings-container"}
              (team-management))))

        (let [columns-num (responsive/columns-num)
              card-width (responsive/calc-card-width)]
         (footer/footer (responsive/total-layout-width-int card-width columns-num)))))))