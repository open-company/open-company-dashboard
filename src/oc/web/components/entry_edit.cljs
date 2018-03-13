(ns oc.web.components.entry-edit
  (:require-macros [if-let.core :refer (when-let*)])
  (:require [rum.core :as rum]
            [cuerdas.core :as string]
            [org.martinklepsch.derivatives :as drv]
            [dommy.core :as dommy :refer-macros (sel1)]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.mixins.ui :as mixins]
            [oc.web.lib.image-upload :as iu]
            [oc.web.lib.responsive :as responsive]
            [oc.web.lib.medium-editor-exts :as editor]
            [oc.web.actions.activity :as activity-actions]
            [oc.web.components.ui.emoji-picker :refer (emoji-picker)]
            [oc.web.components.ui.small-loading :refer (small-loading)]
            [oc.web.components.ui.dropdown-list :refer (dropdown-list)]
            [oc.web.components.ui.user-avatar :refer (user-avatar-image)]
            [oc.web.components.rich-body-editor :refer (rich-body-editor)]
            [oc.web.components.ui.stream-view-attachments :refer (stream-view-attachments)]
            [goog.object :as gobj]
            [goog.events :as events]
            [goog.events.EventType :as EventType]))

(defn should-show-divider-line [s]
  (when @(:first-render-done s)
    (when-let [entry-edit-modal-body (rum/ref-node s "entry-edit-modal-body")]
      (let [container-height (+ (.-clientHeight entry-edit-modal-body) 11) ;; Remove padding
            next-show-divider-line (> (.-scrollHeight entry-edit-modal-body) container-height)]
        (when (not= next-show-divider-line @(::show-divider-line s))
          (reset! (::show-divider-line s) next-show-divider-line))))))

(defn calc-entry-edit-modal-height
  [s & [force-calc]]
  (when @(:first-render-done s)
    (when-let [entry-edit-modal (rum/ref-node s "entry-edit-modal")]
      (when (or force-calc
                (not= @(::entry-edit-modal-height s) (.-clientHeight entry-edit-modal)))
        (reset! (::entry-edit-modal-height s) (.-clientHeight entry-edit-modal))))))

(defn real-close [s]
  (reset! (::dismiss s) true)
  (utils/after 180 activity-actions/entry-edit-dismiss))

;; Local cache for outstanding edits

(defn autosave [s]
  (let [entry-editing @(drv/get-ref s :entry-editing)
        body-el (sel1 [:div.rich-body-editor])
        cleaned-body (when body-el
                      (utils/clean-body-html (.-innerHTML body-el)))]
    (activity-actions/entry-save-on-exit :entry-editing entry-editing cleaned-body)))

(defn save-on-exit?
  "Locally save the current outstanding edits if needed."
  [s]
  (when @(drv/get-ref s :entry-save-on-exit)
    (autosave s)))

(defn toggle-save-on-exit
  "Enable and disable save current edit."
  [s turn-on?]
  (activity-actions/entry-toggle-save-on-exit turn-on?))

;; Close dismiss handling

(defn cancel-clicked [s]
  (if @(::uploading-media s)
    (let [alert-data {:icon "/img/ML/trash.svg"
                      :action "dismiss-edit-uploading-media"
                      :message (str "Leave before finishing upload?")
                      :link-button-title "Stay"
                      :link-button-cb #(dis/dispatch! [:alert-modal-hide])
                      :solid-button-style :red
                      :solid-button-title "Cancel upload"
                      :solid-button-cb #(do
                                          (dis/dispatch! [:entry-clear-local-cache :entry-editing])
                                          (dis/dispatch! [:alert-modal-hide])
                                          (real-close s))
                      }]
      (dis/dispatch! [:alert-modal-show alert-data]))
    (if (:has-changes @(drv/get-ref s :entry-editing))
      (let [alert-data {:icon "/img/ML/trash.svg"
                        :action "dismiss-edit-dirty-data"
                        :message (str "Leave without saving your changes?")
                        :link-button-title "Stay"
                        :link-button-cb #(dis/dispatch! [:alert-modal-hide])
                        :solid-button-style :red
                        :solid-button-title "Lose changes"
                        :solid-button-cb #(do
                                            (dis/dispatch! [:entry-clear-local-cache :entry-editing])
                                            (dis/dispatch! [:alert-modal-hide])
                                            (real-close s))
                        }]
        (dis/dispatch! [:alert-modal-show alert-data]))
      (do
       (dis/dispatch! [:entry-clear-local-cache :entry-editing])
       (real-close s)))))

