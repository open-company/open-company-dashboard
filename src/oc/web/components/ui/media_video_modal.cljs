(ns oc.web.components.ui.media-video-modal
  (:require [rum.core :as rum]
            [cuerdas.core :as string]
            [dommy.core :as dommy :refer-macros (sel1)]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.components.ui.user-avatar :refer (user-avatar-image)]))

(defn dismiss-modal [s]
  (let [dispatch-input-key (first (:rum/args s))]
    (dis/dispatch! [:input [dispatch-input-key :media-video] false])))

(defn close-clicked [s]
  (reset! (::dismiss s) true)
  (utils/after 180 #(dismiss-modal s)))

(def youtube-regexp "https?://(?:www\\.|m\\.)*(?:youtube\\.com|youtu\\.be)/watch/?\\?v=([a-zA-Z0-9_-]{11}/?)")

; https://vimeo.com/223518754 https://vimeo.com/groups/asd/223518754 https://vimeo.com/channels/asd/223518754
(def vimeo-regexp "(?:http|https)?:\\/\\/(?:www\\.)?vimeo.com\\/(?:channels\\/(?:\\w+\\/)?|groups\\/(?:[?:^\\/]*)\\/videos\\/|)(\\d+)(?:|\\/\\?)")

(defn get-video-data [url]
  (let [yr (js/RegExp youtube-regexp "ig")
        vr (js/RegExp vimeo-regexp "ig")
        y-groups (.exec yr url)
        v-groups (.exec vr url)]
    {:id (if (nth y-groups 1) (nth y-groups 1) (nth v-groups 1))
     :type (if (nth y-groups 1) :youtube :vimeo)}))

(defn- get-vimeo-thumbnail-success [s video res]
  (let [dispatch-input-key (first (:rum/args s))
        resp (aget res 0)
        thumbnail (aget resp "thumbnail_small")
        video-data (assoc video :thumbnail thumbnail)]
    (dis/dispatch! [:input [dispatch-input-key :temp-video] video-data])
    (close-clicked s)))

(def _retry (atom 0))

(declare get-vimeo-thumbnail)

(defn- get-vimeo-thumbnail-retry [s video]
  ;; Increment the retry count
  (if (< (swap! _retry inc) 3)
    ; Retry at most 3 times to load the video details
    (get-vimeo-thumbnail s video)
    ;; Add the video without thumbnail
    (let [dispatch-input-key (nth (:rum/args s) 1)]
      (dis/dispatch! [:input [dispatch-input-key :temp-video] video])
      (close-clicked s))))

(defn- get-vimeo-thumbnail [s video]
  (.ajax js/$
    #js {
      :method "GET"
      :url (str "http://vimeo.com/api/v2/video/" (:id video) ".json")
      :success #(get-vimeo-thumbnail-success s video %)
      :error #(get-vimeo-thumbnail-retry s video)}))

(defn valid-video-url? [url]
  (let [trimmed-url (string/trim url)
        yr (js/RegExp youtube-regexp "ig")
        vr (js/RegExp vimeo-regexp "ig")]
    (when-not (empty? trimmed-url)
      (or (.match trimmed-url yr)
          (.match trimmed-url vr)))))

(defn video-add-click [s]
  (when (valid-video-url? @(::video-url s))
    (let [dispatch-input-key (first (:rum/args s))
          video-data (get-video-data @(::video-url s))]
      (if (= :vimeo (:type video-data))
        (get-vimeo-thumbnail s video-data)
        (do
          (dis/dispatch! [:input [dispatch-input-key :temp-video] video-data])
          (close-clicked s))))))

(rum/defcs media-video-modal < (rum/local false ::first-render-done)
                               (rum/local false ::dismiss)
                               (rum/local "" ::video-url)
                               rum/reactive
                               (drv/drv :current-user-data)
                               {:after-render (fn [s]
                                                (when (not @(::first-render-done s))
                                                  (reset! (::first-render-done s) true))
                                                s)
                                :did-mount (fn [s]
                                            (utils/after 100 #(.focus (sel1 [:input.media-video-modal-input])))
                                            s)}
  [s dispatch-input-key]
  (let [current-user-data (drv/react s :current-user-data)]
    [:div.media-video-modal-container
      {:class (utils/class-set {:will-appear (or @(::dismiss s) (not @(::first-render-done s)))
       :appear (and (not @(::dismiss s)) @(::first-render-done s))})}
      [:div.media-video-modal
        [:div.media-video-modal-header.group
          (user-avatar-image current-user-data)
          [:div.title "Adding a video"]]
        [:div.media-video-modal-divider]
        [:div.media-video-modal-content
          [:div.content-title "VIDEO LINK"]
          [:input.media-video-modal-input
            {:type "text"
             :value @(::video-url s)
             :on-change #(reset! (::video-url s) (.. % -target -value))
             :placeholder "Link from YouTube or Vimeo"}]]
        [:div.media-video-modal-buttons.group
          [:button.mlb-reset.mlb-default
            {:on-click #(video-add-click s)
             :disabled (not (valid-video-url? @(::video-url s)))}
            "Add"]
          [:button.mlb-reset.mlb-link-black
            {:on-click #(close-clicked s)}
            "Cancel"]]]]))