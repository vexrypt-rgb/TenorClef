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
