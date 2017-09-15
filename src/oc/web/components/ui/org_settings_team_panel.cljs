(ns oc.web.components.ui.org-settings-team-panel
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [cuerdas.core :as s]
            [oc.web.api :as api]
            [oc.web.lib.jwt :as jwt]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.components.ui.user-avatar :refer (user-avatar-image)]
            [oc.web.components.ui.user-type-picker :refer (user-type-dropdown)]))

(defn user-action [team-id user action method other-link-params]
  (.tooltip (js/$ "[data-toggle=\"tooltip\"]") "hide")
  (dis/dispatch! [:user-action team-id user action method other-link-params nil]))

(defn real-remove-fn [author user team-id]
  (when author
    (api/remove-author author))
  (user-action team-id user "remove" "DELETE"  {:ref "application/vnd.open-company.user.v1+json"}))

(defn alert-resend-done []
  (let [alert-data {:icon "/img/ML/invite_resend.png"
                    :message "Invite resent."
                    :link-button-title nil
                    :link-button-cb nil
                    :solid-button-title nil
                    :solid-button-cb nil}]
   (dis/dispatch! [:alert-modal-show alert-data])))

(rum/defcs org-settings-team-panel
  < rum/reactive
    (drv/drv :invite-users)
    (rum/local false ::resending-invite)
    {:before-render (fn [s]
                     (let [teams-load-data @(drv/get-ref s :invite-users)]
                       (when (and (:auth-settings teams-load-data)
                                  (not (:teams-data-requested teams-load-data)))
                         (dis/dispatch! [:teams-get])))
                     s)
     :after-render (fn [s]
                     (doto (js/$ "[data-toggle=\"tooltip\"]")
                        (.tooltip "fixTitle")
                        (.tooltip "hide"))
                     (when @(::resending-invite s)
                      (let [invite-users-data (:invite-users @(drv/get-ref s :invite-users))]
                        (when (zero? (count invite-users-data))
                          (alert-resend-done)
                          (reset! (::resending-invite s) false))))
                     s)}
  [s org-data]
  (let [invite-users-data (drv/react s :invite-users)
        team-data (:team-data invite-users-data)
        cur-user-data (:current-user-data invite-users-data)
        org-authors (:authors org-data)]
    [:div.org-settings-panel
      ;; Panel rows
      [:div.org-settings-team.org-settings-panel-row
        ;; Team table
        [:table.org-settings-table
          [:thead
            [:tr
              [:th "Name"]
              [:th "Status"]
              [:th.role "Role"]]]
          [:tbody
            (for [user (sort-by utils/name-or-email (:users team-data))
                  :let [user-type (utils/get-user-type user (dis/org-data))
                        author (some #(when (= (:user-id %) (:user-id user)) %) org-authors)
                        remove-fn (fn []
                                    (let [alert-data {:icon "/img/ML/trash.svg"
                                                      :message "Cancel invitation?"
                                                      :link-button-title "No"
                                                      :link-button-cb #(dis/dispatch! [:alert-modal-hide])
                                                      :solid-button-title "Yes"
                                                      :solid-button-cb #(do
                                                                          (real-remove-fn author user (:team-id team-data))
                                                                          (dis/dispatch! [:alert-modal-hide]))
                                                      }]
                                      (dis/dispatch! [:alert-modal-show alert-data])))]]
              [:tr
                {:key (str "org-settings-team-" (:user-id user))}
                [:td.user-name
                  (user-avatar-image user)
                  [:div.user-name-label (utils/name-or-email user)]]
                [:td.status-column
                  [:div.status-column-inner.group
                    [:div.status-label (s/capital (:status user))]
                    (when (and (= "pending" (:status user))
                               (or (contains? user :email)
                                   (contains? user :slack-id)))
                      [:button.mlb-reset.mlb-link
                        {:on-click (fn []
                                     (let [invitation-type (if (contains? user :slack-id) "slack" "email")
                                           inviting-user (if (= invitation-type "email")
                                                            (:email user)
                                                            (select-keys user [:first-name :last-name :slack-id :slack-org-id]))]
                                       (dis/dispatch! [:input [:invite-users]
                                                        [{:user inviting-user
                                                          :type invitation-type
                                                          :role user-type
                                                          :error nil}]])
                                       (reset! (::resending-invite s) true)
                                       (utils/after 100 #(dis/dispatch! [:invite-users]))))}
                        "Resend"])
                    (when (and (= "pending" (:status user))
                               (utils/link-for (:links user) "remove"))
                      [:button.mlb-reset.mlb-link-red
                        {:on-click remove-fn}
                        "Cancel"])]]
                [:td.role
                  (user-type-dropdown {:user-id (:user-id user)
                                       :user-type user-type
                                       :on-change #(api/switch-user-type user user-type % user author)
                                       :hide-admin (not (jwt/is-admin? (:team-id org-data)))
                                       :on-remove (if (and (not= "pending" (:status user))
                                                           (not= (:user-id user) (:user-id cur-user-data)))
                                                    remove-fn
                                                    nil)})]])]]]]))