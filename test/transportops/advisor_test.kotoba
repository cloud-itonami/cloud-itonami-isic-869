(ns transportops.advisor-test
  "Tests for TransportAdvisor mock and LLM seam."
  (:require [clojure.test :refer [deftest is testing]]
            [transportops.store :as store]
            [transportops.advisor :as advisor]))

(deftest mock-advisor-coverage
  (testing "Mock advisor covers all allowed ops"
    (let [a (advisor/mock-advisor)
          s (store/seed-db)]
      (doseq [op [:schedule-transport :coordinate-vehicle-availability :coordinate-supply-request
                  :schedule-staff-shift-proposal :flag-safety-concern]]
        (let [request {:op op :vehicle-id "van-001"}
              response (advisor/advise a request s)]
          (is (= op (:op response)))
          (is (= :propose (:effect response)))
          (is (number? (:confidence response)))
          (is (<= 0 (:confidence response) 1)))))))

(deftest mock-advisor-happy-path
  (testing "Mock advisor produces valid transport scheduling proposals"
    (let [a (advisor/mock-advisor)
          s (store/seed-db)
          request {:op :schedule-transport :vehicle-id "van-001"
                  :pickup-time "10:00" :pickup-location "Clinic A"
                  :dropoff-time "11:00" :dropoff-location "Hospital B"}
          response (advisor/advise a request s)]
      (is (= :schedule-transport (:op response)))
      (is (string? (:summary response)))
      (is (string? (:rationale response)))
      (is (= :non-emergency (:transport-type (:value response)))))))

(deftest mock-advisor-high-confidence
  (testing "Mock advisor assigns high confidence to well-formed requests"
    (let [a (advisor/mock-advisor)
          s (store/seed-db)
          requests [
            {:op :schedule-transport :vehicle-id "van-001"}
            {:op :coordinate-vehicle-availability :vehicle-id "van-001"}
            {:op :coordinate-supply-request :vehicle-id "van-001"}
            {:op :flag-safety-concern :vehicle-id "van-001"}]]
      (doseq [req requests]
        (let [response (advisor/advise a req s)]
          (is (> (:confidence response) 0.8)
              (str "Low confidence for " req)))))))
