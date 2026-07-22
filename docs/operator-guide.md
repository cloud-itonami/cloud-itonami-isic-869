# Operator Guide: Non-Emergency Transport Logistics Coordinator

## Overview

The Non-Emergency Transport Logistics Coordinator is a back-office
dispatch-support actor that:

1. **Proposes transport coordination actions** — scheduling, vehicle
   availability, supply, and staffing proposals (`transportops.advisor`)
2. **Censors every proposal** through an independent `TransportGovernor`
   (`transportops.governor`) before it can commit or even reach a human
   for sign-off
3. **Always escalates safety concerns** — `:flag-safety-concern` reaches a
   human dispatcher at every rollout phase, no exceptions
4. **Maintains transparency** — an append-only audit ledger traces every
   commit/hold/approval-rejected decision

The actor is **not** a decision-maker for anything outside non-emergency
transport logistics, and it has **zero** emergency-dispatch, triage, or
clinical authority. It **proposes** actions and escalates when human input
is needed.

## Operating the Actor

### Prerequisites

1. **Vehicle registration** — the target vehicle must be registered AND
   independently verified in the store (`transportops.store/vehicle`)
   before any proposal targeting it can commit or even escalate
2. **Clear request type** — specify what you're doing:
   - `:schedule-transport` — pickup/dropoff logistics for non-emergency
     transport
   - `:coordinate-vehicle-availability` — administrative vehicle tracking
   - `:coordinate-supply-request` — non-clinical dispatch/vehicle supplies
   - `:schedule-staff-shift-proposal` — administrative roster proposal
   - `:flag-safety-concern` — mechanical/route safety issue (ALWAYS
     escalates)

### Workflow

1. **Submit a request into the compiled StateGraph**
   ```clojure
   (require '[langgraph.graph :as g]
            '[transportops.operation :as operation]
            '[transportops.store :as store])

   (def s (store/seed-db))
   (def actor (operation/build s))

   (g/run* actor
     {:request {:op :schedule-transport :vehicle-id "van-001"
                :pickup-time "10:00" :pickup-location "Clinic A"
                :dropoff-time "11:00" :dropoff-location "Hospital B"}
      :phase-num 3}
     {:thread-id "dispatch-req-1"})
   ```

2. **The actor processes it** — a compiled `langgraph-clj` `StateGraph`
   (`transportops.operation/build`, run via `langgraph.graph/run*`):
   - `:intake` — request enters the graph
   - `:advise` — `TransportAdvisor` proposes an action (`transportops.advisor`)
   - `:govern` — `TransportGovernor` runs its three HARD checks
     (vehicle unverified / effect not `:propose` / scope exclusion) and the
     escalation gate (`transportops.governor`)
   - `:decide` — the rollout-phase gate applies on top of the Governor's
     verdict (`transportops.phase`)
   - `:request-approval` — reached only on escalation; the graph is
     checkpointed and **paused** here (`interrupt-before`) until a human
     dispatcher resumes it
   - `:commit` / `:hold` — terminal nodes; every commit/hold/
     approval-rejected decision fact is appended to the Store's audit
     ledger (`transportops.store/append-ledger!`), and every commit also
     records to `store/coordination-log` via `store/commit-record!`

