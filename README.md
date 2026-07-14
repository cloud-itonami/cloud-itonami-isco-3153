# cloud-itonami-isco-3153

Open Occupation Blueprint for **ISCO-08 3153**: Aircraft Pilots and Related Associate Professionals.

This repository designs a forkable OSS blueprint for flight-operations **support & dispatch** as a governor-gated actor: supporting pre-flight planning, administrative checklist logging, mechanical concern flagging, and ground-support coordination under an independent **Flight Operations Governor** that ensures no proposal touches flight control, go/no-go decisions, or any safety-critical authority.

## CRITICAL: Scope Exclusion — What This Actor Does NOT Do

**This is a SUPPORT & DISPATCH actor only, for ground/pre-flight back-office workflow.**

This actor NEVER:
- Controls any aircraft system (autopilot, flight surfaces, engines, avionics)
- Makes go/no-go decisions on flight readiness or safety-of-flight
- Exercises pilot-in-command authority in any form
- Performs or certifies airworthiness determinations
- Coordinates real-time in-flight operations or communications
- Authorizes crew rest, duty time, or regulatory compliance
- Dispatches aircraft or issues clearance for takeoff/landing
- Makes maintenance sign-off or release determinations
- Performs any time-critical or safety-critical actuation

**Any proposal attempting any of the above is a hard, permanent block** — even with `:propose` effect. Scope violations are never escalable to human sign-off; they are structurally excluded from this actor's vocabulary entirely.

## Flight Operations Premise

All cloud-itonami verticals are designed on the premise that decision-making is gated by an independent governor. Here, a **Flight Operations Governor** gates all proposals under strict safety rules: the advisor (mock or LLM) can only propose ground/pre-flight actions (flight plan draft, checklist log, mechanical flag, ground support coordination). The governor rejects any proposal that strays into flight control, authority, airworthiness certification, or real-time in-flight operations. The governor never dispatches actions itself; mechanical concerns always escalate to human review.

## Core Contract

```text
flight request (pre-flight/back-office only)
        |
        v
Flight Operations Advisor -> Flight Operations Governor -> support action or human escalation
        |
        v
committed operation record + audit ledger
```

No automated advice can dispatch an operation the governor refuses, suppress an operating record, or touch airborne/safety-critical systems.

## Capability Layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3153`). Required capabilities:

- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Reference Implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors section): a real
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph`, with the Advisor and Governor as distinct graph nodes and
human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                           +-> :request-approval   (:escalate? true, interrupt-before)
                                           +-> :hold               (:hard? true)
```

- `src/flight_operations/store.cljc` — `Store` protocol + `MemStore`:
  registered aircraft and crew, committed operations, an append-only audit ledger.
- `src/flight_operations/advisor.cljc` — `Advisor` protocol; `mock-advisor`
  (deterministic, default) proposes a flight operations support action from a
  request; `llm-advisor` wraps a `langchain.model/ChatModel` — either
  way the advisor only ever produces a `:propose`-effect proposal,
  never a committed record, and LLM parse failures always yield
  `confidence 0.0` (forces escalation, never fabricated confidence).
- `src/flight_operations/governor.cljc` — `FlightOperationsGovernor/check`: a pure
  function, wired as its own `:govern` node. Hard invariants
  (unregistered aircraft/crew, a proposal whose `:effect` isn't `:propose`,
  any operation touching flight control / go/no-go / airworthiness / crew authority)
  always route to `:hold`. Escalation invariants (`:flag-mechanical-concern`,
  or low advisor confidence) always route to `:request-approval` — an
  `interrupt-before` node that the graph checkpoints and only resumes on
  explicit human approval (`actor/approve!`).
- `src/flight_operations/actor.cljc` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

```bash
clojure -M:test
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation).

## License

AGPL-3.0-or-later.
