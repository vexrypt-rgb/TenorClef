# Fugue v1 — Dependency-Aware Sub-Task Coordination: Handoff

Status: **complete (Stages 0–11)**. All changes are uncommitted, for review.

Bottom line:
- Classification is **C, inconclusive**, under the pre-registered rules (§12.1). Primary A/B: Fugue 9/10 vs stock
  10/10, with one Fugue-only timeout from a shared water-escape stall.
- A pre-registered replicate shows parity. Pooled empty runs: 9/10 each, no consistent speed difference (§9.4).
- Planning costs about 0.2 ms per replan.
- Recommended next step: stall-escape recovery as Fugue's only plausible path to an improvement (§12.2).
 Evidence labels: **Verified** (read in the code or
shown by a test), **Measured** (live run, log named), **Reported** (from an earlier handoff, not re-checked),
**Contradicted**, **Unknown**.

## 1. Branches, HEADs, worktrees, preserved work

| Repo | Worktree | Branch | HEAD | Uncommitted at start |
|---|---|---|---|---|
| TenorClef | `C:\Users\redfa\Documents\MinecraftDev\TenorClef-main` | `main` | `983cf317` (Maze furnish bridge: busy() and cancel()) | 30 modified + 89 untracked paths (maze, Grace Note, threat, trackers, logs) |
| Ostinato | `C:\Users\redfa\Documents\MinecraftDev\Ostinato-ppe` | `feat/predictive-physics` | `1aed5cc2` (Merge PR #142 maze-tunnels) | 37 modified + 61 untracked paths (PPE, Presto, Legato, Maestro, Cadenza, handoffs) |

**Verified** (`git status --short`, `git log -1`). TenorClef also has a second worktree `polish/tenorclef` on
`workbuddy/polish-20261030` (same HEAD). Nothing was committed, pushed, merged, reset, stashed, cleaned or discarded.
No pre-existing modified file was edited except where listed in §13 (Files). Ostinato was **not modified**.

Other checkouts (`altoclef`, `TenorClef-strip-tungsten`, many `Ostinato-*` worktrees) are older and were not touched.

## 2. Stage 0 audit

### 2.1 Live entry points (Verified)

- Commands are registered in `AltoClefCommands.init`. `@get <item> [n]` → `TaskCatalogue.getItemTask` →
  `AltoClef.runUserTask` → `UserTaskChain` (priority 50). `@goal <item> [n]` → `PlanRunnerTask(GoalManager)`.
- `TaskRunner` ticks the highest-priority active `TaskChain`; survival chains (mob defense, food, world survival)
  interrupt the user chain via `onInterrupt` → `Task.interrupt` (not `stop`).
- `SingleTaskChain.onTick`: `if (mainTask.isFinished() || mainTask.stopped()) onTaskFinish()`.
- The owner's main live target is `@testrun2` on 1.16.1 (memory notes; Reported). Small-objective runs
  (`get iron_pickaxe`, seed 12345) are recorded in `docs/OBJECTIVE_RUNS.md` on 1.16.1: 3/3 finished in 110–115 s,
  judged by vanilla advancements (Reported, 2026-09-28).
- Supported MC modules: 26.3 is the default module (JDK 25 toolchain); 1.16.1 is the live SIM target with an
  auto-world harness (`AutoWorldCreateMixin`, `-Dtenorclef.seed`, `autoRunCommand`) and the committed
  Ostinato 1.16.1 jar (`libs/baritone-unoptimized-fabric-1.16.1.jar`). **Verified** (files present).

### 2.2 Existing outcome model (Verified) — reused, not duplicated

`tasksystem/TaskResult` {RUNNING, SUCCESS, FAILURE, CANCELLED, BLOCKED, RETRY}, `TaskFailure` (reason, message,
recoverable, retryCount), `FailureReason` (NO_PATH, RESOURCE_MISSING, TARGET_UNAVAILABLE, INVENTORY_FULL, DANGER,
PLAYER_DEAD, WORLD_CHANGED, TIMEOUT, BACKEND_FAILURE, PRECONDITION_FAILED, UNKNOWN), `RecoveryManager` (reason →
RETRY / ALTERNATE_PATH / WAIT / ABORT / ESCALATE with bounded counts), `Task.fail/blocked/succeed/absorbChildOutcome`.

### 2.3 Completion-semantics audit (Verified unless marked)

| Finding | Where | Consequence |
|---|---|---|
| `isFinished()==true` resolves to **SUCCESS** unless the task explicitly recorded FAILURE/CANCELLED/BLOCKED | `TaskResultMapper.resolve`, `Task.tick` legacy shim | Any task whose `isFinished` means "stopped trying" reports success. |
| 111 classes override `isFinished`; only 14 task files call `fail(`/`failWithRecovery(` and 9 call `blocked(` | grep over `src/main` | Most tasks have no failure channel; "finished" is the only terminal signal. |
| Give-up paths return `isFinished()==true` | e.g. `DivineTravelTask` (`detection == null \|\| chosen == null`), `GetOutOfWaterTask` (`player == null`), many testrun2 tasks (`return done`) | These read as SUCCESS to observers. |
| "User task FINISHED" is logged for stopped and cancelled tasks too | `UserTaskChain.onTaskFinish` (called from `cancel()` and on `stopped()`) | A log line "FINISHED" is **not** evidence of success; `docs/OBJECTIVE_RUNS.md` already judged by advancements. |
| `ResourceTask.isFinished` = item targets met in inventory (cursor excluded) — a real success predicate, but it calls `succeed()` as a side effect of being observed | `tasks/ResourceTask` | Good predicate for the supported family; the side effect means observing it changes state. |
| `CollectRecipeCataloguedResourcesTask` collects recipe materials one at a time, in `HashMap` key order, re-deriving needs every tick; `isFinished` is a cached flag reset when materials disappear | `tasks/resources/CollectRecipeCataloguedResourcesTask` | Stock iron-pickaxe acquisition is depth-first per recipe; demand is never aggregated across recipes. |
| `CollectFuelTask.isFinished` compares coal **items** with fuel **operations** (`coal >= targetFuel`) while collecting `ceil(targetFuel/8)` coal | `tasks/resources/CollectFuelTask` | Never "finishes" for ≥2 smelts; harmless only because `DoSmeltInFurnaceTask` re-checks fuel itself. Not changed (out of scope, no reproduction of harm). |
| `SmeltInFurnaceTask.isFinished = super \|\| _doTask.isFinished()`; `DoSmeltInFurnaceTask` does not override `isFinished` (false) | `tasks/container/SmeltInFurnaceTask` | Effectively the inventory predicate. OK. |
| `PlanRunnerTask` (Phase 7) advances a step on `sub.isFinished() && !failed` and verifies the **goal** by inventory; steps are one catalogue key each (`SimplePlanner`: one step for the whole item) | `planner/*` | Linear, no dependency graph; reusable pieces: `Goal`, `InventoryView`, `CatalogueInventoryView`. |
| Supported settings: smelting fuel is limited to coal/charcoal (`limitFuelsToSupportedFuels=true`) | `Settings` | Fuel is a real mining prerequisite (coal needs a wooden pickaxe). |

### 2.4 Navigation / Ostinato boundary (Verified)

Leaf tasks (`MineAndCollectTask`, `PickupDroppedItemTask`, `DoStuffInContainerTask` placing/opening stations) drive
Ostinato/Baritone processes (mine, goal, builder). Local recovery (stuck detection, timeouts, unreachable-block
blacklists, `TimeoutWanderTask`) lives in those tasks and in Ostinato. Fugue adds no movement path and no stuck
detector; it only reads inventory, the block scanner's known locations, and player position.

### 2.5 Ostinato context (Reported / Verified)

- `CADENZA_V21_HANDOFF.md` (Ostinato-ppe): Cadenza v2.1 complete, uncommitted; experimental flags default off.
  **Verified** the file and that it says so.
- "Full suite 1441 tests, 16 skipped, 0 failures": **Reported**. `build/v21-full-suite.log` ends `BUILD SUCCESSFUL`,
  but `build/test-results/test` was since overwritten by a 3-suite run (67 tests), so the counts cannot be re-derived.
- Fugue requires none of Cadenza, Maestro, Legato, Presto or PPE.

## 3. What Fugue v1 is (and is not)

Fugue is a small coordination layer **above** the existing task tree. It takes one objective (for example "hold 1
iron pickaxe") and works out every item still missing, aggregated over all recipes that need it. It then runs **one
stock catalogue task per missing item**, in dependency order, verifies each step, and recovers within bounds.
It does no mining, crafting, smelting, pathing or movement of its own. Those stay with `TaskCatalogue` tasks and
Ostinato, unchanged.

- Not a replacement for `@get`, `TaskCatalogue`, `ResourceTask` or the chain system. It does not change them.
- Not a planner over arbitrary goals: v1 supports exactly the iron-pickaxe family (§6).
- No ML, GOAP/HTN, GUI, scripting or background threads. Everything runs on the client tick through `Task.tick`.
- **Opt-in, disabled by default.** Only `@fugue <item> [count]` and `@fuguebench fugue ...` construct a `FugueTask`.
  Nothing in the default command, chain or settings path references it.

### 3.1 Entry points

| Entry | Path |
|---|---|
| `@fugue <item> [count]` | `commands/FugueCommand` → `FugueTask.live(item, count)` → `AltoClef.runUserTask` (user chain, priority 50) |
| `@fugue status` | Last objective: status, Task result, current node, last plan's nodes, metrics |
| `@fuguebench <stock\|fugue> [scenario:reps,...]` | `benchmark/FugueBench` (1.16.1 only; stub elsewhere) |
| Registration | Two lines in `AltoClefCommands.init` (the only edit to a pre-existing file) |

### 3.2 Files (all new unless noted)

```
src/main/java/adris/altoclef/fugue/
  FugueRecipe.java        recipe data (kind, inputs/op, output/op, station, pickaxe tier)
  FugueRecipeBook.java    v1 book (iron-pickaxe family), pickaxe tier helpers
  FugueWorldView.java     read-only world interface (count, stationNearby, distanceToSource)
  LiveFugueView.java      live impl over ItemStorageTracker + TaskCatalogue matches + BlockScanner
  FugueNode.java          one missing item: need, ops, deps, downstream
  FuguePlan.java          netted graph + ready() + summary
  FuguePlanner.java       BOM netting, ledger allocation, presence requirements, bounds
  FugueScheduler.java     deterministic ready-node selection
  FugueCoordinator.java   outcome contract, attempts, recovery, replanning, metrics (pure)
  FugueTask.java          Task wrapper: one stock catalogue task per node
src/main/java/adris/altoclef/commands/FugueCommand.java
src/main/java/adris/altoclef/commands/FugueBenchCommand.java
src/main/java/adris/altoclef/benchmark/FugueBench.java
src/main/java/adris/altoclef/AltoClefCommands.java      (modified: +2 registrations)
src/test/java/adris/altoclef/fugue/{FakeWorld,FuguePlannerTest,FugueCoordinatorTest,FugueTaskTest}.java
FUGUE_V1_HANDOFF.md
```

## 4. Outcome contract (Stage 1)

Fugue reuses `TaskResult` / `TaskFailure` / `FailureReason`. It adds no parallel enum.

| Situation | Fugue's reading |
|---|---|
| Node attempt starts | Fixes an **absolute** target: `held(item) + need` from the current plan |
| Held count ≥ target | Node **SUCCESS** (Fugue's own predicate, regardless of what the child says) |
| Child `isFinished()` but held < target | **Failed attempt**, reason UNKNOWN ("child finished holding x/y"). Never success. |
| Child explicit FAILURE, non-recoverable | Failed attempt with the child's reason, once it has persisted 40 ticks (immediately if the child is also finished) |
| Child FAILURE recoverable / RETRY | Left running: the child is recovering itself |
| Child BLOCKED | Failed attempt (same 40-tick persistence rule). If not retried, the objective ends **BLOCKED** (not FAILURE) |
| Child CANCELLED by someone else | Failed attempt (not success) |
| No rise in held count for 150 s of Fugue ticks | Failed attempt, TIMEOUT (watchdog; interrupted time is not counted) |
| Why the 40-tick grace | `Task.absorbChildOutcome` copies a grandchild's FAILURE up the tree for as long as it lasts, and clears it when the grandchild recovers. The stock user chain ends a task only on `isFinished()`/stop (`SingleTaskChain`), so stock rides through these. Smoke run 2 (§9.1) showed a stale pickup target (`GetToEntityTask`: "Follow target entity unavailable", ABORT) surfacing for a tick at a time while cobblestone mining kept progressing. Without the grace period, Fugue treated each of these as the end of the attempt. |
| Objective | **SUCCESS only when the goal count is held.** FAILURE/BLOCKED carry the exhausted node's reason |
| `Task.stop()` / `@stop` | CANCELLED (Task layer). The running child is stopped too (CANCELLED) |
| `Task.interrupt()` (survival chain) | Suspended; on resume the same node and attempt continue |

FugueBench also checks success itself: SUCCESS requires the task result **and** an iron pickaxe it sees in the
inventory. A cancel trial passes only when the result is CANCELLED.

## 5. Dependency graph and resource accounting (Stages 2–3)

`FuguePlanner.plan(goal, count, view)` does bill-of-materials netting:

1. **Ledger allocation.** Each held item is allocated once, to the first consumer that asks, in a fixed DFS order.
   Two recipes cannot both count the same two sticks. This allocation is the reservation: it is recomputed from
   real inventory at every replan, so nothing is held in a side table that could drift from the inventory.
2. **Aggregation.** All demand for an item lands on one node. Craft rounding surplus (2 planks give 4 sticks) goes
   to the next consumer before another operation is planned.
3. **Presence requirements.** Stations (`crafting_table`, `furnace`) are satisfied by one held, or by a reachable
   one within 40 blocks (the same rule as `CraftInTableTask` / `SmeltInFurnaceTask.getCostToMakeNew`). Pickaxe
   tiers are satisfied by any held pickaxe of that tier or higher. Otherwise the cheapest one is planned **once**.
4. **Fuel.** `ceil(totalSmelts / 8)` coal, computed incrementally per smelt node.
5. **Edges.** A node depends on an input only if that input needed production for it. `ready()` = nodes with no
   dependency left in the plan.
6. **Bounds.** Cycles (including tool cycles) are rejected with the cycle path. So are depth > 12 and more than 32
   nodes, each as an explicit plan error. A missing recipe is RESOURCE_MISSING.

Empty inventory, iron pickaxe: 12 nodes (log 3, planks 11, stick 6, crafting_table 1, wooden_pickaxe 1, cobblestone
11, coal 1, stone_pickaxe 1, furnace 1, raw_iron 3, iron_ingot 3, iron_pickaxe 1). **Verified** by
`FuguePlannerTest.emptyInventoryExpandsWholeChainWithAggregatedTotals`.

## 6. Scheduling, replanning, recovery (Stages 4–7)

- **One node at a time** (the bot has one body). Fugue gains from ordering and aggregation, not concurrency.
- **Selection** (only between nodes; a running node is never preempted for a cheaper one):
  - First: lowest static cost. Base costs: CRAFT_INV 1, CRAFT_TABLE 3 (+5 with no station nearby), SMELT 10 (+5),
    MINE 20 plus whole-block distance to the nearest known source block (unknown counts as 64).
  - Then: more transitive downstream nodes unlocked.
  - Then: same station as the previous node (batches table crafts).
  - Then: catalogue key.
- **Replanning** is event-driven. A fresh plan is netted from current inventory whenever no node is running: at
  start, after a node success, and after a failed attempt. A stolen or consumed input therefore re-opens its node
  (counted as `reopens`).
- **Recovery** works per node:
  - A failed attempt is retried only for a plausible reason:
    - The attempt made progress: always retried, up to 8 failed attempts per node in total. This also resets the
      node's no-progress failure count.
    - No progress, but the reason is transient (NO_PATH, TIMEOUT, TARGET_UNAVAILABLE, WORLD_CHANGED, DANGER,
      BACKEND_FAILURE): retried up to 2 consecutive no-progress failures.
    - This policy was revised after smoke run 2 and before the A/B (§9.1). The first version capped failures at
      4 even when every attempt made progress, and that exhausted a node that was steadily filling up.
  - Otherwise the node is exhausted and the objective ends FAILURE/BLOCKED with that reason.
  - The objective also has a budget of 60 node starts, to guard against livelock.
  - A retry always gets a fresh child instance.
  - No alternative recipes in v1. The only "alternative" is re-netting from whatever is now held.
- **Opportunism** (Stage 7) is limited to selection time: the nearest known source among ready mine nodes wins.
  Detours while a node is running were **not** implemented. Without live evidence that they pay, they would add
  preemption risk.

### 6.1 Supported / unsupported

Supported goals: `log, planks, stick, crafting_table, wooden_pickaxe, cobblestone, coal, stone_pickaxe, furnace,
raw_iron, iron_ingot, iron_pickaxe` (any count). `@fugue` refuses anything else and points to `@get`.
Unsupported: everything else. That includes charcoal fuel, alternative wood/stone recipes as separate nodes, the
nether or other dimensions, containers and chests as sources, and multiple bodies.

### 6.2 Integration boundaries

Fugue reads inventory (`ItemStorageTracker.getItemCount` over `TaskCatalogue.getItemMatches`), the
`BlockScanner` cache and the player position. It writes nothing to the world, settings, Baritone or Ostinato.
Child tasks are `TaskCatalogue.getItemTask(key, target)`, the same objects `@get` would build.

## 7. Live A/B protocol and decision criteria (recorded BEFORE any live run)

**Protocol.**
- Target: 1.16.1 SIM (owner's live target), `scripts/run-once.ps1 -Repo TenorClef-main`.
- One client launch per arm, both with `-Dtenorclef.seed=12345`. Trial *i* of either arm runs at the same spot:
  the *i*-th dry candidate on a line east of world spawn, 512 blocks apart, so every trial gets fresh terrain.
- Every trial: Peaceful, time 1000, fed and healed, inventory cleared and seeded per scenario, dropped items
  cleared, BlockScanner reset.
- Arms: stock = `TaskCatalogue.getItemTask("iron_pickaxe", 1)` (what `@get iron_pickaxe` runs);
  fugue = `FugueTask.live("iron_pickaxe", 1)`.
- Scenarios:
  - empty (nothing held) ×5
  - partial (crafting_table, stone_pickaxe, 2 sticks) ×1
  - full (3 iron_ingot, 2 sticks, crafting_table) ×1
  - steal (empty; all cobblestone removed once ≥8 held) ×2
  - cancel (empty; `stopTasks()` at 40 s) ×1
- Timeout: 7 game-minutes (8400 ticks). Prior stock objective runs took 110–115 s (Reported).
- Every trial is recorded, including timeouts, deaths and failures. None are excluded.

**Primary metric:** success rate. **Secondary:** game ticks to verified success (paired by spot), distance
travelled, crafting tables and furnaces placed, leftover intermediates. **Overhead:** Fugue planning µs (total and
max per objective).

**Classification rules:**
- **A, demonstrated improvement.** Fugue success ≥ stock success in every scenario, AND on the paired empty trials
  Fugue's median ticks are ≥ 10% lower, with Fugue faster in at least 4 of 5 pairs, AND cancel reports CANCELLED,
  AND no scenario regresses.
- **B, correct functional slice.** Fugue succeeds wherever stock does. Steal recovers via reopen. Cancel is
  CANCELLED. The time/distance difference is under 10% or not consistent across pairs.
- **C, inconclusive.** Fewer than 4 valid empty pairs (both arms' failures caused by harness or world, not by
  either arm), or results split so that neither A nor B nor D holds.
- **D, regression.** Fugue fails a scenario stock passes (two or more trials), or median ticks ≥ 10% worse on empty
  pairs, or cancel/steal misreports (a stopped task counted as success).

Planning overhead counts as acceptable if the max per-replan time is < 1 ms (it runs on the client tick).

### 7.1 Supplementary replicate (recorded after the primary A/B, BEFORE running it)

The primary A/B (§9.2) classifies as C by the rules above. To reduce noise for the *recommendation* only, I run a
supplementary replicate: `empty:5` per arm at the same five spots, stock first, with the same harness.
- It does **not** change the pre-registered classification.
- It is reported alongside the primary as Measured, with every trial included.
- It is used for: whether idx2's water stall repeats, and a pooled per-spot time comparison (10 pairs).

## 8. Deterministic tests (Stage 8)

**Verified.** Command: `JAVA_HOME=jdk-21 ./gradlew :26.3:test --no-daemon`.
- Fugue: 46 tests, 0 failures (FuguePlannerTest 16, FugueCoordinatorTest 22, FugueTaskTest 8).
- Full suite after the change, re-run after the smoke-run fix: 44 classes, 283 tests, 0 failures, 0 skipped. Before Fugue the suite had 237 tests; the other 46 are Fugue's.
- `:1.16.1:compileJava` and `:26.3:compileJava` pass. The baseline (untouched tree) compiled the same before any edit.

| Required coverage | Test(s) |
|---|---|
| Dependency expansion | `emptyInventoryExpandsWholeChainWithAggregatedTotals`, `onlyLeavesAreReadyInitially` |
| Shared prerequisites | `sharedPrerequisitesAreOneNodeEach` (one table, one cobblestone node of 11, 2 stick crafts) |
| Cycle rejection | `cycleIsRejected`, `toolCycleIsRejected`, `cycleBrokenByInventoryIsFine`, `depthIsBounded` |
| Already-satisfied requirements | `alreadySatisfiedGoalIsEmptyPlan`, `fullMaterialsLeaveOnlyTheFinalCraft`, `partialInventoryReusesHeldToolsAndStation`, `nearbyStationSatisfiesPresence`, `higherTierPickaxeSatisfiesLowerRequirement`, `goalAlreadyHeldSucceedsWithoutStartingAnything` |
| Missing resources | `missingRecipeIsExplicitResourceMissing`, `unsupportedGoalFailsExplicitly` |
| Reservations (no double counting) | `inventoryIsAllocatedOnceAcrossConsumers` |
| Successful child | `emptyInventoryReachesGoalInDependencyOrder`, `runsWholeChainToVerifiedSuccess` |
| Failed child | `transientFailureRetriesUpToLimitThenFails`, `nonTransientFailureWithoutProgressGivesUpImmediately`, `childGivingUpIsFailureNotSuccess` |
| Cancelled child | `cancelledChildIsAFailedAttemptNotSuccess` |
| Blocked child | `blockedChildEndsObjectiveAsBlocked`, `blockedChildMakesObjectiveBlocked` |
| Unknown / finished-without-postcondition | `childFinishedWithoutPostconditionIsNotSuccess` |
| Invalidation | `stolenInputReopensItsNode`, `invalidatedSucceededNodeIsReopenedAtNextPlan` |
| Task switching / selection | `schedulerPrefersNearerReadySource`, `tableCraftsAreBatchedByDownstreamThenStation` |
| Priority stability | `runningNodeIsNotPreemptedByCheaperChoice` |
| Retry limits / no-progress | `finishedWithProgressIsRetriedThenSucceeds`, `noProgressWatchdogAbandonsAttempt`, `nodeStartBudgetStopsLivelock`, `failuresThatMadeProgressDoNotExhaustTheNode` |
| Transient absorbed failures (live finding) | `momentaryChildFailureIsRiddenOut`, `finishedChildFailureSkipsTheGracePeriod` |
| Recovery alternatives | Re-net after failure (`invalidatedSucceededNodeIsReopenedAtNextPlan`); `recoverableChildFailureIsLeftToTheChild` |
| Cancellation propagation | `stopCancelsObjectiveAndRunningChild` |
| Ownership | `eachNodeOwnsAFreshChildAndOldOnesAreStopped`, `retryOfSameNodeGetsANewChildInstance` |
| Interrupt / resume | `interruptResumesSameNodeWithoutReplanningFromScratch` |
| Legacy compatibility | Pre-existing tests unchanged and passing (283/283 total). `@get` path untouched; `TaskOutcomeTickTest` still passes |
| Determinism / overhead recorded | `planningIsDeterministic`, `planningOverheadIsRecorded` |

## 9. Live results (Stage 9) — Measured

All runs: 1.16.1 SIM, `scripts/run-once.ps1 -SkipOstinatoUpdate`, `-Dtenorclef.seed=12345`, fresh world each
launch. Logs are `logs/sim-run-<tag>.log`; CSVs are in `versions/1.16.1/run/fuguebench/`.

### 9.1 Harness validation and smoke runs (Fugue arm, empty ×1, spot idx0). Not part of the classification.

| Tag | Result | What it showed | Action |
|---|---|---|---|
| fugue-smoke | FAILURE, TIMEOUT, 6603 ticks | Harness works end to end: spot search, teleport, CSV, exit. Wood nodes fine. The cobblestone child held 0 for 2×150 s, then the node was exhausted. Cause unknown: there was no heartbeat yet. | Added a 20 s `FUGUEBENCH BEAT` heartbeat (position plus the running task path) |
| fugue-smoke2 | FAILURE, TARGET_UNAVAILABLE, 2584 ticks | The cobblestone child surfaced `GetToEntityTask` "Follow target entity unavailable" (ABORT, copied up by `absorbChildOutcome`) four times. Each attempt had made progress (1→2→7→5 held). The first policy gave up after 4 failures, even with progress. | 40-tick failure grace; progress-making attempts no longer use up the no-progress budget (§4, §6). +3 unit tests. |
| fugue-smoke3 | **SUCCESS**, 2829 ticks | All 12 nodes, 0 failed attempts | Proceeded to the A/B |

### 9.2 Primary A/B (pre-registered protocol, §7)

Stock: `fugue-ab-stock`. Fugue: `fugue-ab-fugue2`.

The first Fugue launch, `fugue-ab-fugue`, was **aborted by a harness fault** after its idx0, which succeeded in
3274 ticks:
- While looking for the idx1 spot, the bench parks the bot at y=200 so chunks can load. It re-pinned the bot only
  every 2.5 s, so on a slow chunk load the bot free-fell and died ("fell from a high place").
- That death reached a **pre-existing TenorClef bug**: `DeathMenuChain.java:118` busy-waits with
  `while (player.isAlive());` on the render thread. That froze the client. A jstack showed the render thread
  pinned there and the bench thread parked in `mc.submit().get()`.
- I killed only that client (pid 10104, launched by me). I fixed the harness to re-pin every 250 ms
  (`FugueBench.loadSurface`) and re-ran the whole Fugue arm.
- The fix only touches between-trial spot search, not trial conditions. Stock had 0 deaths in all its trials, so
  stock was not re-run. `DeathMenuChain` was not modified (out of scope).

| idx | Scenario | Stock result | Stock ticks | Stock dist | Fugue result | Fugue ticks | Fugue dist | Δ ticks |
|---|---|---|---|---|---|---|---|---|
| 0 | empty | SUCCESS | 2615 | 93 | SUCCESS | 2836 | 89 | +8.5% |
| 1 | empty | SUCCESS | 4118 | 150 | SUCCESS | 4235 | 216 | +2.8% |
| 2 | empty | SUCCESS (2 tables) | 3027 | 134 | **TIMEOUT** | 8401 | 229 | — |
| 3 | empty | SUCCESS | 2792 | 105 | SUCCESS (2 furnace placements) | 2877 | 126 | +3.0% |
| 4 | empty | SUCCESS | 3210 | 159 | SUCCESS | 2458 | 118 | −23.4% |
| 5 | partial | SUCCESS | 1484 | 29 | SUCCESS | 1667 | 35 | +12.3% |
| 6 | full | SUCCESS | 10 | 0 | SUCCESS | 12 | 0 | +2 ticks |
| 7 | steal (cobble removed at t≈850) | SUCCESS | 3181 | 150 | SUCCESS | 4211 | 225 | +32.4% |
| 8 | steal (stock t=1261, Fugue t=1030) | SUCCESS | 2779 | 117 | SUCCESS | 3744 | 136 | +34.7% |
| 9 | cancel at 800 ticks | CANCELLED (correct) | 801 | 46 | CANCELLED (correct) | 801 | 48 | — |

Success rates:
- Stock: 10/10.
- Fugue: 9/10. Empty 4/5; partial, full, steal ×2 and cancel all passed.
- No deaths in either arm. No stopped task was counted as success: the cancel trials pass only as CANCELLED with no
  pickaxe.

Empty, all 5 trials (a timeout counts as 8401 ticks):
- Median ticks: stock 3027, Fugue 2877. That is −5.0%, carried by idx4.
- Fugue was faster in **1 of 5** pairs.
- Over the 4 pairs where both arms succeeded, the median paired Δ is +2.9%.

**Fugue idx2 timeout, diagnosed from heartbeats.**
- All inputs were held by t≈3600. At the final `iron_pickaxe` craft, `CraftInTableTask` judged the placed table
  (1612, 89, −111; up a slope) unusable and began making a new table. Stock did the same at this spot: it placed
  2 tables.
- Fugue's child then went into a `get out of water` ⇄ `Escaping water before mine` loop at (1598, 62, −118) for
  about 4800 ticks.
- The watchdog fired at t≈6400. The retry re-netted correctly: it reopened log 1 → planks → table. It then
  re-entered the same water stall until the 8400-tick limit.
- The stall is in shared child code (`MineAndCollectTask` / water escape), not in Fugue's coordination. It still
  counts against Fugue: nothing is excluded.

**Steal.** In both Fugue steal trials the theft hit the *running* cobblestone node (target 11). The node kept
running and refilled in place, so `reopens=0`. The reopen path (theft after a node succeeded) is covered by unit
tests (`stolenInputReopensItsNode`, `invalidatedSucceededNodeIsReopenedAtNextPlan`) and was exercised live once,
in idx2.

**Overhead (Fugue).**

| Measure | Value |
|---|---|
| Replans per empty objective | 12 (13 with one retry) |
| Planning total per objective | 1.4–2.5 ms steady state (7.1 ms on the first objective of a client launch) |
| Max single replan, steady state | 187–430 µs |
| Max single replan, first objective of a launch | 4.9 ms (cold JIT and class loading; smoke runs 5.6–6.7 ms) |

Against the pre-registered "< 1 ms max" rule: **not met as written**, because of the single cold first plan per
launch. It is met in steady state. One tick is 50 ms.

### 9.3 Supplementary replicate (empty ×5 per arm, same spots; §7.1)

Stock: `fugue-rep-stock`. Fugue: `fugue-rep-fugue`.

| idx | Stock | Stock ticks | Stock dist | Fugue | Fugue ticks | Fugue dist | Δ ticks |
|---|---|---|---|---|---|---|---|
| 0 | **TIMEOUT** | 8401 | 150 | SUCCESS | 2975 | 105 | — |
| 1 | SUCCESS | 3448 | 198 | SUCCESS | 3726 | 213 | +8.1% |
| 2 | SUCCESS | 3418 | 198 | SUCCESS | 3197 | 176 | −6.5% |
| 3 | SUCCESS (2 tables) | 3534 | 138 | SUCCESS | 3006 | 187 | −14.9% |
| 4 | SUCCESS | 3139 | 190 | SUCCESS | 4577 | 417 | +45.8% |

Stock 4/5, median 3448. Fugue 5/5, median 3197. Fugue: 0 failed attempts, 0 retries, steady-state max replan
251–309 µs, cold first plan 6.9 ms.

**Stock idx0 timeout.** Heartbeats show stock in `Collect cobblestone ×3` (for the stone pickaxe) holding 0
cobblestone, within about 4 blocks of one spot, from t=400 to t=8400. This matches the first Fugue smoke run at
the same spot: its cobblestone child held 0 for 2×150 s. Fugue's watchdog ended that run early with an explicit
TIMEOUT failure (6603 ticks). Stock simply ran to the limit.

**Fugue idx2 did not repeat** the primary run's water stall. The stalls are intermittent and specific to the
spot, not to the arm.

### 9.4 Pooled empty-inventory comparison (primary + replicate, 10 pairs per arm)

| | Stock | Fugue |
|---|---|---|
| Success | 9/10 | 9/10 |
| Median ticks (a timeout counts as 8401) | 3314 | 3102 (−6.4%) |
| Pairs where this arm was faster, or the only one to succeed | 6/10 | 4/10 |
| Median paired Δ, 8 pairs where both succeeded | — | +2.9% |
| Timeouts | 1: cobblestone stall at idx0 | 1: water-escape loop at idx2 |

Reading: there is no consistent speed difference. The pooled medians favour Fugue by 6%. The paired comparison
slightly favours stock. Both are inside the ±8% run-to-run spread at a single spot. Reliability is identical, and
each arm's only failure was a shared child-level stall.

## 10. Optimization (Stage 10)

No performance optimization was made, because the evidence does not point at anything in Fugue to optimize:
- Planning costs about 0.2 ms per replan, against a 50 ms tick.
- Every Fugue time loss traced in the heartbeats sits inside child tasks that stock runs too: the water-escape
  loop, the table re-acquisition, mining approach.
- Run-to-run variance at the same spot is large. Fugue at idx0 took 2829, 3274 and 2836 ticks across three
  launches (±8%). That is bigger than most paired differences.

The changes made after live runs were **correctness fixes**, not tuning:
- 40-tick failure grace and progress-aware retry, after smoke2.
- The harness re-pin, after the aborted Fugue launch.

Ostinato was not touched.

## 11. Limitations

- **Narrow scope.** Only the 12-item iron-pickaxe chain is supported, with one recipe per item. No charcoal, no
  alternative recipes, no chests as sources.
- **No live concurrency gain is possible.** There is one body, and Fugue runs one node at a time. Its only levers
  are ordering, aggregation and recovery policy. For the iron-pickaxe chain the stock recursive tree already
  aggregates well: same table and furnace counts, similar leftovers.
- **Fugue inherits child-task stalls.** It can detect a stalled child (150 s watchdog) and re-net, but the retry
  runs the same child code in the same place. A spot-level stall such as idx2's water loop is not escaped. Moving
  elsewhere, or choosing an alternative source, would need a "different instance" recovery that v1 lacks.
- **The station check differs from the child's.** Fugue's "station nearby" uses `WorldHelper::canReach` within 40
  blocks. `CraftInTableTask` can still decide a reachable-but-awkward table is too costly and build a new one
  (idx2, both arms). Fugue then sees an unplanned table detour inside the node.
- **Sample size.** One run per spot per arm in the primary A/B, plus one replicate for the empty spots (§9.3).
  Ticks-to-success varies by ±8% or more per spot between launches.
- **Steal tested only in-attempt live.** Theft during the running node is absorbed in place. A theft after a
  node succeeded (a reopen) was seen live only once (idx2), otherwise only in unit tests.
- **Cold first plan.** The first plan per launch is 4.9–6.7 ms, above the 1 ms bar.
- **Pre-existing bugs found, not fixed (out of scope):**
  - `DeathMenuChain` busy-wait (§9.2) can freeze the client after any death in which the player is alive again
    by the time respawn runs.
  - `CollectFuelTask` units mismatch (Stage 0 audit).
- **Test side effect.** Running `:26.3:test` creates untracked `versions/26.3/altoclef/{compositions,configs}`
  files. They were left in place.

## 12. Classification and recommendation

### 12.1 Classification: **C — inconclusive** (by the pre-registered rules applied to the primary A/B)

| Rule | Holds? | Evidence |
|---|---|---|
| A | No | Fugue empty 4/5 vs stock 5/5. Fugue faster in 1/5 pairs (needs ≥ 4/5, with a ≥ 10% median gain). |
| B | No, on the first condition | "Fugue succeeds wherever stock does" failed once (idx2, water-escape stall). The other B conditions hold: steal recovered 2/2, cancel CANCELLED, time difference < 10% / inconsistent. |
| D | No | Only one Fugue-only failure (D needs ≥ 2). Empty median 2877 vs 3027 is not ≥ 10% worse. No misreport: cancel was CANCELLED and never counted as success. |
| C | **Yes** | Neither A, B nor D holds. |

**Supporting, not overriding** (Measured, §9.3–9.4): the pre-registered replicate shows functional parity:
- Success is 9/10 vs 9/10 pooled.
- Each arm's single failure was a shared child-task stall.
- No speed advantage either way.

In substance this is a correct functional slice (B-like), with **no demonstrated improvement**. Fugue's measurable
differences:
- explicit, structured failure reporting (watchdog TIMEOUT with the node and reason, instead of silently running
  to the limit)
- full per-node observability
- about 2 ms of planning per objective

Evidence labels:

| Claim | Label |
|---|---|
| Correctness of planning, netting, recovery and contract | Verified (46 unit tests; 283/283 suite) |
| Live success, time, distance and overhead | Measured (§9) |
| Stall root causes being in shared child code | Measured (heartbeat task paths) |
| Inherited Stage-0 audit statements | Reported, unless marked otherwise in §2 |

### 12.2 Recommended next step

1. **Do not widen Fugue's scope yet.** On the iron-pickaxe chain, the stock recursive tree already orders and
   aggregates about as well as Fugue does: same table/furnace counts and similar times. More recipes would add
   surface without evidence of payoff.
2. **Fugue's one plausible route to class A is stall escape.**
   - Every timeout seen, in both arms, was a child stuck in one place: the cobblestone hold-0 stall, and the
     water-escape loop.
   - Stock cannot notice these. Fugue already detects them (watchdog) and re-nets.
   - What is missing is a *different-instance* retry: on a no-progress TIMEOUT for a MINE node, blacklist the
     stalled target region or require the retry to start some distance away.
   - That is a bounded change inside `FugueCoordinator`/`FugueTask`. It needs no Ostinato change.
   - Test it with ≥ 10 trials per arm on the known stall-prone spots (idx0, idx2), using the existing harness.
     Pre-register "stall-rescue rate" as the primary metric.
3. **Separately, as a small bugfix outside Fugue:**
   - Fix `DeathMenuChain`'s `while (player.isAlive());` render-thread busy-wait. It can freeze the client
     after a death.
   - Look at the two shared stalls in `MineAndCollectTask` (water escape; cobblestone approach). Both affect
     `@get`, not just Fugue.
4. Keep Fugue opt-in (`@fugue`) and disabled by default, as delivered.

## 13. Files and final repository state

Fugue changes. All uncommitted; nothing was committed, pushed, merged, reset, stashed, cleaned or discarded.

| Path | Status |
|---|---|
| `src/main/java/adris/altoclef/fugue/` (FugueRecipe, FugueRecipeBook, FugueWorldView, LiveFugueView, FugueNode, FuguePlan, FuguePlanner, FugueScheduler, FugueCoordinator, FugueTask) | new, untracked |
| `src/main/java/adris/altoclef/commands/FugueCommand.java`, `FugueBenchCommand.java` | new, untracked |
| `src/main/java/adris/altoclef/benchmark/FugueBench.java` | new, untracked (1.16.1-only body; stub elsewhere) |
| `src/test/java/adris/altoclef/fugue/` (FakeWorld, FuguePlannerTest, FugueCoordinatorTest, FugueTaskTest) | new, untracked |
| `src/main/java/adris/altoclef/AltoClefCommands.java` | modified: one line registering `FugueCommand` and `FugueBenchCommand`. The **only** pre-existing file edited. |
| `FUGUE_V1_HANDOFF.md` | new, untracked |
| `logs/sim-run-fugue-*.log`, `versions/1.16.1/run/fuguebench/*.csv` | run outputs (logs/ was already untracked) |
| `versions/26.3/altoclef/…` | test-run side effect, untracked |

**Verified** at handoff:
- TenorClef `main` @ `983cf317`: 31 M (30 pre-existing + `AltoClefCommands.java`) and 96 ?? entries
  (89 pre-existing + 6 Fugue + `versions/26.3/altoclef/`).
- Ostinato `feat/predictive-physics` @ `1aed5cc2`: 37 M + 61 ??, unchanged from the start.
- Fugue is opt-in only: `@fugue` or `@fuguebench`. No setting, default path or existing task routes through it,
  and `@get` is unchanged.
