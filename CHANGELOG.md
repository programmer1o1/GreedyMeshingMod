# Changelog

All notable changes to Greedy Meshing are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [0.5.35]

### Fixed
- **Crash on 1.21.1 with Sodium's 0.8.x beta line** (issue #13): 0.5.3's build-conflict fix stopped
  compiling the mod's Sodium options page against the new config API for 1.21.1, but still declared
  it as Sodium's `sodium:config_api_user` entrypoint, crashing on launch for anyone on that branch.
  The entrypoint declaration is now dropped for 1.21.1; Mod Menu / Cloth Config still expose the same
  settings there. Full dual-API support (matching 0.5.1's behavior before the 0.5.3 regression) is
  planned for a follow-up release.

## [0.5.3]

### Fixed
- **Distant ore flicker**: greedy terrain UV sampling now uses stable block-position gradients
  across 1.21.x and 26.2 instead of derivatives from discontinuous `fract()` coordinates.
- **Emissive ore depth fighting**: `_e` overlays now remain separated from their base faces at long
  distance, preventing camera-movement flicker.
- **1.21.1 Sodium build compatibility**: the release build now follows the primary stable Sodium
  API pin without resolving incompatible legacy and new option APIs together.

## [0.5.2]

### Fixed
- **Faithful 64x and other high-resolution resource packs**: merged terrain now reconstructs 16x,
  32x, and 64x sprite UVs correctly across vanilla, Sodium, and Vulkan shader paths.
- **OptiFine-style emissive textures**: `_e` overlays now preserve transparent pixels and use the
  correct cutout material, preventing stretched or fullbright backgrounds on glowing ores.
- **Resource-pack emissive atlas compatibility** across the 1.21.x and 26.x atlas APIs.
- **Light-emitting blocks behind translucent faces** now retain their own lightmap sampling.

## [0.5.1]

### Fixed
- **Sodium options page missing on 1.21.1's 0.8.x beta branch** (issue #9's underlying cause):
  Sodium now also ships a concurrent 0.8.x release line for 1.21.1 itself, not just newer Minecraft
  versions, so the mod's old/new Sodium-API detection was keyed on Minecraft version and wrongly
  assumed 1.21.1 could only ever have the pre-0.8 `SodiumOptionsGUI` API. Detection is now driven by
  each Minecraft version's actual pinned Sodium version(s), and 1.21.1 compiles both the legacy
  mixin and the new `sodium:config_api_user` entrypoint into the same jar, so whichever Sodium line
  is actually installed gets a working options page instead of a silent `ClassNotFoundException`.
- **Merged blocks rendering with wrong/tiled textures after a resource pack swap** (e.g. switching
  to a higher-resolution pack like Faithful 64x): eligibility and sprite-compatibility verdicts are
  cached per `BlockState`, but nothing invalidated that cache when the active resource pack changed
  — only reopening the mod's own config screen did, as an unintended side effect of applying
  settings. A resource-reload listener now clears both caches and forces a re-render on every
  resource pack change, not just when the config screen is touched.

### Added
- **Merge Oriented Blocks** is now exposed on Sodium's own in-game options page (both the legacy and
  new Sodium config APIs), matching the existing Mod Menu / Cloth Config toggle.

## [0.5.0]

### Performance
- **Chunk-build cost of the greedy sweep is now proportional to visible faces**
  rather than a flat cost per section. The binary keyed sweep previously probed
  all 4096 cells of every one of the six faces (24,576 probes per section) no
  matter how few faces were actually visible. It now walks only the set bits of
  the visibility mask, buckets them by depth slice, and skips empty slices
  entirely. Emission order is unchanged (ascending block index within a depth
  slice is exactly the old row-major order), so this is a pure speedup with no
  effect on rendered output.
- **Fully culled faces are skipped up front** via a mask emptiness check,
  avoiding per-slice setup for the six-faces-culled case that dominates interior
  and underground sections.
- **Merge-group lookup checks the previous group first.** Runs of one block state
  are the common case, which turns the linear scan over active groups into a
  single comparison for most cells.
- **Quad output list is pre-sized**, removing repeated array-copy growth from the
  default capacity of 10 on chunk-build threads.

### Added
- F3 overlay now reports vertices removed and an estimated vertex-memory saving
  for currently loaded terrain, alongside the existing cumulative quad counts.
  Tracked per-section (added on build, removed on rebuild/unload-by-distance)
  rather than accumulated for the whole session, so the figure reflects what's
  actually loaded right now rather than climbing forever as you play. Vertex
  count is exact; the byte figure assumes the vanilla 32-byte stride and is an
  upper bound (Sodium packs smaller), so it is labelled as an estimate.
- **Merge Oriented Blocks** (experimental, off by default): allows blocks
  carrying a `facing`/`axis`/`rotation` property to merge, provided their baked
  model is verified, by comparing baked UVs against exactly the mapping
  `terrain.fsh` reconstructs, to be a plain six-face cube. Previously any such
  property disqualified a block outright as a blunt proxy for "the model might
  not be six plain faces," which excludes plenty of blocks (particularly
  modded ones) that are. Raises the merge rate on normal terrain, where
  eligibility was weakest. Report any block that renders wrong once merged.

