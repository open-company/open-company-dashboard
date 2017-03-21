(ns oc.web.components.edit-user-profile
  (:require [dommy.core :refer-macros (sel1)]
            [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.local-settings :as ls]
            [oc.web.lib.responsive :as responsive]
            [oc.web.lib.image-upload :as iu]
            [oc.web.components.ui.footer :refer (footer)]
            [oc.web.components.ui.back-to-dashboard-btn :refer (back-to-dashboard-btn)]
            [oc.web.components.ui.user-avatar :refer (user-avatar-image)]
            [goog.object :as googobj]
            [goog.dom :as gdom]))

(defn- img-on-load [owner img]
  (om/set-state! owner :has-changes true)
  (dis/dispatch! [:foce-input {:image-width (.-clientWidth img)
                               :image-height (.-clientHeight img)}])
  (gdom/removeNode img))

(defn success-cb
  [owner res]
  (let [url    (googobj/get res "url")
        node   (gdom/createDom "img")]
    (if-not url
      (js/alert "An error has occurred while processing the image URL. Please try again.")
      (do
        (set! (.-onload node) #(img-on-load owner node))
        (gdom/append (.-body js/document) node)
        (set! (.-src node) url)))
    (dis/dispatch! [:input [:edit-user-profile :avatar-url] url])
    (om/set-state! owner (merge (om/get-state owner) {:has-changes true}))))

(defn progress-cb [owner res progress])

(defn error-cb [owner res error]
  (js/alert "An error has occurred while processing the image URL. Please try again."))

(defn change! [owner k v]
  (dis/dispatch! [:input [:edit-user-profile k] v])
  (om/set-state! owner :has-changes true))

(defn reset-user-profile-data [owner e]
  (.preventDefault e)
  (dis/dispatch! [:reset-user-profile])
  (om/set-state! owner {:has-changes false}))

(defn save-user-profile-data [owner e]
  (.preventDefault e)
  (om/set-state! owner :will-save true)
  (dis/dispatch! [:save-user-profile (om/get-state owner :email-did-change)]))

(def initial-state
  {:email-did-change false
   :will-save false
   :first-name "iac"
   :has-changes false})

(defcomponent edit-user-profile [data owner]

  (init-state [_]
    (dis/dispatch! [:reset-user-profile])
    initial-state)

  (did-mount [_]
    (when-not (responsive/is-tablet-or-mobile?)
      (.tooltip (js/$ "[data-toggle=\"tooltip\"]"))))

  (did-update [_ _ _]
    (when-not (utils/is-test-env?)
      (.tooltip (js/$ "[data-toggle=\"tooltip\"]"))))

  (will-receive-props [_ _]
    (when (and (not (utils/is-test-env?))
               (om/get-state owner :will-save))
      (dis/dispatch! [:reset-user-profile])
      (om/update-state! owner #(merge % initial-state))))

  (render-state [_ {:keys [first-name has-changes email-did-change] :as st}]
    (let [columns-num (responsive/columns-num)
          card-width (responsive/calc-card-width)]
      (dom/div {:class "edit-user-profile fullscreen-page"}
        (back-to-dashboard-btn {:title "User Profile"})
        (dom/div {:class "edit-user-profile-internal mx-auto my4"}
          (dom/form {:class ""}
            (dom/div {:class "edit-user-profile-content group"}
              (dom/div {}
                (dom/div {:class "left-column"}
                  (dom/div {:class "edit-user-profile-title data-title"} "FIRST NAME")
                  (dom/input {:class "edit-user-profile"
                              :type "text"
                              :name "first-name"
                              :on-change #(change! owner :first-name (.. % -target -value))
                              :value (or (:first-name (:edit-user-profile data)) "")})
                  (dom/div {:class "edit-user-profile-title data-title"} "LAST NAME")
                  (dom/input {:class "edit-user-profile"
                              :type "text"
                              :name "last-name"
                              :on-change #(change! owner :last-name (.. % -target -value))
                              :value (or (:last-name (:edit-user-profile data)) "")})
                  (dom/div {:class "edit-user-profile-title data-title"} "PASSWORD")
                  (dom/input {:class "edit-user-profile"
                              :name "password"
                              :min-length 5
                              :on-change #(change! owner :password (.. % -target -value))
                              :type "password"
                              :pattern ".{4,}"
                              :placeholder "at least 5 characters"
                              :value (or (:password (:edit-user-profile data)) "")})
                  (dom/div {:class "edit-user-profile-title data-title"} "EMAIL")
                  (dom/input {:class "edit-user-profile"
                              :name "email"
                              :type "email"
                              :on-change #(do
                                            (change! owner :email (.. % -target -value))
                                            (om/set-state! owner :email-did-change true))
                              :value (or (:email (:edit-user-profile data)) "")}))
                (dom/div {:class "right-column"}
                  (dom/div {:class "edit-user-profile-title data-title"} "AVATAR")
                  (dom/div {:class "user-avatar-container"}
                    (when (pos? (count (:avatar-url (:edit-user-profile data))))
                      (dom/button {:class "btn-reset remove-user-avatar"
                                   :on-click #(do (utils/event-stop %) (change! owner :avatar-url nil))
                                   :title "Remove avatar"
                                   :data-toggle "tooltip"
                                   :data-container "body"
                                   :data-placement "top"}
                        (dom/i {:class "fa fa-remove"})))
                    (user-avatar-image (:edit-user-profile data)))
                  (dom/button {:class "btn-reset camera left"
                               :title (if (zero? (count (:avatar-url (:edit-user-profile data)))) "Add a profile avatar" "Change the profile avatar")
                               :type "button"
                               :data-toggle "tooltip"
                               :data-container "body"
                               :data-placement "top"
                               :on-click #(iu/upload! (partial success-cb owner) (partial progress-cb owner) (partial error-cb owner))}
                      (dom/i {:class "fa fa-camera"}))))
              (dom/div {:class "right mt2"}
                (dom/button {:class "btn-reset btn-outline mr2"
                             :on-click #(reset-user-profile-data owner %)} "CANCEL")
                (dom/button {:class "btn-reset btn-solid"
                             :disabled (or (not has-changes)
                                           (and (pos? (count (:password (:edit-user-profile data))))
                                                (< (count (:password (:edit-user-profile data))) 5))
                                            (and email-did-change
                                                 (or (not email-did-change)
                                                     (not (utils/valid-email? (:email (:edit-user-profile data)))))))
                             :on-click #(save-user-profile-data owner %)} "SAVE")))))
        (footer (responsive/total-layout-width-int card-width columns-num))))))