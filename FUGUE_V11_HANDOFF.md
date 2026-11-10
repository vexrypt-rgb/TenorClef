# Fugue v1.1 — Intelligent Stall Recovery: Handoff

Status labels: **Verified** (read in code or reproduced), **Measured** (live numbers from this assignment),
**Reported** (stated elsewhere, not re-checked), **Contradicted**, **Unknown**.
Evidence levels: Implemented / Unit-tested / Integration-tested (real `Task.tick` loop) / Live-tested /
Measured improvement / Unverified hypothesis.

Everything is uncommitted. Fugue and its recovery stay disabled by default.

**Verdict: D — regression (stall recovery). Recovery stays off by default.**

- **Primary result:** verified successes out of 20 empty trials per arm:
  - stock 18/20
  - Fugue 19/20
  - Fugue + recovery 16/20 (pre-registered D rule: C ≤ B − 3)
- **Failures:** every failure in every arm is the same 1-deep pond at candidate 1. 12 recoveries resumed nothing.
- **Mechanism (post-hoc):** in 3 of the 4 C failures, recovery fired on a transient wet episode that Fugue without
  recovery escaped on its own 5/5 times. It excluded the shore log and left the bot in the water for the next node.
  The pond itself is a stock `DestroyBlockTask` water-handling loop (owner-observed "tiny increments"), so the fix
  belongs in stock (§10).
- **Correctness:** no freeze, crash, death or false success. Recovery cancels were 10/10 CANCELLED. One B-arm cancel
  reported FAILURE through stock result-absorption semantics (§8.3).
- **DeathMenuChain render-thread fix:** classified separately. A correctness fix, unit-tested 7/7, not live-exercised
  (no deaths in the campaign).
- **Tests:** full suite 318/318 passed after the campaign. 1.16.1 compiles.
- **Performance:** with recovery off, 0 µs probe cost. With it on, a median of 3.3 µs per probed tick.

Evidence levels:
- DeathCommandQueue: Implemented, Unit-tested.
- Recovery coordinator: Implemented, Unit-tested, Integration-tested, Live-tested.
- Measured improvement: **none**.
- Harm mechanism: Unverified hypothesis supported by heartbeats.

---

## 1. Stage 0 audit

### 1.1 Repository state at start (Verified)

| Repo | Branch | HEAD | Worktree |
|---|---|---|---|
| TenorClef-main | `main` | `983cf317` | 31 M + 96 ?? (snapshot `/tmp/f11/tc-status-start.txt`), identical to the end of v1 |
| Ostinato-ppe | `feat/predictive-physics` | `1aed5cc2` | 37 M + 61 ?? (snapshot `/tmp/f11/os-status-start.txt`), not touched by v1.1 |

No java process was running at the start.

### 1.2 The two v1 stalls: execution state (Verified, Measured)

v1 recorded two stalls but not their cause. The v1 heartbeat cut the task path at depth 12 and 60 characters, which
hid the stock stall's leaf. v1.1's bench prints the deepest five tasks in full (§7). A stock pilot run at the v1 idx0
spot (§8.1) then showed the leaf state.

| Stall | Where | Leaf state |
|---|---|---|
| v1 Fugue primary idx2, `log` node (t≈4000–8400) | spot 1600,87,-112 | `Destroy block at 1596,63,-126 > Escaping water before mine > get out of water`, alternating with `Getting to block... reach=false` (v1 heartbeat). Position (1598,62,-118) did not change. |
| v1 stock replicate idx0 / Fugue smoke1, `cobblestone` held 0 | spot 576,63,-48 | pilot `f11-pilot-stock` idx0 t=2000–2400: `Collect cobblestone x3 > Mine And Collect [cobblestone] > Mining or Collecting > Destroy block at 579,57,-51 > Escaping water before mine (bobbing, no footing) > get out of water`, alternating with `Getting to block... reach=false ground=true wet=true`. The bot stays at 579,62,-51 with cobble=0. |

**Both stalls are the same mechanism.** The target block is in or under water. Code (Verified):

- `DestroyBlockTask.onTick` (`tasks/construction/DestroyBlockTask.java:342-370`): while wet and not grounded it
  counts bob ticks. At 25, or when there is no reach, it cancels pathing and returns `GetOutOfWaterTask`.
- Once out, `MineOrCollectTask` (`tasks/resources/MineAndCollectTask.java:213-372`) re-pursues the **same**
  `currentlyPursuing` target. `AbstractDoToClosestObjectTask` drops a pursuit only when `isValid` fails
  (`AbstractDoToClosestObjectTask.java:61`), and a block under water stays valid.
- The existing unreachable mechanism never fires. In the pilot's ~3000-tick stall there are **zero** `Blacklist`
  lines for 579,57,-51 (Verified from the log; the only blacklist lines are for an earlier log block and dropped
  items). `MineOrCollectTask.onTick` blacklists `miningPos` only when its `MovementProgressChecker` fails, and that
  checker is reset every tick Baritone is pathing. Unverified hypothesis: the `GetOutOfWaterTask` path in every
  cycle keeps resetting it. The bot does not move (position 579,62,-51 at every heartbeat from t=2000 to t=4800),
  yet no stock signal marks the loop as a failure.
- The stock v1 watchdog analogue does not exist. Fugue v1's 3000-tick watchdog restarts the node, but the fresh child
  picks the nearest target again, which is the same submerged block. That is why v1's idx2 loop continued after the
  re-net.

