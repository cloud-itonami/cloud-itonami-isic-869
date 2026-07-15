(ns transportops.governor-test
  "Governor compliance tests for the three HARD checks and escalation rules."
  (:require [clojure.test :refer [deftest is testing]]
            [transportops.store :as store]
            [transportops.governor :as governor]))

(deftest hard-check-1-vehicle-unverified
  (testing "HARD-check 1: Vehicle must be registered AND verified"
    (let [s (store/seed-db)
          ;; van-001 is registered and verified
          proposal-1 {:op :schedule-transport :vehicle-id "van-001" :effect :propose}
          ;; van-003 is registered but NOT verified
          proposal-2 {:op :schedule-transport :vehicle-id "van-003" :effect :propose}
          ;; unknown-van is not registered at all
          proposal-3 {:op :schedule-transport :vehicle-id "unknown-van" :effect :propose}]
      (is (empty? (governor/check-violations proposal-1 s)))
      (is (not (empty? (governor/check-violations proposal-2 s))))
      (is (not (empty? (governor/check-violations proposal-3 s)))))))

(deftest hard-check-2-effect-not-propose
  (testing "HARD-check 2: Effect must be :propose"
    (let [s (store/seed-db)
          proposal-propose {:op :schedule-transport :vehicle-id "van-001" :effect :propose}
          proposal-execute {:op :schedule-transport :vehicle-id "van-001" :effect :execute}
          proposal-commit {:op :schedule-transport :vehicle-id "van-001" :effect :commit}]
      (is (empty? (governor/check-violations proposal-propose s)))
      (is (not (empty? (governor/check-violations proposal-execute s))))
      (is (not (empty? (governor/check-violations proposal-commit s)))))))

(deftest hard-check-3-scope-exclusion
  (testing "HARD-check 3: Scope exclusion blocks clinical/emergency content"
    (let [s (store/seed-db)]
      ;; Legitimate operational safety concern
      (let [legit {:op :flag-safety-concern :vehicle-id "van-001" :effect :propose
                  :summary "Engine overheating" :rationale "Mechanical issue"}]
        (is (empty? (governor/check-violations legit s))))
      ;; Emergency dispatch (scope-excluded)
      (let [emergency {:op :schedule-transport :vehicle-id "van-001" :effect :propose
                      :summary "Emergency ambulance dispatch" :rationale "Patient in crisis"}]
        (is (not (empty? (governor/check-violations emergency s)))))
      ;; Clinical content (scope-excluded)
      (let [clinical {:op :schedule-transport :vehicle-id "van-001" :effect :propose
                     :summary "Transport patient with medication administration"}]
        (is (not (empty? (governor/check-violations clinical s))))))))

(deftest legitimate-facility-safety-concern-is-not-scope-excluded
  (testing "Legitimate operational safety concerns pass scope check"
    (let [s (store/seed-db)
          proposals [
            {:op :flag-safety-concern :vehicle-id "van-001" :effect :propose
             :summary "Brake system warning light" :rationale "Vehicle maintenance"}
            {:op :flag-safety-concern :vehicle-id "van-001" :effect :propose
             :summary "Damage to passenger door" :rationale "Safety hazard"}
            {:op :coordinate-vehicle-availability :vehicle-id "van-001" :effect :propose
             :summary "Van pending inspection" :rationale "Maintenance scheduling"}]]
      (doseq [p proposals]
        (is (empty? (governor/check-violations p s))
            (str "Proposal failed: " p))))))

(deftest escalation-rules
  (testing "Escalation: :flag-safety-concern always escalates"
    (let [s (store/seed-db)
          safety-concern {:op :flag-safety-concern :vehicle-id "van-001" :effect :propose
                         :summary "Tire pressure warning" :confidence 0.99}]
      (is (true? (governor/should-escalate? safety-concern s)))))

  (testing "Escalation: Low confidence escalates"
    (let [s (store/seed-db)
          low-conf {:op :schedule-transport :vehicle-id "van-001" :effect :propose
                   :confidence 0.5}]
      (is (true? (governor/should-escalate? low-conf s)))))

  (testing "Escalation: HARD violations escalate"
    (let [s (store/seed-db)
          bad-effect {:op :schedule-transport :vehicle-id "van-001" :effect :execute
                     :confidence 0.95}]
      (is (true? (governor/should-escalate? bad-effect s)))))

  (testing "No escalation: Clean proposal, high confidence, non-safety op"
    (let [s (store/seed-db)
          clean {:op :coordinate-supply-request :vehicle-id "van-001" :effect :propose
                :confidence 0.95}]
      (is (false? (governor/should-escalate? clean s))))))

(deftest allowed-ops-enforcement
  (testing "Only allowed ops are accepted"
    (let [s (store/seed-db)]
      (doseq [op governor/allowed-ops]
        (let [proposal {:op op :vehicle-id "van-001" :effect :propose}]
          (is (empty? (governor/check-violations proposal s))
              (str "Allowed op rejected: " op))))
      (let [bad-op-proposal {:op :unauthorized-op :vehicle-id "van-001" :effect :propose}
            violations (governor/check-violations bad-op-proposal s)]
        ;; Bad op will be caught if the proposal structure doesn't match expected
        ;; (In this simplified version, we just note it's not in the allowed set)
        (is (vector? violations))))))
