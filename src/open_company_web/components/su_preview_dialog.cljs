(ns open-company-web.components.su-preview-dialog
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :refer-macros (sel1)]
            [rum.core :as rum]
            [clojure.string :as string]
            [goog.dom :as gdom]
            [goog.style :as gstyle]
            [open-company-web.api :as api]
            [open-company-web.dispatcher :as dis]
            [open-company-web.router :as router]
            [open-company-web.urls :as oc-urls]
            [open-company-web.lib.utils :as utils]
            [open-company-web.components.ui.icon :as i]
            [open-company-web.components.ui.small-loading :as loading]
            [open-company-web.components.ui.emoji-picker :refer (emoji-picker)]
            [org.martinklepsch.derivatives :as drv]
            [cljsjs.react.dom]
            [cljsjs.clipboard]))

(defn send-clicked [type]
  (let [post-data (get-in @dis/app-state [:su-share type])
        emojied   (update post-data :note (fnil utils/unicode-emojis ""))]
    (dis/dispatch! [:su-share/reset])
    (api/share-stakeholder-update {type emojied})))

(defn select-share-link [event]
  (when-let [input (.-target event)]
    (.setSelectionRange input 0 (count (.-value input)))))

;; Rum Mixins

(def emoji-autocomplete
  {:did-mount (fn [s] (js/emojiAutocomplete) s)})

(defn clipboard-mixin [btn-selector]
  {:did-mount    (fn [s] (assoc s ::clipboard (js/Clipboard. btn-selector)))
   :will-unmount (fn [s] (.destroy (::clipboard s)) s)})

;; Modal components

(rum/defc modal-title < rum/static
  [title icon-id]
  [:h3.m0.px3.py25.gray5.domine
   {:style {:border-bottom  "solid 1px rgba(78, 90, 107, 0.1)"}}
   (when icon-id
     (if (= :slack icon-id)
       [:i {:class "fa fa-slack mr2"}]
       (i/icon icon-id {:class "inline mr2"
                        :color :oc-gray-3
                        :accent-color :oc-gray-3})))
   title])

