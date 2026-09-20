# TenorClef architecture (Phase 0 — current vs target)

Audit date: 2026-09-19 (America/Phoenix). Repo audited: `vexrypt-rgb/TenorClef` (AltoClef fork).
On the Windows machine this tree is `C:\Users\redfa\Documents\MinecraftDev\altoclef`.

Phase 0 was docs-only. Phase 2 adds MovementEngine adapter for 2 travel tasks.
Phase 3 extracts `WorldKnowledge` + `MovementController` facades; AltoClef stays a shim.
Phase 4 adds optional `TaskResult` / `FailureReason` on `Task` (legacy boolean shims).
Phase 5 adds `KnowledgeFact` confidence/age/source on top of WorldKnowledge (trackers unchanged).
Phase 6 adds `RecoveryManager` mapping FailureReason → RecoveryDecision (not a planner).
Phase 7 adds a minimal Goal / Plan / PlanExecutor layer above tasks (not full GOAP).
Phase 8 adds a thin ThreatAssessor / ThreatMonitor that can pause/fail goals on HIGH/CRITICAL;
MobDefenseChain / WorldSurvivalChain remain the fallback.
Phase 9 adds a structured Agent JSON protocol over the existing `@agent` / file channel
(keep old chat commands).
Phase 10 adds an offline Scenario / BenchmarkHarness that records TaskResult / Recovery / Threat counters
and exports a JSON summary (no live MC client required for mocks).

## Product intent

| Layer | Role |
|-------|------|
| **TenorClef** | High-level autonomous agent: perception, world model, goals, planning, tasks, evaluation, recovery |
| **Ostinato** | Low-level movement: nav, pathfinding, physics; backends **Baritone** + **Tungsten** |

Target pipeline:

```
Goal → Requirements → Plan → Task → Action Interface → Ostinato MovementEngine → Baritone | Tungsten
```

Incremental refactor of the AltoClef fork — **do not rewrite from scratch**. First real code PR is Phase 2 (MovementEngine API).

## Current architecture

### Entrypoint / god object

- Fabric main entrypoint: `adris.altoclef.AltoClef` (`src/main/resources/fabric.mod.json`).
- `AltoClef.onInitialize()` only registers for `TitleScreenEntryEvent`; mixin `adris.altoclef.mixins.EntryMixin` fires that event → `AltoClef.onInitializeLoad()` (real startup).
- Singleton: `AltoClef.getInstance()` — **~317 call sites** across source (plus many methods still take an `AltoClef mod` argument).
- `AltoClef` constructs and holds: `TaskRunner`, `TrackerManager`, every `TaskChain`, trackers, `BotBehaviour`, Butler, `InputControls` / `SlotHandler`, Baritone settings bootstrap (`initializeBaritoneSettings`), commands.

### Task system

```
TaskRunner.tick()
  └─ selects highest getPriority() among active TaskChain(s)
       └─ SingleTaskChain runs one Task tree
            └─ Task.tick() → onTick() may return a child Task
```

| Type | Package |
|------|---------|
| `TaskRunner` | `adris.altoclef.tasksystem.TaskRunner` |
| `TaskChain` | `adris.altoclef.tasksystem.TaskChain` |
| `Task` | `adris.altoclef.tasksystem.Task` |
| `SingleTaskChain` | `adris.altoclef.chains.SingleTaskChain` |

**Phase 4 (incremental):** optional `TaskResult` / `FailureReason` / `TaskFailure` on `Task`.
Finish still = `isFinished()` for the tick loop (unmigrated tasks unchanged). Observers use
`getLastResult()` / `getLastFailure()`; child FAILURE/RETRY/BLOCKED can absorb upward.
Migrated emitters: GetToBlock (stale TIMEOUT), CustomBaritoneGoalTask (progress TIMEOUT/NO_PATH),
GetToEntity (TARGET_UNAVAILABLE / TIMEOUT), ResourceTask SUCCESS shim, PickupDroppedItem (INVENTORY_FULL).

### Chains (constructed in `AltoClef.onInitializeLoad`)

| Chain | Class | Priority notes (approx.) |
|-------|-------|---------------------------|
| User command tasks | `UserTaskChain` | `50` |
| Mob defense | `MobDefenseChain` | threat-scaled (~50–70+) |
| Death / disconnect UI | `DeathMenuChain` | high when screens require action |
| Interaction repair | `PlayerInteractionFixChain` | situational |
| MLG / fall clutch | `MLGBucketFallChain` | up to `100` while falling |
| Unstuck | `UnstuckChain` | `55` when stuck heuristics fire |
| Pre-equip | `PreEquipItemChain` | situational |
| World survival | `WorldSurvivalChain` | `60`–`100` (lava / fire / drown / portal) |
| Food | `FoodChain` | ~`55` when eating needed |

