#!/usr/bin/env python3
"""Generate src/main/resources/allblocks/catalogue-26.3.txt from a vanilla client jar.

    python3 tools/allblocks/gen_catalogue.py <client.jar> > src/main/resources/allblocks/catalogue-26.3.txt

The file feeds two things on 26.3:
  * TaskCatalogue's generated fallback: vanilla crafting/smelting recipes and self-dropping natural blocks for items
    the hand-written catalogue does not cover ("craft", "smelt", "mine" lines).
  * @allblocks' gather order: every item with a rough cost, cheapest first ("order" lines).
Only data from the jar is used. Re-run it when the Minecraft version changes.
"""
import json
import sys
import zipfile

jar = zipfile.ZipFile(sys.argv[1])
names = jar.namelist()


def load(path):
    return json.loads(jar.read(path))


def strip(i):
    return i.replace('minecraft:', '')


ITEMS = sorted(n[len('assets/minecraft/items/'):-5] for n in names
               if n.startswith('assets/minecraft/items/') and n.endswith('.json'))
ITEMSET = set(ITEMS)

_tags = {}


def tag(kind, t):
    key = (kind, strip(t))
    if key in _tags:
        return _tags[key]
    _tags[key] = []
    p = f'data/minecraft/tags/{kind}/{strip(t)}.json'
    out = []
    if p in names:
        for v in load(p)['values']:
            v = v if isinstance(v, str) else v['id']
            for x in (tag(kind, v[1:]) if v.startswith('#') else [strip(v)]):
                if x not in out:
                    out.append(x)
    _tags[key] = out
    return out


# Hand-written catalogue groups a tag can map to (TaskCatalogue names). Listed first so the bot may use any member.
GROUP = {'planks': 'planks', 'logs': 'log', 'logs_that_burn': 'log', 'wool': 'wool', 'wooden_slabs': 'wooden_slab',
         'stone_crafting_materials': 'cobblestone', 'stone_tool_materials': 'cobblestone', 'coals': 'coal',
         'soul_fire_base_blocks': 'soul_soil', 'saplings': 'sapling', 'small_flowers': 'flower'}


def ingredient(x):
    """Alternatives for one recipe slot, as tokens separated by '|'."""
    if isinstance(x, list):
        toks = []
        for y in x:
            for t in ingredient(y).split('|'):
                if t not in toks:
                    toks.append(t)
        return '|'.join(toks)
    if isinstance(x, dict):
        x = ('#' + x['tag']) if 'tag' in x else x.get('item', '')
    if x.startswith('#'):
        t = strip(x[1:])
        members = tag('item', t)
        return '|'.join(([GROUP[t]] if t in GROUP else []) + members)
    return strip(x)


craft_lines, smelt_lines = [], []
recipes = {}  # out -> list of (count, [token...])
for n in sorted(names):
    if not (n.startswith('data/minecraft/recipe/') and n.endswith('.json')):
        continue
    r = load(n)
    t = strip(r['type'])
    res = r.get('result')
    if not res:
        continue
    out = strip(res['id'] if isinstance(res, dict) else res)
    count = res.get('count', 1) if isinstance(res, dict) else 1
    if out not in ITEMSET:
        continue
    if t == 'crafting_shaped':
        pat = r['pattern']
        h, w = len(pat), max(len(row) for row in pat)
        grid = 2 if (w <= 2 and h <= 2) else 3
        slots = []
        for y in range(grid):
            for x in range(grid):
                c = pat[y][x] if y < h and x < len(pat[y]) else ' '
                slots.append('-' if c == ' ' else ingredient(r['key'][c]))
        craft_lines.append(f'craft {out} {count} {grid} ' + ' '.join(slots))
        recipes.setdefault(out, []).append((count, [s for s in slots if s != '-']))
    elif t in ('crafting_shapeless', 'crafting_transmute'):
        ings = [ingredient(i) for i in r['ingredients']] if t == 'crafting_shapeless' \
            else [ingredient(r['input']), ingredient(r['material'])]
        if len(ings) > 9:
            continue
        grid = 2 if len(ings) <= 4 else 3
        slots = ings + ['-'] * (grid * grid - len(ings))
        craft_lines.append(f'craft {out} {count} {grid} ' + ' '.join(slots))
        recipes.setdefault(out, []).append((count, ings))
    elif t == 'smelting':
        ing = ingredient(r['ingredient'])
        smelt_lines.append(f'smelt {out} {ing}')
        recipes.setdefault(out, []).append((1, [ing]))

