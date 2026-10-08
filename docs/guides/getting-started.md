# Getting started

## Install

1. Download the TenorClef Fabric jar for your exact Minecraft version from
   [Releases](https://github.com/vexrypt-rgb/TenorClef/releases). Release `v0.24.0` ships jars for
   1.16.1, 1.21.4, 1.21.11 and 26.3.
2. Put it in `mods/` next to Fabric Loader and Fabric API.
3. TenorClef needs [Ostinato](https://github.com/vexrypt-rgb/Ostinato) (the pathing/combat engine). The
   release notes name the matching Ostinato jar; do **not** add a second Baritone jar. See
   [Ostinato wiring](../OSTINATO_WIRING.md).
4. Start a **single-player test world first**.

## Talking to the bot

Commands start with `@` (setting `commandPrefix`). Type them in the chat box; they are not sent to the
server. `@help` lists every command. Other players can command the bot by whisper only through the
[butler](swarm-and-fleet.md#butler-remote-control-by-whisper).

Typical first session:

```
@get iron_pickaxe        # fetch an item, crafting everything needed
@status                  # what is it doing right now
@stop                    # stop everything (Ctrl+K also works)
```

`@pause` / `@unpause` (alias `@resume`) suspend and resume the running task without cancelling it.

## Files the bot writes

Under `<game dir>/altoclef/` (several are named in command help):

| File / folder | Written by |
| --- | --- |
| `altoclef_settings.json` | the settings file, see [Settings](settings.md) |
| `last_death.txt` | death position, used by `@back` |
| `home.txt` | `@sethome` |
| `last_run.log` | `@logdump`, `@t2panic` |
| `agent/` | `@agent` file protocol |
| `dj/` | your `.nbs` songs for `@dj` |
| `sigil/` | SIGIL keys and keyring (see [Swarm, fleet and agents](swarm-and-fleet.md#seal-and-sigil)) |

`altoclef_butler_whitelist.txt` lives in the run directory.

## Which commands are safe to try first

Read-only: `@help`, `@status`, `@coords`, `@inventory`, `@list`, `@task`, `@tasktree`, `@why`, `@t2doctor`.
Everything else starts a task that moves, mines, fights or places blocks. Never run `@escape`, `@seal`,
`@fleet`, `@swarm` or `@butler` on a server without reading their sections.

## Reporting a problem

Include the Minecraft, TenorClef and Ostinato versions, the mod list, and `latest.log`; `@why` and `@task`
output helps. Issues: <https://github.com/vexrypt-rgb/TenorClef/issues>.
