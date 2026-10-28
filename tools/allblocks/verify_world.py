#!/usr/bin/env python3
"""Check an @allblocks run against the saved world, independent of the bot's own log.

    pip install nbtlib
    python3 tools/allblocks/verify_world.py <world dir> <altoclef/allblocks/<world>.json> [--json out.json]

Reads the region files of the overworld and reports:
  * every chest of the storage area and the items in it (unique item ids, duplicates across chests);
  * whether each item the progress file calls stored really is in a chest;
  * the block in every grid cell the progress file calls placed;
  * level.dat's allowCommands and game mode (cheats off, survival: /give cannot have been used).
Exit code 0 when everything the progress file claims is in the world.
"""
import json
import math
import os
import sys
import zlib

import nbtlib

CHESTS_PER_ROW, CELLS_PER_ROW, GRID_Z = 16, 32, 12


def chest_pos(o, k):
    return (o[0] + 2 * (k % CHESTS_PER_ROW), o[1], o[2] + 3 * (k // CHESTS_PER_ROW))


def cell_pos(o, n):
    return (o[0] + 2 * (n % CELLS_PER_ROW), o[1], o[2] + GRID_Z + 2 * (n // CELLS_PER_ROW))


def state_name(entry):
    """Block id of a palette entry: {Name: ..., Properties: ...} before 26.x, a bare string (or {'': ...}) after."""
    if isinstance(entry, str):
        return entry
    if 'Name' in entry:
        return str(entry['Name'])
    if '' in entry:
        return str(entry[''])
    return str(entry)


class Region:
    def __init__(self, world):
        # 26.x keeps the overworld under dimensions/minecraft/overworld; older saves use region/ at the top.
        new = os.path.join(world, 'dimensions', 'minecraft', 'overworld', 'region')
        self.dir = new if os.path.isdir(new) else os.path.join(world, 'region')
        self.cache = {}

    def chunk(self, cx, cz):
        key = (cx, cz)
        if key in self.cache:
            return self.cache[key]
        rx, rz = cx >> 5, cz >> 5
        path = os.path.join(self.dir, f'r.{rx}.{rz}.mca')
        result = None
        if os.path.exists(path):
            with open(path, 'rb') as f:
                data = f.read()
            i = 4 * ((cx & 31) + (cz & 31) * 32)
            off = int.from_bytes(data[i:i + 3], 'big') * 4096
            if off:
                length = int.from_bytes(data[off:off + 4], 'big')
                comp = data[off + 4]
                raw = data[off + 5:off + 4 + length]
                if comp == 2:
                    raw = zlib.decompress(raw)
                import io
                result = nbtlib.File.parse(io.BytesIO(raw))
        self.cache[key] = result
        return result

    def block(self, x, y, z):
        c = self.chunk(x >> 4, z >> 4)
        if c is None:
            return None
        for s in c.get('sections', []):
            if int(s['Y']) != y >> 4:
                continue
            bs = s.get('block_states')
            if bs is None:
                return 'minecraft:air'
            pal = bs['palette']
            if 'data' not in bs:
                return state_name(pal[0])
            bits = max(4, math.ceil(math.log2(len(pal))))
            per = 64 // bits
            idx = ((y & 15) * 256) + ((z & 15) * 16) + (x & 15)
            word = int(bs['data'][idx // per]) & ((1 << 64) - 1)
            v = (word >> ((idx % per) * bits)) & ((1 << bits) - 1)
            return state_name(pal[v])
        return None

    def block_entity(self, x, y, z):
        c = self.chunk(x >> 4, z >> 4)
        if c is None:
            return None
        for be in c.get('block_entities', []):
            if int(be['x']) == x and int(be['y']) == y and int(be['z']) == z:
                return be
        return None


def main():
    world, prog_path = sys.argv[1], sys.argv[2]
    out_json = sys.argv[sys.argv.index('--json') + 1] if '--json' in sys.argv else None
    prog = json.load(open(prog_path))
    o = prog['origin']
    reg = Region(world)
    report = {'chests': {}, 'missing_stored': [], 'placed_ok': [], 'placed_wrong': [], 'duplicates': []}

    level = nbtlib.load(os.path.join(world, 'level.dat'))['Data']
    report['allowCommands'] = int(level.get('allowCommands', -1))
    report['GameType'] = int(level.get('GameType', -1))

    chests = sorted(set(prog.get('chestOf', {}).values()))
    seen = {}
    for k in chests:
        p = chest_pos(o, k)
        be = reg.block_entity(*p)
        items = []
        if be is not None:
            for it in be.get('Items', []):
                items.append((str(it['id']).replace('minecraft:', ''), int(it.get('count', it.get('Count', 1)))))
        if be is None and reg.block(*p) != 'minecraft:chest':
            continue
        report['chests'][k] = {'pos': p, 'items': items}
        for iid, _ in items:
            if iid in seen:
                report['duplicates'].append(iid)
            seen[iid] = k
    for iid, k in prog.get('stored', {}).items():
        if iid not in seen:
            report['missing_stored'].append(iid)
    for iid, n in prog.get('placed', {}).items():
        p = cell_pos(o, n)
        b = reg.block(*p)
        (report['placed_ok'] if b is not None and b != 'minecraft:air' else report['placed_wrong']).append([iid, n, p, b])

    print(f"level.dat: allowCommands={report['allowCommands']} GameType={report['GameType']} (0/0 = cheats off, survival)")
    print(f"chests found: {len(report['chests'])}; unique items in chests: {len(seen)}; duplicates: {len(report['duplicates'])}")
    print(f"progress says stored: {len(prog.get('stored', {}))}; not found in any chest: {len(report['missing_stored'])} {report['missing_stored'][:20]}")
    print(f"progress says placed: {len(prog.get('placed', {}))}; cell holds a block: {len(report['placed_ok'])}; empty/unloaded: {len(report['placed_wrong'])}")
    for row in report['placed_wrong'][:20]:
        print('  placed but empty:', row)
    if out_json:
        json.dump(report, open(out_json, 'w'), indent=1, default=str)
    ok = report['allowCommands'] == 0 and not report['missing_stored'] and not report['placed_wrong']
    sys.exit(0 if ok else 1)


if __name__ == '__main__':
    main()
