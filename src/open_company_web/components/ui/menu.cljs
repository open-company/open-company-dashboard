(ns open-company-web.components.ui.menu
  (:require [cljs.core.async :refer (put!)]
            [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :as dommy :refer-macros (sel1)]
            [open-company-web.dispatcher :as dis]
            [open-company-web.urls :as oc-urls]
            [open-company-web.router :as router]
            [open-company-web.dispatcher :as dis]
            [open-company-web.lib.jwt :as jwt]
            [open-company-web.lib.utils :as utils]
            [open-company-web.lib.cookies :as cook]
            [open-company-web.lib.responsive :as responsive]
            [open-company-web.components.ui.popover :as popover]
            [open-company-web.components.prior-updates :refer (prior-updates)]
            [goog.events :as events]
            [goog.events.EventType :as EventType]))

(defn close-menu []
  (dis/toggle-menu))

(defn logout-click [e]
  (utils/event-stop e)
  (dis/dispatch! [:logout]))

(defn user-profile-click [e]
  (utils/event-stop e)
  (dis/save-last-company-slug)
  (close-menu)
  (utils/after (+ utils/oc-animation-duration 100) #(router/nav! oc-urls/user-profile)))

(defn company-profile-click [e]
  (utils/event-stop e)
  (close-menu)
  (utils/after (+ utils/oc-animation-duration 100) #(router/nav! (oc-urls/company-settings))))

(defn prior-updates-click [e]
  (utils/event-stop e)
  (close-menu)
  (utils/after (+ utils/oc-animation-duration 100)
    (if (responsive/is-mobile-size?)
      #(router/nav! (oc-urls/stakeholder-update-list)) ; nav. to prior updates page
      (popover/add-popover-with-rum-component prior-updates {:container-id "prior-updates-dialog"})))) ; popover

(defn on-transition-end [owner body]
  (doto body
    (dommy/remove-class! :left)
    (dommy/remove-class! :right)
    (dommy/remove-class! :animating)
    (dommy/toggle-class! :menu-visible))
  (events/unlistenByKey (om/get-state owner :transition-end-listener)))

(defn toggle-menu [owner close?]
  (set! (.. js/document -body -scrollTop) 0)
  (let [body (sel1 [:body])
        page (sel1 [:div.page])]
    (dommy/add-class! body :animating)
    (if (dommy/has-class? body :menu-visible)
      (dommy/add-class! body :right)
      (dommy/add-class! body :left))
    (let [listener-key (events/listen page EventType/TRANSITIONEND #(on-transition-end owner body))]
      (om/set-state! owner :transition-end-listener listener-key))))

(defn body-clicked [owner e]
  ; if the menu is open and the user click on the content (not the menu)
  ; close the menu
  (when (and (dommy/has-class? (.-body js/document) :menu-visible)
             (utils/event-inside? e (sel1 [:div.page])))
    (utils/event-stop e)
    (toggle-menu owner true)))

(defcomponent menu [data owner options]

  (did-mount [_]
    (when (:menu-open data)
      (let [body (sel1 [:body])]
        (dommy/add-class! body :menu-visible)))
    (let [body-click-listener (events/listen (.-body js/document)
                                             EventType/CLICK
                                             #(body-clicked owner %))]
      (om/set-state! owner :body-click-listener body-click-listener)))

  (will-unmount [_]
    (events/unlistenByKey (om/get-state owner :body-click-listener)))

  (will-receive-props [_ next-props]
    (cond
      (and (:menu-open data)
           (not (:menu-open next-props)))
      (toggle-menu owner true)
      (and (not (:menu-open data))
           (:menu-open next-props))
      (toggle-menu owner false)))

  (render [_]
    (dom/ul {:id "menu"}
      (dom/li {:class "oc-title"}
        (dom/a {:href "https://opencompany.com/" :title "OpenCompany.com"}
          (dom/img {:src "/img/oc-wordmark-white.svg" :style {:height "25px"}})))
      (when (jwt/jwt)
        (dom/li {:class "menu-link"} (dom/a {:title "PRIOR UPDATES" :href oc-urls/stakeholder-update-list :on-click prior-updates-click} "PRIOR UPDATES")))
      (when (jwt/jwt)
        (dom/li {:class "menu-link"} (dom/a {:title "USER INFO" :href oc-urls/user-profile :on-click user-profile-click} "USER INFO")))
      (when (and (router/current-company-slug)
                 (not (utils/in? (:route @router/path) "profile"))
                 (not (:read-only (dis/company-data)))
                 (not (responsive/is-mobile-size?)))
        (dom/li {:class "menu-link"} (dom/a {:title "COMPANY SETTINGS" :href (oc-urls/company-settings) :on-click company-profile-click} "COMPANY SETTINGS")))
      (when (jwt/jwt)
        (dom/li {:class "menu-link"} (dom/a {:title "SIGN OUT" :href oc-urls/logout :on-click logout-click} "SIGN OUT")))
      (when-not (jwt/jwt)
        (dom/li {:class "menu-link"} (dom/a {:title "SIGN IN / SIGN UP" :href oc-urls/login} "SIGN IN / SIGN UP"))))))