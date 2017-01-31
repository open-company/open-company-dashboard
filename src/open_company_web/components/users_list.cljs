(ns open-company-web.components.users-list
  (:require [rum.core :as rum]
            [cuerdas.core :as s]
            [open-company-web.dispatcher :as dis]
            [open-company-web.lib.jwt :as j]
            [open-company-web.lib.utils :as utils]
            [open-company-web.components.ui.small-loading :refer (small-loading)]))

(defn user-action [team-id user action method other-link-params & [payload]]
  (.tooltip (js/$ "[data-toggle=\"tooltip\"]") "hide")
  (dis/dispatch! [:user-action team-id user action method other-link-params payload]))

(rum/defc user-row < rum/static
  [team-id user]
  (let [user-links (:links user)
        user-dropdown-id (str "user-row-" (:user-id user))
        add-link (utils/link-for user-links "add" "PUT" {:ref "application/vnd.open-company.user.v1+json"})
        is-admin? (:admin user)
        admin-type {:ref "application/vnd.open-company.team.admin.v1"}
        remove-user (utils/link-for user-links "remove" "DELETE" {:ref "application/vnd.open-company.user.v1+json"})
        pending? (= (:status user) "pending")
        is-self? (= (j/get-key :user-id) (:user-id user))
        visualized-name (cond
                          (and (not (s/blank? (:first-name user)))
                               (not (s/blank? (:last-name user))))
                          (str (:first-name user) " " (:last-name user))
                          (not (s/blank? (:first-name user)))
                          (:first-name user)
                          (not (s/blank? (:last-name user)))
                          (:last-name user)
                          :else
                          (:email user))]
    [:tr
      [:td
        [:div.dropdown
          [:button.btn-reset.user-type-btn.dropdown-toggle
            {:on-click #()
             :id user-dropdown-id
             :data-toggle "dropdown"
             :aria-haspopup true
             :aria-expanded false}
            (cond
              is-admin?
              [:i.fa.fa-gear]
              :else
              [:i.fa.fa-user])]
          [:ul.dropdown-menu.user-type-dropdown-menu
            {:aria-labelledby user-dropdown-id}
            [:li
              {:class (when (not is-admin?) "active")
               :on-click #(user-action team-id user "remove" "DELETE" admin-type nil)}
              [:i.fa.fa-user] " Viewer"]
            [:li
              {:class (when is-admin? "active")
               :on-click #(user-action team-id user "add" "PUT" admin-type nil)}
              [:i.fa.fa-gear] " Admin"]]]]
      [:td [:div.value
             {:title (if (pos? (count (:email user))) (:email user) "")
              :data-toggle "tooltip"
              :data-placement "top"
              :data-container "body"}
             visualized-name]]
      [:td [:div (when (:status user)
                   (let [upper-status (clojure.string/upper-case (:status user))]
                     (if (= upper-status "UNVERIFIED")
                       "ACTIVE"
                       upper-status)))]]
      [:td {:style {:text-align "center"}}
        (if (:loading user)
          ; if it's loading show the spinner
          [:div (small-loading)]
          [:div
            ; if it has an invite link show a resend invite button
            (when (and pending?
                       (not is-self?))
              [:button.btn-reset.user-row-action
                {:data-placement "top"
                 :data-toggle "tooltip"
                 :data-container "body"
                 :title "RESEND INVITE"
                 :on-click #(dis/dispatch! [:resend-invite user])}
                [:i.fa.fa-share]])
            ; if it has a delete link
            (when remove-user
              [:button.btn-reset.user-row-action
                {:data-placement "top"
                 :data-toggle "tooltip"
                 :data-container "body"
                 :title (if (and pending? (not is-self?)) "CANCEL INVITE" "REMOVE USER")
                 :on-click #(user-action team-id user "remove" "DELETE"  nil)}
                (if (and pending? (not is-self?))
                  [:i.fa.fa-times]
                  [:i.fa.fa-trash-o])])])]]))

(rum/defc users-list < rum/static
                            {:did-mount (fn [s]
                                          (when-not (utils/is-test-env?)
                                            (.tooltip (js/$ "[data-toggle=\"tooltip\"]")))
                                        s)}
  [team-id users]
  [:div.um-users-box.col-12.group
    [:table.table
      [:thead
        [:tr
          [:th "ACCESS"]
          [:th "NAME"]
          [:th "STATUS"]
          [:th {:style {:text-align "center"}} "ACTIONS"]]]
      [:tbody
        (for [user users]
          (rum/with-key (user-row team-id user) (str "user-tr-" team-id "-" (:user-id user))))]]])