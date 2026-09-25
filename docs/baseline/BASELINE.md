# TenorClef + Ostinato — Phase 0 Baseline

Audit date: 2026-09-24. Scope: Phase 0 only (no source/build/test/CI changes).
Supporting detail: [`inventory.md`](inventory.md).

Labels used: **Verified** (I ran it or read it at the checkout) · **Reported** (artifact/doc evidence, not
observed by me) · **Reported-by-owner** · **Contradicted** · **Unknown** · **ABSENT FROM HEAD** · **UNTESTED HERE**.

> **Headline.** The owner's live entry (inferred: `@testrun2` on 1.16.1) does reach the Phase-2
> `MovementEngineAdapter`, but **every Baritone jar available to this machine lacks the Ostinato
> MovementEngine classes**, so all of its travel falls back to `CustomGoalProcess`. TenorClef's 21 JUnit test
> classes **cannot compile** because JUnit is not on any test classpath. The working tree additionally
> **breaks the required 1.21.1 CI gate** via one untracked mixin file.

---

## 1. Repository State

| | TenorClef (`altoclef/`) | Ostinato tip (`Ostinato/`) | Ostinato 1.16.1 (`Ostinato-1.16.1/`, worktree of Ostinato) |
|---|---|---|---|
| Remote | `vexrypt-rgb/tenorclef` | `vexrypt-rgb/Ostinato` | same repo |
| Branch | `feat/tungsten-1161-fullport` | `feat/movement-engine` | `build/ci-noise-cleanup-tests` |
| HEAD | `873e4d79f2bbd506fc3a467850734a4dd7cabce0` (`873e4d79`) | `99ee67ade114c34a7cf86e0279c4fef8dc8257f1` (`99ee67ad`) | `f5472de3567976abd26f8cbe274276caf47e1435` (`f5472de3`) |
| HEAD date / subject | 2026-09-20 "ship full 1.16.1 physics A* port" | "add MovementEngine API on IMovementBackend precursor" | "continue-on-error for flaky 1.16.1 Tests job" |
| Working tree | **Dirty**: 50 tracked files modified (+4203/−340), 20+ untracked paths | Clean | Dirty: 7 files (+299/−165) incl. Gradle wrapper, `MovementPillar.java` |
| Local `main` | — | `60cd3e84` (no `IMovementEngine`) | — |

Verified via `git rev-parse`, `git status --porcelain`, `git diff --stat`.

**TenorClef working-tree vs HEAD matters.** Uncommitted edits include +1388 lines in
`ModernSpeedrunTask.java`, +642 in `T2Codes.java`, +551 in `HolePillar.java`, plus untracked
`AutoWorldCreateMixin.java`, `AutoWorldLoadMixin.java`, `RuinedPortalFinishTask.java`, `T2Deadman.java`,
`T2Trace.java`. `runClient` builds the working tree, so **this audit traces the working tree** and flags
HEAD differences where they change a conclusion. The 1.16.1 Baritone jar in `libs/` is also modified
vs HEAD (see §19).

Modules (TenorClef): one source tree `src/` preprocessed (ReplayMod preprocessor `c2041a34ae`) into 14
version projects `:1.21.11 … :1.16.1` (`settings.gradle.kts`, `root.gradle.kts`). `versions/mainProject`
= `1.21.1`. Ostinato tip: root + `:fabric :forge :neoforge :tweaker`
(`available_loaders`, `gradle.properties`); MC `1.21.11`, `java_version=21`.

---

## 2. Build and Toolchain

**Toolchain (Verified).** JDKs present: 17.0.20, 21.0.12, 25.0.4 (PATH `java` = 25). No JDK 8 found.
TenorClef: Gradle wrapper **9.4.1**, Loom 1.15.5, `options.release = 21`; `gradle.properties.local` pins
the daemon to JDK 21. Ostinato tip wrapper resolves Gradle 8.14.4. Ostinato-1.16.1 wrapper = Gradle 4.9,
CI uses JDK 8. All TenorClef/Ostinato-tip commands below ran with `JAVA_HOME=jdk-21.0.12`.

**Commands executed (Verified, 2026-09-24 ~19:58–20:02 local).**

| # | Repo | Command | Exit | Result | First meaningful error | Classification |
|---|---|---|---|---|---|---|
| 1 | TC | `./gradlew tasks --all` | 0 | 28 `runClient` tasks (one per version, incl. aggregates) | — | — |
| 2 | TC | `./gradlew :1.16.1:compileJava` | 0 | `UP-TO-DATE` (current sources already compiled) | — | **passes** |
| 3 | TC | `./gradlew :1.21.1:compileJava` | **1** | 1 error | `src/main/java/adris/altoclef/mixins/AutoWorldCreateMixin.java:9: package net.minecraft.util.registry does not exist` | **source defect — untracked working-tree file** |
| 4 | TC | `./gradlew :1.21.11:compileJava` | **1** | 1 error (same file, preprocessed copy) | same | same |
| 5 | TC | `./gradlew :1.16.1:compileTestJava` | **1** | blocked: depends on `:1.16.5:compileJava`, which fails | `AutoWorldCreateMixin.java:9: cannot find symbol RegistryTracker` | same |
| 6 | TC | `./gradlew :1.16.1:dependencies --configuration testCompileClasspath` / `testRuntimeClasspath` | 0 | **0 lines matching junit/jupiter** | — | **configuration defect** (see §15) |
| 7 | Ost tip | `./gradlew test` | 0 | root `:test` ran **8 suites, 49 tests, 0 failures, 0 errors**; `:fabric/:forge/:neoforge/:tweaker:test` NO-SOURCE | — | passes |
| 8 | Ost 1.16.1 | not run | — | JDK 8 required (Gradle 4.9 + CI `java-version: '8'`); not installed | — | **environment limitation → UNTESTED HERE** |

