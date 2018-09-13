(ns oc.web.dispatcher
  (:require [cljs-flux.dispatcher :as flux]
            [org.martinklepsch.derivatives :as drv]
            [taoensso.timbre :as timbre]
            [oc.web.router :as router]
            [oc.web.lib.utils :as utils]))

(defonce app-state (atom {:loading false
                          :show-login-overlay false}))

;; Data key paths

(def api-entry-point-key [:api-entry-point])

(def auth-settings-key [:auth-settings])

(def notifications-key [:notifications-data])

(def orgs-key :orgs)

(defn org-key [org-slug]
  [(keyword org-slug)])

(defn org-data-key [org-slug]
  (vec (conj (org-key org-slug) :org-data)))

(defn boards-key [org-slug]
  (vec (conj (org-key org-slug) :boards)))

(defn posts-data-key [org-slug]
  (vec (conj (org-key org-slug) :posts)))

(defn board-key [org-slug board-slug]
  (vec (conj (boards-key org-slug) (keyword board-slug))))

(defn board-data-key [org-slug board-slug]
  (conj (board-key org-slug board-slug) :board-data))

(defn containers-key [org-slug]
  (vec (conj (org-key org-slug) :container-data)))

(defn container-key [org-slug posts-filter]
  (vec (conj (containers-key org-slug) (keyword posts-filter))))

(defn secure-activity-key [org-slug secure-id]
  (vec (concat (org-key org-slug) [:secure-activities secure-id])))

(defn activity-key [org-slug activity-uuid]
  (let [posts-key (posts-data-key org-slug)]
    (vec (concat posts-key [activity-uuid]))))

(defn comments-key [org-slug]
  (vec (conj (org-key org-slug) :comments)))

(defn activity-comments-key [org-slug activity-uuid]
  (vec (conj (comments-key org-slug) activity-uuid :sorted-comments)))

(def teams-data-key [:teams-data :teams])

(defn team-data-key [team-id]
  [:teams-data team-id :data])

(defn team-roster-key [team-id]
  [:teams-data team-id :roster])

(defn team-channels-key [team-id]
  [:teams-data team-id :channels])

(defn uploading-video-key [org-slug video-id]
  (vec (concat (org-key org-slug) [:uploading-videos video-id])))

(defn current-board-key
  "Find the board key for db based on the current path."
  []
  (let [org-slug (router/current-org-slug)
        board-slug (router/current-board-slug)]
     (board-data-key org-slug board-slug)))

;; Change related keys

(defn change-data-key [org-slug]
  (vec (conj (org-key org-slug) :change-data)))

(defn change-cache-data-key [org-slug]
  (vec (conj (org-key org-slug) :change-cache-data)))

(def activities-read-key
  [:activities-read])

(defn get-posts-for-board [posts-data board-slug]
  (let [posts-list (vals posts-data)
        filter-fn (if (= board-slug utils/default-drafts-board-slug)
                    #(not= (:status %) "published")
                    #(and (= (:board-slug %) board-slug)
                                                 (= (:status %) "published")))
        board-posts (map :uuid (filter filter-fn posts-list))]
    (select-keys posts-data board-posts)))

;; Derived Data ================================================================

