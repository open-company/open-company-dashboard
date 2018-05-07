(ns oc.web.components.ui.stream-attachments
  (:require [rum.core :as rum]
            [oc.web.lib.utils :as utils]
            [oc.web.utils.activity :as au]
            [oc.web.lib.responsive :as responsive]
            [clojure.contrib.humanize :refer (filesize)]
            [goog.events :as events]
            [goog.events.EventType :as EventType]))

(rum/defcs stream-attachments < (rum/local false ::attachments-dropdown)
  [s attachments remove-cb]
  (let [atc-num (count attachments)
        ww (responsive/ww)
        editable? (fn? remove-cb)]
    (when (pos? atc-num)
      [:div.stream-attachments
        [:div.stream-attachments-content
          (for [atch attachments
                :let [created-at (:created-at atch)
                      file-name (:file-name atch)
                      size (:file-size atch)
                      subtitle (when size
                                 (filesize size :binary false :format "%.2f"))]]
            [:a.stream-attachments-item.group
              {:key (str "attachment-" size "-" (:file-url atch))
               :href (:file-url atch)
               :target "_blank"}
              [:div.attachment-info
                {:class (when editable? "editable")}
                [:span.attachment-name file-name]
                [:span.attachment-content-separator]
                [:span.attachment-description subtitle]
                (when editable?
                  [:button.mlb-reset.remove-attachment-bt
                    {:data-toggle (when-not (responsive/is-tablet-or-mobile?) "" "tooltip")
                     :data-placement "top"
                     :data-container "body"
                     :title "Remove attachment"
                     :on-click #(do
                                  (utils/event-stop %)
                                  (utils/remove-tooltips)
                                  (when (fn? remove-cb)
                                    (remove-cb atch)))}])]])]])))