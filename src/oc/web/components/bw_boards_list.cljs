(ns oc.web.components.bw-boards-list
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :refer-macros (sel1 sel)]
            [oc.web.api :as api]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.responsive :as responsive]
            [oc.web.components.ui.popover :refer (add-popover hide-popover)]))

(defn- delete-board-alert [e board-slug]
  (utils/event-stop e)
  (add-popover {:container-id "delete-board-alert"
                :message "Are you sure you want to delete this board?"
                :height "130px"
                :success-title "DELETE"
                :success-cb #(do
                                (dis/dispatch! [:delete-board board-slug])
                                (hide-popover nil "delete-board-alert"))
                :cancel-title "KEEP IT"
                :cancel-cb #(hide-popover nil "delete-board-alert")}))

(defn sorted-boards [boards]
  (into [] (sort #(compare (:name %1) (:name %2)) boards)))

(defcomponent bw-boards-list
  [{:keys [org-data board-data card-width show-add-topic] :as data} owner options]

  (did-mount [_]
    (when-not (utils/is-test-env?)
      (.tooltip (js/$ "[data-toggle=\"tooltip\"]"))))

  (did-update [_ prev-props _]
    (when (and (nil? (:create-board prev-props))
               (not (nil? (om/get-props owner :create-board))))
      (utils/after 100 #(.focus (js/$ "input.board-name"))))
    (when-not (utils/is-test-env?)
      (.tooltip (js/$ "[data-toggle=\"tooltip\"]"))))

  (render [_]
    (when-not (:dashboard-sharing data)
      (dom/div {:class "left-boards-list group" :style {:width (str responsive/left-boards-list-width "px")}}
        (dom/div {:class "left-boards-list-top group"}
          (dom/h3 {:class "left-boards-list-top-title"} "BOARDS")
          (when (and (not (responsive/is-tablet-or-mobile?))
                     (utils/link-for (:links org-data) "create"))
            (dom/button {:class "left-boards-list-top-title btn-reset right"
                         :on-click #(when (nil? (:foce-key data))
                                      (dis/dispatch! [:input [:create-board] ""]))
                         :title "Create a new board"
                         :data-placement "top"
                         :data-toggle "tooltip"
                         :data-container "body"}
              (dom/i {:class "fa fa-plus-circle"}))))
        (dom/div {:class (str "left-boards-list-items group")}
          (for [board (sorted-boards (:boards org-data))]
            (dom/div {:class (utils/class-set {:left-boards-list-item true
                                               :highlight-on-hover (nil? (:foce-key data))
                                               :group true
                                               :selected (= (router/current-board-slug) (:slug board))})
                      :style {:width (str (- responsive/left-boards-list-width 5) "px")}
                      :data-board (name (:slug board))
                      :key (str "bw-board-list-" (name (:slug board)))
                      :on-click #(when (nil? (:foce-key data))
                                   (router/nav! (oc-urls/board (router/current-org-slug) (:slug board))))}
              (dom/div {:class "internal"
                        :key (str "bw-board-list-" (name (:slug board)) "-internal")}
                (or (:name board) (:slug board)))
              (when (utils/link-for (:links board) "delete")
                (dom/button {:class "remove-board btn-reset"
                             :title "Delete this board"
                             :data-toggle "tooltip"
                             :data-placement "top"
                             :data-container "body"
                             :on-click #(delete-board-alert % (:slug board))}
                  (dom/i {:class "fa fa-times"})))))
          (when-not (nil? (:create-board data))
            (dom/div {:class "left-boards-list-item group"}
              (dom/input {:class "board-name left"
                          :value (:create-board data)
                          :data-toggle "tooltip"
                          :data-placement "right"
                          :data-container "body"
                          :title "Press Enter to submit or Escape to cancel."
                          :on-change #(dis/dispatch! [:input [:create-board] (.. % -target -value)])
                          :on-blur #(when (empty? (:create-board data))
                                      (dis/dispatch! [:input [:create-board] nil]))
                          :on-key-up (fn [e]
                                          (cond
                                            (= "Enter" (.-key e))
                                            (dis/dispatch! [:create-board])
                                            (= "Escape" (.-key e))
                                            (dis/dispatch! [:input [:create-board] nil])))}))))))))