(defn drv-spec [db route-db]
  {:base                [[] db]
   :route               [[] route-db]
   :orgs                [[:base] (fn [base] (get base orgs-key))]
   :org-slug            [[:route] (fn [route] (:org route))]
   :board-slug          [[:route] (fn [route] (:board route))]
   :activity-uuid       [[:route] (fn [route] (:activity route))]
   :secure-id           [[:route] (fn [route] (:secure-id route))]
   :loading             [[:base] (fn [base] (:loading base))]
   :signup-with-email   [[:base] (fn [base] (:signup-with-email base))]
   :query-params        [[:route] (fn [route] (:query-params route))]
   :teams-data          [[:base] (fn [base] (get-in base teams-data-key))]
   :auth-settings       [[:base] (fn [base] (get-in base auth-settings-key))]
   :org-settings        [[:base] (fn [base] (:org-settings base))]
   :user-settings        [[:base] (fn [base] (:user-settings base))]
   :entry-save-on-exit  [[:base] (fn [base] (:entry-save-on-exit base))]
   :mobile-navigation-sidebar [[:base] (fn [base] (:mobile-navigation-sidebar base))]
   :orgs-dropdown-visible [[:base] (fn [base] (:orgs-dropdown-visible base))]
   :ap-initial-at       [[:base] (fn [base] (:ap-initial-at base))]
   :add-comment-focus   [[:base] (fn [base] (:add-comment-focus base))]
   :comment-add-finish  [[:base] (fn [base] (:comment-add-finish base))]
   :nux                 [[:base] (fn [base] (:nux base))]
   :notifications-data  [[:base] (fn [base] (get-in base notifications-key))]
   :login-with-email-error [[:base] (fn [base] (:login-with-email-error base))]
   :email-verification  [[:base :auth-settings]
                          (fn [base auth-settings]
                            {:auth-settings auth-settings
                             :error (:email-verification-error base)
                             :success (:email-verification-success base)})]
   :jwt                 [[:base] (fn [base] (:jwt base))]
   :current-user-data   [[:base] (fn [base] (:current-user-data base))]
   :subscription        [[:base] (fn [base] (:subscription base))]
   :show-login-overlay  [[:base] (fn [base] (:show-login-overlay base))]
   :made-with-carrot-modal [[:base] (fn [base] (:made-with-carrot-modal base))]
   :site-menu-open      [[:base] (fn [base] (:site-menu-open base))]
   :mobile-menu-open    [[:base] (fn [base] (:mobile-menu-open base))]
   :sections-setup      [[:base] (fn [base] (:sections-setup base))]
   :org-data            [[:base :org-slug]
                          (fn [base org-slug]
                            (when org-slug
                              (get-in base (org-data-key org-slug))))]
   :team-data           [[:base :org-data]
                          (fn [base org-data]
                            (when org-data
                              (get-in base (team-data-key (:team-id org-data)))))]
   :team-roster         [[:base :org-data]
                          (fn [base org-data]
                            (when org-data
                              (get-in base (team-roster-key (:team-id org-data)))))]
   :invite-users        [[:base] (fn [base] (:invite-users base))]
   :invite-data         [[:base :team-data :current-user-data :team-roster :invite-users]
                          (fn [base team-data current-user-data team-roster invite-users]
                            {:team-data team-data
                             :invite-users invite-users
                             :current-user-data current-user-data
                             :team-roster team-roster})]
   :org-settings-team-management
                        [[:base :query-params :org-data :team-data :auth-settings]
                          (fn [base query-params org-data team-data]
                            {:um-domain-invite (:um-domain-invite base)
                             :add-email-domain-team-error (:add-email-domain-team-error base)
                             :team-data team-data
                             :query-params query-params})]
   :container-data      [[:base :org-slug :board-slug]
                         (fn [base org-slug board-slug]
                           (timbre/debug (container-key org-slug board-slug))
                           (when (and org-slug board-slug)
                             (get-in base (container-key org-slug board-slug))))]
   :posts-data          [[:base :org-slug]
                         (fn [base org-slug]
                           (when (and base org-slug)
                             (get-in base (posts-data-key org-slug))))]

   :filtered-posts      [[:base :org-data :posts-data :route]
                         (fn [base org-data posts-data route]
                           (when (and base org-data posts-data route)
                             (let [org-slug (:slug org-data)
                                   all-boards-slug (map :slug (:boards org-data))
                                   container-slug (:board route)
                                   is-board? ((set all-boards-slug) container-slug)]
                              (if is-board?
                                (get-posts-for-board posts-data container-slug)
                                (let [container-key (container-key org-slug container-slug)
                                      items-list (:posts-list (get-in base container-key))]
                                  (zipmap items-list (map #(get posts-data %) items-list)))))))]
   :team-channels       [[:base :org-data]
                          (fn [base org-data]
                            (when org-data
                              (get-in base (team-channels-key (:team-id org-data)))))]
   :change-data         [[:base :org-slug]
                          (fn [base org-slug]
                            (when (and base org-slug)
                              (get-in base (change-data-key org-slug))))]
   :change-cache-data   [[:base :org-slug]
                          (fn [base org-slug]
                            (when (and base org-slug)
                              (get-in base (change-cache-data-key org-slug))))]
   :editable-boards     [[:base :org-slug]
                          (fn [base org-slug]
                            (let [boards-key (boards-key org-slug)
                                  boards (get-in base boards-key)
                                  filtered-boards (filterv
                                                   (fn [board]
                                                      (let [links (-> board :board-data :links)]
                                                        (some #(when (= (:rel %) "create") %) links)))
                                                   (vals boards))]
                              (zipmap
                               (map #(-> % :board-data :slug) filtered-boards)
                               (map :board-data filtered-boards))))]
   :board-data          [[:base :org-slug :board-slug]
                          (fn [base org-slug board-slug]
                            (when (and org-slug board-slug)
                              (get-in base (board-data-key org-slug board-slug))))]
   :activity-data       [[:base :org-slug :activity-uuid]
                          (fn [base org-slug activity-uuid]
                            (get-in base (activity-key org-slug activity-uuid)))]
   :secure-activity-data [[:base :org-slug :secure-id]
                          (fn [base org-slug secure-id]
                            (get-in base (secure-activity-key org-slug secure-id)))]
   :comments-data       [[:base :org-slug]
                         (fn [base org-slug]
                           (get-in base (comments-key org-slug)))]
   :edit-user-profile   [[:base]
                          (fn [base]
                            {:user-data (:edit-user-profile base)
                             :error (:edit-user-profile-failed base)})]
   :entry-editing       [[:base]
                          (fn [base]
                            (:entry-editing base))]
   :section-editing     [[:base]
                          (fn [base]
                            (:section-editing base))]
   :show-section-editor [[:base]
                          (fn [base]
                            (:show-section-editor base))]
   :show-section-add    [[:base]
                          (fn [base]
                            (:show-section-add base))]
   :show-sections-picker [[:base]
                          (fn [base]
                            (:show-sections-picker base))]
   :org-editing         [[:base]
                          (fn [base]
                            (:org-editing base))]
   :alert-modal         [[:base]
                          (fn [base]
                            (:alert-modal base))]
   :activity-share        [[:base] (fn [base] (:activity-share base))]
   :activity-share-medium [[:base] (fn [base] (:activity-share-medium base))]
   :activity-share-container  [[:base] (fn [base] (:activity-share-container base))]
   :activity-shared-data  [[:base] (fn [base] (:activity-shared-data base))]
   :activities-read       [[:base] (fn [base] (get-in base activities-read-key))]
   :fullscreen-post-data [[:base :org-data :activity-data :activity-share
                           :add-comment-focus :ap-initial-at :comments-data
                           :show-sections-picker :section-editing :activities-read]
                          (fn [base org-data activity-data activity-share
                               add-comment-focus ap-initial-at comments-data
                               show-sections-picker section-editing activities-read]
                            {:org-data org-data
                             :activity-data activity-data
                             :activity-modal-fade-in (:activity-modal-fade-in base)
                             :modal-editing-data (:modal-editing-data base)
                             :modal-editing (:modal-editing base)
                             :dismiss-modal-on-editing-stop (:dismiss-modal-on-editing-stop base)
                             :activity-share activity-share
                             :entry-save-on-exit (:entry-save-on-exit base)
                             :add-comment-focus add-comment-focus
                             :comments-data comments-data
                             :ap-initial-at ap-initial-at
                             :show-sections-picker show-sections-picker
                             :section-editing section-editing
                             :read-data (get activities-read (router/current-activity-id))})]
   :navbar-data         [[:base :org-data :board-data]
                          (fn [base org-data board-data]
                            (let [navbar-data (select-keys base [:mobile-menu-open
                                                                 :show-login-overlay
                                                                 :mobile-navigation-sidebar
                                                                 :current-user-data
                                                                 :orgs-dropdown-visible
                                                                 :user-settings
                                                                 :org-settings
                                                                 :search-active])]
                              (-> navbar-data
                                (assoc :org-data org-data)
                                (assoc :board-data board-data))))]
   :confirm-invitation    [[:base :route :auth-settings :jwt]
                            (fn [base route auth-settings jwt]
                              {:invitation-confirmed (:email-confirmed base)
                               :invitation-error (and (contains? base :email-confirmed)
                                                      (not (:email-confirmed base)))
                               :auth-settings auth-settings
                               :token (:token (:query-params route))
                               :jwt jwt})]
   :collect-password      [[:base :jwt]
                            (fn [base jwt]
                              {:invitation-confirmed (:email-confirmed base)
                               :collect-pswd (:collect-pswd base)
                               :collect-pswd-error (:collect-password-error base)
                               :jwt jwt})]
   :password-reset        [[:base :auth-settings]
                            (fn [base auth-settings]
                              {:auth-settings auth-settings
                               :error (:collect-pswd-error base)})]
   :media-input           [[:base]
                            (fn [base]
                              (:media-input base))]
   :search-active         [[:base] (fn [base] (:search-active base))]
   :search-results        [[:base] (fn [base] (:search-results base))]
   :org-dashboard-data    [[:base :orgs :org-data :board-data :container-data :filtered-posts :activity-data :ap-initial-at
                            :show-section-editor :show-section-add :show-sections-picker :entry-editing
                            :mobile-menu-open :jwt]
                            (fn [base orgs org-data board-data container-data filtered-posts activity-data
                                 ap-initial-at show-section-editor show-section-add show-sections-picker
                                 entry-editing mobile-menu-open jwt]
                              {:jwt jwt
                               :orgs orgs
                               :org-data org-data
                               :container-data container-data
                               :board-data board-data
                               :posts-data filtered-posts
                               :org-settings-data (:org-settings base)
                               :user-settings (:user-settings base)
                               :made-with-carrot-modal-data (:made-with-carrot-modal base)
                               :is-entry-editing (boolean (:entry-editing base))
                               :is-sharing-activity (boolean (:activity-share base))
                               :is-showing-alert (boolean (:alert-modal base))
                               :entry-edit-dissmissing (:entry-edit-dissmissing base)
                               :media-input (:media-input base)
                               :ap-initial-at ap-initial-at
                               :show-section-editor show-section-editor
                               :show-section-add show-section-add
                               :show-sections-picker show-sections-picker
                               :entry-editing-board-slug (:board-slug entry-editing)
                               :mobile-navigation-sidebar (:mobile-navigation-sidebar base)
                               :activity-share-container (:activity-share-container base)
                               :mobile-menu-open mobile-menu-open
                               :show-cmail (boolean (:cmail-state base))})]
   :show-add-post-tooltip      [[:nux] (fn [nux] (:show-add-post-tooltip nux))]
   :show-add-comment-tooltip   [[:nux] (fn [nux] (:show-add-comment-tooltip nux))]
   :show-edit-tooltip          [[:nux] (fn [nux] (:show-edit-tooltip nux))]
   :show-post-added-tooltip    [[:nux] (fn [nux] (:show-post-added-tooltip nux))]
   :show-draft-post-tooltip    [[:nux] (fn [nux] (:show-draft-post-tooltip nux))]
   :show-invite-people-tooltip [[:nux] (fn [nux] (:show-invite-people-tooltip nux))]
   :nux-user-type              [[:nux] (fn [nux] (:user-type nux))]
   ;; Cmail
   :cmail-state           [[:base] (fn [base] (:cmail-state base))]
   :cmail-data            [[:base] (fn [base] (:cmail-data base))]})

;; Action Loop =================================================================

(defmulti action (fn [db [action-type & _]]
                   (when (and (not= action-type :input)
                              (not= action-type :update)
                              (not= action-type :entry-toggle-save-on-exit))
                     (timbre/info "Dispatching action:" action-type))
                   action-type))

(def actions (flux/dispatcher))

(def actions-dispatch
  (flux/register
   actions
   (fn [payload]
     ;; (prn payload) ; debug :)
     (swap! app-state action payload))))

(defn dispatch! [payload]
  (flux/dispatch actions payload))

;; Data

(defn ap-initial-at
  "Get ap-initial-at."
  ([] (ap-initial-at @app-state))
  ([data] (:ap-initial-at data)))

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
  ([data] (get-in data [:current-user-data])))

(defn orgs-data
  ([] (orgs-data @app-state))
  ([data] (get data orgs-key)))

(defn org-data
  "Get org data."
  ([]
    (org-data @app-state))
  ([data]
    (org-data data (router/current-org-slug)))
  ([data org-slug]
    (get-in data (org-data-key org-slug))))

(defn posts-data
  "Get org all posts data."
  ([]
    (posts-data @app-state))
  ([data]
    (posts-data data (router/current-org-slug)))
  ([data org-slug]
    (get-in data (posts-data-key org-slug))))

(defn board-data
  "Get board data."
  ([]
    (board-data @app-state))
  ([data]
    (board-data data (router/current-org-slug) (router/current-board-slug)))
  ([data org-slug]
    (board-data data org-slug (router/current-board-slug)))
  ([data org-slug board-slug]
    (get-in data (board-data-key org-slug board-slug))))

(defn container-data
  "Get container data."
  ([]
    (container-data @app-state))
  ([data]
    (container-data data (router/current-org-slug) (router/current-posts-filter)))
  ([data org-slug]
    (container-data data org-slug (router/current-posts-filter)))
  ([data org-slug posts-filter]
    (get-in data (container-key org-slug posts-filter))))

(defn filtered-posts-data
  ([]
    (filtered-posts-data @app-state))
  ([data]
    (filtered-posts-data data (router/current-org-slug) (router/current-posts-filter)))
  ([data org-slug]
    (filtered-posts-data data org-slug (router/current-posts-filter)))
  ([data org-slug posts-filter]
    (let [org-data (org-data data org-slug)
          all-boards-slug (map :slug (:boards org-data))
          is-board? ((set all-boards-slug) posts-filter)
          posts-data (get-in data (posts-data-key org-slug))]
     (if is-board?
       (get-posts-for-board posts-data posts-filter)
       (let [container-key (container-key org-slug posts-filter)
             items-list (:posts-list (get-in data container-key))]
        (zipmap items-list (map #(get posts-data %) items-list)))))))

(defn draft-posts-data
  ([]
    (draft-posts-data @app-state (router/current-org-slug)))
  ([org-slug]
    (draft-posts-data @app-state org-slug))
  ([data org-slug]
    (filtered-posts-data data org-slug utils/default-drafts-board-slug)))

(defn activity-data
  "Get activity data."
  ([]
    (activity-data (router/current-org-slug) (router/current-activity-id) @app-state))
  ([activity-id]
    (activity-data (router/current-org-slug) activity-id @app-state))
  ([org-slug activity-id]
    (activity-data org-slug activity-id @app-state))
  ([org-slug activity-id data]
    (let [activity-key (activity-key org-slug activity-id)]
      (get-in data activity-key))))

(defn secure-activity-data
  "Get secure activity data."
  ([]
    (secure-activity-data (router/current-org-slug) (router/current-secure-activity-id) @app-state))
  ([secure-id]
    (secure-activity-data (router/current-org-slug) secure-id @app-state))
  ([org-slug secure-id]
    (secure-activity-data org-slug secure-id @app-state))
  ([org-slug secure-id data]
    (let [activity-key (secure-activity-key org-slug secure-id)]
      (get-in data activity-key))))

(defn comments-data
  ([]
    (comments-data (router/current-org-slug) @app-state))
  ([org-slug]
    (comments-data org-slug @app-state))
  ([org-slug data]
    (get-in data (comments-key org-slug))))

(defn activity-comments-data
  ([]
    (activity-comments-data
     (router/current-org-slug)
     (router/current-activity-id)
     @app-state))
  ([activity-uuid]
    (activity-comments-data
     (router/current-org-slug)
     activity-uuid @app-state))
  ([org-slug activity-uuid]
    (activity-comments-data org-slug activity-uuid @app-state))
  ([org-slug activity-uuid data]
    (get-in data (activity-comments-key org-slug activity-uuid))))

(defn teams-data
  ([] (teams-data @app-state))
  ([data] (get-in data teams-data-key)))

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

(defn uploading-video-data
  ([video-id] (uploading-video-data (router/current-org-slug) video-id @app-state))
  ([org-slug video-id] (uploading-video-data org-slug video-id @app-state))
  ([org-slug video-id data]
    (let [uv-key (uploading-video-key org-slug video-id)]
      (get-in data uv-key))))

;; Change related

(defn change-data
  "Get change data."
  ([]
    (change-data @app-state))
  ([data]
    (change-data data (router/current-org-slug)))
  ([data org-slug]
    (get-in data (change-data-key org-slug))))

(defn change-cache-data
  "Get change data."
  ([]
    (change-cache-data @app-state))
  ([data]
    (change-cache-data data (router/current-org-slug)))
  ([data org-slug]
    (get-in data (change-cache-data-key org-slug))))

(defn activities-read-data
  "Get the read counts of all the items."
  ([]
    (activities-read-data nil @app-state))
  ([item-ids]
    (activities-read-data @app-state item-ids))
  ([item-ids data]
    (let [all-activities-read (get-in data activities-read-key)]
      (if item-ids
        (select-keys all-activities-read item-ids)
        all-activities-read))))

;; Debug functions

(defn print-app-state []
  (js/console.log @app-state))

(defn print-org-data []
  (js/console.log (get-in @app-state (org-data-key (router/current-org-slug)))))

(defn print-team-data []
  (js/console.log (get-in @app-state (team-data-key (:team-id (org-data))))))

(defn print-team-roster []
  (js/console.log (get-in @app-state (team-roster-key (:team-id (org-data))))))

(defn print-change-data []
  (js/console.log (get-in @app-state (change-data-key (router/current-org-slug)))))

(defn print-activities-read-data []
  (js/console.log (get-in @app-state activities-read-key)))

(defn print-change-cache-data []
  (js/console.log (get-in @app-state (change-cache-data-key (router/current-org-slug)))))

(defn print-board-data []
  (js/console.log
   (get-in @app-state (board-data-key (router/current-org-slug) (router/current-board-slug)))))

(defn print-container-data []
  (js/console.log
   (get-in @app-state (container-key (router/current-org-slug) (router/current-board-slug)))))

(defn print-activity-data []
  (js/console.log
   (get-in
    @app-state
    (activity-key (router/current-org-slug) (router/current-activity-id)))))

(defn print-secure-activity-data []
  (js/console.log
   (get-in
    @app-state
    (secure-activity-key (router/current-org-slug) (router/current-secure-activity-id)))))

(defn print-reactions-data []
  (js/console.log
   (get-in
    @app-state
    (conj
     (activity-key (router/current-org-slug) (router/current-activity-id))
     :reactions))))

(defn print-comments-data []
  (js/console.log
   (get-in
    @app-state
    (comments-key (router/current-org-slug)))))

(defn print-activity-comments-data []
  (js/console.log
   (get-in
    @app-state
    (activity-comments-key (router/current-org-slug) (router/current-activity-id)))))

(defn print-entry-editing-data []
  (js/console.log (get @app-state :entry-editing)))

(defn print-posts-data []
  (js/console.log (get-in @app-state (posts-data-key (router/current-org-slug)))))

(defn print-filtered-posts []
  (js/console.log (filtered-posts-data @app-state (router/current-org-slug) (router/current-posts-filter))))

(set! (.-OCWebPrintAppState js/window) print-app-state)
(set! (.-OCWebPrintOrgData js/window) print-org-data)
(set! (.-OCWebPrintTeamData js/window) print-team-data)
(set! (.-OCWebPrintTeamRoster js/window) print-team-roster)
(set! (.-OCWebPrintChangeData js/window) print-change-data)
(set! (.-OCWebPrintActivitiesReadData js/window) print-activities-read-data)
(set! (.-OCWebPrintChangeCacheData js/window) print-change-cache-data)
(set! (.-OCWebPrintBoardData js/window) print-board-data)
(set! (.-OCWebPrintContainerData js/window) print-container-data)
(set! (.-OCWebPrintActivityData js/window) print-activity-data)
(set! (.-OCWebPrintSecureActivityData js/window) print-secure-activity-data)
(set! (.-OCWebPrintReactionsData js/window) print-reactions-data)
(set! (.-OCWebPrintCommentsData js/window) print-comments-data)
(set! (.-OCWebPrintActivityCommentsData js/window) print-activity-comments-data)
(set! (.-OCWebPrintEntryEditingData js/window) print-entry-editing-data)
(set! (.-OCWebPrintFilteredPostsData js/window) print-filtered-posts)
(set! (.-OCWebPrintPostsData js/window) print-posts-data)