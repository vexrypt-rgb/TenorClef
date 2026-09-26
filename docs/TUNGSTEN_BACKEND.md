# Tungsten movement backend

## Decision (locked)
- Tungsten = physics A* for parkour, chase/escape, travel (tip 1.21.x)
- Baritone = mining / block interaction / inventory (unchanged)
- Do NOT full-merge UnionClef

## Chosen source (tip 1.21.x)
| Field | Value |
|-------|-------|
| Repo | https://github.com/3ndetz/Tungsten |
| Branch | altoclef-compat |
| Commit | 5cb12ad65c0e045aa5a02d017c98df64eeda6d40 |
| License | GPL-3.0 (treat as GPL; fabric.mod.json claims CC0) |
| Checkout | vendor/tungsten (pin: vendor/TUNGSTEN_PIN.txt) |

Why: AltoClef-compat 1.21 fork with Baritone removed. Prefer over stale KaptainWutax/Tungsten. UnionClef tungsten/ may have extra 1.21.1 physics fixes for a later rebase.

Local patch: vendor fabric.mod.json minecraft depends set to >=1.21 for 1.21.1.

## 1.16.1 parity (fullport)
| Field | Value |
|-------|-------|
| Checkout | vendor/tungsten-1.16.1 (pin: vendor/TUNGSTEN_1161_PIN.txt) |
| Target | Yarn 1.16.1+build.21 / Fabric API 0.18.0+build.387-1.16.1 |
| Jar | libs/tungsten-fabric-*-1.16.1*.jar or vendor/tungsten-1.16.1/build/libs (~245KB, ~103 classes) |
| Gradle gate | mcVersion == 11601 (same modImplementation+include pattern as tip) |

**Approach:** Loom `migrateMappings` from tip 5cb12ad → 1.16.1 Yarn, then finish remaining API gaps so **full Agent physics A\*** remaps. Package `kaptainwutax.tungsten.*` unchanged for `TungstenBridge`. The earlier **slim direct-walk stub** (PR #17 era) is **superseded** by this fullport jar.

1.16.1 shims live in `kaptainwutax.tungsten.compat.McCompat` plus vendor-local `VoxelWorld` / `AgentShapeContext` / `AccessorEntity` adaptations. Sources also mirrored under artifacts `tungsten-1161/java-fullport-wip` and `tungsten-1161-fullport/`.

KaptainWutax upstream is 1.19.2 — closer than 1.21 but still not 1.16.1; migrateMappings from tip was preferred to keep FollowEntityTask / PathFinder.find(WorldView,Vec3d,PlayerEntity) API parity with the existing reflection facade.

**Runtime note:** jar declares `java: >=17`. Run 1.16.1 client with JDK 17+ (Fabric Loader 0.16.x). Loom configure still wants JDK 21.

### Remaining behavioral gaps vs tip 1.21 Tungsten
- `FluidHandling.WATER` / `ShapeType.FALLDAMAGE_RESETTING` → closest 1.16.1 enums (`ANY` / `COLLIDER`)
- `VoxelShape.getPointPositions` protected → `forEachBox` Y sampling (step-height edges may differ slightly)
- Submerged fluid tags: 1.16.1 Entity holds a **single** `Tag<Fluid>`; Agent still uses a Set and only WATER/LAVA membership
- `Vec3d.offset(Direction)`, `EntityDimensions.getBoxAt`, `Vec2f.lengthSquared` → McCompat helpers
- Mixin coverage / render / some command paths less battle-tested on 1.16.1 than tip
- In-game `@tgoto` / `mover=tungsten` parkour parity not validated on this box (no live 1.16.1 client)

## What is in this tree
- TungstenBridge — reflection to kaptainwutax.tungsten.*
- TungstenMovement — gotoBlock / followEntity / cancel / isPathing / isAvailable
- TungstenGotoTask / TungstenFollowTask
- @tgoto + Hunter/Runner already call the facade
- Gradle: tip 1.21/1.21.1/1.21.11 **and** 1.16.1 load jars from libs/ or matching vendor build/libs

Absent jar => isAvailable false => Baritone fallback.

## Enable after client is stopped
Do not touch altoclef classes while runClient is live.
1. Tip: enter vendor/tungsten — or 1.16.1: vendor/tungsten-1.16.1
2. Produce remapped tungsten jar (`./gradlew remapJar`)
3. Place version-matching jar under libs/
4. Rebuild the matching project version then relaunch

## API mapping
pathTo - PATHFINDER.find
cancel - PATHFINDER.stop + EXECUTOR.stop + FollowEntityTask.stop
isPathing - active OR isRunning OR FollowEntityTask.isActive
follow - FollowEntityTask.start
probe - kaptainwutax.tungsten.TungstenMod

## Smoke / offline evidence
Jar must contain:
- kaptainwutax.tungsten.TungstenMod
- kaptainwutax.tungsten.TungstenModDataContainer
- kaptainwutax.tungsten.path.PathFinder
- kaptainwutax.tungsten.path.PathExecutor
- kaptainwutax.tungsten.task.FollowEntityTask

See `src/test/java/adris/altoclef/movement/TungstenJarPresenceTest.java`.

## Using Tungsten in @testrun2
`@testrun2` travel (`TungstenMoveTask`) now really uses Tungsten. Before, `TungstenHelper` was an
all-false stub and `TungstenMoveTask` always used Baritone, so the bundled jar was never used.

- Set `speedrunMoverPreference` in `altoclef_settings.json` to `"tungsten"` or `"auto"`
  (default `"baritone"` keeps the old behaviour). Sim loop: `sim-loop.ps1 -Mover tungsten`.
- Tungsten gives up to Baritone on its own: 25s with nothing pathing, or 75s total.
- Water always goes to `WaterBailTask`; mining/placing stays on Baritone.
- `@t2status` shows `tungsten=true primary=true` when it is live.

Bridge fixes that came with it: a path request made while the previous search was still
stopping used to be dropped silently (the bridge reported success); probing before Tungsten
initialised cached "missing" for the whole session; `TungstenGotoTask` re-requested every tick,
restarting the search each time (now every 1.5s at most).
