(ns oc.web.dispatcher
  (:require [defun.core :refer (defun)]
            [taoensso.timbre :as timbre]
            [cljs-flux.dispatcher :as flux]
            [oc.web.utils.drafts :as du]))


(defn- s-or-k? [x]
  (or (keyword? x) (string? x)))

(defonce ^{:export true} app-state (atom {:loading false
                                          :show-login-overlay false}))

(def recent-activity-sort :recent-activity)
(def recently-posted-sort :recently-posted)

(def default-foc-layout :expanded)
(def other-foc-layout :collapsed)

(def premium-picker-modal :show-premium-picker?)

;; Pre-declare some routing functions

(declare current-org-slug)
(declare current-board-slug)
(declare current-contributions-id)
(declare current-container-slug)
(declare current-sort-type)
(declare current-activity-id)
(declare current-secure-activity-id)
(declare current-comment-id)
(declare query-params)
(declare query-param)

;; Data key paths

(def router-key :router-path)
(def router-opts-key :opts)
(def router-dark-allowed-key :dark-allowed)

(def checkout-result-key :checkout-success-result)
(def checkout-update-price-key :checkout-update-price)

(def expo-key [:expo])

(def expo-deep-link-origin-key (vec (conj expo-key :deep-link-origin)))
(def expo-app-version-key (vec (conj expo-key :app-version)))
(def expo-push-token-key (vec (conj expo-key :push-token)))

(def show-invite-box-key :show-invite-box)

(def api-entry-point-key [:api-entry-point])

(def auth-settings-key [:auth-settings])

(def notifications-key [:notifications-data])
(def show-login-overlay-key :show-login-overlay)

(def current-user-key [:current-user-data])

(def orgs-key :orgs)

(defn org-key [org-slug]
  [(keyword org-slug)])

(defn org-data-key [org-slug]
  (vec (conj (org-key org-slug) :org-data)))

(defn boards-key [org-slug]
  (vec (conj (org-key org-slug) :boards)))

;; Editable boards keys

(defn editable-boards-key [org-slug]
  (vec (conj (org-key org-slug) :editable-boards)))

(defn private-boards-key [org-slug]
  (vec (conj (org-key org-slug) :private-boards)))

(defn public-boards-key [org-slug]
  (vec (conj (org-key org-slug) :public-boards)))

(defn payments-key [org-slug]
  (vec (conj (org-key org-slug) :payments)))

(defn payments-notify-cache-key [org-slug]
  (vec (conj (org-key org-slug) :payments-notify-cache)))

(defn posts-data-key [org-slug]
  (vec (conj (org-key org-slug) :posts)))

(defn board-key 
  ([org-slug board-slug sort-type]
    (if sort-type
      (vec (concat (boards-key org-slug) [(keyword board-slug) (keyword sort-type)]))
      (vec (concat (boards-key org-slug) [(keyword board-slug)]))))
  ([org-slug board-slug]
   (vec (concat (boards-key org-slug) [(keyword board-slug) recently-posted-sort]))))

(defn board-data-key
  ([org-slug board-slug]
   (board-data-key org-slug board-slug recently-posted-sort))
  ([org-slug board-slug sort-type]
    (conj (board-key org-slug board-slug sort-type) :board-data)))

(defn contributions-list-key [org-slug]
  (vec (conj (org-key org-slug) :contribs)))

(defn contributions-key
  ([org-slug author-uuid]
   (contributions-key org-slug author-uuid recently-posted-sort))
  ([org-slug author-uuid sort-type]
   (if sort-type
     (vec (concat (contributions-list-key org-slug) [(keyword author-uuid) (keyword sort-type)]))
     (vec (conj (contributions-list-key org-slug) (keyword author-uuid))))))

(defn contributions-data-key
  ([org-slug slug-or-uuid sort-type]
   (conj (contributions-key org-slug slug-or-uuid sort-type) :contrib-data))
  ([org-slug slug-or-uuid]
   (conj (contributions-key org-slug slug-or-uuid) :contrib-data)))

(defn containers-key [org-slug]
  (vec (conj (org-key org-slug) :container-data)))

(defn container-key
  ([org-slug items-filter]
   (container-key org-slug items-filter recently-posted-sort))
  ([org-slug items-filter sort-type]
   (cond
     sort-type
     (vec (conj (containers-key org-slug) (keyword items-filter) (keyword sort-type)))
     :else
     (vec (conj (containers-key org-slug) (keyword items-filter))))))

(defn badges-key [org-slug]
  (vec (conj (org-key org-slug) :badges)))

(defn replies-badge-key [org-slug]
  (vec (conj (badges-key org-slug) :replies)))

(defn following-badge-key [org-slug]
  (vec (conj (badges-key org-slug) :following)))

(defn secure-activity-key [org-slug secure-id]
  (vec (concat (org-key org-slug) [:secure-activities secure-id])))

(defn activity-key [org-slug activity-uuid]
  (let [posts-key (posts-data-key org-slug)]
    (vec (concat posts-key [activity-uuid]))))

