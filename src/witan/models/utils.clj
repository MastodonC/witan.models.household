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

;; Helper functions for testing
(defn apply-row-schema
  [col-schema csv-data]
  (let [row-schema (make-row-schema col-schema)]
    (map (coerce/coercer row-schema coerce/string-coercion-matcher)
         (:columns csv-data))))

(defn apply-col-names-schema
  [col-schema csv-data]
  (let [col-names-schema (make-col-names-schema col-schema)]
    ((coerce/coercer col-names-schema coerce/string-coercion-matcher)
     (:column-names csv-data))))

(defmulti apply-record-coercion
  (fn [data-info csv-data]
    (:type  data-info)))

(defmethod apply-record-coercion :population
  [data-info csv-data]
  {:column-names (apply-col-names-schema PopulationProjections csv-data)
   :columns (vec (apply-row-schema PopulationProjections csv-data))})

(defmethod apply-record-coercion :dclg-household-popn
  [data-info csv-data]
  {:column-names (apply-col-names-schema DclgHouseholdPopulation csv-data)
   :columns (vec (apply-row-schema DclgHouseholdPopulation csv-data))})

(defmethod apply-record-coercion :dclg-institutional-popn
  [data-info csv-data]
  {:column-names (apply-col-names-schema DclgInstitutionalPopulation csv-data)
   :columns (vec (apply-row-schema DclgInstitutionalPopulation csv-data))})

(defmethod apply-record-coercion :dclg-household-representative-rates
  [data-info csv-data]
  {:column-names (apply-col-names-schema HouseholdRepresentativeRates csv-data)
   :columns (vec (apply-row-schema HouseholdRepresentativeRates csv-data))})

(defmethod apply-record-coercion :dclg-dwellings
  [data-info csv-data]
  {:column-names (apply-col-names-schema Dwellings csv-data)
   :columns (vec (apply-row-schema Dwellings csv-data))})

(defmethod apply-record-coercion :vacancy-dwellings
  [data-info csv-data]
  {:column-names (apply-col-names-schema VacancyDwellings csv-data)
   :columns (vec (apply-row-schema VacancyDwellings csv-data))})

(defmethod apply-record-coercion :gla-total-households
  [data-info csv-data]
  {:column-names (apply-col-names-schema TotalHouseholds csv-data)
   :columns (vec (apply-row-schema TotalHouseholds csv-data))})

(defn- load-csv
  "Loads csv file with each row as a vector.
   Stored in map separating column-names from data"
  ([filename]
   (let [file (io/file filename)]
     (when (.exists (io/as-file file))
       (let [parsed-csv (data-csv/read-csv (slurp file))
             parsed-data (rest parsed-csv)
             headers (first parsed-csv)]
         {:column-names headers
          :columns (vec parsed-data)})))))

(defn create-dataset-after-coercion
  [{:keys [column-names columns]}]
  (ds/dataset column-names columns))

(defn load-dataset
  "Input is a keyword and a filepath to csv file
   Output is map with keyword and core.matrix dataset"
  [keyname filepath]
  (->> (apply-record-coercion {:type keyname} (load-csv filepath))
       create-dataset-after-coercion
       (hash-map keyname)))
