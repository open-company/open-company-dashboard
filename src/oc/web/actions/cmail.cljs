(ns oc.web.actions.cmail
  (:require [defun.core :refer (defun)]
            [oc.web.api :as api]
            [oc.web.lib.jwt :as jwt]
            [oc.lib.user :as user-lib]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.utils.activity :as au]
            [oc.web.utils.dom :as dom-utils]
            [oc.web.lib.responsive :as responsive]
            [oc.web.lib.json :refer (json->cljs)]))

;; Last used and default section for editing

(defn get-default-section []
  (let [org-slug (router/current-org-slug)
        editable-boards (dis/editable-boards-data org-slug)
        cookie-value (au/last-used-section)
        board-from-cookie (some #(when (= (:slug %) cookie-value) %) (vals editable-boards))
        filtered-boards (filterv #(not (:draft %)) (vals editable-boards))
        board-data (or board-from-cookie (first (sort-by :name filtered-boards)))]
    {:board-name (:name board-data)
     :board-slug (:slug board-data)}))

(defn get-board-for-edit [& [board-slug]]
  (let [board-data (if (seq board-slug)
                    (dis/board-data (router/current-org-slug) board-slug)
                    (dis/board-data))]
    (if (or (not board-data)
            (= (:slug board-data) utils/default-drafts-board-slug)
            (not (utils/link-for (:links board-data) "create")))
      (get-default-section)
      {:board-slug (:slug board-data)
       :board-name (:name board-data)})))

;; Entry

(defn get-entry-with-uuid [board-slug activity-uuid & [loaded-cb]]
  (api/get-current-entry (router/current-org-slug) board-slug activity-uuid
   (fn [{:keys [status success body]}]
    (when-not (= status 404)
      (dis/dispatch! [:activity-get/finish status (router/current-org-slug) (when success (json->cljs body)) nil]))
    (when (fn? loaded-cb)
      (utils/after 100 #(loaded-cb success status))))))

;; Cmail

(defn edit-open-cookie []
  (str "edit-open-" (jwt/user-id) "-" (:slug (dis/org-data))))

(defn- cmail-fullscreen-cookie []
  (str "cmail-fullscreen-" (jwt/user-id)))

(defn- cmail-fullscreen-save [fullscreen?]
  (cook/set-cookie! (cmail-fullscreen-cookie) fullscreen? (* 60 60 24 30)))

(defn cmail-show [initial-entry-data & [cmail-state]]
  (let [cmail-default-state {:fullscreen (if (contains? cmail-state :fullscreen)
                                           (:fullscrreen cmail-state)
                                           (= (cook/get-cookie (cmail-fullscreen-cookie)) "true"))}
        cleaned-cmail-state (dissoc cmail-state :auto)
        fixed-cmail-state (merge cmail-default-state cleaned-cmail-state)]
    (if (:fullscreen cmail-default-state)
      (dom-utils/lock-page-scroll)
      (when-not (:collapsed cmail-state)
        (cook/remove-cookie! (cmail-fullscreen-cookie))))
    (when (and (not (:auto cmail-state))
               (not (:collapsed cmail-state)))
      (cook/set-cookie! (edit-open-cookie) (or (str (:board-slug initial-entry-data) "/" (:uuid initial-entry-data)) true) (* 60 60 24 365)))
    (dis/dispatch! [:input [:cmail-data] initial-entry-data])
    (dis/dispatch! [:input [:cmail-state] fixed-cmail-state])))

(defn cmail-hide []
  (cook/remove-cookie! (edit-open-cookie))
  (dis/dispatch! [:input [:cmail-data] (get-default-section)])
  (dis/dispatch! [:input [:cmail-state] {:collapsed true :key (utils/activity-uuid)}])
  (dom-utils/unlock-page-scroll))

(defn cmail-toggle-fullscreen []
  (let [next-fullscreen-value (not (:fullscreen (:cmail-state @dis/app-state)))]
    (cmail-fullscreen-save next-fullscreen-value)
    (dis/dispatch! [:update [:cmail-state] #(merge % {:fullscreen next-fullscreen-value})])
    (if next-fullscreen-value
      (dom-utils/lock-page-scroll)
      (dom-utils/unlock-page-scroll))))

(defn cmail-toggle-must-see []
  (dis/dispatch! [:update [:cmail-data] #(merge % {:must-see (not (:must-see %))
                                                   :has-changes true})]))

(defonce cmail-reopen-only-one (atom false))

(defn cmail-reopen? []
  (when (compare-and-set! cmail-reopen-only-one false true)
      ;; Make sure the new param is alone and not with an access param that means
      ;; it was adding a slack team or bot
      (utils/after 100
       ;; If cmail is already open let's not reopen it
       #(when (or (not (:cmail-state @dis/app-state))
                  (:collapsed (:cmail-state @dis/app-state)))
          (let [cmail-state {:auto true
                             ;; reopen fullscreen on desktop, mobile doesn't use it
                             :fullscreen (not (responsive/is-mobile-size?))
                             :collapsed false
                             :key (utils/activity-uuid)}]
            (if (and (contains? (router/query-params) :new)
                     (not (contains? (router/query-params) :access)))
              ;; We have the new GET parameter, let's open a new post with the specified headline if any
              (let [new-data (get-board-for-edit (router/query-param :new))
                    with-headline (if (router/query-param :headline)
                                   (assoc new-data :headline (router/query-param :headline))
                                   new-data)]
                (when new-data
                  (cmail-show with-headline cmail-state)))
              ;; We have the edit paramter or the edit cookie saved
              (when-let [edit-activity-param (or (router/query-param :edit) (cook/get-cookie (edit-open-cookie)))]
                (if (= edit-activity-param "true")
                  ;; If it's simply true open a new post with the data saved in the local DB
                  (cmail-show {} cmail-state)
                  ;; If it's composed by board-slug/activity-uuid
                  (let [[board-slug activity-uuid] (clojure.string/split edit-activity-param #"/")
                        edit-activity-data (dis/activity-data activity-uuid)]
                    (if edit-activity-data
                      ;; Open the activity in edit if it's already present in the app-state
                      (cmail-show edit-activity-data cmail-state)
                      ;; Load it from the server if it's not
                      (when (and board-slug activity-uuid)
                        (get-entry-with-uuid board-slug activity-uuid
                         (fn [success status]
                           (when success
                             (cmail-show (dis/activity-data activity-uuid) cmail-state)))))))))))))))

;; Follow-ups

(defun author-for-user
  ([user :guard map?]
   (-> user
    (select-keys [:user-id :avatar-url])
    (assoc :name (user-lib/name-for user))))
  ([users :guard sequential?]
   (vec (map author-for-user users))))

(defn- follow-up-from-user [user]
  (hash-map :assignee (author-for-user user)
            :completed? false))

(defn follow-up-users [activity-data team-roster]
  (let [all-team-users (filterv #(#{"active" "unverified"} (:status %)) (:users team-roster))
        board-data (dis/board-data (:board-slug activity-data))
        private-board? (= (:access board-data) "private")
        filtered-board-users (when private-board?
                               (concat (:viewers board-data) (:authors board-data)))]
    (if private-board?
      (filterv #((set filtered-board-users) (:user-id %)) all-team-users)
      all-team-users)))

(defn follow-ups-for-activity [activity-data team-roster]
  (let [follow-up-users (follow-up-users activity-data team-roster)
        filtered-follow-up-users (if (= (count follow-up-users) 1)
                                   follow-up-users ; default to self-assignement if there's only 1 user
                                   (filterv #(not= (:user-id %) (jwt/user-id)) follow-up-users))]
      (map follow-up-from-user filtered-follow-up-users)))

(defn cmail-toggle-follow-up [activity-data]
  (let [follow-up (pos? (count (:follow-ups activity-data)))
        turning-on? (not follow-up)
        activity-follow-ups (follow-ups-for-activity activity-data (dis/team-roster))]
    (dis/dispatch! [:follow-up-toggle (router/current-org-slug) activity-data (if turning-on? activity-follow-ups [])])))