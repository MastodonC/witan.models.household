(ns witan.models.acceptance.workspace-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [witan.models.household :refer :all]
            [witan.models.model :as m]
            [witan.workspace-api.protocols :as p]
            [witan.workspace-executor.core :as wex]
            [clojure.core.matrix.dataset :as ds]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

;; Testing the model can be run by the workspace executor

;; Helpers
(def test-data
  (edn/read-string
   (slurp (io/file "data/testing_data.edn"))))

(def test-inputs
  (:test-inputs test-data))

(def test-outputs
  (:test-outputs test-data))

(defn read-inputs [input _ schema]
  (let [data (get test-inputs (:witan/name input))
        data-set (ds/dataset data)]
    data-set))

(defn add-input-params
  [input]
  (assoc-in input [:witan/params :fn] (partial read-inputs input)))

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
          result        (apply merge (wex/run!! workspace' {}))]
      (is (:households result))
      (is (:total-households result))
      (is (:dwellings result)))))
