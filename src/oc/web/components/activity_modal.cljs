(ns oc.web.components.activity-modal
  (:require [rum.core :as rum]
            [dommy.core :as dommy :refer-macros (sel1)]
            [org.martinklepsch.derivatives :as drv]
            [cuerdas.core :as string]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.components.ui.media-attachments :refer (media-attachments)]
            [oc.web.components.ui.user-avatar :refer (user-avatar-image)]
            [oc.web.components.reactions :refer (reactions)]
            [oc.web.components.comments :refer (comments)]))

(defn dismiss-modal [board-filters]
  (if (:from-all-activity @router/path)
    (dis/dispatch! [:all-activity-nav])
    (dis/dispatch! [:board-nav (router/current-board-slug) board-filters])))

(defn close-clicked [s & [board-filters]]
  (reset! (::dismiss s) true)
  (utils/after 180 #(dismiss-modal board-filters)))

(defn delete-clicked [e activity-data]
  (utils/event-stop e)
  (let [alert-data {:icon "/img/ML/trash.svg"
                    :message (str "Delete this update?")
                    :link-button-title "No"
                    :link-button-cb #(dis/dispatch! [:alert-modal-hide])
                    :solid-button-title "Yes"
                    :solid-button-cb #(do
                                       (let [org-slug (router/current-org-slug)
                                             board-slug (router/current-board-slug)
                                             last-filter (keyword (cook/get-cookie (router/last-board-filter-cookie org-slug board-slug)))]
                                         (if (= last-filter :by-topic)
                                           (router/nav! (oc-urls/board-sort-by-topic))
                                           (router/nav! (oc-urls/board))))
                                       (dis/dispatch! [:activity-delete activity-data])
                                       (dis/dispatch! [:alert-modal-hide]))
                    }]
    (dis/dispatch! [:alert-modal-show alert-data])))

(rum/defcs activity-modal < (rum/local false ::first-render-done)
                         (rum/local false ::dismiss)
                         (rum/local false ::animate)
                         (rum/local false ::showing-dropdown)
                         (rum/local nil ::column-height)
                         (rum/local nil ::window-resize-listener)
                         (rum/local false ::close-hovering)
                         rum/reactive
                         (drv/drv :activity-modal-fade-in)
                         {:before-render (fn [s]
                                           (when (and (not @(::animate s))
                                                    (= @(drv/get-ref s :activity-modal-fade-in) (:uuid (first (:rum/args s)))))
                                             (reset! (::animate s) true))
                                           s)
                          :will-mount (fn [s]
                                        (reset! (::window-resize-listener s)
                                         (events/listen js/window EventType/RESIZE #(reset! (::column-height s) nil)))
                                        s)
                          :did-mount (fn [s]
                                       ;; Add no-scroll to the body to avoid scrolling while showing this modal
                                       (dommy/add-class! (sel1 [:body]) :no-scroll)
                                       ;; Scroll to the bottom of the comments box
                                       (let [el (sel1 [:div.activity-right-column-content])]
                                          (set! (.-scrollTop el) (.-scrollHeight el)))
                                       (let [activity-data (first (:rum/args s))]
                                        (.on (js/$ (str "div.activity-modal-" (:uuid activity-data)))
                                         "show.bs.dropdown"
                                         (fn [e]
                                           (reset! (::showing-dropdown s) true)))
                                        (.on (js/$ (str "div.activity-modal-" (:uuid activity-data)))
                                         "hidden.bs.dropdown"
                                         (fn [e]
                                           (reset! (::showing-dropdown s) false))))
                                       s)
                          :did-remount (fn [o s]
                                         (reset! (::column-height s) nil)
                                         s)
                          :after-render (fn [s]
                                          (when (not @(::first-render-done s))
                                            (reset! (::first-render-done s) true))
                                          (when-not @(::column-height s)
                                            (reset! (::column-height s) (max 284 (.height (js/$ ".activity-left-column"))))
                                            (.load (js/$ ".activity-modal-content-body img")
                                             #(reset! (::column-height s) (max 284 (.height (js/$ ".activity-left-column"))))))
                                          s)
                          :will-unmount (fn [s]
                                          ;; Remove no-scroll class from the body tag
                                          (dommy/remove-class! (sel1 [:body]) :no-scroll)
                                          ;; Remove window resize listener
                                          (when @(::window-resize-listener s)
                                            (events/unlistenByKey @(::window-resize-listener s))
                                            (reset! (::window-resize-listener s) nil))
                                          s)}
  [s activity-data]
  (let [column-height @(::column-height s)]
    [:div.activity-modal-container
      {:class (utils/class-set {:will-appear (or @(::dismiss s) (and @(::animate s) (not @(::first-render-done s))))
                                :appear (and (not @(::dismiss s)) @(::first-render-done s))})}
      [:div.activity-modal.group
        {:class (str "activity-modal-" (:uuid activity-data))}
        [:button.close-activity-modal.mlb-reset
          {:on-click #(close-clicked s)
           :on-mouse-enter #(reset! (::close-hovering s) true)
           :on-mouse-leave #(reset! (::close-hovering s) false)}
          [:img {:src (utils/cdn (str "/img/ML/board_remove_filter" (when @(::close-hovering s) "_white") ".png")) :width 12 :height 12}]]
        [:div.activity-modal-inner.group
          [:div.activity-left-column
            {:style (when column-height {:minHeight (str column-height "px")})}
            [:div.activity-left-column-content
              {:style (when column-height {:minHeight (str (- column-height 40) "px")})}
              [:div.activity-modal-head.group
                [:div.activity-modal-head-left
                  (user-avatar-image (first (:author activity-data)))
                  [:div.name (:name (first (:author activity-data)))]
                  [:div.time-since
                    [:time
                      {:date-time (:created-at activity-data)
                       :data-toggle "tooltip"
                       :data-placement "top"
                       :title (utils/activity-date-tooltip activity-data)}
                      (utils/time-since (:created-at activity-data))]]]
                [:div.activity-modal-head-right
                  (when (:topic-slug activity-data)
                    (let [topic-name (or (:topic-name activity-data) (string/upper (:topic-slug activity-data)))]
                      [:div.activity-tag
                        {:on-click #(close-clicked s (:topic-slug activity-data))}
                        topic-name]))]]
              [:div.activity-modal-content
                [:div.activity-modal-content-headline
                  {:dangerouslySetInnerHTML (utils/emojify (:headline activity-data))}]
                [:div.activity-modal-content-body
                  {:dangerouslySetInnerHTML (utils/emojify (:body activity-data))
                   :class (when (empty? (:headline activity-data)) "no-headline")}]
                (media-attachments (:attachments activity-data))
                [:div.activity-modal-footer.group
                  (reactions activity-data)
                  [:div.activity-modal-footer-right
                    (when-not (:read-only activity-data)
                      [:div.more-dropdown.dropdown
                        [:button.mlb-reset.activity-modal-more.dropdown-toggle
                          {:type "button"
                           :id (str "activity-modal-more-" (router/current-board-slug) "-" (:uuid activity-data))
                           :data-toggle "dropdown"
                           :aria-haspopup true
                           :aria-expanded false
                           :title "More"}]
                        [:div.dropdown-menu
                          {:aria-labelledby (str "activity-modal-more-" (router/current-board-slug) "-" (:uuid activity-data))}
                          [:div.triangle]
                          [:ul.activity-modal-more-menu
                            [:li
                              {:on-click (fn [e]
                                           (utils/event-stop e)
                                           (dis/dispatch! [:entry-edit activity-data]))}
                              "Edit"]
                            [:li
                              {:on-click #(delete-clicked % activity-data)}
                              "Delete"]]]])]]]]]
          [:div.activity-right-column
            {:style (when column-height {:minHeight (str column-height "px")})}
            [:div.activity-right-column-content
              (comments activity-data)]]]]]))