Notes:
- #3/#4/#5: `AutoWorldCreateMixin.java` is **untracked** (`?? src/main/java/.../mixins/AutoWorldCreateMixin.java`)
  and imports the 1.16-only `net.minecraft.util.registry.RegistryTracker` with no preprocessor guard.
  It is **ABSENT FROM HEAD**, so committed HEAD is not affected by it. Whether committed HEAD compiles on
  1.21.1 is **Unknown** here (I did not create a clean checkout; `gh` CLI is not installed, so GitHub CI
  status for `873e4d79` is Unknown). javac reported exactly one error, so no other error was surfaced in
  the working tree for those modules.
- #2 is `UP-TO-DATE`: Gradle's input check confirmed the current working-tree sources were already
  compiled successfully; it was not recompiled in this session.

**CI gates (Verified from `.github/workflows/gradle.yml` at the working tree; file not modified vs HEAD).**

| Job | Task | Blocking? |
|---|---|---|
| `build-1211` | `:1.21.1:compileJava` | **Required** |
| `build-1161` | `:1.16.1:compileJava` (requires committed `libs/baritone-unoptimized-fabric-1.16.1.jar`) | **Required** |
| `build-12111` | builds Ostinato **`main` from GitHub**, then `:1.21.11:compileJava` | `continue-on-error: true` |

No TenorClef CI job runs `test`. Ostinato tip CI runs `./gradlew test` (JDK 21) and `build`.
Ostinato-1.16.1 CI runs `./gradlew test` on JDK 8 with `continue-on-error: true`.

---

## 3. Runtime Status

**Runtime status = UNTESTED HERE.** I did not launch Minecraft. (No Java process was running when checked.)

Owner-stated play command: **none stated in this session.** Per brief §16 the live entry is therefore
inferred and labelled **Reported**:

- `versions/1.16.1/run/altoclef/altoclef_settings.json` (mtime 2026-09-24 06:31) contains
  `"autoRunCommand" : "testrun2"`; `AltoClef.maybeFireAutoRunCommand()` executes it once in-world.
- `versions/1.16.1/run/logs/` holds 8 sessions from 2026-09-23/24. Aggregated (Reported artifacts):
  **11** "Loading Minecraft 1.16.1" lines, **22** `TESRUN2 start`, phase heartbeats reaching
  `BOOTSTRAP/IRON/PORTAL/NETHER` (no `EYES/STRONGHOLD/END` seen).
- `versions/1.21.1/run/logs/latest.log` last written 2026-09-17; `1.16.5` 2026-09-16. `1.21.1` run
  settings have no `autoRunCommand`.

**Reported live entry used for §§5, 20, 21:** `@testrun2` on **MC 1.16.1**, launched via
`:1.16.1:runClient` (task exists — Verified), auto-fired by `autoRunCommand`. If the owner states a
different command, §§20–22 must be redone against it.

`@goal`, `@agent`, `@ado`, `@bench`, `@tgoto` are registered commands (`AltoClefCommands.java`, Verified);
whether they launch successfully is UNTESTED HERE.

---

## 4. Architecture as Actually Implemented

```
TaskRunner.tick()  (AltoClef.onClientTick, every client tick)
  ├─ for every active TaskChain: chain.getPriority()        ← MobDefenseChain runs doForceField/KillAura here
  └─ highest-priority chain.tick()
       ├─ UserTaskChain (priority 50)  ← @testrun2 lives here
       ├─ MobDefenseChain (0/50–80/65/70; −∞ if mobDefense=false)
       ├─ UnstuckChain (55), FoodChain, MLGBucketFallChain, WorldSurvivalChain,
       │   DeathMenuChain, PlayerInteractionFixChain, PreEquipItemChain
ThreatMonitor.tick()  (every tick; effects only on @goal GoalManager)
```

- **WHAT (TenorClef)** is, on the live path, a single 2,773-line hand-written phase machine
  (`ModernSpeedrunTask`: `BOOTSTRAP → LOOT → IRON → PORTAL → NETHER → EYES → STRONGHOLD → END`) with
  several watchdog overlays (`T2Brain/T2Probe/T2Solve`, freeze/water/surface bail, `stick()`).
  The Phase 7 planner (`GoalManager/PlanExecutor`) is **not** used by it.
- **HOW (Ostinato)** on the live path is **classic Baritone** (`CustomGoalProcess`, `MineProcess`,
  `ExploreProcess`, builder/input override) reached both through the `MovementController` facade and via
  ~198 direct `getClientBaritone()` calls. Tungsten is loaded as a mod on 1.16.1 but is not driving
  `@testrun2` travel (see §8).

---

## 5. Live Runtime Paths

