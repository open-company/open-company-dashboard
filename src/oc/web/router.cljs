(ns oc.web.router
  (:require [secretary.core :as secretary]
            [oc.web.lib.prevent-route-dispatch :as prd]
            [taoensso.timbre :as timbre]
            [goog.history.Html5History :as history5]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [goog.history.EventType :as HistoryEventType]
            [oc.web.lib.raven :as raven]
            [oc.web.lib.jwt :as jwt]))

(def path (atom {}))

(defn set-route! [route parts]
  (timbre/info "set-route!" route parts)
  (reset! path {})
  (swap! path assoc :route route)
  (doseq [[k v] parts] (swap! path assoc k v)))

(defn get-token []
  (when (or (not js/window.location.pathname)
            (not js/window.location.search))
    (raven/capture-message (str "Window.location problem:"
                                " windown.location.pathname:" js/window.location.pathname
                                " window.location.search:" js/window.location.search
                                " return:" (str js/window.location.pathname js/window.location.search))))
  (str js/window.location.pathname js/window.location.search))

; this is needed as of this
; https://code.google.com/p/closure-library/source/detail?spec=svn88dc096badf091f380b4c2b4a6514184511de657&r=88dc096badf091f380b4c2b4a6514184511de657
; setToken doen't replace the query string, it only attach it at the end
; solution here: https://github.com/Sparrho/supper/blob/master/src-cljs/supper/history.cljs
(defn build-transformer
  "Custom transformer is needed to replace query parameters, rather
  than adding to them.
  See: https://gist.github.com/pleasetrythisathome/d1d9b1d74705b6771c20"
  []
  (let [transformer (goog.history.Html5History.TokenTransformer.)]
    (set! (.. transformer -retrieveToken)
          (fn [path-prefix location]
            (str (.-pathname location) (.-search location))))
    (set! (.. transformer -createUrl)
          (fn [token path-prefix location]
            (str path-prefix token)))
    transformer))

(defn make-history []
  (doto (goog.history.Html5History. js/window (build-transformer))
    (.setPathPrefix (str js/window.location.protocol
                         "//"
                         js/window.location.host))
    (.setUseFragment false)))

(def history (atom nil))
(def route-dispatcher (atom nil))

; FIXME: remove the warning of history not found
(defn nav! [token]
  (timbre/info "nav!" token)
  (.setToken @history token))

(defn redirect! [loc]
  (timbre/info "redirect!" loc)
  (set! (.-location js/window) loc))

(defn redirect-404! []
  (let [win-location (.-location js/window)
        pathname (.-pathname win-location)
        search (.-search win-location)
        hash-string (.-hash win-location)
        encoded-url (js/encodeURIComponent (str pathname search hash-string))]
    (timbre/info "redirect-404!" encoded-url)
    ;; FIXME: can't use oc-urls/not-found because importing the ns create a circular deps
    (redirect! (str "/404?path=" encoded-url))))

(defn redirect-500! []
  (let [win-location (.-location js/window)
        pathname (.-pathname win-location)
        search (.-search win-location)
        hash-string (.-hash win-location)
        encoded-url (js/encodeURIComponent (str pathname search hash-string))]
    (timbre/info "redirect-500!" encoded-url)
    ;; FIXME: can't use oc-urls/not-found because importing the ns create a circular deps
    (redirect! (str "/500?path=" encoded-url))))

(defn history-back! []
  (timbre/info "history-back!")
  (.go (.-history js/window) -1))

(defn setup-navigation! [cb-fn sec-route-dispatcher]
  (reset! route-dispatcher sec-route-dispatcher)
  (let [h (doto (make-history)
            (events/listen HistoryEventType/NAVIGATE
              ;; wrap in a fn to allow live reloading
              cb-fn)
            (.setEnabled true))]
    (reset! history h)))

;; Path components retrieve
(defn current-org-slug []
  (:org @path))

(defn current-board-slug []
  (:board @path))

(defn current-topic-slug []
  (:topic @path))

(defn current-update-date []
  (:update-date @path))

(defn current-update-slug []
  (:update-slug @path))

(defn query-params []
  (:query-params @path))

(defn topic-editing? []
  (some #(= "edit" %) (:route @path)))

(defn current-as-of []
  (:as-of (:query-params @path)))

(defn last-board-cookie
  "Cookie to save the last accessed board"
  [org-slug]
  (str "last-board-" (name org-slug)))