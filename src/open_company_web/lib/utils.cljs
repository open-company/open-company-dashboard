(ns open-company-web.lib.utils
  (:require [om.core :as om :include-macros true]
            [clojure.string]
            [dommy.core :as dommy :refer-macros (sel1)]
            [cljs.core.async :refer (put!)]
            [cljs-time.format :as cljs-time-format]
            [cljs-time.core :as cljs-time]
            [goog.style :refer (setStyle)]
            [goog.format.EmailAddress :as email]
            [goog.fx.dom :refer (Scroll)]
            [goog.string :as gstring]
            [goog.i18n.NumberFormat :as nf]
            [goog.object :as gobj]
            [open-company-web.dispatcher :as dis]
            [open-company-web.router :as router]
            [open-company-web.caches :as caches]
            [open-company-web.lib.cookies :as cook]
            [open-company-web.lib.iso4217 :refer (iso4217)]
            [open-company-web.caches :refer (company-cache)]
            [open-company-web.local-settings :as ls]
            [open-company-web.lib.responsive :as responsive]
            [cuerdas.core :as s]
            [cljsjs.emojione]) ; pulled in for cljsjs externs
  (:import  [goog.i18n NumberFormat]))

(defn abs
  "(abs n) is the absolute value of n"
  [n]
  (cond
   (not (number? n)) n ; non-sensical, so leave it alone
   (neg? n) (- n)
   :else n))

(def oc-animation-duration 300)

(defn sort-by-key-pred [k & invert]
  (if-not (first invert)
    (fn [a b] (compare (k a) (k b)))
    (fn [a b] (compare (k b) (k a)))))

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defn get-currency [currency-code]
  (let [kw (keyword currency-code)]
    (when (contains? iso4217 kw) (kw iso4217))))

(defn get-symbol-for-currency-code [currency-code]
  (let [currency (get-currency currency-code)
        sym (if (and (contains? currency :symbol)
                     (not (clojure.string/blank? (:symbol currency))))
              (:symbol currency)
              currency-code)]
    (or sym (:code currency))))

(def channel-coll (atom {}))

(defn add-channel [channel-name channel]
  (swap! channel-coll assoc channel-name channel))

(defn get-channel [channel-name]
  (@channel-coll channel-name))

(defn remove-channel [channel-name]
  (swap! channel-coll dissoc channel-name))

(defn handle-change [cursor value key]
  (if (array? key)
    (om/transact! cursor assoc-in key (fn [_] value))
    (om/transact! cursor key (fn [_] value))))

(defn change-value [cursor e key]
  (handle-change cursor (.. e -target -value) key))

(defn save-values [channel-name]
  (let [save-channel (get-channel channel-name)]
    (put! save-channel 1)))

