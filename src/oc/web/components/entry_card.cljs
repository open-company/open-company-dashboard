(ns oc.web.components.entry-card
  (:require [rum.core :as rum]
            [cuerdas.core :as s]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.oc-colors :refer (get-color-by-kw)]
            [oc.web.components.ui.user-avatar :refer (user-avatar-image)]
            [oc.web.components.reactions :refer (reactions)]
            [oc.web.components.ui.interactions-summary :refer (interactions-summary)]
            [goog.object :as gobj]))

(rum/defc entry-card-empty
  [read-only?]
  [:div.entry-card.empty-state.group
    [:div.empty-state-content
      [:img {:src (utils/cdn "/img/ML/entry_empty_state.svg")}]
      [:div.entry-card-title
        "This topic’s a little sparse. "
        (when-not read-only?
          [:button.mlb-reset
            {:on-click #(dis/dispatch! [:entry-edit {}])}
            "Add an update?"])]]])

(defn delete-clicked [e entry-data]
  (utils/event-stop e)
  (let [alert-data {:icon "/img/ML/trash.svg"
                    :message "Delete this entry?"
                    :link-button-title "No"
                    :link-button-cb #(dis/dispatch! [:alert-modal-hide])
                    :solid-button-title "Yes"
                    :solid-button-cb #(do
                                        (dis/dispatch! [:entry-delete entry-data])
                                        (dis/dispatch! [:alert-modal-hide]))
                    }]
    (dis/dispatch! [:alert-modal-show alert-data])))

(defn truncate-body [body-sel is-all-activity]
  (.dotdotdot (js/$ body-sel)
   #js {:height (* 24 (if is-all-activity 4 3))
        :wrap "word"
        :watch true
        :ellipsis "... "
        :after (when-not is-all-activity "a.read-more")}))

(defn get-first-body-thumbnail [body]
  (let [$body (js/$ (str "<div>" body "</div>"))
        thumb-els (js->clj (js/$ "img:not(.emojione), iframe" $body))
        found (atom nil)]
    (dotimes [el-num (.-length thumb-els)]
      (let [el (aget thumb-els el-num)
            $el (js/$ el)]
        (when-not @found
          (if (= (s/lower (.-tagName el)) "img")
            (let [width (.attr $el "width")
                  height (.attr $el "height")]
              (when (and (not @found)
                         (or (<= width (* height 2))
                             (<= height (* width 2))))
                (reset! found
                  {:type "image"
                   :thumbnail (if (.data $el "thumbnail")
                                (.data $el "thumbnail")
                                (.attr $el "src"))})))
            (reset! found {:type (.data $el "media-type") :thumbnail (.data $el "thumbnail")})))))
    @found))