### 1.3 Existing mechanisms reused (Verified)

| Need | Existing API | Use |
|---|---|---|
| Exclude a target | `BlockScanner.requestBlockUnreachable(pos, allowed)` → `WorldLocateBlacklist`; `getNearestBlock` and `MineOrCollectTask.getClosestBlock` skip unreachable positions | `allowed=0` makes one position unreachable at once (`AbstractObjectBlacklist.blackListItem`: failures 1 > allowed 0, including the RESET and hard-cap branches) |
| Undo it | none for one position (only `clearBlacklist()` / `reset()`, which clear everything) | **added** `BlockScanner.forgetUnreachable(pos)` → `AbstractObjectBlacklist.forget(item)` (2 small methods) |
| Cancel a child | `Task.stop()`. `FugueTask` returning null makes `Task.tick` call `sub.stop()` and clear `sub`, unless an `ITaskCanForce` in the chain refuses (`Task.canBeInterrupted`) | recovery waits until `getSub()==null` |
| Release navigation | `DestroyBlockTask.onStop` calls `getPathingBehavior().forceCancel()` | recovery also waits for `!isPathing()` |
| Read the child's target | `DestroyBlockTask.getPos()` (exists) | the deepest `DestroyBlockTask` in the child's `getSub()` chain |
| Water-escape signal | `GetOutOfWaterTask` in the chain | rising edges counted per target |

### 1.4 Benchmark arm equivalence (Verified)

`FugueBench.run` gives every arm the same steps: Peaceful, time 1000, items cleared, inventory cleared, teleport,
2.5 s chunk wait, two scanner resets (which also clear the blacklist), the same 8400-tick limit and the same success
predicate (result SUCCESS and an iron pickaxe independently seen in the inventory). Only the task differs:

- A = `TaskCatalogue.getItemTask("iron_pickaxe",1)`
- B = `FugueTask.live(..., recovery=false)`
- C = `FugueTask.live(..., recovery=true)`

Each client launch creates a fresh world from seed 12345 (`AutoWorldCreateMixin`), so every listed spot is untouched
at the start of its trial.

### 1.5 Decision record (narrowest safe implementation)

1. **DeathMenuChain:** replace the spin with a tick-driven queue. This is a separate fix (§2).
2. **Recovery = make the retry differ in the one condition that caused the loop:** the target. On a classified stall,
   Fugue stops its child through the Task API, waits for the subtask and navigation to be released, marks that one
   block unreachable through the existing blacklist, and starts a fresh child. Stock target selection then picks a
   different block.
3. No movement, no inputs, no teleports, no Baritone goals from Fugue. Ostinato is untouched. No changes to Maestro,
   Cadenza, PPE, Legato or Presto. Stock tasks are unchanged.
4. Two stall classes with observable predicates (§3). Anything else is left to v1's rules (UNKNOWN stays unknown).
5. Recovery is off by default (`Config.recovery=false`). Off means exactly v1 behaviour, which is arm B.

---

## 2. DeathMenuChain render-thread freeze (Stage 1)

**Diagnosis (Verified + Measured):**

- `DeathMenuChain.getPriority()` runs on the render thread (`AltoClef.onClientTick → TaskRunner.tick`).
- On the death screen after the 2 s timer it requested respawn, closed the screen, then for **each** death command
  (including empty ones) ran `while (MinecraftClient.getInstance().player.isAlive()) ;` (line 118, original AltoClef
  code).
- Normally the client player reads dead there, so the loop exits at once. If the player reads alive (in v1 a
  harness heal reached the dead player; a server-side respawn racing the timer does the same), the loop never exits,
  because the packets that could change that state are applied on the same blocked thread.
- v1 jstack: render thread at `DeathMenuChain.java:118` with ~18.5 min CPU, client frozen.

**Fix (Implemented, Unit-tested):**

- New `chains/DeathCommandQueue.java` (pure, no Minecraft types). `DeathMenuChain` builds the command list (empty
  commands dropped, `{deathmessage}` substituted) and `schedule()`s it. Every `getPriority()` then calls `tick()` with
  the player state: NO_PLAYER (not in game, player null, or a different world from the one scheduled in), DEAD or
  ALIVE.
- Commands run when the player reads DEAD. That was the old loop's exit condition, so the normal path still runs them
  on the same tick.
- While ALIVE they are held (non-blocking) for at most 40 ticks, then run once. In that case the respawn has completed;
  the old code would have hung forever.
- Leaving the world discards them with a log line. A new respawn replaces an unrun batch. The batch is cleared
  before running, so commands never run twice.
- Respawn, `kill @s` retry, auto-respawn-off cancel and reconnect logic are unchanged.

**Tests:** `DeathCommandQueueTest`, 7/7 (results in §6). They cover:

- dead runs on the scheduling tick, exactly once
- 39 alive ticks under `assertTimeoutPreemptively(2 s)` run nothing and return
- run-after-hold exactly once
- death while held
- discard on leaving
- replace
- empty
- reschedule from inside a command

**Classification of this fix:** a correctness fix, Unit-tested. The live freeze path needs a death with the player
reading alive, which the bench now avoids (Peaceful, fall-safe teleports). It was not reproduced live after the fix
(§10).

## 3. Progress signals and stall classification (Stages 2–3)

### 3.1 v1 signal audit (Verified)

