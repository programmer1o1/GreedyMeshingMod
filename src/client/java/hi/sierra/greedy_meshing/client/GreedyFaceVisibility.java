package hi.sierra.greedy_meshing.client;

import hi.sierra.greedy_meshing.GreedyConfig;
import hi.sierra.greedy_meshing.GreedyMesher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
//? if UNOBFUSCATED {
/*import net.minecraft.client.renderer.block.BlockAndTintGetter;
*///?} else {
import net.minecraft.world.level.BlockAndTintGetter;
//?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.BitSet;

/**
 * Face visibility and merge-key computation shared by the Sodium, vanilla and VulkanMod paths.
 * Every neighbour lookup goes through the per-thread {@link GreedySectionCache}.
 */
public final class GreedyFaceVisibility {
    private static final Direction[] FACES = Direction.values();

    /** For each face, the 8 cells around the sampled neighbour whose occlusion forms the AO key. */
    private static final int[][][] AO_OFFSETS = new int[6][8][3];
    static {
        for (Direction face : FACES) {
            int ax1, ay1, az1, ax2, ay2, az2;
            switch (face) {
                case DOWN, UP     -> { ax1 = 1; ay1 = 0; az1 = 0; ax2 = 0; ay2 = 0; az2 = 1; }
                case NORTH, SOUTH -> { ax1 = 1; ay1 = 0; az1 = 0; ax2 = 0; ay2 = 1; az2 = 0; }
                default           -> { ax1 = 0; ay1 = 0; az1 = 1; ax2 = 0; ay2 = 1; az2 = 0; }
            }
            int idx = 0;
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    AO_OFFSETS[face.ordinal()][idx][0] = face.getStepX() + a * ax1 + b * ax2;
                    AO_OFFSETS[face.ordinal()][idx][1] = face.getStepY() + a * ay1 + b * ay2;
                    AO_OFFSETS[face.ordinal()][idx][2] = face.getStepZ() + a * az1 + b * az2;
                    idx++;
                }
            }
        }
    }

    private GreedyFaceVisibility() {
    }

    /**
     * Fills {@code visibleFaces} and {@code faceMergeKeys} for one section.
     *
     * @param eligibleIndices block indices to visit, or {@code null} to visit every cell
     * @param eligibleCount   number of valid entries in {@code eligibleIndices}
     */
    public static void populate(
            BlockAndTintGetter world,
            GreedySectionCache cache,
            BlockState[] sectionStates,
            BitSet[] visibleFaces,
            long[][] faceMergeKeys,
            int baseX,
            int baseY,
            int baseZ,
            int[] eligibleIndices,
            int eligibleCount
    ) {
        GreedyMesher.clearFaceMaskArray(visibleFaces);
        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        int visits = eligibleIndices != null
                ? eligibleCount
                : GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE;
        boolean aggressive = GreedyConfig.aggressiveGreedy();
        for (int i = 0; i < visits; i++) {
            int idx = eligibleIndices != null ? eligibleIndices[i] : i;
            BlockState state = sectionStates[idx];
            if (state == null) {
                continue;
            }
            int x = idx & 15;
            int y = (idx >> 8) & 15;
            int z = (idx >> 4) & 15;
            int worldX = baseX + x;
            int worldY = baseY + y;
            int worldZ = baseZ + z;
            for (Direction face : FACES) {
                int nx = x + face.getStepX();
                int ny = y + face.getStepY();
                int nz = z + face.getStepZ();
                if (nx >= 0 && nx < GreedyMesher.SECTION_SIZE
                        && ny >= 0 && ny < GreedyMesher.SECTION_SIZE
                        && nz >= 0 && nz < GreedyMesher.SECTION_SIZE
                        && sectionStates[GreedyMesher.index(nx, ny, nz)] != null) {
                    continue;
                }

                samplePos.set(worldX + face.getStepX(), worldY + face.getStepY(), worldZ + face.getStepZ());
                BlockState neighbor = cache.state(samplePos.getX(), samplePos.getY(), samplePos.getZ());
                //? if >=1.21.2 {
                if (Block.shouldRenderFace(state, neighbor, face)) {
                //?} else {
                /*if (Block.shouldRenderFace(state, world, new BlockPos(worldX, worldY, worldZ), face, samplePos)) {
                *///?}
                    visibleFaces[face.ordinal()].set(idx);
                    int mergeKey;
                    if (state.is(Blocks.WATER)) {
                        // Water only merges within the interior of a flat, uncovered still-water body
                        // (see GreedyEligibility.isGreedyWaterSource + isFlatWaterSurface) — vanilla
                        // renders a sloped, per-corner-averaged surface everywhere else, which this
                        // mod's flat GreedyQuad rectangles can't represent. The bottom face is always
                        // an exact flat square regardless of the top surface's slope, so it skips the
                        // check; every other face (including the sides, whose top edge follows the
                        // same slope as the top face) needs it. Non-flat faces get a unique key so they
                        // fall back to individual 1x1 quads — pixel-identical to un-merged vanilla
                        // rendering, never a regression.
                        boolean flat = face == Direction.DOWN
                                || isFlatWaterSurface(world, worldX, worldY, worldZ, samplePos);
                        mergeKey = flat ? 0 : (0x40000000 | idx);
                    } else {
                        // Aggressive ("absolute") greedy ignores the AO signature so same-block faces
                        // merge into the largest possible quads; lighting is still sampled per sub-quad.
                        mergeKey = aggressive ? 0 : computeAoKey(cache, worldX, worldY, worldZ, face);
                    }
                    faceMergeKeys[face.ordinal()][idx] = mergeKey;
                }
            }
        }
    }

    /**
     * True iff the 3x3 horizontal neighbourhood centered on (worldX, worldY, worldZ) is entirely
     * uncovered still-water source blocks — the union of the four lattice corners vanilla's own
     * per-corner height averaging touches for this block's top face. Checking the superset rather
     * than each corner's exact 2x2 footprint is conservative: a handful of tiles that would actually
     * render flat get misclassified as non-flat (and simply don't merge), which is acceptable —
     * this check only ever removes merge opportunities, never approves an incorrect one.
     */
    private static boolean isFlatWaterSurface(
            BlockAndTintGetter world, int worldX, int worldY, int worldZ, BlockPos.MutableBlockPos scratch
    ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                scratch.set(worldX + dx, worldY, worldZ + dz);
                BlockState neighborState = world.getBlockState(scratch);
                if (!neighborState.is(Blocks.WATER)) {
                    return false;
                }
                FluidState neighborFluid = neighborState.getFluidState();
                if (!neighborFluid.isSource() || neighborFluid.getType() != Fluids.WATER) {
                    return false;
                }
                scratch.set(worldX + dx, worldY + 1, worldZ + dz);
                if (world.getBlockState(scratch).getFluidState().getType() == Fluids.WATER) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int computeAoKey(GreedySectionCache cache,
                                    int worldX, int worldY, int worldZ, Direction face) {
        int[][] offsets = AO_OFFSETS[face.ordinal()];
        int key = 0;
        for (int i = 0; i < 8; i++) {
            // occludes() is the single occlusion predicate shared with the smooth-lighting path
            // (isLightPermeable on 26.3, isViewBlocking + light dampening before).
            if (cache.occludes(worldX + offsets[i][0], worldY + offsets[i][1], worldZ + offsets[i][2])) {
                key |= (1 << i);
            }
        }
        // AO occupancy alone is insufficient: two otherwise identical faces can sit on
        // opposite sides of a block/sky-light boundary. Keep the four-bit block and sky
        // levels in disjoint key bits so a dark sample cannot be interpolated across a
        // brighter merged run. Aggressive mode deliberately bypasses this whole key.
        int packedLight = cache.light(worldX + face.getStepX(), worldY + face.getStepY(), worldZ + face.getStepZ());
        key |= ((packedLight >>> 4) & 0xF) << 8;
        key |= ((packedLight >>> 20) & 0xF) << 12;
        return key;
    }
}