# Blocks that drop themselves without silk touch or a special tool, and have no recipe: natural blocks to mine.
REQ = [('needs_diamond_tool', 'DIAMOND'), ('needs_iron_tool', 'IRON'), ('needs_stone_tool', 'STONE')]
NETHER_TAGS = ['crimson_stems', 'warped_stems', 'nylium', 'base_stone_nether', 'wart_blocks']
nether = set(x for t in NETHER_TAGS for x in tag('block', t))
END = {'end_stone', 'chorus_flower', 'chorus_plant', 'dragon_egg', 'purpur_block'}


def self_drop(block):
    p = f'data/minecraft/loot_table/blocks/{block}.json'
    if p not in names:
        return False
    found = []

    def walk(node, guarded):
        if isinstance(node, dict):
            conds = json.dumps(node.get('conditions', node.get('condition', '')))
            g = guarded or 'silk' in conds or 'match_tool' in conds or 'shears' in conds
            if node.get('type') == 'minecraft:item' and strip(node.get('name', '')) == block and not g:
                found.append(True)
            for k, v in node.items():
                if k not in ('conditions', 'condition'):
                    walk(v, g)
        elif isinstance(node, list):
            for v in node:
                walk(v, guarded)

    walk(load(p), False)
    return bool(found)


# Self-dropping blocks that do not generate as such: they come from water, aging, a mob or a vault. A "mine" line
# would send the bot searching the world for them, so they stay without a generated path.
NOT_NATURAL = ('concrete', 'exposed_', 'weathered_', 'oxidized_', 'dead_', 'chipped_anvil', 'damaged_anvil',
               'golem_statue', '_head', '_skull', 'heavy_core', 'dragon_egg', 'carved_pumpkin', 'bell',
               'stripped_', 'froglight', 'sniffer_egg', 'wither_rose', 'petrified_', '_crop',
               'torchflower', 'pitcher_plant')  # the last two grow only from sniffer seeds
mine_lines = []
for item in ITEMS:
    if item in recipes or not self_drop(item) or any(k in item for k in NOT_NATURAL):
        continue
    req = 'HAND'
    if item in tag('block', 'mineable/pickaxe'):
        req = 'WOOD'
        for t, r in REQ:
            if item in tag('block', t):
                req = r
                break
    dim = 'NETHER' if (item in nether or any(k in item for k in ('crimson', 'warped', 'nether', 'soul', 'blackstone', 'basalt', 'shroomlight', 'weeping', 'twisting'))) \
        else 'END' if item in END else 'OVERWORLD'
    mine_lines.append(f'mine {item} {item} {req} {dim}')

# Shears: blocks that drop themselves only to shears (or silk touch): leaves, bushes, dry grass, hanging moss.
shear_lines = []


def shear_drop(block):
    p = f'data/minecraft/loot_table/blocks/{block}.json'
    if p not in names:
        return False
    found = []

    def walk(node, conds):
        if isinstance(node, dict):
            c = conds + json.dumps(node.get('conditions', node.get('condition', '')))
            if node.get('type') == 'minecraft:item' and strip(node.get('name', '')) == block and 'shear' in c:
                found.append(True)
            for k, v in node.items():
                if k not in ('conditions', 'condition'):
                    walk(v, c)
        elif isinstance(node, list):
            for v in node:
                walk(v, conds)

    walk(load(p), '')
    return bool(found)


for item in ITEMS:
    if item in recipes or item in {l.split()[1] for l in mine_lines} or any(k in item for k in NOT_NATURAL):
        continue
    if shear_drop(item):
        shear_lines.append(f'shear {item} {item}')

