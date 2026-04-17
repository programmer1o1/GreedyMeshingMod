package hi.sierra.greedy_meshing;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Greedy meshing for 16x16x16 chunk sections.
 * Merges coplanar block faces of the same type into larger quads.
 */
public final class GreedyMesher {
    public static final int SECTION_SIZE = 16;
    private static final int TOTAL_BLOCKS = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;
    private static final Direction[] ALL_FACES = Direction.values();

    /**
     * Pre-computed lookup: for each face direction and each plane depth,
     * maps (row, col) in the 2D sweep to a 3D block index.
     * Layout: [face][depth][row * 16 + col] -> block index
     */
    private static final int[][][] SWEEP_TO_BLOCK = buildSweepTable();

    private static final ThreadLocal<SweepState> SWEEP_TL = ThreadLocal.withInitial(SweepState::new);
    private static final ThreadLocal<BinarySweepState> BIN_SWEEP_TL = ThreadLocal.withInitial(BinarySweepState::new);

    private GreedyMesher() {}

    // ── Public entry points ──────────────────────────────────────────

    public static List<GreedyQuad> mesh(BlockState[] blocks, FaceVisibility vis) {
        return sweep(blocks, vis, (ox, oy, oz, cx, cy, cz, dir, st) -> true);
    }

    public static List<GreedyQuad> mesh(BlockState[] blocks, FaceVisibility vis, MergeKeyProvider keys) {
        return sweep(blocks, vis,
                (ox, oy, oz, cx, cy, cz, dir, st) ->
                        keys.mergeKey(cx, cy, cz, dir, st) == keys.mergeKey(ox, oy, oz, dir, st));
    }

    public static List<GreedyQuad> mesh(BlockState[] blocks, FaceVisibility vis, MergePredicate pred) {
        return sweep(blocks, vis, pred);
    }

    public static List<GreedyQuad> mesh(BlockState[] blocks, BitSet[] faceMasks) {
        return binarySweep(blocks, faceMasks, null);
    }

    public static List<GreedyQuad> mesh(BlockState[] blocks, BitSet[] faceMasks, MergePredicate pred) {
        return binarySweep(blocks, faceMasks, pred);
    }

    public static List<GreedyQuad> meshWithKeys(BlockState[] blocks, BitSet[] faceMasks, long[][] mergeKeys) {
        return binaryKeyedSweep(blocks, faceMasks, mergeKeys);
    }

    // ── Scalar sweep (callback-based visibility) ─────────────────────

    private static List<GreedyQuad> sweep(
            BlockState[] blocks, FaceVisibility vis, MergePredicate pred) {

        List<GreedyQuad> out = new ArrayList<>();
        SweepState st = SWEEP_TL.get();
        boolean[] done = st.acquire();

        for (Direction dir : ALL_FACES) {
            int fi = dir.ordinal();
            int[][] table = SWEEP_TO_BLOCK[fi];

            for (int depth = 0; depth < SECTION_SIZE; depth++) {
                int[] lookup = table[depth];

                for (int row = 0; row < SECTION_SIZE; row++) {
                    for (int col = 0; col < SECTION_SIZE; ) {
                        int bi = lookup[row * SECTION_SIZE + col];
                        int slot = fi * TOTAL_BLOCKS + bi;

                        if (done[slot]) { col++; continue; }

                        BlockState block = blocks[bi];
                        int bx = bi & 0xF, by = (bi >> 8) & 0xF, bz = (bi >> 4) & 0xF;

                        if (block == null || !vis.isVisibleFace(bx, by, bz, dir, block)) {
                            done[slot] = true;
                            col++;
                            continue;
                        }

                        // Expand width (along col axis)
                        int w = 1;
                        while (col + w < SECTION_SIZE) {
                            int ni = lookup[row * SECTION_SIZE + col + w];
                            int ns = fi * TOTAL_BLOCKS + ni;
                            if (done[ns]) break;
                            BlockState nb = blocks[ni];
                            if (nb != block) break;
                            int nx = ni & 0xF, ny = (ni >> 8) & 0xF, nz = (ni >> 4) & 0xF;
                            if (!vis.isVisibleFace(nx, ny, nz, dir, nb)
                                    || !pred.canMerge(bx, by, bz, nx, ny, nz, dir, nb)) break;
                            w++;
                        }

                        // Expand height (along row axis)
                        int h = 1;
                        outer:
                        while (row + h < SECTION_SIZE) {
                            for (int dc = 0; dc < w; dc++) {
                                int ni = lookup[(row + h) * SECTION_SIZE + col + dc];
                                int ns = fi * TOTAL_BLOCKS + ni;
                                if (done[ns]) break outer;
                                BlockState nb = blocks[ni];
                                if (nb != block) break outer;
                                int nx = ni & 0xF, ny = (ni >> 8) & 0xF, nz = (ni >> 4) & 0xF;
                                if (!vis.isVisibleFace(nx, ny, nz, dir, nb)
                                        || !pred.canMerge(bx, by, bz, nx, ny, nz, dir, nb)) break outer;
                            }
                            h++;
                        }

                        // Mark merged cells
                        for (int dr = 0; dr < h; dr++) {
                            for (int dc = 0; dc < w; dc++) {
                                done[fi * TOTAL_BLOCKS + lookup[(row + dr) * SECTION_SIZE + col + dc]] = true;
                            }
                        }

                        out.add(new GreedyQuad(dir, bx, by, bz, w, h, block));
                        col += w;
                    }
                }
            }
        }

        return out;
    }

