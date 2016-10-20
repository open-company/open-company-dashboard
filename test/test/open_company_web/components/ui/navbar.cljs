(ns test.open-company-web.components.ui.navbar
    (:require [cljs.test :refer-macros [deftest async testing is are use-fixtures]]
              [cljs-react-test.simulate :as sim]
              [cljs-react-test.utils :as tu]
              [om.core :as om :include-macros true]
              [dommy.core :as dommy :refer-macros (sel1)]
              [open-company-web.components.ui.navbar :refer (navbar)]
              [om.dom :as dom :include-macros true]
              [open-company-web.router :as router]))

(enable-console-print!)

; dynamic mount point for components
(def ^:dynamic c)

(def company-symbol "OPEN")

(def test-atom {
  :card-width 500
  :columns-num 1
  :OPEN {
    :company-data {
      :slug company-symbol
      :links [
        {:rel "report"
         :href (str "/companies/" company-symbol "/reports/2015/Q1")}]}}})

(deftest test-navbar-component
  (testing "Navbar component"
    (router/set-route! ["companies" company-symbol] {:slug company-symbol})
    (let [c (tu/new-container!)
          app-state (atom test-atom)
          _ (om/root navbar app-state {:target c})
          navbar-node (sel1 c [:nav.oc-navbar])]
              (is (not (nil? navbar-node)))
              (tu/unmount! c))))