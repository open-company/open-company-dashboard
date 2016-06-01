(ns open-company-web.components.ui.filestack-uploader
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [open-company-web.local-settings :as ls]
            [open-company-web.lib.utils :as utils]
            [open-company-web.components.ui.icon :as i]
            [goog.style :as gstyle]
            [goog.dom :as gdom]
            [goog.dom.classlist :as cl]
            [cljsjs.filestack] ; pulled in for cljsjs externs
            [clojure.string :as string]))

(def placeholder-id (str (random-uuid)))

(defn upload-file! [editor owner file]
  (let [success-cb  (fn [success]
                      (let [url    (.-url success)
                            node   (gdom/createDom "img" #js {:src url})
                            marker (gdom/getElement placeholder-id)]
                        (gdom/replaceNode node marker))
                      (gstyle/setStyle (gdom/getElement "file-upload-ui") #js {:display "none"})
                      (om/set-state! owner {}))
        error-cb    (fn [error] (js/console.log "error" error))
        progress-cb (fn [progress]
                      (om/set-state! owner {:state :show-progress
                                            :progress progress}))]
    (cond
      (and (string? file) (not (string/blank? file)))
      (js/filepicker.storeUrl file success-cb error-cb progress-cb)
      file
      (js/filepicker.store file #js {:name (.-name file)} success-cb error-cb progress-cb))))

(defn insert-marker! []
  (when-not (gdom/getElement placeholder-id)
    (js/MediumEditor.util.insertHTMLCommand
     js/document
     (str "<span id=" placeholder-id "></span>"))))

(defcomponent filestack-uploader [editor owner]
  (did-mount [_]
    (when-not (utils/is-test-env?)
      (assert ls/filestack-key "FileStack API Key required")
      (js/filepicker.setKey ls/filestack-key)))

  (render-state [this _]
    (dom/div {:id "file-upload-ui"
              :style (merge {:transition ".2s"}
                            (when (:state (om/get-state owner))
                              {:right 0}))}
      (dom/div {:class "flex"}
        (dom/input {:id "file-upload-ui--select-trigger" :style {:display "none"} :type "file"
                    :on-change #(upload-file! editor owner (-> % .-target .-files (aget 0)))})
        (dom/button {:class "btn-reset p0"
                     :style {:margin-right "13px"
                             :transition ".2s"
                             :transform (if (om/get-state owner :state) "rotate(135deg)")}
                     :on-click (fn [_] (om/update-state! owner :state #(if % nil :show-options)))}
          (i/icon :circle-add {:size 24}))
        (case (:state (om/get-state owner))
          :show-options
          (dom/div (dom/button {:style {:font-size "14px"} :class "underline btn-reset p0"
                                :on-click (fn [_]
                                            (insert-marker!)
                                            (.click (gdom/getElement "file-upload-ui--select-trigger")))}
                     "Select an image")
            (dom/span {:style {:font-size "14px"}} " or ")
            (dom/button {:style {:font-size "14px"} :class "underline btn-reset p0"
                         :on-click (fn [_]
                                     (insert-marker!)
                                     (om/set-state! owner :state :show-url-field))}
                "provide an image URL"))
          :show-progress
          (dom/span (str "Uploading... " (om/get-state owner :progress) "%"))
          :show-url-field
          (dom/div (dom/input {:type "text" :style {:width 300} :auto-focus true
                               :on-change #(do (om/set-state! owner :url (-> % .-target .-value)) true)
                               :value (om/get-state owner :url)})
            (dom/button {:style {:font-size "14px" :margin-left "1rem"} :class "underline btn-reset p0"
                         :on-click #(upload-file! editor owner (om/get-state owner :url))}
              "add")
            (dom/button {:style {:font-size "14px" :margin-left "1rem" :opacity "0.5"}
                         :class "underline btn-reset p0"
                         :on-click (fn [_]
                                     (gdom/removeNode (gdom/getElement placeholder-id))
                                     (om/set-state! owner {}))}
              "cancel"))
          (dom/span))))))