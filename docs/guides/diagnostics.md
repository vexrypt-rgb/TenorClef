# Diagnostics and tools

## What is the bot doing?
| Command | Output |
| --- | --- |
| `@status` | status of the running command / task chain (useful through whisper) |
| `@task` | live chain, goal, leaf task and last failure |
| `@tasktree` | live task hierarchy of every active chain |
| `@why` | why the user goal is or is not progressing |
| `@goal status` | current planner goal and plan steps |
| `@threat` | threat monitor assessment |
| `@swarm status` / `@swarm why <id>` | swarm assignments and their event history |

Start with `@why`, then `@task`. If those disagree with what you see, `@t2panic` releases stuck keys and
closes any open screen, then writes `last_run.log`.

## Stopping and recovering
`@stop` (`@cancel`, Ctrl+K) cancels everything; `@pause` / `@unpause` keep the task. `@t2panic` is the
emergency button; `@t2reset` aborts the world for a singleplayer reset (T2 speedruns only).

## Logs and doctor
- `@logdump` writes T2 history to `altoclef/last_run.log`.
- `@t2doctor` prints testrun2 wiring, files, eyes, home and catalogue.
- `@list` lists obtainable items; `@xget list` the extra catalogue.

## Benchmarks
| Command | Purpose |
| --- | --- |
| `@bench start <name>` / `stop` / `status` | live benchmark session; `stop` writes a file under the benchmark directory |
| `@bench goal <item> [n]` | run a catalogue goal inside a session |
| `@pathbench search|travel …` | pathfinder and movement benchmarks ([Travel](travel.md)) |
| `@pvp bench N` | PvP bench in singleplayer |
| `@warp <f>` | speed up the world for tests |

## Compositions (`@comp`)
Saved multi-step compositions: `@comp list`, `compile <name>`, `run <name>`, `stop`, `status`, `log`,
`tree`. `compile` reports diagnostics for broken compositions; `run` is refused with a reason if the
composition cannot be performed.

## Quality-of-life
- `@dj [play|stop|list]` (`@nbs`): plays the first `.nbs` song in `altoclef/dj/` through note blocks (client side).
- `@headless` (`@nogui`): caps FPS and view distance for a VPS. It is not a true dedicated-server bot.
- `@gamma <n>`: brightness; values above 1 give fullbright.
- `@t2menu`: open the TenorClef menu.
- `@help`: list all commands.

## Developer-only
`@test [name]` runs experimental test tasks (terminate, deadmeme, 173, replace, piglin, stacked, netherite,
sign, bed; the list in `usage.md` is incomplete). `@marvion` is marked unsupported.
