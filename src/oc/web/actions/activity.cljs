(ns oc.web.actions.activity
  (:require-macros [if-let.core :refer (when-let*)])
  (:require [taoensso.timbre :as timbre]
            [oc.web.api :as api]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.utils.activity :as au]
            [oc.web.lib.user-cache :as uc]
            [oc.web.local-settings :as ls]
            [oc.web.actions.section :as sa]
            [oc.web.lib.json :refer (json->cljs)]
            [oc.web.lib.responsive :as responsive]
            [oc.web.lib.ws-change-client :as ws-cc]
            [oc.web.lib.ws-interaction-client :as ws-ic]
            [oc.web.components.ui.alert-modal :as alert-modal]))

(def initial-revision (atom {}))

(defn watch-boards [posts-data]
  (when (jwt/jwt) ; only for logged in users
    (let [board-slugs (distinct (map :board-slug (vals posts-data)))
          org-data (dis/org-data)
          org-boards (:boards org-data)
          org-board-map (zipmap (map :slug org-boards) (map :uuid org-boards))]
      (ws-ic/board-unwatch (fn [rep]
        (doseq [board-slug board-slugs]
          (timbre/debug "Watching on socket " board-slug (org-board-map board-slug))
          (ws-ic/board-watch (org-board-map board-slug))))))))

;; Reads data

(defn request-reads-data
  "Request the list of readers of the given item."
  [item-id]
  (api/request-reads-data item-id))

(defn request-reads-count
  "Request the reads count data only for the items we don't have already."
  [item-ids]
  (let [cleaned-ids (au/clean-who-reads-count-ids item-ids (dis/activities-read-data))]
    (when (seq cleaned-ids)
      (api/request-reads-count cleaned-ids))))

;; All Posts
(defn all-posts-get-finish [from {:keys [body success]}]
  (when body
    (let [org-data (dis/org-data)
          org (router/current-org-slug)
          posts-data-key (dis/posts-data-key org)
          all-posts-data (when success (json->cljs body))
          fixed-all-posts (au/fix-container (:collection all-posts-data) (dis/change-data))
          should-404? (and from
                           (router/current-activity-id)
                           (not (get (:fixed-items fixed-all-posts) (router/current-activity-id))))]
      (when should-404?
        (router/redirect-404!))
      (when (and (not should-404?)
                 (= (router/current-board-slug) "all-posts"))
        (au/save-last-used-section "all-posts")
        (cook/set-cookie! (router/last-board-cookie org) "all-posts" (* 60 60 24 6)))
      (request-reads-count (keys (:fixed-items fixed-all-posts)))
      (watch-boards (:fixed-items fixed-all-posts))
      (dis/dispatch! [:all-posts-get/finish org fixed-all-posts]))))

(defn all-posts-get [org-data ap-initial-at]
  (when-let [activity-link (utils/link-for (:links org-data) "activity")]
    (api/get-all-posts activity-link ap-initial-at (partial all-posts-get-finish ap-initial-at))))

(defn all-posts-more-finish [direction {:keys [success body]}]
  (when success
    (request-reads-count (map :uuid (:items (json->cljs body)))))
  (dis/dispatch! [:all-posts-more/finish (router/current-org-slug) direction (when success (json->cljs body))]))

(defn all-posts-more [more-link direction]
  (api/load-more-all-posts more-link direction (partial all-posts-more-finish direction))
  (dis/dispatch! [:all-posts-more (router/current-org-slug)]))

;; Must see
(defn must-see-get-finish
  [{:keys [success body]}]
    (when body
    (let [org-data (dis/org-data)
          org (router/current-org-slug)
          must-see-data (when success (json->cljs body))
          must-see-posts (au/fix-container (:collection must-see-data) (dis/change-data))]
      (when (= (router/current-board-slug) "must-see")
        (au/save-last-used-section "must-see"))
      (watch-boards (:fixed-items must-see-posts))
      (dis/dispatch! [:must-see-get/finish org must-see-posts]))))

(defn must-see-get [org-data]
  (when-let [activity-link (utils/link-for (:links org-data) "activity")]
    (let [activity-href (:href activity-link)
          must-see-filter (str activity-href "?must-see=true")
          must-see-link (assoc activity-link :href must-see-filter)]
      (api/get-all-posts must-see-link nil (partial must-see-get-finish)))))

(defn must-see-more-finish [direction {:keys [success body]}]
  (when success
    (request-reads-count (map :uuid (:items (json->cljs body)))))
  (dis/dispatch! [:must-see-more/finish (router/current-org-slug) direction (when success (json->cljs body))]))

