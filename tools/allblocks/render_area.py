#!/usr/bin/env python3
"""Top-down picture of an @allblocks chest room and block grid, drawn from the saved world with the game's textures.

    pip install nbtlib pillow
    python3 tools/allblocks/render_area.py <world dir> <progress.json> <client.jar> <out.png>

Each block column of the area is drawn with the texture of the highest block at the run's height (y-1 .. y+1).
Chests are outlined and numbered; placed grid cells are outlined green. Reads the world only, so it shows what is
really there, not what the bot's log says.
"""
import io
import json
import sys
import zipfile

from PIL import Image, ImageDraw

sys.path.insert(0, __import__('os').path.dirname(__file__))
from verify_world import Region, chest_pos, cell_pos, CHESTS_PER_ROW, CELLS_PER_ROW, GRID_Z  # noqa: E402

PX = 16


def texture(jar, name, cache={}):
    if name in cache:
        return cache[name]
    img = None
    for cand in (f'{name}_top', name, f'{name}_front', f'{name}_side', name.replace('_slab', '').replace('_stairs', '') + 's',
                 name.replace('_slab', '').replace('_stairs', '').replace('_wall', '')):
        path = f'assets/minecraft/textures/block/{cand}.png'
        if path in jar.namelist():
            img = Image.open(io.BytesIO(jar.read(path))).convert('RGBA')
            img = img.crop((0, 0, 16, 16)).resize((PX, PX), Image.NEAREST)
            break
    if img is None:
        path = f'assets/minecraft/textures/item/{name}.png'
        if path in jar.namelist():
            img = Image.open(io.BytesIO(jar.read(path))).convert('RGBA').resize((PX, PX), Image.NEAREST)
    cache[name] = img
    return img


def main():
    world, prog_path, jar_path, out = sys.argv[1:5]
    prog = json.load(open(prog_path))
    o = prog['origin']
    jar = zipfile.ZipFile(jar_path)
    reg = Region(world)
    chests = sorted(set(prog.get('chestOf', {}).values()))
    rows = max(1, (prog.get('nextCell', 0) + CELLS_PER_ROW - 1) // CELLS_PER_ROW)
    x0, x1 = o[0] - 2, o[0] + 2 * CELLS_PER_ROW + 1
    z0, z1 = o[2] - 2, o[2] + GRID_Z + 2 * rows + 2
    img = Image.new('RGBA', ((x1 - x0) * PX, (z1 - z0) * PX), (30, 30, 30, 255))
    draw = ImageDraw.Draw(img)
    for x in range(x0, x1):
        for z in range(z0, z1):
            for y in (o[1] + 1, o[1], o[1] - 1):
                b = reg.block(x, y, z)
                if b and b not in ('minecraft:air', 'minecraft:cave_air'):
                    name = b.replace('minecraft:', '')
                    t = texture(jar, name)
                    px, pz = (x - x0) * PX, (z - z0) * PX
                    if t is not None:
                        img.alpha_composite(t, (px, pz))
                    else:
                        draw.rectangle([px, pz, px + PX - 1, pz + PX - 1], fill=(120, 90, 40, 255))
                    if y < o[1]:  # floor only: darken so placed blocks stand out
                        draw.rectangle([px, pz, px + PX - 1, pz + PX - 1], fill=(0, 0, 0, 90))
                    break
    for k in chests:
        x, _, z = chest_pos(o, k)
        if x0 <= x < x1 and z0 <= z < z1 and reg.block(*chest_pos(o, k)) == 'minecraft:chest':
            px, pz = (x - x0) * PX, (z - z0) * PX
            draw.rectangle([px, pz, px + PX - 1, pz + PX - 1], outline=(255, 210, 0, 255), width=2)
            draw.text((px + 2, pz + 2), str(k), fill=(255, 255, 255, 255))
    for iid, n in prog.get('placed', {}).items():
        x, _, z = cell_pos(o, n)
        px, pz = (x - x0) * PX, (z - z0) * PX
        draw.rectangle([px, pz, px + PX - 1, pz + PX - 1], outline=(0, 255, 0, 255), width=1)
    img.save(out)
    print(f'{out}: {img.size[0]}x{img.size[1]}, {len(chests)} chests assigned, {len(prog.get("placed", {}))} cells placed')


if __name__ == '__main__':
    main()
