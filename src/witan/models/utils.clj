(ns ^{:doc "Defines helper functions for the household model."}
 witan.models.utils
  (:require [witan.workspace-api.utils :as utils]
            [clojure.core.matrix.dataset :as ds]
            [witan.models.schemas :refer :all]
            [clojure.data.csv :as data-csv]
            [clojure.java.io :as io]
            [schema.coerce :as coerce]))

;; Helper functions for the household calcs
(def age-grps
  "Same age groups as the DCLG data
   (5 years age bands with an 85+ band)"
  {(range 0 5) :0_4 (range 5 10) :5_9
   (range 10 15) :10_14 (range 15 20) :15_19
   (range 20 25) :20_24 (range 25 30) :25_29
   (range 30 35) :30_34 (range 35 40) :35_39
   (range 40 45) :40_44 (range 45 50) :45_49
   (range 50 55) :50_54 (range 55 60) :55_59
   (range 60 65) :60_64 (range 65 70) :65_69
   (range 70 75) :70_74 (range 75 80) :75_79
   (range 80 85) :80_84 (range 85 125) :85&})

(defn get-age-grp [n]
  (first (keep (fn [grp] (if (some #(= % n) grp)
                           (get age-grps grp)))
               (keys age-grps))))

(defn year-column-exists?
  [dataset]
  (contains? (set (:column-names dataset)) :year))

(defn get-first-year
  [dataset]
  (utils/property-holds?  dataset year-column-exists?
                          (str "Dataset must have a year column"))
  (reduce min (ds/column dataset :year)))

(defn get-last-year
  [dataset]
  (utils/property-holds?  dataset year-column-exists?
                          (str "Dataset must have a year column"))
  (reduce max (ds/column dataset :year)))

(defn make-coll [x]
  (cond
    (seq? x) x
    (vector? x) x
    :else (vector x)))
