(ns open-company-web.components.user-profile
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [open-company-web.router :as router]
            [open-company-web.lib.jwt :as jwt]
            [open-company-web.components.ui.footer :refer (footer)]
            [open-company-web.components.ui.back-to-dashboard-btn :refer (back-to-dashboard-btn)]))

(defcomponent user-profile [data owner]
  (render [_]
    (dom/div {:class "user-profile fullscreen-page"}
      (back-to-dashboard-btn {})
      (dom/div {:class "user-profile-internal"}
        (dom/div {:class "user-profile-title"} "User Info")
        (dom/div {:class "user-profile-content group"}
          (dom/div {:class "left-column"}
            (dom/div {:class "user-profile-name-title data-title"} "NAME")
            (dom/div {:class "user-profile-name"} (jwt/get-key :real-name))
            (when (= (jwt/get-key :auth-source) "slack")
              (dom/div {:class "user-profile-org-title data-title"} "SLACK ORGANIZATION"))
            (when (= (jwt/get-key :auth-source) "slack")
              (dom/div {:class "user-profile-org"} (jwt/get-key :org-name)))
            (dom/div {:class "user-profile-email-title data-title"} "EMAIL")
            (dom/div {:class "user-profile-email"} (jwt/get-key :email)))
          (dom/div {:class "right-column"}
            (dom/div {:class "user-profile-avatar-title data-title"} "AVATAR")
            (when (jwt/get-key :avatar)
              (dom/img {:class "user-profile-avatar" :src (jwt/get-key :avatar)}))))
        (when (= (jwt/get-key :auth-source) "slack")
          (dom/div {:class "user-profile-disclaimer"}
            "User information is from your Slack account.")))
      (om/build footer data))))