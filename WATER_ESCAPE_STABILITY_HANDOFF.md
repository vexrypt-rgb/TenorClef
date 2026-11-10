# Water-Escape Stability & Cancellation Correctness — Handoff

_Status: complete. All changes are uncommitted._

Evidence labels: **Verified** (read in code, or checked by a deterministic test), **Measured** (live logs),
**Reported** (the owner's observation), **Contradicted**, **Unknown**.

## 1. Repository state

| Repo | Branch | HEAD | Uncommitted entries at start → now |
|---|---|---|---|
| TenorClef-main | `main` | `983cf317` | 131 → 139 |
| Ostinato | `swarm/s2s-identity-1161` | `3d0b1674` | 4 → 4 (not touched) |
| Ostinato-acs-263 | `acs-263` | `ce497e2d` | not touched |

The start snapshots are in `%TEMP%\wes\tc-start.txt`, `os-start.txt` and `osppe-start.txt`. Nothing was committed,
pushed, stashed, reset or cleaned. The 8 new entries in TenorClef are all from this work:

| File | Status at start | Change |
|---|---|---|
| `tasks/construction/DestroyBlockTask.java` | clean | the fix (escape latch + legacy flag) and trace hooks |
| `tasks/construction/WaterEscapeLatch.java` | new | latch / re-entry bound; no Minecraft types |
| `tasksystem/Task.java` | clean | cancellation fix in `stop()` |
| `tasks/movement/GetOutOfWaterTask.java` | clean | two trace event calls only, no behaviour change |
| `util/WaterEscapeTrace.java` | new | opt-in bounded trace (`-Dtenorclef.waterTrace=true`) |
| `test/.../tasks/construction/WaterEscapeLatchTest.java` | new | 13 tests |
| `test/.../tasksystem/CancellationOutcomeTest.java` | new | 6 tests |
| `WATER_ESCAPE_STABILITY_HANDOFF.md` | new | this file |

`benchmark/FugueBench.java` was already untracked from Fugue v1/v1.1. It gained the `pond` scenario, the
`FUGUEBENCH POND` line and the per-trial `FUGUEBENCH WATER` summary. All of that is 1.16.1-only (`//$$`) and is
benchmark code only.

Cadenza, Maestro, PPE, Legato, Presto, Fugue and Ostinato were not modified.

## 2. Stage 0 findings

- **The pond stall is in TenorClef, not Ostinato (Verified + Measured).** The stall happens between
  `DestroyBlockTask` (TenorClef) and its child `GetOutOfWaterTask` (TenorClef). Baritone pathed when it was asked
  to and never wedged on its own: `path=true` within ~2 ticks of every `setGoalAndPath`. The trigger, the
  cancellation and the child handoff are all TenorClef code. No Ostinato change is needed or made.
- **Stock and Fugue share the path (Measured).** Heartbeats in both arms show the same chain:
  `Collect cobblestone > Mine And Collect > Destroy block > get out of water`. Fugue v1.1's A06 stall is this one.
- **Existing diagnostics could not see it (Measured).** The A06 log has only heartbeats and "Refreshed
  inventory". There is no per-tick record of the mining branch, the water state or who owned navigation, so the
  bounded `WaterEscapeTrace` was added (Stage 1).
- **Pond spot (Measured).** `579,62,-51` is itself dry (`FUGUEBENCH POND feetWater=false solidFloor=true`), but
  the stone under the adjacent 1-deep pond (577–578,62,-50/-51) is the nearest stone. The bot walks into the pond
  within seconds every time, so the spot reproduces the stall without placing the bot in water.
- **Not involved (Verified).** `GetOutOfWaterTask`'s pillar/shimmy branch needs throwaway blocks, and the bot has
  none at that point. `WaterBailTask` (testrun2) is not used by `DestroyBlockTask`.

## 3. Reproduction

`fuguebench stock pond:1` with `-Dtenorclef.waterTrace=true`, Fugue recovery off (stock arm), seed 12345, fresh
world.

- **Pilot `wes-pilot1` (pre-fix code):** TIMEOUT at 8402 ticks. The bot was at 577,62,-50 with cobblestone 0 at
  every heartbeat from t=400 to t=8400. Four targets (579,57,-51 → 578,57,-50 → 577,57,-50 → 576,57,-50) each hit 10
  escapes within the first 13 s, and the last one cycled for the rest of the trial. `S183 water escape confirmed dry`
  never fired.

  This matches the owner's reports "bot is stuck sitting in water" and "1-deep water was making the bot move in
  tiny increments" (Reported). The trace measured 0.86 blocks of horizontal movement in 115 ticks.

## 4. Observed transition sequence (Measured, `wes-pilot1` WATERTRACE dump #4)

The target is 5 blocks below the pond floor, so `reach=false` throughout. The cycle repeats every ~20–30 ticks:

```
t=327 PATH       ground=true  wet=true  path=true goal=EscapeFromWaterGoal  EVENT escape task stop finished=false
t=328 PATH       ground=true  wet=true  path=false goal=-                   EVENT setGoalAndPath toward target
t=329 PATH       ground=true  wet=true  path=false goal=GoalNear
t=330 BOB_ESCAPE ground=false wet=true  path=true goal=GoalNear  vy=+0.023  EVENT cancelEverything+escape  EVENT escape task start
t=331 BOB_ESCAPE ground=false wet=true  path=true goal=EscapeFromWaterGoal  EVENT cancelEverything+escape
  ... the same line and event every tick through t=348 (y oscillates 62.00–62.10, x creeps 0.01/tick) ...
t=349 PATH       ground=true  wet=true                                      EVENT escape task stop finished=false
t=350 setGoalAndPath toward target ... t=353 BOB_ESCAPE ... (repeat)
```

Against the brief's candidate list:

- **(1) Alternation between mining navigation and escape:** confirmed.
- **(2) Repeated navigation cancellation:** confirmed. The parent cancels its own child's path on every airborne
  tick.
- **Brief ground contact ending the escape:** confirmed. The first grounded-but-wet tick routes to PATH, the
  parent returns `null`, and the runner stops the escape with `finished=false`.
- **Throwaway/shimmy branch:** not involved.
- **Blacklist churn:** secondary. Targets rotate only after `MineAndCollect` gives up on each one.

## 5. Root cause

In `DestroyBlockTask.onTick`, the water branch is evaluated from scratch every tick, even while its own escape
child is running (Verified):

1. `wet && !grounded && !reach` sends every tick straight to the bob escape (the `!reach` clause skips the 25-tick
   allowance). That branch calls `cancelEverything()` and `customGoalProcess.onLostControl()` before returning
   `new GetOutOfWaterTask()`. `isEqual` keeps the same child instance, but the child's Baritone path was just
   cancelled. The child re-issues its goal on its next tick, so the escape is re-planned from zero every tick and
   only ever executes the start of a path.
2. On the first tick the bot touches the pond floor (1-deep water: grounded, still wet), the bob branch is skipped
   and the PATH branch returns `null`. That stops the escape child, even though its own completion predicate (dry
   and grounded for 750 ms) never held, and paths back toward the buried target. Baritone's first step lifts the
   bot off the floor, and step 1 starts again.

So the parent and the child both claim navigation, and the parent wins every tick. That is a navigation-ownership
defect at the parent/child handoff, not a movement or pathing defect.

## 6. The fix and why it belongs in `DestroyBlockTask`

`WaterEscapeLatch` (new, pure) plus about 30 lines in `DestroyBlockTask`:

- **Escape owns navigation once issued.** At the top of the water logic,
  `Task held = waterEscape.held(); if (held != null) return held;`. While an escape is held the parent does not
  re-evaluate bob/mine/path and does not touch Baritone. The latch releases only when:
  - the child's own predicate holds (`GetOutOfWaterTask.isFinished()`, dry and grounded for 750 ms; a state
    predicate, not a new delay);
  - the child reports a terminal result itself (SUCCESS/FAILURE/BLOCKED/CANCELLED), so an escape that gives up is
    never held forever;
  - or the child was stopped by someone else.

  On the release tick the parent returns `null` once (`releasePending`), so the runner stops the ended escape
  before anything new is issued. This is needed because `GetOutOfWaterTask.isEqual` matches every instance: an
  escape that ended while the bot was still afloat would otherwise stay running in place of the new one, which the
  latch would hold but which would never start. This was found in review during the campaign, before any B launch
  ran (see §9, deviation).
