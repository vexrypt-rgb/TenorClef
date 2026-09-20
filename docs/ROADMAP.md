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

**Status (Phase 2):** Ostinato tip evolves the precursor SPI into `IMovementEngine` /
`HybridMovementEngine` / `MovementGoal` / `PathResult` (see Ostinato `docs/MOVEMENT_ENGINE.md`).
TenorClef adapter uses reflection so stock Baritone jars keep working. **Merged on main.**

## Phase 3 — Core split

Vertical slice only — no rewrite, no mass migration:

1. `WorldKnowledge` read-only facade over existing trackers
2. `MovementController` wrapping `MovementEngineAdapter` (high-value, low-risk)
3. AltoClef shim + `getWorldKnowledge()` / `getMovement()` / `getCoreServices()`
4. Migrate GetToBlock / GetToEntity call sites only
5. Unit-test facade wiring with fakes; `:1.21.1:compileJava`

**Status (Phase 3):** **Merged on main** (PR #4).

## Phase 4 — Task / Failure model

Vertical slice — failures as data; do not break the tick loop:

1. `TaskResult`, `FailureReason`, `TaskFailure` (+ `TaskResultMapper` shims)
2. Optional `lastResult` / `lastFailure` on `Task`; child absorb upward
3. Migrate few tasks only: GetToBlock / GetToEntity / CustomBaritoneGoal / ResourceTask + PickupDroppedItem
4. Keep `isFinished()` / boolean behavior for unmigrated tasks
5. Unit tests for mapping / propagation; `:1.21.1:compileJava`

**Status (Phase 4):** **Merged on main** (PR #5).

## Phase 5 — World model / knowledge confidence

Vertical slice — metadata on top of trackers; do not invent a new tracker subsystem:

1. `KnowledgeSource`, `KnowledgeFact<T>`, `KnowledgeFacts` helpers (fresh/decay/merge)
2. Small in-memory `KnowledgeFactCache` for selected signals
3. Extend `WorldKnowledge` / `AltoClefWorldKnowledge` with fact APIs (position/health/entity/block)
4. Migrate 1–2 call sites: GetToEntity target validity; GetToBlock portal presence
5. Keep direct tracker getters working (shims)
6. Unit tests for age/confidence/decay; `:1.21.1:compileJava`

**Status (Phase 5):** **Merged on main** (PR #6).

## Phase 6 — Recovery / replan

Vertical slice — structured failures become actionable without a planner:

1. `RecoveryAction` / `RecoveryDecision` (RETRY, ALTERNATE_PATH, ALTERNATE_TARGET, WAIT, ABORT, ESCALATE)
2. `RecoveryManager` maps `FailureReason` (+ retry count) → decision with limits
3. Hook: `Task.failWithRecovery` + `absorbChildOutcome` for NO_PATH / TIMEOUT / TARGET_UNAVAILABLE / INVENTORY_FULL
4. Wire concrete cases: CustomBaritoneGoal / GetToEntity path stalls; GetToEntity target gone; PickupDroppedItem inventory full
5. Unit tests for policy mapping; `:1.21.1:compileJava`
6. Docs: ARCHITECTURE + this ROADMAP

**Status (Phase 6):** **Merged on main** (PR #7).

## Phase 7 — Strategic planner

Vertical slice — Goal → Plan → catalogue Task; not full GOAP / HTN:

1. `Goal` / `Requirement` / `Plan` / `PlanStep` / `Planner` / `SimplePlanner`
2. Demo `AcquireItemGoal` (get N of item X) inspecting inventory via `InventoryView`
3. `PlanExecutor` / `GoalManager`: run current step; on TaskFailure use Phase 6 `RecoveryManager`; on ABORT replan once naively or fail goal
4. `PlanRunnerTask` + `@goal` debug command (does not break `@get` / UserTaskChain)
5. Unit tests with fake inventory; `:1.21.1:compileJava`
6. Docs: ARCHITECTURE + this ROADMAP

**Status (Phase 7):** **Merged on main** (PR #8).


## Phase 8 — Combat / survival threat unify

Vertical slice — thin threat layer; do **not** rewrite MobDefense / WorldSurvival:

1. `ThreatLevel` / `ThreatAssessment` / `ThreatSignals`
2. `ThreatEvaluator` / `ThreatAssessor` reading health, food, lava/fire/drown, nearby hostiles
3. `ThreatMonitor` tick hook from AltoClef; `ThreatSignalCollector` wraps existing trackers
4. Integration: HIGH → pause GoalManager; CRITICAL → fail with `FailureReason.DANGER` (RecoveryManager ESCALATEs)
5. Optional `@threat` debug command
6. Unit tests with fake health/hostile inputs; `:1.21.1:compileJava`
7. Docs: ARCHITECTURE + this ROADMAP

**Status (Phase 8):** **Merged on main** (PR #9).

## Phase 9 — Agent JSON protocol

Vertical slice — structured request/response; keep old chat commands:

1. `AgentRequest` / `AgentResponse` / `AgentStatus` (accepted|running|success|failure|blocked|cancelled)
2. `AgentProtocol` / `AgentRequestHandler` dispatching get/acquire/goal → GoalManager; status/snap → WorldKnowledge snapshot; cancel → safe user/goal cancel
3. Transport: `@agent json <payload>`, JSON lines in `inbox.txt`, or `request.json` drop (writes `response.json`)
4. Unit tests for parse + dispatch with fakes; `:1.21.1:compileJava`
5. Docs: `AGENT_PROTOCOL.md` + ARCHITECTURE + this ROADMAP

**Status (Phase 9):** **Merged on main** (PR #10).

## Phase 10 — Testing + benchmarks (this PR)

Incremental harness — offline/mock first; no full Minecraft client required:

1. `Scenario` / `BenchmarkResult` (name, success, durationMs, deaths?, replans?, pathFails?, notes)
2. `BenchmarkCounters` records TaskResult / RecoveryAction / FailureReason / threat tallies
3. `BenchmarkHarness` runs scenarios, aggregates, exports JSON summary (`BenchmarkJson`)
4. `MockScenarios` — acquire success, path-fail→recover, threat-critical abort, death abort
5. Unit tests for aggregation + JSON export; `:1.21.1:compileJava`
6. Docs: ARCHITECTURE + this ROADMAP

**Status (Phase 10):** **Merged on main** (PR #12).
Out of scope then: full in-game RSG harness, LLM eval loops.

### Post-phase-10 — live benchmark hooks

1. `LiveBenchmarkSession` singleton (null/no-op when inactive)
2. Cheap hooks: `Task` succeed/fail, `RecoveryManager.apply`, `PlanExecutor` replan/threat, `ThreatMonitor.tick` peak
3. `@bench start|stop|status|goal` → JSON under `altoclef/bench/`
4. Unit tests with fake events; `:1.21.1:compileJava`
5. Docs: `BENCHMARKS.md` + this note

**Status:** in progress on `feat/live-benchmark-hooks`.


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

- Phase 0–10 merged on `main` (Phase 10 = PR #12 mock BenchmarkHarness)
- **Post-phase-10 live hooks:** `feat/live-benchmark-hooks`
  - `LiveBenchmarkSession` + `@bench start|stop|status|goal`
  - Hooks: Task / RecoveryManager / PlanExecutor / ThreatMonitor (null-safe)
  - JSON under `altoclef/bench/`; see `docs/BENCHMARKS.md`
- CI: `1.21.11` + Deploy Javadoc non-blocking (`build/ci-noise-cleanup`); required gates `1.21.1` + `1.16.1`
- Cloud Agents unavailable — local checkouts / executor patches
