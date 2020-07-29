(ns oc.web.stores.org
  (:require [oc.web.lib.utils :as utils]
            [taoensso.timbre :as timbre]
            [oc.web.lib.jwt :as jwt]
            [oc.web.utils.org :as org-utils]
            [oc.web.utils.activity :as activity-utils]
            [oc.web.utils.user :as user-utils]
            [oc.web.dispatcher :as dispatcher]
            [oc.web.actions.cmail :as cmail-actions]
            [oc.web.stores.user :as user-store]))

(defmethod dispatcher/action :org-loaded
  [db [_ org-data saved? email-domain]]
  ;; We need to remove the boards that are no longer in the org
  (let [boards-key (dispatcher/boards-key (:slug org-data))
        old-boards (get-in db boards-key)
        ;; No need to add a spacial case for drafts board here since
        ;; we are only excluding keys that already exists in the app-state
        board-slugs (set (mapv #(keyword (str (:slug %))) (:boards org-data)))
        filter-board (fn [[k v]]
                       (board-slugs k))
        next-boards (into {} (filter filter-board old-boards))
        section-names (:default-board-names org-data)
        selected-sections (map :name (:boards org-data))
        sections (vec (map #(hash-map :name % :selected (utils/in? selected-sections %)) section-names))
        fixed-org-data (activity-utils/parse-org db org-data)
        with-saved? (if (nil? saved?)
                      ;; If saved? is nil it means no save happened, so we keep the old saved? value
                      org-data
                      ;; If save actually happened let's update the saved value
                      (assoc org-data :saved saved?))
        next-org-editing (-> with-saved?
                          (assoc :email-domain email-domain)
                          (dissoc :has-changes))
        editable-boards (filterv #(and (not (:draft %)) (utils/link-for (:links %) "create" "POST"))
                         (:boards org-data))
        current-board-slug (dispatcher/current-board-slug)
        editing-board (when (seq editable-boards)
                        (cmail-actions/get-board-for-edit (when-not (dispatcher/is-container? current-board-slug) current-board-slug) editable-boards))
        next-db (if (and (not (contains? db (first dispatcher/cmail-state-key)))
                         editing-board)
                  (-> db
                    (assoc-in dispatcher/cmail-state-key {:key (utils/activity-uuid)
                                                          :fullscreen false
                                                          :collapsed true})
                    (update-in dispatcher/cmail-data-key merge editing-board))
                  db)
        active-users (dispatcher/active-users (:slug org-data) db)
        follow-boards-list-key (dispatcher/follow-boards-list-key (:slug org-data))
        unfollow-board-uuids (get-in db (dispatcher/unfollow-board-uuids-key (:slug fixed-org-data)))]
    (-> next-db
     (assoc-in (dispatcher/org-data-key (:slug org-data)) fixed-org-data)
     (assoc :org-editing next-org-editing)
     (assoc :org-avatar-editing (select-keys fixed-org-data [:logo-url :logo-width :logo-height]))
     (assoc-in boards-key next-boards)
     (update-in follow-boards-list-key #(user-store/enrich-boards-list unfollow-board-uuids (:boards fixed-org-data))))))

(defmethod dispatcher/action :org-avatar-update/failed
  [db [_]]
  (let [org-data (dispatcher/org-data db)]
    (assoc db :org-avatar-editing (select-keys org-data [:logo-url :logo-width :logo-height]))))

(defmethod dispatcher/action :org-create
  [db [_]]
  (dissoc db
   :latest-entry-point
   :latest-auth-settings
   ;; Remove the entry point, orgs and auth settings
   ;; to avoid using the old loaded orgs
   (first dispatcher/api-entry-point-key)
   (first dispatcher/auth-settings-key)
   dispatcher/orgs-key))

(defmethod dispatcher/action :org-edit-setup
  [db [_ org-data]]
  (assoc db :org-editing org-data))

(defmethod dispatcher/action :bookmarks-nav/show
  [db [_ org-slug]]
  (let [bookmarks-count-key (vec (conj (dispatcher/org-data-key org-slug) :bookmarks-count))]
    (update-in db bookmarks-count-key #(or % 0))))

(defmethod dispatcher/action :drafts-nav/show
  [db [_ org-slug]]
  (let [drafts-count-key (vec (conj (dispatcher/org-data-key org-slug) :drafts-count))]
    (update-in db drafts-count-key #(or % 0))))

(defmethod dispatcher/action :maybe-badge-following
  [db [_ org-slug current-board-slug]]
  (let [badges-key (dispatcher/badges-key org-slug)
        badges-data (get-in db badges-key)]
    (if (and (or (not badges-data)
                 (not (contains? badges-data :following)))
             (not= (keyword current-board-slug) :following))
      (assoc-in db (conj badges-key :following) true)
      db)))

(defmethod dispatcher/action :maybe-badge-replies
  [db [_ org-slug current-board-slug]]
  (let [badges-key (dispatcher/badges-key org-slug)
        badges-data (get-in db badges-key)]
    (if (and (or (not badges-data)
                 (not (contains? badges-data :replies)))
             (not= (keyword current-board-slug) :replies))
      (assoc-in db (conj badges-key :replies) true)
      db)))