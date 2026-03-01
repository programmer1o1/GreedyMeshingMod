package org.Jean.greedy_meshing;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public final class GreedyMesher {
    public static final int SECTION_SIZE = 16;
    private static final int CELL_COUNT = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;

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
        BitSet[] visited = new BitSet[Direction.values().length];
        for (int i = 0; i < visited.length; i++) {
            visited[i] = new BitSet(CELL_COUNT);
        }

        for (Direction face : Direction.values()) {
            BitSet faceVisited = visited[face.ordinal()];
            for (int plane = 0; plane < SECTION_SIZE; plane++) {
                for (int v = 0; v < SECTION_SIZE; v++) {
                    for (int u = 0; u < SECTION_SIZE; u++) {
                        int x = mapX(face, plane, u, v);
                        int y = mapY(face, plane, u, v);
                        int z = mapZ(face, plane, u, v);
                        int idx = index(x, y, z);

                        if (faceVisited.get(idx)) {
                            continue;
                        }

                        BlockState state = blocks[idx];
                        if (state == null || !visibility.isVisibleFace(x, y, z, face, state)) {
                            faceVisited.set(idx);
                            continue;
                        }

                        int width = 1;
                        while (u + width < SECTION_SIZE) {
                            int nx = mapX(face, plane, u + width, v);
                            int ny = mapY(face, plane, u + width, v);
                            int nz = mapZ(face, plane, u + width, v);
                            int nIdx = index(nx, ny, nz);
                            BlockState nState = blocks[nIdx];
                            if (faceVisited.get(nIdx) || nState != state || !visibility.isVisibleFace(nx, ny, nz, face, nState)
                                    || !mergePredicate.canMerge(x, y, z, nx, ny, nz, face, nState)) {
                                break;
                            }
                            width++;
                        }

                        int height = 1;
                        while (v + height < SECTION_SIZE) {
                            boolean rowMatches = true;
                            for (int du = 0; du < width; du++) {
                                int nx = mapX(face, plane, u + du, v + height);
                                int ny = mapY(face, plane, u + du, v + height);
                                int nz = mapZ(face, plane, u + du, v + height);
                                int nIdx = index(nx, ny, nz);
                                BlockState nState = blocks[nIdx];
                                if (faceVisited.get(nIdx) || nState != state || !visibility.isVisibleFace(nx, ny, nz, face, nState)
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
                            for (int du = 0; du < width; du++) {
                                int mx = mapX(face, plane, u + du, v + dv);
                                int my = mapY(face, plane, u + du, v + dv);
                                int mz = mapZ(face, plane, u + du, v + dv);
                                faceVisited.set(index(mx, my, mz));
                            }
                        }

                        quads.add(new GreedyQuad(face, x, y, z, width, height, state));
                    }
                }
            }
        }

        return quads;
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
}
