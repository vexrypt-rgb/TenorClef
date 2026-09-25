# Phase 0 Inventory — TenorClef + Ostinato

Companion to [`BASELINE.md`](BASELINE.md). Audit date 2026-09-24.
TenorClef = `altoclef/` @ `873e4d79` + dirty working tree. Ostinato tip = `Ostinato/` @ `99ee67ad`.
Ostinato 1.16.1 = `Ostinato-1.16.1/` @ `f5472de3` (+ dirty tree).

**Live status** is relative to the Reported live entry `@testrun2` on MC 1.16.1
(`LIVE` / `GOAL-ONLY` / `TEST-ONLY` / `DEAD` / `ABSENT FROM HEAD`). TC paths are relative to
`src/main/java/adris/altoclef/` unless stated. "Tests" = test classes present; **none are runnable via
Gradle** (BASELINE §15), so "tested" below always means "a test source exists", not "executed".

---

## 1. Live execution spine (TenorClef)

| Component | Path | Exists | Purpose | Callers | Instantiation | Live | Tests | Version scope | Evidence / notes |
|---|---|---|---|---|---|---|---|---|---|
| `@testrun2` / `Testrun2Command` | `commands/Testrun2Command.java` | Yes | Start modern RSG | `AltoClefCommands` registry; `autoRunCommand` | `new Testrun2Command()` in `AltoClefCommands` | LIVE | none | all (shared src) | `call()` → `mod.runUserTask(new ModernSpeedrunTask(), …)` |
| `AltoClef.runUserTask` | `AltoClef.java:624` | Yes | Hand task to user chain | commands | — | LIVE | — | all | → `userTaskChain.runTask` |
| `UserTaskChain` | `chains/UserTaskChain.java` | Yes | Hosts user tasks, priority 50 | `TaskRunner` | `AltoClef.java:145` | LIVE | — | all | |
| `TaskRunner` | `tasksystem/TaskRunner.java` | Yes | Picks max-priority chain each tick | `AltoClef.onClientTick` | `AltoClef.java:139` | LIVE | — | all | Reads leaf `getLastFailure/Recovery` for status string only |
| `Task` / `TaskChain` | `tasksystem/` | Yes | Task tree base | everything | — | LIVE | `TaskPropagationTest`, `TaskResultMapperTest`, `TaskFailureTest` | all | `failWithRecovery` at `Task.java:237` |
| `ModernSpeedrunTask` | `tasks/speedrun/testrun2/ModernSpeedrunTask.java` | Yes (2,773 lines; +1,388 uncommitted) | Phase machine BOOTSTRAP→END | `Testrun2Command` | `new ModernSpeedrunTask()` | LIVE | none | all | `onTickInner:619`, `decide:1096`, `taskFor:1152` |
| `T2Brain` / `T2Probe` / `T2Solve` / `T2History` | `tasks/speedrun/testrun2/` | Yes | Watchdog: stall detection → replacement child | `ModernSpeedrunTask:717`, `AnyWeaponCombatTask:69` | static | LIVE | none | all | `T2Solve:201` input force, `:468` `cancelEverything()` |
| `SurfaceBailTask`, `WaterBailTask`, `HolePillarTask`, `StepOffTableTask`, `UnstickWalkTask`, `DeathRecycleTask`, `RuinedPortalFinishTask` (untracked) | `tasks/speedrun/testrun2/` | Yes | Situational recovery children | `ModernSpeedrunTask` | `new` per phase | LIVE | none | all | `RuinedPortalFinishTask`, `T2Deadman`, `T2Trace` are **untracked** (ABSENT FROM HEAD) |
| `TungstenMoveTask` | `tasks/speedrun/testrun2/TungstenMoveTask.java` | Yes | Name only — wraps `GetToBlockTask` (javadoc: "Baritone travel. Class name kept for callers.") | `ModernSpeedrunTask:1334,1344` | `new` | LIVE | none | all | Does **not** use Tungsten |
| `TaskCatalogue.getItemTask` | `TaskCatalogue.java` | Yes | Resource/craft task factory | `ModernSpeedrunTask` (27 sites) | static | LIVE | none | all | |

