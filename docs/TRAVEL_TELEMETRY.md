# Travel telemetry (`TRAVEL` line)

Every `GetToBlockTask` run logs exactly one structured line when it stops
(`adris.altoclef.movement.TravelTrace`, emitted from `GetToBlockTask.onStop`):

```
TRAVEL goal=GetToBlock start=x,y,z target=x,y,z requested=<backend> executed=<backend> fallback=<bool>
       pathFoundTick=<n> ticks=<n> traveled=<blocks> remaining=<blocks> stallTicks=<n>
       outcome=ARRIVED|FAILED|STOPPED reason=<FailureReason|INTERRUPTED|-> verified=<bool>
```

| Field | Meaning |
|---|---|
| `requested` | Ostinato `movementBackend` preference at dispatch (`MovementEngineAdapter.requestedBackend()`) |
| `executed` | Mover that took the last dispatch: an engine backend (`BARITONE`, `TUNGSTEN`, ...), `CUSTOM_GOAL_PROCESS` (adapter fallback), or `TUNGSTEN_TASK` (GetToBlockTask's direct Tungsten leg) |
| `fallback` | Sticky: true if any dispatch ran on something other than `requested` |
| `pathFoundTick` | First task tick on which Baritone/engine reported pathing (-1 = never) |
| `traveled` | Summed per-tick movement; jumps ≥10 blocks (teleport/respawn) are excluded |
| `remaining` | Distance from the final position to the target **block centre**. `GetToBlock` accepts nearby positions, so an ARRIVED run can show 1–3 blocks here |
| `stallTicks` | Ticks with movement < 0.01 blocks (includes planning time) |
| `verified` | `CustomBaritoneGoalTask.isInGoal()` + dimension, a world-position check (false after an abort) — not engine status (Ostinato's `HybridMovementEngine.status()` never reports ARRIVED) |

## Reproducing

`@goto x y z` with `movementbackend baritone|tungsten` in `run/baritone/settings.txt`, then
`grep -a "TRAVEL goal=" run/logs/latest.log` (the line appears twice: log + chat echo).

## Observed results (1.16.1, seed 12345, `goto 87 69 -115`, spawn ≈ 57–63,68–71,-110–-112)

Only runs actually executed (2026-09-28). Nothing extrapolated.

| backend | ticks | traveled | remaining | stallTicks | pathFoundTick | verified |
|---|---|---|---|---|---|---|
| baritone | 169 | 43.4 | 1.0 | 37 | 3 | true |
| baritone | 164 | 46.6 | 1.0 | 35 | 3 | true |
| baritone | 126 | 32.3 | 1.0 | 36 | 4 | true |
| tungsten | 972 | 38.3 | 3.3 | 853 | 130 | true |
| tungsten | 974 | 43.3 | 19.5 | 836 | 132 | true |
| tungsten | 986 | 42.1 | 47.8 | 862 | 134 | true |

Tungsten runs all took ≈49 s and each logged ~420 Tungsten `search start` lines from a standing position.
**Resolved contradiction:** two tungsten runs reported `verified=true` with `remaining` 19.5 and 47.8.
Cause: `verified` used `isFinished()`, which is also true after a terminal recovery abort. Fixed to a
pure world check (`CustomBaritoneGoalTask.isInGoal()`); those runs were aborts, not arrivals. The
table rows above predate the fix, so `verified` there is unreliable for aborted runs. Tungsten is parked (not a target backend for now); Baritone and
the kinematic mover are the supported travel paths.