;; Data change handling

(defn body-on-change [state]
  (toggle-save-on-exit state true)
  (dis/dispatch! [:input [:entry-editing :has-changes] true])
  (calc-entry-edit-modal-height state))

(defn- headline-on-change [state]
  (toggle-save-on-exit state true)
  (when-let [headline (rum/ref-node state "headline")]
    (let [emojied-headline  (utils/emoji-images-to-unicode (gobj/get (utils/emojify (.-innerText headline)) "__html"))]
      (dis/dispatch! [:update [:entry-editing] #(merge % {:headline emojied-headline
                                                          :has-changes true})]))))

;; Headline setup and paste handler

(defn- setup-headline [state]
  (when-let [headline-el  (rum/ref-node state "headline")]
    (reset! (::headline-input-listener state) (events/listen headline-el EventType/INPUT #(headline-on-change state))))
  (js/emojiAutocomplete))

(defn headline-on-paste
  "Avoid to paste rich text into headline, replace it with the plain text clipboard data."
  [state e]
  ; Prevent the normal paste behaviour
  (utils/event-stop e)
  (let [clipboardData (or (.-clipboardData e) (.-clipboardData js/window))
        pasted-data   (.getData clipboardData "text/plain")]
    ; replace the selected text of headline with the text/plain data of the clipboard
    (js/replaceSelectedText pasted-data)
    ; call the headline-on-change to check for content length
    (headline-on-change state)
    (when-not (responsive/is-tablet-or-mobile?)
      (when-let [headline-el (rum/ref-node state "headline")]
        ; move cursor at the end
        (utils/to-end-of-content-editable headline-el)))))

(defn add-emoji-cb [s]
  (headline-on-change s)
  (body-on-change s))

(defn- clean-body []
  (when-let [body-el (sel1 [:div.rich-body-editor])]
    (dis/dispatch! [:input [:entry-editing :body] (utils/clean-body-html (.-innerHTML body-el))])))

(defn- is-publishable? [entry-editing]
  (seq (:board-slug entry-editing)))

(defn trim [value]
  (if (string? value)
    (string/trim value)
    value))

(rum/defcs entry-edit < rum/reactive
                        ;; Derivatives
                        (drv/drv :org-data)
                        (drv/drv :current-user-data)
                        (drv/drv :entry-editing)
                        (drv/drv :editable-boards)
                        (drv/drv :alert-modal)
                        (drv/drv :media-input)
                        (drv/drv :entry-save-on-exit)
                        ;; Locals
                        (rum/local false ::dismiss)
                        (rum/local nil ::body-editor)
                        (rum/local "" ::initial-body)
                        (rum/local "" ::initial-headline)
                        (rum/local 330 ::entry-edit-modal-height)
                        (rum/local nil ::headline-input-listener)
                        (rum/local nil ::uploading-media)
                        (rum/local false ::show-divider-line)
                        (rum/local false ::saving)
                        (rum/local false ::publishing)
                        (rum/local false ::show-boards-dropdown)
                        (rum/local false ::window-resize-listener)
                        (rum/local false ::window-click-listener)
                        (rum/local nil ::autosave-timer)
                        (rum/local false ::show-legend)
                        ;; Mixins
                        mixins/no-scroll-mixin
                        mixins/first-render-mixin

                        {:will-mount (fn [s]
                          (let [entry-editing @(drv/get-ref s :entry-editing)
                                initial-body (if (seq (:body entry-editing))
                                               (:body entry-editing)
                                               utils/default-body)
                                initial-headline (utils/emojify
                                                   (if (seq (:headline entry-editing))
                                                     (:headline entry-editing)
                                                     ""))]
                            (reset! (::initial-body s) initial-body)
                            (reset! (::initial-headline s) initial-headline))
                          s)
                         :did-mount (fn [s]
                          (utils/after 300 #(setup-headline s))
                          (when-not (responsive/is-tablet-or-mobile?)
                            (when-let [headline-el (rum/ref-node s "headline")]
                              (utils/to-end-of-content-editable headline-el)))
                          (reset! (::window-resize-listener s)
                           (events/listen
                            js/window
                            EventType/RESIZE
                            #(calc-entry-edit-modal-height s true)))
                          (reset! (::window-click-listener s)
                           (events/listen js/window EventType/CLICK
                            #(when (and @(::show-legend s)
                                        (not (utils/event-inside? % (rum/ref-node s "legend-container"))))
                                (reset! (::show-legend s) false))))
                          (reset! (::autosave-timer s) (utils/every 5000 #(autosave s)))
                          (when (responsive/is-tablet-or-mobile?)
                            (set! (.-scrollTop (.-body js/document)) 0))
                          (when (and (responsive/is-tablet-or-mobile?) (js/isSafari))
                            (js/OCStaticStartFixFixedPositioning "div.entry-edit-modal-header-mobile"))
                          s)
                         :before-render (fn [s]
                          (calc-entry-edit-modal-height s)
                          ;; Set or remove the onBeforeUnload prompt
                          (let [save-on-exit @(drv/get-ref s :entry-save-on-exit)]
                            (set! (.-onbeforeunload js/window)
                             (if save-on-exit
                              #(do
                                (save-on-exit? s)
                                "Do you want to save before leaving?")
                              nil)))
                          ;; Handle saving/publishing states to dismiss the component
                          (let [entry-editing @(drv/get-ref s :entry-editing)]
                            ;; Entry is saving
                            (when @(::saving s)
                              ;: Save request finished
                              (when (not (:loading entry-editing))
                                (reset! (::saving s) false)
                                (when-not (:error entry-editing)
                                  (real-close s)
                                  (let [to-draft? (not= (:status entry-editing) "published")]
                                    ;; If it's not published already redirect to drafts board
                                    (utils/after 180
                                     #(router/nav!
                                       (if to-draft?
                                         (oc-urls/drafts (router/current-org-slug))
                                         (oc-urls/board (:board-slug entry-editing)))))))))
                            (when @(::publishing s)
                              (when (not (:publishing entry-editing))
                                (reset! (::publishing s) false)
                                (when-not (:error entry-editing)
                                  (let [redirect? (seq (:board-slug entry-editing))]
                                    ;; Redirect to the publishing board if the slug is available
                                    (when redirect?
                                      (real-close s)
                                      (utils/after
                                       180
                                       #(let [from-ap (or (:from-all-posts @router/path)
                                                          (= (router/current-board-slug) "all-posts"))
                                              go-to-ap (or from-ap
                                                           (not= (:status entry-editing) "published"))]
                                          ;; Redirect to AP if coming from it or if the post is not published
                                          (router/nav!
                                            (if go-to-ap
                                              (oc-urls/all-posts (router/current-org-slug))
                                              (oc-urls/board (router/current-org-slug)
                                               (:board-slug entry-editing))))))))))))
                          s)
                         :after-render  (fn [s] (should-show-divider-line s) s)
                         :will-unmount (fn [s]
                          (when @(::body-editor s)
                            (.destroy @(::body-editor s))
                            (reset! (::body-editor s) nil))
                          (when @(::headline-input-listener s)
                            (events/unlistenByKey @(::headline-input-listener s))
                            (reset! (::headline-input-listener s) nil))
                          (when @(::window-resize-listener s)
                            (events/unlistenByKey @(::window-resize-listener s))
                            (reset! (::window-resize-listener s) nil))
                          (when @(::window-click-listener s)
                            (events/unlistenByKey @(::window-click-listener s))
                            (reset! (::window-click-listener s) nil))
                          (when @(::autosave-timer s)
                            (.clearInterval js/window @(::autosave-timer s))
                            (reset! (::autosave-timer s) nil))
                          (set! (.-onbeforeunload js/window) nil)
                          s)}
  [s]
  (let [org-data          (drv/react s :org-data)
        current-user-data (drv/react s :current-user-data)
        entry-editing     (drv/react s :entry-editing)
        alert-modal       (drv/react s :alert-modal)
        new-entry?        (empty? (:uuid entry-editing))
        is-mobile? (responsive/is-tablet-or-mobile?)
        fixed-entry-edit-modal-height (max @(::entry-edit-modal-height s) 330)
        wh (.-innerHeight js/window)
        media-input (drv/react s :media-input)
        all-boards (drv/react s :editable-boards)
        entry-board (get all-boards (:board-slug entry-editing))
        published? (= (:status entry-editing) "published")]
    [:div.entry-edit-modal-container
      {:class (utils/class-set {:will-appear (or @(::dismiss s) (not @(:first-render-done s)))
                                :appear (and (not @(::dismiss s)) @(:first-render-done s))})}
      [:div.entry-edit-modal-header
        [:button.mlb-reset.mobile-modal-close-bt
          {:on-click #(cancel-clicked s)}]
        (let [should-show-save-button? (and (not @(::publishing s))
                                            (not published?))]
          [:div.entry-edit-modal-header-right
            (let [fixed-headline (trim (:headline entry-editing))
                  disabled? (or @(::publishing s)
                                (not (is-publishable? entry-editing))
                                (zero? (count fixed-headline)))
                  working? (or (and published?
                                    @(::saving s))
                               (and (not published?)
                                    @(::publishing s)))]
              [:button.mlb-reset.header-buttons.post-button
                {:ref "mobile-post-btn"
                 :on-click (fn [_]
                             (clean-body)
                             (if (and (is-publishable? entry-editing)
                                      (not (zero? (count fixed-headline))))
                               (if published?
                                 (do
                                   (reset! (::saving s) true)
                                   (dis/dispatch! [:input [:entry-editing :headline] fixed-headline])
                                   (dis/dispatch! [:entry-save]))
                                 (do
                                   (reset! (::publishing s) true)
                                   (dis/dispatch! [:input [:entry-editing :headline] fixed-headline])
                                   (dis/dispatch! [:entry-publish])))
                               (when (zero? (count fixed-headline))
                                 (when-let [$post-btn (js/$ (rum/ref-node s "mobile-post-btn"))]
                                   (when-not (.data $post-btn "bs.tooltip")
                                     (.tooltip $post-btn
                                      (clj->js {:container "body"
                                                :placement "bottom"
                                                :trigger "manual"
                                                :template (str "<div class=\"tooltip post-btn-tooltip\">"
                                                                 "<div class=\"tooltip-arrow\"></div>"
                                                                 "<div class=\"tooltip-inner\"></div>"
                                                               "</div>")
                                                :title "A title is required in order to save or share this post."})))
                                   (utils/after 10 #(.tooltip $post-btn "show"))
                                   (utils/after 5000 #(.tooltip $post-btn "hide"))))))
                 :class (when disabled?
                          "disabled")}
                (if working?
                  (small-loading)
                  [:div.button-icon
                    {:class (when disabled? "disabled")}])
                (if published?
                  "Save"
                  "Post")])
            (when should-show-save-button?
              [:div.mobile-buttons-divider-line])
            (when should-show-save-button?
              (let [disabled? (or @(::saving s)
                                  (not (:has-changes entry-editing)))
                    working? @(::saving s)]
                [:button.mlb-reset.header-buttons.save-button
                  {:class (when disabled?
                            "disabled")
                   :on-click (fn [_]
                              (when-not disabled?
                                (clean-body)
                                (reset! (::saving s) true)
                                (dis/dispatch! [:entry-save])))}
                  (if working?
                    (small-loading)
                    [:div.button-icon
                      {:class (when disabled? "disabled")}])
                  (str "Save " (when-not is-mobile? "to ") "draft")]))])]
      [:div.modal-wrapper
        [:div.entry-edit-modal.group
          {:ref "entry-edit-modal"}
          [:div.entry-edit-modal-headline
            (user-avatar-image current-user-data)
            [:div.posting-in
              [:span
                (if (:uuid entry-editing)
                  (if (= (:status entry-editing) "published")
                    "Posted in: "
                    "Draft for: ")
                  "Posting in: ")]
              [:div.boards-dropdown-caret
                [:div.board-name
                  {:on-click #(reset! (::show-boards-dropdown s) (not @(::show-boards-dropdown s)))}
                  (:board-name entry-editing)]
                (when @(::show-boards-dropdown s)
                  (dropdown-list
                   {:items (map
                            #(clojure.set/rename-keys % {:name :label :slug :value})
                            (vals all-boards))
                    :value (:board-slug entry-editing)
                    :on-blur #(reset! (::show-boards-dropdown s) false)
                    :on-change (fn [item]
                                 (toggle-save-on-exit s true)
                                 (reset! (::show-boards-dropdown s) false)
                                 (dis/dispatch! [:input [:entry-editing :has-changes] true])
                                 (dis/dispatch! [:input [:entry-editing :board-slug] (:value item)])
                                 (dis/dispatch! [:input [:entry-editing :board-name] (:label item)]))
                    :placeholder (when (and (= (count all-boards) 1)
                                            (= (:slug (first all-boards)) "general"))
                                   [:div.add-section-tooltip-container
                                     [:div.add-section-tooltip-arrow]
                                     [:div.add-section-tooltip
                                      (str
                                       "Keep posts organized by sections, e.g., "
                                       "Announcements, and Design, Sales, and Marketing.")]])}))]]]
          [:div.entry-edit-modal-body
            {:ref "entry-edit-modal-body"}
            ; Headline element
            [:div.entry-edit-headline.emoji-autocomplete.emojiable.group
              {:content-editable true
               :ref "headline"
               :placeholder utils/default-headline
               :on-paste    #(headline-on-paste s %)
               :on-key-down #(headline-on-change s)
               :on-click    #(headline-on-change s)
               :on-key-press (fn [e]
                             (when (= (.-key e) "Enter")
                               (utils/event-stop e)
                               (utils/to-end-of-content-editable (sel1 [:div.rich-body-editor]))))
               :dangerouslySetInnerHTML @(::initial-headline s)}]
            (rich-body-editor {:on-change (partial body-on-change s)
                               :use-inline-media-picker false
                               :multi-picker-container-selector "div#entry-edit-footer-multi-picker"
                               :initial-body @(::initial-body s)
                               :show-placeholder (not (contains? entry-editing :links))
                               :show-h2 true
                               :dispatch-input-key :entry-editing
                               :upload-progress-cb (fn [is-uploading?]
                                                     (reset! (::uploading-media s) is-uploading?))
                               :media-config ["photo" "video"]
                               :classes "emoji-autocomplete emojiable"})
            ; Attachments
            (stream-view-attachments (:attachments entry-editing) #(activity-actions/remove-attachment :entry-editing %))]
          [:div.entry-edit-modal-footer
            [:div.entry-edit-footer-multi-picker
              {:id "entry-edit-footer-multi-picker"}]
            (emoji-picker {:add-emoji-cb (partial add-emoji-cb s)
                           :width 20
                           :height 20
                           :default-field-selector "div.entry-edit-modal div.rich-body-editor"
                           :container-selector "div.entry-edit-modal"})
            [:div.entry-edit-legend-container
              {:on-click #(reset! (::show-legend s) (not @(::show-legend s)))
               :ref "legend-container"}
              [:button.mlb-reset.entry-edit-legend-trigger
                {:aria-label "Keyboard shortcuts"
                 :title "Shortcuts"
                 :data-toggle "tooltip"
                 :data-placement "top"
                 :data-container "body"}]
              (when @(::show-legend s)
                [:div.entry-edit-legend-image])]]]]]))