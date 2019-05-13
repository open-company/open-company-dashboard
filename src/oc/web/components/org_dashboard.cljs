(ns oc.web.components.org-dashboard
  (:require-macros [if-let.core :refer (when-let*)])
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [oc.web.lib.jwt :as jwt]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.mixins.ui :as ui-mixins]
            [oc.web.stores.search :as search]
            [oc.web.components.qsg :refer (qsg)]
            [oc.web.lib.whats-new :as whats-new]
            [oc.web.lib.responsive :as responsive]
            [oc.web.components.ui.wrt :refer (wrt)]
            [oc.web.components.cmail :refer (cmail)]
            [oc.web.components.ui.menu :refer (menu)]
            [oc.web.actions.section :as section-actions]
            [oc.web.actions.nav-sidebar :as nav-actions]
            [oc.web.components.ui.navbar :refer (navbar)]
            [oc.web.components.search :refer (search-box)]
            [oc.web.actions.activity :as activity-actions]
            [oc.web.components.ui.loading :refer (loading)]
            [oc.web.components.post-modal :refer (post-modal)]
            [oc.web.components.org-settings :refer (org-settings)]
            [oc.web.components.ui.alert-modal :refer (alert-modal)]
            [oc.web.components.ui.shared-misc :refer (video-lightbox)]
            [oc.web.components.ui.section-editor :refer (section-editor)]
            [oc.web.components.ui.activity-share :refer (activity-share)]
            [oc.web.components.dashboard-layout :refer (dashboard-layout)]
            [oc.web.components.qsg-digest-sample :refer (qsg-digest-sample)]
            [oc.web.components.ui.activity-removed :refer (activity-removed)]
            [oc.web.components.user-profile-modal :refer (user-profile-modal)]
            [oc.web.components.org-settings-modal :refer (org-settings-modal)]
            [oc.web.components.navigation-sidebar :refer (navigation-sidebar)]
            [oc.web.components.user-notifications :refer (user-notifications)]
            [oc.web.components.ui.login-overlay :refer (login-overlays-handler)]
            [oc.web.components.ui.activity-not-found :refer (activity-not-found)]
            [oc.web.components.invite-settings-modal :refer (invite-settings-modal)]
            [oc.web.components.team-management-modal :refer (team-management-modal)]
            [oc.web.components.recurring-updates-modal :refer (recurring-updates-modal)]
            [oc.web.components.ui.made-with-carrot-modal :refer (made-with-carrot-modal)]
            [oc.web.components.user-notifications-modal :refer (user-notifications-modal)]
            [oc.web.components.edit-recurring-update-modal :refer (edit-recurring-update-modal)]
            [oc.web.components.integrations-settings-modal :refer (integrations-settings-modal)]))

