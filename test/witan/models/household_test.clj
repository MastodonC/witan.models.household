(ns witan.models.household-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [witan.models.household :refer :all]
            [witan.models.model :as m]
            [witan.workspace-api.protocols :as p]
            [witan.workspace-executor.core :as wex]))

;; Building the workspace
(def test-inputs {:resident-popn ""
                  :institutional-popn ""
                  :household-rates ""
                  :vacancy-rates ""
                  :second-homes-rates ""})

(def test-params {})

(defn read-inputs [file schema]
  {})

(defn tasks [inputs params]
  { ;; Inputs
   :input-resident-popn {:var #'witan.models.household/get-resident-popn-1-0-0
                         :params {:src (:resident-popn inputs)
                                  :key :resident-popn
                                  :fn read-inputs}}
   :input-institutional-popn {:var #'witan.models.household/get-institutional-popn-1-0-0
                              :params {:src (:institutional-popn inputs)
                                       :key :institutional-popn
                                       :fn read-inputs}}
   :input-household-rates {:var #'witan.models.household/get-household-rates-1-0-0
                           :params {:src (:household-rates inputs)
                                    :key :household-rates
                                    :fn read-inputs}}
   :input-vacancy-rates {:var #'witan.models.household/get-vacancy-rates-1-0-0
                         :params {:src (:vacancy-rates inputs)
                                  :key :vacancy-rates
                                  :fn read-inputs}}
   :input-second-homes-rates {:var #'witan.models.household/get-second-homes-rates-1-0-0
                              :params {:src (:second-homes-rates inputs)
                                       :key :second-homes-rates
                                       :fn read-inputs}}
   ;; Calcs
   :calculate-household-popn {:var #'witan.models.household/calc-household-popn-1-0-0}
   :group-household-popn {:var #'witan.models.household/grp-household-popn-1-0-0}
   :calculate-households {:var #'witan.models.household/calc-households-1-0-0}
   :calculate-total-households {:var #'witan.models.household/calc-total-households-1-0-0}
   :calculate-occupancy-rate {:var #'witan.models.household/calc-occupancy-rate-1-0-0}
   :calculate-dwellings {:var #'witan.models.household/calc-dwellings-1-0-0}
   ;; Outputs
   :output-hh-and-dwellings {:var #'witan.models.household/output-results-1-0-0}})

(defn get-meta
  [v]
  (-> v :var meta :witan/metadata))

(defn make-contracts
  [task-coll]
  (distinct (mapv (fn [[k v]] (get-meta v)) task-coll)))

(defn make-catalog
  [task-coll]
  (mapv (fn [[k v]]
          (let [m (hash-map :witan/name k
                            :witan/version (:witan/version (get-meta v))
                            :witan/type (:witan/type (get-meta v))
                            :witan/fn (:witan/name (get-meta v)))]
            (if (:params v)
              (assoc m :witan/params (:params v))
              m))) task-coll))

(defn run-workspace
  [inputs params]
  (let [tasks (tasks inputs params)
        workspace {:workflow (:workflow m/household-model)
                   :catalog (make-catalog tasks)
                   :contracts (make-contracts tasks)}
        workspace'    (s/with-fn-validation (wex/build! workspace))
        result        (wex/run!! workspace' {})]
    (first result)))

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
      (doseq [{:keys [witan/name witan/fn witan/version witan/params]} catalog]
        (let [fnc (some #(when (and (= fn (:witan/name %))
                                    (= version (:witan/version %))) %) funs)]
          (is fnc))))
    (testing "The catalog entries are existing functions"
      (let [library-fns (map #(:witan/impl %) funs)
            model-ns-list (map str (keys (ns-publics 'witan.models.household)))
            model-ns-fns (map #(keyword (format "witan.models.household/%s" %)) model-ns-list)]
        (is (= (set library-fns)
               (set model-ns-fns)))))))

(deftest household-workspace-test
  (let [ws-result (run-workspace test-inputs test-params)]
    (is ws-result)
    (is (:total-households ws-result))
    (is (:dwellings ws-result))))
