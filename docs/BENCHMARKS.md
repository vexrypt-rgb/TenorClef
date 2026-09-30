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

## Travel misses, 2026-09-28 (3 reps each, seed 12345)

Source: `pathbench_travel_baritone_20260928_205606.csv` (47/48) and
`pathbench_travel_kinematic_20260928_203533.csv` (46/48). CSVs are local, not committed.

| mover | goal | offset (dx,dz) | rep | result | ticks | endDist |
|---|---|---|---|---|---|---|
| baritone | 15 | 68,-68 | 0 | STALLED | 715 | 44.2 |
| kinematic | 12 | -96,0 | 0 | STALLED | 967 | 17.3 |
| kinematic | 14 | 0,-96 | 1 | STALLED | 704 | 47.7 |

Every miss is one rep of a 96-block goal; the other reps of the same goal reached it, and no goal
failed for both movers. So there is no repeat trouble spot in these runs. Those runs logged only the summary line, so
their stall causes are unknown.

Since then each travel trial also logs one line (`grep -a "PATHBENCH TRIAL" run/logs/latest.log`):

```
PATHBENCH TRIAL mover=kinematic goal=14 rep=0 result=GOAL ticks=338 end=53, 74, -217 endDist=1.9 bestDist=3.0 bestAt=335 lastMoveAt=337 firstMove=11 activeAtEnd=true
```

`end` is the final block position; `bestDist`/`bestAt` are the closest approach and when it happened
(only gains over 1 block count); `lastMoveAt` is the last tick with movement over 0.3 blocks;
`activeAtEnd` is whether the mover was still running before the bench cancelled it. A stall shows as
a large `bestDist` with `bestAt` well before `ticks`, and `end` gives the place to inspect.
Tested in-game on goals 12, 14, 15 (kinematic, 1 rep): 3/3 reached, one line per trial.

## Travel re-run with per-trial lines, 2026-09-28 21:30–22:05 (3 reps each)

| mover | goals reached | avg ticks (reached) | avg ticks to first move |
|---|---|---|---|
| baritone | 48/48 | 362 | 8.8 |
| kinematic | 46/48 | 393 | 10.5 |

Kinematic stalls, from the `PATHBENCH TRIAL` lines:

| goal | rep | end | endDist | bestDist | bestAt | lastMoveAt | ticks | reading |
|---|---|---|---|---|---|---|---|---|
| 11 (-68,68) | 0 | 57, 88, -27 | 68.4 | 66.2 | 546 | 925 | 949 | kept moving for ~380 ticks without getting closer: wandering, at y=88 (well above the ~70 start) |
| 13 | 2 | -4, 83, -171 | 11.4 | 11.9 | 660 | 664 | 1063 | stopped moving 11 blocks short and stayed put for ~400 ticks |

In both, `activeAtEnd=true`: the mover still reported itself active, so these are silent stalls, not
aborts. Goals 11 and 13 did not stall in the earlier 20:35 run, so neither is a repeat spot yet. The
client log has no kinematic-mover output around either trial. Kinematic movement changes are frozen,
so these are recorded, not fixed.

### Kinematic after merging Ostinato `origin/1.16.1` (2026-09-28 23:18)

Ostinato's `claude/blissful-keller-dyrn6h` merged 12 kinematic/jump/slime commits from `origin/1.16.1`.

- **Full run `pathbench travel kinematic 3`:** 42/48 goals, average 389 ticks. The previous result was 46/48.
  - Four trials stalled motionless, with the custom goal process still active and nothing logged:
    - goal 4, reps 1 and 2, both at 46,73,-102;
    - goal 6, rep 0;
    - goal 7, rep 0.
  - Two misses followed a player death: goal 11 rep 1 and goal 13 rep 1. In each, `Death position saved.` appeared about 20 s before the trial ended. The cause of death was not captured.
- **Subset rerun of goals 4, 6, 7, 11 and 13 (3 reps each):** 12/15.
  - Goals 4, 7, 11 and 13 reached the goal 3/3, so none of the stalls reproduced deterministically.
  - Goal 6 ended `STOPPED` 2.9 blocks from the goal in all 3 reps: the process finished, `activeAtEnd=false`.