- **Navigation is cancelled once,** on the tick the escape is issued (the existing call). It is no longer
  repeated every tick, because the held branch returns first.
- **Re-entry bound.** Each new escape for the same target after a previous one ended is a re-entry. Past
  `ALLOWED_REENTRIES = 1` (the third water entry for one target), the target is reported through the existing
  `BlockScanner.requestBlockUnreachable(pos, 0)`. The gather then picks another block instead of cycling between
  shore and pond. It is a count of state transitions, not a timer.
  - It is not permanent and not broad: one position, and the existing blacklist resets apply.
  - A parent restart keeps the count, so the bound can't be evaded by restarts.
- **Unchanged.**
  - The trigger: `bobEscapeDue` is the old predicate, moved verbatim.
  - The mine conditions, underwater mining (grounded + reach mines even when wet; tested), the mining-while-wet
    escape, the path branch, `GetOutOfWaterTask` itself, Baritone/Ostinato, and task semantics.
- **Baseline flag.** `-Dtenorclef.waterEscape.legacy=true` restores the old behaviour exactly, for the A arm.

Why here and nowhere else:

- `GetOutOfWaterTask` already escapes correctly when left alone. In arm B it reaches dry ground (`S183 ... confirmed
  dry`) on the first attempt (Measured, pilot 2).
