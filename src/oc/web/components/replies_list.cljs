(ns oc.web.components.replies-list
  (:require [rum.core :as rum]
            [goog.object :as gobj]
            [clojure.data :as clj-data]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.utils.comment :as cu]
            [oc.web.utils.activity :as au]
            [oc.web.utils.dom :as dom-utils]
            [oc.web.mixins.ui :as ui-mixins]
            [oc.web.utils.ui :refer (ui-compose)]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.user :as user-actions]
            [oc.web.lib.react-utils :as react-utils]
            [oc.web.mixins.mention :as mention-mixins]
            [oc.web.utils.reaction :as reaction-utils]
            [oc.web.actions.nav-sidebar :as nav-actions]
            [oc.web.actions.comment :as comment-actions]
            [oc.web.actions.activity :as activity-actions]
            [oc.web.components.reactions :refer (reactions)]
            [oc.web.components.ui.alert-modal :as alert-modal]
            [oc.web.components.ui.more-menu :refer (more-menu)]
            [oc.web.components.ui.add-comment :refer (add-comment)]
            [oc.web.components.ui.small-loading :refer (small-loading)]
            [oc.web.components.ui.user-avatar :refer (user-avatar-image)]
            [oc.web.components.ui.all-caught-up :refer (all-caught-up caught-up-line)]
            [oc.web.components.ui.info-hover-views :refer (user-info-hover board-info-hover)]))

(defn- reply-to [comment-data add-comment-focus-key]
  (comment-actions/reply-to add-comment-focus-key (:body comment-data) true))

(defn- copy-comment-url [comment-url]
  (let [input-field (.createElement js/document "input")]
    (set! (.-style input-field) "position:absolute;top:-999999px;left:-999999px;")
    (set! (.-value input-field) comment-url)
    (.appendChild (.-body js/document) input-field)
    (.select input-field)
    (utils/copy-to-clipboard input-field)
    (.removeChild (.-body js/document) input-field)))

(rum/defc emoji-picker < rum/static
                         (when (responsive/is-mobile-size?)
                           ui-mixins/no-scroll-mixin)
  [{:keys [add-emoji-cb dismiss-cb]}]
  [:div.emoji-picker-container
    [:button.mlb-reset.close-bt
      {:on-click dismiss-cb}
      "Cancel"]
    (react-utils/build (.-Picker js/EmojiMart)
      {:native true
       :autoFocus true
       :onClick (fn [emoji _]
                  (add-emoji-cb emoji))})])

(defn- emoji-picker-container [s activity-data reply-data read-reply-cb]
  (let [showing-picker? (and (seq @(::show-picker s))
                             (= @(::show-picker s) (:uuid reply-data)))]
    (when showing-picker?
      (emoji-picker {:dismiss-cb #(reset! (::show-picker s) nil)
                     :add-emoji-cb (fn [emoji]
                                     (when (reaction-utils/can-pick-reaction? (gobj/get emoji "native") (:reactions reply-data))
                                       (read-reply-cb (:uuid reply-data))
                                       (comment-actions/react-from-picker activity-data reply-data
                                        (gobj/get emoji "native")))
                                     (reset! (::show-picker s) nil))}))))