Live entry: **`@testrun2` on 1.16.1 (Reported).** All hops below are Verified by source reading.

```
AltoClef.maybeFireAutoRunCommand()  → CommandExecutor "testrun2"
  → Testrun2Command.call()                         commands/Testrun2Command.java:19
  → AltoClef.runUserTask(new ModernSpeedrunTask()) AltoClef.java:624 → userTaskChain.runTask(...)
  → TaskRunner.tick() selects UserTaskChain (priority 50) when no chain outranks it
  → ModernSpeedrunTask.onTick()                    tasks/speedrun/testrun2/ModernSpeedrunTask.java:502
  → onTickInner()                                  :619
       ├─ T2Brain.help(...)                        :717   (watchdog replacement child)
       ├─ SurfaceBailTask / WaterBailTask / unstickCraft / portalWatch / ironWatch overlays
       ├─ combat override: FightNearbyTask.hostiles() → AnyWeaponCombatTask   :1040
       └─ taskFor(phase) → bootstrap()/iron()/portal()/nether()/eyes()/stronghold()/end()   :1069
            children (count of construction sites in ModernSpeedrunTask): TimeoutWanderTask 13,
            TaskCatalogue.getItemTask(...) 27, EnterNetherPortalTask 5, SurfaceBailTask 4,
            ConstructNetherPortalBucketTask 3, GetToBlockTask 2, TungstenMoveTask 2 (wraps GetToBlockTask),
            CollectBlazeRodsTask 2, MineAndCollectTask 1, KillEnderDragonWithBedsTask 1, others 1–2 each
```

**Travel leaf used by the live tree:**
```
GetToBlockTask.onTick()                            tasks/movement/GetToBlockTask.java:113 → super.onTick()
  (useMovementEngine() == true                     :49)
→ CustomBaritoneGoalTask.onTick()
     progress stall → failWithRecovery(TIMEOUT|NO_PATH)   tasks/movement/CustomBaritoneGoalTask.java:183,194
     → mod.getMovement().ensureGoalAndPath(cachedGoal)    :208
→ AdapterMovementController.ensureGoalAndPath()    control/AdapterMovementController.java
→ MovementEngineAdapter.ensureGoalAndPath()        movement/MovementEngineAdapter.java
     probeClasses(): Class.forName("baritone.movement.MovementBackends", "baritone.api.movement.MovementGoal",
                                   "baritone.api.movement.IMovementEngine", "baritone.api.movement.PathResult")
     → absent in every available jar (§8) → bari.getCustomGoalProcess().setGoalAndPath(goal)
```
Runtime artifact: `child=GetToBlockTask` appears 152 times in the 1.16.1 heartbeats (Reported).

**Parallel chains live during `@testrun2` (Verified wiring + owner settings Reported):**
`MobDefenseChain` (owner settings `mobDefense: true`, `killOrAvoidAnnoyingHostiles: true`,
`forceFieldStrategy: SMART`), `UnstuckChain`, `FoodChain`, `MLGBucketFallChain`, `WorldSurvivalChain`.

---

## 6. Existing Infrastructure (live classification relative to `@testrun2` on 1.16.1)

| System | Status | Why |
|---|---|---|
| `Task / TaskChain / TaskRunner / UserTaskChain` | **LIVE** | Hosts `@testrun2`. |
| `ModernSpeedrunTask` + `testrun2/*` watchdogs | **LIVE** | The live task tree. |
| `MovementController` / `AdapterMovementController` / `MovementEngineAdapter` | **LIVE (fallback branch only)** | Called from `GetToBlockTask`; engine probe fails → `CustomGoalProcess`. |
| Ostinato `IMovementEngine`/`HybridMovementEngine`/`MovementGoal`/`PathResult` | **ABSENT** from every runtime jar; present only in Ostinato source at `99ee67ad` / `origin/main` | §8 |
| `TungstenMovement` / `TungstenBridge` | **LIVE for status/cancel only** | `@testrun2` touches `TungstenMovement.statusLine()` (log) and `.cancel()` via adapter fallback; never starts Tungsten travel. |
| `RecoveryManager` / `RecoveryDecision` / `TaskResult` / `FailureReason` | **LIVE (local effect)** | `GetToBlockTask` consults it; decision only gates wander in `CustomBaritoneGoalTask`; nothing upstream consumes ABORT/FAILURE (§10). |
| `WorldKnowledge` / `AltoClefWorldKnowledge` / `KnowledgeFact<T>` | **LIVE (narrow)** | `GetToBlockTask` reads `blockPresentFact` + `getWorld()`; `CustomBaritoneGoalTask.isFinished` reads `getWorldKnowledge().getPlayer()`. |
| `ThreatMonitor` / `ThreatSignalCollector` | **GOAL-ONLY** | Computed every tick; effects only on `GoalManager` (`@goal/@bench/@agent`) and benchmark counters. |
| `GoalManager` / `PlanExecutor` / `PlanRunnerTask` / `SimplePlanner` | **GOAL-ONLY** | Reached from `@goal`, `@bench`, `@agent`. |
| `adris.altoclef.agent` (`@agent`, `@ado`) | **GOAL-ONLY** | Separate entry (`AgentLoopTask`). |
| `LiveBenchmarkSession` / `@bench` | **GOAL-ONLY** (counters are no-op unless `@bench` started) | |
| `BenchmarkHarness` / `MockScenarios` (4 scenarios) | **TEST-ONLY** | Callers are tests + `BenchmarkJson`; tests cannot compile (§15). |
| `CoreServices` (`getCoreServices()`) | **DEAD** relative to runtime | Only referenced inside `AltoClef.java` + test. |
| `MobDefenseChain` / `KillAura` | **LIVE** (parallel chain) | Settings enable it; `doForceField` runs inside `getPriority()` every tick. |
| `AnyWeaponCombatTask` / `WeaponPicker` / `AttributeSwap` | **LIVE** | `ModernSpeedrunTask:1040`. |
| `EarlyOverworldSpeedrunTask`, `SpeedrunBeatMinecraftTask`, `BeatMinecraftTask` | **not on this path** (other commands) | |