### Trackers

- `EntityTracker`, `BlockScanner`, `SimpleChunkTracker`, `MiscBlockTracker`, `CraftingRecipeTracker`
- Storage: `ItemStorageTracker`, `ContainerSubTracker` (+ inventory sub-trackers under `trackers.storage`)
- Blacklisting helpers under `trackers.blacklisting`
- Manager: `TrackerManager`

### Catalogue

- `adris.altoclef.TaskCatalogue` (~971 lines) — static hardcoded resource name → task factory maps.

### Movement coupling today

**Path A — direct Baritone (dominant).** Tasks/chains call through `AltoClef.getClientBaritone()` / `BaritoneAPI`:

- `getCustomGoalProcess().setGoalAndPath(...)`
- `getPathingBehavior().forceCancel()` / `isPathing()` / `isSafeToCancel()`
- `getExploreProcess().explore(...)`
- `getBuilderProcess()` / `getMineProcess()`
- `getInputOverrideHandler().setInputForceState(...)`
- Extra hooks: `baritone.altoclef.AltoClefSettings` via `getExtraBaritoneSettings()`

**Path B — Tungsten travel facade (optional).** `adris.altoclef.movement.TungstenMovement` + reflection `TungstenBridge` → `TungstenGotoTask` / `TungstenFollowTask`. Mining intentionally stays on Baritone (`docs/TUNGSTEN_BACKEND.md`).

**Ostinato MovementEngine (Phase 2):** Ostinato tip exposes `IMovementEngine` / `HybridMovementEngine` (built on `IMovementBackend`). TenorClef routes **GetToBlockTask** and **GetToEntityTask** through `adris.altoclef.movement.MovementEngineAdapter` (reflection + CustomGoalProcess fallback). Other ~60 Baritone call sites unchanged. Mining/builder stay on Baritone processes.

### Phase 3 core split (incremental)

| Type | Package | Role |
|------|---------|------|
| `WorldKnowledge` | `adris.altoclef.knowledge` | Read-only facade over existing trackers + player/world |
| `AltoClefWorldKnowledge` | same | Live impl backed by AltoClef getters |
| `MovementController` | `adris.altoclef.control` | Travel ops (go-to / follow / cancel / status) |
| `AdapterMovementController` | same | Thin wrap of Phase 2 `MovementEngineAdapter` |
| `CoreServices` | `adris.altoclef.core` | Bundles knowledge + movement for injection |

**Shim:** `AltoClef.getInstance()` and existing getters still work. New accessors:
`getWorldKnowledge()`, `getMovement()`, `getCoreServices()`.

**Migrated call sites (few):** `CustomBaritoneGoalTask` (GetToBlock path), `GetToEntityTask`,
and a few `GetToBlockTask` world/scanner reads via `WorldKnowledge`.

**Still on AltoClef god object (~317 `getInstance()` sites):** task runner, chains, butler,
slot/input controls, Baritone settings bootstrap, food/mob/MLG chains, catalogue, most tasks.


### Phase 4 task outcomes (incremental)

| Type | Package | Role |
|------|---------|------|
| `TaskResult` | `adris.altoclef.tasksystem` | RUNNING / SUCCESS / FAILURE / CANCELLED / BLOCKED / RETRY |
| `FailureReason` | same | NO_PATH, TIMEOUT, TARGET_UNAVAILABLE, INVENTORY_FULL, … |
| `TaskFailure` | same | reason + message + recoverable + retryCount |
| `TaskResultMapper` | same | Pure legacy shim + absorb rules (offline-tested) |

**Hypothesis confirmed:** `Task.onTick` still returns `Task` or null; `lastResult`/`lastFailure`
live on the base class — subclasses opt in via `fail()` / `succeed()` without a mass migrate.


### Phase 5 knowledge confidence (incremental)

| Type | Package | Role |
|------|---------|------|
| `KnowledgeSource` | `adris.altoclef.knowledge` | SENSOR / SCANNER / MEMORY / INFERRED / USER |
| `KnowledgeFact<T>` | same | value + observedTick + confidence + source |
| `KnowledgeFacts` | same | fresh-enough / decay / merge / replace / isReliable |
| `KnowledgeFactCache` | same | Bounded LRU cache for selected signals (not a DB) |

