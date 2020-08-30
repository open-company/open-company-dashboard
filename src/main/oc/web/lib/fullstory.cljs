(ns oc.web.lib.fullstory
  (:require [oc.web.lib.jwt :as jwt]))

(defn identify []
  (when (and (exists? js/FS)
             (or (jwt/jwt)
                 (jwt/id-token)))
    (let [is-id-token? (jwt/id-token)
          user-data (if is-id-token?
                      (jwt/get-id-token-contents)
                      (jwt/get-contents))]
      (.identify js/FS (:user-id user-data)
       (clj->js {:displayName (or (:name user-data) (str (:first-name user-data) " " (:last-name user-data)))
                 :securePostId (boolean is-id-token?)
                 :email (:email user-data)})))))

(defn track-org [org-data]
  (when (and (exists? js/FS)
             (map? org-data))
    (.setUserVars js/FS
     (clj->js {:org (:name org-data)
               :org_slug (:slug org-data)
               ;; FIXME: add back user role here if we start using FS seriously
               ;; removed for circular dependency problem
               ;; (user-store/get-user-type (dis/current-user-data) org-data)
               :role "-"}))))

(defn session-url []
  (when (and (exists? js/FS)
             (.-getCurrentSessionURL js/FS))
    (.getCurrentSessionURL js/FS)))