---

## 7. Baritone / AltoClef Coupling (Verified counts, `src/main/java`, working tree, 652 files)

| Metric | Value |
|---|---|
| Files with any `import baritone.` | **63** |
| … of which import `baritone.api.*` | 61 |
| … files importing non-API `baritone.*` (`baritone.altoclef/pathing/utils/process/behavior/…`) | **13** |
| Top import packages | `baritone.api.utils` 50, `baritone.api.pathing` 45, `baritone.api` 8, `baritone.pathing.movement` 6, `baritone.utils` 3, `baritone.api.schematic` 3, `baritone.altoclef` 2 |
| `AltoClef.getInstance()` | **334** occurrences in **132** files |
| `getClientBaritone()` | **198** occurrences in **45** files |
| `getCustomGoalProcess()` | **32** occurrences in **13** files (3 inside the adapter) |
| `getMovement()` | **8** occurrences in 3 files |
| Tasks that opt into the adapter (`useMovementEngine()==true`) | **1** (`GetToBlockTask`) of 14 `CustomBaritoneGoalTask` subclasses; plus `GetToEntityTask` calls `getMovement()` directly |

God-object assessment (evidence): a static singleton reached from 132 files and a 686-line `AltoClef`
exposing trackers, Baritone, settings, chains and facades. The Phase 3 `getMovement()` facade has 8
call sites versus 198 `getClientBaritone()` call sites. This is a factual description; no decoupling is recommended in Phase 0.

---

## 8. Movement Control Planes

| Plane | Owner | Entry | Reached from `@testrun2`? | Backend actually used |
|---|---|---|---|---|
| `CustomGoalProcess.setGoalAndPath` (direct) | Baritone | 13 TC files, 13 `CustomBaritoneGoalTask` subclasses with `useMovementEngine()==false` | **Yes** (e.g., `GetToYTask`, `RunAway*` via `MobDefenseChain`) | Baritone |
| `MovementController → MovementEngineAdapter` | TenorClef | `GetToBlockTask`, `GetToEntityTask` | **Yes** (`GetToBlockTask`) | **Baritone via fallback** (probe fails) |
| Ostinato `IMovementEngine → HybridMovementEngine` | Ostinato | `MovementBackends.engine(IBaritone)` | **No** — classes absent from runtime jar | n/a |
| Ostinato `CustomGoalProcess` Tungsten hand-off (`MovementBackends.preferTungstenTravel`) | Ostinato | inside `CustomGoalProcess` at tip | **No on 1.16.1** (Ostinato-1.16.1 has no `baritone/movement/*`) | n/a |
| `TungstenMovement / TungstenBridge / TungstenGotoTask` | TenorClef | `@tgoto`, `TungstenFollowTask`, manhunt, `SpeedrunBeatMinecraftTask` | **No** (only `statusLine()`/`cancel()`) | Tungsten when used elsewhere |
| Mine/Explore/Builder processes | Baritone | `MineAndCollectTask`, `TimeoutWanderTask` (ExploreProcess), `PlaceBlockTask`, … | **Yes** | Baritone |
| Raw input overrides (`McCompat.setMove`, `InputControls`, `setInputForceState`) | TenorClef | `ModernSpeedrunTask:744`, `T2Solve:201`, `CustomBaritoneGoalTask` portal handling | **Yes** | Vanilla input |

**Jar inspection (Verified with `unzip -l`).**

| Jar | `baritone/movement/MovementBackends` | `api/movement/IMovementEngine`, `MovementGoal`, `PathResult` |
|---|---|---|
| `altoclef/libs/baritone-unoptimized-fabric-1.16.1.jar` (used by `:1.16.1`) | absent | absent |
| `Ostinato/dist/baritone-unoptimized-fabric-1.17.0-7-g9bb1b309-dirty.jar` (picked by `:1.21.11`) | present | **absent** |
| cabaletta `baritone-unoptimized-fabric:1.21.1` (Maven, `:1.21.1`) | Unknown (not a local file); not an Ostinato build | Unknown |

The adapter requires **all four** classes plus `MovementBackends.engine(IBaritone)`; therefore the engine
branch cannot activate for `:1.16.1` or the locally staged `:1.21.11` jar. For 1.21.1 it is Unknown but no
evidence of the engine exists there. Log corroboration: 0 occurrences of
`"MovementEngineAdapter: Ostinato MovementEngine classes present"` in 72,276 lines of 1.16.1 logs (Reported).

