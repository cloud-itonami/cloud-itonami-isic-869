(ns transportops.operation-test
  "Integration tests for `transportops.operation/build` -- builds the
  REAL compiled `langgraph.graph` StateGraph and runs it end-to-end via
  `langgraph.graph/run*` through commit / hard-hold / phase-hold /
  escalate-approve / escalate-reject routes. This namespace did not
  exist before: `operation/execute-proposal` was a plain
  `(-> state intake-node advise-node govern-node decide-node
  commit-node)` threading pipeline that never touched
  `kotoba-lang/langgraph` at all (its own namespace docstring falsely
  called it \"the langgraph-clj StateGraph\"). These tests prove the
  compiled graph is real and that the audit ledger
  (`transportops.store/append-ledger!`) is genuinely wired into the
  `:commit`/`:hold` nodes, and that `store/commit-record!` still fires
  on every commit exactly as it did in the pre-graph pipeline."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [transportops.operation :as operation]
            [transportops.store :as store]))

(defn- exec [actor tid request phase-num]
  (g/run* actor {:request request :phase-num phase-num} {:thread-id tid}))

(deftest commit-path-clean-proposal
  (testing "a clean, phase-3, high-confidence transport-scheduling request
            commits through the real compiled graph, appends to the
            audit ledger, AND to the coordination-log via
            store/commit-record!"
    (let [s (store/seed-db)
          actor (operation/build s)
          result (exec actor "t-commit"
                       {:op :schedule-transport :vehicle-id "van-001"}
                       3)
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :commit (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :committed (:t (first ledger))))
        (is (= :schedule-transport (:op (first ledger))))
        (is (= "van-001" (:subject (first ledger)))))
      (is (= 1 (count (store/coordination-log s)))
          "store/commit-record! (coordination-log write) is preserved on the graph's :commit node"))))

(deftest hard-hold-path-unregistered-vehicle
  (testing "an unregistered vehicle is a HARD, permanent governor
            violation -- the real graph routes straight to :hold (no
            interrupt, no human-approval detour) and durably records
            the hold fact"
    (let [s (store/seed-db)
          actor (operation/build s)
          result (exec actor "t-hold"
                       {:op :schedule-transport :vehicle-id "unknown-van"}
                       3)
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (= :governor-violation (:reason (first ledger))))
        (is (seq (:violations (first ledger)))))
      (is (empty? (store/coordination-log s))
          "a held proposal never reaches store/commit-record!"))))

(deftest hard-hold-path-unverified-vehicle
  (testing "a registered-but-NOT-verified vehicle is also a HARD
            violation -- ground truth is re-derived from the vehicle's
            own store record, never trusted from the proposal"
    (let [s (store/seed-db)
          actor (operation/build s)
          result (exec actor "t-hold-unverified"
                       {:op :schedule-transport :vehicle-id "van-003"}
                       3)]
      (is (= :hold (:decision (:state result))))
      (is (some #{:vehicle-unverified}
                (map :code (:violations (first (store/ledger s)))))))))

(deftest phase-hold-path-clean-but-not-eligible
  (testing "a clean, non-escalating proposal that isn't in the current
            phase's auto-commit set is held (not committed, not
            escalated) -- and STILL durably audited, distinguished from
            a governor violation by :reason"
    (let [s (store/seed-db)
          actor (operation/build s)
          result (exec actor "t-phase-hold"
                       {:op :coordinate-supply-request :vehicle-id "van-001"}
                       0)
          state (:state result)]
      (is (= :hold (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (= :not-in-phase-auto-set (:reason (first ledger))))
        (is (empty? (:violations (first ledger))))))))

(deftest escalate-then-approve-commits
  (testing ":flag-safety-concern ALWAYS escalates -- the real graph
            GENUINELY interrupts (checkpointed) at :request-approval; a
            human dispatcher approve! resumes the SAME compiled graph
            and commits via the graph's own :request-approval -> :commit
            edge, durably appending to the ledger AND the
            coordination-log"
    (let [s (store/seed-db)
          actor (operation/build s)
          held (exec actor "t-escalate"
                     {:op :flag-safety-concern :vehicle-id "van-001"
                      :description "Brake warning light"}
                     3)]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger s)) "not yet committed -- awaiting human sign-off")
      (let [approved (g/run* actor {:approval {:status :approved :by "dispatcher-01"}}
                             {:thread-id "t-escalate" :resume? true})
            approved-state (:state approved)]
        (is (= :done (:status approved)))
        (is (= :commit (:decision approved-state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :flag-safety-concern (:op (first ledger))))
          (is (= "dispatcher-01" (:approved-by (first ledger)))))
        (is (= 1 (count (store/coordination-log s))))))))

(deftest escalate-then-reject-holds
  (testing "a human dispatcher rejecting an escalated request routes to
            :hold via the :request-approval node's own decision, and
            durably records the rejection -- not a hand-rolled parallel
            path"
    (let [s (store/seed-db)
          actor (operation/build s)
          _held (exec actor "t-reject"
                      {:op :flag-safety-concern :vehicle-id "van-001"
                       :description "Tire pressure warning"}
                      3)
          rejected (g/run* actor {:approval {:status :rejected :by "dispatcher-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:decision rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))))))
