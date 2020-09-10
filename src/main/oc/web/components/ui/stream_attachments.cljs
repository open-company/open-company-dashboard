(ns oc.web.components.ui.stream-attachments
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.lib.utils :as utils]
            [oc.web.utils.activity :as au]
            [oc.web.lib.responsive :as responsive]
            [oc.web.components.ui.small-loading :refer (small-loading)]
            [clojure.contrib.humanize :refer (filesize)]
            [goog.events :as events]
            [goog.events.EventType :as EventType]))

(rum/defcs stream-attachments < rum/reactive
                                (rum/local false ::attachments-dropdown)
                                (drv/drv :attachment-uploading)
  [s attachments expand-cb remove-cb]
  (let [atc-num (count attachments)
        editable? (fn? remove-cb)
        show-all? (not (fn? expand-cb))
        is-mobile? (responsive/is-tablet-or-mobile?)
        attachments-num (if show-all?
                          (count attachments)
                          (if is-mobile? 1 2))
        attachments-list (if show-all?
                           attachments
                           (take attachments-num attachments))
        should-show-expand? (> (count attachments) (count attachments-list))
        attachment-uploading (drv/react s :attachment-uploading)]
    (when (or (pos? atc-num)
              attachment-uploading)
      [:div.stream-attachments
        [:div.stream-attachments-content.group
          {:class (when-not show-all? "collapsed")}
          (when attachment-uploading
            [:div.stream-attachments-item.group
              (small-loading)
              [:span.attachment-uploading (str "Uploading " (or (:progress attachment-uploading) 0) "%...")]])
          (for [idx (range (count attachments-list))
                :let [atch (nth attachments-list idx)
                      atch-key (str "attachment-" idx "-" (:file-url atch))
                      created-at (:created-at atch)
                      file-name (:file-name atch)
                      size (:file-size atch)
                      subtitle (when size
                                 (clojure.string/lower-case (str "(" (filesize size :binary false :format "%.2f") ")")))]]
            [:div.stream-attachments-item.group
              {:key atch-key}
              [:a.group
                {:href (:file-url atch)
                 :target "_blank"}
                [:div.attachment-info.group
                  {:class (when editable? "editable")}
                  [:div.attachment-icon]
                  [:div.attachment-labels
                    {:class (when editable? "edit")}
                    [:span.attachment-name
                      {:class utils/hide-class}
                      file-name]
                    [:span.attachment-description subtitle]]
                  (when editable?
                    [:button.mlb-reset.remove-attachment-bt
                      {:data-toggle (when-not is-mobile? "" "tooltip")
                       :data-placement "top"
                       :data-container "body"
                       :data-delay "{\"show\":\"500\", \"hide\":\"0\"}"
                       :title "Remove attachment"
                       :on-click #(do
                                    (utils/event-stop %)
                                    (utils/remove-tooltips)
                                    (when (fn? remove-cb)
                                      (remove-cb atch)))}])]]])
          (when should-show-expand?
            [:div.stream-attachments-show-more
              {:on-click expand-cb}
              (str "+ " (- (count attachments) (count attachments-list)) " more")])]])))