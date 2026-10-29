# `#allblocks` live run on 26.3: proof and status

A survival run of `@allblocks start` (the same runner `#allblocks` uses) on Minecraft 26.3 with TenorClef and
Ostinato from branch `ccr-586b308f-pzyom7`. Singleplayer world `AllBlocks`, cheats off, survival mode, storage origin
75, 70, -102 in a spruce forest. The run went across 72 client sessions (restarts for fixes) and roughly 27 hours of
wall-clock time, including development.

**The run did not finish.** It stored 35 of the 1,534 survival items (1,202 of which have a gather path) and placed
20 of the 1,109 placeable blocks. Everything
below is read back from the world save, not from the bot's own log.

## What the world holds

`verify.txt` (from `tools/allblocks/verify_world.py`, reading the region files and `level.dat`):

- `allowCommands=0`, `GameType=0`: cheats were off and the game was survival, so `/give` could not have been used.
- 13 storage chests hold 35 unique items, one of each, with no duplicates. Every item the progress file calls stored
  is in its chest.
- All 20 grid cells the progress file calls placed hold a block.

`no-commands.txt`: none of the 72 session logs, nor the current one, contains a command sent by the bot.

`storage-and-grid-topdown.png` (from `tools/allblocks/render_area.py`): the chest row and the grid seen from above,
drawn with the game's textures, followed by what each grid cell and each chest holds in the save. Two cells differ
from what was placed, and both are expected: the spruce sapling in cell 3 grew into a tree (spruce log), and string
placed as a block is a tripwire.

`chest-row.png`: the labeled chest row in game during `@allblocks tour`. `death-drops-recovered.png`: the bot after
walking back to a death spot and picking up its armor and tools.

`run-log.txt.gz`: every `ALLBLOCKS` line, death and respawn of all sessions, in order.

## Why it is not complete

The bot gathers about 3 items an hour over the whole run, and much less after the cheap items were done:

- **Deaths.** About 26 deaths: zombies 13, skeletons 4, spiders 3, creepers 3, and one each from a drowned, a
  pillager and a trader llama. Each one used to cost the whole kit (pickaxe, sword, armor, food, bed), about 45
  minutes to rebuild.
- **Far searches.** Items that are not near home (biome flowers, crops, sea plants) send the bot wandering hundreds of
  blocks, and night often catches it far from home.

Fixes made during the run (all in this branch, see the commit log): sleeping in a carried bed or digging in at dusk,
holding still in the pit until morning, picking up the drops after a death (on the surface only), keeping the layout
protected while mob defense or eating has control, re-placing a grid block that went missing, crops on tilled
farmland, one copy per item in each chest (the 26.3 slot-click tracker never saw a click, so whole stacks went in),
and gather-order costs for sea plants and sniffer plants.

## Not obtainable through the current code

`report.txt` lists every entry that will not complete on its own. Rule exclusions (124: spawn eggs, command and
creative items, items with no survival source) are `SKIP`. The 332 items without a gather path are `UNSUPPORTED`.
The failing code path is the same for all of them: `TaskCatalogue.taskExists(item)` is false, with no hand-written
entry and no resolvable line in `allblocks/catalogue-26.3.txt`. The report groups them by root cause (`GAP` lines):

| Root cause | Items | What TenorClef would need |
| --- | ---: | --- |
| loot-boss-trade | 92 | Structure loot, bosses, villager trades, rare mobs |
| copper-aging | 90 | Placing copper and waiting days for it to age, then waxing |
| silk-touch | 58 | Enchanting a silk touch tool |
| no-source | 26 | Mechanics outside the jar's recipes and loot tables (chicken variants, chainmail, mob heads, froglights) |
| loot-brushing | 23 | Brushing suspicious sand and gravel |
| ingredient | 14 | A recipe ingredient that has no path (resin, honey, poplar wood, snow) |
| coral | 10 | Live coral (silk touch) left out of water to die |
| brewing-bottles | 7 | Brewing stand and bottling |
| mob-bucket | 7 | Using a water bucket on the mob |
| sniffer | 5 | Hatching a sniffer from an egg brushed out of ocean ruins |

One block is `PLACE-SKIP`: red mushroom, which needs light below 13 or a podzol or mycelium floor (both silk touch),
and the grid is in daylight.

## Known gaps

- The in-game tour views are poor at this forest site (trees and terrain block the grid); the top-down render is the
  reliable picture.
- TenorClef's respawn fallback sends `/kill @s` when a respawn request is ignored twice (a world saved at 0 HP). It
  never fired in this run, and it fails with cheats off, but it is a command.