(defn pins-key [org-slug entry-uuid]
  (let [entry-key (activity-key org-slug entry-uuid)]
    (vec (concat entry-key [:pins]))))

(defn pin-key [org-slug entry-uuid pin-container-uuid]
  (vec (concat (pins-key org-slug entry-uuid) pin-container-uuid)))

(defn activity-last-read-at-key [org-slug activity-uuid]
  (vec (conj (activity-key org-slug activity-uuid) :last-read-at)))

(defn add-comment-key [org-slug]
  (vec (concat (org-key org-slug) [:add-comment-data])))

(defn add-comment-string-key
  ([activity-uuid] (add-comment-string-key activity-uuid nil nil))
  ([activity-uuid parent-comment-uuid] (add-comment-string-key activity-uuid parent-comment-uuid nil))
  ([activity-uuid parent-comment-uuid comment-uuid]
   (str activity-uuid
     (when parent-comment-uuid
       (str "-" parent-comment-uuid))
     (when comment-uuid
       (str "-" comment-uuid)))))

(def add-comment-force-update-root-key :add-comment-force-update)

(defn add-comment-force-update-key [add-comment-string-key]
  (vec (concat [add-comment-force-update-root-key] [add-comment-string-key])))

(defn add-comment-activity-key [org-slug activity-uuid]
  (vec (concat (add-comment-key org-slug) [activity-uuid])))

(defn comment-reply-to-key [org-slug]
  (vec (conj (org-key org-slug) :comment-reply-to-key)))

(defn comments-key [org-slug]
  (vec (conj (org-key org-slug) :comments)))

(defn activity-comments-key [org-slug activity-uuid]
  (vec (conj (comments-key org-slug) activity-uuid)))

(def sorted-comments-key :sorted-comments)

(defn activity-sorted-comments-key [org-slug activity-uuid]
  (vec (concat (comments-key org-slug) [activity-uuid sorted-comments-key])))

(def teams-data-key [:teams-data :teams])

(defn team-data-key [team-id]
  [:teams-data team-id :data])

(defn team-roster-key [team-id]
  [:teams-data team-id :roster])

(defn team-channels-key [team-id]
  [:teams-data team-id :channels])

(defn active-users-key [org-slug]
  (vec (conj (org-key org-slug) :active-users)))

(defn follow-list-key [org-slug]
  (vec (conj (org-key org-slug) :follow-list)))

(defn follow-list-last-added-key [org-slug]
  (vec (conj (org-key org-slug) :follow-list-last-added)))

(defn follow-publishers-list-key [org-slug]
  (vec (conj (follow-list-key org-slug) :publisher-uuids)))

(defn follow-boards-list-key [org-slug]
  (vec (conj (follow-list-key org-slug) :follow-boards-list)))

(defn unfollow-board-uuids-key [org-slug]
  (vec (conj (follow-list-key org-slug) :unfollow-board-uuids)))

(defn followers-count-key [org-slug]
  (vec (conj (org-key org-slug) :followers-count)))

(defn followers-publishers-count-key [org-slug]
  (vec (conj (followers-count-key org-slug) :publishers)))

(defn followers-boards-count-key [org-slug]
  (vec (conj (followers-count-key org-slug) :boards)))

(defn mention-users-key [org-slug]
  (vec (conj (org-key org-slug) :mention-users)))

(defn users-info-hover-key [org-slug]
  (vec (conj (org-key org-slug) :users-info-hover)))

(defn uploading-video-key [org-slug video-id]
  (vec (concat (org-key org-slug) [:uploading-videos video-id])))

(defn current-board-key
  "Find the board key for db based on the current path."
  []
  (let [org-slug (current-org-slug)
        board-slug (current-board-slug)]
     (board-data-key org-slug board-slug)))

(def can-compose-key :can-copmose?)

(defn org-can-compose-key
  "Key for a boolean value: true if the user has at least one board
   he can publish updates in."
  [org-slug]
  (vec (conj (org-data-key org-slug) can-compose-key)))

;; User notifications

(defn user-notifications-key [org-slug]
  (vec (conj (org-key org-slug) :user-notifications)))

;; Reminders

(defn reminders-key [org-slug]
  (vec (conj (org-key org-slug) :reminders)))

(defn reminders-data-key [org-slug]
  (vec (conj (reminders-key org-slug) :reminders-list)))

(defn reminders-roster-key [org-slug]
  (vec (conj (reminders-key org-slug) :reminders-roster)))

(defn reminder-edit-key [org-slug]
  (vec (conj (reminders-key org-slug) :reminder-edit)))

;; Change related keys

(defn change-data-key [org-slug]
  (vec (conj (org-key org-slug) :change-data)))

(def activities-read-key
  [:activities-read])

;; Seen

(defn org-seens-key [org-slug]
  (vec (conj (org-key org-slug) :container-seen)))

; (defn container-seen-key [org-slug container-id]
;   (vec (conj (org-seens-key org-slug) (keyword container-id))))

;; Cmail keys

(def cmail-state-key [:cmail-state])

(def cmail-data-key [:cmail-data])

;; Payments keys

(def payments-checkout-session-result :checkout-session-result)

;; Payments UI banner keys

