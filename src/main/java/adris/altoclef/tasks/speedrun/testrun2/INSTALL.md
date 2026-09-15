# @testrun2 — modern RSG + tungsten movement

Drop these onto a **UnionClef** tree (the fork that already compiles `tungsten/`
and has `adris.altoclef.util.helpers.TungstenHelper`).

## Files

```
src/main/java/adris/altoclef/commands/Testrun2Command.java
src/main/java/adris/altoclef/commands/T2StatusCommand.java
src/main/java/adris/altoclef/tasks/speedrun/testrun2/   (whole package)
src/main/java/kaptainwutax/tungsten/path/specialMoves/  (water-fix overwrite)

```

## Register

In `AltoClefCommands.java`, next to `new GamerCommand()`:

```java
new GamerCommand(),
new Testrun2Command(),
new T2StatusCommand(),
new T2FightCommand(),
new T2ResetCommand(),
```

## Run

```
@testrun2
```

Log lines to watch:

```
TESRUN2: tungsten PRIMARY movement is live
TESRUN2 phase -> BOOTSTRAP
TESRUN2 phase BOOTSTRAP -> LOOT
...
TESRUN2: finished in M:SS.t
```

## What changed vs `@gamer`

| Stock BeatMinecraftTask | testrun2 |
|---|---|
| Baritone/shredder first (or whatever the goal layer picks) | `TungstenHelper.setPrimary(true)` + `tryPathTo` on travel |
| Diamond chest/legs/boots before eyes | no armor grind |
| Extra eyes / fat food buffer | 6 rods + 6 pearls = 12 eyes |
| Mine iron as default | loot village / RP / wreck first |
| Perch-and-shoot End | beds if we already have them |

Movement is tungsten's physics A* (`;goto`-class sprint jumps). Mining, crafting,
blaze combat still use AltoClef tasks — tungsten does not replace those.

## Compile notes

- `TungstenHelper.stop()` / `isActive()` / `isLocked()` already exist on UnionClef.
  If an older snapshot is missing `stop()`, delete those three calls; pathing will
  still start via `tryPathTo`.
- `EnterNetherPortalTask(Dimension)` and `GoToStrongholdPortalTask(int)` match
  current UnionClef. If your constructor set differs, the compiler will tell you
  — swap to the overload your tree has.
- `StorageHelper.calculateInventoryFoodScore()` is the stock helper. If renamed,
  just `return 20;` and keep going.
- `mod.getBlockScanner().getNearestBlock(Block...)` — some forks return
  `Optional<BlockPos>`. Unwrap accordingly.

## Sub-10

This command is the *modern route + faster mover*. Sub-10 still needs a seed
that a human would reset for (close village or enterable RP, fortress not 400
blocks, stronghold not 2k). Without a resetter, treat anything under 30 as a
win and iterate the stall points you see in the phase log.

## Next knobs (after you drop the folder)

1. Force `CollectBlazeRodsTask` to stop at 6, not 7–12.
2. Skip iron armor / shield if the RP chest already has gold + flint and steel.
3. Wire `RavageRuinedPortalsTask` as the first LOOT action when a portal chest
   is scanned.
4. If bed dragon compile-fails, keep the stock closer — beds are the time cut
   in the End, not a requirement to finish.
