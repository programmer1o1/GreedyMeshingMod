package hi.sierra.greedy_meshing.client.vulkan;

//? if VULKANMOD {
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import net.minecraft.core.BlockPos;
//? if UNOBFUSCATED {
/*import net.minecraft.client.renderer.block.BlockAndTintGetter;
*///?} else {
import net.minecraft.world.level.BlockAndTintGetter;
//?}
import net.minecraft.world.level.block.state.BlockState;
import hi.sierra.greedy_meshing.GreedyConfig;
import hi.sierra.greedy_meshing.GreedyMesher;
import hi.sierra.greedy_meshing.client.GreedyLighting;
import hi.sierra.greedy_meshing.client.GreedyRuntimeState;

import java.util.Arrays;

/**
 * Per-thread scratch + capture state for the VulkanMod greedy-meshing path.
 * Mirrors {@link hi.sierra.greedy_meshing.client.sodium.GreedySodiumWorkState} but targets
 * VulkanMod's chunk-build pipeline (BuildTask / BuilderResources / TerrainBufferBuilder).
 */
public final class GreedyVulkanWorkState {
    private static final int CELLS = GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE;
    private final BlockState[] sectionStates = new BlockState[CELLS];
    private final long[][] faceMergeKeys = new long[6][CELLS];

    private BuilderResources resources;
    private BlockAndTintGetter world;
    private BlockPos sectionOrigin;
    // Decided once per build (see GreedyVanillaWorkState.greedyActive) so every hook agrees.
    private boolean greedyActive;
    private long sectionKey = Long.MIN_VALUE;
    private int eligibleCount;
    private boolean captureDebug;
    private boolean emitted;

    // Pre-allocated scratch to keep the emit hot path allocation-free (one per worker thread).
    public final float[] scratchCorners = new float[12];
    public final GreedyLighting.Scratch scratchLighting = new GreedyLighting.Scratch();
    public final BlockPos.MutableBlockPos scratchTintPos = new BlockPos.MutableBlockPos();
    public final int[][] scratchCornerBlocks = new int[4][3];
    public final float[] scratchBrightness = new float[4];
    public final int[] scratchLightmap = new int[4];
    public final float[] scratchTintR = new float[4];
    public final float[] scratchTintG = new float[4];
    public final float[] scratchTintB = new float[4];

    public void reset(BuilderResources resources) {
        this.resources = resources;
        this.world = null;
        this.sectionOrigin = null;
        this.greedyActive = GreedyRuntimeState.isRuntimeGreedyActive();
        this.sectionKey = Long.MIN_VALUE;
        this.eligibleCount = 0;
        this.emitted = false;
        this.captureDebug = GreedyConfig.debugWireframe() || GreedyConfig.debugTrianglesHud() || GreedyConfig.debugComparison();
        Arrays.fill(this.sectionStates, null);
    }

    /** True once the greedy emit has run for this compile. VulkanMod calls {@code endDrawing()} once
     *  per render type inside a loop, and our emit injects on that call site, so without this guard the
     *  emit (and its merged geometry) would be appended once per render type — duplicating it. */
    public boolean hasEmitted() {
        return emitted;
    }

    public void markEmitted() {
        this.emitted = true;
    }

    public BlockState[] sectionStates() {
        return sectionStates;
    }

    public long[][] faceMergeKeys() {
        return faceMergeKeys;
    }

    public BuilderResources resources() {
        return resources;
    }

    public BlockAndTintGetter world() {
        return world;
    }

    public void world(BlockAndTintGetter world) {
        this.world = world;
    }

    public BlockPos sectionOrigin() {
        return sectionOrigin;
    }

    public void sectionOrigin(BlockPos sectionOrigin) {
        this.sectionOrigin = sectionOrigin;
        this.greedyActive = GreedyRuntimeState.isRuntimeGreedyActive(sectionOrigin);
    }

    public boolean greedyActive() {
        return greedyActive;
    }

    public long sectionKey() {
        return sectionKey;
    }

    public void sectionKey(long sectionKey) {
        this.sectionKey = sectionKey;
    }

    public int eligibleCount() {
        return eligibleCount;
    }

    public void incrementEligibleCount() {
        this.eligibleCount++;
    }

    public boolean captureDebug() {
        return captureDebug;
    }
}
//?}