| Situation | v1 signal | Correct? |
|---|---|---|
| Item count rises | `best`/`lastProgressTick` (inventory count via catalogue matches, including crafting/furnace input slots) | yes, the only progress signal |
| Navigation / movement | none | yes: movement is not progress |
| Mining, crafting, smelting without a count change yet | no failure until the 3000-tick watchdog | yes: stopping to work is not a stall |
| Target change | not observed | gap: v1 cannot tell "roaming" from "stuck on one block" |
| Temporary obstruction / one-tick ABORT | 40-tick failure grace | yes |
| Environmental escape (water) | not observed | gap: the observed loop |
| Genuine no-progress | watchdog after 3000 ticks, then a **fresh but identical** retry | the retry repeats the failing condition |

### 3.2 Classes and predicates (Implemented, Unit-tested)

Observed every tick while recovery is on:

- **target** = the position of the deepest `DestroyBlockTask` in the child's subtask chain, or none
- **escapes** = rising edges of "a `GetOutOfWaterTask` is in the chain" while on that target
- **idle** = ticks since the later of "this target first seen" and "last item gained"

| Class | Predicate | Default |
|---|---|---|
| `ENVIRONMENTAL_TRAP` | target present ∧ escapes ≥ `trapEscapes` ∧ idle ≥ `trapTicks` | 2 escapes, 400 ticks (20 s) |
| `REPEATED_TARGET_FAILURE` | target present ∧ idle ≥ `stallTicks` | 900 ticks (45 s) |
| (unclassified) | anything else: no target (crafting, smelting, pickup, wandering), targets changing, short pauses | v1 rules unchanged; the 3000-tick watchdog still applies |

Gaining an item resets idle, so a progressing task never matches. Movement is not progress: a child that walks
around one unreachable block still matches. NO_RESOURCE_PROGRESS without a target and NAVIGATION_LOOP across
changing targets were considered. They are not separate classes because there is no target to change. They stay
with the v1 watchdog, so unknown stays unknown.

## 4. Recovery state machine (Stages 4–5)

```
RUN(child) --stall predicate & budget--> RECOVER: FugueTask drops the child (returns null)
   Task.tick stops it (onStop -> forceCancel pathing) and clears sub, unless an ITaskCanForce refuses
RECOVER --each tick--> released? = (FugueTask.getSub()==null) && !isPathing()
   no, <= releaseTimeoutTicks (100): keep holding; run NOTHING (no competing child, no navigation)
   no, >  releaseTimeoutTicks:      objective FAILED "recovery: <key> child did not release control" (explicit)
   yes: exclude target (+ resources around the bot if the child escaped water) -> replan -> fresh child (RUN fresh)
new attempt:
   first item gained                -> "recovery RESUMED" (recoveriesResumed++)
   child returns to an excluded target -> "recovery INEFFECTIVE", no more recoveries for this node
   stalls again on a different target -> another recovery if budget remains
objective ends (success / failure / clean stop) -> every exclusion forgotten (BlockScanner.forgetUnreachable)
```

**Exclusion (the one recovery action).**
- The target is marked unreachable with the existing `requestBlockUnreachable(pos, 0)`.
- When the child escaped water at least once on that target (always true for a trap; possible for a
  REPEATED_TARGET stall whose escape state lasted long), the same is done to every known gather resource of Fugue's
  catalogue (logs, stone, coal ore, iron ore) in the column zone around the bot's position at release (horizontal
  distance ≤ 16, any depth below, at most 8 above), nearest first, capped at 128. The failure is the bot's spot, not the block: from the flooded spot every under-water target loops
  on the stock water escape (`DestroyBlockTask` → `GetOutOfWaterTask` "Completing goal" → path again), and stock
  selection picks the nearest resource, i.e. the next one under the same pond. Three narrower predicates were tried
  live and each left the next-nearest resource in the trap (§8.1).
- The scanner only knows the ~40 nearest blocks per type plus scanned chunks, so the zone is what stock selection
  could actually pick next. With it excluded, the fresh child's target lies outside the zone and stock navigation
  walks the bot out; Fugue issues no movement.
- Stock target selection then chooses a different block. No navigation is issued by Fugue.

**Bounds (Stage 5).**
- 3 recoveries per objective.
- 2 per node.
- A target already excluded is never recovered again, so there is no oscillation.
- An exclusion that could not be applied leaves no recovery for the node.
- At most 128 positions per recovery, so at most 3×128 = 384 temporary entries.
- All entries are forgotten when the objective ends.
- Recoveries do not consume the v1 failure budget. They do count as node starts (`maxNodeStarts=60` still guards
  livelock).
- When the budget is spent, the stall is logged once ("no recovery left; v1 limits apply") and v1's
  watchdog/failure rules end the attempt.

**Ownership / cancellation guarantees (Integration-tested).**
- No new child is installed until the old one is stopped and removed and pathing is idle.
- A child that refuses interruption blocks the recovery, which then fails explicitly after 100 ticks.
- Cancel before or during recovery gives CANCELLED and applies nothing; a cancel after an exclusion forgets it.
- An interrupt by a survival chain keeps the exclusions (the objective resumes).
- Exclusions leak only if the Fugue task is replaced by a different user task through `stop(newTask)` (≤ 384
  positions, cleared by the next `BlockScanner.reset()`).

## 5. v1 guarantees preserved (Stage 6)

- SUCCEEDED still requires the held count. A recovery never declares success, and neither does moving to a new
  target (test `successRequiresTheHeldCountNotARecovery`).
