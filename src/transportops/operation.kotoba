(ns transportops.operation
  "OperationActor -- one non-emergency transport-logistics coordination
  request = one supervised actor run, expressed as a REAL compiled
  `langgraph-clj` `StateGraph` (`langgraph.graph/state-graph` +
  `compile-graph`). The advisor (TransportAdvisor) is sealed into a
  single node (`:advise`); its proposal is ALWAYS routed through the
  independent `TransportGovernor` (`:govern`) and the rollout phase gate
  (`:decide`) before anything commits to the SSoT.

  This replaces the previous `execute-proposal`, which was a plain
  `(-> state intake-node advise-node govern-node decide-node
  commit-node)` threading pipeline that never required
  `langgraph.graph` and never touched `state-graph`/`add-node`/
  `compile-graph` at all -- despite this namespace's own former
  docstring calling it \"the langgraph-clj StateGraph.\" That claim was
  false; this is the real thing.

  State machine:
  intake -> advise -> govern -> decide -+-> commit
                                         +-> request-approval -> commit
                                         +-> hold

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (`transportops.store/MemStore`, or any `Store` impl)
    - the Advisor  (mock today; `transportops.advisor/Advisor` is
                     already the injection point -- see its docstring)
    - the Phase    (0->3 rollout; passed per-request via `:phase-num`,
                     not frozen at `build` time)

  One graph run = one transport-logistics coordination request. No
  unbounded inner loop -- each run is auditable and checkpointed. Every
  commit/hold/approval-rejected decision fact lands in
  `transportops.store`'s append-only ledger
  (`store/append-ledger!`) -- this call was already genuinely wired
  (not dead code) in the pre-graph pipeline, and that wiring is
  preserved here, now reachable from both the `:commit` and `:hold`
  terminal nodes instead of only the auto-commit path.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` pauses the actor at the
  `:request-approval` node until a human dispatcher/operator resumes it
  with a decision. `:flag-safety-concern` ALWAYS reaches this node when
  the Governor is clean -- see `transportops.governor/always-escalate-ops`
  and `transportops.phase`'s independent agreement (never a member of
  any phase's `:auto` set either)."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [transportops.advisor :as advisor]
            [transportops.governor :as governor]
            [transportops.phase :as phase]
            [transportops.store :as store]))

;; ============================================================================
;; Audit-fact builders
;; ============================================================================

(defn- hold-fact
  "The audit fact written when a proposal is held -- either a permanent
  HARD governor block (`:violations` non-empty) or a clean proposal
  that simply isn't in the current phase's auto-commit set. `reason`
  distinguishes the two for the ledger without changing the terminal
  node they route to."
  [request proposal violations reason]
  {:t           :governor-hold
   :op          (:op request)
   :subject     (:vehicle-id proposal)
   :disposition :hold
   :violations  violations
   :reason      reason})

(defn- commit-fact
  "The audit fact written when a proposal commits. `:proposal` carries
  the full advisor proposal (transport-scheduling/coordination/supply/
  shift/safety-flag data) -- transportops has no separate stateful
  commit-record! entity beyond the vehicle directory, so the ledger
  fact itself plus `store/coordination-log` (via `commit-record!`) are
  the durable record of what happened."
  [request proposal approval]
  (cond-> {:t           :committed
           :op          (:op request)
           :subject     (:vehicle-id proposal)
           :disposition :commit
           :basis       (:cites proposal)
           :summary     (:summary proposal)
           :proposal    proposal}
    approval (assoc :approved-by (:by approval))))

;; ============================================================================
;; Compiled StateGraph
;; ============================================================================

(defn build
  "Compiles an OperationActor graph bound to `store`. opts:
    :advisor      -- a `transportops.advisor/Advisor` (default: mock-advisor)
    :checkpointer -- a `langgraph.checkpoint/Checkpointer`
                     (default: in-memory `cp/mem-checkpointer`)

  The compiled graph's input map: `{:request .. :phase-num ..}` (phase
  is per-request, not frozen at build time -- matches the old
  `execute-proposal`'s `current-phase` call-time argument)."
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (advisor/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request    {:default nil}
         :phase-num  {:default 0}
         :proposal   {:default nil}
         :violations {:default nil}
         :decision   {:default nil}
         :approval   {:default nil}
         :audit      {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      (g/add-node :advise
        (fn [{:keys [request]}]
          {:proposal (advisor/advise advisor request store)}))

      (g/add-node :govern
        (fn [{:keys [proposal]}]
          {:violations (governor/check-violations proposal store)}))

      (g/add-node :decide
        (fn [{:keys [request proposal violations phase-num]}]
          (let [clean?       (empty? violations)
                escalate?    (governor/should-escalate? proposal store)
                auto-commit? (and clean? (phase/may-auto-commit? (:op proposal) phase-num))]
            (cond
              ;; HARD governor violations are a permanent block -- NEVER
              ;; routed through human approval, straight to :hold.
              (not clean?)
              {:decision :hold
               :audit [(hold-fact request proposal violations :governor-violation)]}

              escalate?
              {:decision :escalate
               :audit [{:t          :approval-requested
                        :op         (:op request)
                        :subject    (:vehicle-id proposal)
                        :reason     (if (contains? governor/always-escalate-ops (:op proposal))
                                      :always-escalate :low-confidence)
                        :phase      phase-num
                        :confidence (:confidence proposal)}]}

              auto-commit?
              {:decision :commit}

              :else
              {:decision :hold
               :audit [(hold-fact request proposal violations :not-in-phase-auto-set)]}))))

      (g/add-node :request-approval
        (fn [{:keys [request proposal approval violations]}]
          (if (= :approved (:status approval))
            {:decision :commit
             :audit [{:t :approval-granted :op (:op request)
                      :subject (:vehicle-id proposal) :by (:by approval)}]}
            {:decision :hold
             :audit [(assoc (hold-fact request proposal violations :approver-rejected)
                            :t :approval-rejected)]})))

      (g/add-node :commit
        (fn [{:keys [request proposal approval]}]
          (store/commit-record! store proposal)
          (let [f (commit-fact request proposal approval)]
            (store/append-ledger! store f)
            {:audit [f]})))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store hf))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [decision]}]
          (case decision
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [decision]}]
          (if (= :commit decision) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
