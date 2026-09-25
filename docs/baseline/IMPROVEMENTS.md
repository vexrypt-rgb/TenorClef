# Bot improvements — 2026-09-24

Follow-up to [`BASELINE.md`](BASELINE.md) and [`PHASE1_RESULT.md`](PHASE1_RESULT.md).
Target: `@testrun2` on MC 1.16.1 (the owner's live bot). **Not committed.**

Evidence sources:
- **Historical logs** — `versions/1.16.1/run/logs/2026-09-2[34]-*.log.gz` + `latest.log`: 11 client launches,
  22 `@testrun2` starts, 5 deaths, 3 seed resets.
- **Live runs launched by Claude** with `scripts/run-once.ps1` (logs in `logs/sim-run-<tag>.log`):
  `base1` (pre-fix), `fix1`, `fix2`.

## Changes

| Code | Problem (evidence) | Change | Verified |
|---|---|---|---|
| **S188** | Every reroll hung the client. `ResetSignal` ran `mc.execute(mc::disconnect)` inside the tick (execute runs inline) and never called `world.disconnect()`; vanilla `disconnect()` loops `while (!server.isStopping()) render()`. Live `base1`: S159 reject at 0:27 → DEADMAN stack `ResetSignal → disconnect → render`, frozen 90s+. Historical: all 3 resets followed by 9–25 min gaps before the next launch. Root cause of the S174 note "the reroll path has never worked". | `ResetSignal`: `mc.send(...)` (always queued), `world.disconnect()` then `disconnect()` then `setScreen(new TitleScreen())` — the vanilla Save-and-Quit sequence (checked with `javap` on 1.16.1 and 1.21.1). 1.21.11 branch uses its new signatures. One pending disconnect at a time. | Compiles 1.16.1. Live reroll: _pending (no rejected seed yet in fix runs)_ |
| **S190** | Water-bail ping-pong: T2Solve S102 returned `WaterBailTask`, next tick returned null ("already escaping") and the phase task replaced the bail. Live `fix1` (owner saw "bot stuck standing in water"): IRON @177,62,-142, child flipped every tick for 60s+. Historical: 63 S164 incidents with WaterBailTask, 772 child flips, one **396s** crafting stall. | `ModernSpeedrunTask.onTickInner`: keep an unfinished `WaterBailTask` (same contract as `SurfaceBailTask`). | **Live `fix2`**: S102 at 1:44 → bail held 67 ticks → `E10 water-bail end dry=20` → mining resumed. |
| **S189** | Respawn free-fall: all 5 historical respawns show sky 15→0 within 0.5s, `ground=false`, free-fall at a fixed XZ (runs U/V ended below bedrock). Driver was already moving 0.2–0.7s after respawn every time. 3/5 tripped S165 `fellIn` → seed abandoned (one 13:40 into a Nether run); the other 2 produced the 463s and 340s surface-bail episodes (≈100% of 808s logged surface-bail time). | New `RespawnSettle`: after any world swap (new player entity: respawn or dimension change) the driver holds still, cancels Baritone, until grounded on loaded solid ground ≥10 ticks (max 4s). Runs before E90/S165. Writes `S189T` per-tick trace for 2s. | Compiles. Live: _see below_. Mechanism of the fall not yet proven — the trace will prove or refute on the next death. |
| **S187** | Blind walk nudge walked off a ledge: `T2Input.walkTurn()` turned +70° and held forward 2s. Historical: NETHER y=95, S100 at 02:32:24, falling 02:32:25, dead 02:32:26. | `walkTurn` picks the first of +70/−70/±140/180 whose next 4 blocks have ground within 3 and no lava (`core.SafeHeading`, pure Java); none safe → stand still and log S187. Covers S100, ProjectileDodge, T2CoreTask. | `SafeHeadingTest` 8/8 (cliff, pillar top, 3-drop ok, 4-drop unsafe, lava, wall, unloaded). |
| **S191** | Nether "climb off lava" targeted the exact block 12 overhead (usually netherrack) and the goal moved every step. Historical: 302s at 27,47,26 → 27,59,26. | `GetToYTask(52)` instead. | Compiles. Not yet exercised live. |
| tests | `TaskPropagationTest.parentAbsorbsChildFailure` failed: a raw non-recoverable child failure was re-run through RecoveryManager and became RETRY. | `Task.absorbChildOutcome` only re-applies policy to recoverable failures (or ones the child already decided). No live consumer of parent results on `@testrun2`. | Full suite **124/124** on clean HEAD + change. |
| build | `AutoWorldCreateMixin` (1.16.1-only APIs) broke `:1.21.1`, `:1.21.11`, `:1.16.5` compile. | Body and imports wrapped in `//#if MC <= 11601`; other versions compile an empty mixin. | _pending compile_ |
| CI | Tests never ran. | `gradle.yml` build-1211: `./gradlew :1.21.1:test`. | local only, not pushed |
| build | JUnit absent (PHASE1_RESULT). | JUnit 5 in `build.gradle`. | 124 tests run |

## Live run results
_to be filled_
