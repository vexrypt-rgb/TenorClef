# Benchmarks

TenorClef records scenario / live-run metrics under `adris.altoclef.benchmark`.

## Phase 10 — offline harness

| Type | Role |
|------|------|
| `Scenario` / `ScenarioContext` | Named runnable |
| `BenchmarkResult` | name, success, durationMs, deaths/replans/pathFails, notes, counters |
| `BenchmarkCounters` | TaskResult / RecoveryAction / FailureReason / threat tallies |
| `BenchmarkHarness` | run / aggregate / JSON export |
| `BenchmarkJson` | Hand-rolled JSON (no Jackson) |
| `MockScenarios` | Offline fixtures |

Run offline unit tests: `BenchmarkAggregationTest`, `MockScenariosTest`.

## Post-phase-10 — live hooks

Hypothesis: a singleton optional `LiveBenchmarkSession` consulted from
`RecoveryManager` / `ThreatMonitor` / `PlanExecutor` / `Task` is enough; avoid
ticking every entity.

| Type | Role |
|------|------|
| `LiveBenchmarkSession` | start/stop around a named run; null when inactive (no-op hooks) |
| `BenchmarkFiles` | `<gameDir>/altoclef/bench/` (fallback `altoclef/bench` or `bench-out`) |
| `@bench` | `start <name>` / `stop` / `status` / `goal <item> [count]` |

### Recorded metrics

- durationMs, success/fail
- TaskResult tallies (succeed / fail / recovery apply)
- FailureReason counts
- RecoveryAction counts (via `RecoveryManager.apply`)
- ThreatLevel peak; HIGH pause + CRITICAL fail counts
- PlanExecutor replan count

### Commands

```
@bench start my-run
@bench status
@bench stop
@bench goal cobblestone 64
```

`@bench goal` starts a session, fires `AcquireItemGoal` via `PlanRunnerTask`, and
stops (writing JSON) when the goal completes or fails.

### Safety

When no session is active, all `LiveBenchmarkSession.note*` helpers return
immediately. Gameplay paths must not depend on an active bench session.

### JSON

Live exports use `BenchmarkJson.toLiveJson` →
`altoclef/bench/<timestamp>-<name>.json` with `type=live`, peak threat, pause/fail
counts, and a nested `result` (same shape as Phase 10 `BenchmarkResult`).
