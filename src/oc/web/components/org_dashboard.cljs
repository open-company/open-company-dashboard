(ns oc.web.components.org-dashboard
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.responsive :as responsive]
            [oc.web.components.ui.navbar :refer (navbar)]
            [oc.web.components.ui.loading :refer (loading)]
            [oc.web.components.entry-edit :refer (entry-edit)]
            [oc.web.components.board-edit :refer (board-edit)]
            [oc.web.components.org-settings :refer (org-settings)]
            [oc.web.components.ui.alert-modal :refer (alert-modal)]
            [oc.web.components.dashboard-layout :refer (dashboard-layout)]
            [oc.web.components.activity-modal :refer (activity-modal)]
            [oc.web.components.ui.onboard-overlay :refer (onboard-overlay)]
            [oc.web.components.ui.media-video-modal :refer (media-video-modal)]
            [oc.web.components.ui.media-chart-modal :refer (media-chart-modal)]
            [oc.web.components.ui.whats-new-modal :refer (whats-new-modal)]
            [oc.web.components.ui.activity-share :refer (activity-share)]
            [oc.web.components.ui.made-with-carrot-modal :refer (made-with-carrot-modal)]))

(defn refresh-board-data []
  (when-not (router/current-activity-id)
    (utils/after 100 (fn []
     (if (= (router/current-board-slug) "all-posts")
       (dis/dispatch! [:all-posts-get])
       (let [board-data (dis/board-data)
             fixed-board-data (or
                                board-data
                                (some #(when (= (:slug %) (router/current-board-slug)) %) (:boards (dis/org-data))))]
         (dis/dispatch! [:board-get (utils/link-for (:links fixed-board-data) ["item" "self"] "GET")])))))))

(defcomponent org-dashboard [data owner]

  (did-mount [_]
    (utils/after 100 #(set! (.-scrollTop (.-body js/document)) 0))
    (refresh-board-data))

  (render-state [_ {:keys [columns-num card-width] :as state}]
    (let [org-slug (keyword (router/current-org-slug))
          org-data (dis/org-data data)
          board-slug (keyword (router/current-board-slug))
          board-data (dis/board-data data)
          all-posts-data (dis/all-posts-data data)]
      ;; Show loading if
      (if (or ;; the org data are not loaded yet
              (not org-data)
              ;; No board specified
              (and (not (router/current-board-slug))
                   ;; but there are some
                   (pos? (count (:boards org-data))))
              ;; Board specified
              (and (router/current-board-slug)
                       ;; But the data are not loaded yet
                   (or (and (not= (router/current-board-slug) "all-posts")
                            (not board-data))
                       ;; Or the all-posts data
                       (and (= (router/current-board-slug) "all-posts")
                            (not all-posts-data)))))
        (dom/div {:class (utils/class-set {:org-dashboard true
                                           :main-scroll true})}
          (om/build loading {:loading true}))
        (dom/div {:class (utils/class-set {:org-dashboard true
                                           :mobile-dashboard (responsive/is-mobile-size?)
                                           :modal-activity-view (router/current-activity-id)
                                           :mobile-or-tablet (responsive/is-tablet-or-mobile?)
                                           :main-scroll true
                                           :no-scroll (router/current-activity-id)})}
          ;; Use cond for the next components to exclud each other and avoid rendering all of them
          (cond
            (:show-onboard-overlay data)
            (onboard-overlay)
            ;; Org settings
            (:org-settings data)
            (org-settings)
            ;; About carrot
            (:whats-new-modal data)
            (whats-new-modal)
            ;; Made with carrot modal
            (:made-with-carrot-modal data)
            (made-with-carrot-modal)
            ;; Entry editing
            (:entry-editing data)
            (entry-edit)
            ;; Board editing
            (:board-editing data)
            (board-edit)
            ;; Activity modal
            (and (router/current-activity-id)
                 (not (:entry-edit-dissmissing data)))
            (let [from-ap (:from-all-posts @router/path)
                  board-slug (if from-ap :all-posts (router/current-board-slug))]
              (activity-modal
               (dis/activity-data
                (router/current-org-slug)
                board-slug
                (router/current-activity-id)
                data))))
          ;; Activity share modal
          (when (:activity-share data)
            (activity-share))
          ;; Alert modal
          (when (:alert-modal data)
            (alert-modal))
          ;; Media video modal for entry editing
          (when (and (:media-input data)
                     (:media-video (:media-input data)))
            (media-video-modal))
          ;; Media chart modal for entry editing
          (when (and (:media-input data)
                     (:media-chart (:media-input data)))
            (media-chart-modal))
          (dom/div {:class "page"}
            ;; Navbar
            (when-not (and (responsive/is-tablet-or-mobile?)
                           (router/current-activity-id))
              (navbar))
            (dom/div {:class "dashboard-container"}
              (dom/div {:class "topic-list"}
                (dashboard-layout)))))))))