Classification: the planes are **layered** (adapter → fallback → Baritone), with the Ostinato engine
**disconnected** from every runtime in this workspace, and the TC Tungsten plane **disconnected** from
`@testrun2`.

**`movementBackend = baritone|tungsten|auto`** (Ostinato tip `Settings.java:1684`, default `"auto"`):
`MovementBackends.current()` returns Tungsten for `TUNGSTEN`/`AUTO` only if
`TungstenMovementBackend.isAvailable()` (reflection on `kaptainwutax.tungsten.*`), else Baritone. This logic
exists only at Ostinato tip; it is not in the 1.16.1 jar and is not reachable from `@testrun2`.

---

## 9. Combat

Weapon **selection/equip** implementations found (Verified):

| # | Implementation | Policy | Live under `@testrun2`? |
|---|---|---|---|
| 1 | `AbstractKillEntityTask.bestWeapon/equipWeapon` (`tasks/entity/AbstractKillEntityTask.java:47,60`) | Best **sword only** (netherite→wooden); if none, keep current hand | **Yes** — via `MobDefenseChain.scopedKillTask → KillEntitiesTask` (`MobDefenseChain.java:387`) and `KillAura.attack(…, true)` (`KillAura.java:133,166`, SMART/DELAY strategy) |
| 2 | `KillAura.attack(…, equipSword=false)` → `SlotHandler.forceDeequipHitTool()` | **De-equip tools**, hit with non-tool | Only for `FASTEST` strategy; owner uses `SMART` → effectively not live |
| 3 | `WeaponPicker.dps` + `AnyWeaponCombatTask.equipBest` (`testrun2/combat/AnyWeaponCombatTask.java:92,162`) | **Max damage×speed** over whole inventory, incl. axes/picks/shovels | **Yes** — `ModernSpeedrunTask:1040` |
| 4 | `AttributeSwap.plan/equip` (same task, `:88–137`) | charger/hitter item swap when `SpeedrunOpt.ATTRIBUTE_SWAP` | **Yes** (conditional) |
| 5 | `MobDefenseChain.getBestMeleeAttackDamage` (`:483`) | sword **or axe** damage, for fight/flee gate only (not equip) | **Yes** (decision input, not equip) |
| — | `EarlyOverworldSpeedrunTask`, `BeatMinecraftTask`, `KillEnderDragonTask`, manhunt, `RavageRuinedPortalsTask` | other routes | not on this path |

Conflict analysis: #1 and #3 are both live and **can** disagree when the inventory holds a sword and an
item with higher `WeaponPicker` DPS (e.g., stone sword 8.0 vs iron axe 8.1; wooden/golden sword 6.4 vs
stone axe 7.2). `doForceField` runs inside `MobDefenseChain.getPriority()` every tick (`:264`), so #1 can
act in the same tick that the user chain runs #3. **Observed impact: none measured.** In the 1.16.1 logs the
`eq=` heartbeat field never shows a sword or axe (top values: `air` 2834, `iron_pickaxe` 1655,
`stone_pickaxe` 1417, …) and "combat: equipping sword" never appears. `OVERNIGHT_NOTES.md` was treated as
historical only and not relied on.

---

## 10. Recovery

Layers present on the live path (all Verified in source):

1. **`CustomBaritoneGoalTask`** progress checker → `failWithRecovery` → `RecoveryManager.DEFAULT`
   (`NO_PATH/TIMEOUT`: ALTERNATE_PATH, RETRY, RETRY, then ABORT). Non-terminal → return `TimeoutWanderTask`;
   terminal → log "Progress retries exhausted, aborting goal.", reset checker, return `null` **for one tick**,
   then keep pathing. `lastFailure.retryCount` is only cleared by `Task.reset()`/`succeed()`, so after
   exhaustion every later stall of the same task instance is immediately ABORT (no further wander).
2. **Consumers of the decision:** only (1) itself and `TaskRunner` (status string). `ModernSpeedrunTask`
   never reads `getLastResult()/getLastRecovery()`; "ABORT" does not abort the goal task.
3. **`ModernSpeedrunTask` overlays:** `T2Brain/T2Solve` (replacement child, e.g., `TimeoutWanderTask`,
   `cancelEverything()` at `T2Solve.java:468`), XZ-freeze detector (E98), `stick()`, `SurfaceBailTask`,
   `WaterBailTask`, `portalWatch`, `ironWatch`, `DeathRecycleTask`, `ResetSignal` reroll.
4. **`UnstuckChain`** (priority 55) and `GetToBlockTask` stale-finished detection (`fail(TIMEOUT)`).

Classification: **layered and partially disconnected** — `RecoveryManager` is live but advisory; the
effective recovery policy for `@testrun2` is the T2 overlay stack.
Runtime artifact (Reported): 1.16.1 logs contain **75** "Progress retries exhausted" vs **9**
"Failed to make progress on goal, wandering (ALTERNATE_PATH|RETRY …)".

---

## 11. World Knowledge

- `WorldKnowledge` (interface) / `AltoClefWorldKnowledge` (impl, constructed `AltoClef.java:175`) /
  `KnowledgeFact<T>` / `KnowledgeFactCache` exist (Verified).
