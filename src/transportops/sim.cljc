(ns transportops.sim
  "Demo driver for the non-emergency transport logistics coordination actor.

  Exercises all scenarios: phase-1 approval-gated proposals, phase-3
  auto-commits for logistics ops, always-escalating safety flags, and
  all HARD-hold scenarios."
  (:require [transportops.store :as store]
            [transportops.operation :as operation]
            [transportops.governor :as governor]))

(defn scenario [title]
  (println "\n" "=" "=" "=" "=" "=" "=" "=" "=" "=" "=")
  (println (str "Scenario: " title))
  (println "=" "=" "=" "=" "=" "=" "=" "=" "=" "="))

(defn -main [& _args]
  (println "Non-Emergency Transport Logistics Coordination Actor - Demo")

  ;; Scenario 1: Happy path
  (scenario "Phase 0: Happy path (all held)")
  (let [s (store/seed-db)
        request {:op :schedule-transport :vehicle-id "van-001"}
        result (operation/execute-proposal request s {:current-phase 0})]
    (println "Decision:" (:decision result)))

  ;; Scenario 2: Phase 3 auto-commit
  (scenario "Phase 3: Auto-commit supply coordination")
  (let [s (store/seed-db)
        request {:op :coordinate-supply-request :vehicle-id "van-001"}
        result (operation/execute-proposal request s {:current-phase 3})]
    (println "Decision:" (:decision result)))

  ;; Scenario 3: Safety always escalates
  (scenario "Always-escalating: Safety concern")
  (let [s (store/seed-db)
        request {:op :flag-safety-concern :vehicle-id "van-001" :description "Engine warning"}
        result (operation/execute-proposal request s {:current-phase 3})]
    (println "Decision:" (:decision result)))

  ;; Scenario 4: Unregistered vehicle
  (scenario "HARD-block: Unregistered vehicle")
  (let [s (store/seed-db)
        request {:op :schedule-transport :vehicle-id "unknown-van"}
        result (operation/execute-proposal request s {:current-phase 3})]
    (println "Decision:" (:decision result)))

  ;; Scenario 5: Unverified vehicle
  (scenario "HARD-block: Unverified vehicle")
  (let [s (store/seed-db)
        request {:op :schedule-transport :vehicle-id "van-003"}
        result (operation/execute-proposal request s {:current-phase 3})]
    (println "Decision:" (:decision result)))

  ;; Scenario 6: Non-:propose effect
  (scenario "HARD-block: Non-:propose effect")
  (let [s (store/seed-db)
        request {:op :schedule-transport :vehicle-id "van-001"}
        result (operation/execute-proposal request s {:current-phase 3})
        proposal (assoc (:advisor-response result) :effect :execute)
        violations (governor/check-violations proposal s)]
    (println "Violations:" (map :code violations)))

  ;; Scenario 7: Scope-excluded content
  (scenario "HARD-block: Scope-excluded emergency dispatch")
  (let [s (store/seed-db)
        proposal {:op :schedule-transport
                 :vehicle-id "van-001"
                 :summary "Emergency ambulance dispatch for patient"
                 :effect :propose}
        violations (governor/check-violations proposal s)]
    (println "Violations:" (map :code violations)))

  (println "\n" "=" "=" "=" "=" "=" "=" "=" "=" "=" "=")
  (println "Demo completed successfully")
  (println "=" "=" "=" "=" "=" "=" "=" "=" "=" "="))