# Natural blocks that drop another item without silk touch, shears or a special tool (chorus plant -> chorus fruit,
# cave vines -> glow berries). Items without any other path only.
covered = {l.split()[1] for l in mine_lines + shear_lines} | set(recipes)
for n in sorted(names):
    if not (n.startswith('data/minecraft/loot_table/blocks/') and n.endswith('.json')):
        continue
    block = n[len('data/minecraft/loot_table/blocks/'):-5]
    if block in recipes or any(k in block for k in NOT_NATURAL):
        continue
    drops = []

    def walk(node, guarded):
        if isinstance(node, dict):
            conds = json.dumps(node.get('conditions', node.get('condition', '')))
            g = guarded or 'silk' in conds or 'match_tool' in conds or 'shear' in conds
            if node.get('type') == 'minecraft:item' and not g:
                drops.append(strip(node.get('name', '')))
            for k, v in node.items():
                if k not in ('conditions', 'condition'):
                    walk(v, g)
        elif isinstance(node, list):
            for v in node:
                walk(v, guarded)

    walk(load(n), False)
    for item in drops:
        if item in ITEMSET and item != block and item not in covered:
            covered.add(item)
            req = 'HAND'
            if block in tag('block', 'mineable/pickaxe'):
                req = 'WOOD'
                for t, r in REQ:
                    if block in tag('block', t):
                        req = r
                        break
            dim = 'NETHER' if (block in nether or any(k in block for k in ('crimson', 'warped', 'nether', 'soul', 'blackstone', 'basalt', 'weeping', 'twisting'))) \
                else 'END' if (block in END or 'chorus' in block) else 'OVERWORLD'
            mine_lines.append(f'mine {item} {block} {req} {dim}')

# Mob drops: items an entity's loot table drops on a plain kill (no "killed by" another mob, no player-only
# special case) and that have no recipe and no mine line. Bosses and non-mobs are left out.
NO_HUNT = {'ender_dragon', 'wither', 'player', 'armor_stand', 'mannequin', 'giant', 'illusioner', 'warden',
           'iron_golem', 'snow_golem', 'villager', 'wandering_trader', 'allay', 'copper_golem'}
NETHER_MOBS = {'blaze', 'ghast', 'magma_cube', 'hoglin', 'piglin', 'piglin_brute', 'strider', 'wither_skeleton',
               'zoglin', 'zombified_piglin'}
END_MOBS = {'shulker', 'endermite'}
mined = {l.split()[1] for l in mine_lines + shear_lines}
kill_lines = []
hunted = {}
for n in sorted(names):
    if not (n.startswith('data/minecraft/loot_table/entities/') and n.endswith('.json')):
        continue
    mob = n[len('data/minecraft/loot_table/entities/'):-5]
    if '/' in mob or mob in NO_HUNT:
        continue
    found = []

    def walk(node, guarded):
        if isinstance(node, dict):
            conds = json.dumps(node.get('conditions', node.get('condition', '')))
            g = guarded or 'damage_source' in conds or 'entity_properties' in conds
            if node.get('type') == 'minecraft:item' and not g:
                found.append(strip(node.get('name', '')))
            for k, v in node.items():
                if k not in ('conditions', 'condition'):
                    walk(v, g)
        elif isinstance(node, list):
            for v in node:
                walk(v, guarded)

    walk(load(n), False)
    for item in found:
        if item in ITEMSET and item not in recipes and item not in mined:
            hunted.setdefault(item, []).append(mob)

# Best mob per item: the one the item is named after, then a common one.
RARE = {'elder_guardian', 'polar_bear', 'witch', 'evoker', 'camel_husk', 'bogged', 'cat', 'hoglin', 'piglin_brute',
        'stray', 'husk', 'drowned', 'zombie_villager', 'vindicator', 'pillager', 'ravager'}
for item, mobs in sorted(hunted.items()):
    mob = sorted(mobs, key=lambda m: (m not in item, m in RARE, len(m), m))[0]
    dim = 'NETHER' if mob in NETHER_MOBS else 'END' if mob in END_MOBS else 'OVERWORLD'
    kill_lines.append(f'kill {item} {mob} {dim}')

