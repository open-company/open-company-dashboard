(ns oc.web.components.navigation-sidebar
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.mixins.ui :as ui-mixins]
            [oc.web.lib.responsive :as responsive]
            [oc.web.components.org-settings :as org-settings]
            [oc.web.components.ui.whats-new-modal :as whats-new-modal]
            [goog.events :as events]
            [taoensso.timbre :as timbre]
            [goog.events.EventType :as EventType]))

(defn close-navigation-sidebar []
  (dis/dispatch! [:input [:mobile-navigation-sidebar] false]))

(defn sort-boards [boards]
  (vec (sort-by :name boards)))

(defn anchor-nav! [e url]
  (utils/event-stop e)
  (router/nav! url)
  (close-navigation-sidebar))

(def sidebar-top-margin 122)
(def footer-button-height 31)

(defn save-content-height [s]
  (when-let [navigation-sidebar-content (rum/ref-node s "left-navigation-sidebar-content")]
    (let [height (.height (js/$ navigation-sidebar-content))]
      (when (not= height @(::content-height s))
        (reset! (::content-height s) height)))))

(defn filter-board [board-data]
  (let [self-link (utils/link-for (:links board-data) "self")]
    (and (not= (:slug board-data) utils/default-drafts-board-slug)
         (or (not (contains? self-link :count))
             (and (contains? self-link :count)
                  (pos? (:count self-link)))))))

(defn filter-boards [all-boards]
  (filterv filter-board all-boards))

(defn save-window-height
  "Save the window height in the local state."
  [s]
  (reset! (::window-height s) (.-innerHeight js/window)))

(rum/defcs navigation-sidebar < rum/reactive
                                ;; Derivatives
                                (drv/drv :org-data)
                                (drv/drv :change-data)
                                (drv/drv :mobile-navigation-sidebar)
                                ;; Locals
                                (rum/local false ::content-height)
                                (rum/local nil ::window-height)
                                ;; Mixins
                                ui-mixins/first-render-mixin
                                (ui-mixins/render-on-resize save-window-height)

                                {:will-mount (fn [s]
                                  (save-window-height s)
                                  (save-content-height s)
                                  s)
                                 :did-mount (fn [s]
                                  (save-content-height s)
                                  (when-not (utils/is-test-env?)
                                    (.tooltip (js/$ "[data-toggle=\"tooltip\"]")))
                                  s)
                                 :will-update (fn [s]
                                  (save-content-height s)
                                  s)
                                 :did-update (fn [s]
                                  (when-not (utils/is-test-env?)
                                    (.tooltip (js/$ "[data-toggle=\"tooltip\"]")))
                                  s)}
  [s]
  (let [org-data (drv/react s :org-data)
        change-data (drv/react s :change-data)
        mobile-navigation-sidebar (drv/react s :mobile-navigation-sidebar)
        left-navigation-sidebar-width (- responsive/left-navigation-sidebar-width 20)
        all-boards (:boards org-data)
        boards (filter-boards all-boards)
        is-all-posts (or (= (router/current-board-slug) "all-posts") (:from-all-posts @router/path))
        create-link (utils/link-for (:links org-data) "create")
        show-boards (or create-link (pos? (count boards)))
        show-all-posts (and (jwt/user-is-part-of-the-team (:team-id org-data))
                            (utils/link-for (:links org-data) "activity"))
        drafts-board (first (filter #(= (:slug %) utils/default-drafts-board-slug) all-boards))
        drafts-link (utils/link-for (:links drafts-board) "self")
        show-drafts (pos? (:count drafts-link))
        org-slug (router/current-org-slug)
        show-invite-people (and org-slug
                                (jwt/is-admin? (:team-id org-data)))
        is-tall-enough? (or (not @(::content-height s))
                            (<
                             @(::content-height s)
                             (-
                              @(::window-height s)
                              sidebar-top-margin
                              footer-button-height
                              20
                              (when show-invite-people
                               footer-button-height))))]
    [:div.left-navigation-sidebar.group
      {:class (when mobile-navigation-sidebar "show-mobile-boards-menu")}
      [:div.left-navigation-sidebar-content
        {:ref "left-navigation-sidebar-content"}
        ;; All posts
        (when show-all-posts
          [:a.all-posts.hover-item.group
            {:class (utils/class-set {:item-selected is-all-posts
                                      :showing-drafts show-drafts})
             :href (oc-urls/all-posts)
             :on-click #(anchor-nav! % (oc-urls/all-posts))}
            [:div.all-posts-icon
              {:class (when is-all-posts "selected")}]
            [:div.all-posts-label
                "All Posts"]])
        (when show-drafts
          (let [board-url (oc-urls/board (:slug drafts-board))]
            [:a.drafts.hover-item.group
              {:class (when (and (not is-all-posts)
                                 (= (router/current-board-slug) (:slug drafts-board)))
                        "item-selected")
               :data-board (name (:slug drafts-board))
               :key (str "board-list-" (name (:slug drafts-board)))
               :href board-url
               :on-click #(anchor-nav! % board-url)}
              [:div.drafts-label.group
                "Drafts "
                [:span.count "(" (:count drafts-link) ")"]]]))
        ;; Boards list
        (when show-boards
          [:div.left-navigation-sidebar-top.group
            ;; Boards header
            [:h3.left-navigation-sidebar-top-title.group
              [:span
                "SECTIONS"]
              (when create-link
                [:button.left-navigation-sidebar-top-title-button.btn-reset
                  {:on-click #(do
                               (dis/dispatch! [:input [:show-section-add] true])
                               (close-navigation-sidebar))
                   :title "Create a new section"
                   :data-placement "top"
                   :data-toggle (when-not (responsive/is-tablet-or-mobile?) "tooltip")
                   :data-container "body"}])]])
        (when show-boards
          [:div.left-navigation-sidebar-items.group
            (for [board (sort-boards boards)
                  :let [board-url (oc-urls/board org-slug (:slug board))
                        is-current-board (= (router/current-board-slug) (:slug board))]]
              [:a.left-navigation-sidebar-item.hover-item
                {:class (when (and (not is-all-posts) is-current-board) "item-selected")
                 :data-board (name (:slug board))
                 :key (str "board-list-" (name (:slug board)))
                 :href board-url
                 :on-click #(anchor-nav! % board-url)}
                (when (= (:access board) "public")
                  [:div.public
                    {:class (when is-current-board "selected")}])
                (when (= (:access board) "private")
                  [:div.private
                    {:class (when is-current-board "selected")}])
                [:div.board-name.group
                  {:class (utils/class-set {:public-board (= (:access board) "public")
                                            :private-board (= (:access board) "private")
                                            :team-board (= (:access board) "team")})}
                  [:div.internal
                    {:class (utils/class-set {:new (:new board)
                                              :has-icon (#{"public" "private"} (:access board))})
                     :key (str "board-list-" (name (:slug board)) "-internal")
                     :dangerouslySetInnerHTML (utils/emojify (or (:name board) (:slug board)))}]]])])]
      [:div.left-navigation-sidebar-footer
        {:style {:position (if is-tall-enough? "absolute" "relative")}}
        (when show-invite-people
          [:button.mlb-reset.invite-people-btn
            {:on-click #(do
                          (org-settings/show-modal :invite)
                          (close-navigation-sidebar))}
            [:div.invite-people-icon]
            [:span "Invite people"]])
        [:button.mlb-reset.about-carrot-btn
          {:on-click #(do
                        (whats-new-modal/show-modal)
                        (close-navigation-sidebar))}
          [:div.about-carrot-icon]
          [:span "Support"]]]]))