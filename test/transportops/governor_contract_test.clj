(ns transportops.governor-contract-test
  "Contract tests for Governor protocol implementation and violation codes."
  (:require [clojure.test :refer [deftest is testing]]
            [transportops.store :as store]
            [transportops.governor :as governor]))

(deftest governor-contract-implementation
  (testing "Governor implements the Governor protocol"
    (let [gov (governor/->TransportGovernor)
          s (store/seed-db)
          proposal {:op :schedule-transport :vehicle-id "van-001" :effect :propose}]
      (is (not (nil? (governor/violations gov proposal s)))))))

(deftest violation-codes-structure
  (testing "Violations have :code and :message fields"
    (let [s (store/seed-db)
          bad-proposal {:op :schedule-transport :vehicle-id "unknown-van" :effect :propose}]
      (let [violations (governor/check-violations bad-proposal s)]
        (is (seq violations))
        (doseq [v violations]
          (is (contains? v :code))
          (is (contains? v :message))
          (is (keyword? (:code v)))
          (is (string? (:message v))))))))

(deftest violation-codes-all-three-hard-checks
  (testing "All three HARD-check codes are distinct"
    (let [s (store/seed-db)
          codes (set [
            ;; Vehicle unverified
            (first (map :code (governor/check-violations
                               {:op :schedule-transport :vehicle-id "unknown-van" :effect :propose} s)))
            ;; Effect not :propose
            (first (map :code (governor/check-violations
                               {:op :schedule-transport :vehicle-id "van-001" :effect :execute} s)))
            ;; Scope-excluded
            (first (map :code (governor/check-violations
                               {:op :schedule-transport :vehicle-id "van-001" :effect :propose
                                :summary "Emergency dispatch"} s)))])]
      (is (contains? codes :vehicle-unverified))
      (is (contains? codes :effect-not-propose))
      (is (contains? codes :scope-excluded)))))

(deftest violation-codes-case-insensitive-scope-check
  (testing "Scope exclusion scan is case-insensitive"
    (let [s (store/seed-db)]
      (let [lower {:op :schedule-transport :vehicle-id "van-001" :effect :propose
                  :summary "emergency ambulance dispatch"}
            upper {:op :schedule-transport :vehicle-id "van-001" :effect :propose
                  :summary "EMERGENCY AMBULANCE DISPATCH"}
            mixed {:op :schedule-transport :vehicle-id "van-001" :effect :propose
                  :summary "Emergency Ambulance Dispatch"}]
        (is (not (empty? (governor/check-violations lower s))))
        (is (not (empty? (governor/check-violations upper s))))
        (is (not (empty? (governor/check-violations mixed s))))))))
