(ns oc.web.components.ui.user-avatar
  (:require-macros [if-let.core :refer (when-let*)])
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.stores.user :as store]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.responsive :as responsive]))

(rum/defcs user-avatar-image < rum/static
                               (rum/local false ::use-default)
  [s user-data tooltip?]
  (let [use-default @(::use-default s)
        default-avatar (store/user-icon (:user-id user-data))
        user-avatar-url (if (or use-default (empty? (:avatar-url user-data)))
                         (utils/cdn default-avatar)
                         (:avatar-url user-data))]
    [:div.user-avatar-img-container.fs-hide
      [:div.user-avatar-img-helper]
      [:img.user-avatar-img
        {:src user-avatar-url
         :on-error #(reset! (::use-default s) true)
         :data-toggle (if tooltip? "tooltip" "")
         :data-placement "top"
         :data-container "body"
         :title (if tooltip? (:name user-data) "")}]]))

(rum/defcs user-avatar < rum/static
                         rum/reactive
                         (drv/drv :current-user-data)
  [s {:keys [classes click-cb disable-menu]}]
  (let [not-mobile? (not (responsive/is-tablet-or-mobile?))]
    [:button.user-avatar-button.group
      {:type "button"
       :class (str classes)
       :id "dropdown-toggle-menu"
       :data-toggle (when (or not-mobile? (not disable-menu)) "dropdown")
       :on-click (when (fn? click-cb) (click-cb))
       :aria-haspopup true
       :aria-expanded false}
      (user-avatar-image (drv/react s :current-user-data) not-mobile?)]))