(ns open-company-web.components.finances
    (:require [om.core :as om :include-macros true]
              [om-tools.core :as om-core :refer-macros [defcomponent]]
              [om-tools.dom :as dom :include-macros true]
              [open-company-web.lib.utils :refer [abs thousands-separator get-symbols-for-currency-code get-channel save-values change-value]]
              [open-company-web.components.report-line :refer [report-line]]
              [open-company-web.components.comment :refer [comment-component]]
              [om-bootstrap.random :as  r]
              [om-bootstrap.panel :as p]
              [cljs.core.async :refer [put!]]))

(defcomponent finances [data owner]
  (will-mount [_]
    (let [finances (:finances data)
          cash (:cash finances)
          revenue (:revenue finances)
          costs (:costs finances)]
      (om/set-state! owner :cash (thousands-separator cash))
      (om/set-state! owner :revenue (thousands-separator revenue))
      (om/set-state! owner :costs (thousands-separator costs))))
  (render [_]
    (let [finances (:finances data)
          cash (:cash finances)
          revenue (:revenue finances)
          costs (:costs finances)
          currency (:currency data)
          burn-rate (- revenue costs)
          burn-rate-label (if (> burn-rate 0) "Growth rate" "Burn rate")
          burn-rate-classes (str "num " (if (> burn-rate 0) "green" "red"))
          burn-rate-helper (if (> burn-rate 0) "Cash earned this quarter" "Cash used this quarter")
          profitable (if (> burn-rate 0) "Yes" "No")
          run-away (if (<= burn-rate 0) (quot cash burn-rate) "N/A")
          currency-symbol (get-symbols-for-currency-code currency)]
      (p/panel {:header (dom/h3 "Finances") :class "finances clearfix"}
        (dom/div {:class "row"}
          (dom/form {:class "form-horizontal"}

            ;; Cash
            (dom/div {:class "form-group"}
              (dom/label {:for "cash" :class "col-md-2 control-label"} "Cash on hand")
              (dom/div {:class "input-group col-md-2"}
                (dom/div {:class "input-group-addon"} currency-symbol)
                (dom/input {
                  :type "text"
                  :id "cash"
                  :class "form-control"
                  :value (om/get-state owner :cash)
                  :on-focus #(om/set-state! owner :cash cash)
                  :placeholder "US Dollar"
                  :on-change #(om/set-state! owner :cash (.. % -target -value))
                  :on-blur (fn [e]
                              (change-value finances e :cash)
                              (om/set-state! owner :cash (thousands-separator (.. e -target -value)))
                              (save-values "save-report"))
                  }))
              (dom/p {:class "help-block"} "Cash and cash equivalents"))

            ;; Renvenue
            (dom/div {:class "form-group"}
              (dom/label {:for "revenue" :class "col-md-2 control-label"} "Revenue")
              (dom/div {:class "input-group col-md-2"}
                (dom/div {:class "input-group-addon"} currency-symbol)
                (dom/input {
                  :type "text"
                  :id "revenue"
                  :class "form-control"
                  :value (om/get-state owner :revenue)
                  :on-focus #(om/set-state! owner :revenue revenue)
                  :placeholder "US Dollar"
                  :on-change #(om/set-state! owner :revenue (.. % -target -value))
                  :on-blur (fn [e]
                              (change-value finances e :revenue)
                              (om/set-state! owner :revenue (thousands-separator (.. e -target -value)))
                              (save-values "save-report"))
                  }))
              (dom/p {:class "help-block"} "All revenue this quarter (not investments)"))

            ;; Costs
            (dom/div {:class "form-group"}
              (dom/label {:for "costs" :class "col-md-2 control-label"} "Costs")
              (dom/div {:class "input-group col-md-2"}
                (dom/div {:class "input-group-addon"} currency-symbol)
                (dom/input {
                  :type "text"
                  :id "costs"
                  :class "form-control"
                  :value (om/get-state owner :costs)
                  :on-focus #(om/set-state! owner :costs costs)
                  :placeholder "US Dollar"
                  :on-change #(om/set-state! owner :costs (.. % -target -value))
                  :on-blur (fn [e]
                              (change-value finances e :costs)
                              (om/set-state! owner :costs (thousands-separator (.. e -target -value)))
                              (save-values "save-report"))
                  }))
              (dom/p {:class "help-block"} "All costs this quarter including salaries"))

            ;; Runaway
            (dom/div {:class "form-group"}
              (dom/label {:class "col-md-2 control-label"} "Profitable?")
              (dom/label {:class "col-md-2 control-label"} profitable)
              (dom/p {:class "help-block"} "Costs exceed revenue this quarter"))

            ;; Burn rate
            (dom/div {:class "form-group"}
              (dom/label {:class "col-md-2 control-label"} burn-rate-label)
              (dom/label {:class "col-md-2 control-label"}
                (dom/span {:class burn-rate-classes} (str currency-symbol (thousands-separator burn-rate))))
              (dom/p {:class "help-block"} burn-rate-helper))

            ;; Runaway
            (dom/div {:class "form-group"}
              (dom/label {:class "col-md-2 control-label"} "Runway?")
              (dom/label {:class "col-md-2 control-label"} run-away)
              (dom/p {:class "help-block"} "Time until cash on hand is $0"))))

        ;; Comment textarea
        (dom/div {:class "row"}
          (dom/div {:class "col-md-1"})
          (dom/textarea {
            :class "col-md-10"
            :rows "5"
            :id "compensation-comment"
            :value (:comment finances)
            :on-change (fn [e]
                        (change-value finances (.. e -target -value) :comment)
                        (save-values "save-report"))
            :placeholder "Comments: explain any recent significant changes in costs or revenue, provide guidance on revenue and profitablity expectations"})
          (dom/div {:class "col-md-1"}))))))

(defcomponent readonly-finances [data owner]
  (render [_]
    (let [finances (:finances data)
          cash (:cash finances)
          revenue (:revenue finances)
          costs (:costs finances)
          currency (:currency data)
          burn-rate (- revenue costs)
          burn-rate-label (if (> burn-rate 0) "Growth rate: " "Burn rate: ")
          burn-rate-classes (str "num " (if (> burn-rate 0) "green" "red"))
          profitable (if (> burn-rate 0) "Yes" "No")
          run-away (if (<= burn-rate 0) (quot cash burn-rate) "N/A")
          currency-symbol (get-symbols-for-currency-code currency)]
      (r/well {:class "report-list finances clearfix"}
        (dom/div {:class "report-list-left"}
          (when (not (= cash nil))
            (dom/div
              (om/build report-line {
                :number cash
                :prefix currency-symbol
                :label "cash on hand"
                :pluralize false})))
          (when (not (= revenue nil))
            (dom/div
              (om/build report-line {
                :number revenue
                :prefix currency-symbol
                :label "revenue this month"
                :pluralize false})))
          (when (not (= costs nil))
            (dom/div
              (om/build report-line {
                :number costs
                :prefix currency-symbol
                :label "costs this month"
                :pluralize false})))
          (dom/div
            (dom/span {:class "label"} (str "Profitable this month? " profitable)))
          (dom/div
            (dom/span {:class "label"} burn-rate-label)
            (dom/span {:class "label"} currency-symbol)
            (dom/span {:class burn-rate-classes} (thousands-separator (abs burn-rate))))
          (dom/div
            (dom/span {:class "label"} "Runaway: " (if (<= burn-rate 0) (str (abs run-away) " months") "N/A")))
          (om/build comment-component {:cursor finances :key :comment :disabled true}))))))
