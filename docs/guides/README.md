# TenorClef user guides

Task-oriented guides for everything TenorClef can do. Every command and setting named here was
read from the source (`src/main/java/adris/altoclef/commands/`, `Settings.java`); where a feature has
only been described from its built-in help and was not exercised for this guide, the guide says so.

| Guide | Covers |
| --- | --- |
| [Getting started](getting-started.md) | Install, first launch, command prefix, stopping the bot, where files live |
| [Command reference](commands.md) | Every `@command`, one line each, grouped |
| [Getting items and resources](items-and-resources.md) | `@get`, `@xget`, `@goal`, `@food`, `@meat`, `@give`, `@deposit`, `@stash`, `@equip`, `@inventory`, `@list` |
| [Travel and navigation](travel.md) | `@goto`, `@tgoto`, `@follow`, `@home`, `@back`, `@village`, `@locate_structure`, `@pathbench`, `@warp` |
| [Combat and survival](combat-and-survival.md) | `@hero`, `@pvp`, `@t2fight`, `@threat`, Mob Defense, auto-eat, bucket clutch, respawn |
| [Speedruns and advancements](speedruns.md) | `@testrun`, `@testrun2`, `@gamer`, `@manhunt`, `@runner`, `@aa`, `@zerocycle`, `@groundzero` |
| [Swarm, fleet and agents](swarm-and-fleet.md) | `@swarm`, `@fleet`, `@butler`, `@agent`, `@ado`, `@seal` |
| [Building and map art](building.md) | `@schem`, `@mapart`, `@escape` |
| [Showcase courses](showcase.md) | `@show` demos for the movement and combat features |
| [Settings](settings.md) | `altoclef_settings.json`, `@reload_settings`, the settings that matter |
| [Diagnostics and tools](diagnostics.md) | `@status`, `@task`, `@tasktree`, `@why`, `@bench`, `@logdump`, `@t2doctor`, `@t2panic`, `@comp`, `@dj`, `@headless` |

Related, existing documents: [Ostinato wiring](../OSTINATO_WIRING.md) ·
[Architecture](../ARCHITECTURE.md) · [Agent protocol](../AGENT_PROTOCOL.md) ·
[Capabilities](../CAPABILITIES.md) · [Benchmarks](../BENCHMARKS.md) ·
[Tungsten backend](../TUNGSTEN_BACKEND.md) · [Changelog](../../CHANGELOG.md).

For the movement engine's own commands (`#goto`, `#mine`, `#build`, `#pvp`, `#swarm`, freecam, the
Ostinato screen) see the [Ostinato guides](https://github.com/vexrypt-rgb/Ostinato/blob/main/docs/guides/README.md).
TenorClef decides *what* to do; Ostinato decides *how* to move and fight.
