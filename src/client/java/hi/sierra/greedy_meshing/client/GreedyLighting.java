package hi.sierra.greedy_meshing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
//? if UNOBFUSCATED {
/*import net.minecraft.client.renderer.block.BlockAndTintGetter;
*///?} else {
import net.minecraft.world.level.BlockAndTintGetter;
//?}
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

public final class GreedyLighting {
    private GreedyLighting() {
    }

    //? if >=26.2 {
    /*// BlockModelLighter keeps mutable per-call scratch state (its own MutableBlockPos), so each
    // chunk-builder thread needs its own instance — mirrors the vanilla ThreadLocal<Cache> inside it.
    private static final ThreadLocal<net.minecraft.client.renderer.block.BlockModelLighter> LIGHTER =
            ThreadLocal.withInitial(net.minecraft.client.renderer.block.BlockModelLighter::new);
    *///?}

    public static void computeTileLighting(
            BlockAndTintGetter world,
            BlockState state,
            Direction face,
            int worldX,
            int worldY,
            int worldZ,
            Scratch scratch
    ) {
        // Water never gets vanilla's opaque-block 4-corner AO blending — that path samples
        // neighbor occlusion (isViewBlocking/getLightBlock) and diagonal corners assuming solid-cube
        // semantics that don't apply to a merged fluid surface, and was never exercised by this mod
        // for fluids before (only opaque blocks used it). Flat per-face lighting matches how vanilla
        // actually lights water (no per-corner AO) and sidesteps that entirely-untested-for-water path.
        if (!useSmoothLighting(state) || state.is(Blocks.WATER)) {
            fillFlatLighting(world, state, face, worldX, worldY, worldZ, scratch);
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
        int i = greedyMeshing$getLightColor(world, state0, samplePos);
        float f = state0.getShadeBrightness(world, samplePos);

        samplePos.set(c1x, c1y, c1z);
        BlockState state1 = world.getBlockState(samplePos);
        int j = greedyMeshing$getLightColor(world, state1, samplePos);
        float g = state1.getShadeBrightness(world, samplePos);

        samplePos.set(c2x, c2y, c2z);
        BlockState state2 = world.getBlockState(samplePos);
        int k = greedyMeshing$getLightColor(world, state2, samplePos);
        float h = state2.getShadeBrightness(world, samplePos);

        samplePos.set(c3x, c3y, c3z);
        BlockState state3 = world.getBlockState(samplePos);
        int l = greedyMeshing$getLightColor(world, state3, samplePos);
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
            o = greedyMeshing$getLightColor(world, diagonalState, samplePos);
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
            q = greedyMeshing$getLightColor(world, diagonalState, samplePos);
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
            s = greedyMeshing$getLightColor(world, diagonalState, samplePos);
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
            u = greedyMeshing$getLightColor(world, diagonalState, samplePos);
        }

        samplePos.set(faceX, faceY, faceZ);
        BlockState faceState = world.getBlockState(samplePos);
        int v = greedyMeshing$getLightColor(world, faceState, samplePos);
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

        //? if UNOBFUSCATED {
        /*float shade = scratch.applyDirectionalShade ? world.cardinalLighting().byFace(face) : 1.0f;
        *///?} else {
        float shade = world.getShade(face, scratch.applyDirectionalShade);
        //?}
        scratch.brightness[0] = c1 * shade;
        scratch.brightness[1] = c2 * shade;
        scratch.brightness[2] = c3 * shade;
        scratch.brightness[3] = c0 * shade;
        scratch.lightmap[0] = lm1;
        scratch.lightmap[1] = lm2;
        scratch.lightmap[2] = lm3;
        scratch.lightmap[3] = lm0;
    }

