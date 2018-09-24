(ns oc.web.actions.section
  (:require [taoensso.timbre :as timbre]
            [oc.web.dispatcher :as dispatcher]
            [oc.web.api :as api]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.lib.utils :as utils]
            [oc.web.utils.activity :as au]
            [oc.web.lib.ws-change-client :as ws-cc]
            [oc.web.lib.ws-interaction-client :as ws-ic]
            [oc.web.lib.json :refer (json->cljs cljs->json)]))

(defn is-currently-shown? [section]
  (= (router/current-board-slug)
     (:slug section)))

(defn watch-single-section [section]
  ;; only watch the currently visible board.
  (ws-ic/board-unwatch (fn [rep]
    (timbre/debug rep "Watching on socket " (:uuid section))
        (ws-ic/board-watch (:uuid section)))))

(defn section-seen
  [uuid]
  ;; Let the change service know we saw the board
  (ws-cc/container-seen uuid))

(defn section-get-finish
  [section]
  (let [is-currently-shown (is-currently-shown? section)
        user-is-part-of-the-team (jwt/user-is-part-of-the-team (:team-id (dispatcher/org-data)))]
    (when is-currently-shown

      (when user-is-part-of-the-team
        ;; Tell the container service that we are seeing this board,
        ;; and update change-data to reflect that we are seeing this board
        (when-let [section-uuid (:uuid section)]
          (utils/after 10 #(section-seen section-uuid)))
        ;; only watch the currently visible board.
        ; only for logged in users
        (when (jwt/jwt)
          (watch-single-section section))))

    ;; Retrieve reads count if there are items in the loaded section
    (when (and user-is-part-of-the-team
               (not= (:slug section) utils/default-drafts-board-slug)
               (seq (:entries section)))
      (let [item-ids (map :uuid (:entries section))
            cleaned-ids (au/clean-who-reads-count-ids item-ids (dispatcher/activities-read-data))]
        (when (seq cleaned-ids)
          (api/request-reads-count cleaned-ids))))
    (dispatcher/dispatch! [:section (assoc section :is-loaded is-currently-shown)])))

(defn load-other-sections
  [sections]
  (doseq [section sections
          :when (not (is-currently-shown? section))]
    (api/get-board (utils/link-for (:links section) ["item" "self"] "GET")
      (fn [status body success]
        (section-get-finish (json->cljs body))))))

(declare refresh-org-data)

(defn section-change
  [section-uuid]
  (timbre/debug "Section change:" section-uuid)
  (utils/after 0 (fn []
    (let [current-section-data (dispatcher/board-data)]
      (when (= section-uuid (:uuid utils/default-drafts-board))
        (refresh-org-data))
      (if (= section-uuid (:uuid current-section-data))
        ;; Reload the current board data
        (api/get-board (utils/link-for (:links current-section-data) "self")
                       (fn [status body success]
                         (when success (section-get-finish (json->cljs body)))))
        ;; Reload a secondary board data
        (let [sections (:boards (dispatcher/org-data))
              filtered-sections (filter #(= (:uuid %) section-uuid) sections)]
          (load-other-sections filtered-sections))))))
  ;; Update change-data state that the board has a change
  (dispatcher/dispatch! [:section-change section-uuid]))

(defn section-get
  [link]
  (api/get-board link
    (fn [status body success]
      (when success (section-get-finish (json->cljs body))))))

(defn section-delete [section-slug]
  (api/delete-board section-slug (fn [status success body]
    (if success
      (let [org-slug (router/current-org-slug)
            last-used-section-slug (au/last-used-section)]
        (when (= last-used-section-slug section-slug)
          (au/save-last-used-section nil))
        (if (= section-slug (router/current-board-slug))
          (do
            (router/nav! (oc-urls/all-posts org-slug))
            (api/get-org (dispatcher/org-data)
              (fn [{:keys [status body success]}]
                (dispatcher/dispatch! [:org-loaded (json->cljs body)]))))
          (dispatcher/dispatch! [:section-delete org-slug section-slug])))
      (.reload (.-location js/window))))))

(defn refresh-org-data []
  (api/get-org (dispatcher/org-data)
    (fn [{:keys [status body success]}]
      (dispatcher/dispatch! [:org-loaded (json->cljs body)]))))

(defn section-name-error [status]
  ;; Board name exists or too short
  (dispatcher/dispatch!
   [:input
    [:section-editing :section-name-error]
    (cond
      (= status 409) "Section name already exists or isn't allowed"
      :else "An error occurred, please retry.")]))

(defn section-save
  ([section-data note] (section-save section-data note nil))
  ([section-data note success-cb]
    (section-save section-data note success-cb section-name-error))
  ([section-data note success-cb error-cb]
    (timbre/debug section-data)
    (if (empty? (:links section-data))
      (api/create-board section-data note
        (fn [{:keys [success status body]}]
          (let [section-data (when success (json->cljs body))]
            (if-not success
              (when (fn? error-cb)
                (error-cb status))
              (do
                (utils/after 100 #(router/nav! (oc-urls/board (router/current-org-slug) (:slug section-data))))
                (utils/after 500 refresh-org-data)
                (ws-cc/container-watch (:uuid section-data))
                (dispatcher/dispatch! [:section-edit-save/finish section-data])
                (when (fn? success-cb)
                  (success-cb)))))))
      (api/patch-board section-data note (fn [success body status]
        (if-not success
          (when (fn? error-cb)
            (error-cb status))
          (do
            (refresh-org-data)
            (dispatcher/dispatch! [:section-edit-save/finish (json->cljs body)])
            (when (fn? success-cb)
              (success-cb)))))))))

(defn private-section-user-add
  [user user-type]
  (dispatcher/dispatch! [:private-section-user-add user user-type]))

(defn private-section-user-remove
  [user]
  (dispatcher/dispatch! [:private-section-user-remove user]))

(defn private-section-kick-out-self
  [user]
  (when (= (:user-id user) (jwt/user-id))
    (api/remove-user-from-private-board user (fn [status success body]
      ;; Redirect to the first available board
      (let [org-data (dispatcher/org-data)
            all-boards (:boards org-data)
            current-board-slug (router/current-board-slug)
            except-this-boards (remove #(#{current-board-slug "drafts"} (:slug %)) all-boards)
            redirect-url (if-let [next-board (first except-this-boards)]
                           (oc-urls/board (:slug next-board))
                           (oc-urls/org (router/current-org-slug)))]
        (refresh-org-data)
        (utils/after 0 #(router/nav! redirect-url))
        (dispatcher/dispatch! [:private-section-kick-out-self/finish success]))))))

(defn ws-comment-add
  [interaction-data]
  (let [org-slug   (router/current-org-slug)
        board-slug (router/current-board-slug)
        activity-uuid (:resource-uuid interaction-data)
        entry-data (dispatcher/activity-data org-slug activity-uuid)]
    (when-not entry-data
      (let [board-data (dispatcher/board-data)]
        (section-get (utils/link-for (:links board-data) ["item" "self"] "GET"))))))

(defn ws-change-subscribe []
  (ws-cc/subscribe :container/status
    (fn [data]
      (let [status-by-uuid (group-by :container-id (:data data))
            clean-change-data (zipmap (keys status-by-uuid) (->> status-by-uuid
                                                              vals
                                                              ; remove the sequence of 1 from group-by
                                                              (map first)))]
        (dispatcher/dispatch! [:container/status clean-change-data]))))

  (ws-cc/subscribe :container/change
    (fn [data]
      (let [change-data (:data data)
            section-uuid (:item-id change-data)
            change-type (:change-type change-data)]
        ;; Refresh the section only in case of an update, let the org
        ;; handle the add and delete cases
        (when (= change-type :update)
          (section-change section-uuid)))))
  (ws-cc/subscribe :item/change
    (fn [data]
      (let [change-data (:data data)
            section-uuid (:container-id change-data)
            change-type (:change-type change-data)
            org-slug (router/current-org-slug)
            item-id (:item-id change-data)]
        ;; Refresh the section only in case of items added or removed
        ;; let the activity handle the item update case
        (when (or (= change-type :add)
                  (= change-type :delete))
          (section-change section-uuid))
        ;; On item/change :add let's add the UUID to the unseen list of
        ;; the specified container to make sure it's marked as seen
        (when (and (= change-type :add)
                   (not= (:user-id change-data) (jwt/user-id)))
          (dispatcher/dispatch! [:item-add/unseen (router/current-org-slug) change-data]))
        (when (= change-type :delete)
          (dispatcher/dispatch! [:item-delete/unseen (router/current-org-slug) change-data]))))))

(defn ws-interaction-subscribe []
  (ws-ic/subscribe :interaction-comment/add
                   #(ws-comment-add (:data %))))

;; Section editing

(def min-section-name-length 2)

(defn section-save-create [section-editing section-name success-cb]
  (if (< (count section-name) min-section-name-length)
    (dispatcher/dispatch! [:section-edit/error (str "Name must be at least " min-section-name-length " characters.")])
    (let [next-section-editing (merge section-editing {:slug utils/default-section-slug
                                                       :name section-name})]
      (dispatcher/dispatch! [:input [:section-editing] next-section-editing])
      (success-cb next-section-editing))))

(defn pre-flight-check [section-slug section-name]
  (dispatcher/dispatch! [:input [:section-editing :pre-flight-loading] true])
  (let [org-data (dispatcher/org-data)
        pre-flight-link (utils/link-for (:links org-data) "pre-flight-create")]
    (api/pre-flight-section-check pre-flight-link section-slug section-name
     (fn [{:keys [success body status]}]
       (when-not success
         (section-name-error status))
       (dispatcher/dispatch! [:input [:section-editing :pre-flight-loading] false])))))

(defn show-section-add-with-callback [callback]
  (dispatcher/dispatch! [:input [:show-section-add-cb]
   (fn [sec-data note]
     (callback sec-data note)
     (dispatcher/dispatch! [:input [:show-section-add-cb] nil])
     (dispatcher/dispatch! [:input [:show-section-add] false]))])
  (dispatcher/dispatch! [:input [:show-section-add] true]))