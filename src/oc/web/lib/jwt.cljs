(ns oc.web.lib.jwt
  (:require [oc.web.lib.cookies :as cook]
            [goog.date.DateTime :as gdt]
            [goog.date :as gd]))

(defn jwt []
  (cook/get-cookie :jwt))

(defn decode [encoded-jwt]
  (when (exists? js/jwt_decode)
    (js/jwt_decode encoded-jwt)))

(defn get-contents []
  (some-> (jwt) decode (js->clj :keywordize-keys true)))

(defn get-key [k]
  (-> (get-contents) (get k)))

(defn expired? []
  (let [expire (gdt/fromTimestamp (get-key :expire))]
    (= expire (gd/min (js/Date.) expire))))

(defn is-slack-org? []
  (= (get-key :auth-source) "slack"))

(defn team-id []
  (first (get-key :teams)))

(defn is-admin? [team-id]
  (let [admins (get-key :admin)]
    (some #{team-id} admins)))

(set! (.-OCWebPrintJWTContents js/window) #(js/console.log (get-contents)))