(defn in?
  "true if seq contains elm"
  [coll elm]
  (some #(= elm %) coll))

(defn vec-dissoc [coll elem]
  (vec (filter #(not= elem %) coll)))

;; TODO use goog.i18n.DateTimeFormat here
(defn month-string-int
 "Return the name of the month given the integer month number. Accept flags to transform the string:
  - :short Return only the first 3 letters of the month name: JAN
  - :capitalize Return the capitalized name: January
  - :uppercase Return the uppercase: JANUARY"
  [month & [flags]]
  (let [short-month (in? flags :short)
        capitalize (in? flags :capitalize)
        uppercase (in? flags :uppercase)
        month-string (case month
                        1 "January"
                        2 "February"
                        3 "March"
                        4 "April"
                        5 "May"
                        6 "June"
                        7 "July"
                        8 "August"
                        9 "September"
                        10 "October"
                        11 "November"
                        12 "December"
                        "")
        short-string (if short-month (subs month-string 0 3) month-string)
        capitalized-string (if capitalize (s/capital short-string) short-string)
        uppercase-string (if uppercase (s/upper capitalized-string) capitalized-string)]
    uppercase-string))

(defn month-string [month & [flags]]
  (month-string-int (.parseInt js/window month 10) flags))

;; TODO use goog.i18n.DateTimeFormat here
(defn month-short-string [month]
  (month-string-int (.parseInt js/window month 10) [:short :uppercase]))

(defn format-value [value]
  (if (nil? value)
    0
    (.toLocaleString value)))

(defn calc-burn-rate [revenue costs]
  (- revenue costs))

(defn calc-runway [cash burn-rate]
  (int (* (/ cash burn-rate) 30)))

(defn calc-burnrate-runway
  "Helper function that add burn-rate and runway to each update section"
  [finances-data]
  (if (empty? finances-data)
    finances-data
    (let [sort-pred (sort-by-key-pred :period)
          sorted-data (vec (sort sort-pred finances-data))]
      (vec (map
              (fn [data]
                (let [idx (inc (.indexOf (to-array sorted-data) data))
                      start (max 0 (- idx 3))
                      sub-data (subvec sorted-data start idx)
                      burn-rate (calc-burn-rate (:revenue data) (:costs data))
                      runway (calc-runway (:cash data) burn-rate)]
                  (merge data {:runway runway
                               :burn-rate burn-rate})))
              sorted-data)))))

(defn camel-case-str [value]
  (when value
    (let [upper-value (clojure.string/replace value #"^(\w)" #(clojure.string/upper-case (first %1)))]
      (clojure.string/replace upper-value #"-(\w)"
                              #(str " " (clojure.string/upper-case (second %1)))))))

(defn js-date [ & [date-str]]
  (if date-str
    (new js/Date date-str)
    (new js/Date)))

(defn add-zero [v]
  (str (when (< v 10) "0") v))

(defn add-zeros [v]
  (str (cond
        (< v 10) "00"
        (< v 100) "0")
    v))

;; TODO use goog.i18n.DateTimeFormat here
(defn date-string [js-date & [flags]]
  (let [month (month-string (add-zero (inc (.getMonth js-date))) (when (in? flags :short-month) [:short]))
        day (.getDate js-date)]
    (str month " " day (when (in? flags :year) (str ", " (.getFullYear js-date))))))

(defn pluralize [string n]
  (if (> n 1)
    (str string "s")
    string))

(defn time-since
  "Get a string representing the elapsed time from a date in the past"
  [past-date]
  (let [past-js-date (js-date past-date)
        past (.getTime past-js-date)
        now (.getTime (js-date))
        seconds (.floor js/Math (/ (- now past) 1000))
        years-interval (.floor js/Math (/ seconds 31536000))
        months-interval (.floor js/Math (/ seconds 2592000))
        days-interval (.floor js/Math (/ seconds 86400))
        hours-interval (.floor js/Math (/ seconds 3600))]
    (cond
      (pos? years-interval)
      (date-string past-js-date [:year])

      (pos? months-interval)
      (date-string past-js-date)

      (pos? days-interval)
      (str days-interval " " (pluralize "day" days-interval) " ago")

      (pos? hours-interval)
      (str hours-interval " " (pluralize "hour" hours-interval) " ago")

      :else
      "just now")))

(defn get-click-position
  "Return the x position inside the clicked element"
  [event]
  (let [client-x (.-clientX event)
        bound-rect (.getBoundingClientRect (.-target event))
        lf (.-left bound-rect)
        from-left (- client-x lf)]
    from-left))

(defn class-set
  "Given a map of class names as keys return a string of the those classes that evaulates as true"
  [classes]
  (clojure.string/join (map #(str " " (name %)) (keys (filter second classes)))))

(defn period-exists [period data]
  (if (pos? (count (filter #(= (:period %) period) data)))
    true
    false))

(defn current-finance-period []
  (let [date (js/Date.)
        fixed-date (js/Date. (.setMonth date (- (.getMonth date) 1)))
        month (inc (.getMonth fixed-date))
        month-str (str (when (< month 10) "0") month)
        cur-period (str (.getFullYear fixed-date) "-" month-str)]
    cur-period))

(defn get-section-keys [company-data]
  "Get the section names, as a vector of keywords."
  (vec (map keyword (:sections company-data))))

(defn link-for
  ([links rel] (some #(when (= (:rel %) rel) %) links))
  ([links rel method] (some #(when (and (= (:method %) method) (= (:rel %) rel)) %) links)))

(defn readonly? [links]
  (let [update (link-for links "update" "PUT")
        partial-update (link-for links "partial-update" "PATCH")
        delete (link-for links "delete" "DELETE")]
    (and (nil? update) (nil? partial-update) (nil? delete))))

(defn as-of-now []
  (let [date (js-date)]
    (.toISOString date)))

(defn fix-finances [section-body]
  (let [finances-data (if (contains? section-body :data) (:data section-body) [])
        fixed-finances (calc-burnrate-runway finances-data)
        sort-pred (sort-by-key-pred :period true)
        sorted-finances (sort sort-pred fixed-finances)
        fixed-section (assoc section-body :data sorted-finances)]
    fixed-section))

(defn fix-section 
  "Add `:section` name and `:as-of` keys to the section map"
  [section-body section-name & [read-only force-write]]
  (let [with-updated-at (if (contains? section-body :updated-at)
                          section-body
                          (assoc section-body :updated-at (as-of-now)))
        with-keys       (-> with-updated-at
                          (assoc :section (name section-name))
                          (assoc :as-of (:updated-at section-body))
                          (assoc :read-only (readonly? (:links section-body))))]
    (if (= section-name :finances)
      (fix-finances with-keys)
      with-keys)))

(defn fix-sections [company-data]
  "Add section name in each section and a section sorter"
  (let [links (:links company-data)
        section-keys (get-section-keys company-data)
        read-only (readonly? links)
        without-sections (apply dissoc company-data section-keys)
        with-read-only (assoc without-sections :read-only read-only)
        sections (apply merge (map (fn [sn] (hash-map sn (fix-section (get company-data sn) sn))) section-keys))
        with-fixed-sections (merge with-read-only sections)]
    with-fixed-sections))

(defn sort-revisions [revisions]
  (let [sort-pred (sort-by-key-pred :updated-at)]
    (vec (sort sort-pred revisions))))

(defn revision-next
  "Return the first future revision"
  [revisions as-of]
  (when (pos? (count revisions))
    (first (remove nil? (map
                          (fn [r]
                            (when (= (:updated-at r) as-of)
                              (let [idx (.indexOf (to-array revisions) r)]
                                (get revisions (inc idx)))))
                          revisions)))))

(defn revision-prev
  "Return the first future revision"
  [revisions as-of]
  (when (pos? (count revisions))
    (first (remove nil? (map
                          (fn [r]
                            (when (= (:updated-at r) as-of)
                              (let [idx (.indexOf (to-array revisions) r)]
                                (get revisions (dec idx)))))
                          revisions)))))

(defn px [n]
  (str n "px"))

(defn select-section-data [section-data section as-of]
  (when (and section-data section (or as-of (:placeholder section-data)))
    (let [slug (keyword (router/current-company-slug))]
      (if (or (not (contains? (slug @caches/revisions) section))
              (= as-of (:updated-at section-data)))
        section-data
        (((keyword section) (slug @caches/revisions)) as-of)))))

(def quarterly-input-format (cljs-time-format/formatter "YYYY-MM"))
(def monthly-input-format (cljs-time-format/formatter "YYYY-MM"))
(def weekly-input-format (cljs-time-format/formatter "YYYY-MM-dd"))

(defn get-formatter [interval]
  "Get the date formatter from the interval type."
  (case interval
    "quarterly"
    quarterly-input-format
    "weekly"
    weekly-input-format
    ; else
    monthly-input-format))

(defn date-from-period [period & [interval]]
  (if period
    (cljs-time-format/parse (get-formatter interval) period)
    (cljs-time/now)))

(defn period-from-date [date & [interval]]
  (cljs-time-format/unparse (get-formatter interval) date))

(defn get-quarter-from-month [month & [flags]]
  (let [short-str (in? flags :short)]
    (cond
      (and (>= month 1) (<= month 3))
      (if short-str
        "Q1"
        "January - March")
      (and (>= month 4) (<= month 6))
      (if short-str
        "Q2"
        "April - June")
      (and (>= month 7) (<= month 9))
      (if short-str
        "Q3"
        "July - September")
      (and (>= month 10) (<= month 12))
      (if short-str
        "Q4"
        "October - December"))))

(defn get-quarter-from-period [period & [flags]]
  (let [date (date-from-period period "quarterly")
        month (cljs-time/month date)]
    (get-quarter-from-month month flags)))

(def default-growth-interval "monthly")

(defn get-period-string [period & [interval flags]]
  "Get descriptive string for the period by interval. Use :short as a flag to get
  the short formatted string."
  (when period
    (let [fixed-interval (or interval default-growth-interval)
          parsed-date (date-from-period period fixed-interval)
          month (cljs-time/month parsed-date)
          year (cljs-time/year parsed-date)
          fixed-year (if (in? flags :short-year) (str "'" (subs (str year) 2 4)) (str year))
          plus-one-week-year (cljs-time/year (cljs-time/plus parsed-date (cljs-time/days 7)))
          minus-one-week-year (cljs-time/year (cljs-time/minus parsed-date (cljs-time/days 7)))
          needs-year (and (not (in? flags :skip-year))
                          (or (in? flags :force-year)
                            (case fixed-interval
                              "weekly"
                              (or (not= plus-one-week-year year) (not= minus-one-week-year year))
                              "quarterly"
                              (or (= month 1) (= month 10))
                              ;else
                              (or (= month 1) (= month 12)))))]
      (case fixed-interval
        "quarterly"
        (str (get-quarter-from-month (cljs-time/month parsed-date) flags)
             (when needs-year
               (str " " fixed-year)))
        "monthly"
        (str (month-string-int (cljs-time/month parsed-date) flags)
             (when needs-year
               (str " " fixed-year)))
        "weekly"
        (str (month-string-int (cljs-time/month parsed-date) flags)
             " "
             (cljs-time/day parsed-date)
             (when needs-year
               (str ", " fixed-year)))))))

(defn update-page-title [title]
  (set! (.-title js/document) title))

(defn periods-diff [first-period last-period & [interval]]
  (let [fixed-interval (or interval default-growth-interval)
        first-date (date-from-period first-period fixed-interval)
        last-date (date-from-period last-period fixed-interval)]
    (case fixed-interval
      "quarterly" (/ (cljs-time/in-months (cljs-time/interval first-date last-date)) 3)
      "monthly" (cljs-time/in-months (cljs-time/interval first-date last-date))
      "weekly" (cljs-time/in-weeks (cljs-time/interval first-date last-date)))))

(defn get-year [period & [interval]]
  (let [fixed-interval (or interval "monthly")
        date (date-from-period period fixed-interval)]
    (cljs-time/year date)))

(defn get-weekly-period-day [period]
  (let [date (date-from-period period "weekly")]
    (cljs-time/day date)))

(defn get-month [period & [interval]]
  (let [fixed-interval (or interval default-growth-interval)
        date (date-from-period period fixed-interval)
        month (add-zero (cljs-time/month date))]
    (case interval
      "quarterly"
      (month-short-string month)
      "weekly"
      (month-short-string month)
      ;else
      (month-short-string month))))

(defn get-month-quarter [current-month]
  (case
    (and (> current-month 1)
         (< current-month 4))
    1
    (and (> current-month 4)
         (< current-month 7))
    4
    (and (> current-month 7)
         (< current-month 9))
    7
    (> current-month 9)
    10))

(defn non-zero-number?
  "Return `true` if the arg is both a number and is not zero, otherwise return `false`."
  [number]
  (and (number? number)
       (not (zero? number))))

(defn no-finances-data?
  "Return `true` if the passed finances data has no substantive data, otherwise return `false`."
  [data]
  (not-any? #(or (non-zero-number? (:cash %))
                 (non-zero-number? (:revenue %))
                 (non-zero-number? (:costs %))) data))

(defn no-growth-data?
  "Return `true` if the passed finances data has no substantive data, otherwise return `false`."
  [data]
  (not-any? #(non-zero-number? (:value %)) (vals data)))

(defn current-growth-period [interval]
  (let [fixed-interval (or interval default-growth-interval)
        now (cljs-time/now)
        year (cljs-time/year now)
        month (cljs-time/month now)]
    (case interval
      "quarterly"
      (str year "-" (add-zero (get-month-quarter month)))
      "weekly"
      (let [day-of-week (cljs-time/day-of-week now)
            to-monday (dec day-of-week)
            monday-date (cljs-time/minus now (cljs-time/days to-monday))]
        (str (cljs-time/year monday-date) "-" (add-zero (cljs-time/month monday-date)) "-" (add-zero (cljs-time/day monday-date))))
      ;; Default tp monthly
      (str year "-" (add-zero month)))))

(defn company-cache-key [k & [v]]
  (let [slug (keyword (router/current-company-slug))
        cc (slug @company-cache)]
    (when v
      (swap! company-cache assoc-in [slug k] v))
    (get cc k nil)))

(defn remove-company-cache-key [k]
  (let [slug (keyword (router/current-company-slug))
        cc (slug @company-cache)]
    (swap! company-cache update-in [slug] dissoc k)))

(defn clean-company-caches []
  (reset! company-cache {}))

(defn thousands-separator
  ([number]
    (.format (NumberFormat. nf/Format.DECIMAL) number))
  ([number currency-code]
    (.format (NumberFormat. nf/Format.CURRENCY currency-code nf/CurrencyStyle.LOCAL) number))
  ([number currency-code decimals]
    (-> (NumberFormat. nf/Format.CURRENCY currency-code nf/CurrencyStyle.LOCAL)
        (.setMinimumFractionDigits (min 2 decimals))
        (.format number))))

(defn offset-top [elem]
  (let [bound-rect (.getBoundingClientRect elem)]
    (.-top bound-rect)))

(defn scroll-to-y [scroll-y & [duration]]
  (.play
    (new Scroll
         (.-body js/document)
         #js [0 (.-scrollTop (.-body js/document))]
         #js [0 scroll-y]
         (or duration oc-animation-duration))))

(defn scroll-to-element [elem]
  (let [elem-scroll-top (offset-top elem)]
    (scroll-to-y elem-scroll-top)))

(defn scroll-top-with-id [id]
  (offset-top (sel1 (str "#" id))))

(defn scroll-to-id [id & [duration]]
  (let [body-scroll-top (.-scrollTop (.-body js/document))
        top (- (+ (scroll-top-with-id id) body-scroll-top) 50)]
    (scroll-to-y top (or duration oc-animation-duration))))

(defn scroll-to-section [section-name]
  (scroll-to-id (str "section-" (name section-name))))

(defn round-2-dec [value decimals]
  ; cut to 2 dec maximum then parse to float to use toString to remove trailing zeros
  (.toLocaleString (js/parseFloat (gstring/format (str "%." decimals "f") value))))

(defn with-metric-prefix [value]
  (when value
    (cond
      ; 100M
      (>= value 100000000)
      (str (round-2-dec (int (/ value 1000000)) 2) "M")
      ; 10.0M
      (>= value 10000000)
      (str (round-2-dec (/ value 1000000) 1) "M")
      ; 1.00M
      (>= value 1000000)
      (str (round-2-dec (/ value 1000000) 2) "M")
      ; 100K
      (>= value 100000)
      (str (round-2-dec (int (/ value 1000)) 2) "K")
      ; 10.0K
      (>= value 10000)
      (str (round-2-dec (/ value 1000) 1) "K")
      ; 1.00K
      (>= value 1000)
      (str (round-2-dec (/ value 1000) 2) "K")
      ; 100
      (>= value 100)
      (str (round-2-dec (int value) 2))
      ; 10.0
      (>= value 10)
      (str (round-2-dec value 1))
      ; 1.00
      :else
      (str (round-2-dec value 2)))))

(defn is-test-env? []
  (not (not (.-_phantom js/window))))

(defonce overlay-max-win-height 670)

(defn absolute-offset [element]
  (loop [left 0
         top 0
         el element]
    (if-not (and el
                 (gobj/get el "offsetTop")
                 (gobj/get el "offsetLeft"))
      {:top top
       :left left}
      (recur (+ left (gobj/get el "offsetLeft" 0))
             (+ top (gobj/get el "offsetTop" 0))
             (.-offsetParent el)))))

(defn medium-editor-options [placeholder hide-on-click]
  {:toolbar #js {:buttons #js ["bold" "italic" "unorderedlist" "anchor"]}
   :buttonLabels "fontawesome"
   :anchorPreview #js {:hideDelay 500, :previewValueSelector "a"}
   :anchor #js {:customClassOption nil
                :customClassOptionText "Button"
                :linkValidation true
                :placeholderText "Paste or type a link"
                :targetCheckbox false
                :targetCheckboxText "Open in new window"}
   :placeholder #js {:text (or placeholder ""), :hideOnClick hide-on-click}})

(defn after [ms fn]
  (js/setTimeout fn ms))

(defn unicode-emojis [txt]
  (js/emojione.shortnameToUnicode txt))

(defn unicode-char [unicode]
  (js/encodeURI (str "&#x" unicode ";")))

(defn emojify
  "Take a string containing either Unicode emoji (mobile device keyboard), short code emoji (web app),
  or ASCII emoji (old skool) and convert it to HTML string ready to be added to the DOM (dangerously)
  with emoji image tags via the Emoji One lib and resources."
  [text & [plain-text]]
  ;; use an SVG sprite map
  (set! (.-imageType js/emojione) "png")
  ;; convert textual emoji's into SVG elements
  (set! (.-ascii js/emojione) true)
  (let [text-string (or text "") ; handle nil
        unicode-string (.toImage js/emojione text-string)]
    (if plain-text
      unicode-string
      #js {"__html" unicode-string})))

(defn strip-HTML-tags [text]
  (when text
    (let [reg (js/RegExp. "</?[^>]+(>|$)" "g")]
      (.replace text reg ""))))

(defn disable-scroll []
  (dommy/add-class! (sel1 [:body]) :no-scroll)
  (setStyle (sel1 [:div.main-scroll]) #js {:height "100vh" :overflow "hidden"}))

(defn enable-scroll []
  (dommy/remove-class! (sel1 [:body]) :no-scroll)
  (setStyle (sel1 [:div.main-scroll]) #js {:height "auto" :overflow "auto"}))

(defn su-default-title []
  (let [js-date (js-date)
        month (month-string (add-zero (.getMonth js-date)))
        year (.getFullYear js-date)]
    (str month " " year " Update")))

(defn my-uuid
  "Generate a 4 char UUID"
  []
  (.substring
    (.toString
      (.floor js/Math (* (+ 1 (.random js/Math)) 0x10000)) 16) 1))

(defn guid
  "Generate v4 GUID based on this http://stackoverflow.com/a/2117523"
  []
  (str (my-uuid) (my-uuid) "-" (my-uuid) "-" (my-uuid) "-" (my-uuid) "-" (my-uuid) (my-uuid) (my-uuid)))

(defn event-stop [e]
  (.preventDefault e)
  (.stopPropagation e))

(defn event-inside? [e el]
  (loop [element (.-target e)]
    (if element
      (if (= element el)
        true
        (recur (.-parentElement element)))
      false)))

(defn to-end-of-content-editable [content-editable-element]
  (if (.-createRange js/document)
    (let [rg (.createRange js/document)]
      (.selectNodeContents rg content-editable-element)
      (.collapse rg false)
      (let [selection (.getSelection js/window)]
        (.removeAllRanges selection)
        (.addRange selection rg)))
    (let [rg (.createTextRange (.-body js/document))]
      (.moveToElementText rg content-editable-element)
      (.collapse rg false)
      (.select rg))))

(defn aspect-ration-image-height [original-width original-height final-width]
  (* (/ original-height original-width) final-width))

(defn emoji-images-to-unicode [html]
  (let [div (.createElement js/document "div")]
    (set! (.-id div) "temp-emojing")
    (set! (.-innerHTML div) html)
    (.appendChild (.-body js/document) div)
    (.replaceWith (js/$ "#temp-emojing img.emojione") (fn [_ _] (this-as this (.-alt this))))
    (let [$div       (js/$ "#temp-emojing")
          inner-html (.html $div)]
      (.remove $div)
      inner-html)))

(defn medium-editor-hide-placeholder [editor editor-el]
  (.each (js/$ (gobj/get editor "extensions"))
    (fn [_ _]
      (this-as this
        (when (gobj/containsKey this "hideOnClick")
          (let [hidePlaceholder (gobj/get this "hidePlaceholder")]
            (hidePlaceholder editor-el)))))))

(defn filter-placeholder-sections [topics company-data]
  (vec (filter #(let [sd (->> % keyword (get company-data))] (and sd (not (:placeholder sd)))) topics)))

(defn su-date-from-created-at [created-at]
  (let [from-js-date (cljs-time/date-time (js-date created-at))]
    (cljs-time-format/unparse (cljs-time-format/formatter "yyyy-MM-dd") from-js-date)))

(def topic-body-limit 500)

(defn exceeds-topic-body-limit [body]
  (> (count (strip-HTML-tags body)) topic-body-limit))

(def min-no-placeholder-section-enable-share 1)

(defn can-edit-sections? [company-data]
  (let [company-topics (vec (map keyword (:sections company-data)))]
    (and (not (responsive/is-mobile-size?))
         (responsive/can-edit?)
         (not (:read-only company-data))
         (>= (count (filter-placeholder-sections company-topics company-data)) min-no-placeholder-section-enable-share))))

(defn company-has-topics? [company-data]
  (let [company-topics (vec (map keyword (:sections company-data)))]
    (pos? (count (filter-placeholder-sections company-topics company-data)))))

(defn remove-ending-empty-paragraph
  "Remove the last p tag if it's empty."
  [body-el]
  (when-not (is-test-env?)
    (while (and (= (count (clojure.string/trim (.text (.last (.find (js/$ body-el) ">p"))))) 0)
                (= (.-length (.find (js/$ body-el) ">p img")) 0)
                (pos? (.-length (.find (js/$ body-el) ">p"))))
      (.remove (js/$ ">p:last-child" (js/$ body-el))))))

(defn data-topic-has-data [section section-data]
  (cond
    ;; growth check count of metrics and count of data
    (= (keyword section) :growth)
    (and (pos? (count (:metrics section-data)))
         (pos? (count (:data section-data))))
    ;; finances check count of data
    (= (keyword section) :finances)
    (pos? (count (:data section-data)))
    ;; else false
    :else
    false))

(defn valid-email? [addr] (when addr (email/isValidAddress addr)))

(defn remove-tooltips []
  (.remove (js/$ "div.tooltip")))

(defn complete-su-url [relative-su-url]
  (let [protocol (.. js/document -location -protocol)
        host     (.. js/document -location -host)]
    (str protocol "//" host relative-su-url)))

(defn slack-share? []
  (if-let [jwt (:jwt @dis/app-state)]
    (and (= (:auth-source jwt) "slack") ; auth'd w/ Slack
         (not (nil? (-> jwt :bot :token)))))) ; with an installed Slack bot

(defn new-section-body-placeholder [topic]
  (str "Write something new about " (s/capital (name topic)) "."))

(defn new-section-initial-data [section title & [old-section-data]]
  (let [section-kw (keyword section)
        section-name (name section)]
    {:section section-kw
     :title (s/upper title)
     :body-placeholder (new-section-body-placeholder section-name)
     :data (when (and old-section-data (contains? old-section-data :data)) (:data old-section-data))
     :metrics (when (and old-section-data (contains? old-section-data :metrics)) (:metrics old-section-data))
     :headline ""
     :placeholder true
     :links (:links old-section-data)}))