package org.Jean.greedy_meshing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

public final class GreedyLighting {
    private GreedyLighting() {
    }

    public static void computeTileLighting(
            BlockAndTintGetter world,
            BlockState state,
            Direction face,
            int worldX,
            int worldY,
            int worldZ,
            Scratch scratch
    ) {
        if (!useSmoothLighting(state)) {
            fillFlatLighting(world, face, worldX, worldY, worldZ, scratch);
            return;
        }

        Direction corner0;
        Direction corner1;
        Direction corner2;
        Direction corner3;
        switch (face) {
            case DOWN -> {
                corner0 = Direction.WEST;
                corner1 = Direction.EAST;
                corner2 = Direction.NORTH;
                corner3 = Direction.SOUTH;
            }
            case UP -> {
                corner0 = Direction.EAST;
                corner1 = Direction.WEST;
                corner2 = Direction.NORTH;
                corner3 = Direction.SOUTH;
            }
            case NORTH -> {
                corner0 = Direction.UP;
                corner1 = Direction.DOWN;
                corner2 = Direction.EAST;
                corner3 = Direction.WEST;
            }
            case SOUTH -> {
                corner0 = Direction.WEST;
                corner1 = Direction.EAST;
                corner2 = Direction.DOWN;
                corner3 = Direction.UP;
            }
            case WEST -> {
                corner0 = Direction.UP;
                corner1 = Direction.DOWN;
                corner2 = Direction.NORTH;
                corner3 = Direction.SOUTH;
            }
            case EAST -> {
                corner0 = Direction.DOWN;
                corner1 = Direction.UP;
                corner2 = Direction.NORTH;
                corner3 = Direction.SOUTH;
            }
            default -> throw new IllegalStateException("Unexpected face: " + face);
        }

        int faceX = worldX + face.getStepX();
        int faceY = worldY + face.getStepY();
        int faceZ = worldZ + face.getStepZ();

        int c0x = faceX + corner0.getStepX();
        int c0y = faceY + corner0.getStepY();
        int c0z = faceZ + corner0.getStepZ();
        int c1x = faceX + corner1.getStepX();
        int c1y = faceY + corner1.getStepY();
        int c1z = faceZ + corner1.getStepZ();
        int c2x = faceX + corner2.getStepX();
        int c2y = faceY + corner2.getStepY();
        int c2z = faceZ + corner2.getStepZ();
        int c3x = faceX + corner3.getStepX();
        int c3y = faceY + corner3.getStepY();
        int c3z = faceZ + corner3.getStepZ();

        BlockPos.MutableBlockPos samplePos = scratch.mutablePos;

        samplePos.set(c0x, c0y, c0z);
        BlockState state0 = world.getBlockState(samplePos);
        int i = LevelRenderer.getLightColor(world, samplePos);
        float f = state0.getShadeBrightness(world, samplePos);

        samplePos.set(c1x, c1y, c1z);
        BlockState state1 = world.getBlockState(samplePos);
        int j = LevelRenderer.getLightColor(world, samplePos);
        float g = state1.getShadeBrightness(world, samplePos);

        samplePos.set(c2x, c2y, c2z);
        BlockState state2 = world.getBlockState(samplePos);
        int k = LevelRenderer.getLightColor(world, samplePos);
        float h = state2.getShadeBrightness(world, samplePos);

        samplePos.set(c3x, c3y, c3z);
        BlockState state3 = world.getBlockState(samplePos);
        int l = LevelRenderer.getLightColor(world, samplePos);
        float m = state3.getShadeBrightness(world, samplePos);

        boolean open0 = isOpen(world, samplePos, c0x + face.getStepX(), c0y + face.getStepY(), c0z + face.getStepZ());
        boolean open1 = isOpen(world, samplePos, c1x + face.getStepX(), c1y + face.getStepY(), c1z + face.getStepZ());
        boolean open2 = isOpen(world, samplePos, c2x + face.getStepX(), c2y + face.getStepY(), c2z + face.getStepZ());
        boolean open3 = isOpen(world, samplePos, c3x + face.getStepX(), c3y + face.getStepY(), c3z + face.getStepZ());

        float n;
        int o;
        if (!open2 && !open0) {
            n = f;
            o = i;
        } else {
            samplePos.set(c0x + corner2.getStepX(), c0y + corner2.getStepY(), c0z + corner2.getStepZ());
            BlockState diagonalState = world.getBlockState(samplePos);
            n = diagonalState.getShadeBrightness(world, samplePos);
            o = LevelRenderer.getLightColor(world, samplePos);
        }

        float p;
        int q;
        if (!open3 && !open0) {
            p = f;
            q = i;
        } else {
            samplePos.set(c0x + corner3.getStepX(), c0y + corner3.getStepY(), c0z + corner3.getStepZ());
            BlockState diagonalState = world.getBlockState(samplePos);
            p = diagonalState.getShadeBrightness(world, samplePos);
            q = LevelRenderer.getLightColor(world, samplePos);
        }

        float r;
        int s;
        if (!open2 && !open1) {
            r = f;
            s = i;
        } else {
            samplePos.set(c1x + corner2.getStepX(), c1y + corner2.getStepY(), c1z + corner2.getStepZ());
            BlockState diagonalState = world.getBlockState(samplePos);
            r = diagonalState.getShadeBrightness(world, samplePos);
            s = LevelRenderer.getLightColor(world, samplePos);
        }

        float t;
        int u;
        if (!open3 && !open1) {
            t = f;
            u = i;
        } else {
            samplePos.set(c1x + corner3.getStepX(), c1y + corner3.getStepY(), c1z + corner3.getStepZ());
            BlockState diagonalState = world.getBlockState(samplePos);
            t = diagonalState.getShadeBrightness(world, samplePos);
            u = LevelRenderer.getLightColor(world, samplePos);
        }

        samplePos.set(faceX, faceY, faceZ);
        BlockState faceState = world.getBlockState(samplePos);
        int v = LevelRenderer.getLightColor(world, samplePos);
        float w = faceState.getShadeBrightness(world, samplePos);

        float ao0 = (m + f + p + w) * 0.25F;
        float ao1 = (h + f + n + w) * 0.25F;
        float ao2 = (h + g + r + w) * 0.25F;
        float ao3 = (m + g + t + w) * 0.25F;

        int light0 = blend(l, i, q, v);
        int light1 = blend(k, i, o, v);
        int light2 = blend(k, j, s, v);
        int light3 = blend(l, j, u, v);

        // Match vanilla remap first, then shift once to our fixed vertex order.
        float c0;
        float c1;
        float c2;
        float c3;
        int lm0;
        int lm1;
        int lm2;
        int lm3;
        switch (face) {
            case DOWN, SOUTH -> {
                c0 = ao0;
                c1 = ao1;
                c2 = ao2;
                c3 = ao3;
                lm0 = light0;
                lm1 = light1;
                lm2 = light2;
                lm3 = light3;
            }
            case UP -> {
                c0 = ao2;
                c1 = ao3;
                c2 = ao0;
                c3 = ao1;
                lm0 = light2;
                lm1 = light3;
                lm2 = light0;
                lm3 = light1;
            }
            case NORTH, WEST -> {
                c0 = ao1;
                c1 = ao2;
                c2 = ao3;
                c3 = ao0;
                lm0 = light1;
                lm1 = light2;
                lm2 = light3;
                lm3 = light0;
            }
            case EAST -> {
                c0 = ao3;
                c1 = ao0;
                c2 = ao1;
                c3 = ao2;
                lm0 = light3;
                lm1 = light0;
                lm2 = light1;
                lm3 = light2;
            }
            default -> throw new IllegalStateException("Unexpected face: " + face);
        }

        float shade = world.getShade(face, true);
        scratch.brightness[0] = c1 * shade;
        scratch.brightness[1] = c2 * shade;
        scratch.brightness[2] = c3 * shade;
        scratch.brightness[3] = c0 * shade;
        scratch.lightmap[0] = lm1;
        scratch.lightmap[1] = lm2;
        scratch.lightmap[2] = lm3;
        scratch.lightmap[3] = lm0;
    }