    // ── Binary sweep (BitSet visibility, optional predicate) ─────────

    private static List<GreedyQuad> binarySweep(
            BlockState[] blocks, BitSet[] faceMasks, MergePredicate pred) {

        List<GreedyQuad> out = new ArrayList<>();
        BinarySweepState bw = BIN_SWEEP_TL.get();
        int[] rows = bw.rows;
        long[] keys = bw.keys;
        int[] origins = bw.origins;

        for (Direction dir : ALL_FACES) {
            int fi = dir.ordinal();
            BitSet visible = faceMasks[fi];
            int[][] table = SWEEP_TO_BLOCK[fi];

            for (int depth = 0; depth < SECTION_SIZE; depth++) {
                int[] lookup = table[depth];
                int nGroups = 0;

                // Bucket visible cells by BlockState identity
                for (int row = 0; row < SECTION_SIZE; row++) {
                    for (int col = 0; col < SECTION_SIZE; col++) {
                        int bi = lookup[row * SECTION_SIZE + col];
                        BlockState block = blocks[bi];
                        if (block == null || !visible.get(bi)) continue;

                        long key = System.identityHashCode(block);
                        int gid = -1;
                        for (int g = 0; g < nGroups; g++) {
                            if (keys[g] == key) { gid = g; break; }
                        }
                        if (gid == -1) {
                            if (nGroups >= BinarySweepState.CAP) break;
                            gid = nGroups++;
                            keys[gid] = key;
                            origins[gid] = bi;
                            for (int r = 0; r < SECTION_SIZE; r++) rows[gid * SECTION_SIZE + r] = 0;
                        }
                        rows[gid * SECTION_SIZE + row] |= 1 << col;
                    }
                }

                // Greedy merge each group
                for (int g = 0; g < nGroups; g++) {
                    int base = g * SECTION_SIZE;
                    BlockState block = blocks[origins[g]];

                    for (int row = 0; row < SECTION_SIZE; row++) {
                        int bits = rows[base + row];
                        while (bits != 0) {
                            int col = Integer.numberOfTrailingZeros(bits);
                            int w = Integer.numberOfTrailingZeros(~(bits >>> col));

                            if (pred != null) {
                                int si = lookup[row * SECTION_SIZE + col];
                                int sx = si & 0xF, sy = (si >> 8) & 0xF, sz = (si >> 4) & 0xF;
                                int rw = 1;
                                for (int dc = 1; dc < w; dc++) {
                                    int ni = lookup[row * SECTION_SIZE + col + dc];
                                    int nx = ni & 0xF, ny = (ni >> 8) & 0xF, nz = (ni >> 4) & 0xF;
                                    if (!pred.canMerge(sx, sy, sz, nx, ny, nz, dir, block)) break;
                                    rw++;
                                }
                                w = rw;
                            }

                            int span = ((1 << w) - 1) << col;
                            int h = 1;
                            while (row + h < SECTION_SIZE) {
                                if ((rows[base + row + h] & span) != span) break;
                                if (pred != null) {
                                    int si = lookup[row * SECTION_SIZE + col];
                                    int sx = si & 0xF, sy = (si >> 8) & 0xF, sz = (si >> 4) & 0xF;
                                    boolean ok = true;
                                    for (int dc = 0; dc < w; dc++) {
                                        int ni = lookup[(row + h) * SECTION_SIZE + col + dc];
                                        int nx = ni & 0xF, ny = (ni >> 8) & 0xF, nz = (ni >> 4) & 0xF;
                                        if (!pred.canMerge(sx, sy, sz, nx, ny, nz, dir, block)) { ok = false; break; }
                                    }
                                    if (!ok) break;
                                }
                                h++;
                            }

                            for (int dr = 0; dr < h; dr++) rows[base + row + dr] &= ~span;

                            int qi = lookup[row * SECTION_SIZE + col];
                            out.add(new GreedyQuad(dir,
                                    qi & 0xF, (qi >> 8) & 0xF, (qi >> 4) & 0xF,
                                    w, h, block));

                            bits = rows[base + row];
                        }
                    }
                }
            }
        }
        return out;
    }

    // ── Binary keyed sweep (pre-computed merge keys, pure bitwise) ───

