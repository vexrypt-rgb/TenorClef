# Speedruns and advancements

## `@testrun` — the modern random-seed run
`@testrun [mode] [flags…]` runs `SpeedrunBeatMinecraftTask`, the phase-based speedrun route (not `@gamer`).

| Mode (aliases) | Starts at phase |
| --- | --- |
| `start` (`run`, `full`) — default | `OVERWORLD_EARLY` |
| `nether` (`nether_entry`, `portal`) | `NETHER_ENTRY` |
| `barter` (`loot`, `nether_gear`, `bastion`) | `BARTER_AND_LOOT` — pearls and bastion |
| `fortress` (`blaze`, `rods`) | `FORTRESS` — blaze rods |
| `stronghold` (`eyes`, `sh`) | `STRONGHOLD` |
| `end` (`dragon`, `end_fight`, `fight`) | `END_FIGHT` |
| `status` (`info`) | print live phase, timers and counts |
| `verbose` (`v`) | toggle verbose phase logs for the session |
| `help` (`?`, `phases`, `phase`) | print the mode and flag list |

Flags may come before the mode (`@testrun skipfood nether`):

| Flag | Meaning |
| --- | --- |
| `pearls=N` | ender pearl target (default 14, setting `speedrunPearlTarget`) |
| `rods=N` (`blaze=`, `blazerods=`) | blaze rod target (default 7, `speedrunBlazeRodTarget`) |
| `skipfood` / `nofood`, `food` / `collectfood` | skip or force food collection |
| `mover=auto\|tungsten\|ostinato` | travel backend preference (default `ostinato`, `speedrunMoverPreference`) |
| `verbose`, `hud` / `nohud` | verbose logs, phase chat HUD |

Starting at a later phase assumes you provide the inventory and dimension that phase expects. `@status`,
`@coords`, `@inventory`, `@stop`, `@pause` and `@unpause` work during a run.

## `@testrun2` and the T2 tools
`@testrun2` is the TenorClef speedrun implementation under `tasks/speedrun/testrun2`. Companion commands:

| Command | Purpose |
| --- | --- |
| `@t2stat` | print the testrun2 clock and kit |
| `@t2doctor` | print wiring, files, eyes, home and catalogue |
| `@t2core [demo]` | test sticky-child, reserve, progress-stall and input-arbiter behaviour |
| `@t2reset` | abort this world and signal a singleplayer reset |
| `@t2panic` | release keys, close the screen, write `last_run.log` |
| `@logdump` | write T2 history to `altoclef/last_run.log` |
| `@t2menu` | open the TenorClef menu |

## End-fight specialists
- `@zerocycle` (`@0cycle`) — end first-perch beds and a bow stall; pearls the dragon pillar.
- `@groundzero` (`@gz`) — fountain zero-cycle: a cage, punch a crystal, then beds.

## Other whole-game commands
| Command | Notes |
| --- | --- |
| `@gamer` | Beats the game with the inherited `BeatMinecraftTask` (Miran version) |
| `@manhunt [player]` | Hunter: track and kill the runner (optional target name) |
| `@runner` | Runner: speedrun and fight back if hunted |
| `@aa` | All Advancements 1.16.5 task: dragon route first, then leftovers; hard goals (Adventuring Time, Two by Two, How Did We Get Here) time out and are skipped |

## Honest status
The speedrun tasks are the largest and least predictable part of TenorClef. The repeatable evidence in this
repository covers small objectives ([OBJECTIVE_RUNS.md](../OBJECTIVE_RUNS.md)), not full-run completion
rates. Treat full runs as experimental; use `@testrun status` and the log to see where one stalls.
