(ns flight-operations.store
  "SSoT for the ISCO-08 3153 flight operations support & dispatch actor.
  Store is a protocol injected into the `flight-operations.actor` StateGraph
  — `MemStore` is the default, deterministic, zero-dep backend; a
  Datomic/kotoba-server-backed implementation can be swapped in without
  touching the actor or governor (itonami actor pattern, per
  ADR-2607011000 / CLAUDE.md Actors section).

  Domain:

    aircraft  — a registered aircraft record (:aircraft-id, :type, :registration)
    crew      — a registered crew member (:crew-id, :license, :type)
    operation — a committed ground/pre-flight operation (flight-plan draft,
               checklist log, mechanical flag, ground-support coordination)
               — written ONLY via commit-operation!, never mutated in place
    ledger    — an append-only audit trail of every proposal/verdict/
               disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (aircraft [s aircraft-id])
  (crew [s crew-id])
  (operations-of [s aircraft-id])
  (ledger [s])
  (register-aircraft! [s aircraft])
  (register-crew! [s crew-member])
  (commit-operation! [s operation])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (aircraft [_ aircraft-id] (get-in @a [:aircraft aircraft-id]))
  (crew [_ crew-id] (get-in @a [:crew crew-id]))
  (operations-of [_ aircraft-id]
    (filter #(= aircraft-id (:aircraft-id %)) (:operations @a)))
  (ledger [_] (:ledger @a))
  (register-aircraft! [s aircraft]
    (swap! a assoc-in [:aircraft (:aircraft-id aircraft)] aircraft) s)
  (register-crew! [s crew-member]
    (swap! a assoc-in [:crew (:crew-id crew-member)] crew-member) s)
  (commit-operation! [s operation]
    (swap! a update :operations (fnil conj []) operation) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:aircraft {} :crew {} :operations [] :ledger []} seed)))))
