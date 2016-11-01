(ns witan.models.acceptance.workspace-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [witan.models.household :refer :all]
            [witan.models.model :as m]
            [witan.workspace-api.protocols :as p]
            [witan.workspace-executor.core :as wex]
            [clojure.core.matrix.dataset :as ds]
            [clojure.data.csv :as data-csv]
            [witan.datasets :as wds]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [witan.models.utils :as u]))

;; Testing the model can be run by the workspace executor

;; Helpers
;; (defn- load-csv
;;   "Loads csv file with each row as a vector.
;;    Stored in map separating column-names from data"
;;   ([filename]
;;    (let [file (io/file filename)]
;;      (when (.exists (io/as-file file))
;;        (let [parsed-csv (data-csv/read-csv (slurp file))
;;              parsed-data (rest parsed-csv)
;;              headers (first parsed-csv)]
;;          {:column-names headers
;;           :columns (vec parsed-data)})))))

;; (defn csv-to-dataset
;;   "Takes in a file path and a schema. Creates a dataset with the file
;;    data after coercing it using the schema."
;;   [filepath schema]
;;   (-> (load-csv filepath)
;;       :columns
;;       (map (coerce/coercer (make-row-schema col-schema)
;;                            coerce/string-coercion-matcher))))

(def test-data
  (edn/read-string
   (slurp (io/file "data/testing_config.edn"))))

(defn read-inputs [input _ schema]
  (let [key (:witan/name input)
        filepath (get test-data key)
        data (u/load-dataset key filepath)]
    (get data key)))

(defn add-input-params
  [input]
  (assoc-in input [:witan/params :fn] (partial read-inputs input)))

(defn- fp-equals? [x y ε] (< (Math/abs (- x y)) ε))

;; Test
(deftest household-workspace-test
  (testing "The model is run on the workspace and returns the outputs expected"
    (let [fixed-catalog (mapv #(if (= (:witan/type %) :input)
                                 (add-input-params %) %)
                              (:catalog m/household-model))
          workspace     {:workflow  (:workflow m/household-model)
                         :catalog   fixed-catalog
                         :contracts (p/available-fns (m/model-library))}
          workspace'    (s/with-fn-validation (wex/build! workspace))
          result        (apply merge (wex/run!! workspace' {}))

          gla-total-hh (wds/select-from-ds
                        (read-inputs
                         {:witan/name :gla-total-households} [] [])
                        {:year {:lt 2040}})
          total-hh (:total-households result)

          joined-ds (wds/join total-hh
                              (ds/rename-columns gla-total-hh {:households
                                                               :gla-households})
                              [:gss-code :year])

          difference (wds/add-derived-column joined-ds :difference
                                             [:households
                                              :gla-households] -)]

      (println difference)

      (is (:households result))
      (is (:dwellings result))

      (is (= (:shape gla-total-hh) (:shape total-hh)))
      (is (= (:column-names gla-total-hh) (:column-names total-hh)))

      (is (every? #(fp-equals? (wds/subset-ds joined-ds :rows % :cols :households)
                               (wds/subset-ds joined-ds :rows % :cols :gla-households)
                               300)
                  (range (first (:shape joined-ds))))))))