**Fact APIs on `WorldKnowledge`** (defaults → unknown; `AltoClefWorldKnowledge` live):
`playerPositionFact`, `playerHealthFact`, `entityAliveFact`, `nearestEntityFact`,
`blockPresentFact`, `lastSeenBlockFact`, plus `currentTick()`.

**Cached keys:** `player.pos`, `player.health`, `entity.alive.<id>`, `entity.nearest.<types>`,
`block.present.<pos>`, `block.lastSeen.<blocks>`.

**Hypothesis confirmed:** wrap high-value reads as facts on `AltoClefWorldKnowledge` rather than
a new tracker subsystem. Direct getters remain shims.

**Migrated call sites:** `GetToEntityTask` (alive fact + stale confidence → TARGET_UNAVAILABLE),
`GetToBlockTask` portal patient init via `blockPresentFact` (world getter still available).

### Phase 6 recovery (incremental)

| Type | Package | Role |
|------|---------|------|
| `RecoveryAction` | `adris.altoclef.tasksystem` | RETRY / ALTERNATE_PATH / ALTERNATE_TARGET / WAIT / ABORT / ESCALATE |
| `RecoveryDecision` | same | action + attempt/maxAttempts + note |
| `RecoveryManager` | same | FailureReason (+ retry count) → decision; `apply()` enriches TaskFailure |

**Policy (defaults: maxPathRetries=3, maxInventoryWaits=2):**

| FailureReason | While under limit | After limit |
|---------------|-------------------|-------------|
| NO_PATH / TIMEOUT | ALTERNATE_PATH (1st) then RETRY | ABORT |
| TARGET_UNAVAILABLE | ABORT | ABORT |
| INVENTORY_FULL | WAIT | ABORT |
| DANGER / PLAYER_DEAD | ESCALATE | ESCALATE |
| other | ESCALATE | ESCALATE |

**Wiring:** `Task.failWithRecovery` / `absorbChildOutcome` consults RecoveryManager for
NO_PATH / TIMEOUT / TARGET_UNAVAILABLE / INVENTORY_FULL. Unmigrated tasks unchanged.
`TaskRunner` status may append `, recovery=<Action>`.

**Migrated emitters:** CustomBaritoneGoal / GetToEntity (TIMEOUT path), GetToEntity
(TARGET_UNAVAILABLE → ABORT), PickupDroppedItem (INVENTORY_FULL → WAIT then ABORT).
GetToBlock progress stalls inherit CustomBaritoneGoal recovery; stale-finished stays
non-recoverable `fail(TIMEOUT,…)`.

**Hypothesis confirmed:** small RecoveryManager from absorb / failWithRecovery is enough —
no GOAP / strategic planner (Phase 7).

### Phase 7 strategic planner (incremental)

| Type | Package | Role |
|------|---------|------|
| `Goal` / `Requirement` | `adris.altoclef.planner` | Strategic objective + item/predicate needs |
| `AcquireItemGoal` | same | Demo: get N of catalogue item X |
| `Plan` / `PlanStep` | same | Linear steps (catalogue key + count) |
| `Planner` / `SimplePlanner` | same | Inventory → short collect plan (not GOAP) |
| `PlanExecutor` / `GoalManager` | same | Run steps; on ABORT replan once via RecoveryManager |
| `PlanRunnerTask` | same | Catalogue Task bridge on UserTaskChain |
| `CatalogueInventoryView` | same | Live inventory counts via TaskCatalogue matches |
| `@goal` | `commands.GoalCommand` | Debug hook (`@goal cobblestone 64`); does not replace `@get` |

**Hypothesis (Phase 7):** linear SimplePlanner + PlanExecutor wrapping catalogue tasks is enough.
Full HTN / GOAP, combat unify, agent JSON protocol remain later phases.


### Phase 8 combat / survival threat (incremental)

| Type | Package | Role |
|------|---------|------|
| `ThreatLevel` | `adris.altoclef.threat` | NONE / LOW / MEDIUM / HIGH / CRITICAL |
| `ThreatAssessment` | same | level + reason + optional timeToDanger + suggested RecoveryAction |
| `ThreatSignals` | same | Pure input snapshot (health, food, lava/fire/drown, hostiles) |
| `ThreatEvaluator` / `ThreatAssessor` | same | Signals → assessment (wraps survival/defense priorities) |
| `ThreatMonitor` | same | Tick hook; latest assessment; apply to PlanExecutor |
| `ThreatSignalCollector` | same | Live AltoClef / EntityTracker / WorldKnowledge → signals |
| `@threat` | `commands.ThreatCommand` | Debug status |

