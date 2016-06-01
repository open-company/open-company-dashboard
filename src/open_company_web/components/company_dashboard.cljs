(ns open-company-web.components.company-dashboard
  (:require-macros [cljs.core.async.macros :refer (go)])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [cljs.core.async :refer (chan <!)]
            [open-company-web.dispatcher :as dis]
            [open-company-web.router :as router]
            [open-company-web.components.navbar :refer (navbar)]
            [open-company-web.components.topic-list :refer (topic-list)]
            [open-company-web.components.ui.login-button :refer [login-button]]
            [open-company-web.components.footer :refer (footer)]
            [open-company-web.components.menu :refer (menu)]
            [open-company-web.components.edit-topic :refer (edit-topic)]
            [open-company-web.lib.utils :as utils]
            [open-company-web.lib.responsive :as responsive]
            [goog.events :as events]
            [goog.events.EventType :as EventType]))

(defonce default-category "progress")

(defn set-save-bt-active [owner active]
  (om/set-state! owner :save-bt-active active))

(defn set-navbar-editing [owner data editing & [title]]
  (if editing
    ; if ALL is selected switch to the first available category
    ; for editing purpose
    (om/set-state! owner :last-active-category (om/get-state owner :active-category))
    (set-save-bt-active owner false))
  (let [fixed-title (or title "")]
    (om/set-state! owner :navbar-editing editing)
    (om/set-state! owner :navbar-title fixed-title)))

(defn switch-category-cb [owner new-category]
  ; reset the last edited topic when switching category
  (om/set-state! owner :last-editing-topic nil)
  (om/set-state! owner :active-category new-category))

(defn topic-edit-cb [owner section]
  (om/set-state! owner :editing-topic section)
  (utils/scroll-to-y 0))

(defn dismiss-topic-editing-cb [owner did-save]
  (let [state (om/get-state owner)]
    (om/set-state! owner :active-category (:last-active-category state))
    (om/set-state! owner :last-editing-topic (:editing-topic state))
    (om/set-state! owner :editing-topic nil)
    (om/set-state! owner :navbar-editing false)
    (om/set-state! owner :last-active-category nil)))


(defn toggle-sharing-mode [owner]
  (om/update-state! owner :sharing-mode not))

(defcomponent login-required [data owner]
  (render [_]
    (dom/div {:class "max-width-3 p4 mx-auto center mb4"}
      (dom/p {:class "mb2"} "Please log in to view this dashboard.")
      (om/build login-button data))))

(defcomponent company-dashboard [data owner]

  (init-state [_]
    (let [url-hash (.. js/window -location -hash)
          url-tab (subs url-hash 1 (count url-hash))
          active-tab (if (pos? (count url-tab))
                       url-tab
                       default-category)]
      {:active-category active-tab
       :navbar-editing false
       :editing-topic false
       :save-bt-active false
       :sharing-mode false
       :columns-num (responsive/columns-num)}))

  (did-mount [_]
    (events/listen js/window EventType/RESIZE #(om/set-state! owner :columns-num (responsive/columns-num))))

  (render-state [_ {:keys [editing-topic navbar-editing save-bt-active active-category columns-num sharing-mode] :as state}]
    (let [company-data (dis/company-data data)
          navbar-editing-cb (partial set-navbar-editing owner data)
          card-width (responsive/calc-card-width)]
      (dom/div {:class (utils/class-set {:company-dashboard true
                                         :main-scroll true
                                         :navbar-offset (not (responsive/is-mobile))})}
        (om/build menu data)
        (if (get-in data [(keyword (router/current-company-slug)) :error])
          (dom/div {:class "page-no-navbar py4"}
            (om/build login-required data))
          (dom/div {:class "page"}
            ;; Navbar
            (when (and company-data
                       (not sharing-mode))
              (om/build navbar {:save-bt-active save-bt-active
                                :company-data company-data
                                :card-width card-width
                                :sharing-mode sharing-mode
                                :columns-num columns-num
                                :auth-settings (:auth-settings data)}))
            (when company-data
              ;; Topic list or topic editing (old editing stuff)
              (if-not editing-topic
                ;; topic list
                (om/build topic-list
                          {:loading (or (:loading company-data) (:loading data))
                           :company-data company-data
                           :latest-su (dis/latest-stakeholder-update)
                           :card-width card-width
                           :columns-num columns-num
                           :active-category (:active-category state)}
                          {:opts {:navbar-editing-cb navbar-editing-cb
                                  :topic-edit-cb (partial topic-edit-cb owner)
                                  :switch-category-cb (partial switch-category-cb owner)
                                  :save-bt-active-cb (partial set-save-bt-active owner)
                                  :toggle-sharing-mode #(toggle-sharing-mode owner)}})
                ;; topic edit
                (om/build edit-topic {:section editing-topic
                                      :section-data (get company-data (keyword editing-topic))}
                          {:opts {:navbar-editing-cb navbar-editing-cb
                                  :save-bt-active-cb (partial set-save-bt-active owner)
                                  :dismiss-topic-editing-cb (partial dismiss-topic-editing-cb owner)}})))
            ;;Footer
            (when company-data
              (om/build footer {:columns-num columns-num
                                :card-width card-width}))))))))