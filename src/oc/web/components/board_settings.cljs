(ns oc.web.components.board-settings
  (:require-macros [cljs.core.async.macros :refer (go)])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :refer-macros (sel1)]
            [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.api :as api]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.local-settings :as ls]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.lib.iso4217 :as iso4217]
            [oc.web.lib.responsive :as responsive]
            [oc.web.components.ui.footer :as footer]
            [oc.web.components.ui.small-loading :as loading]
            [oc.web.components.ui.login-required :refer (login-required)]
            [oc.web.components.ui.user-type-picker :refer (user-type-picker user-type-dropdown)]
            [oc.web.components.ui.back-to-dashboard-btn :refer (back-to-dashboard-btn)]
            [goog.events :as events]
            [goog.fx.dom :refer (Fade)]
            [goog.fx.Animation.EventType :as AnimationEventType]))

(defn user-is-on-board [user-id board-data]
  (or (pos? (count (filter #(= (:user-id %) user-id) (:viewers board-data))))
      (pos? (count (filter #(= (:user-id %) user-id) (:authors board-data))))))

(rum/defc invite-user-option < rum/static
  [user]
  (let [user-type (utils/get-user-type user (dis/org-data) (dis/board-data))]
    [:option
      {:value (:user-id user)}
      (utils/name-or-email user)]))

(rum/defcs invite-user < rum/static
                         rum/reactive
                         (drv/drv :user-management)
                         (drv/drv :org-data)
                         (drv/drv :board-data)
                          ;; Before mounting the component setup the invitation variables
                          ;; to make sure no user and no type is selected by default
                         {:will-mount (fn [s]
                                        (when-not (contains? @dis/app-state :private-board-invite)
                                          (dis/dispatch! [:input [:private-board-invite] {:selected-user-id ""
                                                                                          :selected-user-type nil}]))
                                        s)
                          ;; If it wasn't already, load the users list
                          :before-render (fn [s]
                                           (when (and (:auth-settings @dis/app-state)
                                                      (not (:enumerate-users-requested @dis/app-state)))
                                             (dis/dispatch! [:enumerate-users]))
                                           s)}
  [s]
  (let [{:keys [enumerate-users private-board-invite]} (drv/react s :user-management)
        org-data (drv/react s :org-data)
        board-data (drv/react s :board-data)
        users (:users (get enumerate-users (:team-id org-data)))
        selected-user-id (:selected-user-id private-board-invite)
        selected-user-type (:selected-user-type private-board-invite)
        selected-user (when selected-user-id (some #(when (= (:user-id %) selected-user-id) %) users))
        users-not-boarded (vec (filter #(not (user-is-on-board (:user-id %) board-data)) users))]
    (when-not (empty? users-not-boarded)
      [:div.private-board-invite.mt4

          [:div.private-board-select-wrapper
            [:select.private-board-select
              {:value selected-user-id
               :on-change (fn [e]
                            (when-not (empty? (.. e -target -value))
                              (let [v (.. e -target -value)
                                    actual-selected-user (some #(when (= (:user-id %) v) %) users)
                                    actual-selected-user-type (utils/get-user-type actual-selected-user org-data)
                                    to-user-type (if (or (= actual-selected-user-type :admin)
                                                         (= actual-selected-user-type :author))
                                                  :author
                                                  :viewer)]
                                (dis/dispatch! [:input [:private-board-invite :selected-user-id] v])
                                (dis/dispatch! [:input [:private-board-invite :selected-user-type] to-user-type]))))}
              [:option {:value "" :disabled true} "Select a user"]
              (for [user users-not-boarded]
                (rum/with-key (invite-user-option user) (:user-id user)))]]
        (user-type-picker selected-user-type (not (empty? selected-user-id)) #(dis/dispatch! [:input [:private-board-invite :selected-user-type] %]) false)
        [:button.btn-reset.btn-solid.private-board-add-button
          {:on-click #(when (and (not (empty? selected-user-id))
                                 selected-user-type)
                        (dis/dispatch! [:private-board-add]))
           :disabled (or (empty? selected-user-id)
                         (not selected-user-type))}
          "ADD"]])))

(rum/defc user-row < rum/static
  [user user-type user-data]
  [:tbody
    [:tr
      [:td
        (user-type-dropdown
         (:user-id user)
         user-type
         (fn [t]
           (dis/dispatch! [:input [:private-board-invite] {:selected-user-id (:user-id user)
                                                           :selected-user-type t}])
           (utils/after 100 #(dis/dispatch! [:private-board-add])))
         true)]
      [:td (utils/name-or-email user-data)]
      [:td
        (cond
          (utils/link-for (:links user) "remove")
          [:button.btn-reset
            {:title "Remove this user from this board."
             :on-click #(dis/dispatch! [:private-board-action (:user-id user) user-type (utils/link-for (:links user) "remove")])}
            [:i.fa.fa-trash]])]]])

(rum/defcs users-list < rum/static
                        rum/reactive
                        (drv/drv :board-data)
                        (drv/drv :org-data)
                        (drv/drv :user-management)
  [s]
  (let [org-data (drv/react s :org-data)
        board-data (drv/react s :board-data)
        {:keys [enumerate-users]} (drv/react s :user-management)
        all-users (:users (get enumerate-users (:team-id org-data)))]
    [:div.private-board-users-list
      [:table
        [:thead
          [:tr
            [:th "ACCESS"]
            [:th "NAME"]
            [:th ""]]]
        (for [v (:viewers board-data)
              :let [user-data (some #(when (= (:user-id %) (:user-id v)) %) all-users)]]
          (rum/with-key (user-row v :viewer user-data) (:user-id v)))
        (for [a (:authors board-data)
              :let [user-data (some #(when (= (:user-id %) (:user-id a)) %) all-users)]]
          (rum/with-key (user-row a :author user-data) (:user-id a)))]]))

(rum/defc private-board-setup < rum/static
  []
  [:div.private-board-setup
    {:on-click #(.stopPropagation %)}
    (users-list)
    (invite-user)])

(defn get-state [data current-state]
  (let [board-data (dis/board-data data)]
    {:board-slug (:slug board-data)
     :board-name (or (:board-name current-state) (:name board-data))
     :access (or (:access current-state) (:access board-data))
     :loading false
     :has-changes (or (:has-changes current-state) false)
     :show-save-successful (or (:show-save-successful current-state) false)}))

(defn cancel-clicked [owner]
  (om/set-state! owner (get-state (om/get-props owner) nil)))

(def board-name-min-length 2)

(defcomponent board-settings-form [data owner]

  (init-state [_]
    (get-state data nil))

  (will-receive-props [_ next-props]
    (when (om/get-state owner :loading)
      (utils/after 1500 (fn []
                          (let [fade-animation (new Fade (sel1 [:div#board-settings-save-successful]) 1 0 utils/oc-animation-duration)]
                            (doto fade-animation
                              (.listen AnimationEventType/FINISH #(om/set-state! owner :show-save-successful false))
                              (.play))))))
    (om/set-state! owner (get-state next-props {:show-save-successful (om/get-state owner :loading)})))

  (did-mount [_]
    (when (and (not (utils/is-test-env?))
               (not (responsive/is-tablet-or-mobile?)))
      (.tooltip (js/$ "[data-toggle=\"tooltip\"]"))))

  (render-state [_ {board-slug :board-slug board-name :board-name
                    access :access loading :loading
                    has-changes :has-changes show-save-successful :show-save-successful}]
    (let [org-data (dis/org-data data)
          board-data (dis/board-data)]
      (utils/update-page-title (str "OpenCompany - " (:name org-data) " " board-name " settings"))

      (dom/div {:class "mx-auto my4 settings-container group"}
        (dom/div {:class "my1 group"}
          (when (or (not board-data)
                    loading)
            (dom/div {:class "ml2 my1 left board-settings"}
              (loading/small-loading)))

          (dom/div {:style {:opacity (if show-save-successful "1" "0")}
                    :class "ml2 my1 left"
                    :id "board-settings-save-successful"}
                "Save successful"))
        
        (dom/div {:class "settings-form group p3"}

          ;; Board name
          (dom/div {:class "group"}
            (dom/div {:class "settings-form-input-label"} "BOARD NAME")
            (dom/input {:class "npt col-6 left p1 mb3 mr2"
                        :type "text"
                        :id "name"
                        :value (or board-name "")
                        :on-change #(om/set-state! owner :board-name (.. % -target -value))})
            (dom/button {:class "btn-reset btn-solid rename-button"
                         :disabled (or (< (count board-name) board-name-min-length)
                                       (= board-name (:name board-data)))
                         :on-click #(when (>= (count board-name) board-name-min-length)
                                      (om/set-state! owner :loading true)
                                      (api/patch-board {:slug board-slug
                                                        :name board-name}))}
              "RENAME"))

          ;; Visibility
          (dom/div {:class "settings-form-input-label"} "VISIBILITY")
          (dom/div {:class "settings-form-input visibility"}
            ;; Public
            (dom/div {:class "visibility-value highlightable"
                      :on-click #(do
                                  (om/set-state! owner :loading true)
                                  (api/patch-board {:slug (:slug board-data)
                                                    :access "public"}))}
              (dom/h3 {:class "mr1"} "Public"
                (when (= access "public")
                  (dom/i {:class "ml1 fa fa-check-square-o"})))
              (dom/p {:class (str (when (= access "public") "bold"))} "This board is public to everyone and will show up in search engines like Google. Only designed authors can edit and share information."))
            ;; Private choice
            (dom/div {}
              (dom/div {:class (str "visibility-value" (when (= access "public") " highlightable"))
                        :on-click #(when (= access "public")
                                    (om/set-state! owner :loading true)
                                    (api/patch-board {:slug (:slug board-data)
                                                      :access "team"}))}
                (dom/h3 {} "Private"
                  (when (or (= access "team") (= access "private"))
                    (dom/i {:class "ml1 fa fa-check-square-o"})))
                (dom/p {} "Only designed people can view, edit and share."))
              ;; Team
              (when (or (= access "team") (= access "private"))
                (dom/div {:class "visibility-value highlightable ml2"
                          :on-click #(do
                                      (om/set-state! owner :loading true)
                                      (api/patch-board {:slug (:slug board-data)
                                                        :access "team"}))}
                  (dom/h3 {} "Team"
                    (when (= access "team")
                      (dom/i {:class "ml1 fa fa-check-square-o"})))
                  (dom/p {:class (str (when (= access "team") "bold"))} "All team members can view this board. Only designed authors can edit and share.")))
              ;; Private
              (when (or (= access "team") (= access "private"))
                (dom/div {:class "visibility-value highlightable invite-only-board group ml2"
                          :on-click #(do
                                      (om/set-state! owner :loading true)
                                      (api/patch-board {:slug (:slug board-data)
                                                        :access "private"}))}
                  (dom/h3 {} "Invite-Only"
                    (when (= access "private")
                      (dom/i {:class "ml1 fa fa-check-square-o"})))
                  (dom/p {:class (str (when (= access "private") "bold"))} "Only invited team members can view, edit and share this board.")
                  (when (= access "private")
                    (private-board-setup))))))

          ; Slug
          (dom/div {:class "settings-form-input-label"} "BOARD URL")
          (dom/div {:class "dashboard-slug"}
            (when board-slug
              (str "http" (when ls/jwt-cookie-secure "s")
                   "://"
                   ls/web-server
                   (oc-urls/board (:slug org-data) board-slug)))))))))

(defcomponent board-settings [data owner]

  (render [_]
    (let [org-data (dis/org-data data)
          board-data (dis/board-data data)]
      (when (:read-only board-data)
        (router/redirect! (oc-urls/board)))

      (dom/div {:class "main-board-settings fullscreen-page"}

        (cond
          ;; the data is still loading
          (:loading data)
          (dom/div (dom/h4 "Loading data..."))

          (get-in data [(keyword (router/current-org-slug)) (keyword (router/current-board-slug)) :error])
          (login-required)

          ;; Org profile
          :else
          (dom/div {}
            (back-to-dashboard-btn {:title "Board Settings"})
            (dom/div {:class "board-settings-container"}
              (om/build board-settings-form data))))

        (let [columns-num (responsive/columns-num)
              card-width (responsive/calc-card-width)
              footer-width (responsive/total-layout-width-int card-width columns-num)]
          (footer/footer footer-width))))))