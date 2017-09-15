(ns oc.web.components.entry-edit
  (:require [rum.core :as rum]
            [cuerdas.core :as s]
            [org.martinklepsch.derivatives :as drv]
            [dommy.core :as dommy :refer-macros (sel1)]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.jwt :as jwt]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.medium-editor-exts :as editor]
            [oc.web.lib.image-upload :as iu]
            [oc.web.lib.medium-editor-exts :as editor]
            [oc.web.components.ui.media-picker :refer (media-picker)]
            [oc.web.components.ui.alert-modal :refer (alert-modal)]
            [oc.web.components.ui.emoji-picker :refer (emoji-picker)]
            [oc.web.components.ui.user-avatar :refer (user-avatar-image)]
            [oc.web.components.ui.media-attachments :refer (media-attachments)]
            [cljsjs.medium-editor]
            [cljsjs.rangy-selectionsaverestore]
            [goog.object :as gobj]
            [goog.events :as events]
            [goog.events.EventType :as EventType]))

(defn attachment-upload-success-cb [state res]
  (let [url    (gobj/get res "url")]
    (if-not url
      (dis/dispatch! [:error-banner-show "An error has occurred while processing the file URL. Please try again." 5000])
      (let [entry-editing   @(drv/get-ref state :entry-editing)
            attachments (or (:attachments entry-editing) [])
            attachment-data {:file-name (gobj/get res "filename")
                             :file-type (gobj/get res "mimetype")
                             :file-size (gobj/get res "size")
                             :file-url url}]
        (dis/dispatch! [:input [:entry-editing :attachments] (vec (conj attachments attachment-data))])
        (dis/dispatch! [:input [:entry-editing :has-changes] true])))))

(defn attachment-upload-error-cb [state res error]
  (dis/dispatch! [:error-banner-show "An error has occurred while processing the file. Please try again." 5000]))

(defn dismiss-modal [saving?]
  (dis/dispatch! [:entry-edit/dismiss])
  (when-not saving?
    (dis/dispatch! [:input [:entry-editing] nil])))

