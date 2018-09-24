(ns oc.web.components.ui.empty-board
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.mixins.section :as section-mixins]
            [oc.web.actions.activity :as activity-actions]))

(def mobile-image-size
 {:width 250
  :height 211
  :ratio (/ 250 211)})

(rum/defcs empty-board < rum/reactive
                         (drv/drv :board-data)
                         section-mixins/container-nav-in
  [s edit-board]
  (let [board-data (drv/react s :board-data)
        is-all-posts? (= (router/current-board-slug) "all-posts")
        is-must-see? (= (router/current-board-slug) "must-see")
        is-drafts-board? (= (router/current-board-slug) utils/default-drafts-board-slug)]
    [:div.empty-board.group
      [:div.empty-board-grey-box
        [:div.empty-board-illustration
          {:class (utils/class-set {:ap-section (and (not is-must-see?) (not is-drafts-board?))
                                    :must-see is-must-see?
                                    :drafts is-drafts-board?})}]
        [:div.empty-board-title
          (cond
           is-all-posts? "Stay up to date"
           is-must-see? "Highlight what's important"
           is-drafts-board? "Jot down your ideas and notes"
           :else "This section is empty")]
        [:div.empty-board-subtitle
          (cond
           is-all-posts? "All posts is a stream of what’s new across all sections."
           is-must-see? "When someone marks a post as “must see” everyone will see it here."
           is-drafts-board? "Keep a private draft until you're ready to share it with your team."
           :else (str "Looks like there aren’t any posts in " (:name board-data) "."))]
        (when edit-board
          [:button.mlb-reset.create-new-post-bt
            {:on-click #(activity-actions/activity-edit edit-board)}
            "Create a new post"])]]))