- **Movement faults:** Ostinato's `movementFault` sink defaults to a no-op, so PathBench now logs each fault as a `PATHBENCH FAULT <code> <evidence>` line during travel runs. No faults fired in the subset rerun. The kinematic M01 watchdog (motionless for more than 20 ticks while the controller is driving) was therefore never triggered.
- **Open:** in these runs the silent stalls come from a path where the kinematic controller is not driving, since M01 never fired. That points at Baritone's own executor, or at the controller returning -1 every tick, not at the M01 watchdog. The runs do not yet show which.

### Kinematic re-run with driver attribution (2026-09-29)

`PATHBENCH TRIAL` lines now also carry these fields:

- `pathAtEnd`: whether Baritone held a path when the trial ended.
- `calcAtEnd`: whether a path calculation was still in progress.
- `kinDriven`: ticks the kinematic controller drove during the trial. It is read reflectively from `KinematicController.drivenTicks`.
- `kinSinceBest`: driven ticks after the last progress gain of more than 1 block. 0 means Baritone, not the kinematic controller, had the wheel during a stall.

Full `pathbench travel kinematic 3`: 46/48, average 459 ticks.

- No trial was `STALLED`.
- Both misses were goal 12: reps 0 and 2 ended `STOPPED` at -34,75,-122, 2.7–2.8 blocks out. The goal process finished (`activeAtEnd=false`, no path) and `kinSinceBest=0`. The bench needs d < 2.0.
- Two mob deaths were logged ("slain by Spider", "slain by Zombie"), and neither cost a goal. Earlier deaths were creepers. Mobs are bench noise, not movement faults.
- Faults are now visible:
  - M04 (movement UNREACHABLE, mostly `MovementDiagonal`)
  - M03 (a traverse took 104 ticks)
  - M02 (too far from path)

  No M01 (kinematic stuck) fired.

Kinematic across two full runs after the merge: 42/48, 46/48. The last run before the merge was 46/48. The silent motionless stalls from the 23:18 run did not recur, so their driver is still unattributed.

### Goal 12 rerun with target logging

A goal-12-only rerun scored 3/3 GOAL, averaging 445 ticks. The TRIAL `target=` field shows the goal is `-36,75,-121` on `grass_block`, not on a leaf canopy, so the canopy hypothesis is refuted. The earlier STOPPED misses followed a 7.2-block partial path and then M04 (MovementDiagonal UNREACHABLE). That makes them intermittent Baritone partial-path endings, not a bad goal.

### Goal 6 rerun with target logging

A goal-6-only rerun scored 0/3. All three reps were STOPPED 2.9 blocks out, all ending at `70,71,-150`. The target is `70,82,-153` on `spruce_leaves`, 11 blocks above where the player ends up, on top of a spruce canopy. That comes from `surfaceY`, which uses the MOTION_BLOCKING heightmap, and that heightmap counts leaves (client worlds get no NO_LEAVES map). Baritone's 30-block path ends under the tree and the process finishes with no faults and no stall.

So goal 6's repeated miss is caused by where the bench put the goal, not by a mover fault. The goal set has been left unchanged to keep past runs comparable. Scanning past leaves in `surfaceY` would change the benchmark and should come with a new baseline.

### New baseline: goals placed below leaf canopies (changed goal set)

`surfaceY` now drops through leaves, so every goal sits on a non-leaf block (all 16 are on `grass_block` in this world). This is not directly comparable with earlier runs.

The full run scored **45/48** at an average of 358 ticks, with no STOPPED trials. The three misses were all STALLED:

| Goal | Rep | Distance out | Progress stopped at | `kinSinceBest` |
|---|---|---|---|---|
| 12 | 0 | 27.3 blocks | tick 480 | 235 |
| 14 | 0 | 10.2 blocks | tick 563 | 234 |
| 15 | 1 | 15.5 blocks | tick 308 | 233 |

