#!/usr/bin/env python3
"""Generates the Greedy Meshing test datapack (testing/greedy_test_pack).

Run:  python3 testing/generate_test_pack.py
Scenes are built relative to a marker entity placed where /function greedy_test:build is run,
on a flat (superflat) world, in a 7x3 grid of 32x32 cells (224 x 96 blocks).
"""
import json
import os
import random
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(HERE, "greedy_test_pack")
FUNC = os.path.join(PACK, "data", "greedy_test", "function")
CELL = 32
COLS, ROWS = 7, 3

shutil.rmtree(PACK, ignore_errors=True)
os.makedirs(os.path.join(FUNC, "scene"))
os.makedirs(os.path.join(FUNC, "goto"))
os.makedirs(os.path.join(FUNC, "reset"))


class Fn:
    def __init__(self):
        self.lines = []

    def fill(self, x1, y1, z1, x2, y2, z2, block, mode=""):
        n = (abs(x2 - x1) + 1) * (abs(y2 - y1) + 1) * (abs(z2 - z1) + 1)
        assert n <= 32768, f"fill too large: {n}"
        self.lines.append(f"fill ~{x1} ~{y1} ~{z1} ~{x2} ~{y2} ~{z2} {block}" + (f" {mode}" if mode else ""))

    def set(self, x, y, z, block):
        self.lines.append(f"setblock ~{x} ~{y} ~{z} {block}")

    def raw(self, line):
        self.lines.append(line)

    def write(self, path):
        with open(os.path.join(FUNC, path), "w") as f:
            f.write("\n".join(self.lines) + "\n")


rng = random.Random(42)

# ── 0,0  walls: big flat vertical merges, checker and striped (merge-breaking) ────────────
f = Fn()
f.fill(2, 0, 2, 29, 13, 2, "minecraft:stone_bricks")                  # solid wall
for x in range(2, 14):                                               # checker half (breaks merging)
    for y in range(0, 14):
        f.set(x, y, 12, "minecraft:andesite" if (x + y) % 2 else "minecraft:stone")
f.fill(14, 0, 12, 29, 13, 12, "minecraft:polished_andesite")         # plain polished half
for y in range(0, 14):                                               # horizontal stripes
    f.fill(2, y, 22, 29, y, 22, ["minecraft:bricks", "minecraft:sandstone", "minecraft:deepslate_bricks"][y % 3])
f.write("scene/walls.mcfunction")

# ── 1,0  ambient-occlusion pillars: many distinct AO merge keys ─────────────────────────
f = Fn()
for x in range(2, 30, 2):
    for z in range(2, 30, 2):
        h = rng.randint(1, 6)
        f.fill(x, 0, z, x, h - 1, z, "minecraft:stone" if rng.random() < 0.7 else "minecraft:smooth_stone")
f.fill(0, 0, 0, 31, 0, 31, "minecraft:smooth_stone", "keep")
f.write("scene/pillars.mcfunction")

# ── 2,0  water: flat pool (merges), ledge flow (must not), waterlogged blocks ───────────
f = Fn()
f.fill(2, 0, 2, 25, 3, 25, "minecraft:stone_bricks")
f.fill(3, 1, 3, 24, 2, 24, "minecraft:water")                        # water to y=2, y=3 left open
f.fill(26, 0, 2, 26, 5, 8, "minecraft:stone_bricks")                  # ledge feeding a flowing sheet
f.set(26, 6, 2, "minecraft:water")
f.fill(27, 0, 2, 31, 0, 8, "minecraft:stone")
for i in range(8):
    f.set(4 + i * 2, 4, 28, "minecraft:oak_slab[type=bottom,waterlogged=true]")
    f.set(4 + i * 2, 4, 30, "minecraft:oak_stairs[facing=east,waterlogged=true]")
f.write("scene/water.mcfunction")

# ── 3,0  glass, tinted, stained, leaves, ice: translucent / cutout layers ────────────────
f = Fn()
colors = ["white", "red", "orange", "yellow", "lime", "cyan", "blue", "purple"]
f.fill(2, 0, 2, 29, 7, 2, "minecraft:glass")
f.fill(2, 0, 6, 29, 7, 6, "minecraft:tinted_glass")
for i, c in enumerate(colors):
    f.fill(2 + i * 3, 0, 10, 4 + i * 3, 7, 10, f"minecraft:{c}_stained_glass")
