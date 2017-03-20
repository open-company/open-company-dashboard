(ns oc.web.components.org-editor
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [rum.core :as rum]
            [cuerdas.core :as s]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.jwt :as jwt]
            [oc.web.lib.oc-colors :as occ]
            [oc.web.lib.responsive :as responsive]
            [oc.web.components.ui.navbar :refer (navbar)]
            [oc.web.components.ui.small-loading :as loading]
            [oc.web.components.ui.popover :refer (add-popover hide-popover)]))

(defn- create-org-alert [owner]
  (add-popover {:container-id "create-org-alert"
                :message "Please enter a company name."
                :height "120px"
                :success-title "GOT IT"
                :success-cb #(hide-popover nil "create-org-alert")}))

(defn create-org-clicked [owner e]
  (utils/event-stop e)
  (when-not (om/get-state owner :loading)
    (let [data         (om/get-props owner)
          org-name (:create-org data)]
      (if (clojure.string/blank? org-name)
        (create-org-alert owner)
        (do
          (om/set-state! owner :loading true)
          (dis/dispatch! [:create-org]))))))

(defn setup-org-name [data]
  (when-let [team-data (first (:teams (:teams-data data)))]
    ;; using utils/after here because we can't dispatch inside another dispatch.
    ;; ultimately we should switch to some event-loop impl that works like a proper queue
    ;; and does not have these limitations
    (utils/after 1 #(dis/dispatch! [:input [:create-org] (or (:name team-data) "")]))))

(defcomponent org-editor [data owner]

  (init-state [_]
    {:loading false
     :name-did-change false})

  (did-mount [_]
    (utils/update-page-title "OpenCompany - Setup Your Organization")
    (when-not (:create-org data)
      (setup-org-name data)))

  (will-receive-props [_ next-props]
    (when-not (om/get-state owner :name-did-change)
      (setup-org-name next-props)))

  (render-state [_ {:keys [loading]}]
    (dom/div {:class "org-editor"}
      (dom/div {:class "fullscreen-page group"}
        (om/build navbar {:hide-right-menu true :show-navigation-bar true})
        (dom/div {:class "org-editor-box group navbar-offset"}
          (dom/form {:on-submit (partial create-org-clicked owner)}
            (dom/div {:class "form-group"}
              (when (and (jwt/jwt) (jwt/get-key :first-name))
                (dom/label {:class "org-editor-message"} (str "Hi " (s/capital (jwt/get-key :first-name)) "!")))
              (dom/label {:class "org-editor-message"} "What's the name of your organization?")
              (dom/input {:type "text"
                          :class "org-editor-input domine h4"
                          :style #js {:width "100%"}
                          :placeholder "Simple name without the Inc., LLC, etc."
                          :value (or (:create-org data) "")
                          :on-change #(do
                                        (om/set-state! owner :name-did-change true)
                                        (dis/dispatch! [:input [:create-org] (.. % -target -value)]))})))
            (dom/div {:class "center"}
              (dom/button {:class "btn-reset btn-solid get-started-button"
                           :disabled (not (pos? (count (:create-org data))))
                           :on-click (partial create-org-clicked owner)}
                          (when loading
                            (loading/small-loading {:class "left mt1"}))
                          (dom/label {:class (str "pointer mt1" (when loading " ml2"))} "OK, LET’S GO"))))))))