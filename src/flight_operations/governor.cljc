(ns flight-operations.governor
  "FlightOperationsGovernor — the independent safety/traceability layer for
  the ISCO-08 3153 flight operations support & dispatch actor. Wired as its
  own `:govern` node in `flight-operations.actor`'s StateGraph, downstream of
  `:advise`.

  CRITICAL SCOPE EXCLUSION — What This Actor Does NOT Do
  =========================================================
  This is a SUPPORT & DISPATCH actor ONLY. It NEVER:
    • Controls any aircraft system (autopilot, flight surface, engine, avionics)
    • Makes go/no-go decisions on flight readiness or safety-of-flight
    • Exercises pilot-in-command authority in any form
    • Performs or certifies airworthiness determinations
    • Coordinates real-time in-flight operations or communications
    • Authorizes crew rest, duty time, or regulatory compliance decisions
    • Dispatches aircraft or issues clearance for takeoff/landing
    • Makes maintenance sign-off or release determinations
    • Performs any time-critical or safety-critical actuation

  It ONLY supports pre-flight back-office / ground / administrative workflows.
  All proposals that touch any of the above are HARD blocks (`:hold`), never
  overridable.

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. aircraft & crew provenance — request's aircraft-id and crew-id must
       be registered.
    2. no-actuation — proposal :effect must be :propose.
    3. no-flight-control — any proposal op touching flight control, go/no-go,
       airworthiness, real-time in-flight, maintenance release, crew rest,
       or regulatory authority is permanently blocked (even :propose effect).
       HARD block, not escalation. Complete exclusion list in :disallowed-ops.

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    4. :op :flag-mechanical-concern (always escalates — mechanical concerns
       always surface to human review; never auto-proceed)
    5. low confidence (< `confidence-floor`)"
  (:require [flight-operations.store :as store]))

(def confidence-floor 0.6)

(def ^:private disallowed-ops
  "Operations permanently blocked (no proposal effect will permit these).
  Any proposal with these ops is a :hard? violation."
  #{:flight-control
    :go-no-go-decision
    :airworthiness-determination
    :maintenance-release
    :real-time-dispatch
    :pilot-in-command-authority
    :aircraft-system-control
    :crew-duty-regulation
    :takeoff-authorization
    :landing-authorization
    :in-flight-coordination})

(def ^:private escalating-ops
  "Operations that always require human sign-off (escalate via :request-approval)."
  #{:flag-mechanical-concern})

(defn- hard-violations [{:keys [proposal]} aircraft-record crew-record]
  (cond-> []
    (nil? aircraft-record)
    (conj {:rule :no-aircraft :detail "未登録 aircraft"})

    (nil? crew-record)
    (conj {:rule :no-crew :detail "未登録 crew"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

    (contains? disallowed-ops (:op proposal))
    (conj {:rule :forbidden-scope
           :detail "This operation touches flight control, go/no-go, airworthiness, real-time in-flight operations, or crew authority. Permanently excluded from this actor's scope."})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a `store`
  implementing `store.Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [aircraft-record (store/aircraft store (:aircraft-id request))
        crew-record (store/crew store (:crew-id request))
        hard (hard-violations {:proposal proposal} aircraft-record crew-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        escalating? (contains? escalating-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not escalating?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? escalating?))}))