(rum/defcs entry-card < rum/static
                        (rum/local false ::hovering-card)
                        (rum/local false ::showing-dropdown)
                        (rum/local false ::truncated)
                        (rum/local nil ::first-body-image)
                        {:after-render (fn [s]
                                         (let [entry-data (first (:rum/args s))
                                               body-sel (str "div.entry-card-" (:uuid entry-data) " div.entry-card-body")
                                               body-a-sel (str body-sel " a")
                                               read-more-sel (str body-a-sel ".read-more")
                                               is-all-activity (get (:rum/args s) 3)]
                                           ; Prevent body links in FoC
                                           (.click (js/$ body-a-sel) #(.preventDefault %))
                                           ; Prevent read more link to change directly the url
                                           (.click (js/$ read-more-sel) #(.preventDefault %))
                                           ; Truncate body text with dotdotdot
                                           (when (compare-and-set! (::truncated s) false true)
                                             (truncate-body body-sel is-all-activity)
                                             (utils/after 10 #(do
                                                                (.trigger (js/$ body-sel) "destroy")
                                                                (truncate-body body-sel is-all-activity)))))
                                         s)
                         :will-mount (fn [s]
                                       (let [entry-data (first (:rum/args s))]
                                         (reset! (::first-body-image s) (get-first-body-thumbnail (:body entry-data))))
                                       s)
                         :did-remount (fn [o s]
                                        (let [old-entry-data (first (:rum/args o))
                                              new-entry-data (first (:rum/args s))]
                                          (when (not= (:body old-entry-data) (:body new-entry-data))
                                            (reset! (::first-body-image s) (get-first-body-thumbnail (:body new-entry-data)))
                                            (.trigger (js/$ (str "div.entry-card-" (:uuid old-entry-data) " div.entry-card-body")) "destroy")
                                            (reset! (::truncated s) false)))
                                        s)
                         :did-mount (fn [s]
                                      (let [entry-data (first (:rum/args s))]
                                        (.on (js/$ (str "div.entry-card-" (:uuid entry-data)))
                                         "show.bs.dropdown"
                                         (fn [e]
                                           (reset! (::showing-dropdown s) true)))
                                        (.on (js/$ (str "div.entry-card-" (:uuid entry-data)))
                                         "hidden.bs.dropdown"
                                         (fn [e]
                                           (reset! (::showing-dropdown s) false))))
                                      s)}
  [s entry-data has-headline has-body is-all-activity]
  [:div
    {:class (utils/class-set {:entry-card true
                              (str "entry-card-" (:uuid entry-data)) true
                              :all-activity-card is-all-activity})
     :on-click #(dis/dispatch! [:entry-modal-fade-in (:board-slug entry-data) (:uuid entry-data)])
     :on-mouse-over #(reset! (::hovering-card s) true)
     :on-mouse-leave #(reset! (::hovering-card s) false)}
    (when is-all-activity
      [:div.entry-card-breadcrumb
        "In " [:span.bold (:board-name entry-data)]
        (when (:topic-slug entry-data)
          " → ")
        (when (:topic-slug entry-data)
          [:span.bold (:topic-name entry-data)])])
    ; Card header
    [:div.entry-card-head.group
      ; Card author
      [:div.entry-card-head-author
        (user-avatar-image (first (:author entry-data)))
        [:div.name (:name (first (:author entry-data)))]
        [:div.time-since
          [:time
            {:date-time (:created-at entry-data)
             :data-toggle "tooltip"
             :data-placement "top"
             :title (utils/entry-tooltip entry-data)}
            (utils/time-since (:created-at entry-data))]]]
      ; Card labels
      [:div.entry-card-head-right
        ; Topic tag button
        (when (and (not is-all-activity)
                   (:topic-slug entry-data))
          (let [topic-name (or (:topic-name entry-data) (s/upper (:topic-slug entry-data)))]
            [:div.topic-tag
              {:on-click #(do
                            (utils/event-stop %)
                            (router/nav! (oc-urls/board-filter-by-topic (router/current-org-slug) (:board-slug entry-data) (:topic-slug entry-data))))}
              topic-name]))]]
    [:div.entry-card-content.group
      ; Headline
      [:div.entry-card-headline
        {:dangerouslySetInnerHTML (utils/emojify (:headline entry-data))
         :class (when has-headline "has-headline")}]
      ; Body
      (let [body-without-tags (-> entry-data :body utils/strip-img-tags utils/strip-br-tags utils/strip-empty-tags)
            hidden-class (str "entry-body" (:uuid entry-data))
            $body-content (js/$ (str "<div class=\"" hidden-class " hidden\">" body-without-tags "</div>"))
            appened-body (.append (js/$ (.-body js/document)) $body-content)
            _ (.each (js/$ (str "." hidden-class " .carrot-no-preview")) #(this-as this
                                                                            (let [$this (js/$ this)]
                                                                              (.remove $this))))
            $hidden-div (js/$ (str "." hidden-class))
            body-without-preview (.html $hidden-div)
            _ (.remove $hidden-div)
            read-more-html (str "<a class=\"read-more\" href=\"" (oc-urls/entry (:board-slug entry-data) (:uuid entry-data)) "\">Read more</a>")
            emojied-body (utils/emojify (str body-without-preview (if is-all-activity "" read-more-html)))]
        [:div.entry-card-body
          {:dangerouslySetInnerHTML emojied-body
           :class (utils/class-set {:has-body has-body
                                    :has-media-preview @(::first-body-image s)})}])
      (when (and is-all-activity
                 has-body)
        [:div.read-more "Read Full Entry"])
      ; Body preview
      (when @(::first-body-image s)
        [:div.entry-card-media-preview
          {:style #js {:backgroundImage (str "url(" (:thumbnail @(::first-body-image s)) ")")}
           :class (or (:type @(::first-body-image s)) "image")}])]
    [:div.entry-card-footer.group
      (interactions-summary entry-data)
      [:div.more-button.dropdown
        [:button.mlb-reset.more-ellipsis.dropdown-toggle
          {:type "button"
           :class (utils/class-set {:hidden (and (not @(::hovering-card s)) (not @(::showing-dropdown s)))})
           :id (str "entry-card-more-" (:board-slug entry-data) "-" (:uuid entry-data))
           :on-click #(utils/event-stop %)
           :title "More"
           :data-toggle "dropdown"
           :aria-haspopup true
           :aria-expanded false}]
        [:div.dropdown-menu
          {:aria-labelledby (str "entry-card-more-" (:board-slug entry-data) "-" (:uuid entry-data))}
          [:div.triangle]
          [:ul.entry-card-more-menu
            [:li
              {:on-click (fn [e]
                           (utils/event-stop e)
                           (dis/dispatch! [:entry-edit entry-data]))}
              "Edit"]
            [:li
              {:on-click #(delete-clicked % entry-data)}
              "Delete"]]]]]])