- Baritone executes the paths it is given.
- The only thing wrong was the parent revoking its child's navigation and dropping it on a non-terminal state, and
  that is `DestroyBlockTask`'s handoff. Adding retries, timers or a recovery layer would have kept the competing
  ownership in place. Teleport, Fugue recovery and new scheduling are not used.

## 7. Cancellation race

**Defect (Verified, reproduced by test before the fix).** `absorbChildOutcome` copies a child FAILURE into the
parent while the parent is still running (`absorbedFromChild = true`). The task system never ends a task on a
child failure by itself, so the parent is still active. `Task.stop()` set CANCELLED only when the explicit result
was null/RUNNING/RETRY, so a cancel accepted on the tick a child failed left the parent reporting FAILURE. This is
the Fugue v1.1 B05 race.

**Fix (`Task.stop()`, 1 condition + a 7-line helper):** a copied (absorbed) non-SUCCESS outcome becomes CANCELLED
on stop, unless the task's own finish predicate already holds (then the outcome was terminal before the cancel and
is kept). The contract:

| State when the stop is accepted | Result after stop |
|---|---|
| running, child active | CANCELLED (unchanged) |
| running, a child failure absorbed this tick | **CANCELLED** (was FAILURE) |
| task's own `fail()` / `blocked()` before stop | FAILURE / BLOCKED kept |
| absorbed failure and task `isFinished()` | FAILURE kept |
| task SUCCESS before stop | SUCCESS kept (never rewritten) |
| second `stop()` | no-op (`if (!active) return`): one stable outcome, `onStop` once |

The child keeps its own FAILURE. `isFinished()` is only consulted in the absorbed case and is guarded against a
missing world. Without the new condition, `cancelRacingChildFailureIsCancelled` and
`outcomeIsStableAndCallbacksRunOnce` fail with `expected CANCELLED but was FAILURE`. With it, they pass (mutation
check run).

