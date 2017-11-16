(ns oc.web.components.ui.emoji-picker
  (:require [rum.core :as rum]
            [dommy.core :refer-macros (sel1)]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.react-utils :as react-utils]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [cljsjs.react]
            [cljsjs.react.dom]
            [cljsjs.emoji-mart]))

(def emojiable-class "emojiable")

(defn emojiable-active?
  []
  (>= (.indexOf (.-className (.-activeElement js/document)) emojiable-class) 0))

(defn remove-markers [s]
  (when @(::caret-pos s)
    (.removeMarkers js/rangy @(::caret-pos s))))

(defn on-click-out [s e]
  (when-not (utils/event-inside? e (rum/ref-node s "emoji-picker"))
    (remove-markers s)
    (let [prior-value @(::visible s)]
      (reset! (::visible s) false)
      (let [will-hide-picker (:will-hide-picker (first (:rum/args s)))]
        (when (and prior-value (fn? will-hide-picker))
          (will-hide-picker))))))

(defn save-caret-position [s]
  (remove-markers s)
  (let [caret-pos (::caret-pos s)]
    (if (emojiable-active?)
      (do
        (reset! (::last-active-element s) (.-activeElement js/document))
        (reset! caret-pos (.saveSelection js/rangy js/window)))
      (reset! caret-pos nil))))

(defn replace-with-emoji [caret-pos emoji]
  (when @caret-pos
    (.restoreSelection js/rangy @caret-pos)
    (js/pasteHtmlAtCaret (.-native emoji) (.getSelection js/rangy js/window) false)))

(defn check-focus [s _]
  (let [container-selector (or (:container-selector (first (:rum/args s))) "document.body")
        container-node (.querySelector js/document container-selector)
        active-element (.-activeElement js/document)]
    ;; Enabled when:
    ;; active element is emojiable and active element is descendant of container
    (reset! (::disabled s)
     (or (not (emojiable-active?))
         (not container-node)
         (not (.contains container-node active-element))))))

;; ===== D3 Chart Component =====

;; Render an emoji button that reveal a picker for emoji.
;; It will add the selected emoji in place of the current selection if
;; the current activeElement has the class `emojiable`.

(rum/defcs emoji-picker <
  (rum/local false ::visible)
  (rum/local false ::caret-pos)
  (rum/local false ::last-active-element)
  (rum/local false ::disabled)
  {:init (fn [s p] (js/rangy.init) s)
   :will-mount (fn [s]
                 (check-focus s nil)
                 s)
   :did-mount (fn [s] (when-not (utils/is-test-env?)
                        (let [click-listener (events/listen
                                              js/window
                                              EventType/CLICK
                                              (partial on-click-out s))
                              focusin (events/listen
                                       js/document
                                       EventType/FOCUSIN
                                       (partial check-focus s))
                              focusout (events/listen
                                        js/document
                                        EventType/FOCUSOUT
                                        (partial check-focus s))]
                          (merge s {::click-listener click-listener
                                    ::focusin-listener focusin
                                    ::focusout-listener focusout}))))
   :will-unmount (fn [s] (events/unlistenByKey (::click-listener s))
                         (events/unlistenByKey (::focusin-listener s))
                         (events/unlistenByKey (::focusout-listener s))
                         (dissoc s ::click-listener ::focusin-listener ::focusout-listener))}
  [s {:keys [add-emoji-cb position width height will-show-picker will-hide-picker container-selector]
      :as arg
      :or {position "top"
           width 25
           height 25}}]
  (let [visible (::visible s)
        caret-pos (::caret-pos s)
        last-active-element (::last-active-element s)
        disabled (::disabled s)]
    [:div.emoji-picker.relative
      {:ref "emoji-picker"
       :style {:width (str width "px")
               :height (str height "px")}}
      [:button.emoji-button.btn-reset
        {:type "button"
         :title "Insert emoji"
         :data-placement "top"
         :data-container "body"
         :data-toggle "tooltip"
         :disabled @(::disabled s)
         :on-mouse-down #(if @(::disabled s)
                          (remove-markers s)
                          (save-caret-position s))
         :on-click #(do
                      (.preventDefault %)
                      (let [vis (and @caret-pos (not @visible))]
                        (when (and vis (fn? will-show-picker))
                          (will-show-picker))
                        (when (and vis (fn? will-hide-picker))
                          (will-hide-picker))
                        (reset! visible vis)))}]
      [:div.picker-container
        {:class (utils/class-set {position true
                                  :visible @visible})}
        (when-not (utils/is-test-env?)
          (react-utils/build (.-Picker js/EmojiMart)
           {:native true
            :onClick (fn [emoji event]
                       (replace-with-emoji caret-pos emoji)
                       (remove-markers s)
                       (reset! visible false)
                       (.focus @last-active-element)
                       (when (fn? add-emoji-cb)
                          (add-emoji-cb @last-active-element emoji)))}))]]))