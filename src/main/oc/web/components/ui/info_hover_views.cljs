(ns oc.web.components.ui.info-hover-views
  (:require-macros [if-let.core :refer (when-let*)])
  (:require [rum.core :as rum]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.urls :as oc-urls]
            [oc.web.lib.utils :as utils]
            [oc.web.utils.dom :as dom-utils]
            [oc.web.utils.user :as user-utils]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.user :as user-actions]
            [oc.web.actions.nav-sidebar :as nav-actions]
            [oc.web.components.ui.follow-button :refer (follow-button)]
            [oc.web.components.ui.user-avatar :refer (user-avatar-image)]))

(rum/defc board-info-view < rum/static
  [{:keys [activity-data above? following followers-count]}]
  [:div.board-info-view
    {:class (utils/class-set {:above above?})}
    [:div.board-info-header
      [:div.board-info-right
        [:div.board-info-name
          (:board-name activity-data)]
        [:div.board-info-subline
          (when (pos? followers-count)
            (str followers-count " follower" (when (not= followers-count 1) "s")))]]]
    [:div.board-info-buttons.group
      [:button.mlb-reset.posts-bt
        {:on-click #(do
                      (utils/event-stop %)
                      (nav-actions/nav-to-url! % (:board-slug activity-data) (oc-urls/board (:board-slug activity-data))))}
        "Posts"]
      (follow-button {:following following
                      :resource-type :board
                      :resource-uuid (:board-uuid activity-data)})]])