## 8. Tests

- **`WaterEscapeLatchTest` (13).** It drives the real `WaterEscapeLatch` through the real `Task.tick` runner,
  using a `Miner` that reproduces `DestroyBlockTask`'s branch order over scripted player state.
  - Legacy model reproduces the measured thrash: 100 nav cancels in 5 cycles.
  - Shallow-water ground contact keeps the escape.
  - Navigation is cancelled once per escape, and the escape is started once.
  - Dry/wet flicker does not end the escape.
  - Stable exit releases the escape and paths.
  - Mines after the escape.
  - Underwater collection never escapes (50 mine ticks).
  - Bobbing in reach waits 25 ticks.
  - Wet while mining escapes, then the escape is held.
  - The third water entry for one target reports it unreachable, exactly once in 3 entries.
  - An escape that gives up is released.
  - An escape ending while still afloat hands over cleanly: the old escape is stopped, the new escape is the
    runner's actual child, and it counts as one re-entry, not one per tick.
  - A parent restart drops the escape but keeps the re-entry count.
- **`CancellationOutcomeTest` (6).** Cancel while the child is active; cancel racing a child failure; own failure
  before cancel; absorbed failure on a finished task; success before cancel; stable outcome with each `onStop` run
  once.
- **Full 26.3 suite:** 337 tests, 0 failures, 0 errors (318 before + 19). `:1.16.1:compileJava` and
  `:26.3:compileJava` are clean.
- **Limitation:** the `Miner` model mirrors `DestroyBlockTask`'s branch order by hand. The wiring of the real class
  is checked by code reading and by the live A/B, not by a unit test (it needs a Minecraft world).

## 9. Live methodology (pre-registered before the campaign)

Written before the first campaign launch; nothing here was changed after results came in. It was written after the
two pilots (1 legacy, 1 fixed). The pilots are not part of the tally and are reported separately in §10.

- **Arms.** A = legacy handling (`-Dtenorclef.waterEscape.legacy=true`). B = the latch (default). Both run the
  same jar and the stock task (`TaskCatalogue.getItemTask("iron_pickaxe", 1)`), so Fugue's recovery is never
  involved. The only difference between the arms is the system property.
- **Launches.** 20 launches per arm, run alternately (A,B then B,A, and so on), each in a fresh world: seed 12345,
  Peaceful, `run-once.ps1 -SkipOstinatoUpdate`.
  - Every launch runs one **pond** trial at the default pond spot `579,62,-51`. The bot starts beside a 1-deep pond
    with stone under it, carrying a wooden pickaxe, a crafting table and 6 sticks.
  - The first 10 launches of each arm also run one **control** trial (`empty`, cand 3): ordinary land start,
    nothing given. That is the smaller matched control set: 10 per arm, interleaved the same way.
  - Plans: `pond:1,empty:1` for the first 10 launches of each arm, `pond:1` for the rest, spots `1,3`.
- **Budget.** 8400 ticks per trial, the same in both arms.
- **Primary metric.** Verified objective completion within the budget: `success=true`, meaning the task reported
  SUCCESS and an iron pickaxe is in the inventory. Pond trials are the primary set; control trials are the matched
  check for regressions.
- **Secondary metrics.**
  - From `FUGUEBENCH WATER`: water escapes issued, wet ticks (time in water while mining), escape-held ticks, and
    re-entry bounds.
  - From `FUGUEBENCH TRIAL`: ticks, wall ms, deaths.
  - Freezes: launches whose TRIAL line is missing.
  - CPU: client process CPU seconds per launch, sampled every 5 s from the `devlaunchinjector` java.
- **Validity.** A trial is invalid only if:
  - the world never loaded or the TRIAL line is missing because the launch crashed before the trial started (the
    launch is counted and reported);
  - `ticks == 0`;
  - the bot died within the first 200 ticks (spawn hazard; Peaceful should make this impossible).

  Every other trial counts, including timeouts and failures, and no trial is dropped for any other reason.
