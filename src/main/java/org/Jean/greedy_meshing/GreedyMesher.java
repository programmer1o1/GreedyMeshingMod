package org.Jean.greedy_meshing;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

public final class GreedyMesher {
    public static final int SECTION_SIZE = 16;
    private static final int SECTION_MASK = SECTION_SIZE - 1;
    private static final int CELL_COUNT = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;
    private static final int PLANE_CELL_COUNT = SECTION_SIZE * SECTION_SIZE;
    private static final Direction[] FACES = Direction.values();
    private static final int[][][] FACE_PLANE_INDICES = createFacePlaneIndices();
    private static final ThreadLocal<MeshWork> WORK = ThreadLocal.withInitial(MeshWork::new);

    private GreedyMesher() {
    }

    public static List<GreedyQuad> mesh(BlockState[] blocks, FaceVisibility visibility) {
        return mesh(blocks, visibility, (sx, sy, sz, cx, cy, cz, face, state) -> true);
    }

    public static List<GreedyQuad> mesh(BlockState[] blocks, FaceVisibility visibility, MergeKeyProvider mergeKeyProvider) {
        return mesh(
                blocks,
                visibility,
                (sx, sy, sz, cx, cy, cz, face, state) ->
                        mergeKeyProvider.mergeKey(cx, cy, cz, face, state) == mergeKeyProvider.mergeKey(sx, sy, sz, face, state)
        );
    }

    public static List<GreedyQuad> mesh(BlockState[] blocks, FaceVisibility visibility, MergePredicate mergePredicate) {
        List<GreedyQuad> quads = new ArrayList<>();
        MeshWork work = WORK.get();
        int[] visited = work.visited;
        int stamp = work.nextStamp();

        for (Direction face : FACES) {
            int faceOrdinal = face.ordinal();
            int faceOffset = faceOrdinal * CELL_COUNT;
            int[][] facePlanes = FACE_PLANE_INDICES[faceOrdinal];
            for (int plane = 0; plane < SECTION_SIZE; plane++) {
                int[] planeIndices = facePlanes[plane];
                for (int v = 0; v < SECTION_SIZE; v++) {
                    int rowBase = v << 4;
                    for (int u = 0; u < SECTION_SIZE; u++) {
                        int uv = rowBase + u;
                        int idx = planeIndices[uv];
                        int visitedIdx = faceOffset + idx;
                        if (visited[visitedIdx] == stamp) {
                            continue;
                        }

                        BlockState state = blocks[idx];
                        int x = xFromIndex(idx);
                        int y = yFromIndex(idx);
                        int z = zFromIndex(idx);
                        if (state == null || !visibility.isVisibleFace(x, y, z, face, state)) {
                            visited[visitedIdx] = stamp;
                            continue;
                        }

                        int width = 1;
                        while (u + width < SECTION_SIZE) {
                            int nUv = rowBase + u + width;
                            int nIdx = planeIndices[nUv];
                            int nVisitedIdx = faceOffset + nIdx;
                            BlockState nState = blocks[nIdx];
                            if (visited[nVisitedIdx] == stamp || nState != state) {
                                break;
                            }

                            int nx = xFromIndex(nIdx);
                            int ny = yFromIndex(nIdx);
                            int nz = zFromIndex(nIdx);
                            if (!visibility.isVisibleFace(nx, ny, nz, face, nState)
                                    || !mergePredicate.canMerge(x, y, z, nx, ny, nz, face, nState)) {
                                break;
                            }
                            width++;
                        }

                        int height = 1;
                        while (v + height < SECTION_SIZE) {
                            boolean rowMatches = true;
                            int nextRowBase = (v + height) << 4;
                            for (int du = 0; du < width; du++) {
                                int nUv = nextRowBase + u + du;
                                int nIdx = planeIndices[nUv];
                                int nVisitedIdx = faceOffset + nIdx;
                                BlockState nState = blocks[nIdx];
                                if (visited[nVisitedIdx] == stamp || nState != state) {
                                    rowMatches = false;
                                    break;
                                }

                                int nx = xFromIndex(nIdx);
                                int ny = yFromIndex(nIdx);
                                int nz = zFromIndex(nIdx);
                                if (!visibility.isVisibleFace(nx, ny, nz, face, nState)
                                        || !mergePredicate.canMerge(x, y, z, nx, ny, nz, face, nState)) {
                                    rowMatches = false;
                                    break;
                                }
                            }
                            if (!rowMatches) {
                                break;
                            }
                            height++;
                        }

                        for (int dv = 0; dv < height; dv++) {
                            int markRowBase = (v + dv) << 4;
                            for (int du = 0; du < width; du++) {
                                int markIdx = planeIndices[markRowBase + u + du];
                                visited[faceOffset + markIdx] = stamp;
                            }
                        }

                        quads.add(new GreedyQuad(face, x, y, z, width, height, state));
                        u += width - 1;
                    }
                }
            }
        }

        return quads;
    }

