# Governance

Maintained by the cloud-itonami org (gftdcojp). Decisions land as ADRs in the
superproject ledger. The actor pattern (advisor-LLM sealed behind an
independent governor, append-only audit ledger) is non-negotiable per
ADR-2607011000: the governor gates every action; direct emergency dispatch or
triage, any clinical assessment/medical-necessity/treatment/medication
decision, and any non-`:propose` effect are permanently blocked;
`:flag-safety-concern` (vehicle/route safety flags) always requires human
sign-off, regardless of confidence.