3. **Outcomes** (`:decision` on the graph's returned state)
   - **`:commit`** — proposal committed (`:t :committed` fact lands in the
     ledger, plus a `store/coordination-log` entry)
   - **`:escalate`** (in-flight only — the graph pauses, it never settles
     here) — the graph is checkpointed and paused at `:request-approval`
     pending human decision (audit fact `:t :approval-requested`); resume
     with:
     ```clojure
     (g/run* actor {:approval {:status :approved :by "dispatcher-01"}}
             {:thread-id "dispatch-req-1" :resume? true})
     ```
   - **`:hold`** — either a HARD governor violation (`:t :governor-hold`,
     `:reason :governor-violation`, cites `:violations`), a clean proposal
     that simply isn't in the current phase's auto-commit set
     (`:reason :not-in-phase-auto-set`), or a dispatcher rejection
     (`:t :approval-rejected`) — all three land in the ledger with
     `:disposition :hold`

### Escalation Scenarios

**Automatic escalation (always human sign-off, every phase):**
- `:flag-safety-concern` — any mechanical/route safety issue, regardless of
  confidence
- Any proposal below the Governor's confidence floor (0.6)

**Hard blocks (permanent, no override, never even reach a human):**
- Target vehicle not registered, or registered but not independently
  `:verified?` in the store
- Any proposal whose `:effect` is not `:propose`
- Any proposal touching emergency dispatch/triage/medical-necessity/
  clinical-decision/patient-care/clinical-procedure/vital-signs/
  clinical-authority content (scanned across op/summary/rationale/cites/
  value — see `transportops.governor/scope-excluded-terms`)

### Resuming Escalated Operations

`transportops.operation/build` compiles a real `langgraph-clj` `StateGraph`
(`interrupt-before #{:request-approval}`, checkpoint-based resume). An
escalated run has been checkpointed and **paused** at `:request-approval`:
no further node runs until a human dispatcher resumes the SAME thread:

```clojure
;; kick off the request -- may pause at :request-approval
(g/run* actor {:request request :phase-num 3} {:thread-id tid})

;; ... human dispatcher review happens out of band ...

;; resume with a decision -- the graph continues from the checkpoint
(g/run* actor {:approval {:status :approved :by dispatcher-id}}
        {:thread-id tid :resume? true})
;; or, to reject:
(g/run* actor {:approval {:status :rejected :by dispatcher-id}}
        {:thread-id tid :resume? true})
```

`operation/build`'s default `:checkpointer` is an in-memory
`langgraph.checkpoint/mem-checkpointer` (per-process only); production
deployments should pass a persistent checkpointer so a paused request
survives a process restart.

## Audit & Transparency

Every graph run accumulates an `:audit` vector (approval-requested/
approval-granted facts, and — at the terminal nodes — the disposition
fact). The `:commit` and `:hold` terminal nodes append the resulting
decision fact to the Store's append-only ledger
(`transportops.store/append-ledger!`) themselves; `(store/ledger s)` is
always the authoritative, immutable record of every commit/hold/
approval-rejected decision.

- Every hold cites the specific Governor violation(s), or the phase-gate
  reason, in `:violations`/`:reason`
- Every escalation cites its `:reason` (`:always-escalate` for
  `:flag-safety-concern`, `:low-confidence` otherwise)
- Every committed fact carries `:proposal` — the full advisor proposal, so
  a vehicle's/dispatch-office's full coordination history is always a
  query over `(store/ledger s)`

## Integration

The actor provides a standard protocol (`transportops.store/Store`) for
backend integration:

- **Vehicle lookup** — `(store/vehicle s vehicle-id)`
- **Ledger read** — `(store/ledger s)`
- **Ledger append** — `(store/append-ledger! s fact)` (called by the
  compiled graph's `:commit`/`:hold` nodes; not normally called directly)
- **Coordination-log read/write** — `(store/coordination-log s)` /
  `(store/commit-record! s record)` (called by the compiled graph's
  `:commit` node)

The reference implementation is `MemStore` (in-memory, default,
`transportops.store`); a Datomic/kotoba-server-backed `Store` is the
documented next integration seam, same as every sibling cloud-itonami
actor's store.

## Safety Guarantees

- **No emergency/clinical authority** — the Governor's scope-exclusion scan
  is a permanent, un-overridable block; this actor never touches
  emergency dispatch, triage, or any clinical decision
- **No suppressed safety concerns** — `:flag-safety-concern` cannot be
  hidden, delayed, or auto-committed at any phase
- **No unlogged coordination** — every commit/hold/approval-rejected
  decision is durably recorded in the audit ledger
- **No direct actuation** — every proposal's `:effect` must be `:propose`;
  the Governor hard-blocks anything else

The actor is safe because:
1. It never decides for a human dispatcher — it proposes
2. It always escalates safety concerns
3. It permanently refuses clinical/emergency territory
4. Every action is auditable