**Interrupt policy:** HIGH → `GoalStatus.PAUSED` (PlanRunnerTask idles; MobDefense still runs).
CRITICAL → fail goal with `FailureReason.DANGER` (RecoveryManager ESCALATEs; no naive replan).
Below HIGH → resume if paused.

**Hypothesis confirmed:** ThreatAssessor + monitor pausing GoalManager on HIGH/CRITICAL is enough —
leave MobDefenseChain / WorldSurvivalChain running as today.

### Phase 9 agent JSON protocol (incremental)

| Type | Package | Role |
|------|---------|------|
| `AgentRequest` / `AgentResponse` / `AgentStatus` | `adris.altoclef.agent` | Structured schema |
| `AgentJson` | same | Hand-rolled JSON codec (offline-testable) |
| `AgentRuntime` / `AltoClefAgentRuntime` | same | Side effects → GoalManager / snapshot / cancel |
| `AgentRequestHandler` / `AgentProtocol` | same | Parse + dispatch facade |
| Transport | `AgentCommand` / `AgentFiles` / `AgentLoopTask` | `@agent json`, inbox JSON lines, `request.json` → `response.json` |

**Supported actions:** `get` / `acquire` / `goal` → Phase 7 `AcquireItemGoal` via GoalManager;
`status` / `snap` → WorldKnowledge + active goal/task/threat snapshot (partial OK);
`cancel` → GoalManager.cancel + `cancelUserTask` when safe.

**Hypothesis:** JSON over the existing agent file/command channel is enough — handler routes into
GoalManager / TaskRunner without new networking. Legacy `@agent` verbs / `@goal` / `@get` unchanged.

See [`AGENT_PROTOCOL.md`](./AGENT_PROTOCOL.md).


### Phase 10 scenarios / benchmarks (incremental)

| Type | Package | Role |
|------|---------|------|
| `Scenario` / `ScenarioContext` | `adris.altoclef.benchmark` | Named runnable (offline or future in-game) |
| `BenchmarkResult` | same | name, success, durationMs, deaths?, replans?, pathFails?, notes |
| `BenchmarkCounters` | same | TaskResult / Recovery / FailureReason / threat tallies |
| `BenchmarkHarness` | same | run / aggregate / export |
| `BenchmarkJson` | same | Hand-rolled JSON summary (no Jackson) |
| `MockScenarios` | same | Offline acquire / path-fail / threat / death fixtures |

**Hypothesis:** recording TaskResult / Recovery / Threat counters in a mock harness is enough to
bootstrap metrics without a full Minecraft client; in-game scenarios can plug in later.

### Multi-version

ReplayMod preprocess + Fabric Loom: `settings.gradle.kts` includes `1.21.11` … `1.16.1`. Shared sources in `src/main/java`; per-version dirs under `versions/`.

## Target architecture

```
┌──────────────────────────────────────────────┐
│ TenorClef                                     │
│  Goal / Requirements / Planner                │
│  WorldKnowledge (timestamped facts)           │
│  TaskExecutor (TaskResult / FailureReason)    │
│  Recovery + threat controllers                │
│  Action Interface ──────────────────────┐     │
└─────────────────────────────────────────│─────┘
                                          ▼
┌──────────────────────────────────────────────┐
│ Ostinato MovementEngine                       │
│  MovementGoal / Status / PathResult / failures│
│  Hybrid engine → Baritone | Tungsten backends │
└──────────────────────────────────────────────┘
```

Phase 2 should **evolve** Ostinato's `IMovementBackend` into the public MovementEngine surface and migrate 1–2 TenorClef call sites — not invent a second parallel API and not rewrite TenorClef.

## Related docs

- [`DEPENDENCIES.md`](./DEPENDENCIES.md) — Gradle / jars / JDKs
- [`TECH_DEBT.md`](./TECH_DEBT.md) — known debt
- [`PHASE0_AUDIT.md`](./PHASE0_AUDIT.md) — inventory + hotspots
- [`OSTINATO_BOUNDARY.md`](./OSTINATO_BOUNDARY.md) — ownership boundary
- [`ROADMAP.md`](./ROADMAP.md) — phases 0–10
