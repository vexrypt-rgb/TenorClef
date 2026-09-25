# Tungsten 1.16.1 dry-land @tgoto fix

**Time:** 2026-09-20 ~03:45 PT

## Root cause (verified by code vs tip)
`AgentBlockCollisions.computeNext` used `world.getExistingChunk(chunkX, chunkZ)`.
PathFinder runs Agent physics on a **background thread**. On 1.16.1 `ClientWorld`, `getExistingChunk` commonly returns **null** off-thread → **no collision shapes** → Agent never gets real ground / horizontal travel → children stay at parent pos → openSet drains → no `"Time taken to find path"` → AltoClef Baritone fallback after ~15s.

Tip Tungsten already avoids this by reading blocks via `this.world` as `BlockView` (not chunk lookup).

Secondary: `WalkToNode` was only added when `!canSprint()`; with food>6 only RunToNode/SprintJumpMove ran.

## Fix (minimal)
1. **AgentBlockCollisions** — fall back to `this.world` when `getExistingChunk` is null (tip-aligned).
2. **Node** — always add `WalkToNode` on dry land; pass `WorldView` into Walk/Run.
3. **WalkToNode / RunToNode** — accept `WorldView`, null-guard static world, log near-zero displacement.
4. **PathFinder** — debug logs (first children count/disp, openSet empty, executePath); inject WalkToNode if children have ~0 displacement; clamp bnIdx.

## Rebuild
- Box: `vendor/tungsten-1.16.1 ./gradlew remapJar` — **BUILD SUCCESSFUL**
- Jar: `libs/tungsten-fabric-ALPHA-1.6.0-1.16.1-SNAPSHOT.jar` (~247177 bytes)

## Windows apply
Run `APPLY-TUNGSTEN-DRYGOTO-ON-WINDOWS.ps1` with `tungsten-drygoto/` next to it (sources + jar), or set `$env:TUNGSTEN_DRYGOTO_AGENT_DIR`.

## Smoke
`:1.16.1:runClient` → `@tgoto 23 64 -149` from ~(26.5,63,-145.5). Expect:
- `Serching for inputs!`
- `[PathFinder] first getChildren ... firstChildDisp=` **> 0**
- path found / player walks via PathExecutor (not Baritone after 15s)

## Remaining risks
- Off-thread ClientWorld reads can still race; VoxelWorld snapshot would be more correct long-term.
- SprintJumpMove etc. still use static `TungstenModDataContainer.world`.
- Baritone fallback kept as safety net.
