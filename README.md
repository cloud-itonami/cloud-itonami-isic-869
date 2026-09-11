# cloud-itonami-isic-869

Open Occupation Blueprint for **ISIC Rev. 4 869**: Other Human Health Activities.

This repository implements a forkable OSS **non-emergency transport
logistics coordinator**: a dispatch-support actor manages non-emergency
transport scheduling, vehicle-availability coordination, non-clinical supply
coordination, and staff shift proposals under a governor-gated actor, so a
non-emergency medical transport (NEMT) dispatch office keeps its own
coordination records and maintains full transparency over decisions. This is
a **transportation/logistics coordination actor only** — it has no
emergency-dispatch authority, triage authority, or clinical
decision-making power whatsoever.

**Maturity: `:implemented`.** `src/transportops/` implements the
`TransportAdvisor` (`transportops.advisor`) and the independent
`TransportGovernor` (`transportops.governor`), composed by
`transportops.operation` following the itonami actor pattern
(ADR-2607011000): `intake -> advise -> govern -> decide -> commit |
request-approval -> commit | hold`, compiled to a **real** `langgraph-clj`
`StateGraph` (`langgraph.graph/state-graph` + `compile-graph`) with
`interrupt-before #{:request-approval}` and checkpoint-based
human-in-the-loop resume for escalated operations. This replaces a prior
`execute-proposal` that was a plain `(-> state intake-node advise-node
govern-node decide-node commit-node)` threading pipeline — despite this
namespace's own former docstring calling it "the langgraph-clj StateGraph,"
it never required `langgraph.graph` and never called
`state-graph`/`add-node`/`compile-graph` anywhere. Every commit/hold/
approval-rejected decision fact is appended to `transportops.store`'s
append-only audit ledger (`ledger`/`append-ledger!`) — this wiring was
already genuine in the pre-graph pipeline and is preserved unchanged, now
also covering `:hold` (not only auto-commits). 27 tests / 133 assertions
green (`kbb -M:dev:test`); the demo runner (`kbb -M:dev:run`) drives
the compiled graph end-to-end through a commit path, a phase-hold path, an
escalate→approve→commit path, and hard-hold paths (unregistered/unverified
vehicle), printing the resulting audit ledger.

## Scope

This actor coordinates the logistics and administration of non-emergency
health-support transport operations:
- **Non-emergency transport scheduling** — non-emergency patient/medical-goods transport logistics (pickup/dropoff time and location coordination)
- **Vehicle/equipment availability coordination** — administrative vehicle and equipment tracking (maintenance schedule, availability calendar, NOT clinical readiness)
- **Non-clinical supply coordination** — dispatch office supplies, non-clinical vehicle supplies (never medication or clinical equipment)
- **Staff shift proposals** — administrative roster proposals (never clinical staffing adequacy decisions)
- **Operational safety flagging** — vehicle/route safety concerns (mechanical issues, route hazards, NOT patient medical emergencies)

### Hard-blocked scope

This actor **NEVER** touches:
- Emergency dispatch or triage decisions
- Clinical assessment, medical necessity determination, or patient evaluation
- Treatment planning or care-plan modifications
- Medication administration, dosing, prescribing, or pharmaceutical handling
- Medical procedures or clinical interventions
- Patient safety decisions, vital signs monitoring, or clinical assessment
- Medical or clinical-authority overrides
- Any patient medical emergency or clinical decision

These boundaries are enforced by three HARD, permanent, un-overridable
governor checks (`transportops.governor`):
1. **Transport-resource unverified** — target vehicle/resource must exist AND be independently `:registered?`/`:verified?` in the store — never trusted from the proposal's own claim
2. **Effect not `:propose`** — every proposal's `:effect` must be `:propose` (never direct actuation)
3. **Scope exclusion** — any proposal touching emergency dispatch/triage/clinical/patient-care content is HARD-blocked via substring scanning across op/summary/rationale/cites/value

## Always-escalate operations (human sign-off, regardless of confidence)

- `:flag-safety-concern` — any vehicle mechanical/route safety concern → automatic escalation, at every rollout phase
- Any proposal with confidence below the Governor's floor (0.6)

## Operational requests (closed allowlist, all `:effect :propose`)

```text
:schedule-transport
  — pickup/dropoff time and location coordination for non-emergency transport
  — requires a registered + verified vehicle

:coordinate-vehicle-availability
  — administrative vehicle/equipment availability tracking

:coordinate-supply-request
  — non-clinical dispatch/vehicle supply coordination

:schedule-staff-shift-proposal
  — administrative staff roster proposal

:flag-safety-concern
  — vehicle mechanical issue or route hazard
  — ALWAYS escalates for human dispatcher review
```

## Core Contract

```text
coordination request (schedule / coordinate / flag)
        |
        v
TransportAdvisor -> TransportGovernor -> phase gate -> commit, or escalate for human sign-off
        |
        v
gated coordination actions + coordination-log + audit ledger
```

No automated operation can commit a coordination action the governor
refuses, suppress a coordination-log entry, or hide a safety concern
without governor approval and audit evidence.

## Module shape

- `transportops.store` — `Store` protocol: vehicle directory lookup, append-only audit ledger, coordination-log; `MemStore` (in-memory, default)
- `transportops.advisor` — `Advisor` protocol + `MockAdvisor` (deterministic, real-LLM seam via `llm-advisor`)
- `transportops.governor` — `TransportGovernor`: three HARD checks + escalation gate
- `transportops.phase` — 0→3 rollout phase gate
- `transportops.operation` — compiles the `langgraph-clj` `StateGraph`: intake → advise → govern → decide → commit | request-approval → commit | hold, with `interrupt-before` + checkpoint-based resume for escalated operations
- `transportops.sim` — demo runner (`kbb -M:dev:run`)

## Capability layer

Required capabilities (per `blueprint.edn`): `:identity`, `:forms`, `:dmn`,
`:bpmn`, `:audit-ledger`.

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Testing

```bash
kbb -M:dev:test   # run the test suite (langgraph/langchain resolved via local sibling checkouts)
kbb -M:lint       # clj-kondo, 0 errors
kbb -M:dev:run    # demo runner -- drives the compiled StateGraph end-to-end
```

`:dev` pins the transitive `langchain` dependency to the in-monorepo local
checkout (`../../kotoba-lang/langchain`) for offline workspace development;
a standalone fork should override `deps.edn`'s `:local/root` coordinate
with a git coordinate (see `deps.edn`'s own comment).

## License

AGPL-3.0-or-later
