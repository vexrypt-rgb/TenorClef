# TenorClef

TenorClef is an autonomous Minecraft client bot derived from AltoClef. It combines
high-level objective tasks, survival and inventory behavior with the Ostinato
pathfinding engine. Its current focus is reliable autonomous play, speedrun-oriented
tasks, and a modern Fabric target.

TenorClef is not affiliated with AltoClef, Marvion, or MiranCZ. Those projects are
the upstream history of this fork.

## Supported versions

| Minecraft | Status | Movement engine | Notes |
| --- | --- | --- | --- |
| 1.21.1 | Primary | Matching Baritone artifact | Verified compile target |
| 1.21 | Maintained | Matching Baritone artifact | Build and test before use |
| 1.21.11 | Experimental | Ostinato `main` | Source port is incomplete; not a release target |
| 1.16.5 | Legacy | AltoClef-compatible Baritone | Legacy module |
| 1.16.1 | Legacy | Ostinato `1.16.1` | Requires the legacy Ostinato artifact |

The complete, version-matched setup is in [the Ostinato wiring guide](docs/OSTINATO_WIRING.md).
Older source trees may remain in the repository, but they are not a release promise.

## Install

1. Download the TenorClef Fabric jar for your exact Minecraft version from this
   repository's [Releases](https://github.com/vexrypt-rgb/TenorClef/releases).
2. Place it in the instance's `mods` directory with Fabric Loader and Fabric API.
3. Install the matching Ostinato jar when the release notes require it. Do not add a
   second Baritone jar unless the release notes explicitly say to do so.
4. Start a single-player test world first. Include the game version, TenorClef and
   Ostinato versions, mod list, and `latest.log` when reporting a problem.

No release jar is currently available if the Releases page is empty. In that case,
build from source using the instructions below rather than downloading an upstream
AltoClef jar.

## Build from source

TenorClef uses Java 21 for the current modern modules. On Windows run:

```bat
gradlew.bat :1.21.1:build
```

On macOS or Linux run:

```sh
./gradlew :1.21.1:build
```

For a version that depends on a local Ostinato build, follow the wiring guide first.
The initial Gradle configuration can take a while because Minecraft is remapped.

## Movement backends

The stable modern targets resolve a matching Baritone artifact. Ostinato supplies the
AltoClef-compatible engine for the 1.16.1 pairing and is the engine being developed
for the experimental 1.21.11 port. On the modern targets, Tungsten is an optional
travel backend; mining, building, and inventory operations use Baritone processes.

When using an Ostinato-enabled pairing, its `movementBackend` setting selects
`baritone`, `tungsten`, or `auto`; `auto` falls back to Baritone when Tungsten is not
installed. See [Ostinato's README](https://github.com/vexrypt-rgb/Ostinato) for
backend details.

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
