(ns oc.web.lib.utils
  (:require [clojure.string]
            [goog.format.EmailAddress :as email]
            [goog.fx.dom :refer (Scroll)]
            [goog.object :as gobj]
            [cljs-time.format :as f]
            [cljs-time.core :as time]
            [oc.web.lib.jwt :as jwt]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dispatcher]
            [oc.web.urls :as oc-urls]
            [oc.web.lib.cookies :as cook]
            [oc.web.local-settings :as ls]
            [oc.web.lib.responsive :as responsive]
            [cuerdas.core :as s]
            [defun.core :refer (defun)]
            [cljs.reader :as reader]))

(def oc-animation-duration 300)

(defn in?
  "true if seq contains elm"
  [coll elm]
  (some #(= elm %) coll))

(defn vec-dissoc [coll elem]
  (filterv #(not= elem %) coll))

(defn full-month-string
  [month]
  (case month
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
    ""))

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
        month-string (full-month-string month)
        short-string (if short-month (subs month-string 0 3) month-string)
        capitalized-string (if capitalize (s/capital short-string) short-string)
        uppercase-string (if uppercase (s/upper capitalized-string) capitalized-string)]
    uppercase-string))

(defn month-string [month & [flags]]
  (month-string-int (.parseInt js/window month 10) flags))

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

;; TODO use goog.i18n.DateTimeFormat here
(defn date-string [js-date & [flags]]
  (let [month (month-string
               (add-zero (inc (.getMonth js-date)))
               (when (or (in? flags :short-month) (in? flags :short))
                [:short]))
        day (.getDate js-date)]
    (str month " " day (when (in? flags :year) (str ", " (.getFullYear js-date))))))

(defn pluralize [string n]
  (if (> n 1)
    (str string "s")
    string))

(defn time-since
  "Get a string representing the elapsed time from a date in the past"
  [past-date & [flags]]
  (let [past-js-date (js-date past-date)
        past (.getTime past-js-date)
        now (.getTime (js-date))
        seconds (.floor js/Math (/ (- now past) 1000))
        years-interval (.floor js/Math (/ seconds 31536000))
        months-interval (.floor js/Math (/ seconds 2592000))
        days-interval (.floor js/Math (/ seconds 86400))
        hours-interval (.floor js/Math (/ seconds 3600))
        minutes-interval (.floor js/Math (/ seconds 60))]
    (cond
      (pos? years-interval)
      (date-string past-js-date (concat flags [:year]))

      (or (pos? months-interval)
          (> days-interval 7))
      (date-string past-js-date flags)

      (pos? days-interval)
      (str days-interval " " (pluralize "day" days-interval) " ago")

      (pos? hours-interval)
      (str hours-interval " " (pluralize "hour" hours-interval) " ago")

      (pos? minutes-interval)
      (str minutes-interval " " (pluralize "min" minutes-interval) " ago")

      :else
      "just now")))

(defn class-set
  "Given a map of class names as keys return a string of the those classes that evaulates as true"
  [classes]
  (clojure.string/join (map #(str " " (name %)) (keys (filter second classes)))))

(defun link-for

  ([links rel]
   (some #(when (= (:rel %) rel) %) links))

  ([links rel :guard string? method :guard string?]
   (some #(when (and (= (:method %) method) (= (:rel %) rel)) %) links))

  ([links rels :guard sequential? method :guard string?]
   (some #(when (and (= (:method %) method) (in? rels (:rel %))) %) links))

  ([links rel :guard string? methods :guard sequential?]
   (some #(when (and (in? methods (:method %)) (= (:rel %) rel)) %) links))

  ([links rels :guard sequential? methods :guard sequential?]
   (some #(when (and (in? methods (:method %)) (in? rels (:rel %))) %) links))

  ([links rel method params]
   (some (fn [link]
          (when (and (= (:method link) method)
                        (= (:rel link) rel)
                        (every? #(= (% params) (% link)) (keys params)))
            link)) links)))

(defn readonly-org? [links]
  (let [update-link (link-for links "partial-update")]
    (nil? update-link)))

(defn readonly-board? [links]
  (let [new-link (link-for links "new")
        update-link (link-for links "partial-update")
        delete-link (link-for links "delete")]
    (and (nil? new-link)
         (nil? update-link)
         (nil? delete-link))))

(defn readonly-entry? [links]
  (let [partial-update (link-for links "partial-update")
        delete (link-for links "delete")]
    (and (nil? partial-update) (nil? delete))))

(defn as-of-now []
  (let [date (js-date)]
    (.toISOString date)))

(defn css-color [color]
  (let [colors (subvec (clojure.string/split color #"") 2)
        red (take 2 colors)
        green (take 2 (drop 2 colors))
        blue (take 2 (drop 4 colors))]
    (map #(-> (conj % "0x") (clojure.string/join) (reader/read-string)) [red green blue])))

(defn post-new?
  "
  An entry is new if:
    user is part of the team (we don't track new for non-team members accessing public boards)
      -and-
    user is not the post's author
      -and-
    published-at is < 30 days
      -and-
    published-at of the entry is newer than seen at
      -or-
    no seen at
  "
  [entry changes]
  (let [published-at (:published-at entry)
        too-old (f/unparse (f/formatters :date-time) (-> 30 time/days time/ago))
        seen-at (:seen-at changes)
        user-id (jwt/get-key :user-id)
        author-id (-> entry :author first :user-id)
        in-team? (jwt/user-is-part-of-the-team (:team-id (dispatcher/org-data)))
        new? (and in-team?
                  (not= author-id user-id)
                  (> published-at too-old)
                  (or (> published-at seen-at)
                      (nil? seen-at)))]
    new?))

(defn fix-entry
  "Add `:read-only`, `:board-slug`, `:board-name` and `:content-type` keys to the entry map."
  [entry-data board-data changes]
  (let [comments-link (link-for (:links entry-data) "comments")
        add-comment-link (link-for (:links entry-data) "create" "POST")
        fixed-board-slug (or (:board-slug entry-data) (:slug board-data))
        fixed-board-name (or (:board-name entry-data) (:name board-data))]
    (-> entry-data
      (assoc :content-type "entry")
      (assoc :new (post-new? (:body entry-data) changes))
      (assoc :read-only (readonly-entry? (:links entry-data)))
      (assoc :board-slug fixed-board-slug)
      (assoc :board-name fixed-board-name)
      (assoc :has-comments (boolean comments-link))
      (assoc :can-comment (boolean add-comment-link)))))

(defn fix-board
  "Add `:read-only` and fix each entry of the board, then create a :fixed-entries map with the entry UUID."
  ([board-data] (fix-board board-data {}))

  ([board-data changes]
     (let [links (:links board-data)
           read-only (readonly-board? links)
           with-read-only (assoc board-data :read-only read-only)
           fixed-entries (zipmap
                          (map :uuid (:entries board-data))
                          (map #(fix-entry % board-data changes) (:entries board-data)))
           with-fixed-entries (assoc with-read-only :fixed-items fixed-entries)]
       with-fixed-entries)))

(defn fix-activity [activity collection-data]
  (fix-entry activity collection-data {}))

(defn fix-all-posts
  "Fix org data coming from the API."
  [all-posts-data]
  (let [fixed-activities-list (map
                               #(fix-activity % {:slug (:board-slug %) :name (:board-name %)})
                               (:items all-posts-data))
        without-items (dissoc all-posts-data :items)
        fixed-activities (zipmap (map :uuid fixed-activities-list) fixed-activities-list)
        with-fixed-activities (assoc without-items :fixed-items fixed-activities)]
    with-fixed-activities))

(defn scroll-to-y [scroll-y & [duration]]
  (.play
    (new Scroll
         (.-scrollingElement js/document)
         #js [0 (.-scrollY js/window)]
         #js [0 scroll-y]
         (if (integer? duration) duration oc-animation-duration))))

(defn scroll-to-bottom [elem & [animated]]
  (let [elem-scroll-top (.-scrollHeight elem)]
    (.play
    (new Scroll
         elem
         #js [0 (.-scrollTop elem)]
         #js [0 elem-scroll-top]
         (if animated 320 oc-animation-duration)))))

(defn scroll-to-element [elem & [offset duration]]
  (let [elem-scroll-top (+ (.-offsetTop elem) (or offset 0))]
    (scroll-to-y elem-scroll-top duration)))

(defn is-test-env? []
  (not (not (.-_phantom js/window))))

(defn after [ms fn]
  (js/setTimeout fn ms))

(defn every [ms fn]
  (js/setInterval fn ms))

(defn emojify
  "Take a string containing either Unicode emoji (mobile device keyboard), short code emoji (web app),
  or ASCII emoji (old skool) and convert it to HTML string ready to be added to the DOM (dangerously)
  with emoji image tags via the Emoji One lib and resources."
  [text]
  #js {"__html" text})

(defn strip-HTML-tags [text]
  (when text
    (let [reg (js/RegExp. "</?[^>]+(>|$)" "ig")]
      (.replace text reg ""))))

(defn strip-img-tags [text]
  (when text
    (let [reg (js/RegExp. "<img/?[^>]+(>|$)" "ig")]
      (.replace text reg ""))))

(defn strip-br-tags [text]
  (when text
    (let [reg (js/RegExp. "<br[ ]{0,}/?>" "ig")]
      (.replace text reg ""))))

(defn strip-empty-tags [text]
  (when text
    (let [reg
     (js/RegExp.
      "<[a-zA-Z]{1,}[ ]{0,}(class)?[ ]{0,}([0-9a-zA-Z-]{0,}=\"[a-zA-Z\\s]{0,}\")?>[ ]{0,}</[a-zA-Z]{1,}[ ]{0,}>" "ig")]
      (.replace text reg ""))))

(defn my-uuid
  "Generate a 4 char UUID"
  []
  (.substring
    (.toString
      (.floor js/Math (* (inc (.random js/Math)) 0x10000)) 16) 1))

(defn guid
  "Generate v4 GUID based on this http://stackoverflow.com/a/2117523"
  []
  (str (my-uuid) (my-uuid) "-" (my-uuid) "-" (my-uuid) "-" (my-uuid) "-" (my-uuid) (my-uuid) (my-uuid)))

(defn activity-uuid
  "Generate a UUID for an entry"
 []
 (str (my-uuid) "-" (my-uuid) "-" (my-uuid)))

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

(defn valid-email? [addr]
  (when addr
    (email/isValidAddress addr)))

(defn valid-domain? [domain]
  (when (string? domain)
    (let [re (js/RegExp "^@?[a-z0-9.-]+\\.[a-z]{2,4}$" "i")]
      (pos? (count (.match domain re))))))

(defn remove-tooltips []
  (.remove (js/$ "div.tooltip")))

(defn parse-input-email [email-address]
  (let [parsed-email (email/parse email-address)]
    {:name (.getName parsed-email) :address (.getAddress parsed-email)}))

(defn get-author
  "Get the author data from the org list of authors"
  [user-id authors]
  (some #(when (= (:user-id %) user-id) %) authors))

(defn get-user-type
  "Calculate the user type, return admin if it's an admin,
  check if it's in the authors list if not admin
  return viewer else."
  [user-data org-data & [board-data]]
  (cond
    ;; if :admin is present and it's true
    (true? (:admin? user-data))
    :admin
    ;; if :admin is present and it's a list of teams
    ;; and it contains the actual org team
    (in? (:admin user-data) (:team-id org-data))
    :admin
    ;; if the user is in the list of authors of the org data
    ;; or if a board is passed and the authors list contains the user-id
    (or (get-author (:user-id user-data) (:authors org-data))
        (and board-data
             (get-author (:user-id user-data) (:authors board-data))))
    :author
    ;; viewer in all other cases
    :else
    :viewer))

(defn index-of
  "Given a collection and a function return the index that make the function truely."
  [s f]
  (loop [idx 0 items s]
    (cond
      (empty? items) nil
      (f (first items)) idx
      :else (recur (inc idx) (rest items)))))

(defn name-or-email [user]
  (let [user-name (:name user)
        first-name (:first-name user)
        last-name (:last-name user)]
    (cond
      (and (seq first-name)
           (seq last-name))
      (str first-name " " last-name)
      (seq first-name)
      first-name
      (seq last-name)
      last-name
      (seq user-name)
      user-name
      :else
      (:email user))))

(defn slack-link-with-state [original-url user-id team-id redirect]
  (clojure.string/replace
   original-url
   team-id
   (str
    (when (seq team-id) (str team-id ":"))
    (when (seq user-id) (str user-id ":"))
    redirect)))

(def generic-network-error
 "There may be a problem with your network, or with our servers. Please try again later.")

(defn clean-google-chart-url [gchart-url]
  (if (string? gchart-url)
    (.replace gchart-url #"(?i)/u/\d+" "")
    ""))

(defn valid-google-chart-url? [url]
  (when (string? url)
    (let [cleaned-url (clean-google-chart-url url)]
      (not= (.indexOf cleaned-url "://docs.google.com/spreadsheets/d/") -1))))

(defn cdn [img-src & [no-deploy-folder]]
  (let [use-cdn? (empty? ls/cdn-url)
        cdn (if use-cdn? "" (str "/" ls/cdn-url))
        deploy-key (if (empty? ls/deploy-key) "" (str "/" ls/deploy-key))
        with-deploy-folder (if no-deploy-folder
                             cdn
                             (str cdn deploy-key))]
    (str (when use-cdn? with-deploy-folder) img-src)))

(defn rgb-with-opacity [rgb opacity]
  (str "rgba(" (clojure.string/join "," (conj (vec (css-color rgb)) opacity)) ")"))

(defn get-24h-time
  [js-date]
  (str (.getHours js-date) ":" (add-zero (.getMinutes js-date))))

(defn get-ampm-time
  [js-date]
  (let [hours (.getHours js-date)
        minutes (add-zero (.getMinutes js-date))
        ampm (if (>= hours 12) " p.m." " a.m.")
        hours (mod hours 12)
        hours (if (zero? hours) 12 hours)]
    (str hours ":" minutes ampm)))

(defn format-time-string [js-date]
  (let [r (js/RegExp "am|pm" "i")
        h12 (or (.match (.toLocaleTimeString js-date) r) (.match (str js-date) r))]
    (if h12
      (get-ampm-time js-date)
      (get-24h-time js-date))))

(defn activity-date-string [js-date hide-time]
  (let [time-string (format-time-string js-date)]
    (str
     (full-month-string (inc (.getMonth js-date)))
     " "
     (.getDate js-date)
     ", "
     (.getFullYear js-date)
     (when-not hide-time
      (str " at " time-string)))))

(defn activity-date
  "Get a string representing the elapsed time from a date in the past"
  [past-js-date & [hide-time]]
  (let [past (.getTime past-js-date)
        now (.getTime (js-date))
        seconds (.floor js/Math (/ (- now past) 1000))
        years-interval (.floor js/Math (/ seconds 31536000))
        months-interval (.floor js/Math (/ seconds 2592000))
        days-interval (.floor js/Math (/ seconds 86400))
        hours-interval (.floor js/Math (/ seconds 3600))
        minutes-interval (.floor js/Math (/ seconds 60))]
    (cond
      (pos? years-interval)
      (str "on " (activity-date-string past-js-date hide-time))

      (or (pos? months-interval)
          (> days-interval 7))
      (str "on " (activity-date-string past-js-date hide-time))

      (pos? days-interval)
      (str days-interval " " (pluralize "day" days-interval) " ago")

      (pos? hours-interval)
      (str hours-interval " " (pluralize "hour" hours-interval) " ago")

      (pos? minutes-interval)
      (str minutes-interval " " (pluralize "min" minutes-interval) " ago")

      :else
      "just now")))

(defn entry-date-tooltip [entry-data]
  (let [created-at (js-date (or (:published-at entry-data) (:created-at entry-data)))
        updated-at (js-date (:updated-at entry-data))
        created-str (activity-date created-at)
        updated-str (activity-date updated-at)]
    (if (= (:created-at entry-data) (:updated-at entry-data))
      (str "Posted " created-str)
      (str "Posted " created-str "\nEdited " updated-str " by " (:name (last (:author entry-data)))))))

(defn activity-date-tooltip [activity-data]
  (entry-date-tooltip activity-data))

(defn ios-copy-to-clipboard [el]
  (let [old-ce (.-contentEditable el)
        old-ro (.-readOnly el)
        rg (.createRange js/document)]
    (set! (.-contentEditable el) true)
    (set! (.-readOnly el) false)
    (.selectNodeContents rg el)
    (let [s (.getSelection js/window)]
      (.removeAllRanges s)
      (.addRange s rg)
      (.setSelectionRange el 0 (.. el -value -length))
      (set! (.-contentEditable el) old-ce)
      (set! (.-readOnly el) old-ro))))

(defn copy-to-clipboard [el]
  (try
    (when (and (responsive/is-tablet-or-mobile?)
               (js/isSafari))
      (ios-copy-to-clipboard el))
    (.execCommand js/document "copy")
    (catch :default e
      false)))

(defn body-without-preview [body]
  (let [body-without-tags (-> body strip-img-tags strip-br-tags strip-empty-tags)
        hidden-class (str "activity-body-" (int (rand 10000)))
        $body-content (js/$ (str "<div class=\"" hidden-class " hidden\">" body-without-tags "</div>"))
        appened-body (.append (js/$ (.-body js/document)) $body-content)
        _ (.each (js/$ (str "." hidden-class " .carrot-no-preview")) #(this-as this
                                                                        (let [$this (js/$ this)]
                                                                          (.remove $this))))
        $hidden-div (js/$ (str "." hidden-class))
        body-without-preview (.html $hidden-div)
        _ (.remove $hidden-div)]
    body-without-preview))

(defn newest-org [orgs]
  (first (sort-by :created-at orgs)))

(defn get-default-org [orgs]
  (if-let [last-org-slug (cook/get-cookie (router/last-org-cookie))]
    (let [last-org (first (filter #(= (:slug %) last-org-slug) orgs))]
      (or
        ; Get the last accessed board from the saved cookie
        last-org
        ; Fallback to the newest board if the saved board was not found
        (newest-org orgs)))
    (newest-org orgs)))

;; Get the board to show counting the last accessed and the last created

(def default-board "all-posts")

(defn get-default-board [org-data]
  (let [last-board-slug default-board]
    ; Replace default-board with the following to go back to the last visited board
    ; (or (cook/get-cookie (router/last-board-cookie (:slug org-data))) default-board)]
    (if (and (= last-board-slug "all-posts")
             (link-for (:links org-data) "activity"))
      {:slug "all-posts"}
      (let [boards (:boards org-data)
            board (first (filter #(= (:slug %) last-board-slug) boards))]
        (or
          ; Get the last accessed board from the saved cookie
          board
          (let [sorted-boards (vec (sort-by :name boards))]
            (first sorted-boards)))))))

(defn clean-body-html [inner-html]
  (let [$container (.html (js/$ "<div class=\"hidden\"/>") inner-html)
        _ (.append (js/$ (.-body js/document)) $container)
        _ (.remove (js/$ ".rangySelectionBoundary" $container))
        cleaned-html (.html $container)
        _ (.detach $container)]
    cleaned-html))

(defn your-digest-url []
  (if-let [org-slug (cook/get-cookie (router/last-org-cookie))]
    (if-let [board-slug "all-posts"]
      ;; Repalce all-posts above with the following to go back to the last visited board
      ;; (cook/get-cookie (router/last-board-cookie org-slug))]
      (oc-urls/board org-slug board-slug)
      (oc-urls/org org-slug))
    oc-urls/login))

(defn storage-url-org-slug [storage-url]
  (let [parts (s/split storage-url "/")]
    (if (s/starts-with? storage-url "http")
      (nth parts 4)
      (nth parts 2))))

(def default-headline "Untitled post")

(def default-drafts-board-name "Drafts")

(def default-drafts-board-slug "drafts")

(def default-draft-status "draft")


(def default-drafts-board
  {:uuid "0000-0000-0000"
   :created-at "2000-01-01T00:00:00.000Z"
   :updated-at "2000-01-01T00:00:00.000Z"
   :slug default-drafts-board-slug
   :name default-drafts-board-name
   :entries []
   :fixed-items {}
   :access "private"
   :read-only true})

(def default-section-slug "--default-section-slug")
(def default-section-access "team")

(def default-section
  {:name ""
   :access default-section-access
   :slug default-section-slug})

(defn retina-src [url]
  {:src (cdn (str url ".png"))
   :src-set (str (cdn (str url "@2x.png")) " 2x")})