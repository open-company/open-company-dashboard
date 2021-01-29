(ns oc.web.utils.wrt
  (:require [cuerdas.core :as s]
            [oc.lib.time :as lib-time]
            [oc.web.urls :as oc-urls]
            [oc.web.local-settings :as ls]
            ;; [cljs-time.format :as format]
            ;; [cljs-time.core :as time]
            ))

(def column-separator ", ")
(def row-separator "\n")
(def empty-value "-")

(defn- csv-row [row]
  (let [values (map #(if (keyword? %) (name %) %) row)]
    (s/join column-separator values)))

(defn- csv-rows [rows]
  (let [rows-list (map csv-row rows)]
    (s/join row-separator rows-list)))

;; (defn- formatted-date-time
;;   ([] (formatted-date-time (time/now)))
;;   ([date-time]
;;    (let [date-format (format/formatter "MMM dd yyyy hh:mma")
;;          fixed-date-time (if (time/date? date-time)
;;                            date-time
;;                            (lib-time/from-iso date-time))]
;;      (format/unparse date-format fixed-date-time))))

(defn- read-date [row]
  (if (:read-at row)
    (lib-time/csv-date (:read-at row))
    empty-value))

(defn- clean-value [row k]
  (get row k empty-value))

(defn- name-from-user [{first-name :first-name last-name :last-name}]
  (cond (and (seq first-name) (seq last-name))
        (str first-name " " last-name)
        (seq first-name)
        first-name
        (seq last-name)
        last-name
        :else
        empty-value))

(defn- clean-user [row]
  (vec [(name-from-user row) (clean-value row :email) (read-date row)]))

(defn- post-href [entry-data]
  (str ls/web-server-domain (oc-urls/entry (:board-slug entry-data) (:uuid entry-data))))

(defn- csv-intro [org-name]
  (str "Analytics for " org-name " generated on " (lib-time/csv-date) "\n"))

(defn encoded-csv [org-data entry-data headers data]
  (let [header (when headers (s/join ", " headers))
        cleaned-data (map clean-user data)
        body (csv-rows cleaned-data)
        title (str "Title: " (:headline entry-data))
        published (str "Published on: " (lib-time/csv-date (:published-at entry-data)))
        post-link (str "Link: " (post-href entry-data))
        intro (csv-intro (:name org-data))
        csv-content (s/join "\n" [intro title published post-link "-" header body])]
    (str "data:text/csv;charset=utf-8," (js/encodeURIComponent csv-content))))

(defn csv-filename [entry-data]
  (str "post-" (:uuid entry-data) "-" (lib-time/to-iso (lib-time/utc-now)) ".csv"))