# Rough cost for the gather order. Raw costs for things the hand catalogue gets in its own ways.
RAW = {'oak_log': 1, 'log': 1, 'dirt': 1, 'cobblestone': 2, 'stone': 3, 'sand': 2, 'gravel': 2, 'clay_ball': 4,
       'coal': 3, 'raw_copper': 4, 'copper_ingot': 5, 'raw_iron': 5, 'iron_ingot': 6, 'raw_gold': 10,
       'gold_ingot': 11, 'gold_nugget': 3, 'diamond': 14, 'redstone': 10, 'lapis_lazuli': 8, 'emerald': 30,
       'wool': 4, 'string': 6, 'leather': 5, 'feather': 5, 'bone': 6, 'gunpowder': 8, 'egg': 6, 'wheat': 5,
       'sugar_cane': 4, 'ender_pearl': 20, 'blaze_rod': 40, 'netherrack': 25, 'quartz': 28, 'nether_wart': 30,
       'glowstone_dust': 30, 'soul_sand': 26, 'soul_soil': 26, 'obsidian': 15, 'slime_ball': 25, 'honeycomb': 20,
       'flint': 4, 'apple': 4, 'ink_sac': 15, 'spider_eye': 8, 'rotten_flesh': 6, 'amethyst_shard': 25,
       'vine': 8, 'bamboo': 15, 'cactus': 10, 'pumpkin': 8, 'melon_slice': 10, 'cocoa_beans': 20, 'kelp': 12,
       'prismarine_shard': 60, 'prismarine_crystals': 60, 'shulker_shell': 200, 'nautilus_shell': 80,
       'phantom_membrane': 40, 'rabbit_hide': 10, 'ghast_tear': 70, 'magma_cream': 50, 'chorus_fruit': 150,
       'breeze_rod': 120, 'heart_of_the_sea': 150, 'echo_shard': 150, 'netherite_scrap': 120}
REQCOST = {'HAND': 2, 'WOOD': 3, 'STONE': 5, 'IRON': 9, 'DIAMOND': 16}
cost = dict(RAW)
# Woods that only grow in a few biomes: their logs (and everything made from them) go after the common ones, or the
# run spends its first hour walking to jungles and pale gardens.
for wood in ('acacia', 'dark_oak', 'jungle', 'mangrove', 'cherry', 'pale_oak', 'poplar', 'crimson', 'warped', 'bamboo'):
    for suffix in ('_log', '_stem', '_block', '_sapling', '_leaves', '_propagule'):
        if wood + suffix in ITEMSET:
            cost[wood + suffix] = 30
# Plants that grow only under water: diving for them (with shears, for seagrass) took the bot hundreds of blocks
# away; they go after the land items.
for item in ('seagrass', 'tall_seagrass', 'sea_pickle', 'kelp', 'lily_pad'):
    if item in ITEMSET:
        cost[item] = 40
for line in shear_lines:
    cost.setdefault(line.split()[1], 12)
for line in kill_lines:
    _, item, _, dim = line.split()
    cost.setdefault(item, 30 + (25 if dim == 'NETHER' else 150 if dim == 'END' else 0))
for line in mine_lines:
    _, item, _, req, dim = line.split()
    cost.setdefault(item, REQCOST[req] + (25 if dim == 'NETHER' else 150 if dim == 'END' else 0))


def tok_cost(tok):
    vals = [cost[t] for t in tok.split('|') if t in cost]
    return min(vals) if vals else None


for _ in range(30):
    changed = False
    for out, rs in recipes.items():
        for count, ings in rs:
            cs = [tok_cost(t) for t in ings]
            if None in cs:
                continue
            c = round(sum(cs) / max(1, count), 2) + 1
            if c < cost.get(out, 1e9):
                cost[out], changed = c, True
    if not changed:
        break

