# cloud-itonami-isic-869

**ISIC-869: Other Human Health Activities — Non-Emergency Transport Logistics Coordination**

A langgraph-clj StateGraph actor for non-emergency patient transport and health-support logistics coordination. This is a **transportation/logistics coordination actor only** — it has no emergency dispatch authority, triage authority, or clinical decision-making power whatsoever.

## Scope

This actor coordinates the logistics and administration of non-emergency health-support transport operations:
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

These boundaries are enforced by three HARD, permanent, un-overridable governor checks:
1. **Transport-resource unverified** — target vehicle/resource must exist AND be registered/verified in the store
2. **Effect not `:propose`** — every proposal's `:effect` must be `:propose` (never direct actuation)
3. **Scope exclusion** — any proposal touching emergency dispatch/triage/clinical/patient-care content is HARD-blocked via substring scanning

## Module shape

- `transportops.store` — MemStore (SSoT, append-only audit ledger)
- `transportops.advisor` — TransportAdvisor (deterministic mock, real-LLM seam)
- `transportops.governor` — TransportGovernor (independent compliance censor)
- `transportops.phase` — Phase 0→3 rollout control
- `transportops.operation` — langgraph-clj StateGraph (intake → advise → govern → decide → commit | hold | approval)
- `transportops.sim` — demo driver

## Testing

Run tests:
```bash
clojure -M:test
```

Run linter:
```bash
clojure -M:lint
```

Run demo:
```bash
clojure -M:run
```

## Development

Use the `:dev` alias to override dependencies with local checkouts:
```bash
clojure -M:dev:test
clojure -M:dev:run
```

## License

AGPL-3.0-or-later