(defn refresh-board-data [s]
  (when (and (not (router/current-activity-id))
             (router/current-board-slug))
    (utils/after 100 (fn []
     (let [{:keys [org-data
                   board-data
                   ap-initial-at]} @(drv/get-ref s :org-dashboard-data)]
       (cond

        (= (router/current-board-slug) "all-posts")
        (activity-actions/all-posts-get org-data ap-initial-at)

        (= (router/current-board-slug) "must-see")
        (activity-actions/must-see-get org-data)

        :default
        (when-let* [fixed-board-data (or board-data
                     (some #(when (= (:slug %) (router/current-board-slug)) %) (:boards org-data)))
                    board-link (utils/link-for (:links fixed-board-data) ["item" "self"] "GET")]
          (section-actions/section-get board-link))))))))

(rum/defcs org-dashboard < ;; Mixins
                           rum/static
                           rum/reactive
                           (ui-mixins/render-on-resize nil)
                           ;; Derivatives
                           (drv/drv :org-dashboard-data)
                           (drv/drv search/search-key)
                           (drv/drv search/search-active?)
                           (drv/drv :qsg)

                           {:did-mount (fn [s]
                             (utils/after 100 #(set! (.-scrollTop (.-body js/document)) 0))
                             (refresh-board-data s)
                             (whats-new/init)
                             s)
                            :did-remount (fn [s]
                             (whats-new/init)
                             s)}
  [s]
  (let [{:keys [orgs
                org-data
                jwt
                board-data
                container-data
                posts-data
                ap-initial-at
                user-settings
                org-settings-data
                show-reminders
                made-with-carrot-modal-data
                is-sharing-activity
                entry-edit-dissmissing
                is-showing-alert
                media-input
                show-section-editor
                show-section-add
                show-section-add-cb
                entry-editing-board-slug
                mobile-navigation-sidebar
                activity-share-container
                expanded-user-menu
                show-cmail
                showing-mobile-user-notifications
                wrt-activity-data
                wrt-read-data
                force-login-wall]} (drv/react s :org-dashboard-data)
        is-mobile? (responsive/is-tablet-or-mobile?)
        search-active? (drv/react s search/search-active?)
        search-results? (pos?
                         (count
                          (:results (drv/react s search/search-key))))
        loading? (or ;; the org data are not loaded yet
                     (not org-data)
                     ;; No board specified
                     (and (not (router/current-board-slug))
                          ;; but there are some
                          (pos? (count (:boards org-data))))
                     ;; Board specified
                     (and (not= (router/current-board-slug) "all-posts")
                          (not= (router/current-board-slug) "must-see")
                          (not ap-initial-at)
                          ;; But no board data yet
                          (not board-data))
                     ;; Another container
                     (and (or (= (router/current-board-slug) "all-posts")
                              (= (router/current-board-slug) "must-see")
                              ap-initial-at)
                          ;; But no all-posts data yet
                         (not container-data)))
        org-not-found (and (not (nil? orgs))
                           (not ((set (map :slug orgs)) (router/current-org-slug))))
        section-not-found (and (not org-not-found)
                               org-data
                               (not= (router/current-board-slug) "all-posts")
                               (not= (router/current-board-slug) "must-see")
                               (not ((set (map :slug (:boards org-data))) (router/current-board-slug))))
        entry-not-found (and (not section-not-found)
                             (or (and (router/current-activity-id)
                                      board-data)
                                 (and ap-initial-at
                                      (not (jwt/user-is-part-of-the-team (:team-id org-data))))
                                 (and ap-initial-at
                                      container-data))
                             (not (nil? posts-data))
                             (or (and (router/current-activity-id)
                                      (not ((set (keys posts-data)) (router/current-activity-id))))
                                 (and ap-initial-at
                                      (not ((set (map :published-at (vals posts-data))) ap-initial-at)))))
        show-activity-not-found (and (not jwt)
                                     (or force-login-wall
                                         (and (router/current-activity-id)
                                              (or org-not-found
                                                  section-not-found
                                                  entry-not-found))))
        show-activity-removed (and jwt
                                   (or (router/current-activity-id)
                                       ap-initial-at)
                                   (or org-not-found
                                       section-not-found
                                       entry-not-found))
        is-loading (and (not show-activity-not-found)
                        (not show-activity-removed)
                        loading?)
        is-showing-mobile-search (and is-mobile? search-active?)
        qsg-data (drv/react s :qsg)]
    ;; Show loading if
    (if is-loading
      [:div.org-dashboard
        (loading {:loading true})]
      [:div
        {:class (utils/class-set {:org-dashboard true
                                  :mobile-or-tablet is-mobile?
                                  :activity-not-found show-activity-not-found
                                  :activity-removed show-activity-removed
                                  :showing-qsg (:visible qsg-data)
                                  :showing-digest-sample (:qsg-show-sample-digest-view qsg-data)
                                  :show-menu expanded-user-menu})}
        ;; Use cond for the next components to exclud each other and avoid rendering all of them
        (login-overlays-handler)
        (cond
          ;; User menu
          (and is-mobile?
               expanded-user-menu)
          (menu)
          ;; Activity removed
          show-activity-removed
          (activity-removed)
          ;; Activity not found
          show-activity-not-found
          (activity-not-found)
          ;; Org settings
          (and org-settings-data (= org-settings-data :main))
          (org-settings-modal)
          ;; Integrations settings
          (and org-settings-data (= org-settings-data :integrations))
          (integrations-settings-modal)
          ;; Invite settings
          (and org-settings-data (= org-settings-data :invite))
          (invite-settings-modal)
          ;; Team management
          (and org-settings-data (= org-settings-data :team))
          (team-management-modal)
          ;; Billing
          org-settings-data
          (org-settings)
          ;; User settings
          (and user-settings (= user-settings :profile))
          (user-profile-modal)
          ;; User notifications
          (and user-settings (= user-settings :notifications))
          (user-notifications-modal)
          ;; Reminders list
          (and show-reminders (= show-reminders :reminders))
          (recurring-updates-modal)
          ;; Edit a reminder
          show-reminders
          (edit-recurring-update-modal)
          ;; Made with carrot modal
          made-with-carrot-modal-data
          (made-with-carrot-modal)
          ;; Mobile create a new section
          show-section-editor
          (section-editor board-data
           (fn [sec-data note]
            (if sec-data
              (section-actions/section-save sec-data note #(dis/dispatch! [:input [:show-section-editor] false]))
              (dis/dispatch! [:input [:show-section-editor] false]))))
          ;; Mobile edit current section data
          show-section-add
          (section-editor nil show-section-add-cb)
          ;; Activity share for mobile
          (and is-mobile?
               is-sharing-activity)
          (activity-share)
          ;; Mobile WRT
          (and is-mobile?
               wrt-activity-data)
          (wrt wrt-activity-data wrt-read-data)
          ;; Search results
          is-showing-mobile-search
          (search-box)
          ;; Mobile notifications
          (and is-mobile?
               showing-mobile-user-notifications)
          (user-notifications)
          ;; Show mobile navigation
          (and is-mobile?
               mobile-navigation-sidebar)
          (navigation-sidebar))
        ;; Activity share modal for no mobile
        (when (and (not is-mobile?)
                   is-sharing-activity)
          ;; If we have an element id find the share container inside that element
          ;; and portal the component to that element
          (let [portal-element (when activity-share-container
                                  (.get (.find (js/$ (str "#" activity-share-container))
                                         ".activity-share-container") 0))]
            (if portal-element
              (rum/portal (activity-share) portal-element)
              (activity-share))))
        ;; cmail editor
        (when show-cmail
          (cmail))
        (when-not is-mobile?
          (menu))
        ;; Alert modal
        (when is-showing-alert
          (alert-modal))
        (when (or (not is-mobile?)
                  (and ; (router/current-activity-id)
                       (not is-sharing-activity)
                       (not show-section-add)
                       (not show-section-editor)
                       (not show-cmail)
                       (not wrt-activity-data)
                       (not show-reminders)))
          [:div.page
            (navbar)
            [:div.org-dashboard-container
              [:div.org-dashboard-inner
               (when (or (not is-mobile?)
                         (and (or (not search-active?) (not search-results?))
                              (not mobile-navigation-sidebar)
                              (not org-settings-data)
                              (not user-settings)
                              (not expanded-user-menu)
                              (not is-showing-mobile-search)
                              (not showing-mobile-user-notifications)))
                 (dashboard-layout))]]
            (when (:qsg-show-sample-digest-view qsg-data)
              (qsg-digest-sample))])
        (when (:visible qsg-data)
          [:div.qsg-main-container
            (qsg)
            (video-lightbox)])])))