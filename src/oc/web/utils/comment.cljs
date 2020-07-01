(ns oc.web.utils.comment
  (:require [cljsjs.medium-editor]
            [defun.core :refer (defun defun-)]
            [goog.object :as gobj]
            [cuerdas.core :as string]
            [oc.web.api :as api]
            [oc.web.lib.jwt :as jwt]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.utils.mention :as mention-utils]))

(defn setup-medium-editor [comment-node users-list]
  (let [extentions (if (seq users-list)
                     #js {"mention" (mention-utils/mention-ext users-list)}
                     #js {})
        config {:toolbar false
                :anchorPreview false
                :imageDragging false
                :extensions extentions
                :autoLink true
                :anchor false
                :targetBlank true
                :paste #js {:forcePlainText false
                            :cleanPastedHTML true
                            :cleanAttrs #js ["style" "alt" "dir" "size" "face" "color" "itemprop" "name" "id"]
                            :cleanTags #js ["meta" "video" "audio" "img" "button" "svg" "canvas"
                                            "figure" "input" "textarea"]
                            :unwrapTags #js ["div" "label" "font" "h1" "h2" "h3" "h4" "h5" "div" "p" "ul" "ol" "li"
                                             "h6" "strong" "section" "time" "em" "main" "u" "form" "header" "footer"
                                             "details" "summary" "nav" "abbr" "a"]}
                :placeholder #js {:text "Add a reply…"
                                  :hideOnClick true}
               :keyboardCommands #js {:commands #js [
                                  #js {
                                    :command false
                                    :key "B"
                                    :meta true
                                    :shift false
                                    :alt false
                                  }
                                  #js {
                                    :command false
                                    :key "I"
                                    :meta true
                                    :shift false
                                    :alt false
                                  }
                                  #js {
                                    :command false
                                    :key "U"
                                    :meta true
                                    :shift false
                                    :alt false
                                  }]}}]
    (new js/MediumEditor comment-node (clj->js config))))

(defn add-comment-content [comment-node]
  (let [comment-html (.-innerHTML comment-node)
        $comment-node (.html (js/$ "<div/>") comment-html)
        _remove-mentions-popup (.remove $comment-node ".oc-mention-popup")]
    (utils/clean-body-html (.html $comment-node))))

