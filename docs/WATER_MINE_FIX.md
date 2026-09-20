# Water mine / swim stall (testrun2)

## Symptom
PORTAL CollectWaterBucket: `wet=true`, repeated `T2 [S102] swim out spd=0.000`, E107 portal stall.
Bot bobbing in water while trying to break a block — crosshair never locks; bucket never fills.

## Root cause (verified)

1. **`GetOutOfWaterTask.isEqual` always returned `false`.** Parents (`CollectBucketLiquidTask`, `DestroyBlockTask`, `T2Solve` S102) return `new GetOutOfWaterTask()` every tick. Task system treated each as a different child → interrupt → `CustomBaritoneGoalTask.onStart` **forceCancel** → speed stays ≈0 → endless S102.
2. **`T2Solve` S102 called `cancelPath` every tick** even while already escaping, plus `T2Input.swim()`, fighting pathing and reinforcing bob thrash.
3. **Immediate scoop while standing in the liquid column** while bobbing (`!onGround`) never kept look lock.
4. Ostinato/Baritone `Movement.update` JUMP-in-liquid bob (#2377) remains an engine-level issue; `swimInWater` exists in Ostinato source but TenorClef must still mitigate without relying on a jar rebuild.

## Fixes (this PR)

| Change | Why |
|--------|-----|
| `GetOutOfWaterTask.isEqual` → `instanceof GetOutOfWaterTask` | Stop per-tick path cancel |
| Escape goal heuristic water=8 / adjacent=2 | Milder escape gradient |
| `T2Solve` S102: if already escaping → `null`; no `T2Input.swim()` | Stop cancel thrash |
| `CollectBucketLiquidTask` sticky `GetOutOfWaterTask` + wet-bob timeout blacklist | Shore first; recover via wander |
| Immediate scoop only when grounded; else escape | No look-down thrash in column |
| `ShoreStandSelector` pure helper + unit tests | Deterministic shore pick |

## Ostinato follow-up (out of scope for this PR)

- Ensure the 1.16.1 Ostinato jar used by `:1.16.1:runClient` includes `swimInWater` (#3988) and is copied into `altoclef/libs`.
- See Ostinato `docs/SWIM_PORT.md` / prior `water-swim-fix` bundles.

## Tungsten on 1.16.1
Not on classpath — `build.gradle` only includes tungsten jars for 1.21/1.21.1/1.21.11.
`:1.16.1:runClient` uses Ostinato Baritone jar only. `mover=baritone` is expected.
