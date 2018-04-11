(ns oc.web.lib.ws-change-client
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! timeout pub sub unsub unsub-all]]
            [taoensso.sente :as s]
            [taoensso.timbre :as timbre]
            [taoensso.encore :as encore :refer-macros (have)]
            [oc.lib.time :as time]
            [oc.web.local-settings :as ls]
            [goog.Uri :as guri]))

(def current-org (atom nil))
(def container-ids (atom []))

;; Sente WebSocket atoms
(def channelsk (atom nil))
(def ch-chsk (atom nil))
(def ch-state (atom nil))
(def chsk-send! (atom nil))
(def ch-pub (chan))

;; Publication that handlers will subscribe to
(def publication
  (pub ch-pub :topic))

;; ----- Actions -----

(defn container-watch
  ([]
    (container-watch @container-ids))

  ([watch-ids]
    (when @chsk-send!
      (timbre/debug "Sending container/watch for:" watch-ids)
      (swap! container-ids conj watch-ids)
      (@chsk-send! [:container/watch watch-ids] 1000))))

(defn container-seen [container-id]
  (when @chsk-send!
    (timbre/debug "Sending container/seen for:" container-id)
    (@chsk-send! [:container/seen {:container-id container-id :seen-at (time/current-timestamp)}] 1000)))

(defn subscribe
  [topic handler-fn]
  (let [ws-cc-chan (chan)]
    (sub publication topic ws-cc-chan)
    (go-loop []
      (handler-fn (<! ws-cc-chan))
      (recur))))

;; ----- Event handlers -----

(defmulti event-handler
  "Multimethod to handle our internal events"
  (fn [event & _]
    (timbre/debug "event-handler" event)
    event))

(defmethod event-handler :default
  [_ & r]
  (timbre/info "No event handler defined for" _))

(defmethod event-handler :chsk/ws-ping
  [_ & r]
  (go (>! ch-pub { :topic :chsk/ws-ping })))

(defmethod event-handler :container/status
  [_ body]
  (timbre/debug "Status event:" body)
  (go (>! ch-pub { :topic :container/status :data body })))

(defmethod event-handler :container/change
  [_ body]
  (timbre/debug "Change event:" body)
  (go (>! ch-pub { :topic :container/change :data body })))

;; ----- Sente event handlers -----

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id ; Dispatch on event-id
  )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler :default
  [{:as ev-msg :keys [event]}]
  ; Default/fallback case (no other matching handler)
  (timbre/warn "Unhandled event: " event))

(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (if (:first-open? new-state-map)
      (timbre/debug "Channel socket successfully established!: " new-state-map)
      (timbre/debug "Channel socket state change: " new-state-map))))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (timbre/debug "Push event from server: " ?data)
  (apply event-handler ?data))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (timbre/debug "Handshake:" ?uid ?csrf-token ?handshake-data)
    (container-watch)))

;; ----- Sente event router (our `event-msg-handler` loop) -----

(defn  stop-router! []
  (when @channelsk
    (s/chsk-disconnect! @channelsk)
    (timbre/info "Connection closed")))

(defn start-router! []
  (s/start-client-chsk-router! @ch-chsk event-msg-handler)
  (timbre/info "Connection estabilished"))

(defn reconnect
  "Connect or reconnect the WebSocket connection to the change service"
  [ws-link uid org-slug containers]
  (let [ws-uri (guri/parse (:href ws-link))
        ws-domain (str (.getDomain ws-uri) (when (.getPort ws-uri) (str ":" (.getPort ws-uri))))
        ws-org-path (.getPath ws-uri)]
    (if (or (not @ch-state)
            (not (:open? @@ch-state))
            (not= @current-org org-slug))

      ;; Need a connection to change service
      (do
        (timbre/debug "Reconnect for" (:href ws-link) "and" uid "current state:" @ch-state
                      "current org:" @current-org "this org:" org-slug)
        ; if the path is different it means
        (when (and @ch-state
                   (:open? @@ch-state))
          (timbre/info "Closing previous connection for:" @current-org)
          (stop-router!))
        (timbre/info "Attempting change service connection to:" ws-domain "for org:" org-slug)
        (reset! container-ids containers)
        (let [{:keys [chsk ch-recv send-fn state] :as x} (s/make-channel-socket! ws-org-path
                                                          {:type :auto
                                                           :host ws-domain
                                                           :protocol (if ls/jwt-cookie-secure :https :http)
                                                           :packer :edn
                                                           :uid uid
                                                           :params {:user-id uid}})]
            (reset! current-org org-slug)
            (reset! channelsk chsk)
            (reset! ch-chsk ch-recv)
            (reset! chsk-send! send-fn)
            (reset! ch-state state)
            (start-router!)))

      ;; already connected, make sure we're watching all the current containers
      (do
        (reset! container-ids containers)
        (container-watch)))))