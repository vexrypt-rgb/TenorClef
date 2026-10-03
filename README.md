# TenorClef

TenorClef is an autonomous Minecraft client bot derived from AltoClef. It combines
high-level objective tasks, survival and inventory behavior with the Ostinato
pathfinding engine. Its current focus is reliable autonomous play, speedrun-oriented
tasks, and a modern Fabric target.

TenorClef is not affiliated with AltoClef, Marvion, or MiranCZ. Those projects are
the upstream history of this fork.

> [!WARNING]
> **Only the Minecraft 26.3 build has been tested** (manually, in-game). The 26.3 port is experimental.
> Other versions (1.21.4, 1.21.11, 1.16.1) were **not** rebuilt or tested after the 26.3 mixin changes.
> The changes are wrapped in `//#if MC >= 260000` blocks, so older targets should be unaffected, but this is unverified.

## Supported versions

TenorClef always runs on [Ostinato](https://github.com/vexrypt-rgb/Ostinato), so it is only built
for the Minecraft versions Ostinato is built for:

| Minecraft | Status | Ostinato | Notes |
| --- | --- | --- | --- |
| 26.3 | Experimental, tested in-game | branch `26.3` (`libs/baritone-unoptimized-fabric-ostinato-26.3.jar`) | Java 25; opt-in via `-Pwith26`; the only version tested after the port |
| 1.21.4 | Primary | `main` (`libs/baritone-unoptimized-fabric-1.21.4.jar`) | Anarchy target; vanilla recipe-book crafting disabled (1.21.2+ servers do not sync recipes) |
| 1.16.1 | Legacy | branch `1.16.1` (`libs/baritone-unoptimized-fabric-1.16.1.jar`) | Legacy pairing |
| 1.21.11 | Experimental | branch `1.21.11` (built from source) | Not a release target |

The other versions under `versions/` (1.21.1 down to 1.16.5) are only steps in the source
preprocessor chain; they are not compiled or released. The complete, version-matched setup is in
[the Ostinato wiring guide](docs/OSTINATO_WIRING.md).

## Minecraft 26.3 (experimental)

The 26.3 module is opt-in: it is only included in the Gradle build with `-Pwith26`, so it cannot
break the other targets. It uses Mojang names (Minecraft 26.x is unobfuscated) and Java 25.

### Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5
- Fabric API `0.161.0+26.3`
- Ostinato jar for 26.3, included in `libs/baritone-unoptimized-fabric-ostinato-26.3.jar`
  (built from the Ostinato branch `26.3`; see the [wiring guide](docs/OSTINATO_WIRING.md))
- Java 25 to run the game

### Install (26.3)

Put exactly these three jars in your `mods` folder:

1. `fabric-api-0.161.0+26.3.jar`
2. `altoclef-26.3-<version>.jar` (the full jar, **not** the `-slim` one)
3. `baritone-unoptimized-fabric-ostinato-26.3.jar`

Do not install a second Baritone or another TenorClef jar. Start with a single-player world.

### Build (26.3)

> [!IMPORTANT]
> Run Gradle itself on **JDK 21** (set `JAVA_HOME` to a JDK 21 install, and run `gradlew.bat --stop`
> first if a daemon on another JDK is still running). Gradle on JDK 25 fails in
> `:1.21.4:preprocessCode` with the message `25.0.4.1`. The Java 25 toolchain is used
> automatically to compile 26.3.

Run:

```bat
gradlew.bat :26.3:build -Pwith26 "-Pmod_version=0.23.3" -x ":26.3:test"
```

### Known limitations (26.3)

- `MixinLocalPlayer` is skipped silently on 26.3 (`require = 0`). A replacement for `getPitch`/`getYaw`
  (`getViewXRot`/`getViewYRot`) is not implemented yet.

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
gradlew.bat :1.21.4:build
```

On macOS or Linux run:

```sh
./gradlew :1.21.4:build
```

For a version that depends on a local Ostinato build, follow the wiring guide first.
The initial Gradle configuration can take a while because Minecraft is remapped.

## Movement backends

Every build uses Ostinato, the AltoClef-compatible Baritone fork. On the modern targets,
Tungsten is an optional travel backend; mining, building, and inventory operations use
Ostinato's Baritone processes. Ostinato also carries the encrypted `#swarm` link for
multi-bot groups, including coordinated region builds (`#swarm build`); see Ostinato's
`docs/REGION_BUILD.md`.

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
the one miss was goal 15 rep 0, which stalled 44 blocks away). The physics row is still historical. Earlier 1.16.1 travel runs (16 goals; baritone and kinematic x 3 reps, physics x 1; kinematic from a later run):

| Mover | Goals reached | Avg ticks (reached goals) |
| --- | --- | --- |
| Baritone | 47/48 | 368 |
| Kinematic (experimental) | 46/48 | 383 |
| Physics search (experimental, `physicsTravel`) | 15/16 | 401 |

Newer kinematic runs (2026-09-29, 16 goals x 3 reps, pinned origin, every trial fed to full hunger; one run each):

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