(rum/defcs reply-comment <
  rum/static
  (rum/local false ::show-read-more)
  {:did-mount (fn [s]
   (let [props (-> s :rum/args first)]
     (when (fn? (:unwrap-body-cb props))
       (when-let [comment-body (rum/ref-node s :reply-comment-body)]
         (when (>= (.-scrollHeight comment-body)
                   (inc (* 3 (if (:is-mobile? props) 24 18))))
           (reset! (::show-read-more s) true)))))
   s)}
  [s {:keys [activity-data comment-data mouse-leave-cb
             react-cb reply-cb emoji-picker
             is-mobile? member? showing-picker?
             did-react-cb current-user-id reply-focus-value
             replies-count replying-to unwrap-body-cb]}]
  (let [show-new-comment-tag (:unread comment-data)]
    [:div.reply-comment-outer.open-reply
      {:key (str "reply-comment-" (:created-at comment-data))
       :data-comment-uuid (:uuid comment-data)
       :data-unwrapped-body (:unwrapped-body comment-data)
       :data-unread (:unread comment-data)
       :data-unwrapped-body-fn (fn? unwrap-body-cb)
       :class (utils/class-set {:new-comment (:unread comment-data)
                                :showing-picker showing-picker?
                                :no-replies (zero? replies-count)
                                :truncated-body @(::show-read-more s)
                                :no-mentions-popup @(::show-read-more s)})}
      [:div.reply-comment
        {:ref (str "reply-comment-" (:uuid comment-data))
         :on-mouse-leave mouse-leave-cb}
        [:div.reply-comment-inner
          (when is-mobile?
            [:div.reply-comment-mobile-menu
              (more-menu {:entity-data comment-data
                          :external-share false
                          :entity-type "comment"
                          :can-react? true
                          :react-cb react-cb
                          :can-reply? true
                          :reply-cb reply-cb})
              emoji-picker])
          [:div.reply-comment-right
            [:div.reply-comment-header.group
              {:class utils/hide-class}
              [:div.reply-comment-author-right
                [:div.reply-comment-author-right-group
                  {:class (when (:unread comment-data) "new-comment")}
                  [:div.reply-comment-author-name-container
                    (user-info-hover {:user-data (:author comment-data) :current-user-id current-user-id :leave-delay? true})
                    [:div.reply-comment-author-avatar
                      (user-avatar-image (:author comment-data))]
                    [:div.reply-comment-author-name
                      {:class (when (:user-id (:author comment-data)) "clickable-name")}
                      (:name (:author comment-data))]]
                  [:div.separator-dot]
                  [:div.reply-comment-author-timestamp
                    [:time
                      {:date-time (:created-at comment-data)
                       :data-toggle (when-not is-mobile? "tooltip")
                       :data-placement "top"
                       :data-container "body"
                       :data-delay "{\"show\":\"1000\", \"hide\":\"0\"}"
                       :data-title (utils/activity-date-tooltip comment-data)}
                      (utils/foc-date-time (:created-at comment-data))]]]
                (when show-new-comment-tag
                  [:div.separator-dot])
                (when show-new-comment-tag
                  [:div.new-comment-tag])
                (if (responsive/is-mobile-size?)
                  [:div.reply-comment-mobile-menu
                    (more-menu comment-data nil {:external-share false
                                                 :entity-type "comment"
                                                 :can-react? true
                                                 :react-cb react-cb
                                                 :can-reply? true
                                                 :reply-cb reply-cb})
                    emoji-picker]
                  [:div.reply-comment-floating-buttons
                    {:key "reply-comment-floating-buttons"}
                    ;; Reply to comment
                    [:button.mlb-reset.floating-bt.reply-bt
                      {:data-toggle "tooltip"
                       :data-placement "top"
                       :on-click reply-cb
                       :title "Reply"}
                      "Reply"]
                    ;; React container
                    [:div.react-bt-container.separator-bt
                      [:button.mlb-reset.floating-bt.react-bt
                        {:data-toggle "tooltip"
                         :data-placement "top"
                         :title "Add reaction"
                         :on-click react-cb}
                        "React"]
                      emoji-picker]])]]
            [:div.reply-comment-content
              [:div.reply-comment-body.oc-mentions.oc-mentions-hover
                {:dangerouslySetInnerHTML (utils/emojify (:body comment-data))
                 :ref :reply-comment-body
                 :class (utils/class-set {:emoji-comment (:is-emoji comment-data)
                                          utils/hide-class true})}]]
            (when @(::show-read-more s)
              [:button.mlb-reset.read-more-bt
                {:on-click #(do
                              (reset! (::show-read-more s) false)
                              (unwrap-body-cb))}
                "Read more"])
            (when (seq (:reactions comment-data))
              [:div.reply-comment-reactions-footer.group
                (reactions {:entity-data comment-data
                            :hide-picker (zero? (count (:reactions comment-data)))
                            :did-react-cb did-react-cb
                            :optional-activity-data activity-data})])]]]]))

