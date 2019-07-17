(ns oc.web.actions.app-update
  (:require [oc.web.api :as api]
            [oc.web.lib.utils :as utils]
            [oc.web.actions.notifications :as notification-actions]))

(defonce app-update-cicle (atom false))
(defonce app-update-timeout (atom nil))
(defonce should-check-for-updates (atom true))

(def initial-app-update-wait (* 1000 10)) ;; 10 seconds
(def default-app-update-interval (* 1000 60 5)) ;; 5 minutes
(def default-app-update-dismiss-interval (* 1000 60 60 24)) ;; 24 hours

(declare real-app-update-check)

(defn- set-app-update-timeout
  "Set a timeout to check for updates."
  [interval]
  (when @app-update-timeout
    (.clearTimeout js/window @app-update-timeout))
  (reset! app-update-timeout
   (utils/after interval real-app-update-check)))

(defn- app-update-notification-dismissed
  "App update notification was dismissed, let's set a new timeout with a longer wait time."
  []
  ;; Clear the old interval
  (when @app-update-timeout
    (.clearTimeout js/window @app-update-timeout))
  ;; Set a new interval
  (set-app-update-timeout default-app-update-dismiss-interval))

(defn- real-app-update-check
  "Check for app updates, show the notification if necessary, set a new timeout else."
  []
  (reset! app-update-timeout nil)
  (api/web-app-version-check
    (fn [{:keys [success body status]}]
      (if (= status 404)
        (notification-actions/show-notification
         (merge utils/app-update-error {:expire 0
                                        :dismiss app-update-notification-dismissed}))
        (set-app-update-timeout default-app-update-interval)))))

(defn start-app-update-check
  "Start the app update cicle, make sure it's started only once."
  []
  (when (compare-and-set! app-update-cicle false true)
    ;; Do the first check after 10 seconds from the app initialization
    (set-app-update-timeout initial-app-update-wait))) 