(ns oc.web.stores.reaction
  (:require [taoensso.timbre :as timbre]
            [cljs-flux.dispatcher :as flux]
            [oc.web.lib.jwt :as jwt]
            [oc.web.router :as router]
            [oc.web.lib.utils :as utils]
            [oc.web.dispatcher :as dispatcher]))

;; Store reaction and related data
(defonce reactions-atom (atom {}))

(defn make-activity-index [uuid]
  (keyword (str "activity-" uuid)))

(defn make-comment-index [uuid]
  (keyword (str "comment-" uuid)))

;; Reducers used to watch for reaction dispatch data
(defmulti reducer (fn [db [action-type & _]]
                    (timbre/info "Dispatching reaction reducer:" action-type)
                    action-type))

(def reactions-dispatch
  (flux/register
   dispatcher/actions
   (fn [payload]
     (swap! dispatcher/app-state reducer payload))))

;; Handle dispatch events
(defn handle-reaction-to-entry-finish
  [db activity-data reaction reaction-data]
  (let [activity-uuid (:uuid activity-data)
        next-reactions-loading (utils/vec-dissoc (:reactions-loading activity-data) reaction)
        activity-key (concat (dispatcher/current-board-key) [:fixed-items activity-uuid])]
    (if (nil? reaction-data)
      (let [updated-activity-data (assoc activity-data :reactions-loading next-reactions-loading)]
        (assoc-in db activity-key updated-activity-data))
      (let [reaction (first (keys reaction-data))
            next-reaction-data (assoc (get reaction-data reaction) :reaction (name reaction))
            reactions-data (or (:reactions activity-data) [])
            reaction-idx (utils/index-of reactions-data #(= (:reaction %) (name reaction)))
            next-reactions-data (if (pos? (:count next-reaction-data))
                                  (if (or (neg? reaction-idx) (nil? reaction-idx))
                                    (assoc reactions-data (count reactions-data) next-reaction-data)
                                    (assoc reactions-data reaction-idx next-reaction-data))
                                  (vec (remove #(= (:reaction %) (name reaction)) reactions-data)))
            updated-activity-data (-> activity-data
                                   (assoc :reactions-loading next-reactions-loading)
                                   (assoc :reactions next-reactions-data))]
        (assoc-in db activity-key updated-activity-data)))))

(defn handle-reaction-to-entry [db activity-data reaction-data]
  (let [board-key (dispatcher/current-board-key)
        old-reactions-loading (or (:reactions-loading activity-data) [])
        next-reactions-loading (conj old-reactions-loading (:reaction reaction-data))
        updated-activity-data (assoc activity-data :reactions-loading next-reactions-loading)
        activity-key (concat board-key [:fixed-items (:uuid activity-data)])]
    (assoc-in db activity-key updated-activity-data)))

(defmethod dispatcher/action :handle-reaction-to-entry
  [db [_ activity-data reaction-data]]
  (handle-reaction-to-entry db activity-data reaction-data))

(defmethod dispatcher/action :react-from-picker/finish
  [db [_ {:keys [status activity-data reaction reaction-data]}]]
  (if (and (>= status 200)
           (< status 300))
    (let [reaction-key (first (keys reaction-data))
          reaction (name reaction-key)]
      (handle-reaction-to-entry-finish db activity-data reaction reaction-data))
    ;; Wait for the entry refresh if it didn't
    db))

(defmethod dispatcher/action :activity-reaction-toggle/finish
  [db [_ activity-data reaction reaction-data]]
  (handle-reaction-to-entry-finish db activity-data reaction reaction-data))

(defn- update-entry-reaction
  "Need to update the local state with the data we have, if the interaction is from
   the actual unchecked-short we need to refresh the entry since we don't have the
   links to delete/add the reaction."
  [db interaction-data add-event?]
  (let [; Get the current router data
        org-slug (router/current-org-slug)
        is-all-posts (:from-all-posts @router/path)
        board-slug (router/current-board-slug)
        activity-uuid (:resource-uuid interaction-data)
        ; Entry data
        fixed-activity-uuid (or (router/current-secure-activity-id) activity-uuid)
        is-secure-activity (router/current-secure-activity-id)
        secure-activity-data (when is-secure-activity
                               (dispatcher/activity-data org-slug board-slug fixed-activity-uuid))
        entry-key (dispatcher/activity-key org-slug board-slug fixed-activity-uuid)
        entry-data (get-in db entry-key)]
    (if (and is-secure-activity
             (not= (:uuid secure-activity-data) activity-uuid))
      db
      (if (and entry-data (seq (:reactions entry-data)))
        ; If the entry is present in the local state and it has reactions
        (let [reaction-data (:interaction interaction-data)
              old-reactions-data (or (:reactions entry-data) [])
              reaction-idx (utils/index-of old-reactions-data #(= (:reaction %) (:reaction reaction-data)))
              old-reaction-data (if reaction-idx
                                  (get old-reactions-data reaction-idx)
                                  {:reacted false :reaction (:reaction reaction-data)})
              with-reaction-data (assoc old-reaction-data :count (:count interaction-data))
              is-current-user (= (jwt/get-key :user-id) (:user-id (:author reaction-data)))
              reacted (if is-current-user add-event? (:reacted old-reaction-data))
              with-reacted (assoc with-reaction-data :reacted reacted)
              with-links (assoc with-reacted :links (:links reaction-data))
              new-reactions-data (if (pos? (:count with-links))
                                   (if (or (neg? reaction-idx) (nil? reaction-idx))
                                     (assoc old-reactions-data (count old-reactions-data) with-links)
                                     (assoc old-reactions-data reaction-idx with-links))
                                   (vec (remove #(= (:reaction %) (:reaction reaction-data)) old-reactions-data)))
          ; Update the entry with the new reaction
              updated-entry-data (assoc entry-data :reactions new-reactions-data)]
              ; Update the entry in the local state with the new reaction
          (assoc-in db entry-key updated-entry-data))
        ;; the entry is not present, refresh the full board
        db))))

(defn- get-comments-data
  [db data-key item-uuid]
  (let [comments-data (get-in db data-key)
        comment-idx (utils/index-of comments-data #(= item-uuid (:uuid %)))]
    (when comment-idx
      {:index comment-idx :data comments-data})))

(defn- update-comments-data
  [add-event? interaction-data data]
  (when data
    (let [reaction-data (:interaction interaction-data)
          reaction (:reaction reaction-data)
          comment-data (nth (:data data) (:index data))
          reactions-data (:reactions comment-data)
          reaction-idx (utils/index-of reactions-data #(= (:reaction %) reaction))
          old-reaction-data (nth reactions-data reaction-idx)
          reaction-data-with-count (assoc reaction-data :count (:count interaction-data))
          is-current-user (= (jwt/get-key :user-id) (:user-id (:author reaction-data)))
          with-reacted (if is-current-user
                         ;; If the reaction is from the current user we need to
                         ;; update the reacted, the links are the one coming with
                         ;; the WS message
                         (assoc reaction-data-with-count :reacted add-event?)
                         ;; If it's a reaction from another user we need to
                         ;; survive the reacted and the links from the reactions
                         ;; we already have
                         (merge reaction-data-with-count {:reacted (:reacted old-reaction-data)
                                                          :links (:links old-reaction-data)}))
          with-links (assoc with-reacted :links old-reaction-data)
          new-reactions-data (assoc reactions-data reaction-idx with-reacted)
          new-comment-data (assoc comment-data :reactions new-reactions-data)
          new-comments-data (assoc (:data data) (:index data) new-comment-data)]
      new-comments-data)))

(defn- update-comment-reaction
  [db interaction-data add-event?]
  (let [item-uuid (:resource-uuid interaction-data)
        comments-key (get @reactions-atom (make-comment-index item-uuid))
        all-posts-key (assoc comments-key 2 :all-posts)
        keys (remove nil? [comments-key all-posts-key])
        new-data (->> (mapcat #(vector %
                                       (update-comments-data
                                        add-event?
                                        interaction-data
                                        (get-comments-data db % item-uuid)))
                              keys)
                      (partition 2)
                      (filter second) ;; remove nil data
                      (map vec))]
    (when-not (empty? new-data)
      (reduce #(assoc-in %1 (first %2) (second %2)) db new-data))))

(defn- update-reaction
  [db interaction-data add-event?]
  (let [with-updated-comment (update-comment-reaction db interaction-data add-event?)]
    (or with-updated-comment (update-entry-reaction db interaction-data add-event?))))

(defmethod dispatcher/action :ws-interaction/reaction-add
  [db [_ interaction-data]]
  (update-reaction db interaction-data true))

(defmethod dispatcher/action :ws-interaction/reaction-delete
  [db [_ interaction-data]]
  (update-reaction db interaction-data false))

;; Reaction store specific reducers
(defmethod reducer :default [db payload]
  ;; ignore state changes not specific to reactions
  db)

;; This is used to store the relationship between an activity uuid that has
;; reactions with the activity key in the app state. When a related reaction
;; is then needed you can search for the key by the activity uuid. It does not
;; change the app state.
(defn- index-posts
  [ra org posts]
  (reduce (fn [acc post]
            (let [board-slug (:board-slug post)
                  idx (make-activity-index (:uuid post))
                  activity-key (dispatcher/activity-key
                                org
                                board-slug
                                (:uuid post))]
              (assoc acc idx activity-key)))
          ra posts))

(defmethod reducer :all-posts-get/finish
  [db [_ {:keys [org year month from body]}]]
  (swap! reactions-atom index-posts org (-> body :collection :items))
  db)

(defmethod reducer :board
  [db [_ board-data]]
  (let [org (router/current-org-slug)
        fixed-board-data (utils/fix-board board-data)]
    (swap! reactions-atom index-posts org (vals (:fixed-items fixed-board-data))))
  db)

;; This function is used to store the comment uuid and the comments key to
;; find that comment in the app state.  The reaction store can then find the
;; key by the comment uuid. It does NOT change the app state.
(defn- index-comments
  [ra org board-slug activity-uuid comments]
  (reduce (fn [acc comment]
            (let [idx (make-comment-index (:uuid comment))
                  comment-key (dispatcher/activity-comments-key
                               org
                               board-slug
                               activity-uuid)]
              (assoc acc idx comment-key)))
          ra comments))

(defmethod reducer :ws-interaction/comment-add
  [db [_ interaction-data]]
  (let [activity-uuid (:resource-uuid interaction-data)
        activity-key (@reactions-atom (make-activity-index activity-uuid))
        org (first activity-key)
        board-slug (nth activity-key 2)]
    (when activity-key
      (swap! reactions-atom index-comments
             org
             board-slug
             activity-uuid
             [(:interaction interaction-data)])))
  db)

(defmethod reducer :comments-get/finish
  [db [_ {:keys [success error body activity-uuid]}]]
  (let [activity-key (@reactions-atom (make-activity-index activity-uuid))
        org (first activity-key)
        board-slug (nth activity-key 2)]
    (when activity-key
      (swap! reactions-atom index-comments
             org
             board-slug
             activity-uuid
             (:items (:collection body)))))
  db)
