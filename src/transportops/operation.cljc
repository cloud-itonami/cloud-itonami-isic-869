(ns transportops.operation
  "The langgraph-clj StateGraph for non-emergency transport logistics coordination.

  State machine:
  intake → advise → govern → decide → {commit | hold | approve}
                                          ↓
                                       ledger
                                       audit"
  (:require [transportops.store :as store]
            [transportops.advisor :as advisor]
            [transportops.governor :as governor]
            [transportops.phase :as phase]))

;; ============================================================================
;; State machine nodes
;; ============================================================================

(defn intake-node
  "Parse and validate the incoming request shape."
  [state]
  {:request (:request state)
   :advisor-response nil
   :governance-check nil
   :decision nil
   :outcome nil})

(defn advise-node
  "Query the advisor for recommendations."
  [state]
  (let [request (:request state)
        store (:store state)
        adv (advisor/mock-advisor)
        response (advisor/advise adv request store)]
    (assoc state :advisor-response response)))

(defn govern-node
  "Run governance checks (three HARD checks)."
  [state]
  (let [proposal (:advisor-response state)
        store (:store state)
        violations (governor/check-violations proposal store)]
    (assoc state :governance-check
           {:clean? (empty? violations)
            :violations violations})))

(defn decide-node
  "Determine the outcome: commit, hold, or escalate based on checks and phase."
  [state]
  (let [proposal (:advisor-response state)
        check (:governance-check state)
        store (:store state)
        phase-num (:current-phase state 0)
        should-escalate (governor/should-escalate? proposal store)
        clean (:clean? check)
        can-auto-commit (and clean
                         (phase/may-auto-commit? (:op proposal) phase-num))]
    (assoc state :decision
           (if (not clean)
             :hard-violation
             (if should-escalate
               :escalate
               (if can-auto-commit
                 :auto-commit
                 :hold))))))

(defn commit-node
  "Execute the proposal: record it to the store and ledger."
  [state]
  (let [proposal (:advisor-response state)
        decision (:decision state)
        store (:store state)]
    (when (= :auto-commit decision)
      (store/commit-record! store proposal)
      (store/append-ledger! store
                           {:timestamp (java.util.Date.)
                            :op (:op proposal)
                            :decision :auto-commit
                            :proposal proposal}))
    (assoc state :outcome
           {:decision decision
            :proposal proposal
            :recorded? (= :auto-commit decision)})))

;; ============================================================================
;; State machine execution
;; ============================================================================

(defn execute-proposal
  "Execute a transport coordination proposal through the full state machine.
  Returns the final state with :outcome and :decision fields."
  [request store {:keys [current-phase] :or {current-phase 0}}]
  (let [state {:request request
               :store store
               :current-phase current-phase}
        state (intake-node state)
        state (advise-node state)
        state (govern-node state)
        state (decide-node state)
        state (commit-node state)]
    state))
