(ns oc.web.actions.reaction
  (:require [oc.web.api :as api]
            [oc.web.lib.jwt :as jwt]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.json :refer (json->cljs)]))

(defn react-from-picker [activity-data emoji]
  (dis/dispatch! [:handle-reaction-to-entry activity-data {:reaction emoji :count 1 :reacted true :links [] :authors []}])
  (when (and emoji
             (utils/link-for (:links activity-data) "react"))
    (api/react-from-picker activity-data emoji
      (fn [{:keys [status success body]}]
        (api/get-entry activity-data)
        (dis/dispatch!
         [:react-from-picker/finish
          {:status status
           :activity-data activity-data
           :reaction-data (if success (json->cljs body) {})}])))))

(defn reaction-toggle
  [activity-data reaction-data reacting?]
  (dis/dispatch! [:handle-reaction-to-entry activity-data reaction-data])
  (api/toggle-reaction reaction-data reacting?
    (fn [{:keys [status success body]}]
      (dis/dispatch!
       [:activity-reaction-toggle/finish
        activity-data
        (:reaction reaction-data)
        (when success (json->cljs body))]))))

(defn is-activity-reaction? [org-slug board-slug interaction-data]
  (let [activity-uuid (router/current-activity-id)
        item-uuid (:resource-uuid interaction-data)
        reaction-data (:interaction interaction-data)
        comments-key (dis/activity-comments-key org-slug board-slug activity-uuid)
        comments-data (get-in @dis/app-state comments-key)
        comment-idx (utils/index-of comments-data #(= item-uuid (:uuid %)))]
    (nil? comment-idx)))

(defn refresh-if-needed [org-slug board-slug interaction-data]
  (let [; Get the current router data
        is-all-posts (:from-all-posts @router/path)
        activity-uuid (:resource-uuid interaction-data)
        ; Board data
        board-key (if is-all-posts (dis/all-posts-key org-slug) (dis/board-data-key org-slug board-slug))
        board-data (get-in @dis/app-state board-key)
        ; Entry data
        entry-key (dis/activity-key org-slug board-slug activity-uuid)
        entry-data (get-in @dis/app-state entry-key)
        reaction-data (:interaction interaction-data)
        is-current-user (= (jwt/get-key :user-id) (:user-id (:author reaction-data)))]
    (if (and entry-data (seq (:reactions entry-data)))
      (when is-current-user
        (api/get-entry entry-data))
      (api/get-board (utils/link-for (:links board-data) ["item" "self"] "GET")))))

(defn ws-interaction-reaction-add [interaction-data]
  (let [org-slug (router/current-org-slug)
        board-slug (router/current-board-slug)]
    (when (is-activity-reaction? org-slug board-slug interaction-data)
      (refresh-if-needed org-slug board-slug interaction-data)))
  (dis/dispatch! [:ws-interaction/reaction-add interaction-data]))

(defn ws-interaction-reaction-delete [interaction-data]
  (let [org-slug (router/current-org-slug)
        board-slug (router/current-board-slug)]
    (when (is-activity-reaction? org-slug board-slug interaction-data)
      (refresh-if-needed org-slug board-slug interaction-data)))
  (dis/dispatch! [:ws-interaction/reaction-delete interaction-data]))