- FAILED and CANCELLED are not success.
- Transient failures use v1's budgets.
- With recovery off, the code path is exactly v1's: the same `ChildReport` constructor, no observation, an
  unchanged metrics line (test `recoveryOffIsExactlyV1`, and all 46 v1 tests pass unchanged).
- Stock tasks are untouched. The only non-Fugue changes are the DeathMenuChain fix and the two blacklist
  `forget` methods.

## 6. Tests (Stage 7)

All tests run on the 26.3 module (`./gradlew :26.3:test`, JDK 21 launcher). The results below were actually executed; the
counts come from `versions/26.3/build/test-results/test/TEST-*.xml`.

**New tests (34):**

| Suite | Tests | Covers |
|---|---|---|
| `chains.DeathCommandQueueTest` | 7 | Stage 1. The queue never blocks; a dead player runs commands on the scheduling tick (v1 behaviour); an alive player on the death screen is held ≤ 40 ticks and then run; leaving the world discards; a second respawn replaces an unrun batch; a command that reschedules does not run twice. |
| `fugue.FugueRecoveryTest` | 21 | Stages 3–6 on the coordinator with a fake world. Progress on one target never stalls; work with no target (crafting/smelting) is not a stall; movement without progress is left to the v1 watchdog; repeated-target and water-trap predicates, including the trap firing before the stall window; target-only vs zone exclusion and forgetting all of it; RESUMED only on the stalled node ("replan moved on" is not counted); per-node and per-objective bounds, then v1 limits; no recovery before the window; unreleased navigation fails explicitly without excluding or restarting; a still-installed child holds the recovery; COULD-NOT-EXCLUDE gives no further recovery for that node; a return to an excluded target is INEFFECTIVE, with no oscillation; recoveries do not consume the failure budget; dependency order survives a child replacement; recovery off is exactly v1; SUCCESS needs the held count. |
| `fugue.FugueRecoveryTaskTest` | 6 | Integration through `FugueTask` and the real `Task.tick`. The old child is stopped (through the Task API) before the exclusion, and a new instance is installed; cancel before / during / after recovery; an interrupt keeps exclusions; a child that refuses to stop (`ITaskCanForce`) blocks the recovery, no competing child is started, and the recovery fails explicitly. |

Plus `fugue.FuguePlanColdStartTest` (1, a Stage 9 measurement, not a gate).

The fake world's `wet` map stands in for whatever `LiveFugueView` would add to an exclusion. The test names still
say "wet neighbours" from the first design; the coordinator logic they check (exclude extra positions iff the child
escaped water; forget them all) is unchanged. **The zone geometry itself (column, radius 16, cap 128) is not
unit-tested:** it needs a Minecraft world and was exercised only live.

**Runs:**
- Targeted (`fugue.*` and `chains.*`) after the final code change: 81/81 passed (Coordinator 22, Planner 16,
  Task 8, Recovery 21, RecoveryTask 6, ColdStart 1, DeathCommandQueue 7).
- Full suite after the campaign (no client running, 00:05): **318/318 passed**, 0 failures, 0 errors, 0 skipped
  (48 result files, all written by this run).
- Mutation check: replacing the exclusion call with an empty list failed 11 of the recovery tests (8 + 3), so they
  detect a recovery that changes nothing.
- 1.16.1 `compileJava`: passes (the live jar).


## 7. Harness changes for v1.1 (labelled; made BEFORE any v1.1 A/B/C trial)

All harness changes live in `benchmark/FugueBench.java` (1.16.1 only):

- **Arm `fuguer`:** Fugue with recovery on. `fugue` is still recovery off.
- **Fixed spots:** `-Dtenorclef.fuguebench.spots=1,3,2` runs trial *i* at spot candidate `spots[i mod n]` (same
  deterministic rule as v1, so candidate 1 = v1 idx0 576,63,-48 and candidate 3 = v1 idx2 1600,87,-112).
- **Heartbeat:** depth and the deepest five tasks in full.
- **CSV columns:** spotCandidate, fugueStalls, fugueRecoveries, fugueResumed, fugueIneffective, probeUsTotal,
  probeUsMax, probeTicks, exclusions.

Also, `@fugue` reads `-Dtenorclef.fugue.recovery=true` (default false).

**Mid-campaign environment change (labelled; not a bot-behaviour change).** At 19:31, during launch A01/B01, the
owner pointed out that the clients were opening on the primary monitor, taking the mouse, and playing sound:
`run-once.ps1` does not start `tools/move-mc-window.ps1`, and the 1.16.1 run directory had no `options.txt`.
- Fix: a window watcher (`%TEMP%/f11_window_watch.ps1`) moves each client to the secondary monitor without
  activating it.
- Fix: a minimal `options.txt` (`pauseOnLostFocus:false`, `soundCategory_master:0.0`; every other option left at
  its default) applies from the next launch.
- Why this doesn't affect the bot: `TaskRunner` already forces `pauseOnLostFocus=false` while any task runs, and
  volume and window position don't affect the bot. Render distance and all other options stay at the defaults the
  earlier launches used.

## 8. Live protocol (Stage 8) — pre-registered before the first A/B/C trial

### 8.1 Reproduction pilot (not part of the classification)

`f11-pilot-stock,fugue` is one stock launch at candidates 1 and 3 with the new heartbeat. Its tag is malformed
because of a script argument bug; it is a stock-only launch. It was used only to identify the stall state (§1.2).

