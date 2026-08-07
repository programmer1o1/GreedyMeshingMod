# Greedy Meshing Mod

A client-side Fabric optimization mod that merges identical adjacent block faces into larger quads, reducing the total vertex count for better GPU performance.

## Features
- Automatic merging of opaque full-cube block faces
- Custom terrain shaders for correct texture tiling on merged faces
- **Aggressive Greedy (Absolute)** option. It merges across ambient-occlusion boundaries for even fewer quads, at the cost of slightly coarser lighting
- **Greedy Water** option (experimental). Merges flat still-water surfaces (open oceans, lake interiors) into larger quads. Shorelines and flowing water are unaffected. Not supported on VulkanMod
- **Merge Oriented Blocks** option (experimental). Allows blocks with a facing/axis/rotation property (common on modded blocks) to merge once their baked model is verified to be a plain six-face cube with standard UVs, raising the merge rate on normal terrain instead of excluding those blocks outright
- [Sodium](https://modrinth.com/mod/sodium) compatible
- [VulkanMod](https://modrinth.com/mod/vulkanmod) compatible (1.21-1.21.5, 1.21.9-1.21.11, 26.1-26.1.2; 1.21.6-1.21.8 not available, VulkanMod was never released for those)
- [Distant Horizons](https://modrinth.com/mod/distanthorizons) compatible
- [Sable](https://modrinth.com/mod/sable) compatible (sub-level geometry no longer double-darkens with greedy meshing on)
- Works with Minecraft 26.2's native experimental Vulkan rendering backend
- Debug wireframe and split-screen comparison mode
- In-game config screen (requires [Cloth Config](https://modrinth.com/mod/cloth-config) + [Mod Menu](https://modrinth.com/mod/modmenu))

## Supported Versions

| Minecraft | Loader |
|-----------|--------|
| 1.21 - 1.21.11 | Fabric |
| 26.1 - 26.2 | Fabric |

## Performance
Performance gains vary depending on your hardware and world. If your GPU is already underutilized (high FPS), the improvement may be minimal. The mod benefits most on lower-end GPUs, higher render distances, and worlds with large flat surfaces of the same block type (e.g. superflat, underground caves, large builds).

## Known Limitations
- **Shader packs** (Iris/OptiFine) are not fully supported. The mod falls back to face-culling-only mode.
- **Lighting** on large merged surfaces may look slightly different from vanilla.
- **Greedy Water** is experimental (some water surfaces may render with missing or black faces on Sodium). The root cause is not yet identified. Disable Greedy Water if you see this.
- **Merge Oriented Blocks** is experimental. If a merged block renders with a stretched or misaligned texture, disable this option and report the block.

## Credits
Fork of [Greedy Meshing](https://modrinth.com/mod/greedy-meshing) by BuggiestStudios, with significant modifications including multi-version support (via [Stonecutter](https://github.com/stonecutter-versioning/stonecutter)), binary meshing.
