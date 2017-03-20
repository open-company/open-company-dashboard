(ns oc.web.lib.tooltip
  "Cljs wrapper for Tooltip js object that is in /lib/tooltip/tooltip.js
   Docs at https://github.com/darsain/tooltip/wiki"
  (:require [defun.core :refer (defun)]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.jwt :as jwt]
            [oc.web.lib.utils :refer (after)]
            [oc.web.lib.cookies :as cookie]
            [oc.web.lib.responsive :as responsive]))

(def ten-years (* 60 60 24 365 10))

(defn tooltip-already-shown? [tt-id]
  (when tt-id
    (cookie/get-cookie tt-id)))

(defn- skip-on-device?
  "If there is no data for this device type, then return true to skip showing this tip."
  [mobile desktop]
  (if (responsive/is-mobile-size?)
    (not (string? mobile))
    (not (string? desktop))))

(defn- already-shown?
  "If this is a once-only tip and there is a cookie saying we've already shown it, return true."
  [id once-only]
  (if once-only
    (cookie/get-cookie id)
    false))

(defn got-it-button-html [tip-id]
  (str "<div class=\"got-it-container group\">
          <button class=\"got-it-button\" id=\"got-it-btn-" tip-id "\">GOT IT</button>
        </div>"))

(defn get-device-content [tip-id mobile desktop]
  (if (responsive/is-mobile-size?)
    (str mobile (got-it-button-html tip-id))
    (str desktop (got-it-button-html tip-id))))

(defonce tooltips (atom {}))

(defn real-hide [^js/Tooltip t]
  (.hide t))

(defn- hide-tooltip
  [tt tip-id]
  (when (:tip tt)
    (real-hide (:tip tt)))
  (reset! tooltips (dissoc @tooltips tip-id))
  (when (fn? (:dismiss-cb (:setup tt)))
    ((:dismiss-cb (:setup tt)))))

(defn tooltip
  "Create a tooltip, attach it to the passed element at the gived position and return it"
  [pos {:keys [id mobile desktop once-only dismiss-cb got-it-cb show-to-ro-users show-to-signed-out-users config] :as setup}]
  (when-let [tt (get @tooltips id)]
    (hide-tooltip (:tip tt) id))
  (let [tt (js/Tooltip. (get-device-content id mobile desktop) (clj->js (merge {:baseClass "js-tooltip"
                                                                                :effectClass "fade"
                                                                                :auto true
                                                                                :place "bottom"}
                                                                         config)))]
    (reset! tooltips (assoc @tooltips id {:tip tt
                                          :pos pos
                                          :show-to-ro-users (or show-to-ro-users false)
                                          :show-to-signed-out-users (or show-to-signed-out-users true)
                                          :setup setup}))
    (cond
      (sequential? pos)
      ;; If pos is an array display it with the coords passed
      (.position tt (get pos 0) (get pos 1))
      :else
      ;; else it's an element to attach to.
      (.position tt pos))))

(defn hide
  "Programmatically hide the passed tooltip"
  [tip-id]
  (when-let [tt (get @tooltips tip-id)]
    (hide-tooltip tt tip-id)))

(defn show
  "Show the passed tooltip. Optionally disable the hide on click."
  ([tip-id]
    (let [tt (get @tooltips tip-id)
          tip (:tip tt)]
      (if (and (not (skip-on-device? (:mobile (:setup tt)) (:desktop (:setup tt))))
               (not (already-shown? tip-id (:once-only (:setup tt))))
               (or (:show-to-signed-out-users tt)
                   (and (not (:show-to-signed-out-users tt))
                        (jwt/jwt)))
               (or (and (:show-to-ro-users tt)
                        (jwt/jwt))
                   (and (not (:show-to-ro-users tt))
                        (jwt/jwt)
                        (not (:read-only (dis/board-data))))))
        (do
          (.show tip)
          (after 100
            #(let [$btn (js/$ (str "button#got-it-btn-" tip-id))]
              (.on $btn "click" (fn [e]
                                 (.stopPropagation e)
                                 (when (fn? (:got-it-cb (:setup tt)))
                                   ((:got-it-cb (:setup tt))))
                                 (hide-tooltip tt tip-id)
                                 (.off $btn "click")))))
          (when (:once-only (:setup tt))
            (cookie/set-cookie! tip-id true ten-years)))
        (reset! tooltips (dissoc @tooltips tip-id))))))