**Correction:** an earlier version of this note blamed a kinematic no-progress loop. That was wrong; it was based only on the TRIAL lines. The full log shows each stall began with a mob kill: goal 12 rep 0 and goal 14 rep 0 by creepers, goal 15 rep 1 by a skeleton. After each death, M01 fired three times at the same block and M03 fired once, and the trial then ran out its stall window. So all three misses are mob deaths (bench noise), and no mover fault is shown.

The travel loop had no death detection. TRIAL lines now carry `deaths=`, and the SUMMARY line carries `missesAfterDeath=`, so these misses can be attributed without changing how trials are scored.

**Rerun of goals 12, 14 and 15 with death counting:** 9/9 GOAL, `deaths=0` in every trial, average 447 ticks. None of the three stalled when no mob killed the player.

Two caveats found in this run:
- The goal ring is built around each run's start position, which varies. Goal 12 was `-31,73,-110` in the full run and `-28,72,-104` here, so goal indices are not fixed coordinates across runs.
- Hits are measured on XZ only. Goal 15 (`136,78,-172`) counted as GOAL with the player 4–7 blocks above it (y=82–85).

### Pinned ring origin (current baseline)

Travel mode now centres the goal ring on a fixed `x=65, z=-110` (`-Dtenorclef.pathbench.origin=x,z` to move it, `=spawn` for the old behaviour). Before, the start position ranged over about 10×17 blocks, which moved every goal. The pinned origin resolved to `65,72,-110`, the same origin as the leaf-free baseline, so goal coordinates match that run.

The full run scored **47/48** at an average of 407 ticks, with `missesAfterDeath=1`. The only miss, goal 14 rep 2 (STALLED 14.4 blocks out), came after the player was slain by a spider. With deaths excluded, the score is 47/47. No STOPPED trials.

Hits are still scored on XZ distance only (see the caveat above).

### Peaceful travel (current baseline)

Travel trials now run on peaceful by default (`-Dtenorclef.pathbench.peaceful=false` keeps mobs), and the world's difficulty (easy) is restored after the run. The origin is pinned as above.

The full run scored **48/48** at an average of 264 ticks (407 with mobs), with `missesAfterDeath=0` and no STALLED or STOPPED trials. The speed-up probably comes from no longer fighting or dodging mobs; that is inferred, not measured. *Superseded:* most of the gap came from hunger carrying over between trials. With every trial fed, mobs-on scores 48/48 at 233 (see "Full mobs-on rerun with hunger reset" below). With goals on the ground, a pinned origin and no mobs, this is the reference number for kinematic travel.

## Baritone vs kinematic (peaceful, pinned origin 65,72,-110, 3 reps x 16 goals)

| mover | goalRate | avgGoalTicks | avgFirstMoveTicks | deaths |
|---|---|---|---|---|
| kinematic | 48/48 | 264 | — | 0 |
| baritone | 48/48 | 293 | 8.9 | 0 |

Kinematic was faster on 14 of the 16 goals, by about 20–35% on most. It was slower on goal 2 (323 vs 140) and goal 10 (660 vs 388), which are worth investigating. Overall it was about 10% faster.
Caveat: a goal counts as reached on XZ distance only (< 2.0); this is one run per mover.

## Kinematic sync fix (Ostinato `KinematicController.syncPosition`)

A per-tick trace (`-Dtenorclef.pathbench.trace=65,-102.5`) showed what slowed kinematic down on goals 2 and 10. The kinematic mover cut into column x=66 and passed the end of the traverse 65,74,-103→-102 at z≈-100.3, but the step counter stayed on that traverse. Baritone kept walking back toward the step's end while the kinematic mover pushed forward, until the traverse timed out (M03, 104 ticks).
Fix: a move now also counts as done once the player's position along the path is 0.5 past its destination, subject to the same floor-height check.

| run (peaceful, pinned origin, 3 reps x 16 goals) | goalRate | avgGoalTicks |
|---|---|---|
| kinematic before | 48/48 | 264 |
| kinematic after | 48/48 | 232 |
| baritone | 48/48 | 293 |

