(ns oc.web.actions.routing
  (:require [oc.web.urls :as oc-urls]
            [oc.web.lib.jwt :as jwt]
            [oc.web.utils.dom :as du]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.lib.cljs.useragent :as ua]))

(defn post-routing []
  ;; Re-dispatch the current change data
  (dis/dispatch! [:container/status (dis/change-data) true]))

(defn maybe-404
  ([] (maybe-404 false))
  ([force-404?]
  (if (or (jwt/jwt)
          (jwt/id-token)
          force-404?)
    (router/redirect-404!)
    (dis/dispatch! [:show-login-wall]))))

(defn switch-org-dashboard [org]
  (du/force-unlock-page-scroll)
  (dis/dispatch! [:org-nav-out (dis/current-org-slug) (:slug org)])
  (router/nav! (oc-urls/default-landing (:slug org))))