f.fill(2, 0, 14, 29, 7, 14, "minecraft:sea_lantern")
f.fill(2, 0, 18, 29, 3, 24, "minecraft:oak_leaves[persistent=true]")
f.fill(2, 0, 27, 29, 3, 29, "minecraft:ice")
f.write("scene/glass.mcfunction")

# ── 4,0  lighting: closed room with a single light source (gradient across merged floor) ─
f = Fn()
f.fill(0, 0, 0, 31, 7, 31, "minecraft:stone_bricks")
f.fill(1, 1, 1, 30, 6, 30, "minecraft:air")
f.set(1, 1, 1, "minecraft:glowstone")
f.set(30, 1, 30, "minecraft:redstone_lamp[lit=true]")
for x in range(8, 30, 8):
    f.set(x, 1, 15, "minecraft:lantern[hanging=false]")
f.fill(1, 6, 1, 30, 6, 30, "minecraft:stone_bricks")
f.write("scene/lighting.mcfunction")

# ── 5,0  orientation zoo: blocks the mod must leave on the normal renderer ──────────────
f = Fn()
row = 2
for half in ("bottom", "top"):
    for i, d in enumerate(("north", "south", "east", "west")):
        f.set(2 + i * 2, 0, row, f"minecraft:oak_stairs[facing={d},half={half}]")
    row += 2
for i, t in enumerate(("bottom", "top", "double")):
    f.set(2 + i * 2, 0, row, f"minecraft:oak_slab[type={t}]")
row += 2
for i, a in enumerate(("x", "y", "z")):
    f.set(2 + i * 2, 0, row, f"minecraft:oak_log[axis={a}]")
    f.set(2 + i * 2 + 8, 0, row, f"minecraft:stripped_birch_log[axis={a}]")
row += 2
for i, d in enumerate(("north", "south", "east", "west")):
    f.set(2 + i * 2, 0, row, f"minecraft:white_glazed_terracotta[facing={d}]")
    f.set(2 + i * 2 + 8, 0, row, f"minecraft:carved_pumpkin[facing={d}]")
    f.set(2 + i * 2 + 16, 0, row, f"minecraft:furnace[facing={d}]")
row += 2
for i, d in enumerate(("up", "down", "north", "east")):
    f.set(2 + i * 2, 0, row, f"minecraft:piston[facing={d}]")
    f.set(2 + i * 2 + 8, 0, row, f"minecraft:barrel[facing={d}]")
row += 2
f.fill(2, 0, row, 12, 0, row + 2, "minecraft:grass_block")
f.fill(14, 0, row, 20, 0, row + 2, "minecraft:mycelium")
f.fill(22, 0, row, 29, 0, row + 2, "minecraft:snow_block")
f.write("scene/orientation.mcfunction")

# ── 6,0  biome tint: grass/foliage/water colour changes across merged floors ────────────
f = Fn()
biomes = ["swamp", "desert", "jungle", "snowy_taiga"]
for i, b in enumerate(biomes):
    f.raw(f"fillbiome ~{i * 8} ~-8 ~0 ~{i * 8 + 7} ~16 ~31 minecraft:{b}")
f.fill(0, 0, 0, 31, 0, 31, "minecraft:grass_block")
f.fill(0, 1, 0, 31, 2, 4, "minecraft:oak_leaves[persistent=true]")
f.fill(0, 1, 27, 31, 1, 31, "minecraft:water")
f.write("scene/biomes.mcfunction")

# ── row 1: mega roof (one huge flat plane across many sections) + random noise ──────────
f = Fn()
f.fill(0, 12, 0, 31, 12, 31, "minecraft:stone")
f.write("scene/roof.mcfunction")

f = Fn()
blocks = ["stone", "dirt", "cobblestone", "oak_planks"]
for x in range(2, 30):
    for z in range(2, 30):
        h = rng.randint(1, 3)
        f.fill(x, 0, z, x, h - 1, z, f"minecraft:{rng.choice(blocks)}")
