(ns oc.web.components.ui.alert-modal
  (:require [rum.core :as rum]
            [dommy.core :as dommy :refer-macros (sel1)]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]))

(defn dismiss-modal []
  (dis/dispatch! [:alert-modal-hide-done]))

(defn close-clicked [s]
  (reset! (::dismiss s) true)
  (utils/after 180 dismiss-modal))

(defn first-button-clicked [alert-modal e]
  (utils/event-stop e)
  (when (fn? (:first-button-cb alert-modal))
    ((:first-button-cb alert-modal))))

(defn yes-button-clicked [alert-modal e]
  (utils/event-stop e)
  (when (fn? (:second-button-cb alert-modal))
    ((:second-button-cb alert-modal))))

(rum/defcs alert-modal < (drv/drv :alert-modal)
                         rum/reactive
                         (rum/local false ::first-render-done)
                         (rum/local false ::dismiss)
                         {:did-mount (fn [s]
                                       ;; Add no-scroll to the body to avoid scrolling while showing this modal
                                       (dommy/add-class! (sel1 [:body]) :no-scroll)
                                       s)
                          :after-render (fn [s]
                                          (when (not @(::first-render-done s))
                                            (reset! (::first-render-done s) true))
                                          (let [alert-modal @(drv/get-ref s :alert-modal)]
                                            (when (:dismiss alert-modal)
                                              (close-clicked s)))
                                          s)
                          :will-unmount (fn [s]
                                          ;; Remove no-scroll class from the body tag
                                          (dommy/remove-class! (sel1 [:body]) :no-scroll)
                                          s)}
  "Customizable alert modal. It gets the following property from the :alert-modal derivative:
   :icon The src to use for an image, it's encapsulated in utils/cdn.
   :title The title of the view.
   :message A description message to show in the view.
   :first-button-title The title for the first button, it's black link styled.
   :first-button-cb The function to execute when the first button is clicked.
   :second-button-title The title for the second button, it's green solid styled.
   :second-button-cb The function to execute when the second button is clicked."
  [s]
  (let [alert-modal (drv/react s :alert-modal)]
    [:div.alert-modal-container
      {:class (utils/class-set {:will-appear (or @(::dismiss s) (not @(::first-render-done s)))
                                :appear (and (not @(::dismiss s)) @(::first-render-done s))})}
      [:div.alert-modal
        (when (:icon alert-modal)
          [:img.alert-modal-icon {:src (utils/cdn (:icon alert-modal))}])
        (when (:title alert-modal)
          [:div.alert-modal-title
            (:title alert-modal)])
        (when (:message alert-modal)
          [:div.alert-modal-message
            (:message alert-modal)])
        (when (or (:first-button-title alert-modal)
                  (:second-button-title alert-modal))
          [:div.alert-modal-buttons.group
            {:class (when (or (not (:first-button-title alert-modal))
                              (not (:second-button-title alert-modal))) "single-button")}
            (when (:first-button-title alert-modal)
              [:button.mlb-reset.mlb-link-black
                {:on-click #(first-button-clicked alert-modal %)}
                (:first-button-title alert-modal)])
            (when (:second-button-title alert-modal)
              [:button.mlb-reset.mlb-default
                {:on-click #(yes-button-clicked alert-modal %)}
                (:second-button-title alert-modal)])])]]))