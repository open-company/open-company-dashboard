(ns oc.web.urls
  (:require [oc.web.router :as router]
            [oc.web.lib.jwt :as j]
            [clojure.string :as clj-str]))

(defn params->query-string [m]
     (clojure.string/join "&" (for [[k v] m] (str (name k) "=" v))))

;; Main

(def home "/")

(def home-no-redirect (str home "?no_redirect=1"))

(def about "/about")

(def slack "/slack")

(def blog "https://blog.carrot.io")

(def contact "/contact")

(def help "https://beta.carrot.io/carrot-support/all-posts")

(def what-s-new "https://beta.carrot.io/carrot-support/what-s-new")

(def home-try-it-focus (str home "?tif"))

(def contact-email "hello@carrot.io")
(def contact-mail-to (str "mailto:" contact-email))

(def login "/login")
(def sign-up "/sign-up")
(def sign-up-slack "/sign-up/slack")
(def sign-up-profile "/sign-up/profile")
(def sign-up-team "/sign-up/team")

(defn sign-up-update-team
  ([]
    (sign-up-update-team (router/current-org-slug)))
  ([org-slug]
    (str sign-up "/" (name org-slug) "/team")))

(defn sign-up-invite
  ([]
    (sign-up-invite (router/current-org-slug)))
  ([org-slug]
    (str sign-up "/" (name org-slug) "/invite")))

(defn sign-up-setup-sections
  ([]
    (sign-up-setup-sections (router/current-org-slug)))
  ([org-slug]
    (str sign-up "/" (name org-slug) "/sections")))

(def slack-lander-check "/slack-lander/check")

(def google-lander-check "/google/lander")

(def logout "/logout")

(def pricing "/pricing")

(def terms "/terms")

(def privacy "/privacy")

(defn not-found [& [params]]
  (str "/404" (when params (str "?" (params->query-string params)))))

(def oc-twitter "https://twitter.com/carrot_hq")

(def oc-facebook "https://www.facebook.com/Carrot-111981319388047/")

(def oc-github "https://github.com/open-company")

(def oc-trello-public "https://trello.com/b/eKs2LtLu")

(def subscription-callback "/subscription-completed")

(def email-confirmation "/verify")

(def confirm-invitation "/invite")
(def confirm-invitation-password "/invite/password")
(def confirm-invitation-profile "/invite/profile")

(def password-reset "/reset")

(def email-wall "/email-required")

;; User

(def user-profile "/profile")
(def user-notifications "/profile/notifications")

;; Organizations

(defn org
  "Org url"
  ([]
    (org (router/current-org-slug)))
  ([org-slug]
    (str "/" (name org-slug))))

(defn all-posts
  "Org all posts url"
  ([]
    (all-posts (router/current-org-slug)))
  ([org-slug]
    (str (org org-slug) "/all-posts")))

(defn must-see
  "Org must see url"
  ([]
    (must-see (router/current-org-slug)))
  ([org-slug]
    (str (org org-slug) "/must-see")))

;; Boards

(defn board
  "Board url"
  ([]
    (board (router/current-org-slug) (router/current-board-slug)))
  ([board-slug]
    (board (router/current-org-slug) board-slug))
  ([org-slug board-slug]
   (str (org org-slug) "/" (name board-slug))))

;; Drafts

(defn drafts
  ([]
    (drafts (router/current-org-slug)))
  ([org-slug]
    (str (org org-slug) "/drafts")))

;; Entries

(defn entry
  "Entry url"
  ([] (entry (router/current-org-slug) (router/current-board-slug) (router/current-activity-id)))
  ([entry-uuid] ( (router/current-org-slug) (router/current-board-slug) entry-uuid))
  ([board-slug entry-uuid] (entry (router/current-org-slug) board-slug entry-uuid))
  ([org-slug board-slug entry-uuid] (str (board org-slug board-slug) "/post/" (name entry-uuid))))

;; Secure activities

(defn secure-activity
  "Secure url for activity to show read only view."
  ([] (secure-activity (router/current-org-slug) (router/current-activity-id)))
  ([secure-id] (secure-activity (router/current-org-slug) secure-id))
  ([org-slug secure-id] (str (org org-slug) "/post/" secure-id)))