# Travel and navigation

TenorClef never walks by itself: every move goes through [Ostinato](https://github.com/vexrypt-rgb/Ostinato)
(Baritone-compatible pathing) or, on modern targets, optionally Tungsten.

## `@goto`
```
@goto 100 64 -200            # x y z
@goto 100 -200               # x z (any height)
@goto 70                     # y level only
@goto 100 64 -200 nether     # with a dimension
@goto nether                 # just change dimension (uses a portal)
```
Accepted shapes: `[x y z dimension]`, `[x z dimension]`, `[y dimension]`, `[dimension]`, `[x y z]`, `[x z]`, `[y]`.

## `@tgoto` and `@ttest`
`@tgoto` takes the same arguments but asks the Tungsten physics A* to travel when it is installed and
falls back to Baritone otherwise. `@ttest [legs]` runs a Tungsten self-test over `legs` random surface legs
(default 8). See [Tungsten backend](../TUNGSTEN_BACKEND.md). Which backend is used by default is Ostinato's
`movementBackend` setting (`baritone`, `tungsten`, `auto`); check the log if you expect Tungsten.

## People and places
| Command | Notes |
| --- | --- |
| `@follow [player]` | Follows a player. With no name it only works when issued through the butler (then it follows the requester) |
| `@sethome` / `@home` | Saves feet position to `altoclef/home.txt`; `@home` walks back |
| `@back` | Walks to the last death position (`altoclef/last_death.txt`) |
| `@village` | Paths to the nearest villager |
| `@locate_structure <structure>` | Locates a world-generated structure (the argument is an enum; tab completion lists it) |
| `@coords` | Prints current coordinates |

## Travel across dimensions
`netherFastTravelWalkingRange` (default 600) is the distance above which the bot prefers travelling through
the Nether. Portals are built/entered by the task layer when needed.

## Measuring movement
`@pathbench search [-|setting=a,b] [reps]` times path searches (optionally sweeping settings);
`@pathbench travel [baritone|tungsten|kinematic] [reps]` runs fixed goals end to end and writes CSV to
`versions/<mc>/run/pathbench/`. Recorded numbers and their caveats are in the README and
[BENCHMARKS.md](../BENCHMARKS.md).

## Test acceleration
`@warp <factor>` runs the singleplayer world and the bot `factor` times faster (1 = off, max 20). It exists
for testing; do not use it for timing comparisons.

## When travel fails
The bot retries with alternate paths, then aborts the goal (`Progress retries exhausted`). Use `@why` for the
reason and `@task` for the live leaf task. A goal at an unreachable place (for example below bedrock) is
expected to abort; that is covered in [OBJECTIVE_RUNS.md](../OBJECTIVE_RUNS.md).
