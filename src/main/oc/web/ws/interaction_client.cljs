(ns oc.web.ws.interaction-client
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [sablono.core :as html :refer-macros [html]]
            [taoensso.sente :as s]
            [taoensso.timbre :as timbre]
            [cljs.core.async :refer [chan <! >! timeout pub sub unsub unsub-all]]
            [taoensso.encore :as encore :refer-macros (have)]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.jwt :as j]
            [oc.web.actions.jwt :as ja]
            [oc.web.local-settings :as ls]
            [oc.web.ws.utils :as ws-utils]
            [oc.web.utils.ws-client-ids :as ws-client-ids]
            [goog.Uri :as guri]))

(defonce current-board-path (atom nil))

;; Sente WebSocket atoms
(defonce channelsk (atom nil))
(defonce ch-chsk (atom nil))
(defonce ch-state (atom nil))
(defonce chsk-send! (atom nil))

(defonce last-ws-link (atom nil))
(defonce last-board-uuids (atom []))

(defonce ch-pub (chan))

(defonce last-interval (atom nil))

;; Publication that handlers will subscribe to
(defonce publication
  (pub ch-pub :topic))

;; Send wrapper

(defn- send! [chsk-send! & args]
  (ws-utils/send! "Interaction" chsk-send! ch-state args))

;; Actions
(defn boards-watch
  ([board-uuids]
    (timbre/debug "Watching boards: " board-uuids)
    (reset! last-board-uuids board-uuids)
    (boards-watch))
  ([]
    (when (and (fn? @chsk-send!)
               @ch-state
               (:open? @@ch-state))
      (doseq [board-uuid @last-board-uuids]
        (send! chsk-send! [:watch/board {:board-uuid board-uuid}])))))

(defn board-unwatch [callback]
  (timbre/debug "Unwatching all boards.")
  (reset! last-board-uuids [])
  (send! chsk-send! [:unwatch/board] 10000 callback))

(defn subscribe
  [topic handler-fn]
  (let [ws-ic-chan (chan)]
    (sub publication topic ws-ic-chan)
    (go-loop []
      (handler-fn (<! ws-ic-chan))
      (recur))))
;; Event handler

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

(defmethod event-handler :interaction-comment/add
  [_ body]
  (timbre/debug "Comment add event" body)
  (go (>! ch-pub { :topic :interaction-comment/add :data body })))

(defmethod event-handler :interaction-comment/update
  [_ body]
  (timbre/debug "Comment update event" body)
  (go (>! ch-pub { :topic :interaction-comment/update :data body })))

(defmethod event-handler :interaction-comment/delete
  [_ body]
  (timbre/debug "Comment delete event" body)
  (go (>! ch-pub { :topic :interaction-comment/delete :data body })))

(defmethod event-handler :interaction-reaction/add
  [_ body]
  (timbre/debug "Reaction add event" body)
  (go (>! ch-pub { :topic :interaction-reaction/add :data body })))

(defmethod event-handler :interaction-reaction/delete
  [_ body]
  (timbre/debug "Reaction delete event" body)
  (go (>! ch-pub { :topic :interaction-reaction/delete :data body })))


;; Sente events handlers

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

(declare reconnect)

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [auth-cb (partial ws-utils/auth-check "Interaction" ch-state chsk-send!
                 channelsk ja/jwt-refresh #(reconnect @last-ws-link (j/user-id)) boards-watch)]
    (ws-utils/post-handshake-auth ja/jwt-refresh
     #(send! chsk-send! [:auth/jwt {:jwt (j/jwt)}] 60000 auth-cb)))
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (timbre/debug "Handshake:" ?uid ?csrf-token ?handshake-data)))

;; Session test
(defn test-session
  "Ping the server to update the sesssion state."
  []
  (send! chsk-send! [:session/status]))

;;;; Sente event router (our `event-msg-handler` loop)

(defn  stop-router! []
  (when @channelsk
    (s/chsk-disconnect! @channelsk)
    (timbre/info "Connection closed")))

(defn start-router! []
  (s/start-client-chsk-router! @ch-chsk event-msg-handler)
  (timbre/info "Connection established")
  (ws-utils/reconnected last-interval "Interaction" chsk-send! ch-state
   #(reconnect @last-ws-link (j/user-id))))

(defn reconnect [ws-link uid]
  (let [ws-uri (guri/parse (:href ws-link))
        ws-domain (str (.getDomain ws-uri) (when (.getPort ws-uri) (str ":" (.getPort ws-uri))))
        ws-board-path (.getPath ws-uri)]
    (reset! last-ws-link ws-link)
    (if (or (not @ch-state)
            (not (:open? @@ch-state))
            (not= @current-board-path ws-board-path))
      (do
        (timbre/debug "Reconnect for" (:href ws-link) "and" uid "current state:" @ch-state
         "current board:" @current-board-path)
        ; if the path is different it means
        (when (and @ch-state
                   (:open? @@ch-state))
          (timbre/info "Closing previous connection for" @current-board-path)
          (stop-router!))
        (timbre/info "Attempting interaction service connection to" ws-domain "for board" ws-board-path)
        (let [{:keys [chsk ch-recv send-fn state] :as x} (s/make-channel-socket! ws-board-path
                                                          {:type :auto
                                                           :host ws-domain
                                                           :protocol (if ls/jwt-cookie-secure :https :http)
                                                           :packer :edn
                                                           :uid uid
                                                           :params {:user-id uid}})]
            (reset! current-board-path ws-board-path)
            (reset! channelsk chsk)
            (reset! ch-chsk ch-recv)
            (reset! chsk-send! send-fn)
            (when @ch-state
              (remove-watch @ch-state :interaction-client-state-watcher))
            (reset! ch-state state)
            (add-watch @ch-state :interaction-client-state-watcher
             (fn [key a old-state new-state]
               (reset! ws-client-ids/interaction-client-id (:uid new-state))))
            (start-router!)))
      (when (pos? (count @last-board-uuids))
        (boards-watch)))))