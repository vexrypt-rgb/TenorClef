# Water mine / swim stall (testrun2)

## Symptom
PORTAL CollectWaterBucket: `wet=true`, repeated `T2 [S102] swim out spd=0.000`, E107 portal stall.
Bot bobbing in water while trying to break a block — crosshair never locks.

## Root cause
1. `DestroyBlockTask` treated `isTouchingWater()` as valid mining footing and, when reach flickered, **held left-click** while bobbing.
2. `T2Solve` S102 only nudged forward+jump (`T2Input.swim`) and returned null — mining child kept thrashing.
3. Ostinato/Baritone `Movement.update` always held JUMP in liquid → classic #2377 bob.

## Fixes
- TenorClef: escape via `GetOutOfWaterTask` before mine; CollectWaterBucket prefers shore stand.
- Ostinato: port cabaletta/baritone#3988 `swimInWater` sprint-swim (see Ostinato `docs/SWIM_PORT.md`).

## Tungsten on 1.16.1
Not on classpath — `build.gradle` only includes tungsten jars for 1.21/1.21.1/1.21.11.
`:1.16.1:runClient` uses Ostinato Baritone jar only. `mover=baritone` is expected.
