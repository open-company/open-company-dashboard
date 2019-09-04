(ns oc.web.mixins.activity
  (:require [rum.core :as rum]
            [oc.web.lib.utils :as utils]
            [oc.web.utils.activity :as au]))

(defn truncate-element-mixin [element-class height]
  (letfn [(truncate-fn [s]
            ; Truncate body text with dotdotdot
            (when-let [dom-node (rum/dom-node s)]
              (let [$dom-node (js/$ element-class dom-node)]
                (.dotdotdot $dom-node
                 #js {:height height
                      :wrap "word"
                      :watch false
                      :ellipsis "..."}))))]
    {:did-mount (fn [s]
      (truncate-fn s)
      s)
     :did-remount (fn [_ s]
      (truncate-fn s)
      s)}))

(def truncate-comments-mixin
  {:init (fn [s]
    (assoc s :comments-truncated (atom false)))
   :after-render (fn [s]
    ; Truncate body text with dotdotdot
    (when (compare-and-set! (:comments-truncated s) false true)
      (let [dom-node (rum/dom-node s)
            $collapsed-comments (.find (js/$ dom-node) "div.stream-comment-body:not(.expanded)")]
        (.dotdotdot $collapsed-comments
          #js {:height (* 22 5)
               :wrap "word"
               :watch true
               :ellipsis "..."
               :callback (fn [is-truncated]
                (this-as this
                  (when is-truncated
                    (.append (js/$ this) "<p><a class=\"read-more\">Read More</a></p>"))))})))
    s)
   :did-remount (fn [o s]
    (reset! (:comments-truncated s) false)
    s)})