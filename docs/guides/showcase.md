# Showcase courses (`@show`)

`@show <demo>` builds a small course **east of the player** with `/fill` and sends the bot across it using
the Ostinato movement or combat feature being demonstrated. It needs cheats (operator commands) and
is meant for a creative or test world, never a server you do not own. Courses float high above the ground
with void around them, so the only way to the goal (an emerald pad) is the course itself. Each demo clears
the inventory first so leftover blocks cannot be used to bridge around the course.

| Demo | Shows |
| --- | --- |
| `everything` (also `all`) | Runs the whole list below in order (`physics` is skipped on 1.21.11 and 26.3) |
| `parkour` | Jump gaps, momentum/neo jumps |
| `swim`, `dive` | 3D swimming, underwater travel and air management |
| `boat` | Boat travel |
| `ladder` | Ladder/vine climbing and the ladder moves |
| `pillar` | Pillaring up |
| `bridge` | Bridging over a gap |
| `tunnel` | Tunnelling |
| `kinematic`, `physics` | Kinematic travel; `physics` is the older physics-search travel (1.16.1 builds only) |
| `melee`, `ranged`, `horde` | Combat arenas: a sealed arena full of mobs, finished when none are left |
| `off` | Cancel the demo and turn kinematic/physics travel back off |

Travel demos end when the bot stands on the pad, close horizontally, at the pad's height, on the ground;
combat demos end when no hostile mobs are left in the arena.

## Tips
- Run `@show off` if a course hangs; then `@stop`.
- Demos reset the travel settings (`kinematicTravel` etc.) before starting, so run them *before* tuning those.
- Ostinato's `-Dostinato.simbench` flags build similar courses for the simulation benchmark; see the
  [Ostinato movement guide](https://github.com/vexrypt-rgb/Ostinato/blob/main/docs/guides/movement.md).
- Only 1.21.11 has been exercised live in the most recent work; other versions compile but were not re-run.