- Live reads from `@testrun2`: `GetToBlockTask` (`blockPresentFact(pos, NETHER_PORTAL)` then a direct
  world-read fallback), `CustomBaritoneGoalTask.isFinished` (`getPlayer()`), `GetToEntityTask`.
- `ModernSpeedrunTask` itself reads `mod.getPlayer()` (63×), `getBlockScanner()` (12×), `getWorld()`,
  `getItemStorage()`, `getEntityTracker()` — i.e., **the legacy trackers are authoritative** for the live agent.
- No stale-plan invalidation exists on the live path (the planner is GOAL-ONLY).

---

## 12. Goals and Planning

`GoalManager extends PlanExecutor`; `PlanRunnerTask` ticks it; `SimplePlanner` + `AcquireItemGoal` +
`CatalogueInventoryView`. Constructed only by `GoalCommand` (`@goal`), `BenchCommand` (`@bench goal`),
`AltoClefAgentRuntime` (`@agent`). **GOAL-ONLY.** `ModernSpeedrunTask` uses its own `Phase` enum and
`decide()` (`:1096`).

## 13. Threat and Survival

`ThreatMonitor.tick(ThreatSignalCollector.collect(this))` runs every in-game tick (`AltoClef.java` tick loop).
Effects: `LiveBenchmarkSession.noteThreat` (no-op unless `@bench`), and `applyToGoalManager` only if
`GoalCommand.getActiveManager() != null`. **GOAL-ONLY** — computed but has no effect on `@testrun2`.
Live survival behavior for `@testrun2` comes from `MobDefenseChain`, `FoodChain`, `MLGBucketFallChain`,
`WorldSurvivalChain`, and T2 overlays (`WaterBailTask`, `SurfaceBailTask`, `SurviveTick`).

## 14. Agent Protocol

`adris.altoclef.agent` (8 classes): `AgentProtocol/AgentRequest/AgentResponse/AgentJson/AgentRequestHandler/
AltoClefAgentRuntime`. Entries: `@agent` → `AgentLoopTask` (file-based JSON, whitelisted actions),
`@ado` queues one action. **GOAL-ONLY**; no expansion recommended.

---

## 15. Benchmarks and Tests