    private static List<GreedyQuad> binaryKeyedSweep(
            BlockState[] blocks, BitSet[] faceMasks, long[][] mergeKeys) {

        List<GreedyQuad> out = new ArrayList<>();
        BinarySweepState bw = BIN_SWEEP_TL.get();
        int[] rows = bw.rows;
        long[] keys = bw.keys;
        int[] origins = bw.origins;

        for (Direction dir : ALL_FACES) {
            int fi = dir.ordinal();
            BitSet visible = faceMasks[fi];
            long[] faceKeys = mergeKeys[fi];
            int[][] table = SWEEP_TO_BLOCK[fi];

            for (int depth = 0; depth < SECTION_SIZE; depth++) {
                int[] lookup = table[depth];
                int nGroups = 0;

                for (int row = 0; row < SECTION_SIZE; row++) {
                    for (int col = 0; col < SECTION_SIZE; col++) {
                        int bi = lookup[row * SECTION_SIZE + col];
                        BlockState block = blocks[bi];
                        if (block == null || !visible.get(bi)) continue;

                        long ck = ((long) System.identityHashCode(block) << 32) | (faceKeys[bi] & 0xFFFFFFFFL);
                        int gid = -1;
                        for (int g = 0; g < nGroups; g++) {
                            if (keys[g] == ck) { gid = g; break; }
                        }
                        if (gid == -1) {
                            if (nGroups >= BinarySweepState.CAP) break;
                            gid = nGroups++;
                            keys[gid] = ck;
                            origins[gid] = bi;
                            for (int r = 0; r < SECTION_SIZE; r++) rows[gid * SECTION_SIZE + r] = 0;
                        }
                        rows[gid * SECTION_SIZE + row] |= 1 << col;
                    }
                }

                for (int g = 0; g < nGroups; g++) {
                    int base = g * SECTION_SIZE;
                    BlockState block = blocks[origins[g]];

                    for (int row = 0; row < SECTION_SIZE; row++) {
                        int bits = rows[base + row];
                        while (bits != 0) {
                            int col = Integer.numberOfTrailingZeros(bits);
                            int w = Integer.numberOfTrailingZeros(~(bits >>> col));
                            int span = ((1 << w) - 1) << col;

                            int h = 1;
                            while (row + h < SECTION_SIZE
                                    && (rows[base + row + h] & span) == span) h++;

                            for (int dr = 0; dr < h; dr++) rows[base + row + dr] &= ~span;

                            int qi = lookup[row * SECTION_SIZE + col];
                            out.add(new GreedyQuad(dir,
                                    qi & 0xF, (qi >> 8) & 0xF, (qi >> 4) & 0xF,
                                    w, h, block));

                            bits = rows[base + row];
                        }
                    }
                }
            }
        }
        return out;
    }

    // ── Utilities ────────────────────────────────────────────────────

    public static BitSet[] createFaceMaskArray() {
        BitSet[] masks = new BitSet[ALL_FACES.length];
        for (int i = 0; i < masks.length; i++) masks[i] = new BitSet(TOTAL_BLOCKS);
        return masks;
    }

    public static void clearFaceMaskArray(BitSet[] masks) {
        for (BitSet m : masks) m.clear();
    }

    public static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    // ── Sweep-to-block lookup table ─────────────────────────────────

    private static int[][][] buildSweepTable() {
        int[][][] t = new int[ALL_FACES.length][SECTION_SIZE][SECTION_SIZE * SECTION_SIZE];
        for (Direction dir : ALL_FACES) {
            for (int depth = 0; depth < SECTION_SIZE; depth++) {
                for (int row = 0; row < SECTION_SIZE; row++) {
                    for (int col = 0; col < SECTION_SIZE; col++) {
                        int x, y, z;
                        switch (dir) {
                            case DOWN, UP     -> { x = col; y = depth; z = row; }
                            case NORTH, SOUTH -> { x = col; y = row;   z = depth; }
                            default           -> { x = depth; y = row; z = col; }
                        }
                        t[dir.ordinal()][depth][row * SECTION_SIZE + col] = index(x, y, z);
                    }
                }
            }
        }
        return t;
    }

    // ── Types ────────────────────────────────────────────────────────

    @FunctionalInterface
    public interface FaceVisibility {
        boolean isVisibleFace(int x, int y, int z, Direction face, BlockState state);
    }

    @FunctionalInterface
    public interface MergeKeyProvider {
        int mergeKey(int x, int y, int z, Direction face, BlockState state);
    }

    @FunctionalInterface
    public interface MergePredicate {
        boolean canMerge(int startX, int startY, int startZ,
                         int currentX, int currentY, int currentZ,
                         Direction face, BlockState state);
    }

    public record GreedyQuad(Direction face, int x, int y, int z,
                             int width, int height, BlockState state) {}

    // ── Thread-local work buffers ───────────────────────────────────

    private static final class SweepState {
        private final boolean[] used = new boolean[ALL_FACES.length * TOTAL_BLOCKS];

        boolean[] acquire() {
            java.util.Arrays.fill(used, false);
            return used;
        }
    }

    private static final class BinarySweepState {
        static final int CAP = 256;
        final int[] rows = new int[CAP * SECTION_SIZE];
        final long[] keys = new long[CAP];
        final int[] origins = new int[CAP];
    }
}
