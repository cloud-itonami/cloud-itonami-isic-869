(ns transportops.sim
  "Demo driver -- `clojure -M:run` / `clojure -M:dev:run`. Drives the
  REAL compiled `langgraph-clj` `StateGraph` (`transportops.operation/
  build`) end-to-end through a phase-3 auto-commit, an always-escalate
  safety-concern flag (dispatcher approves), a phase-0 hold, and all
  HARD-block scenarios (unregistered vehicle, unverified vehicle,
  non-:propose effect, scope-excluded content), then prints the
  resulting audit ledger. Mirrors `cerealops.sim`
  (cloud-itonami-isic-0111) / `vegops.sim` (cloud-itonami-isic-0113)."
  (:require [langgraph.graph :as g]
            [transportops.store :as store]
            [transportops.operation :as operation]
            [transportops.governor :as governor]))

(defn scenario [title]
  (println "\n" "=" "=" "=" "=" "=" "=" "=" "=" "=" "=")
  (println (str "Scenario: " title))
  (println "=" "=" "=" "=" "=" "=" "=" "=" "=" "="))

(defn- exec-op [actor tid request phase-num]
  (g/run* actor {:request request :phase-num phase-num} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "dispatcher-01"}}
          {:thread-id tid :resume? true}))

(defn demo
  "Run the compiled StateGraph through a phase-3 auto-commit path, an
  always-escalating safety-concern flag (approved by a human
  dispatcher), a phase-0 hold, and the three HARD-block scenarios;
  print each result and the final audit ledger."
  []
  (println "Non-Emergency Transport Logistics Coordination Actor - Demo")

  (scenario "Phase 3: Auto-commit transport scheduling")
  (let [s (store/seed-db)
        actor (operation/build s)
        result (exec-op actor "t1" {:op :schedule-transport :vehicle-id "van-001"} 3)]
    (println (:state result))
    (println "Decision:" (:decision (:state result))))

  (scenario "Phase 0: Clean proposal, still held (nothing auto-commits at phase 0)")
  (let [s (store/seed-db)
        actor (operation/build s)
        result (exec-op actor "t2" {:op :coordinate-supply-request :vehicle-id "van-001"} 0)]
    (println (:state result))
    (println "Decision:" (:decision (:state result))))

  (scenario "Always-escalating: safety concern (ALWAYS pauses at :request-approval)")
  (let [s (store/seed-db)
        actor (operation/build s)
        held (exec-op actor "t3"
                      {:op :flag-safety-concern :vehicle-id "van-001" :description "Engine warning"}
                      3)]
    (println "Status:" (:status held) "Frontier:" (:frontier held))
    (println "-- dispatcher approves --")
    (let [approved (approve! actor "t3")]
      (println (:state approved))
      (println "Decision:" (:decision (:state approved)))))

  (scenario "HARD-block: Unregistered vehicle")
  (let [s (store/seed-db)
        actor (operation/build s)
        result (exec-op actor "t4" {:op :schedule-transport :vehicle-id "unknown-van"} 3)]
    (println "Decision:" (:decision (:state result))
             "Audit:" (:audit (:state result))))

  (scenario "HARD-block: Unverified vehicle")
  (let [s (store/seed-db)
        actor (operation/build s)
        result (exec-op actor "t5" {:op :schedule-transport :vehicle-id "van-003"} 3)]
    (println "Decision:" (:decision (:state result))
             "Audit:" (:audit (:state result))))

  (scenario "HARD-block: Non-:propose effect (governor check, pre-graph)")
  (let [s (store/seed-db)
        proposal {:op :schedule-transport :vehicle-id "van-001" :effect :execute}
        violations (governor/check-violations proposal s)]
    (println "Violations:" (map :code violations)))

  (scenario "HARD-block: Scope-excluded emergency dispatch (governor check, pre-graph)")
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

(defn -main [& _args]
  (demo))

(comment
  (demo))