## 2. Movement

| Component | Repo / Path | Exists | Purpose | Callers | Instantiation | Live | Tests | Version scope | Evidence / notes |
|---|---|---|---|---|---|---|---|---|---|
| `MovementController` | TC `control/MovementController.java` | Yes | Travel facade interface | `CustomBaritoneGoalTask`, `GetToEntityTask` | — | LIVE | `FakeMovementController` (test fake) | all | |
| `AdapterMovementController` | TC `control/AdapterMovementController.java` | Yes | Delegates to adapter | via `AltoClef.getMovement()` (`:419`) | `INSTANCE`, stored `AltoClef.java:176` | LIVE | — | all | |
| `MovementEngineAdapter` | TC `movement/MovementEngineAdapter.java` | Yes | Reflection bridge to Ostinato engine; fallback `CustomGoalProcess` | `AdapterMovementController` | static | LIVE (fallback branch) | none | all | Probe needs `MovementBackends`, `MovementGoal`, `IMovementEngine`, `PathResult` — absent in runtime jars (BASELINE §8) |
| `CustomBaritoneGoalTask` | TC `tasks/movement/CustomBaritoneGoalTask.java` | Yes | Goal→task base; progress checker; recovery hook | 13 subclasses | — | LIVE | none | all | engine path `:208`, direct `CustomGoalProcess` `:210–212` |
| `GetToBlockTask` | TC `tasks/movement/GetToBlockTask.java` | Yes | Travel to block | `ModernSpeedrunTask`, `TungstenMoveTask`, bail tasks | `new` | LIVE | none | all | only subclass with `useMovementEngine()==true` (`:49`) |
| `GetToEntityTask` | TC `tasks/movement/GetToEntityTask.java` | Yes | Follow entity | many (`AbstractDoToEntityTask`…) | `new` | Unknown for `@testrun2` (not traced) | none | all | calls `getMovement().ensureGoalAndPath` `:177–180` |
| `TimeoutWanderTask` | TC `tasks/movement/TimeoutWanderTask.java` | Yes | Explore/wander | `ModernSpeedrunTask` (13 sites), `CustomBaritoneGoalTask`, `T2Solve` | `new` | LIVE | none | all | uses Baritone `ExploreProcess` |
| `TungstenMovement` | TC `movement/TungstenMovement.java` | Yes | Tungsten travel facade + mover pref | `@tgoto`, `TungstenGotoTask/FollowTask`, manhunt, `SpeedrunBeatMinecraftTask`; `@testrun2` only `statusLine()` (`ModernSpeedrunTask:492`) and `cancel()` via adapter | static | LIVE (status/cancel only) | `TungstenJarPresenceTest` | 1.21.x, 1.16.1 (jar present) | travel mover default `BARITONE` |
| `TungstenBridge` | TC `movement/TungstenBridge.java` | Yes | Reflection to `kaptainwutax.tungsten` | `TungstenMovement` | static | LIVE (bind/log only) | — | same | log: "TungstenBridge: kaptainwutax.tungsten bound" (Reported) |
| `TungstenGotoTask` / `@tgoto` | TC `tasks/movement/TungstenGotoTask.java`, `commands/TungstenGotoCommand.java` | Yes | Tungsten travel | `@tgoto`, `TungstenMovement.gotoBlock` | `new` | GOAL-ONLY (other entry) | none | | |
| `IMovementBackend`, `MovementBackendKind` | Ost `src/api/java/baritone/api/movement/` | Yes (tip, local `main`, `origin/main`); **absent** in `1.16.1`, `1.21.11` branches | Backend SPI | `MovementBackends`, `HybridMovementEngine` | — | not reachable from 1.16.1 | `MovementBackendSelectorTest` (8 pass) | tip | |
| `IMovementEngine`, `MovementGoal`, `PathResult`, `MovementStatus`, `MovementFailureReason` | Ost `src/api/java/baritone/api/movement/` | Yes at `99ee67ad` + `origin/main`; **absent** local `main`, `1.16.1`, `1.21.11`, and all `dist/` jars | Engine API | `HybridMovementEngine`, TC adapter (reflection) | — | ABSENT from runtime | none | tip source only | |
| `HybridMovementEngine` | Ost `src/main/java/baritone/movement/HybridMovementEngine.java` | Same as above | Tungsten-first, Baritone fallback | `MovementBackends.engine()` | `forBaritone()` per call | ABSENT from runtime | none | tip source only | |
| `MovementBackends` | Ost `src/main/java/baritone/movement/MovementBackends.java` | Yes (tip/main); present in `dist/…9bb1b309-dirty.jar` **without** `engine()` target classes | Backend selection from `movementBackend` | `CustomGoalProcess` (tip), adapter (reflection) | static | not reachable from 1.16.1 | via selector test | tip | `current()`: AUTO/TUNGSTEN → Tungsten iff `isAvailable()` |
| `BaritoneMovementBackend`, `TungstenMovementBackend` | Ost `src/main/java/baritone/movement/` | Yes (tip/main) | Backends | `MovementBackends`, `HybridMovementEngine` | `INSTANCE` | not reachable from 1.16.1 | selector test | tip | Tungsten availability by `Class.forName` on `kaptainwutax.tungsten.*` |
| `movementBackend` setting | Ost `src/api/java/baritone/api/Settings.java:1684` | Yes (tip) | `"auto"` default | `MovementBackends.preference()` | — | not reachable from 1.16.1 | — | tip | |

