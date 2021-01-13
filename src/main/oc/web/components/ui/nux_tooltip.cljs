(ns oc.web.components.ui.nux-tooltip
  (:require [rum.core :as rum]
            [oops.core :refer (oset!)]
            [dommy.core :as dommy]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.utils.dom :as dom-utils]
            [oc.web.actions.nux :as nux-actions]
            [oc.web.lib.utils :as utils]))

(defn- scroll-to-tooltip [data el]
  (case (:scroll data)
    :top
    (oset! js/document "scrollingElement.scrollTop" (utils/page-scroll-top))
    :element
    (.scrollIntoView el #js {:block "center"})
    nil))

(defn- remove-old-tooltip-handle [state key]
  (when (not= key @(::last-key state))
    (when-let [old-el (dommy/sel1 @(::last-sel state))]
      (when (dommy/has-class? old-el :nux-tooltip-handle)
        (dommy/remove-class! old-el :nux-tooltip-handle)))))

(defn- next-tooltip [state el data]
  (let [key (-> state :rum/args first :key)
        new-key? (not= @(::last-key state) key)
        _ (when new-key?
            (scroll-to-tooltip data el))
        rect (dom-utils/bounding-rect el)
        pos (case (:position data)
              :top
              {:x (:x rect)
              :y (+ (:y rect) (:height rect))}
              :right
              {:x (+ (:x rect) (:width rect))
              :y (+ (:y rect) (/ (:height rect) 2))}
              :bottom
              {:x (+ (:x rect) (/ (:width rect) 2))
              :y (+ (:y rect) (:height rect))}
              :left
              {:x (:x rect)
              :y (+ (:y rect) (/ (:height rect) 2))}
              :bottom-left
              {:x (- (dom-utils/window-width) 24)
              :y (+ (:y rect) (:height rect))}
              :right-top
              {:x (+ (:x rect) (:width rect))
               :y (+ (:y rect) (/ (:height rect) 2))}
              :left-top
              {:x (:x rect)
               :y (+ (:y rect) (/ (:height rect) 2))})]
    (dommy/add-class! el :nux-tooltip-handle)
    (when-not (= @(::pos state) pos)
      (reset! (::pos state) pos))
    (when new-key?
      (remove-old-tooltip-handle state key)
      (reset! (::last-key state) key)
      (reset! (::last-sel state) (:sel data)))))

(defn- check-data [state]
  (let [data (-> state :rum/args first :data)
        el (dommy/sel1 (:sel data))]
    (if el
      (next-tooltip state el data)
      (do
        (when @(::pos state)
          (reset! (::pos state) nil))
        (when (fn? (:show-el-cb data))
          ((:show-el-cb data))
          (utils/after 1500 #(reset! (::rand state) (rand 10000))))))))

(rum/defcs nux-tooltip < rum/static
  (rum/local nil ::pos)
  (rum/local nil ::last-key)
  (rum/local nil ::last-sel)
  (rum/local nil ::rand)
  {:will-mount (fn [state]
                 (check-data state)
                 state)
   :did-update (fn [state]
                 (check-data state)
                 state)}
  [state
   {user-type                               :user-type
    dismiss-cb                              :dismiss-cb
    next-cb                                 :next-cb
    prev-cb                                 :prev-cb
    key                                     :key
    {:keys [title description next-title back-title
            steps arrow-position position sel key
            post-dismiss-cb post-next-cb post-prev-cb]
     :as data}                              :data}]
  (let [{left :x top :y} @(::pos state)]
    (when (and data left top)
      [:div.nux-tooltip-container
       {:on-mouse-down utils/event-stop}
        [:div.nux-tooltip
          {:class (utils/class-set {position true})
            :style (clj->js {:left (str left "px")
                             :top (str top "px")})
            :key (str "nux-tooltip-" key)}
          (when arrow-position
            [:div.triangle
              {:class (utils/class-set {arrow-position true})}])
          [:div.nux-tooltip-inner
            [:div.nux-tooltip-header
            [:div.nux-tooltip-title
              title]
            [:button.mlb-reset.nux-tooltip-dismiss-bt
              {:on-click (fn [e]
                           (utils/event-stop e)
                           (when-let [el (dommy/sel1 sel)]
                             (utils/after 100 #(dommy/remove-class! el :nux-tooltip-handle)))
                           (dismiss-cb e)
                           (when (fn? post-dismiss-cb)
                             (post-dismiss-cb)))}]]
            [:div.nux-tooltip-description
            description]
            [:div.nux-tooltip-footer
             [:div.nux-tooltip-back-bt-container
              (when back-title
                [:button.mlb-reset.nux-tooltip-back-bt
                  {:on-click (fn [e]
                              (utils/event-stop e)
                              (when-let [el (dommy/sel1 sel)]
                                (utils/after 100 #(dommy/remove-class! el :nux-tooltip-handle)))
                              (prev-cb e)
                              (when (fn? post-prev-cb)
                                (post-prev-cb)))}
                  back-title])]
             [:div.nux-tooltip-steps
              steps]
             [:div.nux-tooltip-next-bt-container
              (when next-title
                [:button.mlb-reset.nux-tooltip-next-bt
                 {:on-click (fn [e]
                             (utils/event-stop e)
                             (when-let [el (dommy/sel1 sel)]
                               (utils/after 100 #(dommy/remove-class! el :nux-tooltip-handle)))
                             (next-cb e)
                             (when (fn? post-next-cb)
                               (post-next-cb)))}
                 next-title])]]]]])))

(rum/defcs nux-tooltips-manager <
  rum/reactive
  (drv/drv :nux)
  [s]
  (let [{key :key user-type :user-type} (drv/react s :nux)]
    (when key
      (nux-tooltip {:key key
                    :user-type user-type
                    :data (nux-actions/get-tooltip-data key user-type)
                    :dismiss-cb #(nux-actions/dismiss-nux)
                    :next-cb #(nux-actions/next-step)
                    :prev-cb #(nux-actions/prev-step)}))))