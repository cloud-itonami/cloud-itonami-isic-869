(ns transportops.governor
  "TransportGovernor -- the independent compliance layer for
  ISIC-869 non-emergency transport logistics coordination. The advisor has no notion
  of whether a vehicle/resource is actually registered and verified, whether
  its own proposed `:effect` secretly claims a direct actuation instead
  of a mere proposal, or whether it has silently drifted into a
  permanently out-of-scope decision area, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- NON-EMERGENCY TRANSPORT
  LOGISTICS COORDINATION ONLY (non-emergency transport scheduling/logistics,
  vehicle/equipment availability coordination, non-clinical supply coordination,
  staff shift proposals, operational safety-concern flagging). It NEVER performs
  or authorizes:
    - emergency dispatch or triage decisions
    - medical necessity determination or clinical assessment
    - treatment planning or care-plan changes
    - medication administration, dosing, prescribing, or any pharma handling
    - medical procedures or clinical interventions
    - patient safety decisions, vital signs monitoring, or clinical assessment
    - any clinical-authority overrides

  Transport dispatch for emergencies is a separate, clinically-aware system.
  This actor is strictly logistics for pre-arranged, non-emergency transport.
  If a proposal reads like an emergency dispatch or patient medical assessment,
  that's out-of-scope content, not this actor's territory -- it routes through
  actual emergency dispatch/clinical systems, not this coordination actor.

  Three HARD checks, ALL permanent, un-overridable by any human
  approval:

    1. Transport-resource unverified  -- the target vehicle/transport resource
                                   record must exist AND be independently
                                   confirmed :registered?/:verified?
                                   in the store before ANY proposal
                                   for it may commit or even escalate.
                                   Never trusts a proposal's own claim
                                   about the resource -- re-derived from
                                   the resource's own store record, the
                                   same 'ground truth, not self-report'
                                   discipline every sibling actor's
                                   governor uses.
    2. Effect not :propose      -- every proposal's :effect MUST
                                   be :propose. Any other effect value
                                   is, by construction, a claim to
                                   directly actuate/commit outside
                                   governance -- HARD block, not merely
                                   low-confidence.
    3. Scope exclusion          -- ANY proposal (regardless of op)
                                   whose op, rationale, summary,
                                   citations or draft value touches
                                   emergency/triage/medical-necessity/
                                   clinical-decision/patient-care/
                                   clinical-procedure/vital-signs/
                                   clinical-authority territory is a HARD,
                                   PERMANENT block. Non-emergency transport
                                   is fundamentally separate from emergency
                                   dispatch.

  One ESCALATE (SOFT) gate: LLM confidence below the floor, OR the op
  is :flag-safety-concern -- ALWAYS escalates to a human, regardless
  of confidence, regardless of how clean the proposal otherwise is.
  `transportops.phase` independently agrees: :flag-safety-concern is
  never a member of any phase's :auto set either -- two layers, not
  one."
  (:require [clojure.string :as str]
            [transportops.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed proposal-op allowlist for non-emergency TRANSPORT
  LOGISTICS COORDINATION ONLY. An op outside this set is a scope violation
  by construction."
  #{:schedule-transport :coordinate-vehicle-availability :coordinate-supply-request
    :schedule-staff-shift-proposal :flag-safety-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-safety-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area. Non-emergency transport is
  fundamentally separate from emergency dispatch and clinical decision-
  making. Covers emergency dispatch, triage, medical necessity, diagnosis,
  treatment, medication, clinical procedures, patient assessment, vital signs,
  or clinical-authority enforcement. Scanned across the proposal's op/summary/
  rationale/cites/value, never trusting the advisor's own intent."
  ;; Emergency dispatch & triage
  ["emergency" "emergency dispatch" "emergency-dispatch" "緊急" "緊急車両"
   "triage" "emergency room" "emergency-room" "er" "911" "999" "119"
   "ambulance dispatch" "ambulance-dispatch" "応急車"
   ;; Medical necessity & clinical assessment
   "medical necessity" "medical-necessity" "medical decision" "医学的必要性"
   "clinical assessment" "clinical-assessment" "臨床評価" "evaluate patient"
   "diagnosis" "diagnos" "診断" "assessment" "assessment of patient"
   ;; Treatment & care planning
   "treatment" "treatment plan" "treatment-plan" "care plan" "care-plan"
   "ケアプラン" "therapeutic" "therapy plan"
   ;; Medication & pharmaceutical
   "medicatio" "薬" "dosing" "処方" "prescription" "rx" "pharma" "drug"
   "iv fluid" "infusion" "inject" "intravenous" "subcutaneous"
   "antibiotic" "drug administration" "medication administration"
   ;; Clinical procedures
   "procedure" "手術" "wound care" "wound-care" "創傷" "dressing"
   "catheter" "カテーテル" "foley" "central line" "feeding tube"
   "peg tube" "tracheostomy" "ostomy" "suture"
   ;; Patient assessment & monitoring
   "vital sign" "vital-sign" "vitals" "blood pressure" "heart rate"
   "respiration" "temperature" "blood glucose" "o2 saturation"
   "patient assessment" "nursing assessment" "nursing-assessment"
   "physical restraint" "physical-restraint" "restraint" "拘束" "身体拘束"
   "seclusion" "隔離"
   ;; Clinical authority
   "clinical decision" "clinical-decision" "clinical authority"
   "license suspension" "license-suspension" "compliance enforcement"
   "investigat" "complaint" "patient safety" "patient-safety"
   "医療安全" "patient medical" "patient-medical"
   ;; End-of-life & palliative
   "end of life" "end-of-life" "dnr" "do not resuscitate" "終末期"
   "advance directive" "code status" "palliative" "hospice"])

(defprotocol Governor
  (violations [g proposal store]))

(defrecord TransportGovernor []
  Governor
  (violations [_ proposal store]
    (let [violations (transient [])]
      ;; Check 1: vehicle unverified
      (let [vehicle-id (:vehicle-id proposal)
            vehicle (store/vehicle store vehicle-id)]
        (when (or (nil? vehicle)
                  (not (:registered? vehicle))
                  (not (:verified? vehicle)))
          (conj! violations
                 {:code :vehicle-unverified
                  :message (str "Vehicle " vehicle-id " is not registered/verified in store")})))
      ;; Check 2: effect not :propose
      (when (not= :propose (:effect proposal))
        (conj! violations
               {:code :effect-not-propose
                :message (str "Effect must be :propose, got " (:effect proposal))}))
      ;; Check 3: scope exclusion (content scan)
      (let [proposal-str (str (clojure.string/lower-case
                              (str (:op proposal) " "
                                   (:summary proposal) " "
                                   (:rationale proposal) " "
                                   (:cites proposal) " "
                                   (:value proposal))))]
        (when (some #(clojure.string/includes? proposal-str %) scope-excluded-terms)
          (conj! violations
                 {:code :scope-excluded
                  :message "Proposal contains scope-excluded clinical/emergency/triage content"})))
      (persistent! violations))))

(defn check-violations
  "Run governor checks on a proposal. Returns empty seq if clean,
  or a seq of violation maps if problems found."
  [proposal store]
  (violations (->TransportGovernor) proposal store))

(defn should-escalate?
  "Determine if a proposal should escalate to human approval.
  Escalates on HARD violations, low confidence, or :flag-safety-concern ops."
  [proposal store]
  (let [viols (check-violations proposal store)
        confidence (:confidence proposal 1.0)]
    (or (seq viols)
        (< confidence confidence-floor)
        (contains? always-escalate-ops (:op proposal)))))
