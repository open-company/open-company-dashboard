(ns open-company-web.components.su-snapshot-preview
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :as dommy :refer-macros (sel1 sel)]
            [open-company-web.api :as api]
            [open-company-web.router :as router]
            [open-company-web.dispatcher :as dis]
            [open-company-web.lib.utils :as utils]
            [open-company-web.lib.responsive :as responsive]
            [open-company-web.components.ui.menu :refer (menu)]
            [open-company-web.components.ui.navbar :refer (navbar)]
            [open-company-web.components.ui.back-to-dashboard-btn :refer (back-to-dashboard-btn)]
            [open-company-web.components.ui.icon :as i]
            [open-company-web.components.ui.onboard-tip :refer (onboard-tip)]
            [open-company-web.components.topics-columns :refer (topics-columns)]
            [open-company-web.components.su-preview-dialog :refer (su-preview-dialog item-input email-item)]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [goog.fx.Animation.EventType :as AnimationEventType]
            [goog.fx.dom :refer (Fade)]
            [goog.object :as gobj]
            [cljsjs.hammer]
            [cljsjs.react.dom]))

(defn ordered-topics-list []
  (let [topics (sel [:div.topic-row])
        topics-list (for [topic topics] (.data (js/jQuery topic) "topic"))]
    (vec (remove nil? topics-list))))

(defn post-stakeholder-update [owner]
  (om/set-state! owner :link-posting true)
  (api/share-stakeholder-update {}))

(defn stakeholder-update-data [data]
  (:stakeholder-update (dis/company-data data)))

(defn patch-stakeholder-update [owner]
  (let [title  (om/get-state owner :title)
        topics (om/get-state owner :su-topics)]
    (api/patch-stakeholder-update {:title (or title "")
                                   :sections topics})))

(defn share-clicked [owner]
 (patch-stakeholder-update owner)
 (om/set-state! owner :show-su-dialog :prompt))

(defn dismiss-su-preview [owner]
  (om/set-state! owner (merge (om/get-state owner) {:show-su-dialog false
                                                    :slack-loading false
                                                    :link-loading false
                                                    :email-loading false
                                                    :link-posting false
                                                    :link-posted false})))

(defn setup-sortable [owner options]
  (when-let [list-node (js/jQuery (sel1 [:div.topics-column]))]
    (.sortable list-node #js {:scroll true
                              :forcePlaceholderSize true
                              :placeholder "sortable-placeholder"
                              :items ".topic-row"
                              :axis "y"
                              :start (fn [event ui]
                                        (if-let [dragged-item (gobj/get ui "item")]
                                          (do 
                                            (om/set-state! owner :su-dragging-topic (.data dragged-item "topic"))
                                            (.addClass (js/$ dragged-item) "su-dragging-topic")
                                            (.addClass (js/$ (sel1 [:div.topics-columns])) "sortable-active"))))
                              :stop (fn [event ui]
                                      (if-let [dragged-item (gobj/get ui "item")]
                                        (do
                                          (.removeClass (js/$ dragged-item) "su-dragging-topic")
                                          (.removeClass (js/$ (sel1 [:div.topics-columns])) "sortable-active")
                                          (om/set-state! owner :su-topics (ordered-topics-list))))
                                      (om/set-state! owner :su-dragging-topic nil))
                              :opacity 1})))