- **Decision rule (mining repair).**
  - **A (demonstrated improvement):** B's pond success exceeds A's with Fisher's exact one-sided p < 0.05, and B's
    control success is not lower than A's by more than 2 trials.
  - **B (correctness repair verified):** the thrash signature (escapes per pond trial, and escapes that never
    reach dry ground) is gone in arm B, but the completion difference is not significant.
  - **C (inconclusive):** neither of the above.
  - **D (regression):** control success or pond success drops in B by 3 or more trials, or a new freeze/death
    class appears.
- **Cancellation fix.** Classified separately, from the deterministic tests (`CancellationOutcomeTest`). The
  live race is too rare to sample in 40 launches.

**Deviation (recorded when it happened).** The campaign started at 01:05. After launch 1 (A01, legacy), the
review in §6 found the release-handoff edge case. I stopped only the campaign runner, so B01 never started, and let
A01 finish untouched. I then added `releasePending`, re-ran the full suite (337/337), and resumed at 01:21 with the
same run list from B01 on.

- Arm A's code path is unchanged by this (`LEGACY_WATER_ESCAPE` returns before the latch), so A01 is kept as a
  valid trial.
- Every campaign B trial ran the final code.
- A01 has no CPU sample, because the runner that sampled it was stopped.

## 10. All trials

**Pilots (not in the tally):**

| Run | Arm | Scenario | Result | Ticks | Escapes | Wet ticks | Held ticks |
|---|---|---|---|---|---|---|---|
| wes-pilot1 | pre-fix | pond | TIMEOUT | 8402 | ≥40 (4 dumps of 10) | — | — |
| wes-pilot2 | B | pond | SUCCESS | 1788 | 4 | 46 | 53 |
| wes-pilot2 | B | control | SUCCESS | 2643 | 0 | 0 | 0 |

**Campaign (40 launches, 60 trials, all valid, none excluded; no missing TRIAL lines, no freezes, no deaths).**
Order as run (A01 from the first runner, the rest after the §9 restart). Raw tally: `%TEMP%\wes\final.txt`, from
`analyze.py`; logs `logs/sim-run-wes[AB]NN.log`.

