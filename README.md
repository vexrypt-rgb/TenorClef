# TenorClef

TenorClef is an autonomous Minecraft client bot derived from AltoClef. It combines
high-level objective tasks, survival and inventory behavior with the Ostinato
pathfinding engine. Its current focus is reliable autonomous play, speedrun-oriented
tasks, and a modern Fabric target.

TenorClef is not affiliated with AltoClef, Marvion, or MiranCZ. Those projects are
the upstream history of this fork.

## Supported versions

TenorClef always runs on [Ostinato](https://github.com/vexrypt-rgb/Ostinato), so it is only built
for the Minecraft versions Ostinato is built for:

| Minecraft | Status | Ostinato | Notes |
| --- | --- | --- | --- |
| 1.21.11 | Primary | `main` (`libs/baritone-unoptimized-fabric-ostinato-1.21.11.jar`) | Default build target; CI runs the unit tests on it |
| 1.21.4 | Supported | branch `1.21.4` (`libs/baritone-unoptimized-fabric-1.21.4.jar`) | Anarchy target; vanilla recipe-book crafting disabled (1.21.2+ servers do not sync recipes) |
| 1.16.1 | Legacy | branch `1.16.1` (`libs/baritone-unoptimized-fabric-1.16.1.jar`) | Legacy pairing |
| 26.3 | Experimental | branch `26.3` (release jar `ostinato-mc26.3-unoptimized-fabric-1.21.0.jar`) | Released as experimental |

The other versions under `versions/` (1.21.1 down to 1.16.5) are only steps in the source
preprocessor chain; they are not compiled or released. The complete, version-matched setup is in
[the Ostinato wiring guide](docs/OSTINATO_WIRING.md).

## Install

1. Download the TenorClef Fabric jar for your exact Minecraft version from this
   repository's [Releases](https://github.com/vexrypt-rgb/TenorClef/releases).
2. Place it in the instance's `mods` directory with Fabric Loader and Fabric API.
3. Install the matching Ostinato jar when the release notes require it. Do not add a
   second Baritone jar unless the release notes explicitly say to do so.
4. Start a single-player test world first. Include the game version, TenorClef and
   Ostinato versions, mod list, and `latest.log` when reporting a problem.

Each release lists the matching Ostinato jar in its notes (see [CHANGELOG.md](CHANGELOG.md)). If the Releases page is empty,
build from source using the instructions below rather than downloading an upstream
AltoClef jar.

## Build from source

TenorClef uses Java 21 for the current modern modules. On Windows run:

```bat
gradlew.bat :1.21.11:build
```

On macOS or Linux run:

```sh
./gradlew :1.21.11:build
```

`:1.21.4:build` and `:1.16.1:build` work the same way. For a version that depends on a local Ostinato
build, follow the wiring guide first.
The initial Gradle configuration can take a while because Minecraft is remapped.

## Movement backends

Every build uses Ostinato, the AltoClef-compatible Baritone fork. On the modern targets,
Tungsten is an optional travel backend; mining, building, and inventory operations use
Ostinato's Baritone processes. Ostinato also carries the encrypted `#swarm` link for
multi-bot groups, including coordinated region builds (`#swarm build`); see Ostinato's
`docs/REGION_BUILD.md`. TenorClef's own swarm (`@swarm`, or the Swarm tab in the menu) uses that same
link, so both need the Ostinato version named in the release notes; see the
[swarm guide](docs/guides/swarm-and-fleet.md).

When using an Ostinato-enabled pairing, its `movementBackend` setting selects
`baritone`, `tungsten`, or `auto`; `auto` falls back to Baritone when Tungsten is not
installed. The 1.16.1 pairing also has an experimental physics-driven `kinematicTravel`
controller, plus `pitfallAvoidance`. See [Ostinato's README](https://github.com/vexrypt-rgb/Ostinato) for
backend details.

## Benchmarking movement

The in-game `@pathbench` command measures the pathfinder and the movement layer:

- `@pathbench search [-|setting=a,b] [reps]` times path searches, optionally sweeping a setting.
- `@pathbench travel [baritone|tungsten|kinematic] [reps]` runs end-to-end trials over a fixed
  set of goals and records reached/stalled and ticks per goal.

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

## User guides

Step-by-step guides for every feature are in [docs/guides](docs/guides/README.md):

- [Getting started](docs/guides/getting-started.md)
- [Command reference](docs/guides/commands.md)
- [Getting items and resources](docs/guides/items-and-resources.md)
- [Travel and navigation](docs/guides/travel.md)
- [Combat and survival](docs/guides/combat-and-survival.md)
- [Speedruns and advancements](docs/guides/speedruns.md)
- [Swarm, fleet and agents](docs/guides/swarm-and-fleet.md)
- [Building and map art](docs/guides/building.md)
- [Showcase courses](docs/guides/showcase.md)
- [Settings](docs/guides/settings.md)
- [Diagnostics and tools](docs/guides/diagnostics.md)

## Project guides

- [Development / CI (Phase 1)](docs/DEVELOPMENT.md)
- [Ostinato wiring](docs/OSTINATO_WIRING.md)
- [Usage](usage.md)
- [Development](develop.md)
- [Planned work](TODO.md)

## Reporting issues

Please use the [TenorClef issue tracker](https://github.com/vexrypt-rgb/TenorClef/issues).
Describe the goal, Minecraft version, TenorClef and Ostinato versions, installed mods,
and attach a relevant log or reproduction steps.

## License and notices

TenorClef is licensed under the [MIT License](LICENSE). Releases which bundle or
depend on Ostinato must preserve Ostinato's LGPL-3.0 notices and provide a way to
obtain its corresponding source. See [LICENSING.md](LICENSING.md).