(rum/defc reply-top <
  rum/static
  [{:keys [current-user-id publisher board-name published-at headline links] :as activity-data}]
  (let [follow-link (utils/link-for links "follow")
        unfollow-link (utils/link-for links "unfollow")]
    [:div.reply-item-top
      [:div.reply-item-header
        [:div.reply-item-author-container
          (user-info-hover {:user-data publisher :current-user-id current-user-id :leave-delay? true})
          (user-avatar-image publisher)
          [:span.author-name
            (:name publisher)]]
        ; [:span.in "in"]
        ; [:div.reply-item-board-container
        ;   (board-info-hover {:activity-data activity-data})
        ;   [:span.board-name
        ;     board-name]]
        [:div.separator-dot]
        [:span.time-since
          [:time
            {:date-time published-at}
            (utils/tooltip-date published-at)]]
        [:div.reply-item-title
          (str "→ " headline)]
        (when (or follow-link unfollow-link)
          [:button.mlb-reset.mute-bt
            {:title (if follow-link
                      "Get notified about new activity"
                      "Don't show future replies to this update")
             :class (if follow-link "unfollowing" "following")
             :data-toggle (when-not (responsive/is-mobile-size?) "tooltip")
             :data-placement "top"
             :data-container "body"
             :on-click #(activity-actions/inbox-unfollow (:uuid activity-data))}])]]))

(defn- reply-item-unique-class [{:keys [uuid]}]
  (str "reply-item-" uuid))

(defn- add-comment-focus-prefix []
  (str "reply-comment-" (int (rand 10000)) "-prefix"))

(defn- expand-reply [s reply-data]
  (let [replies @(::replies s)
        idx (utils/index-of replies #(= (:uuid %) (:uuid reply-data)))]
    (swap! (::replies s) (fn [reply]
                           (-> reply
                             (assoc-in [idx :collapsed-count] 0)
                             (update-in [idx :thread-children]
                              (fn [children]
                                (map #(assoc % :expanded true) children))))))))

(defn- reply-mark-read [s reply-uuid]
  (swap! (::replies s) (fn [replies]
                         (mapv
                          #(if (= (:uuid %) reply-uuid)
                             (assoc % :unread false)
                             %)
                          replies))))

(defn- entry-mark-read [s]
  (swap! (::replies s) (fn [replies] (mapv #(assoc % :unread false) replies))))

(defn unwrap-body [s reply-uuid]
  (swap! (::replies s) (fn [replies]
                         (mapv #(if (= (:uuid %) reply-uuid)
                                 (assoc % :unwrapped-body true)
                                 %)
                          replies))))

(defn- comment-item
  [s {:keys [activity-data reply-data is-mobile? read-reply-cb member?
             current-user-id reply-focus-value]}]
  (let [showing-picker? (and (seq @(::show-picker s))
                             (= @(::show-picker s) (:uuid reply-data)))
        replying-to (@(::replying s) (:uuid reply-data))]
    [:div.reply-item-block.vertical-line.group
      {:key (str "reply-thread-item-" (:uuid reply-data))}
      (reply-comment {:activity-data activity-data
                      :comment-data reply-data
                      :reply-focus-value reply-focus-value
                      :unwrap-body-cb (when-not (:unwrapped-body reply-data)
                                        #(unwrap-body s (:uuid reply-data)))
                      :is-mobile? is-mobile?
                      :react-cb #(reset! (::show-picker s) (:uuid reply-data))
                      :reply-cb #(reply-to reply-data reply-focus-value)
                      :did-react-cb #(read-reply-cb (:uuid reply-data))
                      :emoji-picker (when showing-picker?
                                      (emoji-picker-container s activity-data reply-data read-reply-cb))
                      :showing-picker? showing-picker?
                      :member? member?
                      :replies-count (:replies-count reply-data)
                      :current-user-id current-user-id
                      :replying-to replying-to})]))

(rum/defc collapsed-comments-button <
  rum/static
  [{:keys [message collapsed-count comment-uuids collapse-id expand-cb unread-collapsed]}]
  [:button.mlb-reset.view-more-bt
    {:on-click expand-cb
     :data-collapsed-count collapsed-count
     :data-comment-uuids comment-uuids
     :data-collapse-id collapse-id
     :data-unread-collapsed unread-collapsed}
    message])

(defn- update-replies [s]
  (let [props (-> s :rum/args first)
        all-comments (cu/ungroup-comments @(::replies s))
        expanded-unread-map (map #(select-keys % [:expanded :unread :unwrapped-body]) all-comments)
        collapsed-map (zipmap (map :uuid all-comments) expanded-unread-map)
        collapsed-comments (cu/collapse-comments (:initial-last-read-at props) (:comments-data props) collapsed-map)
        all-comments-after (cu/ungroup-comments collapsed-comments)]
    (reset! (::replies s) collapsed-comments)))

(defn expand-comments [s collapse-id]
  (let [{[collapse-item] :collapse-item ret :ret} (group-by #(if (= (:collapse-id %) collapse-id) :collapse-item :ret) @(::replies s))]
    (reset! (::replies s) (mapv #(if ((set (:comment-uuids collapse-item)) (:uuid %))
                                   (assoc % :expanded true)
                                   %)
                           ret))))

(rum/defcs reply-item < rum/static
                        rum/reactive
                        (rum/local nil ::show-picker)
                        (rum/local #{} ::replying)
                        (rum/local nil ::replies)
                        (rum/local nil ::add-comment-focus-prefix)
                        ui-mixins/refresh-tooltips-mixin
                        (ui-mixins/interactive-images-mixin "div.reply-comment-body")
                        (ui-mixins/on-window-click-mixin (fn [s e]
                         (when (and @(::show-picker s)
                                    (not (utils/event-inside? e
                                     (.get (js/$ "div.emoji-mart" (rum/dom-node s)) 0))))
                           (reset! (::show-picker s) nil))))
                        ;; Mentions:
                        (drv/drv :users-info-hover)
                        (drv/drv :current-user-data)
                        (drv/drv :follow-publishers-list)
                        (drv/drv :followers-publishers-count)
                        (mention-mixins/oc-mentions-hover {:click? true})
                        {:will-mount (fn [s]
                           (reset! (::add-comment-focus-prefix s) (add-comment-focus-prefix))
                           (update-replies s)
                         s)
                         :did-remount (fn [o s]
                           (let [items (-> s :rum/args first :comments-data)
                                 items-changed (clj-data/diff (-> o :rum/args first :comments-data) items)]
                              (when (or (seq (first items-changed))
                                        (seq (second items-changed)))
                                (update-replies s)))
                            s)}
  [s {uuid             :uuid
      publisher        :publisher
      unread           :unread
      published-at     :published-at
      member?          :member?
      comments-data    :comments-data
      initial-last-read-at :initial-last-read-at
      :as activity-data}]
  (let [_users-info-hover (drv/react s :users-info-hover)
        _follow-publishers-list (drv/react s :follow-publishers-list)
        _followers-publishers-count (drv/react s :followers-publishers-count)
        current-user-data (drv/react s :current-user-data)
        is-mobile? (responsive/is-mobile-size?)
        reply-item-class (reply-item-unique-class activity-data)
        replies @(::replies s)
        comments-loaded? (not (seq replies))
        add-comment-focus-value (cu/add-comment-focus-value @(::add-comment-focus-prefix s) uuid)]
    [:div.reply-item.group
      {:class (utils/class-set {:unread unread
                                :open-item true
                                :close-item true
                                reply-item-class true})
       :data-activity-uuid uuid
       :ref :reply-item
       :on-click (fn [e]
                   (let [reply-el (rum/ref-node s :reply-item)]
                     (when (and (not (utils/button-clicked? e))
                                (not (utils/input-clicked? e))
                                (not (utils/anchor-clicked? e))
                                (not (utils/content-editable-clicked? e))
                                (not (utils/event-inside? e (.querySelector reply-el "div.emoji-mart")))
                                (not (utils/event-inside? e (.querySelector reply-el "div.add-comment-box-container"))))
                       (nav-actions/open-post-modal activity-data false))))}
      (reply-top (assoc activity-data :current-user-id (:user-id current-user-data)))
      (if comments-loaded?
        [:div.reply-item-blocks.group
          [:div.reply-item-loading.group
            (small-loading)
            [:span.reply-item-loading-inner
              "Loading replies..."]]]
        [:div.reply-item-blocks.group
          (for [reply @(::replies s)
                :when (:expanded reply)]
            (if (= (:resource-type reply) :collapsed-comments)
              (rum/with-key
               (collapsed-comments-button (assoc reply :expand-cb #(expand-comments s (:collapse-id reply))))
               (str "collapsed-comments-bt-" (clojure.string/join "-" (:comment-uuids reply))))
              (comment-item s {:activity-data activity-data
                               :reply-data reply
                               :is-mobile? is-mobile?
                               :read-reply-cb (partial reply-mark-read s)
                               :member? member?
                               :reply-focus-value add-comment-focus-value
                               :current-user-id (:user-id current-user-data)})))
          (rum/with-key
           (add-comment {:activity-data activity-data
                         :collapse? true
                         :add-comment-placeholder "Reply..."
                         :add-comment-cb #(do
                                            (entry-mark-read s)
                                            (swap! (::replies s) merge {(:uuid %) {:unread false :expanded true :unwrapped-body true}}))
                         :add-comment-focus-prefix @(::add-comment-focus-prefix s)})
           (str "add-comment-" @(::add-comment-focus-prefix s) "-" uuid))])]))

(defn- mark-read-if-needed [s items-container offset-top item]
  (when-let [item-node (.querySelector items-container (str "div." (reply-item-unique-class item)))]
    (when (dom-utils/is-element-bottom-in-viewport? item-node offset-top)
      (let [read (activity-actions/mark-read (:uuid item))]
        (when read
          (swap! (::read-items s) conj (:uuid item)))))))

(defn- did-scroll [s _scroll-event]
  (when @(::has-unread-items s)
    (when-let [items-container (rum/ref-node s :entries-list)]
      (let [items @(::entries s)
            offset-top (if (responsive/is-mobile-size?) responsive/mobile-navbar-height responsive/navbar-height)]
        (doseq [item items
                :when (and (= (:resource-type item) :entry)
                           (seq (:last-read-at item))
                           (pos? (:new-comments-count item))
                           (not (@(::read-items s) (:uuid item))))]
          (mark-read-if-needed s items-container offset-top item))
        (when-not (some (comp pos? :new-comments-count) @(::entries s))
          (reset! (::has-unread-items s) false))))))

(defn- mark-read-entries? [entries]
  (some #(when (and (seq (:last-read-at %)) (pos? (:new-comments-count %))) %) entries))

(defn- last-reads-at-from-entries [entries]
  (zipmap (map :uuid entries) (map :last-read-at entries)))

(rum/defcs replies-list <
  rum/static
  rum/reactive
  (drv/drv :comments-data)
  (drv/drv :comment-reply-to)
  (drv/drv :add-comment-focus)
  ui-mixins/refresh-tooltips-mixin
  (rum/local #{} ::read-items)
  (rum/local false ::has-unread-items)
  (rum/local [] ::entries)
  (rum/local nil ::initial-last-reads-at)
  (ui-mixins/on-window-scroll-mixin did-scroll)
  {:will-mount (fn [s]
    (let [entries (-> s :rum/args first :items-to-render)]
     (reset! (::entries s) entries)
     (reset! (::has-unread-items s) (mark-read-entries? entries))
     (reset! (::initial-last-reads-at s) (last-reads-at-from-entries entries)))
    s)
   :did-mount (fn [s]
     (did-scroll s nil)
   s)
   :did-remount (fn [o s]
   (let [entries (-> s :rum/args first :items-to-render)
         items-changed (clj-data/diff (-> o :rum/args first :items-to-render) entries)]
     (when (or (seq (first items-changed))
               (seq (second items-changed)))
       (reset! (::entries s) entries)
       (reset! (::has-unread-items s) (mark-read-entries? entries))
       (swap! (::initial-last-reads-at s) #(merge (last-reads-at-from-entries entries) %))
       (utils/after 0 #(did-scroll s nil))))
   s)}
  [s {:keys [items-to-render last-read-at member?]}]
  (let [is-mobile? (responsive/is-mobile-size?)
        items @(::entries s)
        comments-drv (drv/react s :comments-data)
        _reply-to (drv/react s :comment-reply-to)
        _add-comment-focus (drv/react s :add-comment-focus)]
    [:div.replies-list
      (if (empty? items)
        [:div.replies-list-empty
          (all-caught-up)]
        [:div.replies-list-container
          {:ref :entries-list}
          (for [item* items
                :let [caught-up? (= (:resource-type item*) :caught-up)
                      loading-more? (= (:resource-type item*) :loading-more)
                      closing-item? (= (:resource-type item*) :closing-item)
                      item-comments-data (au/activity-comments item* comments-drv)
                      item-props (assoc item* :member? member?
                                              :comments-data item-comments-data
                                              :initial-last-read-at (get @(::initial-last-reads-at s) (:uuid item*)))]]
            (cond
              caught-up?
              (rum/with-key
               (caught-up-line item-props)
               (str "reply-caught-up-" (:last-activity-at item-props)))
              loading-more?
              [:div.loading-updates.bottom-loading
                {:key (str "reply-loading-more-" (:last-activity-at item-props))}
                (:message item-props)]
              closing-item?
              [:div.closing-item
                {:key (str "reply-closing-item-" (:last-activity-at item-props))}
                (:message item-props)]
              :else
              (rum/with-key
               (reply-item item-props)
               (str "reply-" (:uuid item-props) "-" (count item-comments-data)))))])]))