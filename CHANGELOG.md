# Changelog

All notable changes to Greedy Meshing are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

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
