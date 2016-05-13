(ns open-company-web.components.footer
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [open-company-web.urls :as oc-urls]
            [open-company-web.lib.responsive :as responsive]
            [open-company-web.components.ui.icon :refer (icon)]))

(defcomponent footer [data owner options]
  (render [_]
    (dom/div {:class "footer"}
      (dom/div {:class "footer-cta"}
        "Powered by OpenCompany")
      (dom/div {:class "footer-internal"}
        (dom/div {:class (str "footer-bottom columns-" (:columns-num data))}
          (when-not (responsive/is-mobile)
            (dom/a {:class "oc-logo" :href oc-urls/home} (dom/img {:src "/img/oc-logo-grey.svg"})))
          (dom/a {:class "footer-link" :href oc-urls/about} (str "ABOUT"  (when-not (responsive/is-mobile) " US")))
          (dom/a {:class "footer-link" :target "_blank" :href "mailto:oc@opencompany.com"} (str "CONTACT" (when-not (responsive/is-mobile) " US")))
          (when (responsive/is-mobile)
            (dom/a {:class "twitter" :target "_blank" :href oc-urls/oc-twitter :alt "twitter"}
              (dom/img {:src "/img/twitter.svg"})))
          (when (responsive/is-mobile)
            (dom/a {:class "github" :target "_blank" :href oc-urls/oc-github :alt "github"}
              (dom/img {:src "/img/github.svg"})))
          (when-not (responsive/is-mobile)
            (dom/div {:class "footer-bottom-right"}
              (dom/a {:class "twitter" :target "_blank" :href oc-urls/oc-twitter :alt "twitter"}
                (dom/img {:src "/img/twitter.svg"}))
              (dom/a {:class "github" :target "_blank" :href oc-urls/oc-github :alt "github"}
                (dom/img {:src "/img/github.svg"})))))))))