# Why an item has no generated path, by root cause. @allblocks reports unsupported items under these categories.
def silk_only(block):
    """The block drops itself only with silk touch (ores, grass, ice, coral, sculk, ...)."""
    p = f'data/minecraft/loot_table/blocks/{block}.json'
    return p in names and block in json.dumps(load(p)) and 'silk' in json.dumps(load(p)) and not self_drop(block) \
        and not shear_drop(block)


MOB_BUCKETS = ('cod_bucket', 'salmon_bucket', 'pufferfish_bucket', 'tropical_fish_bucket', 'axolotl_bucket',
               'tadpole_bucket', 'sulfur_cube_bucket')


def gap(item):
    if any(k in item for k in ('exposed_', 'weathered_', 'oxidized_')):
        return 'copper-aging', 'placed copper ages over in-game days; waxed kinds then need honeycomb on the aged block'
    if item.startswith('dead_'):
        return 'coral', 'coral placed out of water dies; live coral needs silk touch'
    if silk_only(item):
        return 'silk-touch', 'drops itself only to a silk touch tool (needs enchanting)'
    if item in ('torchflower', 'pitcher_plant', 'torchflower_seeds', 'pitcher_pod', 'sniffer_egg'):
        return 'sniffer', 'a sniffer digs the seeds up; its egg comes from brushing ocean ruins'
    if item in MOB_BUCKETS:
        return 'mob-bucket', 'a water bucket used on the mob'
    if 'potion' in item or item in ('dragon_breath', 'experience_bottle', 'ominous_bottle', 'honey_bottle'):
        return 'brewing-bottles', 'brewing stand, bottling or trading'
    if item.endswith('pottery_sherd'):
        return 'loot-brushing', 'brushing suspicious sand or gravel in ruins'
    if item.endswith(('smithing_template', 'banner_pattern')) or item.startswith('music_disc') or item.endswith('_map') \
            or 'horse_armor' in item or 'nautilus_armor' in item or item.startswith('enchanted_') \
            or item in ('heart_of_the_sea', 'elytra', 'trident', 'nether_star', 'heavy_core', 'ominous_trial_key',
                        'echo_shard', 'goat_horn', 'sniffer_egg', 'enchanted_book', 'filled_map', 'written_book',
                        'firework_star', 'mace', 'wolf_armor', 'turtle_helmet', 'armadillo_scute', 'conduit',
                        'beacon', 'recovery_compass'):
        return 'loot-boss-trade', 'structure loot, a boss, a trade, or a rare mob or mechanic, or crafted from one'
    if item in recipes:
        missing = sorted({t for _, ings in recipes[item] for t in ings if tok_cost(t) is None})
        if missing:
            return 'ingredient', 'recipe needs ' + ','.join(missing[:4]) + ' (no path)'
        return 'ingredient', 'an ingredient of its recipe has no TaskCatalogue path'
    if item in cost:
        return 'no-task', 'listed with a raw cost but TaskCatalogue has no task for it'
    return 'no-source', 'no recipe, natural self-drop or plain mob drop in the jar data'


print('# Generated by tools/allblocks/gen_catalogue.py from the Minecraft 26.3 client jar. Do not edit by hand.')
print('# order <item> <cost>: @allblocks gathers cheapest first; items without a cost come last.')
print('# craft <item> <count> <grid 2|3> <slot>...: slot "-" is empty, "a|b" are alternatives (catalogue names first).')
print('# smelt <item> <ingredient alternatives>   mine <item> <block> <HAND|WOOD|STONE|IRON|DIAMOND> <dimension>')
print('# kill <item> <entity> <dimension>: a mob that drops it   shear <item> <block>: shear the block')
print('# gap <item> <category> <why>: the root cause when the item has no gather path at run time')
for item in sorted(ITEMS, key=lambda i: (cost.get(i, 1e9), i)):
    print(f'order {item} {cost[item] if item in cost else -1}')
for line in craft_lines + smelt_lines + mine_lines + shear_lines + kill_lines:
    print(line)
# Every item: TaskCatalogue (hand-written entries included) decides at run time which ones really lack a path.
for item in ITEMS:
    if True:
        cat, why = gap(item)
        print(f'gap {item} {cat} {why}')
