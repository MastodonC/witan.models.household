(ns witan.models.household-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [witan.models.household :refer :all]
            [witan.models.model :as m]
            [witan.workspace-api.protocols :as p]
            [witan.workspace-executor.core :as wex]))

;; Building the workspace
(def test-inputs {:input-resident-popn :resident-popn
                  :input-institutional-popn :institutional-popn
                  :input-household-rates :household-rates
                  :input-vacancy-rates :vacancy-rates
                  :input-second-homes-rates :second-homes-rates})

(defn read-inputs [file schema]
  {})

(defn fix-input
  [input]
  (assoc-in input [:witan/params] {:src ""
                                   :key (get test-inputs (:witan/name input))
                                   :fn read-inputs}))

;; Testing the model and the workspace
(deftest household-model-test
  (let [library (m/model-library)
        funs    (p/available-fns library)
        model   (first (p/available-models library)) ;; Only one model defined so far
        {:keys [catalog metadata workflow]} model]
    (testing "A model is created correctly"
      (is workflow)
      (is catalog)
      (is metadata))
    (testing "The catalog entries match funtions in the model-library"
      (for [{:keys [witan/name witan/fn witan/version witan/params]} catalog
            :let [fnc (some #(when (and (= fn (:witan/name %))
                                        (= version (:witan/version %))) %) funs)]]
        (is fnc)))
    (testing "The catalog entries are existing functions"
      (let [library-fns (map #(:witan/impl %) funs)
            model-ns-list (map str (keys (ns-publics 'witan.models.household)))
            model-ns-fns (map #(keyword (format "witan.models.household/%s" %)) model-ns-list)]
        (is (= (set library-fns)
               (set model-ns-fns)))))))

(deftest household-workspace-test
  (let [fixed-catalog (mapv #(if (= (:witan/type %) :input) (fix-input %) %)
                            (:catalog m/household-model))
        workspace     {:workflow  (:workflow m/household-model)
                       :catalog   fixed-catalog
                       :contracts (p/available-fns (m/model-library))}
        workspace'    (s/with-fn-validation (wex/build! workspace))
        result        (first (wex/run!! workspace' {}))]
    (is result)
    (is (:total-households result))
    (is (:dwellings result))))