(defn close-clicked [s & [saving?]]
  (reset! (::dismiss s) true)
  (utils/after 180 #(dismiss-modal saving?)))

(defn unique-slug [topics topic-name]
  (let [slug (atom (s/slug topic-name))]
    (while (seq (filter #(= (:slug %) @slug) topics))
      (reset! slug (str (s/slug topic-name) "-" (int (rand 1000)))))
    @slug))

(defn toggle-topics-dd []
  (.dropdown (js/$ "div.entry-card-dd-container button.dropdown-toggle") "toggle"))

(defn body-on-change [state]
  (when-let [body-el (sel1 [:div.entry-edit-body])]
    ; Hide/show placeholder capturing the situation medium-editor ignores:
    ; multiple empty Ps, img or iframes
    (utils/after 1000
     #(when-let [$body-el (js/$ "div.entry-edit-body.medium-editor-placeholder")]
        (if (and (empty? (.text $body-el))
                 (<= (.-length (.find $body-el "div, p")) 1)
                 (zero? (.-length (.find $body-el "img, iframe"))))
          (.removeClass $body-el "hide-placeholder")
          (.addClass $body-el "hide-placeholder"))))
    ; Attach paste listener to the body and all its children
    (js/recursiveAttachPasteListener body-el (comp #(utils/medium-editor-hide-placeholder @(::body-editor state) body-el) #(body-on-change state)))
    (let [emojied-body (utils/emoji-images-to-unicode (gobj/get (utils/emojify (.-innerHTML body-el)) "__html"))]
      (dis/dispatch! [:input [:entry-editing :body] emojied-body])
      (dis/dispatch! [:input [:entry-editing :has-changes] true]))))

(defn- headline-on-change [state]
  (when-let [headline (sel1 [:div.entry-edit-headline])]
    (let [emojied-headline   (utils/emoji-images-to-unicode (gobj/get (utils/emojify (.-innerHTML headline)) "__html"))]
      (dis/dispatch! [:input [:entry-editing :headline] emojied-headline])
      (dis/dispatch! [:input [:entry-editing :has-changes] true]))))

(defn body-placeholder []
  (let [first-name (jwt/get-key :first-name)]
    (if-not (empty? first-name)
      (str "What's new, " first-name "?")
      "What's new?")))

(defn- setup-body-editor [state]
  (let [media-picker-id @(::media-picker-id state)
        headline-el  (sel1 [:div.entry-edit-headline])
        body-el      (sel1 [:div.entry-edit-body])
        body-editor  (new js/MediumEditor body-el (clj->js (-> (body-placeholder)
                                                            (utils/medium-editor-options false false)
                                                            (editor/inject-extension (editor/media-upload media-picker-id {:top -104 :left -4} (.querySelector js/document ".entry-edit-modal-container"))))))]
    (.subscribe body-editor
                "editableInput"
                (fn [event editable]
                  (body-on-change state)))
    (reset! (::body-editor state) body-editor)
    (js/recursiveAttachPasteListener body-el (comp #(utils/medium-editor-hide-placeholder @(::body-editor state) body-el) #(body-on-change state)))
    (events/listen headline-el EventType/INPUT #(headline-on-change state))
    (js/emojiAutocomplete)))

(defn headline-on-paste
  "Avoid to paste rich text into headline, replace it with the plain text clipboard data."
  [state e]
  ; Prevent the normal paste behaviour
  (utils/event-stop e)
  (let [clipboardData (or (.-clipboardData e) (.-clipboardData js/window))
        pasted-data (.getData clipboardData "text/plain")
        headline-el     (.querySelector js/document "div.entry-edit-headline")]
    ; replace the selected text of headline with the text/plain data of the clipboard
    (js/replaceSelectedText pasted-data)
    ; call the headline-on-change to check for content length
    (headline-on-change state)
    ; move cursor at the end
    (utils/to-end-of-content-editable headline-el)))

(defn create-new-topic [s]
  (when-not (empty? @(::new-topic s))
    (let [topics (:topics @(drv/get-ref s :board-data))
          topic-name (s/trim @(::new-topic s))
          topic-slug (unique-slug topics topic-name)]
      (dis/dispatch! [:topic-add {:name topic-name :slug topic-slug} true])
      (reset! (::new-topic s) ""))))

(defn media-picker-did-change [s]
  (body-on-change s)
  (utils/after 100 
    #(do
       (utils/to-end-of-content-editable (sel1 [:div.entry-edit-body]))
       (utils/scroll-to-bottom (sel1 [:div.entry-edit-modal-container]) true))))

(rum/defcs entry-edit < rum/reactive
                        (drv/drv :board-data)
                        (drv/drv :current-user-data)
                        (drv/drv :entry-editing)
                        (drv/drv :board-filters)
                        (rum/local false ::first-render-done)
                        (rum/local false ::dismiss)
                        (rum/local nil ::body-editor)
                        (rum/local "" ::initial-body)
                        (rum/local "" ::initial-headline)
                        (rum/local "" ::new-topic)
                        (rum/local false ::focusing-create-topic)
                        (rum/local false ::remove-no-scroll)
                        (rum/local "entry-edit-media-picker" ::media-picker-id)
                        {:will-mount (fn [s]
                                       (let [entry-editing @(drv/get-ref s :entry-editing)
                                             board-filters @(drv/get-ref s :board-filters)
                                             initial-body (utils/emojify (if (contains? entry-editing :links) (:body entry-editing) ""))
                                             initial-headline (utils/emojify (if (contains? entry-editing :links) (:headline entry-editing) ""))]
                                         (reset! (::initial-body s) initial-body)
                                         (reset! (::initial-headline s) initial-headline)
                                         (when (and (string? board-filters)
                                                    (nil? (:topic-slug entry-editing)))
                                            (let [topics (:topics @(drv/get-ref s :board-data))
                                                  topic (first (filter #(= (:slug %) board-filters) topics))]
                                              (when topic
                                                (dis/dispatch! [:input [:entry-editing :topic-slug] (:slug topic)])
                                                (dis/dispatch! [:input [:entry-editing :topic-name] (:name topic)])))))
                                       s)
                         :did-mount (fn [s]
                                      ;; Add no-scroll to the body to avoid scrolling while showing this modal
                                      (let [body (sel1 [:body])]
                                        (when-not (dommy/has-class? body :no-scroll)
                                          (reset! (::remove-no-scroll s) true)
                                          (dommy/add-class! (sel1 [:body]) :no-scroll)))
                                      (setup-body-editor s)
                                      (utils/to-end-of-content-editable (sel1 [:div.entry-edit-body]))
                                      s)
                         :after-render (fn [s]
                                         (when (not @(::first-render-done s))
                                           (reset! (::first-render-done s) true))
                                         s)
                         :will-unmount (fn [s]
                                         ;; Remove no-scroll class from the body tag
                                         (when @(::remove-no-scroll s)
                                          (dommy/remove-class! (sel1 [:body]) :no-scroll))
                                         s)}
  [s]
  (let [board-data        (drv/react s :board-data)
        topics            (distinct (:topics board-data))
        current-user-data (drv/react s :current-user-data)
        entry-editing     (drv/react s :entry-editing)
        new-entry?        (empty? (:uuid entry-editing))
        attachments       (:attachments entry-editing)]
    [:div.entry-edit-modal-container
      {:class (utils/class-set {:will-appear (or @(::dismiss s) (not @(::first-render-done s)))
                                :appear (and (not @(::dismiss s)) @(::first-render-done s))})
       :on-click #(when (and (empty? (:body entry-editing))
                             (empty? (:headline entry-editing))
                             (not (utils/event-inside? % (sel1 [:div.entry-edit-modal]))))
                    (close-clicked s))}
      [:div.entry-edit-modal.group
        [:div.entry-edit-modal-header.group
          (user-avatar-image current-user-data)
          [:div.posting-in (if new-entry? "Posting" "Posted") " in " [:span (:name board-data)]]
          [:div.arrow " · "]
          [:div.entry-card-dd-container
            [:button.mlb-reset.dropdown-toggle
              {:class (when-not (:topic-name entry-editing) "select-a-topic")
               :type "button"
               :id "entry-edit-dd-btn"
               :data-toggle "dropdown"
               :aria-haspopup true
               :aria-expanded false}
              (if (:topic-name entry-editing) (:topic-name entry-editing) "Add a topic")
              [:i.fa.fa-caret-down]]
            [:div.entry-edit-topics-dd.dropdown-menu
              {:aria-labelledby "entry-edit-dd-btn"}
              [:div.triangle]
              [:div.entry-dropdown-list-content
                [:ul
                  (for [t (sort #(compare (:name %1) (:name %2)) topics)
                        :let [selected (= (:topic-name entry-editing) (:name t))]]
                    [:li.selectable.group
                      {:key (str "entry-edit-dd-" (:slug t))
                       :on-click #(dis/dispatch! [:input [:entry-editing] (merge entry-editing {:topic-name (:name t) :has-changes true})])
                       :class (when selected "select")}
                      [:button.mlb-reset
                        (:name t)]
                      (when selected
                        [:button.mlb-reset.mlb-link.remove
                          {:on-click (fn [e]
                                       (utils/event-stop e)
                                       (dis/dispatch! [:input [:entry-editing] (merge entry-editing {:topic-slug nil :topic-name nil :has-changes true})]))}
                          "Remove"])])
                  [:li.divider]
                  [:li.entry-edit-new-topic.group
                    ; {:on-click #(do (utils/event-stop %) (toggle-topics-dd))}
                    (when-not @(::focusing-create-topic s)
                      [:button.mlb-reset.entry-edit-new-topic-plus
                        {:on-click (fn [e]
                                     (utils/event-stop e)
                                     (toggle-topics-dd)
                                     (.focus (js/$ "input.entry-edit-new-topic-field")))
                         :title "Create a new topic"}])
                    [:input.entry-edit-new-topic-field
                      {:type "text"
                       :value @(::new-topic s)
                       :on-focus #(reset! (::focusing-create-topic s) true)
                       :on-blur (fn [e] (utils/after 100 #(reset! (::focusing-create-topic s) false)))
                       :on-key-up (fn [e]
                                    (cond
                                      (= "Enter" (.-key e))
                                      (create-new-topic s)))
                       :on-change #(reset! (::new-topic s) (.. % -target -value))
                       :placeholder "Create New Topic"}]
                    (when @(::focusing-create-topic s)
                      [:button.mlb-reset.mlb-default.entry-edit-new-topic-create
                        {:on-click (fn [e]
                                     (utils/event-stop e)
                                     (create-new-topic s))
                         :disabled (empty? (s/trim @(::new-topic s)))}
                        "Create"])]]]]]]
      [:div.entry-edit-modal-divider]
      [:div.entry-edit-modal-body
        ; Headline element
        [:div.entry-edit-headline.emoji-autocomplete.emojiable
          {:content-editable true
           :placeholder "Title this (if you like)"
           :on-paste    #(headline-on-paste s %)
           :on-key-Up   #(headline-on-change s)
           :on-key-down #(headline-on-change s)
           :on-focus    #(headline-on-change s)
           :on-blur     #(headline-on-change s)
           :dangerouslySetInnerHTML @(::initial-headline s)}]
        ; Body element
        [:div.entry-edit-body.emoji-autocomplete.emojiable
          {:role "textbox"
           :aria-multiline true
           :contentEditable true
           :class (when-not (empty? (gobj/get @(::initial-body s) "__html")) "hide-placeholder")
           :dangerouslySetInnerHTML @(::initial-body s)}]
        ; Media handling
        (media-picker [:photo :video :chart] @(::media-picker-id s) #(media-picker-did-change s) "div.entry-edit-body" entry-editing :entry-editing)
        [:div.entry-edit-controls-right]]
        ; Bottom controls
        [:div.entry-edit-controls.group]
      (media-attachments attachments #(dis/dispatch! [:input [:entry-editing :has-changes] true]))
      [:div.entry-edit-modal-divider]
      [:div.entry-edit-modal-footer.group
        ;; Attachments button
        [:button.mlb-reset.attachment
          {:title "Add an attachment"
           :type "button"
           :data-toggle "tooltip"
           :data-container "body"
           :data-placement "top"
           :on-click (fn [e]
                      (.blur (.-target e))
                      (utils/after 100 #(.tooltip (js/$ "[data-toggle=\"tooltip\"]") "hide"))
                      (iu/upload!
                       nil
                       (partial attachment-upload-success-cb s)
                       nil
                       (partial attachment-upload-error-cb s)))}
            [:i.mdi.mdi-paperclip]]
        [:button.mlb-reset.mlb-default.form-action-bt
          {:on-click #(do
                        (dis/dispatch! [:entry-save])
                        (close-clicked s))
           :disabled (not (:has-changes entry-editing))}
          (if new-entry? "Post" "Save")]
        [:button.mlb-reset.mlb-link-black.form-action-bt
          {:on-click #(close-clicked s)}
          "Cancel"]]]]))