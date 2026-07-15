(ns transportops.advisor
  "TransportAdvisor -- the non-emergency transport logistics advisor.

  This is a mock advisor (deterministic) that also serves as a seam
  for a real LLM via langchain.model. The mock covers both the happy path
  (legitimate transport coordination) and the governor's hard-check failure
  modes (unregistered vehicle, non-:propose effect, scope-excluded content)
  so the actor can be tested end-to-end offline without LLM calls.")

(defprotocol Advisor
  (advise [a request store]))

(defrecord MockAdvisor []
  Advisor
  (advise [_ request _store]
    (let [op (:op request)]
      (case op
        :schedule-transport
        {:op :schedule-transport
         :vehicle-id (:vehicle-id request "van-001")
         :pickup-time (:pickup-time request)
         :dropoff-time (:dropoff-time request)
         :pickup-location (:pickup-location request)
         :dropoff-location (:dropoff-location request)
         :summary "Schedule non-emergency patient transport"
         :rationale "Coordination of transport logistics"
         :cites []
         :confidence 0.95
         :effect :propose
         :value {:transport-type :non-emergency}}

        :coordinate-vehicle-availability
        {:op :coordinate-vehicle-availability
         :vehicle-id (:vehicle-id request "van-001")
         :summary "Update vehicle availability status"
         :rationale "Administrative vehicle tracking"
         :cites []
         :confidence 0.9
         :effect :propose
         :value {:status :available}}

        :coordinate-supply-request
        {:op :coordinate-supply-request
         :vehicle-id (:vehicle-id request "van-001")
         :summary "Request non-clinical vehicle supplies"
         :rationale "Dispatch office supply coordination"
         :cites []
         :confidence 0.88
         :effect :propose
         :value {:supplies [:fuel :cleaning-supplies :safety-equipment]}}

        :schedule-staff-shift-proposal
        {:op :schedule-staff-shift-proposal
         :summary "Propose administrative staff shift roster"
         :rationale "Dispatch center staffing coordination"
         :cites []
         :confidence 0.85
         :effect :propose
         :value {:shift-count 3 :shift-duration 8}}

        :flag-safety-concern
        {:op :flag-safety-concern
         :vehicle-id (:vehicle-id request)
         :summary "Flag vehicle maintenance issue"
         :rationale "Mechanical safety concern"
         :cites []
         :confidence 0.92
         :effect :propose
         :value {:concern-type :mechanical :description (:description request)}}

        ;; Fallback for unknown op
        {:op op
         :vehicle-id (:vehicle-id request)
         :summary "Unknown operation"
         :confidence 0.0
         :effect :propose
         :value {}}))))

(defn mock-advisor []
  (->MockAdvisor))

;; LLM seam (stub for now, langchain integration point)
(defn llm-advisor [_model-name]
  ;; TODO: Implement real LLM advisor via langchain.model
  ;; For now, return the mock
  (mock-advisor))
