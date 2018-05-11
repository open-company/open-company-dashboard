(ns oc.web.components.ui.activity-share
  (:require [rum.core :as rum]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [org.martinklepsch.derivatives :as drv]
            [dommy.core :as dommy :refer-macros (sel1)]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.mixins.ui :as mixins]
            [oc.web.local-settings :as ls]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.team :as team-actions]
            [oc.web.actions.activity :as activity-actions]
            [oc.web.components.ui.small-loading :refer (small-loading)]
            [oc.web.components.ui.item-input :refer (item-input email-item)]
            [oc.web.components.ui.slack-channels-dropdown :refer (slack-channels-dropdown)]))

(defn dismiss []
  (activity-actions/activity-share-hide))

(defn close-clicked [s]
  (reset! (::dismiss s) true)
  (utils/after 180 dismiss))

(defn- has-bot?
  "Check if the current team has a bot associated."
  [org-data]
  (jwt/team-has-bot? (:team-id org-data)))

(defn- show-slack-tooltip?
  "Show the Slack tooltip only if the user signed in with Slack
  and there our bot was added to the team."
  [org-data]
  (and (jwt/is-slack-org?)
       (not (has-bot? org-data))))

(defn- highlight-url
  "Select the whole content of the share link filed."
  [s]
  (when-let [url-field (rum/ref-node s "activity-share-url-field")]
    (.select url-field)))

