# Command reference

All commands take the prefix `@` (setting `commandPrefix`). Descriptions come from each command's own
registration in `src/main/java/adris/altoclef/commands/`. Aliases are in parentheses. Details live in the
linked guide.

## Items and inventory — [guide](items-and-resources.md)
| Command | What it does |
| --- | --- |
| `@get <item[ n]>[, item…]` | Get items/resources, crafting and mining whatever is needed |
| `@xget [item\|list]` | Get an item through the extra catalogue (aliases, version skips); `@xget list` dumps it |
| `@goal <item> [n]` / `@goal status` | Acquire a catalogue item through the planner (`GoalManager`) and show the plan |
| `@food <n>` / `@meat <n>` | Collect `n` units of food / meat |
| `@give <player> <items> [x y z]` | Collect items and hand them to a player |
| `@deposit [items]` | Deposit all (or the listed) items into a container |
| `@stash x1 y1 z1 x2 y2 z2 [items]` | Store items in a chest within a box; everything non-equipped if no items |
| `@equip <items>` | Equip armor/tools; aliases `leather`, `iron`, `gold` etc. expand to a set |
| `@inventory [item]` | Print the inventory, or the count of one item |
| `@list` | List all obtainable items |

## Travel — [guide](travel.md)
| Command | What it does |
| --- | --- |
| `@goto <x y z [dim]>` / `<x z>` / `<y>` / `<dim>` | Travel to coordinates or a dimension |
| `@tgoto …` | Same, via the Tungsten physics A* when available, else Baritone |
| `@ttest [legs]` | Tungsten self-test over random surface legs |
| `@follow [player]` | Follow a player |
| `@sethome` (`@set_home`) / `@home` (`@t2home`) | Save feet position / walk back to it |
| `@back` | Walk to the last death position |
| `@village` | Path to the nearest villager |
| `@locate_structure <structure>` | Locate a generated structure |
| `@coords` | Print the bot's coordinates |
| `@pathbench`, `@bench` | Benchmarks, see [Diagnostics](diagnostics.md) |
| `@warp <factor>` | Simulation time warp for singleplayer testing (1 = off, max 20) |

## Combat and survival — [guide](combat-and-survival.md)
| Command | What it does |
| --- | --- |
| `@hero` | Kill all hostile mobs |
| `@pvp [players\|mobs\|bench N]` | Fight the nearest player / hostiles; `bench N` runs the singleplayer bench |
| `@t2fight` | Fight the nearest hostile with any tool/weapon |
| `@threat` | Threat monitor status |
| `@idle` | Stand still |

## Speedruns — [guide](speedruns.md)
`@gamer`, `@testrun [mode] [flags]`, `@testrun2`, `@manhunt [player]`, `@runner`, `@aa`, `@zerocycle` (`@0cycle`),
`@groundzero` (`@gz`), `@t2core`, `@t2stat`, `@t2reset`.

## Swarm, fleet and agents — [guide](swarm-and-fleet.md)
`@swarm`, `@fleet` (`@link`), `@butler` (`@tenorbutler`), `@agent`, `@ado`, `@seal`.

## Building and map art — [guide](building.md)
`@schem [file\|list]`, `@mapart` (`@printart`), `@escape`.

## Showcase — [guide](showcase.md)
`@show <demo>`.

## Control, diagnostics and tools — [guide](diagnostics.md)
| Command | What it does |
| --- | --- |
| `@help` | List all commands |
| `@stop` (`@cancel`) | Stop all automation |
| `@pause`, `@unpause` (`@resume`) | Pause / resume the running task |
| `@status` | Status of the running command/task chain |
| `@task`, `@tasktree`, `@why` | What the bot is doing now, the live task tree, why the goal is or is not progressing |
| `@reload_settings` | Reload settings and butler lists |
| `@gamma <value>` | Set brightness |
| `@logdump`, `@t2panic`, `@t2doctor` | Write history, release keys and dump, print wiring/files |
| `@comp <list\|compile\|run\|stop\|status\|log\|tree> [name]` | Compositions |
| `@dj [play\|stop\|list]` (`@nbs`) | Play Note Block Studio songs |
| `@headless` (`@nogui`) | Cap FPS/view distance for a VPS |
| `@t2menu` | Open the TenorClef menu |
| `@test`, `@marvion` | Developer/legacy; `@marvion` is marked unsupported |