## 3. Combat

| Component | Path | Exists | Purpose | Callers | Live | Tests | Notes |
|---|---|---|---|---|---|---|---|
| `AbstractKillEntityTask.equipWeapon/bestWeapon` | `tasks/entity/AbstractKillEntityTask.java:47,60` | Yes | Sword-only equip | `KillEntitiesTask` (via `MobDefenseChain`), `KillAura.equipWeapon` | LIVE | none | never axes |
| `KillAura` | `control/KillAura.java` | Yes | Force-field attacks, shielding | `MobDefenseChain.doForceField` (`:529`, called from `getPriorityInner:264` every tick) | LIVE | none | SMART/DELAY → `attack(…, true)` sword-only; FASTEST → `forceDeequipHitTool` |
| `MobDefenseChain` | `chains/MobDefenseChain.java` | Yes (modified, +79 lines uncommitted) | Fight/flee/shield chain | `TaskRunner` | LIVE (owner settings `mobDefense:true`) | none | priorities 0, 50–100 (creeper), 60, 65, 70, 80; `MAX_ENGAGE_MS` disengage |
| `AnyWeaponCombatTask` | `tasks/speedrun/testrun2/combat/AnyWeaponCombatTask.java` | Yes | Melee/bow fight with best-DPS item | `FightNearbyTask` ← `ModernSpeedrunTask:1040` | LIVE | none | `equipBest:162` |
| `WeaponPicker` | `tasks/speedrun/testrun2/combat/WeaponPicker.java` | Yes | dmg×speed table ("Vanilla 1.21-ish") | `AnyWeaponCombatTask` | LIVE | none | unknown items default to fist (1 × 4.0) |
| `AttributeSwap` | `tasks/speedrun/testrun2/combat/AttributeSwap.java` | Yes | Charger/hitter swap | `AnyWeaponCombatTask` | LIVE (if `SpeedrunOpt.ATTRIBUTE_SWAP`) | none | |
| `EarlyOverworldSpeedrunTask` | `tasks/speedrun/EarlyOverworldSpeedrunTask.java` | Yes | Legacy speedrun early game | `SpeedrunBeatMinecraftTask:311` | GOAL-ONLY (other entry) | none | |
| `HotbarEquipSelector` | `util/helpers/` | Yes | Helper | not traced | Unknown | `HotbarEquipSelectorTest` | |

## 4. Recovery / tasks results

