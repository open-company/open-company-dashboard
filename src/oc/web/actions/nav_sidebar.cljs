(ns oc.web.actions.nav-sidebar
  (:require [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.qsg :as qsg-actions]
            [oc.web.actions.user :as user-actions]
            [oc.web.actions.routing :as routing-actions]
            [oc.web.actions.section :as section-actions]))

;; Panels
;; :menu
;; :org
;; :integrations
;; :team
;; :invite
;; :profile
;; :notifications
;; :reminders
;; :reminder-{uuid}/:reminder-new
;; :section-add
;; :section-edit

(defn close-navigation-sidebar []
  (dis/dispatch! [:input [:mobile-navigation-sidebar] false]))

(defn nav-to-url! [e url]
  (when (and e
             (.-preventDefault e))
    (.preventDefault e))
  (dis/dispatch! [:reset-ap-initial-at (router/current-org-slug)])
  (let [current-path (str (.. js/window -location -pathname) (.. js/window -location -search))]
    (if (= current-path url)
      (do
        (routing-actions/routing @router/path)
        (user-actions/initial-loading true))
      (router/nav! url)))
  (qsg-actions/turn-on-show-guide)
  (close-navigation-sidebar))

(defn mobile-nav-sidebar []
  (dis/dispatch! [:update [:mobile-navigation-sidebar] not]))

;; Push panel

(defn- push-panel [panel]
  (dis/dispatch! [:update [:panel-stack] #(vec (conj (or % []) panel))]))

(defn- pop-panel []
  (let [panel-stack (:panel-stack @dis/app-state)]
    (when (pos? (count panel-stack))
      (dis/dispatch! [:update [:panel-stack] pop]))
    (peek panel-stack)))

;; Section settings

(defn show-section-editor []
  (push-panel :section-editor))

(defn hide-section-editor []
  (pop-panel))

(defn show-section-add []
  (dis/dispatch! [:input [:show-section-add-cb]
   (fn [sec-data note]
     (if sec-data
       (section-actions/section-save sec-data note #(dis/dispatch! [:input [:show-section-add] false]))
       ; (dis/dispatch! [:input [:show-section-add] false])
       (pop-panel)))])
  ; (dis/dispatch! [:input [:show-section-add] true])
  (push-panel :section-add)
  (close-navigation-sidebar))

(defn show-section-add-with-callback [callback]
  (dis/dispatch! [:input [:show-section-add-cb]
   (fn [sec-data note]
     (callback sec-data note)
     (dis/dispatch! [:input [:show-section-add-cb] nil])
     ; (dis/dispatch! [:input [:show-section-add] false])
     (pop-panel))])
  ; (dis/dispatch! [:input [:show-section-add] true])
  (push-panel :section-add))

(defn hide-section-add []
  (pop-panel))

;; Reminders

(defn show-reminders []
  ; (dis/dispatch! [:input [:show-reminders] :reminders])
  ; (dis/dispatch! [:input [:back-to-menu] (:expanded-user-menu @dis/app-state)])
  ; (dis/dispatch! [:input [:expanded-user-menu] false])
  (push-panel :reminders)
  (close-navigation-sidebar))

(defn show-new-reminder []
  ; (dis/dispatch! [:input [:show-reminders] :new])
  ; (dis/dispatch! [:input [:back-to-menu] (:expanded-user-menu @dis/app-state)])
  ; (dis/dispatch! [:input [:expanded-user-menu] false])
  (push-panel :reminder-new)
  (close-navigation-sidebar))

(defn edit-reminder [reminder-uuid]
  ; (dis/dispatch! [:input [:show-reminders] reminder-uuid])
  ; (dis/dispatch! [:input [:back-to-menu] (:expanded-user-menu @dis/app-state)])
  ; (dis/dispatch! [:input [:expanded-user-menu] false])
  (push-panel (keyword (str "reminder-" reminder-uuid)))
  (close-navigation-sidebar))

(defn close-reminders []
  ; (dis/dispatch! [:input [:expanded-user-menu] (:back-to-menu @dis/app-state)])
  ; (dis/dispatch! [:input [:back-to-menu] false])
  ; (dis/dispatch! [:input [:show-reminders] nil])
  (pop-panel))

;; Menu

(defn menu-toggle []
  (let [panel-stack (:panel-stack @dis/app-state)]
    (if (= (peek panel-stack) :menu)
      (pop-panel)
      (push-panel :menu))))

(defn menu-close []
  (pop-panel))

;; Show panels

(defn show-org-settings [panel]
  ;; When closing org settings
  ; (when-not panel
  ;   (dis/dispatch! [:input [:expanded-user-menu] (:back-to-menu @dis/app-state)])
  ;   (dis/dispatch! [:input [:back-to-menu] false]))
  ; ;; Set new panel
  ; (dis/dispatch! [:input [:org-settings] panel])
  ; ;; When opening panel
  ; (when panel
  ;   (dis/dispatch! [:input [:back-to-menu] (:expanded-user-menu @dis/app-state)])
  ;   (dis/dispatch! [:input [:expanded-user-menu] false]))
  (if panel
    (push-panel panel)
    (pop-panel))
  (close-navigation-sidebar))

(defn show-user-settings [panel]
  ;; When closing org settings
  ; (when-not panel
  ;   (dis/dispatch! [:input [:expanded-user-menu] (:back-to-menu @dis/app-state)])
  ;   (dis/dispatch! [:input [:back-to-menu] false]))
  ; ;; Set new panel
  ; (dis/dispatch! [:input [:user-settings] panel])
  ; ;; When opening panel
  ; (when panel
  ;   (dis/dispatch! [:input [:back-to-menu] (:expanded-user-menu @dis/app-state)])
  ;   (dis/dispatch! [:input [:expanded-user-menu] false]))
  (if panel
    (push-panel panel)
    (pop-panel))
  (close-navigation-sidebar))