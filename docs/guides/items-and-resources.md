# Getting items and resources

## `@get`
```
@get cobblestone 64
@get iron_pickaxe
@get diamond 3, iron_ingot 12     # several targets, comma separated
```
TenorClef resolves the item to a task (mine, smelt, craft, loot…) from its task catalogue and runs it as the
current user task. With more than one target it builds one combined ("squashed") task instead of running
them one after another. A bare name means count 1. If nothing runs, the item is not in the catalogue —
use `@list` to see what is obtainable.

Observed runs on 1.16.1 are tabulated in [OBJECTIVE_RUNS.md](../OBJECTIVE_RUNS.md) (e.g. `get iron_pickaxe`
finished in 110–115 s on a fresh seed, 3 of 3 runs). Those are dated measurements, not guarantees.

## `@xget`
`@xget list` prints the extra catalogue; `@xget <item>` gets an item through it, resolving aliases and
skipping items that do not exist on the current Minecraft version (it warns `not on this client` instead).

## `@goal` — the planner path
```
@goal cobblestone 64      # plan + execute
@goal status              # current goal, status, and each plan step
```
`@goal` uses the planner (`GoalManager` → `PlanRunnerTask`) rather than the plain task catalogue. It reports a
status (RUNNING / SUCCESS / FAILED / CANCELLED), and survives interruption: when Mob Defense takes over it
suspends and resumes the goal instead of cancelling it. A goal that ended is replaced by a fresh one when
you issue the same `@goal` again. This is also what the [swarm](swarm-and-fleet.md) workers run.

## Food
`@food <n>` collects `n` food units, `@meat <n>` meat. Automatic eating is a setting (`autoEat`), and
`minimumFoodAllowed` / `foodUnitsToCollect` control when the bot goes foraging by itself
(see [Settings](settings.md)).

## Handing items over
- `@give <player> <items> [x y z]` — collect the items, then bring them to the player (or the coordinates).
- `@deposit [items]` — put all (or listed) items into a container.
- `@stash x1 y1 z1 x2 y2 z2 [items]` — find a chest inside the box and store the items; no item list
  stores everything that is not equipped or a tool.
- `@equip <items>` — equip. Armor aliases `leather`, `iron`, `gold` expand to the whole set.
- `@inventory` prints everything; `@inventory <item>` returns one count.

## What the bot keeps and discards
Settings `throwawayItems`, `importantItems`, `throwAwayUnusedItems`, `reservedBuildingBlockCount`,
`dontThrowAwayCustomNameItems` and `dontThrowAwayEnchantedItems` decide what is dropped to free inventory
space. `areasToProtect` lists regions the bot should leave alone.
