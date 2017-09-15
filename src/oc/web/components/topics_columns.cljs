(ns oc.web.components.topics-columns
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [rum.core :as rum]
            [cuerdas.core :as s]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.responsive :as responsive]
            [oc.web.lib.utils :as utils]
            [oc.web.components.navigation-sidebar :refer (navigation-sidebar)]
            [oc.web.components.ui.filters-dropdown :refer (filters-dropdown)]
            [oc.web.components.ui.empty-board :refer (empty-board)]
            [oc.web.components.activity-card :refer (activity-card)]
            [oc.web.components.entries-layout :refer (entries-layout)]
            [oc.web.components.drafts-layout :refer (drafts-layout)]
            [oc.web.components.stories-layout :refer (stories-layout)]
            [oc.web.components.all-activity :refer (all-activity)]
            [oc.web.components.ui.dropdown-list :refer (dropdown-list)]))

(defn did-select-storyboard-cb [storyboard]
  (dis/dispatch! [:story-create (clojure.set/rename-keys storyboard {:value :slug :label :name :links :links})]))

(defcomponent topics-columns [{:keys [columns-num
                                      content-loaded
                                      total-width
                                      board-data
                                      all-activity-data
                                      is-dashboard
                                      is-all-activity
                                      is-stakeholder-update
                                      board-filters] :as data} owner options]

  (did-mount [_]
    (when-not (utils/is-test-env?)
      (.tooltip (js/$ "[data-toggle=\"tooltip\"]"))
      (when is-all-activity
        (dis/dispatch! [:calendar-get]))))

  (render-state [_ {:keys [show-storyboards-dropdown]}]
    (let [current-activity-id (router/current-activity-id)
          is-mobile-size? (responsive/is-mobile-size?)
          columns-container-key (if current-activity-id
                                  (str "topics-columns-selected-topic-" current-activity-id)
                                  (s/join "-" (map :slug (:topics board-data))))
          topics-column-conatiner-style (if is-dashboard
                                          (if (responsive/window-exceeds-breakpoint)
                                            #js {:width total-width}
                                            #js {:margin "0px 9px"
                                                 :width "auto"})
                                          (if is-mobile-size?
                                            #js {:margin "0px 9px"
                                                 :width "auto"}
                                            #js {:width total-width}))
          total-width-int (js/parseInt total-width 10)
          empty-board? (zero? (count (:fixed-items board-data)))
          org-data (dis/org-data)]
      ;; Topic list
      (dom/div {:class (utils/class-set {:topics-columns true
                                         :overflow-visible true
                                         :group true
                                         :content-loaded content-loaded})}
        (cond
          ;; render 2 or 3 column layout
          (> columns-num 1)
          (dom/div {:class (utils/class-set {:topics-column-container true
                                             :group true
                                             :tot-col-3 (and is-dashboard
                                                             (= columns-num 3))
                                             :tot-col-2 (and is-dashboard
                                                             (= columns-num 2))})
                    :style topics-column-conatiner-style
                    :key columns-container-key}
            (when-not (responsive/is-mobile-size?)
              (navigation-sidebar))
            (dom/div {:class "board-container right"
                      :style {:width (str (- total-width-int responsive/left-navigation-sidebar-width responsive/topic-list-right-margin (* 2 responsive/topic-list-x-padding)) "px")}}
              ;; Board name row: board name, settings button and say something button
              (dom/div {:class "group"}
                ;; Board name and settings button
                (dom/div {:class "board-name"}
                  (if is-all-activity
                    (dom/div {:class "all-activity-icon"})
                    (if (= (:type board-data) "story")
                      (dom/div {:class "stories-icon"})
                      (dom/div {:class "boards-icon"})))
                  (if is-all-activity
                    "All Activity"
                    (:name board-data))
                  ;; Settings button
                  (when (and (router/current-board-slug)
                             (not (:read-only board-data)))
                    (dom/button {:class "mlb-reset board-settings-bt"
                                 :data-toggle "tooltip"
                                 :data-placement "top"
                                 :data-container "body"
                                 :title (str (:name board-data) " settings")
                                 :on-click #(dis/dispatch! [:board-edit board-data])})))
                ;; Add entry button
                (when (and (not is-all-activity)
                           (not (:read-only org-data))
                           (not (responsive/is-tablet-or-mobile?))
                           (= (:type board-data) "entry"))
                  (dom/button {:class "mlb-reset mlb-default add-to-board-btn"
                               :on-click #(dis/dispatch! [:entry-edit {}])}
                    (dom/div {:class "add-to-board-pencil"})
                    "New"))
                (let [;; All the boards that are of story type, that are not drafts and that are not read-only
                      storyboards (filter #(and (= (:type %) "story") (not= (:slug %) "drafts") (utils/link-for (:links %) "create")) (:boards org-data))
                      ;; Select only the needed keys
                      storyboards-list (map #(select-keys % [:name :slug :links]) storyboards)
                      ;; Rename the keys for the dropdown
                      fixed-storyboards (vec (map #(clojure.set/rename-keys % {:name :label :slug :value :links :links}) storyboards-list))]
                  (when (or (not (:read-only board-data))
                            (and (= (:slug board-data) "drafts")
                                 (pos? (count storyboards))))
                    (dom/div {:class "new-story-container"}
                      (when (and (not is-all-activity)
                                 (not (responsive/is-tablet-or-mobile?))
                                 (= (:type board-data) "story")
                                 (or (utils/link-for (:links board-data) "create")
                                     (= (:slug board-data) "drafts")))
                        (dom/button {:class (str "mlb-reset mlb-default add-to-board-btn" (when (= (:slug board-data) "drafts") " is-draft"))
                                     :on-click #(if (= (router/current-board-slug) "drafts")
                                                  (if (= (count fixed-storyboards) 1)
                                                    (dis/dispatch! [:story-create (first storyboards)])
                                                    (om/set-state! owner :show-storyboards-dropdown (not show-storyboards-dropdown)))
                                                  (dis/dispatch! [:story-create board-data]))}
                          (dom/div {:class "add-to-board-pencil"})
                          "New"))
                      (when show-storyboards-dropdown
                        (dropdown-list fixed-storyboards nil did-select-storyboard-cb #(om/set-state! owner :show-storyboards-dropdown false))))))
                ;; Board filters dropdown
                (when (and (not is-mobile-size?)
                           (not empty-board?)
                           (= (:type board-data) "entry"))
                  (filters-dropdown)))
              ;; Board content: empty board, add topic, topic view or topic cards
              (cond
                (and is-dashboard
                     is-all-activity)
                (rum/with-key (all-activity all-activity-data) (str "all-activity-" (apply str (keys (:fixed-items all-activity-data)))))
                (and is-dashboard
                     (not is-mobile-size?)
                     (not current-activity-id)
                     empty-board?)
                (empty-board)
                ; for each column key contained in best layout
                :else
                (cond
                  ;; Drafts
                  (and (= (:type board-data) "story")
                       (= (:slug board-data) "drafts"))
                  (drafts-layout board-data)
                  ;; Stories
                  (= (:type board-data) "story")
                  (stories-layout board-data)
                  ;; Entries
                  :else
                  (entries-layout board-data board-filters)))))
          ;; 1 column or default
          :else
          (dom/div {:class "topics-column-container columns-1 group"
                    :style topics-column-conatiner-style
                    :key columns-container-key}
            (dom/div {:class "topics-column"}
              (for [activity-data (if (= (:type board-data) "story") (:stories board-data) (:entries board-data))]
                (activity-card activity-data)))))))))