Goal 2 went from 323 to 131 ticks (Baritone 140), and goal 10 from 660 to 387 (Baritone 388). Other goals moved by less than 20 ticks, except 12 (350→310) and 15 (359→321). The M03 faults are gone. One M04 on goal 8 was present before too.
Caveat: this is one run, and a hit is still counted on XZ distance only.

### Sync fix with mobs (difficulty easy, pinned origin)

The full run scored **47/48** at an average of 378 ticks, against 407 before the fix. The single miss was goal 15 rep 0 (STALLED), and it came after a death (`missesAfterDeath=1`), so the score is 47/47 with deaths excluded, the same as before the fix. No M03 fault appeared on the goal 2/10 traverse. One run of each, and mob variance is large. *Superseded:* this run carried hunger over between trials, so later trials walked instead of sprinting. The current mobs-on figure is 48/48 at 233; see "Full mobs-on rerun with hunger reset" below.

The run also logged one M03 on a diagonal at 109,71,-172 (goals 12–15 heading), together with 3× M01 at 110,71,-172. The peaceful run did not log these, so it may be mob-related. Later traces (see "Trace at 110,71,-172") and the fed mobs-on rerun did not reproduce them, so the cause is still unknown.

## Trace at 110,71,-172 (goals 12–15, kinematic, seed 12345)

Follow-up on the M01/M03 seen at 109–110,71,-172 in the mobs-on run.

| Run | Goals | Avg ticks | M01/M03 at spot |
|---|---|---|---|
| Peaceful | 12/12 | 342 | none; route never comes within 3 blocks of 110,-172 |
| Mobs on | 12/12 | 498 | none |

- With mobs on, goals 14 and 15 took 584–661 ticks (306–358 in peaceful). 110,-172 is on the way to goal 15 (133,-178). *Correction below: nothing shows goal 14 going through it.*
- On that route the trace shows:
  - a 3–4 tick Baritone takeover on the diagonal 109,71,-172;
  - about 10 ticks of horizontal collision at 110.7,72,-173.5 while stepping down to the traverse at y=70.
- The mover recovers on its own both times.
- The earlier M01/M03 did not reproduce, so it is not attributed to the mover or to mobs. It is still unexplained.

### Goal 14 alone (route breadcrumbs, `-Dtenorclef.pathbench.route=20`)

| Run | Ticks | Route |
|---|---|---|
| Peaceful | 356 | 65,-110 → 73,-121 → 71,-137 → 68,-160 → 72,-195 → goal |
| Mobs on | 345 | the same corridor, finishing along x=65 |

- Run by itself, goal 14 takes the direct route in both modes and costs no extra time with mobs on.
- So the 584–661 ticks for goals 14 and 15 in the 12–15 mobs run are not a route choice caused by mobs. They depend on state left by the earlier trials (for example world time or mobs that have gathered). That part is not yet measured.

### Why goals 14–15 were slow with mobs on: hunger, not routing

New opt-in logging:
- `ROUTE` breadcrumbs now include player `age`, `food` and `sprint`.
- A `TRIALSTATE` line (mobs-on only) records time of day and hostile mobs within 32 blocks.

Goals 12–15, mobs on, kinematic, 3 reps:
- The whole run is daytime (timeOfDay 175 → 5598), with 0–13 hostiles near the origin.
- Player `age` tracks world ticks 1:1, so there is no client lag.
- Food falls from 20 to 0 across trials. On easy difficulty it doesn't regenerate, and sprint-jumping drains it fast.
- Sprinting stops once food is 6 or below. That happens in goal 13 rep 2 (540 ticks), and every later trial walks (568–664 ticks) along the same route.

Fix (bench only): `teleport()` now sets food to 20 at the start of every trial, so trials no longer depend on the ones before them.

| Goals 12–15, mobs on | Avg ticks | Goal 14 | Goal 15 |
|---|---|---|---|
| Before (hunger carried over) | 497 | 568–607 | 628–664 |
| After (fed each trial) | 350 | 308–354 | 362–364 |
| Peaceful, for reference | 342 | 306–345 | 343–358 |