(defn ungroup-comments [comments]
  (filterv #(#{:thread :comment} (:resource-type %)) comments))

(defun sort-comments
  ([comments :guard nil?]
   [])
  ([comments :guard map?]
   (sort-comments (vals comments)))
  ([comments :guard coll?]
   (vec (sort-by :created-at comments)))
  ([comments :guard coll? parent-uuid]
   (let [check-fn (if parent-uuid #(-> % :parent-uuid (= parent-uuid)) (comp empty? :parent-uuid))
         filtered-comments (filterv check-fn comments)
         sorted-comments (sort-by :created-at filtered-comments)]
     (if (nil? parent-uuid)
      (vec (reverse sorted-comments))
      (vec sorted-comments)))))

(defn- get-collapsed-item [item collapsed-map]
  (merge item (get collapsed-map (:uuid item))))

(defn is-expanded? [item collapsed-map]
  (:expanded (get-collapsed-item item collapsed-map)))

; (defn is-unread? [item collapsed-map]
;   (not (:unread (get-collapsed-item item collapsed-map))))

; (defn unread?
;   "If unread was already set let's reuse it."
;   [last-read-at comment-data]
;   (if (contains? comment-data :unread)
;     (:unread comment-data)
;     (and (not (:author? comment-data))
;          (< (.getTime (utils/js-date last-read-at))
;             (.getTime (utils/js-date (:created-at comment-data)))))))

; (defn- unread-comment [comment-data last-read-at collapsed-map]
;   (if-not last-read-at
;     ;; User has never read the post, so comment is new
;     (assoc comment-data :unread true)
;     (assoc comment-data :unread
;      (unread? last-read-at (get-collapsed-item comment-data collapsed-map)))))

(defun comment-unread?
  "A comment is unread if it's created-at is past the last seen-at of the contianer it belongs to."

  ([comment-data :guard map? last-read-at]
   (comment-unread? (:created-at comment-data) last-read-at))

  ([created-at last-read-at :guard #(or (nil? %) (string? %))]
   (pos? (compare created-at last-read-at))))

(defn is-unseen? [item collapsed-map]
  (not (:unseen (get-collapsed-item item collapsed-map))))

(defun comment-unseen?
  "A comment is unseen if it's created-at is later than the last seen-at of the container it belongs to."

  ([comment-data :guard map? container-seen-at]
   (comment-unseen? (:created-at comment-data) container-seen-at))

  ([created-at container-seen-at :guard #(or (nil? %) (string? %))]
   (pos? (compare created-at container-seen-at))))

; (defn- unseen-comment [comment-data container-seen-at collapsed-map]
;   (assoc comment-data :unseen (comment-unseen? comment-data container-seen-at)))

(defn- unwrapped-body-comment [comment-data collapsed-map]
  (let [item (get-collapsed-item comment-data collapsed-map)]
    (assoc comment-data :unwrapped-body (:unwrapped-body item))))

; (defn- expanded-comment [comment-data container-seen-at collapsed-map]
;   (assoc comment-data :expanded (or ;; comment is unseen
;                                     (:unseen comment-data)
;                                     ;; Keep the comment expanded if it was already
;                                     (is-expanded? comment-data collapsed-map)
;                                     ;; Do not collapse root comments
;                                     ; (not (seq (:parent-uuid comment-data)))
;                                     ;; User has not read the post yet
;                                     (not (seq container-seen-at)))))

(defun collapse-comments
  "Add a collapsed flag to every comment that is a reply and is not unseen.
   Also add unseen? flag to every unseen one. Add a count of the collapsed
   comments to each root comment."

   ([_comments :guard empty?]
    [])

  ([comments :guard coll?]
    (collapse-comments comments {}))
  ;; When we have less than 4 comments we always show all of them
  ([comments :guard #(and (coll? %)
                          (<= (count %) 3))
    collapsed-map :guard map?]
   (mapv
    #(-> %
      ; (unseen-comment container-seen-at collapsed-map)
      (assoc :expanded true)
      (unwrapped-body-comment collapsed-map))
    comments))
  ;; Add :unseen info to each comment before entering the recursion
  ([comments :guard (fn [cs] (and (coll? cs)
                                  (not (some #(contains? % :expanded) cs))
                                  (> (count cs) 3)))
    collapsed-map :guard map?]
   (let [enriched-comments (mapv #(-> %
                                   ; (unseen-comment container-seen-at collapsed-map)
                                   (unwrapped-body-comment collapsed-map))
                            comments)
         comments-count (count enriched-comments)
         trailing-expanded-comments-count (if (> comments-count 4) 2 1)
         trailing-expanded-comments (subvec enriched-comments (- comments-count trailing-expanded-comments-count) comments-count)
         collapsed-comments (subvec enriched-comments 1 (- comments-count trailing-expanded-comments-count))
         unseen-collapsed (some :unseen collapsed-comments)]
     (vec (concat
      [(assoc (first enriched-comments) :expanded true)]
      (map #(assoc % :expanded false) collapsed-comments)
      [{:resource-type :collapsed-comments
        :collapsed-count (count collapsed-comments)
        :collapse-id (clojure.string/join "-" (map :uuid collapsed-comments))
        :expanded true
        :unseen false
        :unseen-collapsed unseen-collapsed
        :message (str "View " (if (some :unseen collapsed-comments) "new " "more ") "comments")
        :comment-uuids (map :uuid collapsed-comments)}]
      (map #(assoc % :expanded true) trailing-expanded-comments)))))
  ;; If at least one already has expanded let's expand the remainig ones
  ([comments :guard (fn [cs] (and (coll? cs)
                                  (not-every? #(contains? % :expanded) cs)
                                  (> (count cs) 3)))
    collapsed-map :guard map?]
   (mapv #(assoc % :expanded (or (:expanded %) true)) comments)))


  ; ;; Recursive step: unseen has been set, let's add expand now
  ; ([in-comments :guard coll?
  ;   out-comments :guard coll?
  ;   collapsed-map :guard map?]
  ;   (let [potential-collapse-items (vec (take-while #(not (contains? % :expanded)) in-comments))]
  ;     (if (seq potential-collapse-items)
  ;       (let [next-in-comments (subvec in-comments (count potential-collapse-items))
  ;             should-add-collapsed-item? (> (count potential-collapse-items) 2)
  ;             collapse-items (if (> (count potential-collapse-items) 3)
  ;                              (subvec potential-collapse-items 0 (- (count potential-collapse-items) 2))
  ;                              (vec (butlast potential-collapse-items)))
  ;             expand-items (if (> (count potential-collapse-items) 3)
  ;                            (subvec potential-collapse-items (- (count potential-collapse-items) 2) (count potential-collapse-items))
  ;                            [(last potential-collapse-items)])
  ;             unseen-collapsed (some :unseen collapse-items)
  ;             collapsed-item (when should-add-collapsed-item?
  ;                              {:resource-type :collapsed-comments
  ;                               :collapsed-count (count collapse-items)
  ;                               :collapse-id (clojure.string/join "-" (map :uuid collapse-items))
  ;                               :expanded true
  ;                               :unseen false
  ;                               :unseen-collapsed unseen-collapsed
  ;                               :message (str "View " (if (some :unseen collapse-items) "new " "more ") "comments")
  ;                               :comment-uuids (map :uuid collapse-items)})
  ;             next-out-comments (if should-add-collapsed-item?
  ;                                 ;; In case there are at least 3 read and not expanded items in a row
  ;                                 ;; add a collapsed item after the first n-1 and add the last after (we want the most recent expanded)
  ;                                 (vec (concat out-comments
  ;                                              (mapv #(assoc % :expanded false) collapse-items)
  ;                                              [collapsed-item]
  ;                                              (mapv #(assoc % :expanded true) expand-items)))
  ;                                 (vec (concat out-comments
  ;                                              (mapv #(assoc % :expanded true) potential-collapse-items))))]
  ;         (if (seq next-in-comments)
  ;           (recur next-in-comments next-out-comments collapsed-map)
  ;           next-out-comments))
  ;       ;; First comment is unseen or expanded
  ;       (let [next-in-comments (vec (rest in-comments))
  ;             next-out-comments (vec (conj out-comments
  ;                                     (assoc (first in-comments) :expanded true)))]
  ;         (if (seq next-in-comments)
  ;           (recur next-in-comments next-out-comments collapsed-map)
  ;           next-out-comments)))))

(defun add-comment-focus-value
  ([prefix :guard string? comment-data :guard map?]
   (add-comment-focus-value prefix (:resource-uuid comment-data) (:parent-uuid comment-data) (:uuid comment-data)))

  ([prefix :guard string? entry-uuid :guard string?]
   (add-comment-focus-value prefix entry-uuid nil nil))

  ([prefix :guard string? entry-uuid :guard string? parent-comment-uuid]
   (add-comment-focus-value prefix entry-uuid parent-comment-uuid nil))

  ([prefix :guard string? entry-uuid :guard string? parent-comment-uuid edit-comment-uuid]
   (str prefix "-" (dis/add-comment-string-key entry-uuid parent-comment-uuid edit-comment-uuid))))