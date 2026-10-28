# `#allblocks` / `@allblocks`

A survival collection run on Minecraft 26.3. For every item in the registry that a survival player can get, the bot
gathers one and stores it in a labeled chest. Every placeable block it gets is also placed once on a grid. Nothing is
spawned: all items come from TaskCatalogue's gather paths (mining, crafting, smelting, mobs, loot), and the run never
sends a chat command.

Ostinato owns the `#allblocks` command and TenorClef does the work (`AllBlocksBridge` registers it as Ostinato's
runner). `@allblocks` does the same from TenorClef's own command prefix.

| Command | What it does |
| --- | --- |
| `#allblocks start [x y z] [retry]` | Start or resume. The origin defaults to your feet and is fixed after the first start. `retry` gives stuck items another go. |
| `#allblocks status` | Obtained / placed / skipped / unsupported / stuck / remaining, and what the bot is doing. |
| `#allblocks report [n]` | Stuck, unsupported and skipped entries with the reason and the failing code path. |
| `#allblocks stop` | Stop. Progress is kept. |
| `#allblocks reset` | Forget this world's progress (the chests and the grid stay). |
| `#allblocks tour` | Walk to the chest-room view, then the grid view, and hold each for 10 seconds (for screenshots). |

## Layout

O is the origin. All positions are at O's height; each chest, sign and grid cell gets a solid floor block under it and
two clear blocks above it.

- **Chests**: chest k at O + (2·(k mod 16), 0, 3·(k div 16)). Single chests with a gap between them, so they never
  join. Chest k holds the 27 items with plan index 27k to 27k+26 (the plan is cheapest first; the assignment is saved
  at the first start, so it does not move later).
- **Labels**: a sign at chest + (0, 0, 1): `ALLBLOCKS k`, the first item id, `to`, the last item id.
- **Grid**: cell n at O + (2·(n mod 32), 0, 12 + 2·(n div 32)), one block per cell with a gap between cells so that
  beds, doors and redstone do not interfere. A block that cannot stand on the default floor gets dirt, sand, soul sand
  (nether wart) or end stone (chorus) under it instead; when none works it is skipped with the reason. Crops (seeds,
  potatoes, carrots, stems, torchflower, pitcher pod) get a dirt floor tilled to farmland with a hoe (a wooden hoe is
  made when none is carried).

## What happens each tick

1. A sign edit screen (a label was just placed) gets the label text and closes.
2. **Night**: at dusk the bot drops off what it carries and goes home; at night it sleeps in its bed. When it cannot
   sleep within a minute (no bed, monsters nearby) it digs a two-deep pit where it stands, closes it over its
   head and waits for morning.
3. **Survival kit**, before every gather: a pickaxe with life left (copper, then iron, then stone; a wooden pickaxe
   does not count), a sword (copper, then iron, then stone), food score 10 or more, a bed, and armor in every slot
   (copper, then iron, then leather). Each need gets 12 minutes; a need that runs out of time cools down for 30 minutes
   and the next choice is tried. The bot keeps one of each kit item (and its food) out of the chests.
4. **Deposit**, when 4 gathered items wait, fewer than 5 inventory slots are free, the bot is within 24 blocks of
   home, or nothing is left to gather. It places the chest and its label first when they are missing, puts exactly one of
   each item in, and records an item as stored only when the chest's contents show it. Near home, each chest is
   opened once per session and any extra copies are taken back out, so a chest never holds a stack.
5. **Place**: a stored block with a spare copy in the inventory goes into the next grid cell.
6. **Gather** the next item, cheapest first: 2 of a placeable block (one for the chest, one for the grid), 1 of
   anything else. The first try gets 10 minutes; when it fails, the rest of its family (the same first word:
   `acacia_`, `red_`, ...) waits until everything else has had a try. The retry gets 25 minutes; an item that fails
   both is reported as stuck.
7. **Blocks sweep**: a stored block that was never placed is gathered once more and placed.

Items gathered but not yet in a chest are protected from being thrown away. If one is used up as an ingredient, it is
gathered again later.

## What is left out, and why

- **By rule** (never attempted, reported as `SKIP`): spawn eggs; command and creative items (command blocks,
  structure blocks, jigsaw, barrier, light, debug stick, test blocks, knowledge book); items with no survival source
  (bedrock, end portal frame, reinforced deepslate, budding amethyst, spawners, vaults, farmland, dirt path, petrified
  oak slab, chorus plant, frogspawn, player head, suspicious sand and gravel, infested blocks).
- **Unsupported** (reported as `UNSUPPORTED`): an obtainable item with no TaskCatalogue path. These are capability
  gaps, not exclusions.
- **Stuck** (reported as `STUCK`): an item whose gather failed twice. The report shows the task chain that was running
  when it gave up.
- **Not placed**: items that are not blocks (tools, food, entities such as boats, buckets of fluid) are only stored.
  Blocks that cannot stand on any floor the grid offers (they need water or a wall) are reported as
  `PLACE-SKIP`.

## Gather paths on 26.3

Besides the hand-written catalogue, 26.3 loads `src/main/resources/allblocks/catalogue-26.3.txt`, made by
`tools/allblocks/gen_catalogue.py` from the 26.3 client jar:

- `craft` and `smelt` lines: the vanilla crafting and smelting recipes, used for an item without a hand-written entry
  once every ingredient has a path (copper tools and armor, wood variants, colored blocks, ...).
- `mine` lines: natural blocks that drop themselves without silk touch and have no recipe, with the pickaxe tier
  from the block tags and the dimension they generate in.
- `order` lines: a rough cost per item, used for the gather order.

Re-run the generator when the Minecraft version changes.

## Progress and proof

Progress is saved after every change to `<game dir>/altoclef/allblocks/<world>.json` (`sp-<world name>` in
singleplayer, `mp-<address>` on a server). A crash or relaunch resumes from the next missing item; items already in
the inventory are deposited first.

Every gather, deposit, placement and failure is logged with an `ALLBLOCKS` prefix. To check a run against the world
itself, independent of the log:

```sh
pip install nbtlib
python3 tools/allblocks/verify_world.py <saves/World> <altoclef/allblocks/sp-World.json>
```

`tools/allblocks/render_area.py <saves/World> <progress.json> <client.jar> <out.png>` draws the chest room and the
grid from above with the game's textures.

`verify_world.py` reads the region files and reports every chest's contents, whether each stored item is really in a chest, the
block in each placed grid cell, and level.dat's `allowCommands` (0 means cheats were off, so `/give` could not have
been used).