f.write("scene/noise.mcfunction")

# ── row 2: stepped pyramids, long dark corridor, checkerboard floor ─────────────────────
f = Fn()
for i in range(8):
    f.fill(i * 2, i, i * 2, 31 - i * 2, i, 31 - i * 2, "minecraft:stone" if i % 2 else "minecraft:grass_block")
f.write("scene/pyramid.mcfunction")

f = Fn()
f.fill(0, 0, 0, 31, 8, 8, "minecraft:stone")
f.fill(1, 1, 1, 30, 4, 7, "minecraft:air")
for x in range(6, 30, 12):
    f.set(x, 1, 4, "minecraft:torch")
f.write("scene/corridor.mcfunction")

f = Fn()
for x in range(2, 30):
    for z in range(2, 30):
        if (x + z) % 2:
            f.set(x, 0, z, "minecraft:black_concrete")
        else:
            f.set(x, 0, z, "minecraft:white_concrete")
f.write("scene/checker.mcfunction")

f = Fn()
f.fill(0, 0, 0, 31, 3, 31, "minecraft:cobblestone")
f.fill(0, 4, 0, 31, 4, 31, "minecraft:mossy_cobblestone")
f.write("scene/slab_block.mcfunction")

CELLS = {
    "walls": (0, 0), "pillars": (1, 0), "water": (2, 0), "glass": (3, 0), "lighting": (4, 0),
    "orientation": (5, 0), "biomes": (6, 0),
    "roof": (0, 1), "noise": (6, 1),
    "pyramid": (0, 2), "corridor": (1, 2), "checker": (2, 2), "slab_block": (3, 2),
}
# roof spans cells 0..5 of row 1
ROOF_CELLS = [(i, 1) for i in range(0, 6)]

b = Fn()
b.raw("kill @e[type=marker,tag=greedy_origin]")
b.raw("summon marker ~ ~ ~ {Tags:[\"greedy_origin\"]}")
b.raw("time set noon")
b.raw("weather clear")
b.raw("execute at @e[type=marker,tag=greedy_origin,limit=1] run function greedy_test:build_cells")
b.write("build.mcfunction")

c = Fn()
for name, (i, j) in CELLS.items():
    if name == "roof":
        for (ri, rj) in ROOF_CELLS:
            c.raw(f"execute positioned ~{ri * CELL} ~ ~{rj * CELL} run function greedy_test:scene/roof")
    else:
        c.raw(f"execute positioned ~{i * CELL} ~ ~{j * CELL} run function greedy_test:scene/{name}")
c.write("build_cells.mcfunction")

p = Fn()
p.raw("execute at @s run forceload add ~-16 ~-16 ~240 ~112")
p.raw('tellraw @s {"text":"Greedy test: wait ~10 seconds for chunks to load, then run /function greedy_test:build","color":"yellow"}')
p.write("prepare.mcfunction")

for name, (i, j) in CELLS.items():
    g = Fn()
    g.raw(f"execute at @e[type=marker,tag=greedy_origin,limit=1] run tp @s ~{i * CELL + 1} ~1 ~{j * CELL - 3}")
    g.write(f"goto/{name}.mcfunction")

r = Fn()
for j in range(ROWS):
    for i in range(COLS):
        for y0, y1 in ((-0, 23), (24, 47)):
            r.raw(f"execute at @e[type=marker,tag=greedy_origin,limit=1] run fill ~{i * CELL} ~{y0} ~{j * CELL} ~{i * CELL + 31} ~{y1} ~{j * CELL + 31} minecraft:air")
r.raw("kill @e[type=marker,tag=greedy_origin]")
r.raw("forceload remove all")
r.write("reset.mcfunction")

meta = {"pack": {
    "description": "Greedy Meshing test scenes",
    "pack_format": 48,
    "supported_formats": {"min_inclusive": 48, "max_inclusive": 81},
    "min_format": 48,
    "max_format": [121, 0],
}}
with open(os.path.join(PACK, "pack.mcmeta"), "w") as fh:
    json.dump(meta, fh, indent=2)

print("wrote", PACK)
