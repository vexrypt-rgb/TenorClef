# Stall map for @testrun2

## Bugs that were in the first draft (now patched)

| Bug | Symptom | Fix |
|---|---|---|
| New child every tick | Bot starts a task, immediately cancels it, stands still | `stick()` reuses `active` when `isEqual` |
| LOOT wander forever | No village → TimeoutWanderTask never yields | 90s cap then force IRON |
| Re-loot same chest | Walk to chest, open, walk back | `looted` blacklist |
| `\|\| true` bed grind | 10+ min of sheep before stronghold | Beds only if wool already in inv |
| `get obsidian` when we have 10 | Idle “collecting” obsidian we already hold | Just `EnterNetherPortalTask` |
| `wanted == null` → closer instantly | BeatMinecraftTask fights our phases next tick | Re-decide once; closer only on real stall / End |
| Piglin trade first forever | Gold in bag, no fortress, 0 rods | 75s trade cap then blazes |
| `isFinished` never true | Timer never prints | DONE when dragon gone or closer finished |
| PORTAL without bucket | Flint from RP, walk into lava lake | Bucket required before PORTAL |
| Water + tungsten lock | Drowned, phase doesn't change | WaterBailTask intercept |

## Stock AltoClef / Tungsten stalls we cannot fully own

- **Food chain** steals the user task when hunger drops. Looks like “stuck between tasks.” Eat or carry more food.
- **Mob defense chain** same story next to a blaze spawner.
- **Crafting table missing in the Nether** when crafting eyes — catalogue should place one; if your fork doesn't, closer takes over after 2 min.
- **Flint** needs gravel. No gravel + no RP flint = IRON phase stall → closer at 2 min.
- **GoToStrongholdPortalTask** throwing eyes into a cave wall / ocean. Water fix helps ocean; caves are stock.
- **Two pathfinders** if shredder still gets a goal while tungsten is locked. UnionClef G-0 should have tungsten primary; `setPrimary(true)` is on start.
- **Death** resets inventory; `decide()` jumps back to BOOTSTRAP. That is correct, not a hang.

## What to watch in chat

```
TESRUN2 phase BOOTSTRAP -> LOOT
TESRUN2 phase LOOT -> IRON
TESRUN2 stall in IRON after 120s — closer
TESRUN2: closer = BeatMinecraftTask
TESRUN2 water-bail -> x y z
```

If you see phase A -> B -> A every few seconds, paste the log. That would be a new oscillation.