(rum/defcs activity-share < rum/reactive
                            ;; Derivatives
                            (drv/drv :org-data)
                            (drv/drv :activity-share)
                            (drv/drv :activity-shared-data)
                            ;; Locals
                            (rum/local nil ::email-data)
                            (rum/local {:note ""} ::slack-data)
                            (rum/local :url ::medium)
                            (rum/local false ::dismiss)
                            (rum/local false ::copied)
                            (rum/local false ::sharing)
                            (rum/local false ::shared)
                            (rum/local "" ::email-subject)
                            (rum/local (rand 1000) ::item-input-key)
                            (rum/local (rand 1000) ::slack-channels-dropdown-key)
                            (rum/local nil ::window-click-listener)
                            (rum/local :team ::url-audience)
                            ;; Mixins
                            mixins/first-render-mixin
                            {:will-mount (fn [s]
                              (team-actions/teams-get-if-needed)
                              (let [activity-data (:share-data @(drv/get-ref s :activity-share))
                                    org-data @(drv/get-ref s :org-data)
                                    subject (.text (.html (js/$ "<div />") (:headline activity-data)))]
                               (reset! (::email-subject s) subject)
                               (reset! (::email-data s) {:subject subject
                                                         :note ""})
                               (when (has-bot? org-data)
                                 (reset! (::medium s) :slack)))
                             s)
                             :did-mount (fn [s]
                              (let [slack-button (rum/ref-node s "slack-button")
                                    org-data @(drv/get-ref s :org-data)]
                                (when (show-slack-tooltip? org-data)
                                  (.tooltip
                                   (js/$ slack-button)
                                   #js {:trigger "manual"})))
                              s)
                             :before-render (fn [s]
                              ;; When we have a sharing response
                              (when-let [shared-data @(drv/get-ref s :activity-shared-data)]
                                (when (compare-and-set! (::sharing s) true false)
                                  (reset!
                                   (::shared s)
                                   (if (:error shared-data) :error :shared))
                                  ;; If share succeeded reset share fields
                                  (when-not (:error shared-data)
                                    (cond
                                      (= @(::medium s) :email)
                                      (do
                                        (reset! (::item-input-key s) (rand 1000))
                                        (reset! (::email-data s) {:subject @(::email-subject s)
                                                                  :note ""}))
                                      (= @(::medium s) :slack)
                                      (do
                                        (reset! (::slack-channels-dropdown-key s) (rand 1000))
                                        (reset! (::slack-data s) {:note ""}))))
                                  (utils/after
                                   2000
                                   (fn []
                                    (reset! (::shared s) false)
                                    (activity-actions/activity-share-reset)))))
                              s)}
  [s]
  (let [activity-data (:share-data (drv/react s :activity-share))
        org-data (drv/react s :org-data)
        email-data @(::email-data s)
        slack-data @(::slack-data s)
        secure-uuid (:secure-uuid activity-data)
        ;; Make sure it gets remounted when share request finishes
        _ (drv/react s :activity-shared-data)
        is-mobile? (responsive/is-tablet-or-mobile?)]
    [:div.activity-share-modal-container
      {:class (utils/class-set {:will-appear (or @(::dismiss s) (not @(:first-render-done s)))
                                :appear (and (not @(::dismiss s)) @(:first-render-done s))})}
      [:div.activity-share-modal
        [:div.activity-share-main-cta
          (when is-mobile?
            [:button.mobile-modal-close-bt.mlb-reset
              {:on-click #(close-clicked s)}])
          "Share post"]
        [:div.activity-share-medium-selector-container
          [:div.activity-share-medium-selector
            {:class (when (= @(::medium s) :url) "selected")
             :on-click (fn [_]
                        (when-not @(::sharing s)
                          (reset! (::medium s) :url)
                          (utils/after
                           500
                           #(highlight-url s))))}
            "URL"]
          (let [slack-disabled (not (has-bot? org-data))
                show-slack-tooltip? (show-slack-tooltip? org-data)]
            [:div.activity-share-medium-selector
              {:class (utils/class-set {:selected (= @(::medium s) :slack)
                                        :medium-selector-disabled slack-disabled})
               :data-placement "top"
               :data-container "body"
               :title "Enable the Slack bot in Settings"
               :ref "slack-button"
               :on-click (fn [e]
                           (utils/event-stop e)
                           (when-not @(::sharing s)
                             (if slack-disabled
                               (when show-slack-tooltip?
                                 (let [$this (js/$ (rum/ref-node s "slack-button"))]
                                   (.tooltip $this "show")
                                   (utils/after
                                    2000
                                    #(.tooltip $this "hide"))))
                               (reset! (::medium s) :slack))))}
              "Slack"])
          [:div.activity-share-medium-selector
            {:class (when (= @(::medium s) :email) "selected")
             :on-click #(when-not @(::sharing s)
                         (reset! (::medium s) :email))}
            "Email"]
          ]
        (when is-mobile?
          [:div.activity-share-subheadline
            "People outside your Carrot team will not see comments."])
        [:div.activity-share-divider-line]
        (when (= @(::medium s) :email)
          [:div.activity-share-share.fs-hide
            [:div.mediums-box
              [:div.medium
                [:div.email-medium.group
                  [:div.medium-row.group
                    [:div.labels
                      "To"]
                    [:div.fields
                      {:class (when (:to-error email-data) "error")
                       ;; Set the key to force remount a new component with empty value
                       :key (str "email-share-item-input-" @(::item-input-key s))}
                      (item-input {:item-render email-item
                                   :match-ptn #"(\S+)[,|\s]+"
                                   :split-ptn #"[,|\s]+"
                                   :container-node :div.email-field
                                   :valid-item? utils/valid-email?
                                   :items (:to email-data)
                                   :input-type "email"
                                   :on-intermediate-change #(reset!
                                                             (::email-data s)
                                                             (merge email-data {:to-error false}))
                                   :on-change (fn [v]
                                               (reset!
                                                (::email-data s)
                                                (merge email-data {:to v
                                                                   :to-error false})))})]]
                  [:div.medium-row.subject.group
                    [:div.labels
                      "Subject"]
                    [:div.fields
                      {:class (when (:subject-error email-data) "error")}
                      [:input
                        {:type "text"
                         :value (:subject email-data)
                         :on-change #(reset! (::email-data s) (merge email-data {:subject (.. % -target -value)
                                                                                 :subject-error false}))}]]]
                  [:div.medium-row.note.group
                    [:div.labels
                      "Personal note (optional)"]
                    [:div.fields
                      [:textarea
                        {:value (:note email-data)
                         :on-change #(reset! (::email-data s) (merge email-data {:note (.. % -target -value)}))}]]]]]]
            [:div.share-footer.group
              [:div.right-buttons
                [:button.mlb-reset.mlb-black-link
                  {:on-click #(close-clicked s)}
                  "Cancel"]
                [:button.mlb-reset.share-button
                  {:on-click #(let [email-share {:medium :email
                                                 :note (:note email-data)
                                                 :subject (:subject email-data)
                                                 :to (:to email-data)}]
                                (if (empty? (:to email-data))
                                  (reset! (::email-data s) (merge email-data {:to-error true}))
                                  (do
                                    (reset! (::sharing s) true)
                                    (activity-actions/activity-share activity-data [email-share]))))
                   :class (when (empty? (:to email-data)) "disabled")}
                  (if @(::shared s)
                    (if (= @(::shared s) :shared)
                      "Shared!"
                      "Ops...")
                    [(when @(::sharing s)
                      (small-loading))
                     "Share"])]]]])
        (when (= @(::medium s) :url)
          [:div.activity-share-modal-shared.group
            [:form
              {:on-submit #(utils/event-stop %)}
              [:div.medium-row.group
                [:div.labels
                  "Who can view this post?"]
                [:div.fields
                  [:select.mlb-reset.url-audience
                    {:value @(::url-audience s)
                     :on-change #(reset! (::url-audience s) (keyword (.. % -target -value)))}
                    [:option
                      {:value :team}
                      "Logged in team only"]
                    [:option
                      {:value :all}
                      "Public readonly"]]]]
              (when (not= @(::url-audience s) :team)
                [:div.url-audience-description
                  (str "Sharing this URL will allow non-registered users to access post content. "
                       "Note that activity from non-registered users will be exempt from post analytics.")])
              [:div.medium-row.url-field-row.group
                [:div.labels
                  "Share post URL"]
                (let [url-protocol (str "http" (when ls/jwt-cookie-secure "s") "://")
                      secure-url (oc-urls/secure-activity (router/current-org-slug) secure-uuid)
                      post-url (oc-urls/entry (router/current-org-slug) (:board-slug activity-data) (:uuid activity-data))
                      share-url (str url-protocol ls/web-server
                                  (if (= @(::url-audience s) :team)
                                    post-url
                                    secure-url))]
                  [:div.shared-url-container.group
                    [:input
                      {:value share-url
                       :key share-url
                       :read-only true
                       :content-editable false
                       :on-click #(highlight-url s)
                       :ref "activity-share-url-field"
                       :data-test1 url-protocol
                       :data-test2 secure-url
                       :data-test3 post-url
                       :data-test4 share-url
                       :data-test5 @(::url-audience s)
                       :data-test6 (= @(::url-audience s) :team)
                       :data-placement "top"}]])
                [:button.mlb-reset.copy-btn
                  {:ref "activity-share-url-copy-btn"
                   :on-click (fn [e]
                              (utils/event-stop e)
                              (let [url-input (rum/ref-node s "activity-share-url-field")]
                                (highlight-url s)
                                (when (utils/copy-to-clipboard url-input)
                                  (reset! (::copied s) true)
                                  (utils/after 2000 #(reset! (::copied s) false)))))}
                  (if @(::copied s)
                    "Copied!"
                    "Copy")]]]])
        (when (= @(::medium s) :slack)
          [:div.activity-share-share.fs-hide
            [:div.mediums-box
              [:div.medium
                [:div.slack-medium.group
                  [:div.medium-row.group
                    [:div.labels "Publish link to which channel?"]
                    [:div.fields
                      {:class (when (:channel-error slack-data) "error")
                       :key (str "slack-share-channels-dropdown-" @(::slack-channels-dropdown-key s))}
                      (slack-channels-dropdown
                       {:on-change (fn [team channel]
                                     (reset! (::slack-data s)
                                      (merge slack-data (merge slack-data
                                                         {:channel {:channel-id (:id channel)
                                                                    :channel-name (:name channel)
                                                                    :slack-org-id (:slack-org-id team)}
                                                          :channel-error false}))))
                        :on-intermediate-change (fn [_]
                                                 (reset! (::slack-data s)
                                                  (merge slack-data (merge slack-data {:channel-error false}))))
                        :initial-value ""
                        :disabled false})]]
                  [:div.medium-row.note.group
                    [:div.labels
                      "Personal note (optional)"]
                    [:div.fields
                      [:textarea
                        {:value (:note slack-data)
                         :on-change (fn [e]
                                     (reset! (::slack-data s)
                                      (merge slack-data {:note (.. e -target -value)})))}]]]]]]
            [:div.share-footer.group
              [:div.right-buttons
                [:button.mlb-reset.mlb-black-link
                  {:on-click #(close-clicked s)}
                  "Cancel"]
                [:button.mlb-reset.share-button
                  {:on-click #(let [slack-share {:medium :slack
                                                 :note (:note slack-data)
                                                 :channel (:channel slack-data)}]
                                (if (empty? (:channel slack-data))
                                  (when (empty? (:channel slack-data))
                                    (reset! (::slack-data s) (merge slack-data {:channel-error true})))
                                  (do
                                    (reset! (::sharing s) true)
                                    (activity-actions/activity-share activity-data [slack-share]))))
                   :class (when (empty? (:channel slack-data)) "disabled")}
                  (if @(::shared s)
                    (if (= @(::shared s) :shared)
                      "Sent!"
                      "Ops...")
                    [(when @(::sharing s)
                      (small-loading))
                     "Send"])]]]])]]))