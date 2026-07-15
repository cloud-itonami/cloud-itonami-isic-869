(ns transportops.sim
  "Demo driver for the non-emergency transport logistics coordination actor.

  Exercises all scenarios: phase-1 approval-gated proposals, phase-3
  auto-commits for logistics ops, always-escalating safety flags, and
  all four HARD-hold scenarios (unregistered vehicle, unverified vehicle,
  non-:propose effect, scope-excluded clinical content)."
  (:require [transportops.store :as store]
            [transportops.operation :as operation]
            [transportops.governor :as governor]
            [clojure.pprint :refer [pprint]]))

(defn scenario-description [name]
  (println "\n" "=" "=" "=" "=" "=" "=" "=" "=" "=" "=")
  (println (str "Scenario: " name))
  (println "=" "=" "=" "=" "=" "=" "=" "=" "=" "="))

(defn -main [& _args]
  (println "Non-Emergency Transport Logistics Coordination Actor - Demo")

  ;; ===== Scenario 1: Happy path -- phase-0 (all held)
  (scenario-description "Phase 0: Happy path transport scheduling (held for approval)")
  (let [s (store/seed-db)
        request {:op :schedule-transport :vehicle-id "van-001" :pickup-time "10:00" :dropoff-time "11:00"}
        result (operation/execute-proposal request s {:current-phase 0})]
    (println "Proposal:" (:request result))
    (println "Advisor response:" (:op (:advisor-response result)))
    (println "Clean?" (get-in result [:governance-check :clean?]))
    (println "Decision:" (:decision result)))

  ;; ===== Scenario 2: Phase 3 auto-commit (supply coordination)
  (scenario-description "Phase 3: Auto-commit supply coordination")
  (let [s (store/seed-db)
        request {:op :coordinate-supply-request :vehicle-id "van-001"}
        result (operation/execute-proposal request s {:current-phase 3})]
    (println "Proposal:" (:request result))
    (println "Decision:" (:decision result))
    (println "Recorded?" (get-in result [:outcome :recorded?])))

  ;; ===== Scenario 3: Safety concern (always escalates)
  (scenario-description "Always-escalating: Safety concern flag")
  (let [s (store/seed-db)
        request {:op :flag-safety-concern :vehicle-id "van-001" :description "Engine overheating"}
        result (operation/execute-proposal request s {:current-phase 3})]
    (println "Proposal:" (:request result))
    (println "Decision:" (:decision result)))

  ;; ===== Scenario 4: HARD-block unregistered vehicle
  (scenario-description "HARD-block: Unregistered vehicle")
  (let [s (store/seed-db)
        request {:op :schedule-transport :vehicle-id "unknown-van" :pickup-time "10:00"}
        result (operation/execute-proposal request s {:current-phase 3})]
    (println "Proposal:" (:request result))
    (println "Violations:" (get-in result [:governance-check :violations]))
    (println "Decision:" (:decision result)))

  ;; ===== Scenario 5: HARD-block unverified vehicle
  (scenario-description "HARD-block: Unverified vehicle")
  (let [s (store/seed-db)
        request {:op :schedule-transport :vehicle-id "van-003" :pickup-time "10:00"}
        result (operation/execute-proposal request s {:current-phase 3})]
    (println "Proposal:" (:request result))
    (println "Violations:" (get-in result [:governance-check :violations]))
    (println "Decision:" (:decision result)))

  ;; ===== Scenario 6: HARD-block non-:propose effect
  (scenario-description "HARD-block: Non-:propose effect")
  (let [s (store/seed-db)
        request {:op :schedule-transport :vehicle-id "van-001"}
        ;; Simulate advisor returning non-:propose effect (shouldn't happen normally)
        advisor-resp (assoc (:advisor-response (operation/execute-proposal request s {}))
                           :effect :execute)
        violations (governor/check-violations advisor-resp s)]
    (println "Advisor effect:" (:effect advisor-resp))
    (println "Violations:" violations)))

  ;; ===== Scenario 7: HARD-block scope-excluded clinical content
  (scenario-description "HARD-block: Scope-excluded emergency dispatch content")
  (let [s (store/seed-db)
        ;; Simulate advisor drafting clinical content (shouldn't happen, but governor catches it)
        proposal {:op :schedule-transport
                 :vehicle-id "van-001"
                 :summary "Emergency ambulance dispatch for patient in crisis"
                 :confidence 0.9
                 :effect :propose}
        violations (governor/check-violations proposal s)]
    (println "Proposal summary:" (:summary proposal))
    (println "Violations detected:" (map :code violations))))

  (println "\n" "=" "=" "=" "=" "=" "=" "=" "=" "=" "=")
  (println "Demo completed successfully")
  (println "=" "=" "=" "=" "=" "=" "=" "=" "=" "="))