- Pilot result (stock): candidate 1 TIMEOUT at 8401 ticks (water-trap stall reproduced, cobble 0); candidate 3
  SUCCESS at 4113 ticks (the v1 idx2 stall did not recur in this world/run).
- `f11-smoke-C` is one `fuguer` launch, `empty:1` at candidate 1, run after the unit/integration tests passed and
  before the primary campaign. Its only purpose is to catch a crash or ownership defect in the never-yet-live
  recovery path before spending the campaign. Not part of the classification, whatever its result.
- `f11-smoke-C` result: TIMEOUT at 8401 ticks; no crash; 3 recoveries, each released in 1 tick (ownership worked).
  It exposed two defects, both fixed before any A/B/C trial:
  1. **Wet-neighbour exclusion never fired.** The trapped stone is under ~4 blocks of pond floor and shallow water,
     so neither it nor its neighbours touch water on a face. The stock child then picked the adjacent stones
     (579,57,-51 → 580,57,-51 → 579,57,-50) and the budget of 3 was spent in ~2400 ticks. Fix: a block also counts
     as under water when water lies in the column above it (≤ 8 blocks) before air; plus a one-line diagnostic.
  2. **RESUMED over-credited.** A `log` recovery (2/3 held) was followed by a replan to `planks`, which crafted at
     once and was logged as RESUMED. RESUMED now requires the restarted attempt to be the stalled node; otherwise it
     is logged as "replan moved on" and not counted. (Also fixed: COULD-NOT-EXCLUDE marked the wrong node.)
- `f11-smoke-C2`: one more `fuguer` `empty:1` launch at candidate 1 after those fixes, same purpose and status.
  Result: the column predicate fired (`+21` neighbours, all at y=54–56), but the adjacent same-level stones
  580,57,-51 and 579,57,-50 failed it (air before water in their column) and trapped the bot in turn; the budget was
  spent by t≈2400 again. Fix: drop per-block water geometry; exclude the same-type known neighbourhood (radius 6,
  cap 48) whenever the child escaped water on the target. The diagnostic now goes to the log unconditionally.
- `f11-smoke-C3`: one more `fuguer` `empty:1` launch at candidate 1 after that fix, same purpose and status.
  Result: TIMEOUT. The stone trap was cleared (all 40 known stones within 6 excluded) and the replan went to coal,
  but the coal ores under the same pond trapped the bot at the **same position** (580,62,-51) for 583,52,-47 and
  then 585,53,-41, spending the budget. The failure condition is the bot's spot, so the zone is now centred on the
  bot and covers all of Fugue's gather resources (radius 16, cap 128).
- `f11-smoke-C4`: one more `fuguer` `empty:1` launch at candidate 1 after that fix, same purpose and status.
  Result: still trapped. Every later trap target lay under the pond horizontally 7–13 blocks from the spot but
  17+ blocks deep (578,45,-60; 579,45,-61; 581,56,-66; 579,56,-61), outside a sphere of 16. Fix: the zone is a
  column (horizontal ≤ 16, unbounded below, ≤ 8 above).
- `f11-smoke-C5`: one more launch after that fix. **Stopping rule, set before C5 ran:** C5 is the last tuning run;
  the primary campaign then runs with whatever design exists, and the pre-registered rules classify it.
  Result: SUCCESS at 3392 ticks with **0 stalls, 0 recoveries**. The bot never went to the pond in this launch,
  so the run-to-run route varies even with a fixed seed and the stall is not deterministic. C5 is no evidence for or
  against the final zone design; that design was first exercised on a trap only in the primary campaign.
- Smoke totals (all `fuguer`, candidate 1, excluded from classification): C–C4 TIMEOUT (trap, design under
  correction), C5 SUCCESS (no trap). No crash, freeze, release timeout or false success in any of them.
- **Caveat on the evaluation.** Smoke runs C–C4 tuned the recovery action on candidate 1, which is also a primary
  evaluation spot. Candidate 3 (and cancel at candidate 2) was not used for tuning. The two spots are reported
  separately in the results.

### 8.2 Primary protocol

- **Arms:** A = `stock`, B = `fugue` (recovery off), C = `fuguer` (recovery on, defaults in §3.2/§4).
- **Launch:** `fuguebench <arm> empty:2,cancel:1` with `-Dtenorclef.seed=12345 -Dtenorclef.fuguebench.spots=1,3,2`.
  - Trial 0 = empty at candidate 1 (v1 idx0).
  - Trial 1 = empty at candidate 3 (v1 idx2).
  - Trial 2 = cancel at candidate 2 (cancel at 800 ticks).
  - Each launch is a fresh world, so every spot is untouched.
- **Sample:** 10 launches per arm, so 20 empty trials per arm (10 per stall-prone spot) and 10 cancel trials per
  arm. Order is interleaved A, B, C, A, B, C, …
