(ns oc.web.components.navigation-sidebar
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.lib.responsive :as responsive]
            [oc.web.mixins.ui :refer (first-render-mixin)]
            [goog.events :as events]
            [taoensso.timbre :as timbre]
            [goog.events.EventType :as EventType]))

(defn sort-boards [boards]
  (vec (sort-by :name boards)))

(defn anchor-nav! [e url]
  (utils/event-stop e)
  (router/nav! url))

(defn new?
  "
  A board is new if:
    user is part of the team (we don't track new for non-team members accessing public boards)
     -and-
    change-at is newer than seen at
      -or-
    we have a change-at and no seen at
  "
  [change-data board]
  (let [changes (get change-data (:uuid board))
        change-at (:change-at changes)
        nav-at (:nav-at changes)
        in-team? (jwt/user-is-part-of-the-team (:team-id (dis/org-data)))
        new? (and in-team?
                  (or (and change-at nav-at (> change-at nav-at))
                      (and change-at (not nav-at))))]
    (timbre/debug "New'ness in nav. test for:" (:slug board)
                  "in-team?:" in-team?
                  "change:" change-at
                  "nav:" nav-at
                  "new?:" new?)
    new?))

(def sidebar-top-margin 122)
(def footer-button-height 31)

(rum/defcs navigation-sidebar < rum/reactive
                                ;; Derivatives
                                (drv/drv :org-data)
                                (drv/drv :change-data)
                                ;; Locals
                                (rum/local false ::content-height)
                                (rum/local nil ::resize-listener)
                                (rum/local nil ::window-height)
                                ;; Mixins
                                first-render-mixin
                                {:did-mount (fn [s]
                                  (when-not (utils/is-test-env?)
                                    (.tooltip (js/$ "[data-toggle=\"tooltip\"]")))
                                  (reset! (::window-height s) (.-innerHeight js/window))
                                  (reset! (::resize-listener s)
                                   (events/listen
                                    js/window
                                    EventType/RESIZE
                                    #(reset! (::window-height s) (.-innerHeight js/window))))
                                  s)
                                 :will-update (fn [s]
                                  (when @(:first-render-done s)
                                    (let [height (.height (js/$ (rum/ref-node s "left-navigation-sidebar-content")))]
                                      (when (not= height @(::content-height s))
                                        (reset! (::content-height s) height))))
                                  s)
                                 :did-update (fn [s]
                                  (when-not (utils/is-test-env?)
                                    (.tooltip (js/$ "[data-toggle=\"tooltip\"]")))
                                  s)
                                 :will-unmount (fn [s]
                                  (when @(::resize-listener s)
                                    (events/unlistenByKey @(::resize-listener s)))
                                  s)}
  [s]
  (let [org-data (drv/react s :org-data)
        change-data (drv/react s :change-data)
        left-navigation-sidebar-width (- responsive/left-navigation-sidebar-width 20)
        all-boards (:boards org-data)
        boards (filterv #(not= (:slug %) "drafts") all-boards)
        is-all-posts (or (= (router/current-board-slug) "all-posts") (:from-all-posts @router/path))
        create-link (utils/link-for (:links org-data) "create")
        show-boards (or create-link (pos? (count boards)))
        show-all-posts (and (jwt/user-is-part-of-the-team (:team-id org-data))
                            (> (count (:boards org-data)) 1))
        show-create-new-board (and (not (responsive/is-tablet-or-mobile?))
                                   create-link)
        drafts-board (first (filter #(= (:slug %) "drafts") all-boards))
        drafts-link (utils/link-for (:links drafts-board) "self")
        show-drafts (pos? (:count drafts-link))
        org-slug (router/current-org-slug)
        show-invite-people (and org-slug
                                (jwt/is-admin? (:team-id org-data)))
        is-tall-enough? (<
                         @(::content-height s)
                         (-
                          @(::window-height s)
                          sidebar-top-margin
                          footer-button-height
                          20
                          (when show-invite-people
                           footer-button-height)))]
    [:div.left-navigation-sidebar.group
      [:div.left-navigation-sidebar-content
        {:ref "left-navigation-sidebar-content"}
        ;; All posts
        (when show-all-posts
          [:a.all-posts.hover-item.group
            {:class (when is-all-posts "item-selected")
             :href (oc-urls/all-posts)
             :on-click #(anchor-nav! % (oc-urls/all-posts))}
            [:div.all-posts-icon]
            [:div.all-posts-label
                "All Posts"]])
        ;; Boards list
        (when show-boards
          [:div.left-navigation-sidebar-top.group
            ;; Boards header
            [:h3.left-navigation-sidebar-top-title.group
              {:id "navigation-sidebar-boards"}
              [:span
                "BOARDS"]
              (when show-create-new-board
                [:button.left-navigation-sidebar-top-title-button.btn-reset.right
                  {:on-click #(dis/dispatch! [:board-edit nil])
                   :title "Create a new board"
                   :id "add-board-button"
                   :data-placement "top"
                   :data-toggle "tooltip"
                   :data-container "body"}])]])
        (when show-boards
          [:div.left-navigation-sidebar-items.group
            (for [board (sort-boards boards)
                  :let [board-url (utils/get-board-url org-slug (:slug board))]]
              [:a.left-navigation-sidebar-item.hover-item
                {:class (when (and (not is-all-posts) (= (router/current-board-slug) (:slug board))) "item-selected")
                 :data-board (name (:slug board))
                 :key (str "board-list-" (name (:slug board)))
                 :href board-url
                 :on-click #(anchor-nav! % board-url)}
                (when (or (= (:access board) "public")
                          (= (:access board) "private"))
                  [:img
                    {:src (if (= (:access board) "public")
                           (utils/cdn "/img/ML/board_public.svg")
                           (utils/cdn "/img/ML/board_private.svg"))
                     :class (if (= (:access board) "public") "public" "private")}])
                [:div.board-name.group
                  {:class (utils/class-set {:public-board (= (:access board) "public")
                                            :private-board (= (:access board) "private")
                                            :team-board (= (:access board) "team")})}
                  [:div.internal
                    {:class (utils/class-set {:new (new? change-data board)})
                     :key (str "board-list-" (name (:slug board)) "-internal")
                     :dangerouslySetInnerHTML (utils/emojify (or (:name board) (:slug board)))}]]])
            (when show-drafts
              (let [board-url (oc-urls/board (:slug drafts-board))]
                [:a.left-navigation-sidebar-item.drafts-board.hover-item
                  {:class (when (and (not is-all-posts)
                                     (= (router/current-board-slug)
                                     (:slug drafts-board)))
                            "item-selected")
                   :data-board (name (:slug drafts-board))
                   :key (str "board-list-" (name (:slug drafts-board)))
                   :href board-url
                   :on-click #(anchor-nav! % board-url)}
                  (when (or (= (:access drafts-board) "public")
                            (= (:access drafts-board) "private"))
                    [:img
                      {:src (if (= (:access drafts-board) "public")
                             (utils/cdn "/img/ML/board_public.svg")
                             (utils/cdn "/img/ML/board_private.svg"))
                       :class (if (= (:access drafts-board) "public") "public" "private")}])
                  [:div.board-name.group
                    {:class (utils/class-set {:public-board (= (:access drafts-board) "public")
                                              :private-board (= (:access drafts-board) "private")
                                              :team-board (= (:access drafts-board) "team")})}
                    [:div.internal
                      {:key (str "board-list-" (name (:slug drafts-board)) "-internal")
                       :dangerouslySetInnerHTML
                        (utils/emojify
                         (str
                          (or (:name drafts-board) (:slug drafts-board))
                          " (" (:count drafts-link) ")"))}]]]))])]
      [:div.left-navigation-sidebar-footer
        {:style {:position (if is-tall-enough? "absolute" "relative")}}
        (when show-invite-people
          [:button.mlb-reset.invite-people-btn
            {:on-click #(dis/dispatch! [:org-settings-show :invite])}
            [:div.invite-people-icon]
            [:span "Add teammates"]])
        [:button.mlb-reset.about-carrot-btn
          {:on-click #(dis/dispatch! [:whats-new-modal-show])}
          [:div.about-carrot-icon]
          [:span "About Carrot"]]]]))