| Component | Path | Exists | Callers | Live | Tests | Notes |
|---|---|---|---|---|---|---|
| `RecoveryManager` | `tasksystem/RecoveryManager.java` | Yes | `Task.failWithRecovery`, `Task.absorbChildOutcome`, `PlanExecutor` | LIVE (advisory) | `RecoveryManagerTest` (9) | defaults 3 path retries, 2 inventory waits |
| `RecoveryDecision` / `RecoveryAction` | `tasksystem/` | Yes | above; `CustomBaritoneGoalTask`, `GetToEntityTask`, `PickupDroppedItemTask` | LIVE | same | `isTerminal()` consumed only by those three tasks |
| `TaskResult` / `FailureReason` / `TaskFailure` / `TaskResultMapper` | `tasksystem/` | Yes | `Task` | LIVE (recorded; not consumed by `ModernSpeedrunTask`) | 3 test classes | |
| `UnstuckChain` | `chains/UnstuckChain.java` | Yes | `TaskRunner` | LIVE | none | priority 55 |
| T2 overlays | see §1 | Yes | `ModernSpeedrunTask` | LIVE | none | effective recovery for `@testrun2` |

## 5. World knowledge

| Component | Path | Exists | Callers | Live | Tests | Notes |
|---|---|---|---|---|---|---|
| `WorldKnowledge` | `knowledge/WorldKnowledge.java` | Yes | `GetToBlockTask`, `GetToEntityTask`, `CustomBaritoneGoalTask`, `ThreatSignalCollector`, `CatalogueInventoryView`, agent runtime | LIVE (narrow) | `FakeWorldKnowledge` | |
| `AltoClefWorldKnowledge` | `knowledge/AltoClefWorldKnowledge.java` | Yes | `AltoClef.getWorldKnowledge()` | LIVE | — | constructed `AltoClef.java:175` |
| `KnowledgeFact<T>` / `KnowledgeFactCache` / `KnowledgeFacts` / `KnowledgeSource` | `knowledge/` | Yes | `GetToBlockTask` (`blockPresentFact`), `GetToEntityTask`, threat collector, agent | LIVE (narrow) | `KnowledgeFactTest` (9), `KnowledgeFactCacheTest` (4) | |
| Legacy trackers (`EntityTracker`, `ItemStorageTracker`, `BlockScanner`, …) | `trackers/` | Yes | `ModernSpeedrunTask` and most tasks | LIVE — authoritative | none | `InventorySubTracker` +89 lines uncommitted |

## 6. Planning, threat, agent, benchmark, config

