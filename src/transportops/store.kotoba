(ns transportops.store
  "SSoT for the ISIC-869 non-emergency transport logistics coordination actor,
  behind a `Store` protocol so the backend is a swap, not a rewrite -- the
  same seam every `cloud-itonami-isic-*` actor in this fleet uses.

  This actor coordinates the logistics of non-emergency patient/medical-goods
  transport operations: non-emergency transport scheduling/logistics (pickup/dropoff
  time and location), vehicle/equipment availability coordination (administrative
  tracking, never clinical readiness), non-clinical supply coordination
  (dispatch office supplies, non-clinical vehicle supplies), staff shift proposals,
  and operational safety-concern flagging (vehicle maintenance, route hazards).
  It NEVER touches emergency dispatch/triage, medical necessity determination,
  clinical assessment, or any patient medical emergency -- see `transportops.governor`'s
  `scope-excluded-terms`, a HARD, permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `vehicles` directory keyed by `:vehicle-id` STRING (never a
  keyword -- consistent keying from the start, avoiding the silent-miss
  bug that plagued an earlier shepherd attempt).

  A registered/verified vehicle record must exist before ANY proposal
  for that vehicle may ever commit or escalate -- `transportops.governor`'s
  `vehicle-unverified-violations` re-derives this from the vehicle's own
  `:registered?`/`:verified?` fields, never from proposal self-report,
  the SAME 'ground truth, not self-report' discipline every sibling
  actor's own governor uses.

  The ledger stays append-only: which vehicle a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by
  whom is always a query over an immutable log.")

(defprotocol Store
  (vehicle [s vehicle-id] "Registered vehicle record, or nil.
    Vehicle map: {:vehicle-id .. :location .. :registered? bool :verified? bool}.")
  (all-vehicles [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-vehicles [s vehicles] "replace/seed the vehicle directory (map vehicle-id->vehicle)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained vehicle directory covering both the happy path
  and the governor's own hard checks, so the actor + tests run offline."
  []
  {:vehicles
   {"van-001" {:vehicle-id "van-001" :location "Dispatch Base A"
               :registered? true :verified? true}
    "van-002" {:vehicle-id "van-002" :location "Dispatch Base A"
               :registered? true :verified? true}
    "van-003" {:vehicle-id "van-003" :location "Maintenance - pending inspection"
               :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (vehicle [_ vehicle-id] (get-in @a [:vehicles vehicle-id]))
  (all-vehicles [_] (sort-by :vehicle-id (vals (:vehicles @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-vehicles [s vehicles] (when (seq vehicles) (swap! a assoc :vehicles vehicles)) s))

(defn seed-db
  "A MemStore seeded with the demo vehicle directory. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `vehicles` map (vehicle-id string ->
  vehicle map) -- the primary test/dev entry point. `vehicles` may be empty
  (an unregistered-everywhere store)."
  ([] (mem-store {}))
  ([vehicles]
   (->MemStore (atom {:vehicles vehicles :ledger [] :coordination-log []}))))