(defn add-su-section [owner topic]
  (om/update-state! owner :su-topics #(conj % topic)))

(defn title-from-section-name [owner section]
  (let [company-data (dis/company-data (om/get-props owner))]
    (->> section keyword (get company-data) :title)))

(def topic-row-x-padding 40)

(defcomponent su-snapshot-preview [data owner options]

  (init-state [_]
    (let [company-data (dis/company-data data)
          su-data (stakeholder-update-data data)
          su-sections (if (empty? (:sections su-data))
                        (utils/filter-placeholder-sections (vec (:sections company-data)) company-data)
                        (utils/filter-placeholder-sections (:sections su-data) company-data))]
      {:columns-num (responsive/columns-num)
       :su-topics su-sections
       :title-focused false
       :title (:title su-data)
       :show-su-dialog false
       :link-loading false
       :slack-loading false
       :link-posting false
       :link-posted false}))

  (did-mount [_]
    (om/set-state! owner :did-mount true)
    (setup-sortable owner options)
    (events/listen js/window EventType/RESIZE #(om/set-state! owner :columns-num (responsive/columns-num))))

  (will-receive-props [_ next-props]
    (when-not (= (dis/company-data data) (dis/company-data next-props))
      (let [company-data (dis/company-data next-props)
            su-data      (stakeholder-update-data next-props)
            su-sections  (if (empty? (:sections su-data))
                           (vec (:sections company-data))
                           (utils/filter-placeholder-sections (:sections su-data) company-data))]
        (om/set-state! owner :su-topics su-sections)))
    ; share via link
    (when (om/get-state owner :link-loading)
      (if-not (om/get-state owner :link-posting)
        (post-stakeholder-update owner)
        ; show share url dialog
        (when (not= (dis/latest-stakeholder-update data) (dis/latest-stakeholder-update next-props))
          (om/set-state! owner :link-loading false)
          (om/set-state! owner :link-posted true)
          (om/set-state! owner :show-su-dialog true)))))

  (did-update [_ _ _]
    (setup-sortable owner options))

  (render-state [_ {:keys [columns-num
                           title-focused
                           title
                           show-su-dialog
                           email-loading
                           link-loading
                           slack-loading
                           link-posting
                           link-posted
                           su-topics]}]
    (let [share-medium (keyword (:medium @router/path))
          company-slug (router/current-company-slug)
          company-data (dis/company-data data)
          su-data      (stakeholder-update-data data)
          card-width   (responsive/calc-card-width 1)
          ww           (.-clientWidth (.-body js/document))
          title-width  (if (>= ww responsive/c1-min-win-width)
                          (str (if (< ww card-width) ww card-width) "px")
                          "auto")
          fields-width  (if (>= ww responsive/c1-min-win-width)
                           (str (if (< ww card-width) ww (- card-width 5)) "px")
                           "auto")
          total-width  (if (>= ww responsive/c1-min-win-width)
                          (str (if (< ww card-width) ww (+ card-width (* topic-row-x-padding 2))) "px")
                          "auto")
          su-subtitle  (str "— " (utils/date-string (js/Date.) [:year]))
          possible-sections (utils/filter-placeholder-sections (vec (:sections company-data)) company-data)
          topics-to-add (sort #(compare (title-from-section-name owner %1) (title-from-section-name owner %2)) (reduce utils/vec-dissoc possible-sections su-topics))]
      (dom/div {:class (utils/class-set {:su-snapshot-preview true
                                         :main-scroll true})}
        (when (and (seq company-data)
                   (empty? (:sections su-data)))
          (onboard-tip {
            :id (str "update-preview-" company-slug)
            :once-only true
            :mobile false
            :desktop "This is a preview of your update. You can drag topics to reorder, and you can remove them by clicking the \"X\"."
            :css-class "large"
            :dismiss-tip-fn #(.focus (sel1 [:input#su-snapshot-preview-title]))}))
        (om/build menu data)
        (dom/div {:class "page snapshot-page"}
          (dom/div {:class "su-snapshot-header"}
            (back-to-dashboard-btn {})
            (dom/div {:class "snapshot-cta"} "Choose the topics to share and arrange them in any order.")
            (dom/div {:class "share-su"}
              (dom/button {:class "btn-reset btn-solid share-su-button"
                           :on-click #(share-clicked owner)
                           :disabled (zero? (count su-topics))}
                "SHARE " (dom/i {:class "fa fa-share"}))))
          ;; SU Snapshot Preview
          (when company-data
            (dom/div {:class "su-sp-content group"
                      :key (apply str su-topics)}
              (dom/div {:class "su-sp-company-header group"}
                (when (not= share-medium :email)
                  (dom/div {:class "company-logo-container group"}
                    (dom/img {:class "company-logo" :src (:logo company-data)})))
                (when (and (:title su-data) (not= share-medium :email))
                  (dom/div {:class "preview-field-container group"
                            :style #js {:width fields-width}}
                    (dom/input #js {:className "preview-title"
                                    :id "su-snapshot-preview-title"
                                    :type "text"
                                    :value title
                                    :ref "preview-title"
                                    :placeholder "Title of this Update"
                                    :onChange #(om/set-state! owner :title (.. % -target -value))})))
                (when (= share-medium :email)
                  (dom/div {:class "preview-field-container group"
                            :style #js {:width fields-width}}
                    (dom/label {} "To")
                    (dom/div {:class "preview-to"}
                      (item-input {:item-render email-item
                                   :match-ptn #"(\S+)[,|\s]+"
                                   :split-ptn #"[,|\s]+"
                                   :container-node :div.npt.pt1.pr1.pl1.mh4.overflow-auto.preview-to-field
                                   :input-node :input.border-none.outline-none.mr.mb1
                                   :valid-item? utils/valid-email?
                                   :on-change (fn [val] (dis/dispatch! [:input [:su-share :email :to] val]))}))
                    (when-let [to-field (->> data :su-share :email :to)]
                      (cond
                        (not (seq to-field))
                        (dom/span {:class "left red pt1 ml1"} "Required")
                        (not (every? utils/valid-email? to-field))
                        (dom/span {:class "left red pt1 ml1"} "Not a valid email address")))))
                (when (= share-medium :email)
                  (dom/div {:class "preview-field-container group"
                            :style #js {:width fields-width}}
                    (dom/label {} "Subject")
                    (dom/div {:class "preview-subject"}
                      (dom/input #js {:className "preview-subject-field"
                                      :type "text"
                                      :value (-> data :su-share :email :subject)
                                      :on-change #(dis/dispatch! [:input [:su-share :email :subject] (.val (js/$ ".preview-subject-field"))])}))))
                (when (= share-medium :email)
                  (dom/div {:class "preview-field-container"
                            :style #js {:width fields-width}}
                    (dom/label {} "Note")
                    (dom/div {:class "preview-note"}
                      (dom/input #js {:className "preview-note-field"
                                      :type "text"
                                      :value (-> data :su-share :email :note)
                                      :on-change #(dis/dispatch! [:input [:su-share :email :note] (.val (js/$ ".preview-note-field"))])})))))
              (when show-su-dialog
                (om/build su-preview-dialog {:selected-topics (:sections su-data)
                                             :company-data company-data
                                             :latest-su (dis/latest-stakeholder-update)
                                             :share-via-slack slack-loading
                                             :share-via-email email-loading
                                             :share-via-link (or link-loading link-posted)
                                             :su-title title}
                                            {:opts {:dismiss-su-preview #(dismiss-su-preview owner)}}))
              (om/build topics-columns {:columns-num 1
                                        :card-width card-width
                                        :total-width total-width
                                        :is-stakeholder-update true
                                        :content-loaded (not (:loading data))
                                        :topics su-topics
                                        :su-dragging-topic (om/get-state owner :su-dragging-topic)
                                        :su-medium share-medium
                                        :company-data company-data
                                        :show-share-remove true
                                        :topics-data company-data
                                        :hide-add-topic true}
                                       {:opts {:share-remove-click (fn [topic]
                                                                      (let [fade-anim (Fade. (sel1 [(str "div#topic-" topic)]) 1 0 utils/oc-animation-duration)]
                                                                        (doto fade-anim
                                                                          (events/listen AnimationEventType/FINISH
                                                                            (fn [] (om/set-state! owner :su-topics (utils/vec-dissoc (om/get-state owner :su-topics) topic))))
                                                                          (.play))))}})))
          ;; Add section container
          (when (pos? (count topics-to-add))
            (dom/div {:class "su-preview-add-section-container"}
              (dom/div {:class "su-preview-add-section"
                        :style #js {:width total-width}}
                (dom/div {:class "add-header"} "Topics You Can Add to this Update")
                (for [topic topics-to-add
                      :let [title (->> topic keyword (get company-data) :title)]]
                  (dom/div {:class "add-section"
                            :on-click #(add-su-section owner topic)}
                    (i/icon :check-square-09 {:accent-color "transparent" :size 16 :color "black"})
                    (dom/div {:class "section-name"} title)))))))))))