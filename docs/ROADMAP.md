# TenorClef + Ostinato — Engineering Roadmap

Source: user Engineering Implementation Brief (2026-09-19). Incremental refactor; **do not rewrite**.

Canonical workflow copy: `agent-data/workflows/tenorclef-ostinato/ROADMAP.md` (kept in sync with this file as of Phase 0).

## Target split

- **TenorClef** — high-level autonomous Minecraft agent: perception → world model → goals → planning → task execution → evaluation → recovery/replanning.
- **Ostinato** — low-level movement/action layer: navigation → pathfinding → movement → physics traversal → backend abstraction (**Baritone** + **Tungsten**).
- TenorClef must not know Baritone/Tungsten internals; Ostinato exposes a stable **MovementEngine** API.

```
Goal → Requirements → Plan → Tasks → Action Interface → Ostinato MovementEngine → Baritone | Tungsten
```

## Phases (order matters)

| Phase | Name | Outcome |
|------:|------|---------|
| 0 | Audit | `ARCHITECTURE` / `DEPENDENCIES` / `TECH_DEBT` / `PHASE0_AUDIT` / `OSTINATO_BOUNDARY` / this `ROADMAP` |
| 1 | Stabilize | Reproducible builds, CI, pin versions, remove machine-specific Gradle |
| 2 | Ostinato MovementEngine API | **First real code PR** — stable movement boundary |
| 3 | TenorClef core split | Extract WorldKnowledge, controllers; shrink AltoClef god object |
| 4 | Task / Failure model | TaskResult, FailureReason, structured TaskFailure, upward propagation |
| 5 | World model | KnowledgeFact with timestamp/confidence/source |
| 6 | Recovery | Retry, alternate path/resource, danger/death/inventory recovery |
| 7 | Strategic planner | Goal / Requirements / Subgoals / Plan / PlanStep |
| 8 | Combat + survival | Unify defense/survival under threat + recovery |
| 9 | Agent protocol | Structured JSON request/response (keep old commands temporarily) |
| 10 | Testing + benchmarks | Scenarios, metrics, mock world harness |

## Phase 2 — First PR (do not expand scope)

Define and ship:

- `MovementEngine`, `MovementGoal`, `MovementStatus`, `PathResult`, movement failures
- `BaritoneMovementBackend`, `TungstenMovementBackend`, `HybridMovementEngine`
- Preserve Tungsten-unavailable → Baritone fallback
- Migrate 1–2 representative TenorClef tasks onto the API
- Unit (+ integration where possible) tests + interface docs

**Note (Phase 0 finding):** Ostinato tip (`main` / MC 1.21.11) already has a precursor SPI —
`baritone.api.movement.IMovementBackend` + `MovementBackends` + `Settings.movementBackend`.
Phase 2 should **evolve that surface into MovementEngine**, not invent a parallel API and not rewrite from scratch.

## Engineering rules

1. Don't rewrite blindly — preserve working behavior
2. Don't mix architecture + feature + version migration in one change
3. Every architectural change needs tests
4. Keep Minecraft-specific code at the edges
5. No direct backend leakage into TenorClef
6. Failure is data (not false/null/bare exception)
7. Prefer deterministic behavior
8. LLMs are strategic, not motor control
9. Measure important outcomes
10. One architectural boundary at a time

## Definition of success (north star)

User: "Get me a full set of iron armor and a shield." → goal → plan → Ostinato nav (backend auto-selected) → interrupt on threat → escape → world update → replan → craft → verify SUCCESS. Failures and world change do not require a human-hardcoded path for every case.

## GitHub issue themes (create when stabilizing)

| Theme | Examples |
|-------|----------|
| Foundation | CI matrix, JDK pins, Ostinato jar publish, remove `org.gradle.java.home` |
| Ostinato movement | MovementEngine API, Baritone/Tungsten backends, Hybrid fallback |
| TenorClef core | Split AltoClef god object, inject services, kill singleton creep |
| Tasks / failure | TaskResult / FailureReason, catalogue cleanup |
| Intelligence | World model, planner, agent protocol |
| Testing / benchmarks | Scenario harness, metrics, mock world |

## Active work

- Phase 0 audit docs (this PR)
- Cloud Agents unavailable on current plan — use local checkouts / executors
- Do **not** start Phase 1/2 code until Phase 0 docs land
