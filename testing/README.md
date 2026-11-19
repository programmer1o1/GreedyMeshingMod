# Greedy Meshing test scenes

A datapack that builds a gallery of test scenes on a flat world, each aimed at a code path in the mod
(merging, AO/light merge keys, water, translucent layers, orientation blocks, biome tint, section borders).

Not yet run in-game. If a version rejects `pack.mcmeta`, see "If it doesn't load" below.

## Setup

1. Create a **Superflat** world (Allow Commands: ON, Creative). Quit it once so the folder exists.
2. Copy the pack into the world's `datapacks` folder:

   ```bash
   cp -R testing/greedy_test_pack "<instance>/saves/<World>/datapacks/"
   ```

3. Open the world, stand on the ground where you want the gallery's corner (it extends +X and +Z, 224 x 96 blocks).
4. Run these in order:

   ```
   /function greedy_test:prepare      (force-loads the area; wait ~10 s)
   /function greedy_test:build        (builds everything around a marker at your position)
   ```

5. Jump between scenes with `/function greedy_test:goto/<name>`. Run `/function greedy_test:reset` to clear everything
   (including the force-load) and rebuild elsewhere. Biome changes from `fillbiome` stay in the world.

## Scenes

| Name | Cell | What it exercises |
|---|---|---|
| `walls` | 0,0 | huge flat vertical merges, a checker wall and striped wall that must not merge |
| `pillars` | 1,0 | many different AO merge keys on one floor |
| `water` | 2,0 | flat pool (merges), flowing sheet (must stay vanilla), waterlogged blocks |
| `glass` | 3,0 | glass, tinted glass, stained glass, sea lanterns, leaves, ice |
| `lighting` | 4,0 | closed room with a glowstone in one corner: light gradient across a merged floor |
| `orientation` | 5,0 | stairs, slabs, logs, glazed terracotta, pistons, barrels: must render exactly like vanilla |
| `biomes` | 6,0 | grass, leaves and water tint across biome borders |
| `roof` | row 1 | one 192 x 32 stone plane across many sections (shadow on the ground beneath) |
| `noise` | 6,1 | random heights and blocks: worst case, almost nothing merges |
| `pyramid` | 0,2 | stepped terraces |
| `corridor` | 1,2 | long dark corridor with sparse torches |
| `checker` | 2,2 | black/white concrete checkerboard |
| `slab_block` | 3,2 | thick solid slab with a mossy top |

## Comparing builds

Fly the same route in the same world with the old and new jar, with F3+L chunk profiling on.
After `build`, rejoin the world or move far away and back so every section recompiles.
Compare chunk build times, and check the scenes above look identical to the old build and to vanilla
(toggle the mod off in its config to see the vanilla reference).

## If it doesn't load

- `/datapack list` shows whether `file/greedy_test_pack` is available, enabled or flagged incompatible.
- Force it with `/datapack enable "file/greedy_test_pack"`, then `/reload`.
- The pack declares formats 48 (1.21.1) through 121 (26.3). If a version complains, edit `pack.mcmeta`
  (1.21.9+ uses `min_format`/`max_format`; 1.21.1-1.21.8 use `pack_format`), or rerun
  `python3 testing/generate_test_pack.py` after changing the numbers in it.
- Functions only use `fill`, `setblock`, `fillbiome`, `forceload`, `summon`, `kill`, `tp`, `time`, `weather`.
  There are no gamerules, which were renamed in recent versions.