(rum/defc user-info-view < rum/static
  [{:keys [user-data user-id my-profile? hide-buttons otf above? inline? following followers-count hide-last-name? short-name?]}]
  (let [timezone-location-string (user-utils/timezone-location-string user-data)]
    [:div.user-info-view
      {:class (utils/class-set {:otf otf
                                :inline inline?
                                :above above?})}
      [:div.user-info-header
        (user-avatar-image user-data {:preferred-avatar-size 96})
        [:div.user-info-right
          [:div.user-info-name
            (cond (and hide-last-name?
                       (seq (:first-name user-data)))
                  (:first-name user-data)
                  (and short-name?
                       (seq (:pointed-name user-data)))
                  (:pointed-name user-data)
                  :else
                  (:name user-data))]
          (when (seq (:title user-data))
            [:div.user-info-line
              (:title user-data)])
          (cond
            (seq timezone-location-string)
            [:div.user-info-subline
              timezone-location-string]
            (seq (:slack-username user-data))
            [:div.user-info-subline.slack-icon
              (:slack-username user-data)]
            (seq (:email user-data))
            [:div.user-info-subline
              (:email user-data)])
          (when (pos? followers-count)
            [:div.user-info-subline
              (str followers-count " follower" (when (not= followers-count 1) "s"))])
          (when-not hide-buttons
            [:button.mlb-reset.profile-bt
              {:on-click #(nav-actions/nav-to-author! % (:user-id user-data) (oc-urls/contributions (:user-id user-data)))}
              "View profile and posts"])]]
      ; (when-not hide-buttons
      ;   [:div.user-info-buttons.group
      ;     [:button.mlb-reset.posts-bt
      ;       {:on-click #(nav-actions/nav-to-author! % (:user-id user-data) (oc-urls/contributions (:user-id user-data)))}
      ;       "Posts"]
      ;     [:button.mlb-reset.profile-bt
      ;       {:on-click #(nav-actions/show-user-info (:user-id user-data))}
      ;       "Profile"]
      ;     ; (when-not my-profile?
      ;     ;   (follow-button {:following following :resource-type :user :resource-uuid (:user-id user-data)}))
      ;     ])
      ]))

(rum/defc user-info-otf < rum/static
  [{:keys [portal-el] :as props}]
  (when (and (not (responsive/is-mobile-size?))
             portal-el
             (.-parentElement portal-el))
    (let [viewport-size (dom-utils/viewport-size)
          pos (dom-utils/viewport-offset portal-el)
          above? (>= (:y pos) (/ (:height viewport-size) 2))
          next-props (merge props {:above? above?
                                   :otf true})]
      (rum/portal (user-info-view (assoc next-props :above? above?)) portal-el))))

(def ^:private default-positioning {:vertical-position nil :horizontal-position nil})

(def ^:private popup-size
  {:width 200
   :height 211})

(def ^:private padding 16)

(def ^:private popup-offset
  {:x padding
   :y (+ padding responsive/navbar-height)})

(defn- check-hover [s parent-el]
  (let [pos (dom-utils/viewport-offset parent-el)
        vertical-position (if (> (- (:y pos) (:y popup-offset)) (:height popup-size))
                            :above
                            :below)
        horizontal-offset (if (> (:x pos) (:x popup-offset))
                            0
                            (if (neg? (:x pos))
                              (+ (* (:x pos) -1) (:x popup-offset))
                              (- (:x popup-offset) (:x pos))))]
    {:vertical-position vertical-position
     :horizontal-offset horizontal-offset}))

(defn- enter! [s parent-el]
  (reset! (::enter-timeout s) nil)
  (when (compare-and-set! (::hovering s) false true)
    (reset! (::positioning s) (check-hover s parent-el))))

(defn- leave! [s]
  (when (compare-and-set! (::hovering s) true false)
    (reset! (::positioning s) default-positioning)))

(defn- enter-ev [s parent-el]
  (when-not (-> s :rum/args first :disabled)
    (.clearTimeout js/window @(::enter-timeout s))
    (.clearTimeout js/window @(::leave-timeout s))
    (reset! (::enter-timeout s) (.setTimeout js/window #(enter! s parent-el) 500))))

(defn- leave-ev [s]
  (when-not (-> s :rum/args first :disabled)
    (.clearTimeout js/window @(::enter-timeout s))
    (.clearTimeout js/window @(::leave-timeout s))
    (if (-> s :rum/args first :leave-delay?)
      (reset! (::leave-timeout s) (.setTimeout js/window #(leave! s) 500))
      (leave! s))))

(defn- click [s e]
  (let [user-id (-> s :rum/args first :user-data :user-id)]
    (nav-actions/nav-to-author! e user-id (oc-urls/contributions user-id))))

(rum/defcs user-info-hover <
  rum/static
  rum/reactive
  (drv/drv :users-info-hover)
  (drv/drv :follow-publishers-list)
  (drv/drv :followers-publishers-count)
  (rum/local nil ::mouse-enter)
  (rum/local nil ::mouse-leave)
  (rum/local nil ::click)
  (rum/local default-positioning ::positioning)
  (rum/local false ::hovering)
  (rum/local nil ::enter-timeout)
  (rum/local nil ::leave-timeout)
  {:did-mount (fn [s]
   (when-let* [el (rum/dom-node s)
               parent-el (.-parentElement el)]
    (if (responsive/is-mobile-size?)
      (reset! (::click s) (events/listen parent-el EventType/CLICK
       (partial click s)))
      (do
        (reset! (::mouse-enter s) (events/listen parent-el EventType/MOUSEENTER
         #(enter-ev s parent-el)))
        (reset! (::mouse-leave s) (events/listen parent-el EventType/MOUSELEAVE
         #(leave-ev s))))))
   s)
   :will-unmount (fn [s]
    (when @(::mouse-enter s)
      (events/unlistenByKey @(::mouse-enter s))
      (reset! (::mouse-enter s) nil))
    (when @(::mouse-leave s)
      (events/unlistenByKey @(::mouse-leave s))
      (reset! (::mouse-leave s) nil))
    (when @(::click s)
      (events/unlistenByKey @(::click s))
      (reset! (::click s) nil))
    s)}
  [s {:keys [disabled user-data current-user-id leave-delay? hide-last-name? short-name?]}]
  ;; Return an empty DOM for mobile since we don't show the hover popup
  (if (responsive/is-mobile-size?)
    [:div.info-hover-view]
    (let [my-profile? (= (:user-id user-data) current-user-id)
          pos @(::positioning s)
          users-info (drv/react s :users-info-hover)
          active-user-data (get users-info (:user-id user-data))
          complete-user-data (merge user-data active-user-data)
          follow-publishers-list (set (map :user-id (drv/react s :follow-publishers-list)))
          following? (follow-publishers-list (:user-id user-data))
          followers-publishers-count (drv/react s :followers-publishers-count)
          followers-count (get followers-publishers-count (:user-id user-data))]
      [:div.info-hover-view
        {:class (utils/class-set {:show @(::hovering s)
                                  (:vertical-position pos) true})
         :on-click #(when-not (utils/button-clicked? %)
                      (utils/event-stop %))
         :style {:margin-left (str (:horizontal-offset pos) "px")}}
        (user-info-view {:user-data complete-user-data
                         :inline? (not active-user-data)
                         :hide-buttons (not active-user-data)
                         :hide-last-name? hide-last-name?
                         :short-name? short-name?
                         :my-profile? my-profile?
                         :following following?
                         :followers-count (:count followers-count)})])))

(rum/defcs board-info-hover <
  rum/static
  rum/reactive
  (drv/drv :org-data)
  (drv/drv :follow-boards-list)
  (drv/drv :followers-boards-count)
  (rum/local nil ::mouse-enter)
  (rum/local nil ::mouse-leave)
  (rum/local nil ::click)
  (rum/local default-positioning ::positioning)
  (rum/local false ::hovering)
  (rum/local nil ::enter-timeout)
  (rum/local nil ::leave-timeout)
  {:did-mount (fn [s]
   (when-let* [el (rum/dom-node s)
               parent-el (.-parentElement el)]
    (if (responsive/is-mobile-size?)
      (reset! (::click s) (events/listen parent-el EventType/CLICK
       (partial click s)))
      (do
        (reset! (::mouse-enter s) (events/listen parent-el EventType/MOUSEENTER
         #(enter-ev s parent-el)))
        (reset! (::mouse-leave s) (events/listen parent-el EventType/MOUSELEAVE
         #(leave-ev s))))))
   s)
   :will-unmount (fn [s]
    (when @(::mouse-enter s)
      (events/unlistenByKey @(::mouse-enter s))
      (reset! (::mouse-enter s) nil))
    (when @(::mouse-leave s)
      (events/unlistenByKey @(::mouse-leave s))
      (reset! (::mouse-leave s) nil))
    (when @(::click s)
      (events/unlistenByKey @(::click s))
      (reset! (::click s) nil))
    s)}
  [s {:keys [disabled activity-data leave-delay?]}]
  ;; Return an empty DOM for mobile since we don't show the hover popup
  (if (responsive/is-mobile-size?)
    [:div.info-hover-view]
    (let [pos @(::positioning s)
          follow-boards-list (set (map :uuid (drv/react s :follow-boards-list)))
          following? (follow-boards-list (:board-uuid activity-data))
          followers-boards-count (drv/react s :followers-boards-count)
          followers-count (get followers-boards-count (:board-uuid activity-data))]
      [:div.info-hover-view
        {:class (utils/class-set {:show @(::hovering s)
                                  (:vertical-position pos) true})
         :on-click #(when-not (utils/button-clicked? %)
                      (utils/event-stop %))
         :style {:margin-left (str (:horizontal-offset pos) "px")}}
        (board-info-view {:activity-data activity-data
                          :following following?
                          :followers-count (:count followers-count)})])))
