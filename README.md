# Greedy Meshing

A client-side Fabric mod that optimizes chunk rendering by merging adjacent identical block faces into larger quads using greedy meshing. This reduces the number of quads the GPU needs to render, improving frame rates in areas with large flat surfaces of the same block.

## How It Works

Instead of rendering each block face as a separate quad, Greedy Meshing scans each chunk section and merges eligible faces into larger combined quads. Only opaque, full-cube blocks with no block entities are eligible. Custom GLSL shaders reconstruct per-block UVs at render time so textures tile correctly across merged faces.

## Features

- Automatic merging of identical opaque block faces
- Custom terrain shaders for correct texture tiling
- **Aggressive Greedy (Absolute)** option. It merges across ambient-occlusion
  boundaries for even fewer quads, at the cost of slightly coarser lighting
- Sodium compatible
- VulkanMod compatible (1.21-1.21.5, 1.21.9-1.21.11, 26.1-26.1.2. 1.21.6-1.21.8 not available, VulkanMod was never released for those)
- Works with Minecraft 26.2's native experimental Vulkan rendering backend
- F3 debug overlay showing quad reduction stats
- Debug wireframe overlay (configurable via Mod Menu)
- Split-screen comparison mode (greedy vs vanilla)

> **Note:** This mod is experimental. Some mods that modify chunk rendering or terrain shaders may be incompatible. If you experience visual glitches or crashes, try disabling Greedy Meshing in the config (`config/greedy_meshing.json` or via Mod Menu) to confirm it's the cause.
>
> **Shader packs** (Iris/OptiFine) are not fully supported. When a shader pack is active, the mod falls back to face-culling-only mode (no quad merging) since shader packs replace the terrain shaders that greedy meshing relies on.
>
> **Lighting** on large merged faces (especially on superflat) may look slightly different from vanilla. Merged quads have fewer vertices for the GPU to interpolate light between, so torch light and shadows can appear less smooth across large flat surfaces. This is an inherent tradeoff of greedy meshing.

## Supported Versions

| Minecraft | Loader | Status |
|-----------|--------|--------|
| 1.21 - 1.21.11 | Fabric | Supported |
| 26.1 - 26.2 | Fabric | Supported |
| Any | NeoForge (via Sinytra Connector) | Not officially supported |

Built with [Stonecutter](https://github.com/stonecutter-versioning/stonecutter) for multi-version support.

> **NeoForge via Sinytra Connector:** this is a Fabric-only mod. Running it under Connector may work, but Connector's Fabric-on-NeoForge translation is known to be inconsistent with mixins that target another mod's internals (such as this mod's Sodium integration), so behavior there isn't guaranteed. Sodium's own resource-pack scanner warning about replaced terrain shaders is expected and harmless on any loader: the actual Sodium UV fix is applied via a mixin into Sodium's in-memory shader source, not through the replaced vanilla shader files.

## Dependencies

- **Required:** [Fabric API](https://modrinth.com/mod/fabric-api)
- **Optional:** [Cloth Config](https://modrinth.com/mod/cloth-config) + [Mod Menu](https://modrinth.com/mod/modmenu) (for in-game config screen), [Sodium](https://modrinth.com/mod/sodium) (compatible), [VulkanMod](https://modrinth.com/mod/vulkanmod) (compatible, see Features above for exact version coverage)

## Installation

1. Install [Fabric Loader](https://fabricmc.net/)
2. Download the jar for your Minecraft version from [Modrinth](https://modrinth.com/mod/greedy-meshing) or [GitHub Releases](https://github.com/programmer1o1/GreedyMeshingMod/releases)
3. Place the jar in your `mods/` folder along with Fabric API and Cloth Config

## Configuration

Open the config screen via Mod Menu, or edit `config/greedy_meshing.json`:

- **Enabled** - Toggle greedy meshing on/off
- **Aggressive Greedy (Absolute)** - Merge across ambient-occlusion boundaries for the largest possible quads (off by default)
- **Debug Wireframe** - Show merged quad outlines
- **Debug Comparison** - Split-screen greedy vs vanilla view
- **Mesh Opacity** - Wireframe overlay opacity

## Building from Source

```bash
git clone https://github.com/programmer1o1/GreedyMeshingMod.git
cd GreedyMeshingMod
./gradlew build
```

Jars are output to `versions/<version>/build/libs/`.

## License

[MIT](LICENSE)
