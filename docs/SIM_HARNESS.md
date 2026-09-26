# 1.16.1 sim (live client)

The only faithful sim is the real Fabric **1.16.1** client (`:1.16.1:runClient`).
Offline `benchmark` mock scenarios do **not** emulate 1.16.1 and must not be treated as the sim.

## Loop

`scripts\sim-loop.ps1` keeps one 1.16.1 client alive:
- new Survival **Easy** world each cycle (never Hardcore; never load an old save)
- `@testrun2` via chat after join (`idleCommand` empty)
- soft-reset to title between worlds when possible (no Gradle relaunch)

```powershell
cd C:\Users\redfa\Documents\MinecraftDev\altoclef
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
powershell -ExecutionPolicy Bypass -File .\scripts\sim-loop.ps1
```

Status: `logs\overnight-status.json`  
Summary: `logs\MORNING_SUMMARY.md`

## Time warp (faster sims)

The world and the bot can run N times faster than real time (singleplayer only):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sim-loop.ps1 -Warp 5
```

or in game: `@warp 5` (`@warp 1` turns it off). Max 20.

- 1.16.1 has no `/tick`, so `ServerTickWarpMixin` (integrated server 50ms budget) and
  `ClientTimerWarpMixin` (client `RenderTickCounter.tickTime`) scale both clocks together.
  On 1.20.3+ `@warp` uses vanilla `/tick rate` (cheats must be on).
- Vanilla runs at most 10 client ticks per frame, so real speedup is about `fps * 10 / 20`.
  `@headless` caps fps at 10, which limits warp to 5x.
- How fast it can really go depends on your CPU: if the server can't keep up it simply runs
  as fast as it can (watch for "Can't keep up!" in latest.log). 3-5x is a sensible start.
- Wall-clock timers (TimerReal, keepalive, `-StallSec`, T2Deadman) are not scaled, so they
  get more game time per real second: more lenient, never stricter.

## Pathfinding benchmark (`@pathbench`, 1.16.1)

Measures pathfinding against a fixed ring of 16 goals around the bot (8 directions × 32/96 blocks).

- `@pathbench search [- | setting=v1,v2,...] [reps]` runs Baritone's A* only, with no movement.
  With a sweep, the whole ring is re-run once per value of that Baritone setting, e.g.
  `@pathbench search costHeuristic=3.0,3.563,4.0 2`. The original value is restored afterwards.
- `@pathbench travel [baritone|tungsten] [reps]` runs end to end. The bot teleports back to the
  start before each trial. Results are in game ticks, so they don't depend on `@warp`.

Output goes to `run/pathbench/pathbench_*.csv`, plus a `PATHBENCH SUMMARY ...` line in latest.log.

Reproducible headless loop (Linux; `xvfb` required):

```
# run/altoclef_settings.json: {"autoLoadWorld": true, "autoRunCommand": "pathbench search - 2"}
JAVA_TOOL_OPTIONS="-Dtenorclef.seed=12345 -Dtenorclef.pathbench.exit=true" \
  xvfb-run -a ./gradlew :1.16.1:runClient
```

- `tenorclef.seed` pins the auto-created world seed.
- `tenorclef.pathbench.exit` closes the client when the bench finishes.
- Optional: `tenorclef.pathbench.timeoutMs` (search, default 4000) and
  `tenorclef.pathbench.travelTicks` (travel, default 1800).
