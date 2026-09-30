# TenorClef

TenorClef is an autonomous Minecraft client bot derived from AltoClef. It
combines high-level objective tasks, survival and inventory behavior with the
[Ostinato](https://github.com/vexrypt-rgb/Ostinato) pathfinding engine.

Current focus: reliable autonomous play, speedrun-oriented tasks, and a modern
Fabric target. Package name stays `adris.altoclef`.

TenorClef is not affiliated with AltoClef, Marvion, or MiranCZ. Those projects
are the upstream history of this fork.

Current release line is **0.20.0**. Sister repos: [Ostinato](https://github.com/vexrypt-rgb/Ostinato)
(movement) and [SIGIL](https://github.com/vexrypt-rgb/sigil) (the sealed-chat
format Ostinato swarm uses).

## Supported versions

TenorClef always runs on Ostinato. Build only the Minecraft versions Ostinato
is built for, and never mix jars across versions.

| Minecraft | Status | Ostinato | Notes |
| --- | --- | --- | --- |
| 1.21.4 | Primary | `main` (`libs/baritone-unoptimized-fabric-1.21.4.jar`) | Anarchy target. Vanilla recipe-book crafting is disabled (1.21.2+ servers do not sync recipes). |
| 1.16.1 | Legacy | branch `1.16.1` (`libs/baritone-unoptimized-fabric-1.16.1.jar`) | RSG / `@testrun2` lane. Java 8 on the Ostinato side. |
| 1.21.11 | Experimental | branch `1.21.11` (built from source) | Not a release target. |
| 26.3 | Unsupported | branch `26.3` | Needs a newer JDK than this toolchain. |

Modules under `versions/` from 1.21.1 down to 1.16.5 exist only as steps in the
source preprocessor chain. They are not compiled or released. The
version-matched setup is in [the Ostinato wiring guide](docs/OSTINATO_WIRING.md).

## Install

GitHub Releases for this repository is empty as of this writing. Build from
source (below) rather than downloading an upstream AltoClef jar.

When a release *is* published:

1. Download the TenorClef Fabric jar for your exact Minecraft version.
2. Place it in the instance `mods` directory with Fabric Loader and Fabric API.
3. Install the matching Ostinato jar named in the release notes. Do not add a
   second Baritone jar unless the notes say to.
4. Start a single-player test world first. Include the game version, TenorClef
   and Ostinato versions, mod list, and `latest.log` when reporting a problem.

Each release lists the matching Ostinato jar in its notes (see
[CHANGELOG.md](CHANGELOG.md)).

## Build from source

TenorClef uses Java 21 for the modern modules. On Windows:

```bat
gradlew.bat :1.21.4:build
```

On macOS or Linux:

```sh
./gradlew :1.21.4:build
```

Legacy 1.16.1 (`:1.16.1:build` / `:1.16.1:runClient`) is the RSG client lane.
For a version that depends on a local Ostinato build, follow the wiring guide
first. The initial Gradle configuration can take a while because Minecraft is
remapped.

## Commands (short list)

TenorClef commands use `@`. Ostinato / Baritone commands use `#`.

| Command | What it does |
| --- | --- |
| `@help` | List TenorClef commands |
| `@gamer` | Beat the game |
| `@testrun2` | Modern speedrun task (BOOTSTRAP → END). 1.16.1 lane. |
| `@stop` / `Ctrl+K` | Cancel the current task |
| `@pathbench …` | Path / travel benches (see below) |
| `#help` | Ostinato / Baritone commands |
| `#swarm …` | Multi-bot link and region builds (Ostinato) |
| `@swarm …` | TenorClef front end for `#swarm`: `on`/`off`, `status`, `ping`, `reload`, `build`, `stop`, `set <setting> <value>`, `menu` |

Full command and settings notes: [usage.md](usage.md). Settings live under
`.minecraft/altoclef/` after the first launch; `@reload_settings` applies
edits.

## Movement backends

Every build uses Ostinato. On the modern targets, Tungsten is an optional
travel backend; mining, building, and inventory stay on Ostinato's Baritone
processes. Ostinato also carries the encrypted `#swarm` link for multi-bot
groups, including coordinated region builds (`#swarm build`). See Ostinato's
[`docs/REGION_BUILD.md`](https://github.com/vexrypt-rgb/Ostinato/blob/main/docs/REGION_BUILD.md).

`movementBackend` selects `baritone`, `tungsten`, or `auto`. `auto` falls back
to Baritone when Tungsten is not installed. The 1.16.1 pairing also has an
experimental physics-driven `kinematicTravel` controller, plus
`pitfallAvoidance`. Details: [Ostinato's README](https://github.com/vexrypt-rgb/Ostinato).

## Benchmarking movement

`@pathbench` measures the pathfinder and the movement layer:

- `@pathbench search [-|setting=a,b] [reps]` times path searches, optionally
  sweeping a setting.
- `@pathbench travel [baritone|tungsten|kinematic] [reps]` runs end-to-end
  trials over a fixed set of goals and records reached/stalled and ticks per
  goal.

Results are written as CSV to `versions/<mc>/run/pathbench/` (not committed). The table below was
recorded in earlier sessions; its Baritone and physics source CSVs were not retained, so treat it as
historical, not reproducible from the repo. A local kinematic run at 09:34 on 2026-09-28 reached only 11/38; every miss
never started moving (`firstMoveTicks=-1`), which points to the mover not starting, not to pathing. It did not reproduce:
a fresh run on the same day, 1 rep, reached 16/16 (avg 415 ticks, first move 8.5 ticks).
The kinematic row comes from a 3-rep run at 20:35 the same day (`pathbench_travel_kinematic_20260928_203533.csv`,
first move after 8.9 ticks on average, 0 runs that never moved).
The Baritone row comes from a 3-rep run at 20:56 the same day (`pathbench_travel_baritone_20260928_205606.csv`;
the one miss was goal 15 rep 0, which stalled 44 blocks away). The physics row is still historical. Earlier 1.16.1 travel runs (16 goals; baritone and kinematic × 3 reps, physics × 1; kinematic from a later run):

| Mover | Goals reached | Avg ticks (reached goals) |
| --- | --- | --- |
| Baritone | 47/48 | 368 |
| Kinematic (experimental) | 46/48 | 383 |
| Physics search (experimental, `physicsTravel`) | 15/16 | 401 |

Newer kinematic runs (2026-09-29, 16 goals × 3 reps, pinned origin, every trial fed to full hunger; one run each):

| Mover | Conditions | Goals reached | Avg ticks |
| --- | --- | --- | --- |
| Kinematic (experimental) | Mobs on | 48/48 | 233 |
| Kinematic (experimental) | Peaceful | 48/48 | 230 |

These replace the kinematic row above. Its lower score and higher tick count came mostly from hunger carrying over between trials. Baritone has not been re-run fed, so the two movers can't be compared yet. Details and logs are in [docs/BENCHMARKS.md](docs/BENCHMARKS.md).

The averages only cover goals each mover reached. The bench origin moves between runs, so
compare runs taken together. Single-rep runs are noisy; re-run with 3 reps before drawing conclusions.

## Project guides

- [Development / CI (Phase 1)](docs/DEVELOPMENT.md)
- [Ostinato wiring](docs/OSTINATO_WIRING.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Usage](usage.md)
- [Development notes](develop.md)
- [Planned work](TODO.md)

## Reporting issues

Use the [TenorClef issue tracker](https://github.com/vexrypt-rgb/TenorClef/issues).
Describe the goal, Minecraft version, TenorClef and Ostinato versions,
installed mods, and attach a relevant log or reproduction steps.

## License and notices

TenorClef is licensed under the [MIT License](LICENSE). Releases which bundle
or depend on Ostinato must preserve Ostinato's LGPL-3.0 notices and provide a
way to obtain its corresponding source. See [LICENSING.md](LICENSING.md).

## Vibe coding / AI use

Large parts of this repository were written or edited with AI assistants
(Claude, Grok, and similar). That is vibe coding: a person set the
direction; a model produced a lot of the text. A green CI run or a
commit message is not proof that a human understood every line.

Read the diff before you run or merge it. Do not treat this as audited
software. File bugs. Do not assume the model already considered your
case.