(def payments-ui-upgraded-banner-key :payments-ui-upgraded-banner)

;; Boards helpers

(defn get-posts-for-board [posts-data board-slug]
  (let [filter-fn (if (= board-slug du/default-drafts-board-slug)
                    #(not= (:status %) "published")
                    #(and (= (:board-slug %) board-slug)
                          (= (:status %) "published")))]
    (filter (comp filter-fn last) posts-data)))

;; Container helpers

(defn is-container? [container-slug]
  ;; Rest of containers
  (#{:inbox :all-posts :bookmarks :following :unfollowing :activity :replies} (keyword container-slug)))

(defn is-container-with-sort? [container-slug]
  ;; Rest of containers
  (#{"all-posts" "following" "unfollowing"} container-slug))

(defn is-recent-activity? [container-slug]
  (when-let [container-slug-kw (keyword container-slug)]
    (#{:replies} container-slug-kw)))

(defn- get-container-posts [base posts-data org-slug container-slug sort-type items-key]
  (let [cnt-key (cond
                  (is-container? container-slug)
                  (container-key org-slug container-slug sort-type)
                  (seq (current-contributions-id))
                  (contributions-data-key org-slug container-slug)
                  :else
                  (board-data-key org-slug container-slug))
        container-data (get-in base cnt-key)
        posts-list (get container-data items-key)
        container-posts (map (fn [entry]
                               (if (and (map? entry)
                                        (= (:resource-type entry) :entry)
                                        (map? (get posts-data (:uuid entry))))
                                 ;; Make sure the local map is merged as last value
                                 ;; since the kept value relates directly to the container
                                 (merge (get posts-data (:uuid entry)) entry)
                                 entry))
                         posts-list)
        items (if (= container-slug du/default-drafts-board-slug)
                (filter (comp not :published?) container-posts)
                container-posts)]
    (vec items)))

(def theme-key [:theme])
(def theme-setting-key :setting-value)
(def theme-mobile-key :mobile-value)
(def theme-desktop-key :desktop-value)
(def theme-web-key :web-value)

;; Functions needed by derivatives

(declare org-data)
(declare board-data)
(declare contributions-data)
(declare editable-boards-data)
(declare private-boards-data)
(declare public-boards-data)
(declare activity-data)
(declare secure-activity-data)
(declare activity-read-data)
(declare activity-data-get)

;; Action Loop =================================================================

(defmulti action (fn [_db [action-type & _]]
                   (when (and (not= action-type :input)
                              (not= action-type :update)
                              (not= action-type :entry-toggle-save-on-exit)
                              (not= action-type :cmail-state/update)
                              (not= action-type :cmail-data/update))
                     (timbre/info "Dispatching action:" action-type))
                   action-type))

(def actions (flux/dispatcher))

(def actions-dispatch
  (flux/register
   actions
   (fn [payload]
     ;; (prn payload) ; debug :)
     (let [next-db (swap! app-state action payload)]
       (when (get next-db nil)
         (timbre/warn "Nil key in app-state! Content:" (get next-db nil)))
       next-db))))

(defn dispatch! [payload]
  (flux/dispatch actions payload))

;; Path components retrieve

(defn ^:export route
  ([] (route @app-state))
  ([data] (get-in data [router-key])))

(defn ^:export current-org-slug
  ([] (current-org-slug @app-state))
  ([data] (get-in data [router-key :org])))

(defn ^:export current-board-slug
  ([] (current-board-slug @app-state))
  ([data] (get-in data [router-key :board])))

(defn ^:export current-contributions-id
  ([] (current-contributions-id @app-state))
  ([data] (get-in data [router-key :contributions])))

(defn ^:export current-container-slug
  ([] (current-container-slug @app-state))
  ([data] (when-not (current-activity-id data)
            (or (current-contributions-id data)
                (current-board-slug data)))))

(defn ^:export current-sort-type
  ([] (current-sort-type @app-state))
  ([data] (get-in data [router-key :sort-type])))

(defn ^:export current-activity-id
  ([] (current-activity-id @app-state))
  ([data] (get-in data [router-key :activity])))

(defn ^:export current-entry-board-slug
  ([] (current-entry-board-slug @app-state))
  ([data] (get-in data [router-key :entry-board])))

(defn ^:export current-secure-activity-id
  ([] (current-secure-activity-id @app-state))
  ([data] (get-in data [router-key :secure-id])))

(defn ^:export current-comment-id
  ([] (current-comment-id @app-state))
  ([data] (get-in data [router-key :comment])))

(defn ^:export query-params
  ([] (query-params @app-state))
  ([data] (get-in data [router-key :query-params])))

(defn ^:export query-param
  ([k] (query-param @app-state k))
  ([data k] (get-in data [router-key :query-params k])))

(defun ^:export route-param
  ([k] (route-param @app-state k))
  ([data k :guard s-or-k?] (route-param data [k]))
  ([data ks :guard coll?] (get-in data (concat [router-key] ks))))

(defn ^:export route-set
  ([] (route-set @app-state))
  ([data] (route-param data :route)))

(defn ^:export invite-token
  ([] (invite-token @app-state))
  ([data] (query-param data :invite-token)))

(defn in-route?
  ([route-name] (in-route? (route-set @app-state) route-name))
  ([routes route-name]
  (when route-name
    (routes (keyword route-name)))))

;; Theme

(defn ^:export theme-map
  ([] (theme-map @app-state))
  ([data] (get-in data theme-key)))

;; Payments

(defn payments-data
  ([]
    (payments-data @app-state (current-org-slug)))
  ([org-slug]
   (payments-data @app-state org-slug))
  ([data org-slug]
   (get-in data (payments-key org-slug))))

;; Payments cached data

(defn payments-notify-cache-data
  ([]
    (payments-notify-cache-data @app-state (current-org-slug)))
  ([org-slug]
   (payments-notify-cache-data @app-state org-slug))
  ([data org-slug]
   (get-in data (payments-notify-cache-key org-slug))))

;; Data

(defn bot-access
  ""
  ([] (bot-access @app-state))
  ([data]
    (:bot-access data)))

(defn notifications-data
  ""
  ([] (notifications-data @app-state))
  ([data]
    (get-in data notifications-key)))

(defn teams-data-requested
  ""
  ([] (teams-data-requested @app-state))
  ([data] (:teams-data-requested data)))

(defn auth-settings
  "Get the Auth settings data"
  ([] (auth-settings @app-state))
  ([data] (get-in data auth-settings-key)))

(defn api-entry-point
  "Get the API entry point."
  ([] (api-entry-point @app-state))
  ([data] (get-in data api-entry-point-key)))

(defn current-user-data
  "Get the current logged in user info."
  ([] (current-user-data @app-state))
  ([data] (get-in data current-user-key)))

(defn ^:export orgs-data
  ([] (orgs-data @app-state))
  ([data] (get data orgs-key)))

(defn ^:export early-org-data
  ([] (early-org-data @app-state (current-org-slug)))
  ([data] (early-org-data data (current-org-slug)))
  ([data org-slug]
   (some #(when (= (:slug %) org-slug) %) (orgs-data data))))

(defn ^:export org-data
  "Get org data."
  ([]
    (org-data @app-state (current-org-slug)))
  ([data]
    (org-data data (current-org-slug)))
  ([data org-slug]
    (get-in data (org-data-key org-slug))))

(defn ^:export posts-data
  "Get org all posts data."
  ([]
    (posts-data @app-state))
  ([data]
    (posts-data data (current-org-slug data)))
  ([data org-slug]
    (get-in data (posts-data-key org-slug))))

(defun org-board-data
  "Get board data from org data map: mostly used to edit the board infos."
  ([nil] nil)
  ([nil _] nil)
  ([_ nil] nil)
  ([]
   (org-board-data (org-data) (current-board-slug)))
  ([board-slug :guard s-or-k?]
   (org-board-data (org-data) board-slug))
  ([org-slug :guard #(or (keyword? %) (string? %)) board-slug :guard #(or (keyword? %) (string? %))]
   (org-board-data (org-data @app-state org-slug) board-slug))
  ([org-data :guard #(and (map? %) (:links %) (:boards %))
    board-slug]
   (when board-slug
    (let [board-slug-kw (keyword board-slug)]
      (some #(when (-> % :slug keyword (= board-slug-kw)) %) (:boards org-data)))))
  ([data :guard map? org-slug]
   (org-board-data (org-data data org-slug) (current-board-slug data)))
  ([data :guard map? org-slug board-slug]
   (org-board-data (org-data data org-slug) board-slug)))

(defun board-data
  "Get board data."
  ([]
    (board-data @app-state))
  ([data :guard map?]
    (board-data data (current-org-slug data) (current-board-slug data)))
  ([board-slug :guard #(or (keyword? %) (string? %))]
    (board-data @app-state (current-org-slug) board-slug))
  ([org-slug :guard #(or (keyword? %) (string? %)) board-slug :guard #(or (keyword? %) (string? %))]
    (board-data @app-state org-slug board-slug))
  ([data :guard map? org-slug :guard #(or (keyword? %) (string? %))]
    (board-data @app-state org-slug (current-board-slug data)))
  ([data org-slug board-slug]
    (when (and org-slug board-slug)
      (get-in data (board-data-key org-slug board-slug)))))

(defun ^:export contributions-data
  "Get contributions data"
  ([]
    (contributions-data @app-state))
  ([data :guard map?]
    (contributions-data data (current-org-slug data) (current-contributions-id data)))
  ([contributions-id :guard #(or (keyword? %) (string? %))]
    (contributions-data @app-state (current-org-slug) contributions-id))
  ([org-slug :guard #(or (keyword? %) (string? %)) contributions-id :guard #(or (keyword? %) (string? %))]
    (contributions-data @app-state org-slug contributions-id))
  ([data :guard map? org-slug :guard #(or (keyword? %) (string? %))]
    (contributions-data @app-state org-slug (current-contributions-id data)))
  ([data org-slug contributions-id]
    (when (and org-slug contributions-id)
      (get-in data (contributions-data-key org-slug contributions-id)))))

(defn ^:export editable-boards-data
  ([] (editable-boards-data @app-state (current-org-slug)))
  ([org-slug] (editable-boards-data @app-state org-slug))
  ([data org-slug]
   (get-in data (editable-boards-key org-slug))))

(defn ^:export private-boards-data
  ([] (private-boards-data @app-state (current-org-slug)))
  ([org-slug] (private-boards-data @app-state org-slug))
  ([data org-slug]
   (get-in data (private-boards-key org-slug))))

(defn ^:export public-boards-data
  ([] (public-boards-data @app-state (current-org-slug)))
  ([org-slug] (public-boards-data @app-state org-slug))
  ([data org-slug]
   (get-in data (public-boards-key org-slug))))

(defn ^:export container-data
  "Get container data."
  ([]
    (container-data @app-state (current-org-slug) (current-board-slug) (current-sort-type)))
  ([data]
    (container-data data (current-org-slug data) (current-board-slug data) (current-sort-type data)))
  ([data org-slug]
    (container-data data org-slug (current-board-slug data) (current-sort-type data)))
  ([data org-slug board-slug]
    (container-data data org-slug board-slug (current-sort-type data)))
  ([data org-slug board-slug sort-type]
    (get-in data (container-key org-slug board-slug sort-type))))

(defn ^:explore current-container-data []
  (let [board-slug (current-board-slug)
        contributions-id (current-contributions-id)]
    (cond
      (seq contributions-id)
      (contributions-data @app-state (current-org-slug) contributions-id)
      (is-container? board-slug)
      (container-data @app-state (current-org-slug) board-slug)
      (= (keyword board-slug) :topic)
      nil
      :else
      (board-data @app-state (current-org-slug) board-slug))))

(defn ^:export all-posts-data
  "Get all-posts container data."
  ([]
    (all-posts-data (current-org-slug) recently-posted-sort @app-state))
  ([org-slug]
    (all-posts-data org-slug recently-posted-sort @app-state))
  ([org-slug data]
    (all-posts-data org-slug recently-posted-sort data))
  ([org-slug sort-type data]
    (container-data data org-slug :all-posts sort-type)))

(defn ^:export replies-data
  "Get replies container data."
  ([]
    (replies-data (current-org-slug) @app-state))
  ([org-slug]
    (replies-data org-slug @app-state))
  ([org-slug data]
    (container-data data org-slug :replies recent-activity-sort)))

(defn ^:export following-data
  "Get following container data."
  ([]
    (following-data (current-org-slug) @app-state))
  ([org-slug]
    (following-data org-slug @app-state))
  ([org-slug data]
    (container-data data org-slug :following recently-posted-sort)))

(defn ^:export unfollowing-data
  "Get following container data."
  ([]
    (unfollowing-data (current-org-slug) @app-state))
  ([org-slug]
    (unfollowing-data org-slug @app-state))
  ([org-slug data]
    (container-data data org-slug :unfollowing recently-posted-sort)))

(defn ^:export bookmarks-data
  "Get following container data."
  ([]
    (bookmarks-data (current-org-slug) @app-state))
  ([org-slug]
    (bookmarks-data org-slug @app-state))
  ([org-slug data]
    (container-data data org-slug :bookmarks recently-posted-sort)))

(defn ^:export filtered-posts-data
  ([]
    (filtered-posts-data @app-state (current-org-slug) (current-board-slug) (current-sort-type)))
  ([data]
    (filtered-posts-data data (current-org-slug data) (current-board-slug data) (current-sort-type data)))
  ([data org-slug]
    (filtered-posts-data data org-slug (current-board-slug data) (current-sort-type data)))
  ([data org-slug board-slug]
    (filtered-posts-data data org-slug board-slug (current-sort-type data)))
  ([data org-slug board-slug sort-type]
    (let [posts-data (get-in data (posts-data-key org-slug))]
     (get-container-posts data posts-data org-slug board-slug sort-type :posts-list)))
  ; ([data org-slug board-slug activity-id]
  ;   (let [org-data (org-data data org-slug)
  ;         all-boards-slug (map :slug (:boards org-data))
  ;         is-board? ((set all-boards-slug) board-slug)
  ;         posts-data (get-in data (posts-data-key org-slug))]
  ;    (if is-board?
  ;      (get-posts-for-board activity-id posts-data board-slug)
  ;      (let [container-key (container-key org-slug board-slug)
  ;            items-list (:posts-list (get-in data container-key))]
  ;       (zipmap items-list (map #(get posts-data %) items-list))))))
  )

(defn ^:export items-to-render-data
  ([]
    (items-to-render-data @app-state))
  ([data]
    (items-to-render-data data (current-org-slug data) (current-board-slug data) (current-sort-type data)))
  ([data org-slug]
    (items-to-render-data data org-slug (current-board-slug data) (current-sort-type data)))
  ([data org-slug board-slug]
    (items-to-render-data data org-slug board-slug (current-sort-type data)))
  ([data org-slug board-slug sort-type]
    (let [posts-data (get-in data (posts-data-key org-slug))]
     (get-container-posts data posts-data org-slug board-slug sort-type :items-to-render)))
  ; ([data org-slug board-slug activity-id]
  ;   (let [org-data (org-data data org-slug)
  ;         all-boards-slug (map :slug (:boards org-data))
  ;         is-board? ((set all-boards-slug) board-slug)
  ;         posts-data (get-in data (posts-data-key org-slug))]
  ;    (if is-board?
  ;      (get-posts-for-board activity-id posts-data board-slug)
  ;      (let [container-key (container-key org-slug board-slug)
  ;            items-list (:posts-list (get-in data container-key))]
  ;       (zipmap items-list (map #(get posts-data %) items-list))))))
  )

(defn ^:export draft-posts-data
  ([]
    (draft-posts-data @app-state (current-org-slug)))
  ([org-slug]
    (draft-posts-data @app-state org-slug))
  ([data org-slug]
    (filtered-posts-data data org-slug du/default-drafts-board-slug)))

(defn ^:export activity-data
  "Get activity data."
  ([]
    (activity-data (current-org-slug) (current-activity-id) @app-state))
  ([activity-id]
    (activity-data (current-org-slug) activity-id @app-state))
  ([org-slug activity-id]
    (activity-data org-slug activity-id @app-state))
  ([org-slug activity-id data]
    (let [activity-key (activity-key org-slug activity-id)]
      (get-in data activity-key))))
(def activity-data-get activity-data)
(def entry-data activity-data)

(defn ^:export pins-data
  "Get entry pins data."
  ([]
   (pins-data (current-org-slug) (current-activity-id) @app-state))
  ([entry-uuid]
   (pins-data (current-org-slug) entry-uuid @app-state))
  ([org-slug entry-uuid]
   (pins-data org-slug entry-uuid @app-state))
  ([org-slug entry-uuid data]
   (let [entry-pins-key (pins-key org-slug entry-uuid)]
     (get-in data entry-pins-key))))

(defn ^:export pin-data
  "Get entry pin data."
  ([pin-container-uuid]
   (pin-data (current-org-slug) (current-activity-id) pin-container-uuid @app-state))
  ([entry-uuid pin-container-uuid]
   (pin-data (current-org-slug) entry-uuid pin-container-uuid @app-state))
  ([org-slug entry-uuid pin-container-uuid]
   (pin-data org-slug entry-uuid pin-container-uuid @app-state))
  ([org-slug entry-uuid pin-container-uuid data]
   (let [entry-pin-key (pin-key org-slug entry-uuid pin-container-uuid)]
     (get-in data entry-pin-key))))

(defn ^:export secure-activity-data
  "Get secure activity data."
  ([]
    (secure-activity-data (current-org-slug) (current-secure-activity-id) @app-state))
  ([secure-id]
    (secure-activity-data (current-org-slug) secure-id @app-state))
  ([org-slug secure-id]
    (secure-activity-data org-slug secure-id @app-state))
  ([org-slug secure-id data]
    (let [activity-key (secure-activity-key org-slug secure-id)]
      (get-in data activity-key))))

(defn ^:export comments-data
  ([]
    (comments-data (current-org-slug) @app-state))
  ([org-slug]
    (comments-data org-slug @app-state))
  ([org-slug data]
    (get-in data (comments-key org-slug))))

(defn ^:export comment-data
  ([comment-uuid]
    (comment-data (current-org-slug) comment-uuid @app-state))
  ([org-slug comment-uuid]
    (comment-data org-slug comment-uuid @app-state))
  ([org-slug comment-uuid data]
    (let [all-entry-comments (get-in data (comments-key org-slug))
          all-comments (flatten (map :sorted-comments (vals all-entry-comments)))]
      (some #(when (= (:uuid %) comment-uuid) %) all-comments))))

(defn ^:export activity-comments-data
  ([]
    (activity-comments-data
     (current-org-slug)
     (current-activity-id)
     @app-state))
  ([activity-uuid]
    (activity-comments-data
     (current-org-slug)
     activity-uuid @app-state))
  ([org-slug activity-uuid]
    (activity-comments-data org-slug activity-uuid @app-state))
  ([org-slug activity-uuid data]
    (get-in data (activity-comments-key org-slug activity-uuid))))

(defn ^:export activity-sorted-comments-data
  ([]
    (activity-sorted-comments-data
     (current-org-slug)
     (current-activity-id)
     @app-state))
  ([activity-uuid]
    (activity-sorted-comments-data
     (current-org-slug)
     activity-uuid @app-state))
  ([org-slug activity-uuid]
    (activity-sorted-comments-data org-slug activity-uuid @app-state))
  ([org-slug activity-uuid data]
    (get-in data (activity-sorted-comments-key org-slug activity-uuid))))

(defn ^:export teams-data
  ([] (teams-data @app-state))
  ([data] (get-in data teams-data-key))
  ([data team-id] (some #(when (= (:team-id %) team-id) %) (get-in data teams-data-key))))

(defn team-data
  ([] (team-data (:team-id (org-data))))
  ([team-id] (team-data team-id @app-state))
  ([team-id data] (get-in data (team-data-key team-id))))

(defn team-roster
  ([] (team-roster (:team-id (org-data))))
  ([team-id] (team-roster team-id @app-state))
  ([team-id data] (get-in data (team-roster-key team-id))))

(defn team-channels
  ([] (team-channels (:team-id (org-data))))
  ([team-id] (team-channels team-id @app-state))
  ([team-id data] (get-in data (team-channels-key team-id))))

(defn ^:export active-users
  ([] (active-users (:slug (org-data)) @app-state))
  ([org-slug] (active-users org-slug @app-state))
  ([org-slug data] (get-in data (active-users-key org-slug))))

(defn ^:export follow-list
  ([] (follow-list (:slug (org-data)) @app-state))
  ([org-slug] (follow-list org-slug @app-state))
  ([org-slug data] (get-in data (follow-list-key org-slug))))

(defn ^:export followers-count
  ([] (followers-count (:slug (org-data)) @app-state))
  ([org-slug] (followers-count org-slug @app-state))
  ([org-slug data] (get-in data (followers-count-key org-slug))))

(defn ^:export followers-publishers-count
  ([] (followers-publishers-count (:slug (org-data)) @app-state))
  ([org-slug] (followers-publishers-count org-slug @app-state))
  ([org-slug data] (get-in data (followers-publishers-count-key org-slug))))

(defn ^:export followers-boards-count
  ([] (followers-boards-count (:slug (org-data)) @app-state))
  ([org-slug] (followers-boards-count org-slug @app-state))
  ([org-slug data] (get-in data (followers-boards-count-key org-slug))))

(defn ^:export follow-publishers-list
  ([] (follow-publishers-list (:slug (org-data)) @app-state))
  ([org-slug] (follow-publishers-list org-slug @app-state))
  ([org-slug data] (get-in data (follow-publishers-list-key org-slug))))

(defn ^:export follow-boards-list
  ([] (follow-boards-list (:slug (org-data)) @app-state))
  ([org-slug] (follow-boards-list org-slug @app-state))
  ([org-slug data] (get-in data (follow-boards-list-key org-slug))))

(defn ^:export unfollow-board-uuids
  ([] (unfollow-board-uuids (:slug (org-data)) @app-state))
  ([org-slug] (unfollow-board-uuids org-slug @app-state))
  ([org-slug data] (get-in data (unfollow-board-uuids-key org-slug))))

(defn uploading-video-data
  ([video-id] (uploading-video-data (current-org-slug) video-id @app-state))
  ([org-slug video-id] (uploading-video-data org-slug video-id @app-state))
  ([org-slug video-id data]
    (let [uv-key (uploading-video-key org-slug video-id)]
      (get-in data uv-key))))

;; User notifications

(defn user-notifications-data
  "Get user notifications data"
  ([]
    (user-notifications-data (current-org-slug) @app-state))
  ([org-slug]
    (user-notifications-data org-slug @app-state))
  ([org-slug data]
    (get-in data (user-notifications-key org-slug))))

;; Change related

(defn change-data
  "Get change data."
  ([]
    (change-data @app-state))
  ([data]
    (change-data data (current-org-slug)))
  ([data org-slug]
    (get-in data (change-data-key org-slug))))

(defun activity-read-data
  "Get the read counts of all the items."
  ([]
    (activity-read-data @app-state))
  ([data :guard map?]
    (get-in data activities-read-key))
  ([item-ids :guard seq?]
    (activity-read-data item-ids @app-state))
  ([item-ids :guard seq? data :guard map?]
    (let [all-activities-read (get-in data activities-read-key)]
      (select-keys all-activities-read item-ids)))
  ([item-id :guard string?]
    (activity-read-data item-id @app-state))
  ([item-id :guard string? data :guard map?]
    (let [all-activities-read (get-in data activities-read-key)]
      (get all-activities-read item-id))))

;; Seen

(defn org-seens-data
  ([] (org-seens-data @app-state (current-org-slug)))
  ([org-slug] (org-seens-data @app-state org-slug))
  ([data org-slug] (get-in data (org-seens-key org-slug))))

; (defn container-seen-data
;  ([container-id] (container-seen-data @app-state (current-org-slug) container-id))
;  ([org-slug container-id] (container-seen-data @app-state org-slug container-id))
;  ([data org-slug container-id] (get-in data (container-seen-key org-slug container-id))))

;; Cmail

(defn ^:export cmail-data
  ([] (cmail-data @app-state))
  ([data] (get-in data cmail-data-key)))

(defn ^:export cmail-state
  ([] (cmail-state @app-state))
  ([data] (get-in data cmail-state-key)))

;; Reminders

(defn reminders-data
  ([] (reminders-data (current-org-slug) @app-state))
  ([org-slug] (reminders-data org-slug @app-state))
  ([org-slug data]
    (get-in data (reminders-data-key org-slug))))

(defn reminders-roster-data
  ([] (reminders-roster-data (current-org-slug) @app-state))
  ([org-slug] (reminders-roster-data org-slug @app-state))
  ([org-slug data]
    (get-in data (reminders-roster-key org-slug))))

(defn reminder-edit-data
  ([] (reminder-edit-data (current-org-slug) @app-state))
  ([org-slug] (reminder-edit-data org-slug @app-state))
  ([org-slug data]
    (get-in data (reminder-edit-key org-slug))))

;; Expo

(defn expo-deep-link-origin
  ([] (expo-deep-link-origin @app-state))
  ([data] (get-in data expo-deep-link-origin-key)))

(defn expo-app-version
  ([] (expo-app-version @app-state))
  ([data] (get-in data expo-app-version-key)))

(defn expo-push-token
  ([] (expo-push-token @app-state))
  ([data] (get-in data expo-push-token-key)))

;; Debug functions

(defn print-app-state []
  @app-state)

(defn print-org-data []
  (get-in @app-state (org-data-key (current-org-slug))))

(defn print-team-data []
  (get-in @app-state (team-data-key (:team-id (org-data)))))

(defn print-team-roster []
  (get-in @app-state (team-roster-key (:team-id (org-data)))))

(defn print-change-data []
  (get-in @app-state (change-data-key (current-org-slug))))

(defn print-activity-read-data []
  (get-in @app-state activities-read-key))

(defn print-board-data []
  (get-in @app-state (board-data-key (current-org-slug) (current-board-slug))))

(defn print-container-data []
  (if (is-container? (current-board-slug))
    (get-in @app-state (container-key (current-org-slug) (current-board-slug) (current-sort-type)))
    (get-in @app-state (board-data-key (current-org-slug) (current-board-slug)))))

(defn print-activity-data []
  (get-in
   @app-state
   (activity-key (current-org-slug) (current-activity-id))))

(defn print-secure-activity-data []
  (get-in
   @app-state
   (secure-activity-key (current-org-slug) (current-secure-activity-id))))

(defn print-reactions-data []
  (get-in
   @app-state
   (conj
    (activity-key (current-org-slug) (current-activity-id))
    :reactions)))

(defn print-comments-data []
  (get-in
   @app-state
   (comments-key (current-org-slug))))

(defn print-activity-comments-data []
  (get-in
   @app-state
   (activity-comments-key (current-org-slug) (current-activity-id))))

(defn print-entry-editing-data []
  (get @app-state :entry-editing))

(defn print-posts-data []
  (get-in @app-state (posts-data-key (current-org-slug))))

(defn print-filtered-posts []
  (filtered-posts-data @app-state (current-org-slug) (current-board-slug)))

(defn print-items-to-render []
  (items-to-render-data @app-state (current-org-slug) (current-board-slug)))

(defn print-user-notifications []
  (user-notifications-data (current-org-slug) @app-state))

(defn print-reminders-data []
  (reminders-data (current-org-slug) @app-state))

(defn print-reminder-edit-data []
  (reminder-edit-data (current-org-slug) @app-state))

(defn print-panel-stack []
  (:panel-stack @app-state))

(defn print-payments-data []
  (payments-data @app-state (current-org-slug)))

(defn print-router-path []
  (route @app-state))

(set! (.-OCWebPrintAppState js/window) print-app-state)
(set! (.-OCWebPrintOrgData js/window) print-org-data)
(set! (.-OCWebPrintTeamData js/window) print-team-data)
(set! (.-OCWebPrintTeamRoster js/window) print-team-roster)
(set! (.-OCWebPrintActiveUsers js/window) active-users)
(set! (.-OCWebPrintChangeData js/window) print-change-data)
(set! (.-OCWebPrintActivityReadData js/window) print-activity-read-data)
(set! (.-OCWebPrintBoardData js/window) print-board-data)
(set! (.-OCWebPrintContainerData js/window) print-container-data)
(set! (.-OCWebPrintActivityData js/window) print-activity-data)
(set! (.-OCWebPrintSecureActivityData js/window) print-secure-activity-data)
(set! (.-OCWebPrintReactionsData js/window) print-reactions-data)
(set! (.-OCWebPrintCommentsData js/window) print-comments-data)
(set! (.-OCWebPrintActivityCommentsData js/window) print-activity-comments-data)
(set! (.-OCWebPrintEntryEditingData js/window) print-entry-editing-data)
(set! (.-OCWebPrintFilteredPostsData js/window) print-filtered-posts)
(set! (.-OCWebPrintItemsToRender js/window) print-items-to-render)
(set! (.-OCWebPrintPostsData js/window) print-posts-data)
(set! (.-OCWebPrintUserNotifications js/window) print-user-notifications)
(set! (.-OCWebPrintRemindersData js/window) print-reminders-data)
(set! (.-OCWebPrintReminderEditData js/window) print-reminder-edit-data)
(set! (.-OCWebPrintPanelStack js/window) print-panel-stack)
(set! (.-OCWebPrintPaymentsData js/window) print-payments-data)
(set! (.-OCWebPrintRouterPath js/window) print-router-path)
;; Utility externs
(set! (.-OCWebUtils js/window) #js {:deref deref
                                    :keyword keyword
                                    :count count
                                    :get get
                                    :filter filter
                                    :map map
                                    :clj__GT_js clj->js
                                    :js__GT_clj js->clj})