- 12/12 goals reached, with no M01/M03.
- The earlier 47/48 mobs-on result (378 avg) was measured with hunger carried over, so it overstates the cost of mobs.

## Full mobs-on rerun with hunger reset

`pathbench travel kinematic 3`, mobs on, every trial fed to 20 (log `obj-mobsfed-1790669557.log`):
- **48/48 GOAL, 233 ticks on average.** First move comes at 10.0 ticks on average, and the average end distance is 1.7.
- 0 deaths and no M01/M03/M04 lines.
- The peaceful reference is 48/48 at 232. This result replaces the old mobs-on 47/48 at 378. That gap was hunger carrying over between trials, not mobs.

## Peaceful rerun with hunger reset

The same bench on peaceful, every trial fed (log `obj-peacefed-1790670955.log`): **48/48 GOAL, 230 ticks on average**, first move at 10.7 ticks on average, 0 deaths, one M-fault line.
The old peaceful reference was 232, so hunger did not skew it. Peaceful already stops hunger from dropping, so feeding makes no difference there. Fed trials score 230 on peaceful and 233 with mobs on: mobs add no measurable travel cost.

The one fault line is `M04 MovementDiagonal UNREACHABLE at 88,71,-133`. It is logged in the same second goal 7 rep 2 finishes, at goal 7's target, before goal 8 starts. This matches the known "one M04 on goal 8" from the sync-fix run. It looks like goal 7's leftover path being dropped at the teleport, not a failure: goal 8 then scored 3/3 GOAL. That reading is inferred from timing and position, not traced.

*Correction:* the teleport explanation is wrong. With the end-of-trial cancel made synchronous (`mc.submit(...).get()`), a peaceful rerun (`obj-peacecancel-1790672133.log`, 48/48 at 235) still logged the same M04. It came after the goal 7 rep 2 TRIAL line and at the player's own feet, 88,71,-133, so the player had not been teleported yet. `PathingBehavior.cancelEverything()` only drops the path when `isSafeToCancel()` holds. Mid-diagonal it doesn't, so the rest of goal 7's path keeps running after the bench counts the goal (XZ < 2), and that diagonal really returns UNREACHABLE. The change was reverted, since it did not fix anything. The fault looks like a genuine diagonal failure next to goal 7's target, not a bench artifact. That is not traced yet.

### Goal 7 M04, traced

This run was goal 7 only, 3 reps, peaceful, with `-Dtenorclef.pathbench.trace=88,-133 -Dtenorclef.pathbench.tail=40` (log `obj-t88-1790673332.log`). The new `tail=N` option keeps the path running N ticks after a trial ends and logs each poll as `PATHBENCH TAIL`.

- 3/3 GOAL. The M04 appeared once, in the tail after rep 1.
- The final movement of the path (pos 29) is `MovementDiagonal 86,70,-131 -> 87,70,-132`. The kinematic mover takes it airborne (y 72.25, vel ≈ 0.21,0,-0.22, not on ground), so the bench's XZ < 2 check counts the goal mid-jump.
- The player's momentum carries it past the diagonal's destination to 88,71,-133. That block is neither the source nor the destination, so the diagonal returns UNREACHABLE. It lands at 89.05,70.68,-133.08 a few ticks later.
- So the fault is the kinematic mover **overshooting the last movement of a path while sprint-jumping**. It is not a teleport artifact or a blocked diagonal. It happens after the goal counts, so it doesn't affect scores. In normal use it would cancel a path that was already at its end.
- Side finding: `execState` logged `IndexOutOfBoundsException: Index -1` once the path was empty. Fixed: it now reads the executor once and logs `mv=none` for an empty path. The rerun (`obj-t88b-1790673688.log`, 3/3, the same M04) has 0 `exec?` lines.
- Tick counts in this run (132–180) are not comparable with other runs. The trace was logging every poll inside the 3-block box.
