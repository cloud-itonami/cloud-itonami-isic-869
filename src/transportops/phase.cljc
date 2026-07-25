(ns transportops.phase
  "Phase 0→3 rollout control for the non-emergency transport logistics actor.

  Phase 0: Intake-only (all proposals held for human approval)
  Phase 1: Low-risk auto-commit (supply & staff proposals auto-commit, others held)
  Phase 2: Medium-risk auto-commit (vehicle coordination auto-commits, others held)
  Phase 3: High-risk auto-commit (transport scheduling auto-commits, safety always held)

  :flag-safety-concern ALWAYS escalates and NEVER auto-commits at any phase
  (two-layer enforcement: governor + phase gate).")

(defn phase-config
  "The phase rollout table. Each phase specifies which ops may auto-commit
   (members of :auto set). An op outside :auto is held for human approval."
  [phase-num]
  (case phase-num
    0 {:auto #{}}                                    ;; Phase 0: all held
    1 {:auto #{:coordinate-supply-request
               :schedule-staff-shift-proposal}}      ;; Phase 1: low-risk
    2 {:auto #{:coordinate-supply-request
               :schedule-staff-shift-proposal
               :coordinate-vehicle-availability}}    ;; Phase 2: medium-risk
    3 {:auto #{:coordinate-supply-request
               :schedule-staff-shift-proposal
               :coordinate-vehicle-availability
               :schedule-transport}}                 ;; Phase 3: high-risk
    ;; Unknown phase: conservative default (all held)
    {:auto #{}}))

(defn may-auto-commit?
  "True if the given op may auto-commit in the given phase (after governor
  clears it). :flag-safety-concern is NEVER auto-commit, regardless of phase."
  [op phase-num]
  (if (= :flag-safety-concern op)
    false
    (let [cfg (phase-config phase-num)]
      (contains? (:auto cfg) op))))

(defn allowed-ops-for-phase
  "All ops allowed to be proposed at this phase. For transport, this is
   always the closed allowlist regardless of phase -- phase only controls
   whether they auto-commit or hold."
  [phase-num]
  #{:schedule-transport :coordinate-vehicle-availability :coordinate-supply-request
    :schedule-staff-shift-proposal :flag-safety-concern})
