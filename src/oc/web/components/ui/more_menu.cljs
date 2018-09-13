(ns oc.web.components.ui.more-menu
  (:require-macros [if-let.core :refer (when-let*)])
  (:require [rum.core :as rum]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.activity :as activity-actions]
            [oc.web.components.ui.alert-modal :as alert-modal]
            [oc.web.components.ui.activity-move :refer (activity-move)]))

(defn show-hide-menu
  [s will-open will-close]
  (utils/remove-tooltips)
  (let [next-showing-menu (not @(::showing-menu s))]
    (if next-showing-menu
      (when (fn? will-open)
        (will-open))
      (when (fn? will-close)
        (will-close)))
    (reset! (::showing-menu s) next-showing-menu)))

;; Delete handling

(defn delete-clicked [e activity-data]
  (let [alert-data {:action "delete-entry"
                    :title "Delete this post?"
                    :message "This action cannot be undone."
                    :link-button-style :green
                    :link-button-title "No, keep post"
                    :link-button-cb #(alert-modal/hide-alert)
                    :solid-button-style :red
                    :solid-button-title "Yes, delete post"
                    :solid-button-cb #(let [org-slug (router/current-org-slug)
                                            board-slug (router/current-board-slug)
                                            board-url (oc-urls/board org-slug board-slug)]
                                       (router/nav! board-url)
                                       (activity-actions/activity-delete activity-data)
                                       (alert-modal/hide-alert))
                    :bottom-button-title (when (and (:sample activity-data)
                                                    (activity-actions/has-sample-posts))
                                           "Delete all sample posts")
                    :bottom-button-style :red
                    :bottom-button-cb #(do
                                         (activity-actions/delete-all-sample-posts)
                                         (alert-modal/hide-alert))
                    }]
    (alert-modal/show-alert alert-data)))

(rum/defcs more-menu < rum/reactive
                       (rum/local false ::showing-menu)
                       (rum/local false ::move-activity)
                       (rum/local nil ::click-listener)
                       (drv/drv :editable-boards)
                       {:will-mount (fn [s]
                         (reset! (::click-listener s)
                           (events/listen js/window EventType/CLICK
                            #(when-not (utils/event-inside? % (rum/ref-node s "more-menu"))
                               (when-let* [args (into [] (:rum/args s))
                                           opts (get args 2)
                                           will-close (:will-close opts)]
                                 (when (fn? will-close)
                                   (will-close)))
                               (reset! (::showing-menu s) false))))
                         s)
                        :did-mount (fn [s]
                         (.tooltip (js/$ "[data-toggle=\"tooltip\"]"))
                         s)
                        :will-unmount (fn [s]
                         (when @(::click-listener s)
                           (events/unlistenByKey @(::click-listener s))
                           (reset! (::click-listener s) nil))
                         s)}
  [s activity-data share-container-id
   {:keys [will-open will-close external-share tooltip-position]}]
  (let [delete-link (utils/link-for (:links activity-data) "delete")
        edit-link (utils/link-for (:links activity-data) "partial-update")
        share-link (utils/link-for (:links activity-data) "share")
        editable-boards (drv/react s :editable-boards)]
    (when (or edit-link
              share-link
              delete-link)
      [:div.more-menu
        {:ref "more-menu"}
        (when (or edit-link
                  delete-link
                  (and (not external-share)
                       share-link))
          [:button.mlb-reset.more-menu-bt
            {:type "button"
             :ref "more-menu-bt"
             :on-click #(show-hide-menu s will-open will-close)
             :class (when @(::showing-menu s) "active")
             :data-toggle (if (responsive/is-tablet-or-mobile?) "" "tooltip")
             :data-placement (or tooltip-position "top")
             :data-container "body"
             :data-delay "{\"show\":\"500\", \"hide\":\"0\"}"
             :title "More"}])
        (cond
          @(::move-activity s)
          (activity-move {:boards-list (vals editable-boards)
                          :activity-data activity-data
                          :dismiss-cb #(reset! (::move-activity s) false)})
          @(::showing-menu s)
          [:ul.more-menu-list
            (when edit-link
              [:li.edit
                {:on-click #(do
                              (utils/event-stop %)
                              (reset! (::showing-menu s) false)
                              (when (fn? will-close)
                                (will-close))
                              (activity-actions/activity-edit activity-data))}
                "Edit"])
            (when delete-link
              [:li.delete
                {:on-click #(do
                              (utils/event-stop %)
                              (reset! (::showing-menu s) false)
                              (when (fn? will-close)
                                (will-close))
                              (delete-clicked % activity-data))}
                "Delete"])
            (when edit-link
              [:li.move
               {:on-click #(do
                             (utils/event-stop %)
                             (reset! (::showing-menu s) false)
                             (reset! (::move-activity s) true))}
               "Move"])
            (when (and (not external-share)
                       share-link)
              [:li.share
                {:on-click #(do
                              (utils/event-stop %)
                              (reset! (::showing-menu s) false)
                              (when (fn? will-close)
                                (will-close))
                              (activity-actions/activity-share-show activity-data share-container-id))}
                "Share"])
            (when edit-link
              [:li
               {:class (utils/class-set
                         {:must-see (not (:must-see activity-data))
                          :must-see-on (:must-see activity-data)})
                :on-click #(do
                             (utils/event-stop %)
                             (activity-actions/toggle-must-see activity-data))}
               (if (:must-see activity-data)
                 "Unmark"
                 "Must see")])])
        (when (and external-share
                   share-link
                   (not (responsive/is-tablet-or-mobile?)))
          [:button.mlb-reset.more-menu-share-bt
            {:type "button"
             :ref "tile-menu-share-bt"
             :on-click #(do
                          (reset! (::showing-menu s) false)
                          (when (fn? will-close)
                            (will-close))
                          (activity-actions/activity-share-show activity-data share-container-id))
             :data-toggle "tooltip"
             :data-placement (or tooltip-position "top")
             :data-delay "{\"show\":\"500\", \"hide\":\"0\"}"
             :title "Share"}])])))