;; TODO (mk) Revert the following commit to restore on-change behavior as described by docstring
;; https://github.com/open-company/open-company-web/pull/161/commits/eb4b29ead8e45388c8edb48d74801b2ba16c1765
(rum/defcs item-input
  "An input that accepts multiple items of things

  Options
  :item-render Should be a function receiving three arguments: the item,
               a function to delete the item and a boolean field to indicate
               if this item is valid or not
  :on-change is called with the list of items whenever it updates this may include
             invalid items which are currently being typed out
  :match-ptn regex-pattern will be used to extract a 'submitted' value from the input
  :split-ptn regex-pattern will be used to split a string which might contain multiple values
             used to match items in pasted strings
  :tab-index (default 0) sets tab-index on container node
  :valid-item? (optional) Takes a value returned by `submitted` and returns true
               if it is valid otherwise false
  :container-node (default :div) Provide a different node for the container
  :input-node (default :input) Provide a different node for the input field"
  < (rum/local true ::show-input?) (rum/local [] ::items) (rum/local "" ::input)
  [s {:keys [item-render on-change match-ptn split-ptn tab-index
             valid-item? container-node input-node]
      :or {valid-item? identity
           tab-index 0
           container-node :div
           input-node :input}}]
  (let [*items       (::items s) ; tracking items already entered
        *input       (::input s) ; tracking value of input field
        *show-input? (::show-input? s)
        submitted    (fn [v] (second (first (re-seq match-ptn v))))
        remove-item! (fn [v]
                       (on-change (swap! *items #(filterv (comp not #{v}) %))))
        clear-input! (fn [] (reset! *input "") (on-change @*items))
        submit!      (fn [v]
                       (when (valid-item? v)
                         (clear-input!)
                         (on-change (swap! *items #(vec (distinct (conj % v)))))))
        maybe-submit (fn [v]
                       (if-let [s' (submitted v)]
                         (submit! s')
                         (reset! *input v)))]
    [container-node
     {:on-click #(reset! *show-input? true)
      :on-focus #(reset! *show-input? true)
      :tab-index tab-index}
     (for [e @*items]
       (rum/with-key (item-render e #(remove-item! e) (valid-item? e)) e))
     (cond
       ;; Render the current input as invalid item
       (and (not @*show-input?) (not (string/blank? @*input)))
       (item-render @*input clear-input! false)

       ;; Render an input to maintain same spacing
       (and (not @*show-input?) (not (seq @*items)))
       [:div {:style {:visibility "hidden" :pointer-events "none"}} [input-node {:class "col-12"}]]

       ;; Render actual input to add new items
       @*show-input?
       [input-node
        {:type      "text"
         :class     (when-not (seq @*items) "col-12")
         :placeholder (when-not (seq @*items) "investor@vc.com,advisor@smart.com")
         :auto-focus true
         :value      @*input
         :on-paste   #(let [pasted (string/split (.getData (.-clipboardData %) "Text") split-ptn)]
                        (.stopPropagation %)
                        (on-change (swap! *items into pasted)))
         :on-key-down #(when (and (= 8 (.-keyCode %)) (empty? @*input))
                         (on-change (swap! *items (comp vec drop-last))))
         :on-blur   (fn [e]
                      (when-not (string/blank? (.. e -target -value))
                        (clear-input!)
                        (on-change (swap! *items #(vec (distinct (conj % (.. e -target -value)))))))
                      (when (seq @*items) (reset! *show-input? false))
                      nil)
         :on-change #(maybe-submit (.. % -target -value))}])]))

(rum/defc email-item [v delete! submitted?]
  [:div.inline-block.mr1.mb1.rounded
   {:class (when-not submitted? "border b--red")
    :style (when submitted? {:backgroundColor "rgba(78, 90, 107, 0.1)"})}
   [:span.inline-block.p1 v
    [:button.btn-reset.p0.ml1
     {:on-click #(delete!)}
     "x"]]])

(defn email-note-did-change []
  (let [email-notes (utils/emoji-images-to-unicode (.-innerHTML (sel1 [:div.email-note])))]
    (dis/dispatch! [:input
                    [:su-share :email :note]
                    email-notes])))

(rum/defcs email-dialog < rum/static
                          rum/reactive
                          (drv/drv :su-share)
                          emoji-autocomplete
                          (rum/local false ::subject-focused)
  [{:keys [::subject-focused] :as s} {:keys [share-link]}]
  [:div
   (modal-title "Share by Email" :email-84)
   [:div.p3
    [:div
     [:label.block.small-caps.bold.mb2
      "To"
      (when-let [to-field (->> (drv/react s :su-share) :email :to)]
        (cond
          (not (seq to-field))
          [:span.red.py1 " — Required"]
          (not (every? utils/valid-email? to-field))
          [:span.red.py1 " — Not a valid email address"]))]
     (item-input {:item-render email-item
                  :match-ptn #"(\S+)[,|\s]+"
                  :split-ptn #"[,|\s]+"
                  :container-node :div.npt.pt1.pr1.pl1.mb3.mh4.overflow-auto
                  :input-node :input.border-none.outline-none.mr.mb1
                  :valid-item? utils/valid-email?
                  :on-change (fn [val] (dis/dispatch! [:input [:su-share :email :to] val]))})]
    [:label.block.small-caps.bold.mb2
      "Subject"
      (when-let [subject-field (->> (drv/react s :su-share) :email :subject)]
        (cond
          (and @subject-focused (string/blank? subject-field))
          [:span.red.py1 " — Required"]))]
    [:input.domine.npt.p1.col-12.mb3
     {:type "text"
      :on-change #(dis/dispatch! [:input [:su-share :email :subject] (.. % -target -value)])
      :on-blur #(reset! subject-focused true)
      :value (-> (drv/react s :su-share) :email :subject)}]
    [:label.block.small-caps.bold.mb2 "Your Note"]
    [:div.npt.group
      [:div.domine.p1.col-12.emoji-autocomplete.ta-mh.no-outline.emojiable.email-note
       {:content-editable true
        :on-key-down #(email-note-did-change)
        :on-key-up #(email-note-did-change)
        :placeholder "Optional note to go with this update."}]
      [:div.group
        {:style {:min-height "25px"}}
        [:div.left
          {:style {:color "rgba(78, 90, 107, 0.5)"}}
          (emoji-picker {:add-emoji-cb (fn [_] (email-note-did-change))})]]]]])

(defn slack-note-did-change []
  (let [slack-notes (utils/emoji-images-to-unicode (.-innerHTML (sel1 [:div.slack-note])))]
    (dis/dispatch! [:input
                    [:su-share :slack :note]
                    slack-notes])))

(rum/defc slack-dialog < rum/static emoji-autocomplete
  []
  [:div
   (modal-title "Share to Your Slack Team" :slack)
   [:div.p3
    [:label.block.small-caps.bold.mb2 "Your Note"]
    [:div.npt.group
      [:div.domine.p1.col-12.emoji-autocomplete.ta-mh.no-outline.emojiable.slack-note
        {:content-editable true
         :placeholder "Optional note to go with this update."
         :on-key-down #(slack-note-did-change)
         :on-key-up #(slack-note-did-change)}]
      [:div.group
        {:style {:min-height "25px"}}
        [:div.left
          {:style {:color "rgba(78, 90, 107, 0.5)"}}
          (emoji-picker {:add-emoji-cb (fn [_] (slack-note-did-change))})]]]]])

(rum/defcs link-dialog < (rum/local false ::copied)
                         (rum/local false ::clipboard)
                         (clipboard-mixin ".js-copy-btn")
  [{:keys [::copied] :as _state} link]
  [:div
   (modal-title  "Share a Link" :link-72)
   [:div.p3
    [:label.block.small-caps.bold.mb2 "Share this private link"]
    [:div.flex
     [:input.domine.npt.p1.flex-auto
      {:type "text"
       :id "share-link-input"
       :on-focus select-share-link
       :on-key-up select-share-link
       :value link}]
     [:button {:class "btn-reset btn-solid js-copy-btn"
               :data-clipboard-target "#share-link-input"
               :on-click #(reset! copied true)}
      (if @copied "COPIED ✓" "COPY")]]
    [:div.block.mt2
     [:a.small-caps.underline.bold.dimmed-gray
      {:href link :target "_blank"}
      "Open in New Window"]]]])

(rum/defc prompt-dialog < rum/static
  [prompt-cb]
  [:div
   (modal-title "Share Update" nil)
   [:div.p3
    ; Temporarily comment out until Slack bot is fixed.
    ; [:div.group
    ;  [:button.btn-reset {:on-click #(prompt-cb :slack)}
    ;   [:div.circle50.left [:img {:src "/img/Slack_Icon.png" :style {:width "20px" :height "20px"}}]]
    ;   [:span.left.ml1.gray5.h6 {} "SHARE TO SLACK"]]]
    [:div.group
     [:button.btn-reset {:on-click #(prompt-cb :email)}
      [:div.circle50.left (i/icon :email-84 {:color "rgba(78,90,107,0.6)" :accent-color "rgba(78,90,107,0.6)" :size 20})]
      [:span.left.ml1.gray5.h6 {} "SHARE BY EMAIL"]]]
    [:div.group
     [:button.btn-reset {:on-click #(prompt-cb :link)}
      [:div.circle50.left (i/icon :link-72 {:color "rgba(78,90,107,0.6)" :accent-color "rgba(78,90,107,0.6)" :size 20})]
      [:span.left.ml1.gray5.h6 {} "SHARE A LINK"]]]]])

(rum/defcs modal-actions < rum/reactive (drv/drv :su-share)
  [s send-fn cancel-fn type]
  [:div.px3.pb3.right-align
   [:button.btn-reset.btn-outline
    {:class (when-not (or (= :link type) (= :prompt type)) "mr1")
     :on-click cancel-fn}
    (if (= :link type) "DONE" "CANCEL")]
   (when-not (or (= :link type) (= :prompt type))
     [:button.btn-reset.btn-solid
      {:on-click send-fn
       :disabled (when (= :email type)
                   (let [to (->> (drv/react s :su-share) :email :to)
                         subject (->> (drv/react s :su-share) :email :subject)]
                     (or (not (and (seq to) (every? utils/valid-email? to)))
                         (string/blank? subject))))}
      (case type
        :sent "SENT ✓"
        :sending (loading/small-loading {})
        "Send")])])

(rum/defc confirmation < rum/static
  [type cancel-fn]
  [:div
   (case type
     :email (modal-title "Email Sent!" :email-84)
     :slack (modal-title "Shared via Slack!" :slack))
   [:div.p3
    [:p.domine
     (case type
       :email "Recipients will get your update by email."
       :slack "Members of your Slack organization will get your update.")]
    [:div.right-align.mt3
     [:button.btn-reset.btn-solid
      {:on-click cancel-fn}
      "DONE"]]]])

(defn reset-scroll-height []
  (let [main-scroll (gdom/getElementByClass "main-scroll")]
    (gstyle/setStyle (.-body js/document) #js {:overflow "auto"})
    (gstyle/setStyle main-scroll #js {:height "auti"})))

(defn setup-scroll-height []
  (let [main-scroll       (gdom/getElementByClass "main-scroll")
        su-preview-window (js/$ ".su-preview-window")
        window-height     (max (+ (.height su-preview-window) (* (.-top (.offset su-preview-window)) 2))
                               (.height (js/$ js/window)))]
    (gstyle/setStyle (.-body js/document) #js {:overflow "hidden"})
    (gstyle/setStyle main-scroll #js {:height (str window-height "px")})))

(defcomponent su-preview-dialog [data owner options]

  (init-state [_]
    {:share-via (cond (:share-via-email data) :email
                      (:share-via-slack data) :slack
                      (:share-via-link data)  :link
                      :else                   :prompt)
     :share-link (:latest-su data)
     :sending false
     :sent false})

  (did-mount [_]
    (dis/dispatch! [:input [:su-share :email :subject] (:su-title data)])
    (setup-scroll-height))

  (did-update [_ _ _]
    (setup-scroll-height))

  (will-unmount [_]
    (dis/dispatch! [:su-share/reset])
    (reset-scroll-height))

  (will-receive-props [_ next-props]
    ; slack SU posted
    (when (and (= (om/get-state owner :share-via) :link)
               (:latest-su next-props))
      (om/set-state! owner :share-link (:latest-su next-props)))
    (when (and (#{:email :slack} (om/get-state owner :share-via))
               (om/get-state owner :sending)
               (not (om/get-state owner :sent)))
      (om/set-state! owner :sent true)))

  (render-state [_ {:keys [share-via share-link sending sent] :as state}]
    (let [company-data (:company-data data)
          cancel-fn    (:dismiss-su-preview options)]
      (dom/div {:class "su-preview-dialog"}
        (dom/div {:class "su-preview-window"}
          (dom/button
              {:class "absolute top-0 btn-reset" :style {:left "100%"}
               :on-click #(cancel-fn)}
            (i/icon :simple-remove {:class "inline mr1" :stroke "4" :color "white" :accent-color "white"}))
          (if sent
            (confirmation share-via cancel-fn)
            (dom/div {:class "su-preview-box"}
              (case share-via
                :prompt (prompt-dialog #(do
                                          (when (= % :link)
                                            (api/share-stakeholder-update {}))
                                          (om/set-state! owner :share-via %)))
                :link  (link-dialog share-link)
                :email (email-dialog {:share-link share-link})
                :slack (slack-dialog))
              (modal-actions
                (if sent
                  cancel-fn
                  #(do (om/set-state! owner :sending true)
                       (send-clicked share-via)))
                cancel-fn
                (cond (and sending (not sent)) :sending
                      (and sending sent)       :sent
                      :else                    share-via)))))))))