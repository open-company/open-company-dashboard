(ns open-company-web.components.ui.navbar
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [rum.core :as rum]
            [open-company-web.dispatcher :as dis]
            [open-company-web.urls :as oc-urls]
            [open-company-web.router :as router]
            [open-company-web.lib.jwt :as jwt]
            [open-company-web.lib.utils :as utils]
            [open-company-web.lib.responsive :as responsive]
            [open-company-web.components.ui.icon :as i]
            [open-company-web.components.ui.user-avatar :refer (user-avatar)]
            [open-company-web.components.ui.login-button :refer (login-button)]
            [open-company-web.components.ui.small-loading :as loading]
            [open-company-web.components.ui.company-avatar :refer (company-avatar)]
            [open-company-web.components.ui.login-overlay :refer (login-overlays-handler)]))

(defn menu-click [owner e]
  (when e
    (.preventDefault e))
  (dis/toggle-menu))

(defcomponent navbar [{:keys [company-data columns-num card-width latest-su link-loading email-loading slack-loading menu-open show-share-su-button] :as data} owner options]

  (render [_]
    (let [header-width (+ (* card-width columns-num)    ; cards width
                          (* 20 (dec columns-num))      ; cards right margin
                          (when (> columns-num 1) 60))] ; x margins if needed
      (dom/nav {:class "oc-navbar group"}
        (when (and (not (jwt/jwt)) (not (utils/is-test-env?)))
          (login-overlays-handler (rum/react dis/app-state)))
        (dom/div {:class "oc-navbar-header"
                  :style #js {:width (str header-width "px")}}
          (if (utils/in? (:route @router/path) "companies")
            (dom/a {:href "https://opencompany.com/" :title "OpenCompany.com"}
              (dom/img {:src "/img/oc-wordmark.svg" :style {:height "25px" :margin-top "12px"}}))
            (om/build company-avatar data))
          (when-not (:hide-right-menu data)
            (dom/ul {:class "nav navbar-nav navbar-right"}
              (dom/li {}
                (if (responsive/is-mobile-size?)
                  (dom/div {:on-click (partial menu-click owner)}
                    (i/icon :menu-34 {}))
                  (if (jwt/jwt)
                    (dom/div {}
                      (when show-share-su-button
                        (dom/div {:class "sharing-button-container"}
                          (dom/button {:class "btn-reset sharing-button right"
                                       :disabled (not (nil? (:foce-key data)))
                                       :on-click #(router/nav! (oc-urls/stakeholder-update-preview))} (dom/i {:class "fa fa-share"}) " SHARE AN UPDATE")))
                      (user-avatar (partial menu-click owner)))
                    (login-button)))))))))))