- **Budget:** the same fixed 8400-tick limit for every trial (v1's).
- **Primary metric:** verified success (result SUCCESS and an iron pickaxe seen by the harness) on the 20 empty
  trials per arm.

**Classification (applied mechanically to the primary sample):**

- **A — demonstrated improvement.** All of:
  - (a) C ≥ B + 3 and C ≥ A + 3 successes out of 20;
  - (b) one-sided Fisher exact p < 0.10 for C vs B and for C vs A;
  - (c) ≥ 2 C trials in which a recovery fired and the trial then succeeded;
  - (d) no regression: 0 client freezes, every C cancel trial CANCELLED with no pickaxe, and C's median ticks over
    successful trials ≤ 1.15 × B's.
- **D — regression.** Any of: C ≤ B − 3 successes; a freeze or hang attributable to recovery; a C cancel trial not
  CANCELLED; a false SUCCESS.
- **B — verified recovery capability.** Neither A nor D, and at least one live C trial in which a recovery fired,
  the child moved to a non-excluded target (no INEFFECTIVE) and item progress resumed (RESUMED).
- **C — inconclusive.** Otherwise.

**Secondary (reported, not used for the class):**
- median ticks of successful trials per arm and spot
- trials with a heartbeat-visible water stall (≥ 2 consecutive heartbeats with an `Escaping water`/`get out of
  water` leaf and no change in the target count), per arm
- recoveries, resumed and ineffective counts
- distance, tables, deaths
- probe overhead
- freezes
- cancel correctness

**Harness vs bot failures (decided now):**
- **Invalid (harness):** the launch never reaches the world, a bench exception, no land spot, a death within the
  first 200 ticks of a trial (teleport/fall), or a client crash whose stack is in harness code. Invalid trials are
  reported and the launch is re-run once, labelled as a re-run.
- **Bot failure (counts as failure):** anything else, including TIMEOUT, FAILED, a death after 200 ticks, or a
  missing pickaxe.

**Harness incidents during the campaign (recorded when observed, before later launches ran):**
- **B01 (19:30):** the client lost focus at launch. With no `options.txt`, pause-on-lost-focus froze the
  singleplayer world ("Saving and pausing game"). Trial 0 recorded `ticks=0` over 300 s of wall time and then
  FAILURE; the client stopped at 19:36 after 1 of 3 trials. Harness-invalid. Fixed for later launches (§7); B01 is
  re-run once at the end as `f11-B01r`.
- **A01 cancel trial:** it started with the previous trial's full inventory, including the iron pickaxe, so the
  bench's server-side `inventory.clear()` did not take effect. The stock task finished at 0 ticks (SUCCESS,
  `success=false`). Harness defect, intermittent (the clear worked between A01 trials 0 and 1 and throughout C01).
  It was not fixed mid-campaign, because `run-once` rebuilds the jar on every launch.
- **Rule set at 19:45, after seeing only A01/B01/C01:** a trial ending at `ticks == 0` is harness-invalid (paused
  world or carried-over inventory) and is excluded from all arms. It is applied by the analysis script.

**Live observation during the campaign (owner, watching C02 / B03): "1-deep water was making the bot move in tiny
increments."** Read against the code and the C02 log (unverified hypothesis, not instrumented):
- The pond spot is 1-deep water over solid ground. Walking there makes `isOnGround()` flicker.
- `DestroyBlockTask` treats `wet && !grounded` with the target out of reach as bobbing. On every flicker it calls
  `cancelEverything()` and returns `GetOutOfWaterTask`.
- On the next grounded tick it drops the escape and paths toward the block again.
- Baritone therefore gets only a few ticks per path before the next cancel, and the bot creeps.
- C02's probe counted `escapes=28` in 400 ticks, about one flip every 14 ticks, which fits this.
- `SafeRandomShimmyTask`, which sneaks, is *not* the cause: zero "Random Orientation" lines (it needs throwaway
  blocks, which the bot had none of at this stage).
- `EscapeFromWaterGoal` is never reached either: zero "S183 … confirmed dry" lines.
- Consequence for v1.1: excluding targets cannot fix this once the bot stands in the pond. C02's third recovery
  moved the target 130 blocks away and the bot still did not leave. The defect is in stock `DestroyBlockTask`'s
  bob handling (two competing navigation owners alternating), so it affects every arm whenever the bot enters
  that water.
- Not changed mid-campaign. Candidate next slice: escape hysteresis in `DestroyBlockTask` (once the escape starts,
  keep it until `GetOutOfWaterTask.isFinished()`; do not treat ground-flicker over a solid floor in 1-deep water
  as bobbing). It would be classified separately, like the DeathMenuChain fix, and tested with supplementary runs.

### 8.3 Results (Measured)

Campaign 19:28–23:56, then `f11-B01r` 23:57–00:04. All 30 launches reached the world, plus the re-run, with no
client crash, no freeze and no death. Each launch logs the same startup exceptions (2 `ClassNotFoundException` and
1 `RuntimeException`) in every arm. There is no crash report. The analysis is `/tmp/f11/analyze.py`, run unchanged
apart from the two pre-declared rules in §8.2 (ticks == 0 invalid; `.err.log` excluded).

**Invalid and re-run:** B01 trial 0 (paused world) was invalid; B01 was re-run as `f11-B01r` (both empty trials
SUCCESS, 2856 and 2837 ticks). The A01 cancel trial (inventory carry-over) was invalid; there was no re-run,
because the primary metric counts empty trials only.
**Deviation (my error):** `f11-B01r` was launched with plan `empty:2` and no `cancel:1`, so arm B has 9 cancel
trials, not 10. The primary sample is complete (20 empty trials per arm). The cancel criterion in §8.2 applies to C
only, so the class is unaffected.

**Primary: verified successes on empty trials (20 per arm)**

