(ns open-company-web.actions
  (:require [medley.core :as med]
            [clojure.string :as string]
            [open-company-web.dispatcher :as dispatcher]
            [open-company-web.lib.utils :as utils]
            [open-company-web.urls :as oc-urls]
            [open-company-web.router :as router]
            [open-company-web.caches :as cache]
            [open-company-web.api :as api]))

;; ---- Generic Actions Dispatch
;; This is a small generic abstraction to handle "actions".
;; An `action` is a transformation on the app state.
;; The return value of an action will be used as the new app-state.

;; The extended multimethod `action` is defined in the dispatcher
;; namespace to avoid cyclical dependencies between namespaces

(defmethod dispatcher/action :default [db payload]
  (js/console.warn "No handler defined for" (str (first payload)))
  (js/console.log "Full event: " (pr-str payload))
  db)

(defmethod dispatcher/action :input [db [_ path value]]
  (assoc-in db path value))

(defmethod dispatcher/action :entry [db [_ {:keys [links]}]]
  (let [create-link    (med/find-first #(= (:rel %) "company-create") links)
        slug           (fn [co] (last (string/split (:href co) #"/")))
        [first second] (filter #(= (:rel %) "company") links)]
    (cond
      (and first (not second)) (router/nav! (oc-urls/company (slug first)))
      (and first second)       (router/nav! oc-urls/companies)
      create-link              (router/nav! oc-urls/create-company)))
  db)

(defmethod dispatcher/action :company-submit [db _]
  (api/post-company (:company-editor db))
  db)

(defmethod dispatcher/action :company-created [db [_ body]]
  (if (:links body)
    (let [updated (utils/fix-sections body)]
      (router/redirect! (oc-urls/company (:slug updated)))
      (assoc-in db (dispatcher/company-data-key (:slug updated)) updated))
    db))

(defmethod dispatcher/action :new-section [db [_ body]]
  (when body
    (let [slug (:slug body)
          response (:response body)]
      (swap! cache/new-sections assoc-in [(keyword slug) :categories] (:categories response))
      ;; signal to the app-state that the new-sections have been loaded
      (assoc-in db [(keyword slug) :new-sections] (rand 4)))))

(defmethod dispatcher/action :auth-settings [db [_ body]]
  (when body
    (-> db
        (assoc :auth-settings body)
        (dissoc :loading))))

(defmethod dispatcher/action :revision [db [_ body]]
  (when body
    (let [fixed-section (utils/fix-section (:body body) (:section body) true)
          assoc-in-coll [(:slug body) (:section body) (:updated-at fixed-section)]]
      (swap! cache/revisions assoc-in assoc-in-coll fixed-section)
      (dissoc db :loading))) ; remove loading key
  db)

(defmethod dispatcher/action :section [db [_ body]]
  (if body
    (let [fixed-section (utils/fix-section (:body body) (:section body))]
      (-> db
          (assoc-in (dispatcher/company-section-key (:slug body) (:section body)) fixed-section)
          (dissoc :loading)))
    db))

(defmethod dispatcher/action :company [db [_ {:keys [slug success status body]}]]
  (cond
    success
    ;; add section name inside each section
    (let [updated-body (utils/fix-sections body)]
      (-> db
          (assoc-in (dispatcher/company-data-key (:slug updated-body)) updated-body)
          (dissoc :loading)))
    (= 404 status)
    (do
      (router/redirect-404!)
      db)
    ;; probably some default failure handling should be added here
    :else db))

(defmethod dispatcher/action :companies [db [_ body]]
  (if body
    (-> db
     (assoc-in dispatcher/companies-key (:companies (:collection body)))
     (dissoc :loading))
    db))

(defmethod dispatcher/action :su-list [db [_ {:keys [slug response]}]]
  (-> db
    (assoc-in (dispatcher/su-list-key slug) response)
    (dissoc :loading)))

(defmethod dispatcher/action :su-edit [db [_ {:keys [slug stakeholder-update]}]]
  (-> db
    (assoc-in (dispatcher/latest-stakeholder-update-key slug) stakeholder-update)
    (dissoc :loading)))

(defmethod dispatcher/action :stakeholder-update [db [_ {:keys [slug update-slug response]}]]
  (-> db
    (assoc-in (dispatcher/stakeholder-update-key slug update-slug) response)
    (dissoc :loading)))