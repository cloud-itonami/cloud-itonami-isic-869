# Contributing

**Maturity: `:implemented`** — `src/transportops/` implements the reference
TransportAdvisor / TransportGovernor actor, composed by
`transportops.operation/build` into a real compiled `langgraph-clj`
`StateGraph` (`intake -> advise -> govern -> decide -+-> commit /
request-approval -> commit / hold`) with `interrupt-before
#{:request-approval}` and checkpoint-based human-in-the-loop resume.
Contributions that extend coverage are welcome: a Datomic/kotoba-server
`Store` backend, a real LLM `Advisor` implementation, additional Governor
rules (e.g. broader scope-exclusion coverage), and a persistent
(non-in-memory) `Checkpointer` for production deployments. Open an issue or
PR. License: AGPL-3.0-or-later.