| Arm | Success | Candidate 1 | Candidate 3 | Median ticks (successes) | Cand 1 median | Cand 3 median |
|---|---|---|---|---|---|---|
| A stock | **18/20** | 8/10 | 10/10 | 2758 | 2773 | 2758 |
| B fugue | **19/20** | 9/10 | 10/10 | 3793 | 4280 | 3275 |
| C fuguer | **16/20** | 6/10 | 10/10 | 3123 | 2914 | 3207 |

- One-sided Fisher exact: C > B p = 0.976; C > A p = 0.909.
- Every failure in every arm is a TIMEOUT at candidate 1 with the bot standing in the same 1-deep pond
  (x 574–588, y 62, z −45..−60): A06, A08, B08, C02, C04, C07, C09. There are no FAILURE, death or false-SUCCESS
  results.
- Candidate 3 (not used for tuning) is 10/10 in every arm. No recovery ever fired there.

**Cancel trials (candidate 2, cancel at 800 ticks):**
- A: 9/9 CANCELLED.
- C: 10/10 CANCELLED.
- B: 8/9 CANCELLED. `f11-B05` reported FAILURE at tick 801: `TARGET_UNAVAILABLE: Follow target entity unavailable`
  from a `PickupDroppedItemTask` leaf.
  - Cause (Verified in code): `Task.stop()` turns only a null, RUNNING or RETRY result into CANCELLED; it keeps a
    FAILURE the parent had absorbed from its child (`TaskResultMapper.shouldAbsorbChild`). The child failed
    transiently in the cancel tick.
  - The cancel itself took effect (the task stopped at 801, no pickaxe). This is stock task-system result
    semantics, reachable by any arm, not a recovery defect. Arm B has recovery off.
- No cancel trial in any arm ended with a pickaxe.

**Recovery (C):**
- 12 recoveries in 4 trials (C02, C04, C07, C09), 3 each, so every per-node or per-objective bound was reached.
- 0 RESUMED, 0 INEFFECTIVE, 0 COULD-NOT-EXCLUDE.
- All 4 trials timed out. No recovery fired in any of C's 16 successes.
- Every recovery released the child in 1 tick.

**Secondary: heartbeat-visible water stall** (≥ 2 consecutive heartbeats with a water-escape leaf and an unchanged
count):

| Arm | Empty trials with a water stall |
|---|---|
| A | 3/20 |
| B | 1/20 |
| C | 4/20 |

**Exploratory, post-hoc (not pre-registered; it explains the result and does not change the class):** recovery
caused the trap in at least 3 of C's 4 failures.

The heartbeat (every 400 ticks) shows the same early pattern at candidate 1 in both Fugue arms: the bot is wet in
the pond at t ≈ 1200 while its `log` node targets the shore log 587,63,−53.

| Arm | Trials wet at the log stage | What happened next | Outcome |
|---|---|---|---|
| B | 5 (B02, B03, B04, B06, B09) | dry again by the next one or two heartbeats; it took the shore log and started `cobblestone` from dry land | 5/5 success |
| C | 4 (C02, C04, C07, C09) | see below | 0/4 success |

What happened in C:
- In C02, C04 and C07 the stall classifier saw `idle=400 escapes≈28` and fired RECOVER #1 on that log. The zone
  exclusion removed the shore log and 127 neighbours. The replan "moved on from log to planks" with 2/3 logs held,
  so the next resource node (`cobblestone`) started **while the bot was still in the pond**.
- Its nearest stone was under the pond (578,57,−50), the 1-deep-water loop (§8.2 observation) took over, and every
  later heartbeat is wet.
- C09 was wet at the log stage, then dry, then wet again at the cobblestone stage. Its first recovery was on
  cobblestone.
- Conditional on "wet at the log stage", B succeeded 5/5 and C 0/4 (Fisher p ≈ 0.008). This is a post-hoc subgroup,
  so it is evidence for a mechanism, not a pre-registered result.

Mechanism (Unverified hypothesis, supported by these logs): the 400-tick window classifies a *transient* water
episode (one that stock navigation leaves on its own) as an environmental trap. The recovery then removes the very
target that would have walked the bot out, and leaves it standing in the water for the next node.


## 9. Performance (Stage 9, Measured)

From the campaign CSVs (`versions/1.16.1/run/fuguebench/fuguebench_*_20261101_19*–23*.csv`, every trial of the
arm):

| Arm | Trials | Probe µs per probed tick, median (max) | Worst single probe | planUsMax median (max) |
|---|---|---|---|---|
| B fugue (recovery off) | 28 | 0 (0): probes do not run | 0 | 562 µs (16.8 ms) |
| C fuguer | 30 | 3.3 (11.0) | 13.1 ms | 616 µs (18.1 ms) |

- The worst planning call in each arm is the cold first plan.
- `FuguePlanColdStartTest` (3 cold JVMs): first plan 2.4–2.8 ms, second 0.13–0.26 ms, steady state 18–24 µs. It is
  class loading and interpreted code, not steady-state cost.
- The 13 ms worst probe in C is one outlier (the median per-launch maximum is 156 µs).
- With recovery off, v1.1 adds no per-tick cost.


## 10. Classification (Stage 10)