## [0.4.2]

### Fixed
- **Milkshade Dynamics compatibility** (issue #5): recognize Milkshade's
  replacement Sodium terrain shader and apply Greedy Meshing's UV-retiling
  patch to it, preventing merged block faces from rendering as flat colors.

## [0.4.1]

### Fixed
- **Top/bottom texture flip on merged quads** (issue #7): the shader-side UV
  reconstruction used for greedy-merged UP/DOWN faces had those two faces' V
  mapping swapped relative to Minecraft's actual cube-UV convention, mirroring
  the texture on every merged top/bottom face. Most visible where a merged face
  bordered an unmerged neighbor of a different block variant (e.g. a stone brick
  slab next to a stone brick block).
- **Sodium 0.8+ startup mixin crash**: `SodiumOptionsGUIMixin` compiles to a
  no-op class with no `@Mixin` annotation on Sodium 0.8+ (config now registers
  through the `sodium:config_api_user` entrypoint instead), which Mixin's loader
  treated as a hard `InvalidMixinException` regardless of `"required": false`.
  The entry is now dropped from the mixin config on those versions instead of
  just being marked optional.

## [0.4.0]

### Added
- **Sodium options integration** for Greedy Meshing settings, including support for
  running without Cloth Config / Mod Menu installed.
- **Debug options on Sodium's options page** for easier runtime diagnosis and
  comparison workflows.

### Fixed
- **Rotated full-cube model correctness** (issue #6): orientation-bearing block
  states (`facing`, `axis`, `rotation`) are excluded from greedy capture so their
  baked-model geometry stays on the normal renderer path.
- **Weird visual artifacts / average-color-style faces** (issue #5): greedy capture
  now skips blocks whose face sprites are not compatible with the greedy UV
  reconstruction path, keeping those blocks on vanilla/Sodium/Vulkan normal render
  paths.
- **Sodium 0.8+ options wiring regressions** where some boolean options lacked
  required default values.
- **Sodium 0.8+ startup/ClassNotFound crash path** by providing the expected mixin
  target class presence in newer API layouts.

### Changed
- **Greedy Water on 26.x** is now disabled in the runtime path to avoid known
  renderer-specific artifacts on that line.

## [0.3.0]

### Added
- **Greedy Water (flat still-water merging)** for supported renderer/version paths.
- **Sable compatibility** adjustments for sub-level shading behavior.
- **Dev renderer switch** (`-Prenderer=sodium|vulkanmod|vanilla`) to simplify
  runtime-path testing without editing project properties.

### Fixed
- **Modrinth listing/version metadata** so Minecraft 26.2 is featured correctly
  instead of appearing as all-version coverage.
- **Build dependency resolution** by updating the TerraformersMC repository URL to
  the current releases endpoint (needed for newer Mod Menu artifacts).

## [0.2.0]

### Added
- **Aggressive Greedy (Absolute)** option in the config screen. Merges coplanar
  faces of the same block into the largest possible quads, ignoring the
  ambient-occlusion boundaries that normally cap merge size. Fewer quads at the
  cost of slightly coarser lighting on large flat surfaces. Off by default.
- **VulkanMod support for the 26.1.x line** (26.1, 26.1.1, 26.1.2), using
  VulkanMod 0.6.8. Greedy meshing now works under the VulkanMod renderer on the
  unobfuscated Minecraft line, alongside the existing Sodium and vanilla paths.
  Runtime-verified; still considered experimental.
- **Minecraft 26.2 support**, including Minecraft's own native experimental
  Vulkan rendering backend (distinct from the VulkanMod mod, which has no 26.2
  build yet). Vanilla/OpenGL and Sodium both verified working alongside it.

### Fixed
- **Fancy-grass mod compatibility** (BetterGrassify, LambdaBetterGrass, ArdaGrass).
  Grass-spread blocks (grass block, podzol, mycelium, crimson/warped nylium) are
  now excluded from greedy meshing when such a mod is installed, so their
  neighbour-based side textures render correctly. No effect when no such mod is
  present.
- **Mod icon not displaying in Mod Menu** (showed as a "?"). Replaced the
  oversized 1224×1224 icon with a standard 128×128 PNG.
- **Terrain shader failing to compile under 26.2's native Vulkan backend.** An
  unused vertex attribute in the shipped terrain shader passed silently under
  OpenGL but failed strict Vulkan pipeline validation; removed.
- **Chunk-build crash on Sodium 0.9.x (26.2).** An internal Sodium field our
  accessor mixin reads was renamed upstream; fixed the version-specific target.

### Changed
- Fabric Loader requirement bumped to 0.18.6 on the 26.1.x versions (required by
  VulkanMod 0.6.8); 26.2 requires Fabric Loader 0.19.3.

## [0.0.5]

- Multi-version support, lighting fixes, Sodium compatibility.