| Launch | Arm | Trial | Result | Ticks | Escapes¹ | Wet ticks | Held ticks | Bounds | Deaths | Client CPU s (launch) |
|---|---|---|---|---|---|---|---|---|---|---|
| wesA01 | A | pond | TIMEOUT | 8401 | 6622 | 8445 | 0 | 0 | 0 | — |
| wesA01 | A | control | SUCCESS | 2537 | 0 | 0 | 0 | 0 | 0 | — |
| wesB01 | B | pond | SUCCESS | 1814 | 4 | 68 | 52 | 0 | 0 | 1157.4 |
| wesB01 | B | control | TIMEOUT | 8402 | 0 | 0 | 0 | 0 | 0 | 1157.4 |
| wesB02 | B | pond | SUCCESS | 1787 | 4 | 44 | 52 | 0 | 0 | 763.6 |
| wesB02 | B | control | SUCCESS | 2643 | 0 | 0 | 0 | 0 | 0 | 763.6 |
| wesA02 | A | pond | TIMEOUT | 8401 | 6648 | 8442 | 0 | 0 | 0 | 1490.9 |
| wesA02 | A | control | SUCCESS | 4122 | 0 | 0 | 0 | 0 | 0 | 1490.9 |
| wesA03 | A | pond | TIMEOUT | 8402 | 6636 | 8450 | 0 | 0 | 0 | 1311.8 |
| wesA03 | A | control | SUCCESS | 2468 | 0 | 0 | 0 | 0 | 0 | 1311.8 |
| wesB03 | B | pond | SUCCESS | 1808 | 4 | 44 | 52 | 0 | 0 | 864.6 |
| wesB03 | B | control | SUCCESS | 2434 | 0 | 0 | 0 | 0 | 0 | 864.6 |
| wesB04 | B | pond | SUCCESS | 1787 | 4 | 46 | 49 | 0 | 0 | 894.6 |
| wesB04 | B | control | SUCCESS | 2992 | 0 | 0 | 0 | 0 | 0 | 894.6 |
| wesA04 | A | pond | TIMEOUT | 8401 | 6644 | 8446 | 0 | 0 | 0 | 1349.4 |
| wesA04 | A | control | SUCCESS | 2515 | 0 | 0 | 0 | 0 | 0 | 1349.4 |
| wesA05 | A | pond | TIMEOUT | 8402 | 6638 | 8446 | 0 | 0 | 0 | 1447.5 |
| wesA05 | A | control | SUCCESS | 2643 | 0 | 0 | 0 | 0 | 0 | 1447.5 |
| wesB05 | B | pond | SUCCESS | 1795 | 4 | 47 | 55 | 0 | 0 | 773.3 |
| wesB05 | B | control | SUCCESS | 2522 | 0 | 0 | 0 | 0 | 0 | 773.3 |
| wesB06 | B | pond | SUCCESS | 1800 | 4 | 46 | 51 | 0 | 0 | 879.9 |
| wesB06 | B | control | SUCCESS | 2743 | 0 | 0 | 0 | 0 | 0 | 879.9 |
| wesA06 | A | pond | TIMEOUT | 8401 | 6618 | 8401 | 0 | 0 | 0 | 1327.9 |
| wesA06 | A | control | SUCCESS | 2657 | 0 | 0 | 0 | 0 | 0 | 1327.9 |
| wesA07 | A | pond | TIMEOUT | 8401 | 6646 | 8439 | 0 | 0 | 0 | 1750.8 |
| wesA07 | A | control | TIMEOUT | 8402 | 4470 | 4873 | 0 | 0 | 0 | 1750.8 |
| wesB07 | B | pond | SUCCESS | 1821 | 4 | 47 | 51 | 0 | 0 | 904.9 |
| wesB07 | B | control | SUCCESS | 3104 | 0 | 0 | 0 | 0 | 0 | 904.9 |
| wesB08 | B | pond | SUCCESS | 1801 | 4 | 49 | 54 | 0 | 0 | 896.5 |
| wesB08 | B | control | SUCCESS | 2759 | 0 | 0 | 0 | 0 | 0 | 896.5 |
| wesA08 | A | pond | TIMEOUT | 8401 | 6635 | 8444 | 0 | 0 | 0 | 1732.1 |
| wesA08 | A | control | TIMEOUT | 8401 | 0 | 0 | 0 | 0 | 0 | 1732.1 |
| wesA09 | A | pond | TIMEOUT | 8402 | 6643 | 8452 | 0 | 0 | 0 | 1320.8 |
| wesA09 | A | control | SUCCESS | 2451 | 0 | 0 | 0 | 0 | 0 | 1320.8 |
| wesB09 | B | pond | SUCCESS | 1787 | 4 | 44 | 51 | 0 | 0 | 828.5 |
| wesB09 | B | control | SUCCESS | 2656 | 0 | 0 | 0 | 0 | 0 | 828.5 |
| wesB10 | B | pond | SUCCESS | 1802 | 4 | 45 | 67 | 0 | 0 | 823.5 |
| wesB10 | B | control | SUCCESS | 2660 | 0 | 0 | 0 | 0 | 0 | 823.5 |
| wesA10 | A | pond | TIMEOUT | 8401 | 6629 | 8448 | 0 | 0 | 0 | 1234.0 |
| wesA10 | A | control | SUCCESS | 2651 | 0 | 0 | 0 | 0 | 0 | 1234.0 |
| wesA11 | A | pond | TIMEOUT | 8402 | 6594 | 8402 | 0 | 0 | 0 | 959.0 |
| wesB11 | B | pond | SUCCESS | 2041 | 4 | 45 | 52 | 0 | 0 | 609.6 |
| wesB12 | B | pond | SUCCESS | 1785 | 3 | 47 | 54 | 0 | 0 | 494.2 |
| wesA12 | A | pond | TIMEOUT | 8401 | 6594 | 8400 | 0 | 0 | 0 | 952.4 |
| wesA13 | A | pond | TIMEOUT | 8401 | 6593 | 8399 | 0 | 0 | 0 | 1002.1 |
| wesB13 | B | pond | SUCCESS | 1788 | 4 | 46 | 51 | 0 | 0 | 613.5 |
| wesB14 | B | pond | SUCCESS | 1790 | 4 | 43 | 51 | 0 | 0 | 572.1 |
| wesA14 | A | pond | TIMEOUT | 8408 | 6600 | 8404 | 0 | 0 | 0 | 957.8 |
| wesA15 | A | pond | TIMEOUT | 8401 | 6715 | 8439 | 0 | 0 | 0 | 1238.5 |
| wesB15 | B | pond | SUCCESS | 1784 | 4 | 41 | 51 | 0 | 0 | 540.5 |
| wesB16 | B | pond | SUCCESS | 1799 | 4 | 42 | 50 | 0 | 0 | 527.8 |
| wesA16 | A | pond | TIMEOUT | 8401 | 6698 | 8433 | 0 | 0 | 0 | 1181.5 |
| wesA17 | A | pond | TIMEOUT | 8401 | 6573 | 8401 | 0 | 0 | 0 | 1108.8 |
| wesB17 | B | pond | SUCCESS | 1803 | 4 | 44 | 51 | 0 | 0 | 610.1 |
| wesB18 | B | pond | SUCCESS | 1793 | 4 | 46 | 51 | 0 | 0 | 601.4 |
| wesA18 | A | pond | TIMEOUT | 8401 | 6572 | 8401 | 0 | 0 | 0 | 1086.8 |
| wesA19 | A | pond | TIMEOUT | 8402 | 6619 | 8445 | 0 | 0 | 0 | 1047.6 |
| wesB19 | B | pond | SUCCESS | 1786 | 4 | 44 | 51 | 0 | 0 | 527.6 |
| wesB20 | B | pond | SUCCESS | 1824 | 4 | 44 | 51 | 0 | 0 | 516.6 |
| wesA20 | A | pond | TIMEOUT | 8401 | 6595 | 8400 | 0 | 0 | 0 | 964.8 |

