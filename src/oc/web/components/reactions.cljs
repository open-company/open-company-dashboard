(ns oc.web.components.reactions
  (:require-macros [dommy.core :refer (sel1)]
                   [if-let.core :refer (when-let*)])
  (:require [rum.core :as rum]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.jwt :as jwt]
            [cljsjs.web-animations]))

(defn animate-reaction [e s]
  (when-let* [target (.-currentTarget e)
              span-reaction (sel1 target :span.reaction)]
    (doseq [i (range 5)]
      (let [cloned-el (.cloneNode span-reaction true)
            translate-y {:transform #js ["translateY(0px)" "translateY(-80px)"]
                         :opacity #js [1 0]}
            v (+ 7 (* 3 (int (rand 4))))]
        (set! (.-opacity (.-style cloned-el)) 0)
        (set! (.-position (.-style cloned-el)) "absolute")
        (set! (.-left (.-style cloned-el)) (str v "px"))
        (set! (.-top (.-style cloned-el)) "2px")
        (.appendChild (.-parentElement span-reaction) cloned-el)
        (.animate
         cloned-el
         (clj->js translate-y)
         (clj->js {:duration 800 :delay (* 150 i) :fill "forwards" :easing "ease-out"}))
        (utils/after (+ 800 200 (* 4 150)) #(.removeChild (.-parentNode cloned-el) cloned-el))))))

(defn- is-comment-reaction?
  [item-data]
  (not (= (:content-type item-data) "entry")))

(defn- read-only?
  [item-data reaction-data]
  (if (is-comment-reaction? item-data)
    (= (jwt/user-id) (:user-id (:author item-data)))
    (not (utils/link-for (:links reaction-data) "react" ["PUT" "DELETE"]))))

(defn- reaction-class-helper
  [item-data r]
  (when (is-comment-reaction? item-data)
    (utils/class-set {:no-reactions (not (pos? (:count r)))
                      :comment true})))

(defn- reaction-display-helper
  "Display is different if reaction is on an entry vs a comment."
  [item-data r]
  (let [count (:count r)
        reacted (:reacted r)]
    (if (not (is-comment-reaction? item-data))
      (str "+" count)
      (if (pos? count)
        (if reacted
          (str "You"
               (when (> count 1) (str " and +" (dec count)))
               " agreed")
          (str "+" count " agreed"))
        (str "Agree")))))

(defn- display-reactions?
  "
   We want to skip the reactions display if the data is a comment,
   and there are no reactions and the owner of the comment is the current user.
  "
  [item-data]
  (let [reactions-data (:reactions item-data)
        reaction (when (= (count reactions-data) 1)
                   (first reactions-data))
        owner? (= (jwt/user-id) (:user-id (:author item-data)))
        skip? (when (is-comment-reaction? item-data)
                (when (and owner? (zero? (:count reaction)))
                  true))]
    (not skip?)))

(rum/defcs reactions
  [s item-data]
  (when (seq (:reactions item-data))
    (let [reactions-data (:reactions item-data)
          reactions-loading (:reactions-loading item-data)]
      (when (display-reactions? item-data)
       [:div.reactions
        (for [idx (range (count reactions-data))
              :let [reaction-data (get reactions-data idx)
                    is-loading (utils/in? reactions-loading (:reaction reaction-data))
                    read-only-reaction (read-only? item-data reaction-data)
                    r (if is-loading
                        (merge reaction-data {:count (if (:reacted reaction-data)
                                                      (dec (:count reaction-data))
                                                      (inc (:count reaction-data)))
                                              :reacted (not (:reacted reaction-data))})
                        reaction-data)]]
          [:button.reaction-btn.btn-reset
            {:key (str "-entry-" (:uuid item-data) "-" idx)
             :class (utils/class-set {:reacted (:reacted r)
                                      :can-react (not read-only-reaction)
                                      :has-reactions (pos? (:count r))
                                      :comment (is-comment-reaction? item-data)})
             :on-click (fn [e]
                         (when (and (not is-loading) (not read-only-reaction))
                           (when (and (not (:reacted r))
                                      (not (js/isSafari))
                                      (not (js/isEdge))
                                      (not (js/isIE)))
                             (animate-reaction e s))
                           (dis/dispatch! [:reaction-toggle item-data r])))}
            [:span.reaction
              {:class (reaction-class-helper item-data r)}
              (:reaction r)]
            [:div.count
              (reaction-display-helper item-data r)]])]))))