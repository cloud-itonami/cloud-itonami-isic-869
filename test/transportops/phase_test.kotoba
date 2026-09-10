(ns transportops.phase-test
  "Tests for the 0→3 phase rollout control."
  (:require [clojure.test :refer [deftest is testing]]
            [transportops.phase :as phase]))

(deftest phase-0-all-held
  (testing "Phase 0: All ops are held for human approval"
    (doseq [op [:schedule-transport :coordinate-vehicle-availability :coordinate-supply-request
                :schedule-staff-shift-proposal :flag-safety-concern]]
      (is (false? (phase/may-auto-commit? op 0))))))

(deftest phase-1-low-risk-auto
  (testing "Phase 1: Only supply and staff proposals auto-commit"
    (is (true? (phase/may-auto-commit? :coordinate-supply-request 1)))
    (is (true? (phase/may-auto-commit? :schedule-staff-shift-proposal 1)))
    (is (false? (phase/may-auto-commit? :schedule-transport 1)))
    (is (false? (phase/may-auto-commit? :coordinate-vehicle-availability 1)))
    (is (false? (phase/may-auto-commit? :flag-safety-concern 1)))))

(deftest phase-2-medium-risk-auto
  (testing "Phase 2: Low-risk + vehicle coordination auto-commit"
    (is (true? (phase/may-auto-commit? :coordinate-supply-request 2)))
    (is (true? (phase/may-auto-commit? :schedule-staff-shift-proposal 2)))
    (is (true? (phase/may-auto-commit? :coordinate-vehicle-availability 2)))
    (is (false? (phase/may-auto-commit? :schedule-transport 2)))
    (is (false? (phase/may-auto-commit? :flag-safety-concern 2)))))

(deftest phase-3-high-risk-auto
  (testing "Phase 3: All logistics ops auto-commit, except safety (always held)"
    (is (true? (phase/may-auto-commit? :coordinate-supply-request 3)))
    (is (true? (phase/may-auto-commit? :schedule-staff-shift-proposal 3)))
    (is (true? (phase/may-auto-commit? :coordinate-vehicle-availability 3)))
    (is (true? (phase/may-auto-commit? :schedule-transport 3)))
    (is (false? (phase/may-auto-commit? :flag-safety-concern 3)))))

(deftest safety-concern-never-auto-commits
  (testing ":flag-safety-concern is NEVER auto-commit at any phase"
    (doseq [phase [0 1 2 3]]
      (is (false? (phase/may-auto-commit? :flag-safety-concern phase))))))
