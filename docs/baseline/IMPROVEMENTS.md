# Bot improvements — 2026-09-24

Follow-up to [`BASELINE.md`](BASELINE.md) and [`PHASE1_RESULT.md`](PHASE1_RESULT.md).
Target: `@testrun2` on MC 1.16.1 (the owner's live bot).
Pushed on branch `feat/testrun2-live-fixes` (TenorClef) and `fix/1161-runtime-jar-source` (Ostinato).

Evidence sources:
- **Historical logs**: `versions/1.16.1/run/logs/2026-09-2[34]-*.log.gz` + `latest.log`.
  11 distinct runs (each start logs two lines), 5 deaths, 3 seed resets; 3 runs reached the Nether,
  none got further.
- **Live runs launched by Claude** with `scripts/run-once.ps1` (logs in `logs/sim-run-<tag>.log`):
  `base1` (pre-fix), `fix1`, `fix2`, `fix3`, `fix4`.

## Changes

| Code | Problem (evidence) | Change | Verified |
|---|---|---|---|
| **S189** | **Harness worlds were hardcore.** `AutoWorldCreateMixin` passed `true` as LevelInfo arg 3, labelled `generateStructures`; yarn 1.16.1 mislabels it, and vanilla `CreateWorldScreen` passes `hardcore` there (`javap`). `level.dat` of every AutoRun world: `hardcore=1`. Each death made the bot a **spectator**, which sinks through blocks. That is the "respawn free-fall" in all 5 logged respawns (sky 15→0 inside rock, fixed XZ, earlier runs U/V below bedrock), and it caused all 3 seed resets (S165 `fellIn`) and ≈100% of the 808s of surface-bail time. S165/S169/S172 were fighting this symptom. | Arg 3 `false` (hardcore), arg 5 `false` (allowCommands), as vanilla. | **Verified**: new world `AutoRun_20260924_212048` has `level.dat hardcore=0` (pre-fix world: 1). Preprocessed 1.16.1 source differs from the original only in these arguments. |
| **S188** | Every reroll hung the client. `ResetSignal` ran `mc.execute(mc::disconnect)` inside the tick (execute runs inline) and never called `world.disconnect()`; vanilla `disconnect()` loops `while (!server.isStopping()) render()`. Live `base1`: S159 reject at 0:27 → DEADMAN stack `ResetSignal → disconnect → render`, frozen 90s+. Root cause of the S174 note "the reroll path has never worked". | `mc.send(...)` (always queued), then `world.disconnect()`, `disconnect()`, `setScreen(new TitleScreen())` (the vanilla Save-and-Quit order, checked with `javap` on 1.16.1/1.21.1). 1.21.11 branch uses its new signatures. One pending disconnect at a time. | **Verified live `fix3`**: treeless-desert reject at 0:19 → `S188` → "Stopping singleplayer server" → fresh world `…_r1` created. |
| **S192** | After S188 worked, the rerolled world sat idle: `autoRunCommand` fires once per client launch, the old task ended at phase DONE, and the still-armed DEADMAN exited 87 after 120s. Live `fix3` (owner saw "the bot is not doing anything"). | `AltoClef` re-arms the auto-run when the reroll count changed **and** the world object changed; `ResetSignal` disarms the watchdog until the next `onStart`. | Compiles; live reroll in `fix4`: see below. |
| **S190** | Water-bail ping-pong: T2Solve S102 returned `WaterBailTask`, the next tick returned null ("already escaping"), and the phase task replaced the bail. Live `fix1` (owner saw "bot stuck standing in water"): IRON @177,62,-142, child flipped every tick for 60s+. Historical: 63 S164 incidents with WaterBailTask, 772 flips, one **396s** crafting stall. | Keep an unfinished `WaterBailTask` (same contract as `SurfaceBailTask`). | **Verified live `fix2`**: S102 at 1:44 → bail held 67 ticks → `E10 water-bail end dry=20` → mining resumed. |
| **S187** | Blind walk nudge walked off a ledge: `T2Input.walkTurn()` turned +70° and held forward 2s. Historical: NETHER y=95, S100 at 02:32:24, falling 02:32:25, dead 02:32:26. | `walkTurn` picks the first of +70/−70/±140/180 whose next 4 blocks have ground within 3 and no lava (`core.SafeHeading`, pure Java); none safe → stand still, log S187. Covers S100, ProjectileDodge, T2CoreTask. | `SafeHeadingTest` 8/8 (cliff, pillar top, 3-drop ok, 4-drop unsafe, lava, wall, unloaded). |
| **S191** | Nether "climb off lava" targeted the exact block 12 overhead (usually netherrack), and the goal moved every step. Historical: 302s at 27,47,26 → 27,59,26. | `GetToYTask(52)`. | Compiles; not yet exercised live. |
| tests | `TaskPropagationTest.parentAbsorbsChildFailure` failed: a raw non-recoverable child failure was re-run through RecoveryManager and became RETRY. | `Task.absorbChildOutcome` re-applies policy only to recoverable failures (or ones the child already decided). No live consumer of parent results on `@testrun2`. | 124/124 |
| build | The working tree failed the required `:1.21.1` gate: 1.16.1-only `AutoWorldCreateMixin` / `AutoWorldLoadMixin`, and `getMaterial().blocksMovement()` (Material is gone in 1.20+) in `T2Solve` and `RuinedPortalFinishTask`. | Mixins guarded with `//#if MC <= 11601` (other versions get empty mixins); `blocksMovement` switched at `MC >= 12001` (checked per version with `javap`). | `:1.21.1:compileJava`, `:1.16.1:compileJava`, `:1.21.1:test` 124/124, all in the working tree. |
| build/CI | JUnit absent; CI never ran tests. | JUnit 5 in `build.gradle`; `gradle.yml` build-1211 runs `./gradlew :1.21.1:test`. | 124 tests run locally. |

Withdrawn during the session: a "respawn settle" gate (first-draft S189). It was built on the free-fall
evidence before that turned out to be hardcore spectator mode, so it was removed.

## Known remaining issues (not changed)

- `:1.21.11:compileJava` fails in `MobDefenseChain`, `MLGBucketFallChain` and `EntityTracker`
  (`Item` vs `ItemStack` API drift in the uncommitted work). The 1.21.11 CI job is non-blocking.
- `:1.19.4`–`:1.20.6` fail on `DjPlayer.java:138`; not CI-gated, but it blocks `:1.16.1:test`.
- HolePillar dig-down/pillar-up cycles (E100/S130/S136/S180) are the most frequent codes in live runs;
  that subsystem is under active owner development and was left untouched.
- `vendor/tungsten` (tip) has uncommitted changes inside the submodule; not committed.

## Live run results
- `base1` (pre-fix): hardcore world; spawn reject at 0:27 → client frozen in `disconnect()` (S188).
- `fix1`: hardcore world; water-bail ping-pong for 60s+ at IRON (S190). Stopped manually.
- `fix2`: hardcore world; S190 verified; reached PORTAL at 6:11, still building the portal at 13:04 when stopped for the hardcore fix.
- `fix3`: first non-hardcore world; S188 reroll verified; idle after reroll (S192). DEADMAN exit 87.
- `fix4`: _in progress_