(defn must-see-more [more-link direction]
  (let [more-href (:href more-link)
        more-must-see-filter (str more-href "&must-see=true")
        more-must-see-link (assoc more-link :href more-must-see-filter)]
    (api/load-more-all-posts more-must-see-link direction (partial must-see-more-finish direction))
    (dis/dispatch! [:must-see-more (router/current-org-slug)])))

;; Referesh org when needed
(defn refresh-org-data-cb [{:keys [status body success]}]
  (let [org-data (json->cljs body)
        is-all-posts (or (:from-all-posts @router/path)
                         (= (router/current-board-slug) "all-posts"))
        board-data (some #(when (= (:slug %) (router/current-board-slug)) %) (:boards org-data))]
    (dis/dispatch! [:org-loaded org-data])
    (if is-all-posts
      (all-posts-get org-data (:ap-initial-at @dis/app-state))
      (sa/section-get (utils/link-for (:links board-data) "self" "GET")))))

(defn refresh-org-data []
  (api/get-org (dis/org-data) refresh-org-data-cb))

;; Entry
(defn get-entry-cache-key
  [entry-uuid]
  (str (or
        entry-uuid
        (router/current-org-slug))
   "-entry-edit"))

(defn remove-cached-item
  [item-uuid]
  (uc/remove-item (get-entry-cache-key item-uuid)))

(defn load-cached-item
  [entry-data edit-key & [completed-cb]]
  (let [cache-key (get-entry-cache-key (:uuid entry-data))]
    (uc/get-item cache-key
     (fn [item err]
       (if (and (not err)
                (map? item)
                (= (:updated-at entry-data) (:updated-at item)))
         (let [entry-to-save (merge item (select-keys entry-data [:links :board-slug :board-name]))]
           (dis/dispatch! [:input [edit-key] entry-to-save]))
         (do
           ;; If we got an item remove it since it won't be used
           ;; since we have an updated version of it already
           (when item
             (remove-cached-item (:uuid entry-data)))
           (dis/dispatch! [:input [edit-key] entry-data])))
       (when (fn? completed-cb)
         (completed-cb))))))

(defn activity-modal-fade-in
  [activity-data & [editing item-load-cb dismiss-on-editing-end]]
  (let [org (router/current-org-slug)
        board (:board-slug activity-data)
        activity-uuid (:uuid activity-data)
        to-url (oc-urls/entry board activity-uuid)]
    (.pushState (.-history js/window) #js {} "" to-url)
    (router/set-route! [org board activity-uuid "activity"]
     {:org org
      :board board
      :activity activity-uuid
      :previous-board (router/current-board-slug)
      :query-params (dissoc (:query-params @router/path) :ap-initial-at)
      :from-all-posts (= (router/current-board-slug) "all-posts")}))
  (when editing
    (utils/after 100 #(load-cached-item activity-data :modal-editing-data item-load-cb)))
  (dis/dispatch! [:activity-modal-fade-in activity-data editing dismiss-on-editing-end]))

(defn activity-modal-fade-out
  [activity-board-slug]
  (let [from-all-posts (:from-all-posts @router/path)
        previous-board-slug (:previous-board @router/path)
        to-board (cond
                   ;; If user was in AP go back there
                   from-all-posts "all-posts"
                   ;; if the previous position is set use it
                   (seq previous-board-slug) previous-board-slug
                   ;; use the passed activity board slug if present
                   (seq activity-board-slug) activity-board-slug
                   ;; fallback to the current board slug
                   :else (router/current-board-slug))
        org (router/current-org-slug)
        to-url (if from-all-posts
                (oc-urls/all-posts org)
                (oc-urls/board org to-board))]
    (.pushState (.-history js/window) #js {} "" to-url)
    (router/set-route! [org to-board (if from-all-posts "all-posts" "dashboard")]
     {:org org
      :board to-board
      :activity nil
      :previous-board nil
      :query-params (:query-params @router/path)
      :from-all-posts false}))
  (dis/dispatch! [:activity-modal-fade-out activity-board-slug]))

(defn- edit-open-cookie []
  (str "edit-open-" (jwt/user-id) "-" (:slug (dis/org-data))))

(defn entry-edit
  [initial-entry-data]
  (cook/set-cookie! (edit-open-cookie) (or (:uuid initial-entry-data) true) (* 60 30))
  (load-cached-item initial-entry-data :entry-editing))

(defn entry-edit-dismiss
  []
  ;; If the user was looking at the modal, dismiss it too
  (when (router/current-activity-id)
    (utils/after 1 #(let [from-all-posts (or
                                          (:from-all-posts @router/path)
                                          (= (router/current-board-slug) "all-posts"))]
                      (router/nav!
                        (if from-all-posts ; AP
                          (oc-urls/all-posts (router/current-org-slug))
                          (oc-urls/board (router/current-org-slug) (router/current-board-slug)))))))
  ;; Add :entry-edit-dissmissing for 1 second to avoid reopening the activity modal after edit is dismissed.
  (utils/after 1000 #(dis/dispatch! [:input [:entry-edit-dissmissing] false]))
  (dis/dispatch! [:entry-edit/dismiss]))

(defn activity-modal-edit
  [activity-data activate]
  (if activate
    (do
      (load-cached-item activity-data :modal-editing-data)
      (dis/dispatch! [:modal-editing-activate]))
    (dis/dispatch! [:modal-editing-deactivate])))


(declare entry-save)

(defn entry-save-on-exit
  [edit-key activity-data entry-body section-editing]
  (let [entry-map (assoc activity-data :body entry-body)
        cache-key (get-entry-cache-key (:uuid activity-data))]
    ;; Save the entry in the local cache without auto-saving or
    ;; we it will be picked up it won't be autosaved
    (uc/set-item cache-key (dissoc entry-map :auto-saving)
     (fn [err]
       (when-not err
         ;; auto save on drafts that have changes
         (when (and (not= "published" (:status entry-map))
                    (:has-changes entry-map)
                    (not (:auto-saving entry-map)))
           ;; dispatch that you are auto saving
           (dis/dispatch! [:update [edit-key ] #(merge % {:auto-saving true :has-changes false})])
           (entry-save edit-key entry-map section-editing
             (fn [entry-data-saved edit-key-saved {:keys [success body status]}]
               (if-not success
                 ;; If save fails let's make sure save will be retried on next call
                 (dis/dispatch! [:update [edit-key ] #(merge % {:auto-saving false :has-changes true})])
                 (let [json-body (json->cljs body)
                       board-data (if (:entries json-body)
                                    (au/fix-board json-body)
                                    false)
                       fixed-items (:fixed-items board-data)
                       entry-saved (if fixed-items
                                         ;; board creation
                                         (first (vals fixed-items))
                                         json-body)]
                   (cook/set-cookie! (edit-open-cookie) (:uuid entry-saved) (* 60 60 24 365))
                   ;; remove the initial document cache now that we have a uuid
                   ;; uuid didn't exist before
                   (when (and (nil? (:uuid entry-map))
                              (:uuid entry-saved))
                     (remove-cached-item (:uuid entry-map)))
                   ;; set the initial version number after the first auto save
                   ;; this is used to revert if user decides to lose the changes
                   (when (nil? (get @initial-revision (:uuid entry-saved)))
                     (swap! initial-revision assoc (:uuid entry-saved)
                            (or (:revision-id entry-map) -1)))
                   (when board-data
                     (dis/dispatch! [:entry-save-with-board/finish (router/current-org-slug) board-data]))
                   ;; add or update the entry in the app-state list of posts
                   ;; also move the updated data to the entry editing
                   (dis/dispatch! [:entry-auto-save/finish entry-saved edit-key entry-map])))))
           (dis/dispatch! [:entry-toggle-save-on-exit false])))))))

(defn entry-toggle-save-on-exit
  [enable?]
  (dis/dispatch! [:entry-toggle-save-on-exit enable?]))

(declare send-item-read)

(defn entry-save-finish [board-slug activity-data initial-uuid edit-key]
  (let [org-slug (router/current-org-slug)]
    (when (and (router/current-activity-id)
               (not= board-slug (router/current-board-slug)))
      (router/nav! (oc-urls/entry org-slug board-slug (:uuid activity-data))))
    (au/save-last-used-section board-slug)
    (refresh-org-data)
    ;; Remove saved cached item
    (remove-cached-item initial-uuid)
    ;; reset initial revision after successful save.
    ;; need a new revision number on the next edit.
    (swap! initial-revision dissoc (:uuid activity-data))
    (dis/dispatch! [:entry-save/finish (assoc activity-data :board-slug board-slug) edit-key])
    ;; Send item read
    (when (= (:status activity-data) "published")
      (send-item-read (:uuid activity-data)))))

(defn create-update-entry-cb [entry-data edit-key {:keys [success body status]}]
  (if success
    (entry-save-finish (:board-slug entry-data) (json->cljs body) (:uuid entry-data) edit-key)
    (dis/dispatch! [:entry-save/failed edit-key])))

(defn entry-modal-save-with-board-finish [activity-data response]
  (let [fixed-board-data (au/fix-board response)
        org-slug (router/current-org-slug)
        saved-activity-data (first (vals (:fixed-items fixed-board-data)))]
    (au/save-last-used-section (:slug fixed-board-data))
    (remove-cached-item (:uuid activity-data))
    ;; reset initial revision after successful save.
    ;; need a new revision number on the next edit.
    (swap! initial-revision dissoc (:uuid activity-data))
    (refresh-org-data)
    (when-not (= (:slug fixed-board-data) (router/current-board-slug))
      ;; If creating a new board, start watching changes
      (ws-cc/container-watch (:uuid fixed-board-data)))
    (dis/dispatch! [:entry-save-with-board/finish org-slug fixed-board-data])
    (when (= (:status saved-activity-data) "published")
      (send-item-read (:uuid saved-activity-data)))))

(defn board-name-exists-error [edit-key]
  (dis/dispatch!
   [:input
    [edit-key :section-name-error]
    utils/section-name-exists-error]))

(defn entry-modal-save [activity-data section-editing]
  (if (and (= (:board-slug activity-data) utils/default-section-slug)
           section-editing)
    (let [fixed-entry-data (dissoc activity-data :board-slug :board-name :invite-note)
          final-board-data (assoc section-editing :entries [fixed-entry-data])]
      (api/create-board final-board-data (:invite-note activity-data)
        (fn [{:keys [success status body]}]
          (if (= status 409)
            ;; Board name exists
            (board-name-exists-error :modal-editing-data)
            (let [board-data (when success (json->cljs body))]
              (when (router/current-activity-id)
                (router/nav! (oc-urls/entry (router/current-org-slug) (:slug board-data) (:uuid activity-data))))
              (entry-modal-save-with-board-finish activity-data board-data))))))
    (api/update-entry activity-data :modal-editing-data create-update-entry-cb))
  (dis/dispatch! [:entry-modal-save]))

(defn add-attachment [dispatch-input-key attachment-data]
  (dis/dispatch! [:activity-add-attachment dispatch-input-key attachment-data]))

(defn remove-attachment [dispatch-input-key attachment-data]
  (dis/dispatch! [:activity-remove-attachment dispatch-input-key attachment-data]))

(defn get-entry [entry-data]
  (api/get-entry entry-data
    (fn [{:keys [status success body]}]
      (dis/dispatch! [:activity-get/finish status (router/current-org-slug) (json->cljs body) nil]))))

(declare entry-revert)

(defn entry-clear-local-cache [item-uuid edit-key item]
  "Removes user local cache and also reverts any auto saved drafts."
  (remove-cached-item item-uuid)
  ;; revert draft to old version
  (timbre/debug "Reverting to " @initial-revision item-uuid)
  (when (not= "published" (:status item))
    (when-let [revision-id (get @initial-revision item-uuid)]
      (entry-revert revision-id item)))
  (dis/dispatch! [:entry-clear-local-cache edit-key]))

(defn entry-save
  ([edit-key edited-data section-editing]
     (entry-save edit-key edited-data section-editing create-update-entry-cb))

  ([edit-key edited-data section-editing entry-save-cb]
     (let [fixed-edited-data (assoc edited-data :status (or (:status edited-data) "draft"))
           fixed-edit-key (or edit-key :entry-editing)]
       (if (:links fixed-edited-data)
         (if (and (= (:board-slug fixed-edited-data) utils/default-section-slug)
                  section-editing)
           ;; Save existing post to new board
           (let [fixed-entry-data (dissoc fixed-edited-data :board-slug :board-name :invite-note)
                 final-board-data (assoc section-editing :entries [fixed-entry-data])]
             (api/create-board final-board-data (:invite-note fixed-edited-data)
               (fn [{:keys [success status body] :as response}]
                 (if (= status 409)
                   ;; Board name exists
                   (board-name-exists-error fixed-edit-key)
                   (entry-save-cb fixed-edited-data fixed-edit-key response)))))
           ;; Update existing post
           (api/update-entry fixed-edited-data fixed-edit-key entry-save-cb))
         (if (and (= (:board-slug fixed-edited-data) utils/default-section-slug)
                  section-editing)
           ;; Save new post to new board
           (let [fixed-entry-data (dissoc fixed-edited-data :board-slug :board-name :invite-note)
                 final-board-data (assoc section-editing :entries [fixed-entry-data])]
             (api/create-board final-board-data (:invite-note fixed-edited-data)
               (fn [{:keys [success status body] :as response}]
                 (if (= status 409)
                   ;; Board name exists
                   (board-name-exists-error fixed-edit-key)
                   (entry-save-cb fixed-edited-data fixed-edit-key response)))))
           ;; Save new post to existing board
           (let [org-slug (router/current-org-slug)
                 entry-board-data (dis/board-data @dis/app-state org-slug (:board-slug fixed-edited-data))
                 entry-create-link (utils/link-for (:links entry-board-data) "create")]
             (api/create-entry fixed-edited-data fixed-edit-key entry-create-link entry-save-cb))))
       (dis/dispatch! [:entry-save fixed-edit-key]))))

(defn entry-publish-finish [initial-uuid edit-key board-slug activity-data]
  ;; Save last used section
  (au/save-last-used-section board-slug)
  (refresh-org-data)
  ;; Remove entry cached edits
  (remove-cached-item initial-uuid)
  ;; reset initial revision after successful publish.
  ;; need a new revision number on the next edit.
  (swap! initial-revision dissoc (:uuid activity-data))
  (dis/dispatch! [:entry-publish/finish edit-key activity-data])
  ;; Send item read
  (send-item-read (:uuid activity-data)))

(defn entry-publish-cb [entry-uuid posted-to-board-slug edit-key {:keys [status success body]}]
  (if success
    (entry-publish-finish entry-uuid edit-key posted-to-board-slug (when success (json->cljs body)))
    (dis/dispatch! [:entry-publish/failed edit-key])))

(defn entry-publish-with-board-finish [entry-uuid edit-key new-board-data]
  (let [board-slug (:slug new-board-data)
        saved-activity-data (first (:entries new-board-data))]
    (au/save-last-used-section (:slug new-board-data))
    (remove-cached-item entry-uuid)
    ;; reset initial revision after successful publish.
    ;; need a new revision number on the next edit.
    (swap! initial-revision dissoc entry-uuid)
    (refresh-org-data)
    (when-not (= (:slug new-board-data) (router/current-board-slug))
      ;; If creating a new board, start watching changes
      (ws-cc/container-watch (:uuid new-board-data)))
    (dis/dispatch! [:entry-publish-with-board/finish new-board-data edit-key])
    ;; Send item read
    (send-item-read (:uuid saved-activity-data))))

(defn entry-publish-with-board-cb [entry-uuid edit-key {:keys [status success body]}]
  (if (= status 409)
    ; Board name already exists
    (board-name-exists-error :section-editing)
    (entry-publish-with-board-finish entry-uuid edit-key (when success (json->cljs body)))))

(defn entry-publish [entry-editing section-editing & [edit-key]]
  (let [fixed-entry-editing (assoc entry-editing :status "published")
        fixed-edit-key (or edit-key :entry-editing)]
    (if (and (= (:board-slug fixed-entry-editing) utils/default-section-slug)
             section-editing)
      (let [fixed-entry-data (dissoc fixed-entry-editing :board-slug :board-name :invite-note)
            final-board-data (assoc section-editing :entries [fixed-entry-data])]
        (api/create-board final-board-data (:invite-note section-editing)
         (partial entry-publish-with-board-cb (:uuid fixed-entry-editing) fixed-edit-key)))
      (let [entry-exists? (seq (:links fixed-entry-editing))
            org-slug (router/current-org-slug)
            board-data (dis/board-data @dis/app-state org-slug (:board-slug fixed-entry-editing))
            publish-entry-link (if entry-exists?
                                ;; If the entry already exists use the publish link in it
                                (utils/link-for (:links fixed-entry-editing) "publish")
                                ;; If the entry is new, use
                                (utils/link-for (:links board-data) "create"))]
        (api/publish-entry fixed-entry-editing publish-entry-link
         (partial entry-publish-cb (:uuid fixed-entry-editing) (:board-slug fixed-entry-editing) fixed-edit-key))))
    (dis/dispatch! [:entry-publish fixed-edit-key])))

(defn activity-delete-finish []
  ;; Reload the org to update the number of drafts in the navigation
  (when (= (router/current-board-slug) utils/default-drafts-board-slug)
    (refresh-org-data)))

(defn activity-delete [activity-data]
  (api/delete-activity activity-data activity-delete-finish)
  (dis/dispatch! [:activity-delete (router/current-org-slug) activity-data]))

(defn activity-move [activity-data board-data]
  (let [fixed-activity-data (assoc activity-data :board-slug (:slug board-data))]
    (api/update-entry fixed-activity-data nil create-update-entry-cb)
    (dis/dispatch! [:activity-move activity-data (router/current-org-slug) board-data])))

(defn activity-share-show [activity-data & [element-id share-medium]]
  (dis/dispatch! [:activity-share-show activity-data element-id (or share-medium :url)]))

(defn activity-share-hide []
  (dis/dispatch! [:activity-share-hide]))

(defn activity-share-reset []
  (dis/dispatch! [:activity-share-reset]))

(defn activity-share-cb [{:keys [status success body]}]
  (dis/dispatch! [:activity-share/finish success (when success (json->cljs body))]))

(defn activity-share [activity-data share-data & [share-cb]]
  (api/share-activity activity-data share-data (or share-cb activity-share-cb))
  (dis/dispatch! [:activity-share share-data]))

(defn entry-revert [revision-id entry-editing]
  (when (not (nil? revision-id))
    (let [entry-exists? (seq (:links entry-editing))
          entry-version (assoc entry-editing :revision-id revision-id)
          org-slug (router/current-org-slug)
          board-data (dis/board-data @dis/app-state org-slug (:board-slug entry-editing))
          revert-entry-link (when entry-exists?
                              ;; If the entry already exists use the publish link in it
                              (utils/link-for (:links entry-editing) "revert"))]
      (if entry-exists?
        (api/revert-entry entry-version revert-entry-link
                          (fn [{:keys [success body]}]
                            (dis/dispatch! [:entry-revert entry-version])
                            (when success
                              (dis/dispatch! [:entry-revert/finish (json->cljs body)]))))
        (dis/dispatch! [:entry-revert false])))))

(defn activity-get-finish [status activity-data secure-uuid]
  (when (= status 404)
    (router/redirect-404!))
  (when (and secure-uuid
             (jwt/jwt)
             (jwt/user-is-part-of-the-team (:team-id activity-data)))
    (router/nav! (oc-urls/entry (router/current-org-slug) (:board-slug activity-data) (:uuid activity-data))))
  (dis/dispatch! [:activity-get/finish status (router/current-org-slug) activity-data secure-uuid]))

(defn secure-activity-get-finish [{:keys [status success body]}]
  (activity-get-finish status (if success (json->cljs body) {}) (router/current-secure-activity-id)))

(defn secure-activity-get []
  (api/get-secure-activity (router/current-org-slug) (router/current-secure-activity-id) secure-activity-get-finish))

;; Change reaction

(defn activity-change [section-uuid activity-uuid]
  (let [org-data (dis/org-data)
        section-data (first (filter #(= (:uuid %) section-uuid) (:boards org-data)))
        activity-data (dis/activity-data (:slug org-data) activity-uuid)]
    (when activity-data
      (get-entry activity-data))))

;; Change service actions

(defn ws-change-subscribe []
  (ws-cc/subscribe :container/change
    (fn [data]
      (let [change-data (:data data)
            section-uuid (:item-id change-data)
            change-type (:change-type change-data)]
        ;; Refresh AP if user is looking at it
        (when (= (router/current-board-slug) "all-posts")
          (all-posts-get (dis/org-data) (dis/ap-initial-at))))))
  (ws-cc/subscribe :item/change
    (fn [data]
      (let [change-data (:data data)
            activity-uuid (:item-id change-data)
            section-uuid (:container-id change-data)
            change-type (:change-type change-data)]
        (when (= change-type :delete)
          (dis/dispatch! [:activity-delete (router/current-org-slug) {:uuid activity-uuid}]))
        ;; Refresh the AP in case of items added or removed
        (when (or (= change-type :add)
                  (= change-type :delete))
          (when (= (router/current-board-slug) "all-posts")
            (all-posts-get (dis/org-data) (dis/ap-initial-at)))
          (when (= (router/current-board-slug) "must-see")
            (must-see-get (dis/org-data))))
        ;; Refresh the activity in case of an item update
        (when (= change-type :update)
          (activity-change section-uuid activity-uuid)))))
  (ws-cc/subscribe :item/counts
    (fn [data]
      (dis/dispatch! [:activities-count (:data data)])))
  (ws-cc/subscribe :item/status
    (fn [data]
      (dis/dispatch! [:activity-reads (:item-id (:data data)) (:reads (:data data)) (dis/team-roster)]))))

;; AP Seen

(defn- send-item-seen
  "Actually send the seen. Needs to get the activity data from the app-state
  to read the published-at and make sure it's still inside the TTL."
  [activity-id]
  (when-let* [activity-data (dis/activity-data (router/current-org-slug) activity-id)
              publisher-id (:user-id (:publisher activity-data))
              container-id (:board-uuid activity-data)
              published-at-ts (.getTime (utils/js-date (:published-at activity-data)))
              today-ts (.getTime (utils/js-date))
              oc-seen-ttl-ms (* ls/oc-seen-ttl 24 60 60 1000)
              minimum-ttl (- today-ts oc-seen-ttl-ms)]
    (when (> published-at-ts minimum-ttl)
      ;; Send the seen because:
      ;; 1. item is published
      ;; 2. item is newer than TTL
      (ws-cc/item-seen publisher-id container-id activity-id))))

(def ap-seen-timeouts-list (atom {}))
(def ap-seen-wait-interval 3)

(defn ap-seen-events-gate
  "Gate to throttle too many seen call for the same UUID.
  Set a timeout to ap-seen-wait-interval seconds every time it's called with a new UUID,
  if there was already a timeout for that item remove the old one.
  Once the timeout finishes it means no other events were fired for it so we can send a seen.
  It will send seen every 3 seconds or more."
  [activity-id]
  ;; Discard everything if we are not on AP
  (when (= :all-posts (keyword (router/current-board-slug)))
    (let [wait-interval-ms (* ap-seen-wait-interval 1000)]
      ;; Remove the old timeout if there is
      (when-let [uuid-timeout (get @ap-seen-timeouts-list activity-id)]
        (.clearTimeout js/window uuid-timeout))
      ;; Set the new timeout
      (swap! ap-seen-timeouts-list assoc activity-id
       (utils/after wait-interval-ms
        (fn []
         (swap! ap-seen-timeouts-list dissoc activity-id)
         (send-item-seen activity-id)))))))

;; WRT read

(defn- send-item-read
  "Actually send the read. Needs to get the activity data from the app-state
  to read the published-id and the board uuid."
  [activity-id]
  (when-let* [activity-key (dis/activity-key (router/current-org-slug) activity-id)
              activity-data (get-in @dis/app-state activity-key)
              org-id (:uuid (dis/org-data))
              container-id (:board-uuid activity-data)
              user-name (jwt/get-key :name)
              avatar-url (jwt/get-key :avatar-url)]
    (ws-cc/item-read org-id container-id activity-id user-name avatar-url)))

(def wrt-timeouts-list (atom {}))
(def wrt-wait-interval 3)

(defn wrt-events-gate
  "Gate to throttle too many wrt call for the same UUID.
  Set a timeout to wrt-wait-interval seconds every time it's called with a certain UUID,
  if there was already a timeout for that item remove it and reset it.
  Once the timeout finishes it means no other events were fired for it so we can send a seen.
  It will send seen every 3 seconds or more."
  [activity-id]
  (let [wait-interval-ms (* wrt-wait-interval 1000)]
    ;; Remove the old timeout if there is
    (when-let [uuid-timeout (get @wrt-timeouts-list activity-id)]
      (.clearTimeout js/window uuid-timeout))
    ;; Set the new timeout
    (swap! wrt-timeouts-list assoc activity-id
     (utils/after wait-interval-ms
      (fn []
       (swap! wrt-timeouts-list dissoc activity-id)
       (send-item-read activity-id))))))

(defn toggle-must-see [activity-data]
  (let [must-see (:must-see activity-data)
        must-see-toggled (update-in activity-data [:must-see] not)
        org-data (dis/org-data)
        must-see-count (:must-see-count dis/org-data)
        new-must-see-count (if-not must-see
                             (inc must-see-count)
                             (dec must-see-count))]
    (dis/dispatch! [:org-loaded
                    (assoc org-data :must-see-count new-must-see-count)
                    false])
    (dis/dispatch! [:must-see-toggle (router/current-org-slug) must-see-toggled])
    (api/update-entry must-see-toggled :must-see
                      (fn [entry-data edit-key {:keys [success body status]}]
                        (if success
                          (api/get-org org-data
                            (fn [{:keys [status body success]}]
                              (let [api-org-data (json->cljs body)]
                                (dis/dispatch! [:org-loaded api-org-data false])
                                (must-see-get api-org-data))))
                          (dis/dispatch! [:activity-get/finish
                                           status
                                           (router/current-org-slug)
                                           (json->cljs body)
                                           nil]))))))

;; Video handling

(defn uploading-video [video-id]
  (dis/dispatch! [:uploading-video (router/current-org-slug) video-id]))

(defn remove-video [edit-key]
  (dis/dispatch! [:update [edit-key] #(merge % {:fixed-video-id nil
                                                :video-id nil
                                                :video-transcript nil
                                                :video-processed false
                                                :video-error false
                                                :has-changes true})]))

(defn prompt-remove-video [edit-key]
  (let [alert-data {:icon "/img/ML/trash.svg"
                    :action "rerecord-video"
                    :message "Are you sure you want to delete the current video? This can’t be undone."
                    :link-button-title "Keep"
                    :link-button-cb #(alert-modal/hide-alert)
                    :solid-button-style :red
                    :solid-button-title "Yes"
                    :solid-button-cb (fn []
                                      (remove-video edit-key)
                                      (alert-modal/hide-alert))}]
    (alert-modal/show-alert alert-data)))

(defn video-started-recording-cb [edit-key video-token]
  (dis/dispatch! [:update [edit-key] #(merge % {:fixed-video-id video-token
                                                :video-id video-token
                                                ;; default video error to true
                                                :video-error true
                                                :has-changes true})]))

(defn video-processed-cb [edit-key video-token unmounted?]
  (when-not unmounted?
    (dis/dispatch! [:update [edit-key] #(merge % {:fixed-video-id video-token
                                                  :video-id video-token
                                                  ;; turn off video error since upload finished
                                                  :video-error false
                                                  :has-changes true})])))

;; Sample post handling

(defn delete-all-sample-posts []
  (let [all-posts (dis/posts-data)
        sample-posts (filterv :sample (vals all-posts))]
    (when (router/current-activity-id)
      (router/nav! (oc-urls/all-posts)))
    (doseq [post sample-posts]
      (activity-delete post))))

(defn has-sample-posts []
  (let [all-posts (dis/posts-data)
        sample-posts (filterv :sample (vals all-posts))]
    (pos? (count sample-posts))))

;; Cmail

(defn- cmail-fullscreen-cookie []
  (str "cmail-fullscreen-" (jwt/user-id)))

(defn- cmail-fullscreen-save [fullscreen?]
  (cook/set-cookie! (cmail-fullscreen-cookie) fullscreen? (* 60 60 24 30)))

(defn cmail-show [initial-entry-data & [cmail-state]]
  (let [cmail-default-state {:collapse false
                             :fullscreen (= (cook/get-cookie (cmail-fullscreen-cookie)) "true")}
        cleaned-cmail-state (dissoc cmail-state :auto)
        fixed-cmail-state (merge cmail-default-state cleaned-cmail-state)]
    (when-not (:auto cmail-state)
      (cook/set-cookie! (edit-open-cookie) (or (:uuid initial-entry-data) true) (* 60 60 24 365)))
    (load-cached-item initial-entry-data :cmail-data
     #(dis/dispatch! [:input [:cmail-state] fixed-cmail-state]))))

(defn cmail-hide []
  (cook/remove-cookie! (edit-open-cookie))
  (dis/dispatch! [:input [:cmail-data] nil])
  (dis/dispatch! [:input [:cmail-state] nil]))

(defn cmail-toggle-fullscreen []
  (cmail-fullscreen-save (not (:fullscreen (:cmail-state @dis/app-state))))
  (dis/dispatch! [:update [:cmail-state] #(merge % {:collapse false
                                                    :fullscreen (not (:fullscreen %))})]))

(defn cmail-toggle-collapse []
  (dis/dispatch! [:update [:cmail-state] #(merge % {:collapse (not (:collapse %))
                                                    :fullscreen (:fullscreen %)})]))

(defn cmail-toggle-must-see []
  (dis/dispatch! [:update [:cmail-data] #(merge % {:must-see (not (:must-see %))
                                                   :has-changes true})]))

(defonce cmail-reopen-only-one (atom false))

(defn cmail-reopen? []
  (when (compare-and-set! cmail-reopen-only-one false true)
    (when-let [activity-uuid (cook/get-cookie (edit-open-cookie))]
      (if (responsive/is-tablet-or-mobile?)
        (entry-edit (dis/activity-data activity-uuid))
        (cmail-show (dis/activity-data activity-uuid))))))

(defn activity-edit
  [activity-data]
  (let [fixed-activity-data (if (not (seq (:uuid activity-data)))
                              (assoc activity-data :must-see (= (router/current-board-slug) "must-see"))
                              activity-data)
        is-published? (= (:status fixed-activity-data) "published")]
    (if (responsive/is-tablet-or-mobile?)
      (entry-edit fixed-activity-data)
      (cmail-show fixed-activity-data (if is-published? {:fullscreen true :auto true} {})))))