    public static List<GreedyQuad> mesh(BlockState[] blocks, BitSet[] visibleFaces) {
        return mesh(blocks, visibleFaces, null, false);
    }

    public static List<GreedyQuad> mesh(BlockState[] blocks, BitSet[] visibleFaces, MergePredicate mergePredicate) {
        return mesh(blocks, visibleFaces, mergePredicate, true);
    }

    private static List<GreedyQuad> mesh(
            BlockState[] blocks,
            BitSet[] visibleFaces,
            MergePredicate mergePredicate,
            boolean checkMergePredicate
    ) {
        List<GreedyQuad> quads = new ArrayList<>();
        MeshWork work = WORK.get();
        int[] visited = work.visited;
        int stamp = work.nextStamp();

        for (Direction face : FACES) {
            int faceOrdinal = face.ordinal();
            int faceOffset = faceOrdinal * CELL_COUNT;
            BitSet faceVisible = visibleFaces[faceOrdinal];
            int[][] facePlanes = FACE_PLANE_INDICES[faceOrdinal];
            for (int plane = 0; plane < SECTION_SIZE; plane++) {
                int[] planeIndices = facePlanes[plane];
                for (int v = 0; v < SECTION_SIZE; v++) {
                    int rowBase = v << 4;
                    for (int u = 0; u < SECTION_SIZE; u++) {
                        int uv = rowBase + u;
                        int idx = planeIndices[uv];
                        int visitedIdx = faceOffset + idx;
                        if (visited[visitedIdx] == stamp) {
                            continue;
                        }

                        BlockState state = blocks[idx];
                        if (state == null || !faceVisible.get(idx)) {
                            visited[visitedIdx] = stamp;
                            continue;
                        }

                        int startX = xFromIndex(idx);
                        int startY = yFromIndex(idx);
                        int startZ = zFromIndex(idx);

                        int width = 1;
                        while (u + width < SECTION_SIZE) {
                            int nUv = rowBase + u + width;
                            int nIdx = planeIndices[nUv];
                            int nVisitedIdx = faceOffset + nIdx;
                            BlockState nState = blocks[nIdx];
                            if (visited[nVisitedIdx] == stamp || nState != state || !faceVisible.get(nIdx)) {
                                break;
                            }
                            if (checkMergePredicate) {
                                int nx = xFromIndex(nIdx);
                                int ny = yFromIndex(nIdx);
                                int nz = zFromIndex(nIdx);
                                if (!mergePredicate.canMerge(startX, startY, startZ, nx, ny, nz, face, nState)) {
                                    break;
                                }
                            }
                            width++;
                        }

                        int height = 1;
                        while (v + height < SECTION_SIZE) {
                            boolean rowMatches = true;
                            int nextRowBase = (v + height) << 4;
                            for (int du = 0; du < width; du++) {
                                int nUv = nextRowBase + u + du;
                                int nIdx = planeIndices[nUv];
                                int nVisitedIdx = faceOffset + nIdx;
                                BlockState nState = blocks[nIdx];
                                if (visited[nVisitedIdx] == stamp || nState != state || !faceVisible.get(nIdx)) {
                                    rowMatches = false;
                                    break;
                                }
                                if (checkMergePredicate) {
                                    int nx = xFromIndex(nIdx);
                                    int ny = yFromIndex(nIdx);
                                    int nz = zFromIndex(nIdx);
                                    if (!mergePredicate.canMerge(startX, startY, startZ, nx, ny, nz, face, nState)) {
                                        rowMatches = false;
                                        break;
                                    }
                                }
                            }
                            if (!rowMatches) {
                                break;
                            }
                            height++;
                        }

                        for (int dv = 0; dv < height; dv++) {
                            int markRowBase = (v + dv) << 4;
                            for (int du = 0; du < width; du++) {
                                int markIdx = planeIndices[markRowBase + u + du];
                                visited[faceOffset + markIdx] = stamp;
                            }
                        }

                        quads.add(new GreedyQuad(face, startX, startY, startZ, width, height, state));
                        u += width - 1;
                    }
                }
            }
        }

        return quads;
    }

