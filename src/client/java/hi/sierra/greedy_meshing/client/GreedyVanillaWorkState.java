package hi.sierra.greedy_meshing.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import hi.sierra.greedy_meshing.GreedyMesher;

import java.util.Arrays;
import java.util.BitSet;

public final class GreedyVanillaWorkState {
    private static final int CELLS = GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE;
    private final BlockState[] sectionStates = new BlockState[CELLS];
    private final BitSet[] visibleFaces = GreedyMesher.createFaceMaskArray();
    private final long[][] faceMergeKeys = new long[6][CELLS];
    private final int[] eligibleIndices = new int[CELLS];
    private int baseX, baseY, baseZ;
    private int eligibleCount;
    private boolean initialized;

    // Pre-allocated scratch objects for emitGreedyQuad to avoid per-call allocation
    public final float[] scratchCorners = new float[12];
    public final GreedyLighting.Scratch scratchLighting = new GreedyLighting.Scratch();
    public final BlockPos.MutableBlockPos scratchTintPos = new BlockPos.MutableBlockPos();
    public final int[][] scratchCornerBlocks = new int[4][3];
    public final float[] scratchBrightness = new float[4];
    public final int[] scratchLightmap = new int[4];
    public final float[] scratchTintR = new float[4];
    public final float[] scratchTintG = new float[4];
    public final float[] scratchTintB = new float[4];

    // Per-compile cache for face layer lookups: key = (stateHash << 32) | dirOrdinal.
    // Open-addressing array avoids Long autoboxing and HashMap overhead.
    public static final int LAYER_CACHE_SIZE = 128; // must be power of 2
    public final long[] layerCacheKeys = new long[LAYER_CACHE_SIZE];
    public final Object[] layerCacheValues = new Object[LAYER_CACHE_SIZE];

    public void reset(SectionPos sectionPos) {
        Arrays.fill(sectionStates, null);
        GreedyMesher.clearFaceMaskArray(visibleFaces);
        baseX = sectionPos.minBlockX();
        baseY = sectionPos.minBlockY();
        baseZ = sectionPos.minBlockZ();
        eligibleCount = 0;
        initialized = true;
        Arrays.fill(layerCacheKeys, 0L);
        Arrays.fill(layerCacheValues, null);
    }

    public boolean initialized() { return initialized; }
    public int baseX() { return baseX; }
    public int baseY() { return baseY; }
    public int baseZ() { return baseZ; }
    public BlockState[] sectionStates() { return sectionStates; }
    public BitSet[] visibleFaces() { return visibleFaces; }
    public int eligibleCount() { return eligibleCount; }
    public long[][] faceMergeKeys() { return faceMergeKeys; }
    public int[] eligibleIndices() { return eligibleIndices; }

    public void addEligible(int idx, BlockState state) {
        if (sectionStates[idx] == null) {
            eligibleIndices[eligibleCount] = idx;
            eligibleCount++;
        }
        sectionStates[idx] = state;
    }
}