| Component | Path | Exists | Callers | Live | Tests | Notes |
|---|---|---|---|---|---|---|
| `GoalManager` (extends `PlanExecutor`) | `planner/` | Yes | `GoalCommand`, `BenchCommand`, `AltoClefAgentRuntime`, `ThreatMonitor` | GOAL-ONLY | `PlanExecutorTest` (8) | |
| `PlanRunnerTask` | `planner/PlanRunnerTask.java` | Yes | `@goal`, `@bench`, `@agent` | GOAL-ONLY | — | |
| `SimplePlanner`, `AcquireItemGoal`, `Plan*`, `Requirement` | `planner/` | Yes | above | GOAL-ONLY | `SimplePlannerTest` (5) | |
| `ThreatMonitor` | `threat/ThreatMonitor.java` | Yes | `AltoClef` tick; `ThreatCommand`; agent runtime | GOAL-ONLY (computed every tick; no `@testrun2` effect) | `ThreatMonitorTest` (5) | effects: `applyToGoalManager`, `LiveBenchmarkSession.noteThreat` |
| `ThreatAssessor`, `ThreatEvaluator`, `ThreatSignalCollector`, `ThreatSignals` | `threat/` | Yes | `ThreatMonitor` | GOAL-ONLY | `ThreatAssessorTest` (13) | |
| `adris.altoclef.agent` (8 classes) | `agent/` | Yes | `@agent` → `AgentLoopTask`, `@ado` | GOAL-ONLY | `AgentJsonTest` (7), `AgentRequestHandlerTest` (10) | |
| `LiveBenchmarkSession` / `@bench` | `benchmark/`, `commands/BenchCommand.java` | Yes | `BenchCommand`; hooks in `Task`, `RecoveryManager`, `PlanExecutor`, `ThreatMonitor` | GOAL-ONLY (hooks no-op unless started) | `LiveBenchmarkSessionTest` (3) | |
| `BenchmarkHarness`, `MockScenarios` (4), `Scenario*`, `BenchmarkJson/Result/Counters/Files` | `benchmark/` | Yes | tests, `BenchmarkJson` | TEST-ONLY | `MockScenariosTest` (4), `BenchmarkAggregationTest` (5) | |
| `CoreServices` / `getCoreServices()` | `core/` | Yes | `AltoClef.java` only | DEAD (runtime) | `CoreServicesTest` (5) | |
| `Settings` / `ConfigHelper` / `altoclef_settings.json` | `Settings.java`, `util/helpers/ConfigHelper.java` | Yes | `AltoClef` (`Settings.load`, `:183`), tasks via `getModSettings()` | LIVE | none | file read from `<run>/altoclef/altoclef_settings.json` (1.16.1 also has a stale root-level copy, mtime 09-20) |
| Baritone settings (hard-coded) | `AltoClef.initializeBaritoneSettings()` `:345` | Yes | `AltoClef` init | LIVE | — | second config source (e.g., `allowParkour=false`) |
| `SpeedrunOpt` constants | `tasks/speedrun/testrun2/SpeedrunOpt.java` | Yes | T2 tasks | LIVE | — | third config source (compile-time constants for `@testrun2`) |
| `speedrunMoverPreference` | `Settings.java:490` | Yes | `SpeedrunBeatMinecraftTask` | not read by `@testrun2` | — | |

## 7. Build / jars

| Item | Location | Evidence |
|---|---|---|
| 1.16.1 Baritone jar used by `:1.16.1` | `altoclef/libs/baritone-unoptimized-fabric-1.16.1.jar` (modified vs HEAD) | md5 `145e376c…` = `Ostinato-1.16.1/dist/baritone-unoptimized-fabric-1.16.5.jar` (built 09-20 17:04 from dirty worktree); HEAD blob md5 `9f60ce44…`, 739,203 B |
| 1.21.11 Baritone jar picked locally | `Ostinato/dist/baritone-unoptimized-fabric-1.17.0-7-g9bb1b309-dirty.jar` | Gradle config log; built 09-17 from commit `9bb1b309` (pre-`99ee67ad`) |
| Tungsten jars | `altoclef/libs/tungsten-fabric-ALPHA-1.6.0-1.16.1-SNAPSHOT.jar` (modified), `…-1.21compat-SNAPSHOT.jar` | Gradle config log |
| Stale jar | `Ostinato/dist/baritone-unoptimized-fabric-ostinato-1.16.1.jar.stale-20260916` | not matched by globs |
| Untracked TC files touching build | `AutoWorldCreateMixin.java` (breaks non-1.16.1 compiles), `AutoWorldLoadMixin.java`, 2 accessor mixins, `util/AutoWorldState.java`, `altoclef.mixins.json` (modified) | `git status` |

## 8. Command outputs referenced

All raw logs are in the session scratchpad and were not added to the repo. Summary:

```
tasks --all                      exit 0   31s
:1.16.1:compileJava              exit 0   14s  (UP-TO-DATE)
:1.21.1:compileJava              exit 1   14s  AutoWorldCreateMixin.java:9 package net.minecraft.util.registry does not exist
:1.21.11:compileJava             exit 1   33s  same (preprocessed copy)
:1.16.1:compileTestJava          exit 1   12s  via :1.16.5:compileJava — AutoWorldCreateMixin.java:9 cannot find symbol
:1.16.1:dependencies testCompileClasspath/testRuntimeClasspath → 0 junit/jupiter matches
Ostinato ./gradlew test          exit 0  106s  8 suites / 49 tests / 0 failures / 0 errors
```