    public static BitSet[] createFaceMaskArray() {
        BitSet[] masks = new BitSet[FACES.length];
        for (int i = 0; i < masks.length; i++) {
            masks[i] = new BitSet(CELL_COUNT);
        }
        return masks;
    }

    public static void clearFaceMaskArray(BitSet[] masks) {
        for (BitSet mask : masks) {
            mask.clear();
        }
    }

    private static int[][][] createFacePlaneIndices() {
        int[][][] indices = new int[FACES.length][SECTION_SIZE][PLANE_CELL_COUNT];
        for (Direction face : FACES) {
            int[][] facePlanes = indices[face.ordinal()];
            for (int plane = 0; plane < SECTION_SIZE; plane++) {
                int[] planeIndices = facePlanes[plane];
                for (int v = 0; v < SECTION_SIZE; v++) {
                    int rowBase = v << 4;
                    for (int u = 0; u < SECTION_SIZE; u++) {
                        int x = mapX(face, plane, u, v);
                        int y = mapY(face, plane, u, v);
                        int z = mapZ(face, plane, u, v);
                        planeIndices[rowBase + u] = index(x, y, z);
                    }
                }
            }
        }
        return indices;
    }

    private static int mapX(Direction face, int plane, int u, int v) {
        return switch (face) {
            case NORTH, SOUTH -> u;
            case WEST, EAST -> plane;
            case UP, DOWN -> u;
        };
    }

    private static int mapY(Direction face, int plane, int u, int v) {
        return switch (face) {
            case NORTH, SOUTH -> v;
            case WEST, EAST -> v;
            case UP, DOWN -> plane;
        };
    }

    private static int mapZ(Direction face, int plane, int u, int v) {
        return switch (face) {
            case NORTH, SOUTH -> plane;
            case WEST, EAST -> u;
            case UP, DOWN -> v;
        };
    }

    private static int xFromIndex(int index) {
        return index & SECTION_MASK;
    }

    private static int yFromIndex(int index) {
        return (index >> 8) & SECTION_MASK;
    }

    private static int zFromIndex(int index) {
        return (index >> 4) & SECTION_MASK;
    }

    public static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

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
        boolean canMerge(int startX, int startY, int startZ, int currentX, int currentY, int currentZ, Direction face, BlockState state);
    }

    public record GreedyQuad(Direction face, int x, int y, int z, int width, int height, BlockState state) {
    }

    private static final class MeshWork {
        private final int[] visited = new int[FACES.length * CELL_COUNT];
        private int stamp;

        private int nextStamp() {
            int next = stamp + 1;
            if (next <= 0) {
                Arrays.fill(visited, 0);
                next = 1;
            }
            stamp = next;
            return next;
        }
    }
}