¹ "Escapes" counts executions of an escape-issuing branch. In arm A that is once per airborne tick (the defect: the
parent re-issues and re-cancels every tick), in arm B once per real escape.

**Summary.**

| | A pond | B pond | A control | B control |
|---|---|---|---|---|
| Verified completion | **0/20** | **20/20** | 8/10 | 9/10 |
| Median ticks | 8401 (all timeout) | 1797 | 2647 | 2702 |
| Escapes / trial (mean) | 6626 | 4.0 | 447 | 0 |
| Wet ticks / trial (mean) | 8427 | 46 | 487 | 0 |
| Escape-held ticks (mean) | 0 | 52 | 0 | 0 |
| Re-entry bounds fired | — | 0 | — | 0 |
| Deaths | 0 | 0 | 0 | 0 |

Pond: Fisher exact one-sided p(B > A) = **7.3e-12**. Control: p = 0.50 (no difference).

**The three control timeouts.**
- **A07 (A):** the same water loop on an ordinary land start: 4470 escape-branch executions, 4873 wet ticks, the
  bot stationary at 1605,61,-112 for >4800 ticks. So the defect is not specific to the pond setup (Measured).
- **A08 (A) and B01 (B):** a different, unrelated stall in both arms: `Pickup Dropped Items: [[furnace]] >
  Approach entity > Going to entity` with zero movement for >4000 ticks. Zero water involvement. Not addressed here
  (§13).