    private static boolean useSmoothLighting(BlockState state) {
        return Minecraft.useAmbientOcclusion() && state.getLightEmission() == 0;
    }

    private static void fillFlatLighting(
            BlockAndTintGetter world,
            Direction face,
            int worldX,
            int worldY,
            int worldZ,
            Scratch scratch
    ) {
        BlockPos.MutableBlockPos samplePos = scratch.mutablePos;
        samplePos.set(worldX + face.getStepX(), worldY + face.getStepY(), worldZ + face.getStepZ());
        int packedLight = LevelRenderer.getLightColor(world, samplePos);
        float shade = world.getShade(face, true);
        Arrays.fill(scratch.brightness, shade);
        Arrays.fill(scratch.lightmap, packedLight);
    }

    private static boolean isOpen(BlockAndTintGetter world, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        pos.set(x, y, z);
        BlockState state = world.getBlockState(pos);
        return !state.isViewBlocking(world, pos) || state.getLightBlock() == 0;
    }

    private static int blend(int a, int b, int c, int d) {
        if (a == 0) {
            a = d;
        }
        if (b == 0) {
            b = d;
        }
        if (c == 0) {
            c = d;
        }
        return (a + b + c + d >> 2) & 0xFF00FF;
    }

    public static final class Scratch {
        public final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        public final float[] brightness = new float[4];
        public final int[] lightmap = new int[4];
    }
}
