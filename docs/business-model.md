# Business Model: Non-Emergency Transport Logistics Coordinator

## Classification

- Repository: `cloud-itonami-isic-869`
- ISIC Rev. 4: `869`
- Industry: Other human health activities
- Domain: `services/health-support-coordination`
- Social impact: operational-efficiency, accessibility, patient-transport-coordination,
  vehicle-management

## Customer

- Non-emergency medical transport (NEMT) dispatch offices
- Home-health and outpatient-clinic transport coordinators
- Adult day-care and long-term-care facility transport programs
- Community health-support and rural transit programs coordinating
  patient/medical-goods pickup and delivery

## Offer

- Non-emergency transport scheduling (pickup/dropoff logistics)
- Vehicle/equipment availability coordination (administrative tracking only)
- Non-clinical supply coordination for dispatch offices and vehicles
- Administrative staff shift roster proposals
- Operational (mechanical/route) safety-concern flagging with mandatory
  human sign-off
- Audit trail and transparency over every coordination decision

## Revenue

- SaaS subscription (per-vehicle or per-dispatch-seat pricing)
- Integration fees for clinics/facilities that route transport requests
  through the coordinator
- API access for dispatch-office and fleet-management partners
- Reporting/analytics add-ons (utilization, on-time performance)

## Trust Controls

- The actor never claims emergency-dispatch, triage, or clinical authority —
  a HARD, permanent, un-overridable Governor block (`transportops.governor`'s
  `scope-excluded-terms`) rejects any proposal that even touches that
  territory, regardless of who asked
- Every proposal's `:effect` must be `:propose` — the actor never claims
  direct actuation
- A vehicle/transport resource must be independently registered AND
  verified in the store before any proposal targeting it may commit or even
  escalate — never trusted from the proposal's own self-report
- `:flag-safety-concern` ALWAYS escalates to a human dispatcher, regardless
  of confidence or how clean the proposal otherwise is
- Every commit, hold, and approval-rejected decision is appended to an
  append-only audit ledger (never editable) via the compiled
  `langgraph-clj` `StateGraph`'s `:commit`/`:hold` terminal nodes

## What we do NOT do

- **Emergency dispatch or triage decisions** — routed to actual emergency
  dispatch/clinical systems, never this actor
- **Medical necessity determination or clinical assessment** — clinical
  authority is never claimed
- **Treatment planning or care-plan modification** — human clinical staff
  decide
- **Medication administration, dosing, prescribing, or any pharma
  handling** — permanently out of scope
- **Patient safety/vital-signs/clinical monitoring decisions** — permanently
  out of scope
- **Direct vehicle dispatch or actuation** — every proposal is a proposal,
  gated by the Governor and (for safety concerns) a human dispatcher

## Supported Operations

### Non-Emergency Transport Scheduling (`:schedule-transport`)
- Pickup/dropoff time and location coordination
- Non-emergency patient/medical-goods transport logistics only

### Vehicle Availability Coordination (`:coordinate-vehicle-availability`)
- Administrative vehicle/equipment tracking (maintenance windows,
  availability calendar) — never clinical readiness

### Non-Clinical Supply Coordination (`:coordinate-supply-request`)
- Dispatch office supplies, non-clinical vehicle supplies (fuel, cleaning
  supplies, safety equipment) — never medication or clinical equipment

### Staff Shift Proposals (`:schedule-staff-shift-proposal`)
- Administrative roster proposals — never clinical staffing-adequacy
  decisions

### Operational Safety Flagging (`:flag-safety-concern`)
- Vehicle mechanical issues, route hazards — NOT patient medical
  emergencies
- ALWAYS escalates to a human dispatcher via the compiled graph's
  `:request-approval` interrupt, at every rollout phase
