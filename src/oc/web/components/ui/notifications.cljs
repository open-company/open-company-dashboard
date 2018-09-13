(ns oc.web.components.ui.notifications
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.lib.utils :as utils]
            [oc.web.mixins.ui :as ui-mixins]
            [oc.web.actions.notifications :as notification-actions]))

(defn description-wrapper [desc]
  (cond
   (string? desc)
   {:dangerouslySetInnerHTML #js {"__html" desc}}

   (sequential? desc)
   desc))

(defn button-wrapper [s bt-cb bt-title bt-style bt-dismiss]
  (let [has-html (string? bt-title)
        button-base-map {:on-click (fn [e]
                                     (when bt-dismiss
                                       (notification-actions/remove-notification (first (:rum/args s))))
                                     (when (fn? bt-cb)
                                       (bt-cb e)))
                         :class (utils/class-set {:solid-green (= bt-style :solid-green)
                                                  :default-link (= bt-style :default-link)})}
        button-map (if has-html
                     (assoc button-base-map :dangerouslySetInnerHTML #js {"__html" bt-title})
                     button-base-map)]
    [:button.mlb-reset.notification-button.group
      button-map
      (when-not has-html
        bt-title)]))

(defn setup-timeout [s]
  (when @(::timeout s)
    (js/clearTimeout @(::timeout s))
    (reset! (::timeout s) nil))
  (let [n-data (first (:rum/args s))]
    (reset! (::old-expire s) (:expire n-data))
    (when (pos? (:expire n-data))
      (let [expire (* (:expire n-data) 1000)]
        (reset! (::timeout s)
         (utils/after expire
          #(reset! (::dismiss s) true)))))))

(rum/defcs notification < rum/static
                          ui-mixins/first-render-mixin
                          (rum/local false ::dismiss)
                          (rum/local false ::notification-removed)
                          (rum/local false ::timeout)

                          (rum/local 0 ::old-expire)
                          {:did-mount (fn [s]
                           (setup-timeout s)
                           s)
                           :did-remount (fn [o s]
                            (setup-timeout s)
                            s)
                           :after-render (fn [s]
                            (when @(::dismiss s)
                              (when (compare-and-set! (::notification-removed s) false true)
                                ;; remove notification from list
                                (notification-actions/remove-notification (first (:rum/args s)))))
                            s)}
  [s {:keys [id title description slack-icon opac dismiss-bt server-error dismiss
             primary-bt-cb primary-bt-title primary-bt-style primary-bt-dismiss
             secondary-bt-cb secondary-bt-title secondary-bt-style secondary-bt-dismiss
             app-update slack-bot] :as notification-data}]
  [:div.notification
    {:class (utils/class-set {:server-error server-error
                              :app-update app-update
                              :slack-bot slack-bot
                              :opac opac
                              :dismiss-button dismiss-bt})
     :data-notificationid id}
    [:div.notification-title.group
      (when slack-icon
        [:span.slack-icon])
      title]
    (when dismiss
      [:button.mlb-reset.notification-dismiss-bt
        {:on-click #(do
                      (reset! (::timeout s) nil)
                      (js/clearTimeout @(::timeout s))
                      (notification-actions/remove-notification notification-data)
                      (when (fn? dismiss)
                        (dismiss %)))}])
    (when (seq description)
      [:div.notification-description
        (description-wrapper description)])
    (when (seq secondary-bt-title)
      (button-wrapper s secondary-bt-cb secondary-bt-title secondary-bt-style secondary-bt-dismiss))
    (when (seq primary-bt-title)
      (button-wrapper s primary-bt-cb primary-bt-title primary-bt-style primary-bt-dismiss))])

(rum/defcs notifications < rum/static
                           rum/reactive
                           (drv/drv :notifications-data)
  [s]
  (let [notifications-data (drv/react s :notifications-data)]
    [:div.notifications
      (for [idx (range (count notifications-data))
            :let [n (nth notifications-data idx)]]
        (rum/with-key (notification n) (str "notif-" (:id n))))]))