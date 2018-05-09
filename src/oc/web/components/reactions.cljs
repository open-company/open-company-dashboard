(ns oc.web.components.reactions
  (:require-macros [dommy.core :refer (sel1)]
                   [if-let.core :refer (when-let*)])
  (:require [rum.core :as rum]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.responsive :as responsive]
            [oc.web.lib.react-utils :as react-utils]
            [oc.web.actions.reaction :as reaction-actions]
            [cljsjs.react]
            [cljsjs.react.dom]
            [cljsjs.emoji-mart]
            [goog.events :as events]
            [goog.object :as gobj]
            [goog.events.EventType :as EventType]))

(defn can-pick-reaction
  "Given an emoji and the list of the current reactions
   check if the user can react.
   A user can react if:
   - the reaction is NOT already in the reactions list
   - the reaction is already in the reactions list and its not reacted"
  [emoji reactions-data]
  (let [reaction-map (first (filter #(= (:reaction %) emoji) reactions-data))]
    (or (not reaction-map)
        (and (map? reaction-map)
             (not (:reacted reaction-map))))))

(def default-reaction-number 5)

(rum/defcs reactions < (rum/local false ::show-picker)
                       (rum/local nil ::window-click)
                       {:did-mount (fn [s]
                         (reset!
                          (::window-click s)
                          (events/listen
                           js/window
                           EventType/CLICK
                           #(when-not (utils/event-inside? % (rum/dom-node s))
                              (reset! (::show-picker s) false))))
                         s)
                        :will-unmount (fn [s]
                         (when @(::window-click s)
                           (events/unlistenByKey @(::window-click s))
                           (reset! (::window-click s) nil))
                         s)}
  [s entry-data]
  (let [reactions-data (vec (:reactions entry-data))
        reactions-loading (:reactions-loading entry-data)
        react-link (utils/link-for (:links entry-data) "react")
        should-show-picker? (and react-link
                                 (< (count reactions-data) default-reaction-number))]
    ;; If there are reactions to render or there is at least the link to add a reaction from the picker
    (when (or (seq reactions-data)
              should-show-picker?)
      [:div.reactions
        [:div.reactions-list
          (when (seq reactions-data)
            (for [idx (range (count reactions-data))
                  :let [reaction-data (get reactions-data idx)
                        is-loading (utils/in? reactions-loading (:reaction reaction-data))
                        reacted (:reacted reaction-data)
                        reaction-authors (:authors reaction-data)
                        multiple-reaction-authors? (> (count reaction-authors) 1)
                        attribution-start (if multiple-reaction-authors? "Reactions" "Reaction")
                        attribution-end (if multiple-reaction-authors?
                                          (str (clojure.string/join ", " (butlast reaction-authors))
                                               " and "
                                               (last reaction-authors))
                                          (first reaction-authors))
                        reaction-attribution (if (empty? reaction-authors)
                                                ""
                                                (str attribution-start " by " attribution-end))
                        read-only-reaction (not (utils/link-for
                                                 (:links reaction-data)
                                                 "react"
                                                 (if reacted "DELETE" "PUT")))
                        r (if is-loading
                            (merge reaction-data {:count (if reacted
                                                          (dec (:count reaction-data))
                                                          (inc (:count reaction-data)))
                                                  :reacted (not reacted)})
                            reaction-data)]]

              [:button.reaction-btn.btn-reset.fs-hide
                {:key (str "reaction-" (:uuid entry-data) "-" idx)
                 :class (utils/class-set {:reacted (:reacted r)
                                          :can-react (not read-only-reaction)
                                          :has-reactions (pos? (:count r))})
                 :on-mouse-leave #(this-as this
                                   (utils/remove-tooltips)
                                   (.tooltip (js/$ this)))
                 :title reaction-attribution
                 :data-placement "top"
                 :data-container "body"
                 :data-toggle "tooltip"
                 :on-click (fn [e]
                             (when (and (not is-loading) (not read-only-reaction))
                               (when (and (not (:reacted r))
                                          (not (js/isSafari))
                                          (not (js/isEdge))
                                          (not (js/isIE)))
                                 ;;TODO: animate reaction
                                 )
                               (reaction-actions/reaction-toggle entry-data r (not reacted))))}
                [:span.reaction
                  {:class (when (pos? (:count r)) "has-count")}
                  (:reaction r)]
                [:div.count
                  (:count r)]]))
          (when should-show-picker?
            [:button.reaction-btn.btn-reset.can-react.reaction-picker
              {:key (str "reaction-" (:uuid entry-data) "-picker")
               :on-click #(reset! (::show-picker s) (not @(::show-picker s)))}
              [:span.reaction]
              [:div.count "+"]])]
       (when @(::show-picker s)
         [:div.reactions-picker-container
           (when (responsive/is-tablet-or-mobile?)
             [:button.mlb-reset.dismiss-mobile-picker
               {:on-click #(reset! (::show-picker s) false)}
               "Cancel"])
           (when-not (utils/is-test-env?)
             (react-utils/build (.-Picker js/EmojiMart)
               {:native true
                :onClick (fn [emoji event]
                           (when (can-pick-reaction (gobj/get emoji "native") reactions-data)
                             (reaction-actions/react-from-picker entry-data (gobj/get emoji "native")))
                           (reset! (::show-picker s) false))}))])])))