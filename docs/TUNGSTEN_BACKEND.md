# Tungsten movement backend

## Decision (locked)
- Tungsten = physics A* for parkour, chase/escape, travel
- Baritone = mining / block interaction / inventory (unchanged)
- Do NOT full-merge UnionClef

## Chosen source
| Field | Value |
|-------|-------|
| Repo | https://github.com/3ndetz/Tungsten |
| Branch | altoclef-compat |
| Commit | 5cb12ad65c0e045aa5a02d017c98df64eeda6d40 |
| License | GPL-3.0 (treat as GPL; fabric.mod.json claims CC0) |
| Checkout | vendor/tungsten (pin: vendor/TUNGSTEN_PIN.txt) |

Why: AltoClef-compat 1.21 fork with Baritone removed. Prefer over stale KaptainWutax/Tungsten. UnionClef tungsten/ may have extra 1.21.1 physics fixes for a later rebase.

Local patch: vendor fabric.mod.json minecraft depends set to >=1.21 for 1.21.1.

## What is in this tree
- TungstenBridge — reflection to kaptainwutax.tungsten.*
- TungstenMovement — gotoBlock / followEntity / cancel / isPathing / isAvailable
- TungstenGotoTask / TungstenFollowTask
- @tgoto + Hunter/Runner already call the facade
- Gradle 1.21/1.21.1: modImplementation+include jars from libs/ or vendor/tungsten/build/libs

Absent jar => isAvailable false => Baritone fallback.

## Enable after client is stopped
Do not touch altoclef classes while runClient is live.
1. Enter vendor/tungsten
2. Produce remapped tungsten jar
3. Place jar under libs/
4. Rebuild project version 1.21.1 then relaunch
 
## API mapping 
pathTo - PATHFINDER.find 
cancel - PATHFINDER.stop + EXECUTOR.stop + FollowEntityTask.stop 
isPathing - active OR isRunning OR FollowEntityTask.isActive 
follow - FollowEntityTask.start 
probe - kaptainwutax.tungsten.TungstenMod
 
## Next kill 
Stop client, build vendor tungsten jar into libs/, rebuild 1.21.1, relaunch. 
Expect TungstenBridge bound. No altoclef compile was run while client live.