**Fugue v1.1 stall recovery: D — regression** (mechanical, §8.2).
- The rule "C ≤ B − 3 successes" holds: C 16/20 vs B 19/20.
- The other D triggers did not occur: no freeze or hang, every C cancel was CANCELLED, no false SUCCESS.
- A is not met: C is below both arms, p = 0.976 / 0.909, and no recovery was followed by success.
- B would not be met either: 0 RESUMED.
- The post-hoc analysis (§8.3) gives a concrete mechanism by which the recovery *causes* the loss, so the class is
  not just noise at n = 20: the 3-success gap is exactly the C02/C04/C07 pattern.

**Defaults:** `-Dtenorclef.fugue.recovery` stays **false**; Fugue itself stays disabled by default. Recovery off is
v1 behaviour exactly (unit-tested; arm B's 19/20 is the live check).

**DeathMenuChain render-thread fix: classified separately — correctness fix, Unit-tested (7/7), not live-exercised.**
- The campaign had 0 deaths (Peaceful), so the fixed path never ran live.
- No freeze occurred in 90 live trials across 31 launches with the fix in the jar. This shows it is harmless on the
  normal path, not that the freeze path is fixed.
- It is independent of recovery and can be kept regardless of the D above.

**What is known:**
- (Measured) Stock, Fugue and Fugue+recovery all lose trials to the same 1-deep pond at candidate 1.
- (Measured) Excluding targets, even a 16-block zone, never freed a bot already in that water: 0 of 12 recoveries
  resumed, including a third recovery whose next target was 130 blocks away.
- (Measured) Recovery fired only at that pond.

**What is not known:**
- Whether the `DestroyBlockTask` flip-flop described in §8.2 is the exact creep mechanism. It is not instrumented;
  the escape count and the owner's observation fit it.
- Whether a later or condition-aware trigger would avoid the harm.

**Recommended next slice (not done; each item to be classified on its own):**
1. **Stock fix, outside Fugue:** escape hysteresis in `DestroyBlockTask` (keep `GetOutOfWaterTask` until it
   finishes; do not treat ground-flicker over a solid floor in 1-deep water as bobbing). It addresses the actual
   failure condition for every arm. Verify with supplementary runs at candidate 1 and a heartbeat water-stall count.
2. **If Fugue recovery is kept at all:** do not fire while the bot is wet and stock escape is still active. Treat
   "wet" as a reason to wait, not to exclude, and only exclude targets once the bot is dry and grounded but still
   not progressing. This would be a new design and needs a fresh pre-registered campaign.
3. **Harness:** fix the intermittent `inventory.clear()` carry-over (A01) and have cancel trials record the task's
   explicit result separately from absorbed child failures (B05).


## 11. Files and repository state

Everything is **uncommitted** on TenorClef `main` at `983cf317`. Nothing was pushed, merged, stashed, reset or
cleaned.

**v1.1 changes (TenorClef):**

| File | Status at start | v1.1 change |
|---|---|---|
| `chains/DeathCommandQueue.java` | new | non-blocking death-command queue (Stage 1) |
| `chains/DeathMenuChain.java` | clean | render-thread spin replaced by the queue |
| `trackers/blacklisting/AbstractObjectBlacklist.java` | already M (pre-existing work kept) | `+forget(item)` |
| `trackers/BlockScanner.java` | already M (pre-existing work kept) | `+forgetUnreachable(pos)` |
| `fugue/FugueCoordinator.java` | v1 (untracked) | stall observation/classification, RECOVER, bounds, exclusions |
| `fugue/FugueTask.java` | v1 (untracked) | target/escape probes, RECOVER handling, release on stop, probe timing |
| `fugue/FugueWorldView.java` | v1 (untracked) | `navigationIdle`, `exclude`, `forget` |
| `fugue/LiveFugueView.java` | v1 (untracked) | the live implementations (zone exclusion) |
| `commands/FugueCommand.java` | v1 (untracked) | reads `-Dtenorclef.fugue.recovery` (default false) |
| `commands/FugueBenchCommand.java` | v1 (untracked) | help text `<stock|fugue|fuguer>` |
| `benchmark/FugueBench.java` | v1 (untracked) | arm `fuguer`, fixed spots, heartbeat, CSV columns (1.16.1 only) |
| `test/.../chains/DeathCommandQueueTest.java` | new | 7 tests |
| `test/.../fugue/FakeWorld.java` | v1 (untracked) | fake exclusion/forget/navigation |
| `test/.../fugue/FugueRecoveryTest.java` | new | 21 tests |
| `test/.../fugue/FugueRecoveryTaskTest.java` | new | 6 tests |
| `test/.../fugue/FuguePlanColdStartTest.java` | new | Stage 9 measurement |
| `FUGUE_V11_HANDOFF.md` | new | this document |

- `git status --porcelain` now differs from the start snapshot (`/tmp/f11/tc-status-start.txt`) by exactly 4 lines:
  ` M chains/DeathMenuChain.java`, `?? FUGUE_V11_HANDOFF.md`, `?? chains/DeathCommandQueue.java`,
  `?? src/test/.../chains/`. The other files above were already modified or untracked at the start.
- Every other pre-existing modification is untouched.
- Ostinato-ppe (`feat/predictive-physics` `1aed5cc2`): `git status --porcelain` is identical to the start snapshot.
- Maestro, Cadenza, PPE, Legato and Presto were not modified.
- Live artefacts:
  - logs: `logs/sim-run-f11-*.log`
  - CSVs: `versions/1.16.1/run/fuguebench/fuguebench_*_20261101_*.csv`
  - campaign driver: `%TEMP%/f11_campaign.ps1`
  - analysis script: `/tmp/f11/analyze.py`
