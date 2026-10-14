# Building and map art

## `@schem` — build a schematic
Files go in `<gameDir>/schematics/` (Baritone's folder; created on first use). Supported extensions:
`.schem`, `.schematic`, `.litematic`, `.nbt`.

```
@schem list              # show files and sizes (the default)
@schem house.schem       # gather tools and blocks, then build at your feet
```
The origin is the block position of the player when the command runs. The task first gathers the materials
and tools, then builds with Ostinato's builder. Re-running is safe: blocks already correct are skipped.
To split one build across several bots use Ostinato's `#swarm build` ([region builds](https://github.com/vexrypt-rgb/Ostinato/blob/main/docs/REGION_BUILD.md)).

## `@mapart` (`@printart`)
Convert an image to map art, then gather the blocks and place it.

| Step | Command |
| --- | --- |
| Pick an image (file dialog, or drop a PNG into the inbox folder) | `@mapart add` (`open`, `pick`, `image`) |
| Convert everything in the inbox | `@mapart convert` (`inbox`) |
| Build the most recent conversion | `@mapart print` (`build`, `go`) |
| Show inbox, output and last result | `@mapart list` |

Whispered requests to `@mapart` are denied by the butler.

## `@escape`
2b2t-style spawn escape: a random destination (about ±30M blocks) reached through a Nether tunnel, filling
netherrack behind the bot, then a hidden shaft.
- `@escape` runs the whole route (kit, portal, tunnel to a destination/8, portal out, shaft).
- `@escape here` digs and seals the shaft at the current feet.
- `@escape help` prints a summary.
The destination is only written to `escape_dest.txt`; do not paste coordinates into chat. It runs until arrival
or `@stop`. Intended for anarchy servers; do not run it elsewhere.