**TenorClef tests (Verified).** `src/test/java`: 21 test classes + 2 fakes, JUnit **5** (`org.junit.jupiter`),
≥116 `@Test` methods. `build.gradle` declares **no** JUnit dependency and **no** `useJUnitPlatform()`;
`:1.16.1` `testCompileClasspath` and `testRuntimeClasspath` resolve **zero** junit/jupiter artifacts.
⇒ These tests cannot compile via Gradle, and no CI job invokes them. (Additionally, in the current working
tree `:1.16.1:compileTestJava` is blocked earlier by the untracked mixin, §2 #5.)

Tests that exercise code on the `@testrun2` path: `RecoveryManagerTest` (9), `TaskResultMapperTest` (7),
`TaskFailureTest` (4), `TaskPropagationTest` (4), `KnowledgeFactTest` (9), `KnowledgeFactCacheTest` (4),
`util.helpers.*SelectorTest`/`NearbyPlacePenaltyTest` (14, helper classes — live reachability not traced).
Tests of GOAL-ONLY/TEST-ONLY code: agent, benchmark, planner, threat, core.

**Benchmarks.** `BenchmarkHarness` + 4 `MockScenarios` (`acquireSuccess`, `pathFailThenRecover`,
`threatCriticalAbort`, `deathAbort`) — mocks, no Minecraft, TEST-ONLY, and currently not runnable.
`@bench` wraps a `GoalManager` goal plus `LiveBenchmarkSession` counters; it does not exercise `@testrun2`.
Not evidence of live-agent quality.

**Ostinato tip tests (Verified run).** 8 suites / 49 tests pass, including `MovementBackendSelectorTest` (8).
No test exercises `HybridMovementEngine` or `IMovementEngine`.

---

## 16. Known Defects (evidence-backed only)

| ID | Defect | Evidence | Scope |
|---|---|---|---|
| D1 | TenorClef JUnit tests cannot compile/run: no JUnit on any test classpath | §2 #6; `build.gradle` has no `testImplementation`/`useJUnitPlatform` | committed HEAD + working tree |
| D2 | Working tree fails required CI gate `:1.21.1:compileJava` (and `:1.21.11`, `:1.16.5`) | §2 #3–#5; untracked `AutoWorldCreateMixin.java:9` | **working tree only** (file ABSENT FROM HEAD) |
| D3 | Docs claim `GetToBlockTask/GetToEntityTask → MovementEngineAdapter → Ostinato IMovementEngine`; at runtime the engine is absent from every available jar and the adapter always falls back | `docs/ARCHITECTURE.md:103`, `docs/OSTINATO_BOUNDARY.md:17` vs §8 jar inspection | documentation vs runtime |
| D4 | `docs/BENCHMARKS.md:16` instructs "Run offline unit tests: `BenchmarkAggregationTest`, `MockScenariosTest`" — impossible per D1 | §15 | documentation |
| D5 | "Progress retries exhausted, **aborting** goal" does not abort; the task keeps pathing and only stops wandering | `CustomBaritoneGoalTask.java:183–199`, §10 | misleading log/comment; behavior impact not measured |

Not listed as defects (no measured impact): combat policy overlap (§9); 334 `getInstance()` calls; 14-node
preprocess chain.

---

## 17. Verified Metrics

| Metric | Value | Source |
|---|---|---|
| TC main Java files / test Java files | 652 / 23 | `find` |
| TC version modules | 14 (`1.21.11 … 1.16.1`) | `settings.gradle.kts` |
| TC `runClient` tasks listed | 28 | `tasks --all` |
| Files importing `baritone.` / non-API | 63 / 13 | grep |
| `AltoClef.getInstance()` | 334 in 132 files | grep |
| `getClientBaritone()` | 198 in 45 files | grep |
| Weapon-equip implementations (distinct policies) | 3 (+1 swap helper, +1 damage-gate) | §9 |
| Movement control planes | 7 (§8) | source |
| Task systems | 1 (AltoClef `Task/TaskChain/TaskRunner`); planner is a Task-hosted state machine | source |
| World-knowledge systems | 2 (legacy trackers — authoritative; `WorldKnowledge` facade) | source |
| Planner systems | 2 (`ModernSpeedrunTask` phase machine; `GoalManager/PlanExecutor`) | source |
| Recovery layers on live path | 4 (§10) | source |
| Benchmark scenarios | 4 mock | `MockScenarios` |
| TC test classes / `@Test` methods / runnable | 21 / ≥116 / **0** | §15 |
| Ostinato tip tests run / passed | 49 / 49 | §2 #7 |
| `ModernSpeedrunTask.java` size (working tree) / uncommitted additions | 2,773 lines / +1,388 | `wc`, `git diff --stat` |
| 1.16.1 run logs 09-23/24: MC launches / `@testrun2` starts | 11 / 22 (Reported) | log grep |

---

## 18. Unverified / Contradicted Claims

| Claim (source) | Status | Evidence |
|---|---|---|
| `@testrun2 → Testrun2Command → ModernSpeedrunTask → Task/TaskChain/TaskRunner` (brief §8) | **Verified** (source) | §5 |
| Package namespace `adris.altoclef` (brief) | Verified | |
| `WeaponPicker`, `AnyWeaponCombatTask` as top-level combat classes (brief §13) | Exist, but under `tasks/speedrun/testrun2/combat/` | `find` |
| Movement flows TenorClef → Ostinato public interface → Baritone/Tungsten (brief §10 question) | **Contradicted at runtime**: flows TenorClef adapter → **fallback** `CustomGoalProcess` | §8 |
| Ostinato `main` / 1.21.11 = hybrid Baritone/Tungsten engine (brief §17) | **Contradicted locally**: local `main` (`60cd3e84`) has `IMovementBackend/MovementBackends/TungstenMovementBackend` but no `IMovementEngine/HybridMovementEngine`; local branch `1.21.11` (`23723891`) has neither. `origin/main` (`ed157fb4`, last fetch) and HEAD `99ee67ad` have the engine. | `git ls-tree` |
| `movementBackend=auto` performs automatic selection (brief §12) | **Verified in Ostinato tip source only**; not present/reachable for 1.16.1 | §8 |
| 1.16.5 as possible owner play target (brief §17) | Unknown; last 1.16.5 run log 2026-09-16; `:1.16.5:compileJava` fails in working tree | §2 |
| TC 1.21.11 + Ostinato main playable | **Unknown** (compile fails in working tree; runtime UNTESTED HERE) | §2 #4 |
| Docs: GetToBlock/GetToEntity route through Ostinato MovementEngine | **Contradicted** (runtime) | D3 |
| Docs: benchmark unit tests are runnable | **Contradicted** | D4 |
| Prior `docs/PHASE0_AUDIT.md` (prior AI analysis) | Not used as evidence | brief §5 |

---

## 19. Version Scope

| Version | Evidence of role | Compile (working tree) | Runtime |
|---|---|---|---|
| **1.16.1** | Owner's active target (Reported: run settings `autoRunCommand=testrun2`, 8 log sessions 09-23/24, branch name, `libs/` jars modified); **required CI gate** | **passes** (UP-TO-DATE) | UNTESTED HERE; Reported runs reach NETHER |
| **1.21.1** | `versions/mainProject`; **required CI gate** ("primary") | **fails** (D2, working-tree only) | UNTESTED HERE; last log 09-17 |
| **1.21.11** | CI experimental (`continue-on-error`), paired with Ostinato `main` from GitHub | **fails** (D2) | UNTESTED HERE |
| 1.16.5 | run dir exists (last log 09-16); preprocess parent of 1.16.1 | **fails** (D2) | UNTESTED HERE |
| 1.21 / others | in preprocess chain | not run | UNTESTED HERE |

1.16.1 Baritone jar provenance (Verified by md5): `altoclef/libs/baritone-unoptimized-fabric-1.16.1.jar`
(modified, 749,257 B) is byte-identical to `Ostinato-1.16.1/dist/baritone-unoptimized-fabric-1.16.5.jar`
(built 2026-09-20 17:04 from the **dirty** Ostinato-1.16.1 worktree). The committed HEAD jar differs
(739,203 B). So the owner's runtime Baritone is **not reproducible from any commit**.

Required answers (§17): (1) CI compile gates = `:1.21.1:compileJava` and `:1.16.1:compileJava`;
`:1.21.11` non-blocking. (2) Owner play command: not stated by owner; Reported = `@testrun2` on 1.16.1 via
`autoRunCommand`. (3) TC 1.21.11 + Ostinato main: **Unknown** (compile-fails in this working tree; CI
result for HEAD Unknown; runtime UNTESTED HERE). Version-scope decisions are the owner's.

---

## 20. Exactly One Recommended First Change

**Class D — Make the existing TenorClef JUnit tests runnable, validated on `RecoveryManagerTest`.**

Concretely (for Phase 1, not done here): in `build.gradle`, add a JUnit 5 `testImplementation`
(+ `testRuntimeOnly` launcher) and `tasks.test { useJUnitPlatform() }`; no source or test edits.
Out of scope: adding a CI test job, fixing any test that then fails, the untracked mixin (D2).

**Precondition (owner action, not part of the change):** `:1.16.1:compileTestJava` depends on
`:1.16.5:compileJava`, which the untracked `AutoWorldCreateMixin.java` currently breaks. The change must be
verified on a tree where that file is committed with a version guard or not present (e.g., clean HEAD).

## 21. Live-Path Proof

```
Live entry:            @testrun2 on MC 1.16.1 via :1.16.1:runClient + autoRunCommand
                       (Reported from run settings/logs; owner has not stated a command)
Current call path:     Testrun2Command.call() → AltoClef.runUserTask() → UserTaskChain.runTask()
                       → TaskRunner.tick() → ModernSpeedrunTask.onTick()/onTickInner()
                       → taskFor(phase) returns GetToBlockTask (directly, or via TungstenMoveTask /
                         SurfaceBailTask / WaterBailTask / RuinedPortalFinishTask)
                       → Task.tick() → GetToBlockTask.onTick() → CustomBaritoneGoalTask.onTick()
                       → Task.failWithRecovery(TIMEOUT|NO_PATH)
                       → RecoveryManager.DEFAULT.apply() → decide() → decidePathish()
Changed component:     Gradle test configuration that compiles and runs the existing
                       RecoveryManagerTest (and the other 20 classes)
Exact method/file:     build.gradle (dependencies {} + test {}); target under test:
                       src/main/java/adris/altoclef/tasksystem/RecoveryManager.java  decide()/apply()
Proof target is reached: CustomBaritoneGoalTask.onTick() (CustomBaritoneGoalTask.java:183,194) calls
                       Task.failWithRecovery() (Task.java:237), which calls
                       getRecoveryManager().apply() → RecoveryManager.DEFAULT (Task.java:191–192,243)
Behavior affected:     whether a stalled GetToBlockTask wanders (ALTERNATE_PATH/RETRY → TimeoutWanderTask)
                       or stops wandering (ABORT) — Reported 9 vs 75 occurrences in owner logs.
                       The change itself does not alter runtime behavior; it makes this live policy
                       mechanically checkable before any future edit to it.
Verification method:   build + test: ./gradlew :1.16.1:test --tests "adris.altoclef.tasksystem.RecoveryManagerTest"
                       then ./gradlew :1.16.1:test; also confirm ./gradlew :1.16.1:compileJava and
                       :1.21.1:compileJava are unchanged.
Expected measurable result: before = compileTestJava cannot resolve org.junit.jupiter (0 tests run);
                       after = 9 RecoveryManagerTest tests executed with a pass/fail count in
                       versions/1.16.1/build/test-results/test/*.xml; total executed/failed counts for all
                       21 classes recorded as the first test baseline (failures are recorded, not fixed).
```

## 22. Evidence Supporting the Selection

- **A (unify duplicated live policy) — not eligible.** Two live equip policies exist (§9), but selection
  condition 3 (behaviorally meaningful limitation) is not met: across 22 Reported runs no sword or axe was
  ever equipped, so the conflicting inventory state was never reached. Recommending it would be based on
  source possibility, not measured behavior.
- **B (wire a facade) — not eligible.** The only facade held on the live path (`MovementController`) is
  already called. Its bypassed alternative is the Ostinato engine, which does not exist in the 1.16.1
  Ostinato line or any available jar (§8). Using it would require porting `IMovementEngine` into Ostinato-1.16.1
  (not small, not isolated). Switching other `CustomBaritoneGoalTask` subclasses to `useMovementEngine()`
  would produce identical runtime behavior on 1.16.1 (fallback to `CustomGoalProcess`), so there is no
  measurable effect. `ThreatMonitor`/planner are GOAL-ONLY with no live caller holding them (§11 test fails).
- **C (stop a misleading plane) — not eligible.** D3 is a documentation/runtime mismatch, but I found no
  evidence that it has caused specific engineering work or a wrong decision, which C strictly requires.
- **D — eligible.** D1 is Verified by executed classpath resolution. `RecoveryManagerTest` exercises
  `RecoveryManager.decide/apply`, reached from the Reported live entry through a fully traced path (§21).
  The change touches build configuration only, is isolated, preserves runtime behavior, and its success
  is a measured count of executed tests. It also establishes the measurement tool needed before any
  A/B-class change to live recovery or combat can be shown to preserve behavior.
- **Evidence still missing** (would change this ranking): an owner-stated play command; a measured
  occurrence of the combat conflict; GitHub CI results for `873e4d79`; a JDK 8 build of Ostinato-1.16.1.