## 11. Performance

- **Client CPU per launch (median):** A pond-only 1234 s, B pond-only ~600 s (launches with control: A 1349 s,
  B 872 s). B uses less CPU per launch because its pond trials end at ~1800 ticks instead of 8400.
- **Per tick:** a least-squares fit of CPU seconds against total trial ticks per launch gives the same marginal
  cost in both arms: A 86, B 82 CPU s per 1000 ticks, with roughly 350–440 s of fixed launch/world-load cost.
  The latch adds no measurable per-tick cost; it is one null check and two field reads per tick.
- **Trace:** `WaterEscapeTrace` is off unless `-Dtenorclef.waterTrace=true`; off, every hook is one static boolean
  check. All campaign launches ran with it on in both arms.

## 12. Classifications

- **Mining / water-escape repair: A — demonstrated improvement.** Against the pre-registered rule:
  - B's pond completion (20/20) exceeds A's (0/20) with p = 7.3e-12, far below 0.05;
  - B's control completion (9/10) is not lower than A's (8/10);
  - no new freeze or death class appeared;
  - the thrash signature is gone (4 real escapes per trial, every one reaching dry ground, vs ~6600 re-issues).

  Completion is the verified original objective: an iron pickaxe in the inventory and the task reporting SUCCESS.
- **Cancellation race: B — correctness repair verified.** Deterministic tests reproduce the race on the old code
  (2 failures: `expected CANCELLED but was FAILURE`) and pass on the new code; the preserve cases (own failure,
  terminal failure, success) pass; the full suite is green. It was not sampled live (pre-registered: too rare in
  40 launches), so no live improvement is claimed.

## 13. Limitations

- **One pond, one seed.** All pond trials use the same spot and seed, so they test one geometry repeatedly. B's
  ticks are nearly identical across trials (1784–2041). Other water shapes (deep lakes, rivers, oceans, flowing
  water) are covered only by the unit tests and A07's incidental land-start loop.
- **The re-entry bound never fired live** (0 in 20 B pond trials). It is exercised only by unit tests.
  `ALLOWED_REENTRIES = 1` is a judgement, not a measured value.
- **The unit tests mirror `DestroyBlockTask`'s branch order in a model.** The real class's wiring is checked by
  code reading and the live A/B, not by a unit test.
- **The pre-registration was written after the two pilots** (1 A, 1 B), which already showed the direction. The
  pilots are excluded from the tally.
- **Campaign deviation:** one restart after A01 to add `releasePending` (§9). Every B trial ran the final code.
- **1.16.1 only.** The live runs were on 1.16.1 with the prebuilt Ostinato jar. 26.3 is compiled and unit-tested,
  not run live.

**Unknowns.**
- Whether `GetOutOfWaterTask` itself can still fail to reach dry ground in very large water bodies. If it gives up,
  the latch releases it and the re-entry bound limits the cycle, but this has not been observed live.
- How often the cancellation race happens in real use, beyond the v1.1 B05 observation.
- The cause of the furnace-pickup stall (A08, B01). It is out of scope, seen in both arms, and unrelated to water.

## 14. Next step

1. Review and commit the change set (DestroyBlockTask, WaterEscapeLatch, Task.stop, the two tests,
   WaterEscapeTrace, the GetOutOfWaterTask trace hooks), or drop the trace hooks first if a smaller diff is
   preferred. Nothing is committed.
2. Re-run Fugue v1.1's A06/A-arm scenarios. Its stall root cause was this loop, so its class-D verdict should be
   re-measured with this fix in place before any further Fugue recovery work.
3. Investigate the furnace `Pickup Dropped Items > Approach entity` stall as a separate defect: 2 of 20 control
   trials, both arms.
4. Optionally, a second pond geometry (a deeper lake, or a different seed) to exercise the re-entry bound live.
