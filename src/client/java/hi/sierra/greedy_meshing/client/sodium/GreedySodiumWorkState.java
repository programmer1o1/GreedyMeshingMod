package hi.sierra.greedy_meshing.client.sodium;

//? if SODIUM {
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
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

import java.util.Arrays;

public final class GreedySodiumWorkState {
    private static final int CELLS = GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE;
    private final BlockState[] sectionStates = new BlockState[CELLS];
    private final long[][] faceMergeKeys = new long[6][CELLS];
    private ChunkBuildContext buildContext;
    private BlockAndTintGetter world;
    private BlockRenderer blockRenderer;
    private BlockPos sectionOrigin;
    private long sectionKey = Long.MIN_VALUE;
    private int eligibleCount;
    private boolean captureDebug;

    // Pre-allocated scratch objects for emitMergedQuad / emitTiledQuads to avoid per-call allocation
    public final float[] scratchCorners = new float[12];
    public final GreedyLighting.Scratch scratchLighting = new GreedyLighting.Scratch();
    public final BlockPos.MutableBlockPos scratchTintPos = new BlockPos.MutableBlockPos();
    public final int[][] scratchCornerBlocks = new int[4][3];
    public final float[] scratchBrightness = new float[4];
    public final int[] scratchLightmap = new int[4];
    public final float[] scratchTintR = new float[4];
    public final float[] scratchTintG = new float[4];
    public final float[] scratchTintB = new float[4];

    public void reset(ChunkBuildContext context) {
        this.buildContext = context;
        this.world = null;
        this.blockRenderer = null;
        this.sectionOrigin = null;
        this.sectionKey = Long.MIN_VALUE;
        this.eligibleCount = 0;
        this.captureDebug = GreedyConfig.debugWireframe() || GreedyConfig.debugTrianglesHud() || GreedyConfig.debugComparison();
        Arrays.fill(this.sectionStates, null);
    }

    public BlockState[] sectionStates() {
        return sectionStates;
    }

    public ChunkBuildContext buildContext() {
        return buildContext;
    }

    public BlockAndTintGetter world() {
        return world;
    }

    public void world(BlockAndTintGetter world) {
        this.world = world;
    }

    public BlockRenderer blockRenderer() {
        return blockRenderer;
    }

    public void blockRenderer(BlockRenderer blockRenderer) {
        this.blockRenderer = blockRenderer;
    }

    public BlockPos sectionOrigin() {
        return sectionOrigin;
    }

    public void sectionOrigin(BlockPos sectionOrigin) {
        this.sectionOrigin = sectionOrigin;
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

    public long[][] faceMergeKeys() {
        return faceMergeKeys;
    }
}
//?}