    /**
     * Flat lighting for greedy merged quads — samples the light level at a specific
     * block position without AO. Used per-corner on merged faces so the GPU
     * interpolates a smooth light gradient without broken AO artifacts.
     */
    public static void computeGreedyCornerLighting(
            BlockAndTintGetter world,
            Direction face,
            int worldX,
            int worldY,
            int worldZ,
            Scratch scratch,
            int cornerIndex
    ) {
        BlockPos.MutableBlockPos samplePos = scratch.mutablePos;
        samplePos.set(worldX + face.getStepX(), worldY + face.getStepY(), worldZ + face.getStepZ());
        BlockState cornerState = world.getBlockState(samplePos);
        int packedLight = greedyMeshing$getLightColor(world, cornerState, samplePos);
        //? if UNOBFUSCATED {
        /*float shade = scratch.applyDirectionalShade ? world.cardinalLighting().byFace(face) : 1.0f;
        *///?} else {
        float shade = world.getShade(face, scratch.applyDirectionalShade);
        //?}
        scratch.brightness[cornerIndex] = shade;
        scratch.lightmap[cornerIndex] = packedLight;
    }

    private static boolean useSmoothLighting(BlockState state) {
        //? if UNOBFUSCATED {
        /*return Minecraft.getInstance().options.ambientOcclusion().get() && state.getLightEmission() == 0;
        *///?} else {
        return Minecraft.useAmbientOcclusion() && state.getLightEmission() == 0;
        //?}
    }

    private static void fillFlatLighting(
            BlockAndTintGetter world,
            BlockState state,
            Direction face,
            int worldX,
            int worldY,
            int worldZ,
            Scratch scratch
    ) {
        BlockPos.MutableBlockPos samplePos = scratch.mutablePos;
        // Emissive blocks must retain their own block light when a translucent block is
        // adjacent to the face. Sampling the adjacent block is correct for ordinary opaque
        // blocks, but tinted glass intentionally blocks light, so using its lightmap position
        // makes sea lanterns (and other light-emitting blocks) render dark through the glass.
        if (state.getLightEmission() > 0) {
            samplePos.set(worldX, worldY, worldZ);
        } else {
            samplePos.set(worldX + face.getStepX(), worldY + face.getStepY(), worldZ + face.getStepZ());
        }
        BlockState faceState = world.getBlockState(samplePos);
        int packedLight = greedyMeshing$getLightColor(world, faceState, samplePos);
        //? if UNOBFUSCATED {
        /*float shade = scratch.applyDirectionalShade ? world.cardinalLighting().byFace(face) : 1.0f;
        *///?} else {
        float shade = world.getShade(face, scratch.applyDirectionalShade);
        //?}
        Arrays.fill(scratch.brightness, shade);
        Arrays.fill(scratch.lightmap, packedLight);
    }

    private static boolean isOpen(BlockAndTintGetter world, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        pos.set(x, y, z);
        BlockState state = world.getBlockState(pos);
        //? if UNOBFUSCATED {
        /*return !state.isViewBlocking(world, pos) || state.getLightDampening() == 0;
        *///?} else if >=1.21.2 {
        return !state.isViewBlocking(world, pos) || state.getLightBlock() == 0;
        //?} else {
        /*return !state.isViewBlocking(world, pos) || state.getLightBlock(world, pos) == 0;
        *///?}
    }

    private static int greedyMeshing$getLightColor(BlockAndTintGetter world, BlockState state, BlockPos pos) {
        //? if >=26.2 {
        /*return LIGHTER.get().getLightCoords(state, world, pos);
        *///?} else if UNOBFUSCATED {
        /*return LevelRenderer.getLightCoords(world, pos);
        *///?} else {
        return LevelRenderer.getLightColor(world, pos);
        //?}
    }

    /** Packed block/sky light at a face sample, using the version-correct light API. */
    public static int mergeLightKey(BlockAndTintGetter world, BlockPos pos) {
        return greedyMeshing$getLightColor(world, world.getBlockState(pos), pos);
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
        return ((a + b + c + d) >> 2) & 0xFF00FF;
    }

    public static final class Scratch {
        public final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        public final float[] brightness = new float[4];
        public final int[] lightmap = new int[4];
        // Set once per section build. False inside a Sable sub-level, whose own shader applies
        // directional shading dynamically — baking ours in on top would double it (issue #4).
        public boolean applyDirectionalShade = true;
    }
}
