(ns transportops.store-contract-test
  "Contract tests for Store protocol implementation."
  (:require [clojure.test :refer [deftest is testing]]
            [transportops.store :as store]))

(deftest store-contract
  (testing "MemStore implements all Store protocol methods"
    (let [s (store/mem-store {"van-001" {:vehicle-id "van-001" :registered? true :verified? true}})]
      (is (not (nil? (store/vehicle s "van-001"))))
      (is (nil? (store/vehicle s "van-999")))
      (is (seq (store/all-vehicles s)))
      (is (vector? (store/ledger s)))
      (is (vector? (store/coordination-log s)))
      (is (not (nil? (store/append-ledger! s {:event :test}))))
      (is (not (nil? (store/commit-record! s {:proposal :test})))))))

(deftest demo-data-coverage
  (testing "Demo data contains both registered and unverified vehicles"
    (let [s (store/seed-db)]
      (is (= 3 (count (store/all-vehicles s))))
      (let [van-001 (store/vehicle s "van-001")
            van-003 (store/vehicle s "van-003")]
        (is (:verified? van-001))
        (is (not (:verified? van-003)))))))

(deftest vehicle-keying-by-string
  (testing "Vehicles are keyed by STRING, never keywords"
    (let [vehicles {"van-001" {:vehicle-id "van-001" :registered? true :verified? true}}
          s (store/mem-store vehicles)]
      (is (= "van-001" (:vehicle-id (store/vehicle s "van-001"))))
      (is (nil? (store/vehicle s :van-001))))))
