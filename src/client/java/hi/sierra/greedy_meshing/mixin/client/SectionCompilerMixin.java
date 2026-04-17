package hi.sierra.greedy_meshing.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
//? if UNOBFUSCATED {
/*import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
*///?} else if >=1.21.5 {
/*import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
*///?} else {
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
//?}
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
//? if >=1.21.6 {
/*import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
*///?} else {
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
//?}
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
//? if UNOBFUSCATED {
/*import net.minecraft.client.renderer.block.BlockAndTintGetter;
*///?} else {
import net.minecraft.world.level.BlockAndTintGetter;
//?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import hi.sierra.greedy_meshing.GreedyConfig;
import hi.sierra.greedy_meshing.GreedyEligibility;
import hi.sierra.greedy_meshing.GreedyMesher;
import hi.sierra.greedy_meshing.client.GreedyDebugStore;
import hi.sierra.greedy_meshing.client.GreedyLighting;
import hi.sierra.greedy_meshing.client.GreedyPerformanceStats;
import hi.sierra.greedy_meshing.client.GreedyRuntimeState;
import hi.sierra.greedy_meshing.client.GreedyVanillaWorkState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    //? if >=1.21.6 {
    /*@Shadow
    protected abstract BufferBuilder getOrBeginLayer(
            Map<ChunkSectionLayer, BufferBuilder> map,
            SectionBufferBuilderPack pack,
            ChunkSectionLayer layer
    );
    *///?} else {
    @Shadow
    protected abstract BufferBuilder getOrBeginLayer(
            Map<RenderType, BufferBuilder> map,
            SectionBufferBuilderPack pack,
            RenderType layer
    );
    //?}

    @Unique
    private static final ThreadLocal<GreedyVanillaWorkState> GREEDY_MESHING$STATE = ThreadLocal.withInitial(GreedyVanillaWorkState::new);
    @Unique
    private static final Direction[] GREEDY_MESHING$FACES = Direction.values();
    @Inject(method = "compile", at = @At("HEAD"))
    private void greedyMeshing$beginCompile(
            SectionPos sectionPos,
            //? if >=1.21.6 {
            /*RenderSectionRegion renderSectionRegion,
            *///?} else {
            RenderChunkRegion renderSectionRegion,
            //?}
            VertexSorting vertexSorting,
            SectionBufferBuilderPack sectionBufferBuilderPack,
            CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        GreedyVanillaWorkState work = GREEDY_MESHING$STATE.get();
        work.reset(sectionPos);
        GreedyDebugStore.clearSection(sectionPos.asLong());
        GreedyPerformanceStats.onVanillaCompileHook();
    }

    /**
     * Wraps getRenderShape() inside the block iteration loop of compile().
     * This works WITH Fabric Indigo's redirect — if Indigo is present, our wrapper
     * runs around Indigo's redirect. For greedy-eligible blocks, we store them and
     * return INVISIBLE to prevent both Indigo and vanilla from rendering them.
     */
    @WrapOperation(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"
            )
    )
    private RenderShape greedyMeshing$wrapGetRenderShape(
            BlockState state,
            Operation<RenderShape> original,
            //? if >=1.21.6 {
            /*@Local(argsOnly = true) RenderSectionRegion region,
            *///?} else {
            @Local(argsOnly = true) RenderChunkRegion region,
            //?}
            @Local(ordinal = 2) BlockPos blockPos
    ) {
        GreedyVanillaWorkState work = GREEDY_MESHING$STATE.get();
        if (work.initialized() && GreedyRuntimeState.isRuntimeGreedyActive()) {
            int localX = blockPos.getX() - work.baseX();
            int localY = blockPos.getY() - work.baseY();
            int localZ = blockPos.getZ() - work.baseZ();
            if (localX >= 0 && localX < GreedyMesher.SECTION_SIZE
                    && localY >= 0 && localY < GreedyMesher.SECTION_SIZE
                    && localZ >= 0 && localZ < GreedyMesher.SECTION_SIZE
                    && GreedyEligibility.isGreedyOpaqueCube(state, region, blockPos)) {
                int idx = GreedyMesher.index(localX, localY, localZ);
                work.addEligible(idx, state);
                return RenderShape.INVISIBLE;
            }
        }
        return original.call(state);
    }

    /**
     * Inject just before the map.entrySet() loop that builds MeshData from BufferBuilders.
     * At this point, Indigo/vanilla have rendered all non-greedy blocks into the buffers.
     * We add our greedy quads to the SOLID buffer before it gets built into MeshData.
     */
    @Inject(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;entrySet()Ljava/util/Set;"
            )
    )
    private void greedyMeshing$emitGreedyQuads(
            SectionPos sectionPos,
            //? if >=1.21.6 {
            /*RenderSectionRegion renderSectionRegion,
            *///?} else {
            RenderChunkRegion renderSectionRegion,
            //?}
            VertexSorting vertexSorting,
            SectionBufferBuilderPack sectionBufferBuilderPack,
            CallbackInfoReturnable<SectionCompiler.Results> cir,
            //? if >=1.21.6 {
            /*@Local Map<ChunkSectionLayer, BufferBuilder> map
            *///?} else {
            @Local Map<RenderType, BufferBuilder> map
            //?}
    ) {
        GreedyVanillaWorkState work = GREEDY_MESHING$STATE.get();
        try {
            if (!GreedyRuntimeState.isRuntimeGreedyActive() || work.eligibleCount() <= 0) {
                return;
            }

            int baseX = work.baseX();
            int baseY = work.baseY();
            int baseZ = work.baseZ();

            long[][] mergeKeys = work.faceMergeKeys();
            populateFaceVisibility(renderSectionRegion, work.sectionStates(), work.visibleFaces(), mergeKeys, baseX, baseY, baseZ, work.eligibleIndices(), work.eligibleCount());
            List<GreedyMesher.GreedyQuad> merged = GreedyMesher.meshWithKeys(
                    work.sectionStates(), work.visibleFaces(), mergeKeys
            );
            if (merged.isEmpty()) {
                return;
            }

            boolean captureDebug = GreedyConfig.debugWireframe() || GreedyConfig.debugTrianglesHud() || GreedyConfig.debugComparison();
            List<GreedyDebugStore.DebugQuad> debugQuads = captureDebug ? new ArrayList<>(merged.size()) : List.of();
            int emittedQuads = 0;
            int vanillaEquivalent = 0;
            boolean shaderPackActive = GreedyRuntimeState.isShaderPackActive();

            for (GreedyMesher.GreedyQuad quad : merged) {
                long cacheKey = ((long) quad.state().hashCode() << 32) | quad.face().ordinal();
                // Open-addressing cache lookup — avoids Long autoboxing and HashMap overhead
                int slot = (int)(cacheKey ^ (cacheKey >>> 32)) & (GreedyVanillaWorkState.LAYER_CACHE_SIZE - 1);
                @SuppressWarnings("unchecked")
                List<FaceAppearance> faceLayers;
                if (work.layerCacheKeys[slot] == cacheKey && work.layerCacheValues[slot] != null) {
                    faceLayers = (List<FaceAppearance>) work.layerCacheValues[slot];
                } else {
                    faceLayers = greedyMeshing$resolveFaceLayers(quad.state(), quad.face());
                    work.layerCacheKeys[slot] = cacheKey;
                    work.layerCacheValues[slot] = faceLayers;
                }
                int blockFaces = quad.width() * quad.height();
                for (FaceAppearance faceLayer : faceLayers) {
                    //? if UNOBFUSCATED {
                    /*BufferBuilder layerBuilder = getOrBeginLayer(map, sectionBufferBuilderPack, faceLayer.layer());
                    *///?} else {
                    BufferBuilder layerBuilder = getOrBeginLayer(map, sectionBufferBuilderPack, ItemBlockRenderTypes.getChunkRenderType(quad.state()));
                    //?}
                    if (shaderPackActive) {
                        emittedQuads += emitTiledQuads(layerBuilder, quad,
                                faceLayer.sprite().getU0(), faceLayer.sprite().getU1(),
                                faceLayer.sprite().getV0(), faceLayer.sprite().getV1(),
                                faceLayer.tinted(), faceLayer.tintIndex(), renderSectionRegion, baseX, baseY, baseZ,
                                work);
                    } else {
                        emittedQuads += emitGreedyQuad(layerBuilder, quad,
                                faceLayer.sprite().getU0(), faceLayer.sprite().getU1(),
                                faceLayer.sprite().getV0(), faceLayer.sprite().getV1(),
                                faceLayer.tinted(), faceLayer.tintIndex(), renderSectionRegion, baseX, baseY, baseZ,
                                work);
                    }
                    vanillaEquivalent += blockFaces;
                }
                if (captureDebug) {
                    debugQuads.add(toDebugQuad(quad, baseX, baseY, baseZ));
                }
            }

            GreedyPerformanceStats.onGreedySectionBuilt(work.eligibleCount(), merged.size(), emittedQuads, vanillaEquivalent);
            if (captureDebug) {
                GreedyDebugStore.setSectionQuads(sectionPos.asLong(), debugQuads);
            }
        } finally {
            GREEDY_MESHING$STATE.remove();
        }
    }

    private static GreedyDebugStore.DebugQuad toDebugQuad(GreedyMesher.GreedyQuad quad, int baseX, int baseY, int baseZ) {
        float[] c = corners(quad);
        return new GreedyDebugStore.DebugQuad(
                c[0] + baseX, c[1] + baseY, c[2] + baseZ,
                c[3] + baseX, c[4] + baseY, c[5] + baseZ,
                c[6] + baseX, c[7] + baseY, c[8] + baseZ,
                c[9] + baseX, c[10] + baseY, c[11] + baseZ
        );
    }

    /**
     * Returns ALL quad layers for a block face. Most blocks have 1 layer.
     * Blocks like grass have 2 layers on sides (dirt base + tinted overlay).
     * We emit one merged quad per layer, preserving overlays on merged faces.
     */
    @Unique
    private static List<FaceAppearance> greedyMeshing$resolveFaceLayers(BlockState state, Direction face) {
        //? if UNOBFUSCATED {
        /*BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        RandomSource random = RandomSource.create(0L);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        List<FaceAppearance> layers = new ArrayList<>();

        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(face)) {
                layers.add(new FaceAppearance(quad.materialInfo().sprite(), quad.materialInfo().isTinted(), quad.materialInfo().layer(), quad.materialInfo().tintIndex()));
            }
        }
        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == face) {
                    layers.add(new FaceAppearance(quad.materialInfo().sprite(), quad.materialInfo().isTinted(), quad.materialInfo().layer(), quad.materialInfo().tintIndex()));
                }
            }
        }
        if (layers.isEmpty()) {
            layers.add(new FaceAppearance(model.particleMaterial().sprite(), false, ChunkSectionLayer.SOLID, -1));
        }
        *///?} else if >=1.21.5 {
        /*BlockStateModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        RandomSource random = RandomSource.create(0L);
        List<BlockModelPart> parts = model.collectParts(random);
        List<FaceAppearance> layers = new ArrayList<>();

        for (BlockModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(face)) {
                layers.add(new FaceAppearance(quad.sprite(), quad.isTinted(), quad.tintIndex()));
            }
        }
        for (BlockModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == face) {
                    layers.add(new FaceAppearance(quad.sprite(), quad.isTinted(), quad.tintIndex()));
                }
            }
        }
        if (layers.isEmpty()) {
            layers.add(new FaceAppearance(model.particleIcon(), false, -1));
        }
        *///?} else {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        RandomSource random = RandomSource.create(0L);
        List<FaceAppearance> layers = new ArrayList<>();

        for (BakedQuad quad : model.getQuads(state, face, random)) {
            layers.add(new FaceAppearance(quad.getSprite(), quad.isTinted(), quad.getTintIndex()));
        }
        for (BakedQuad quad : model.getQuads(state, null, random)) {
            if (quad.getDirection() == face) {
                layers.add(new FaceAppearance(quad.getSprite(), quad.isTinted(), quad.getTintIndex()));
            }
        }
        if (layers.isEmpty()) {
            layers.add(new FaceAppearance(model.getParticleIcon(), false, -1));
        }
        //?}
        return layers;
    }

    @Unique
    private static void populateFaceVisibility(
            //? if >=1.21.6 {
            /*RenderSectionRegion region,
            *///?} else {
            RenderChunkRegion region,
            //?}
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
        for (int i = 0; i < eligibleCount; i++) {
            int idx = eligibleIndices[i];
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
            for (Direction face : GREEDY_MESHING$FACES) {
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
                BlockState neighbor = region.getBlockState(samplePos);
                //? if >=1.21.2 {
                /*if (Block.shouldRenderFace(state, neighbor, face)) {
                *///?} else {
                if (Block.shouldRenderFace(state, region, new BlockPos(worldX, worldY, worldZ), face, samplePos)) {
                //?}
                    visibleFaces[face.ordinal()].set(idx);
                    int aoKey = computeAoKey(region, samplePos, worldX, worldY, worldZ, face);
                    faceMergeKeys[face.ordinal()][idx] = aoKey;
                }
            }
        }
    }

    @Unique
    private static final int[][][] AO_OFFSETS = new int[6][8][3];
    static {
        Direction[] faces = Direction.values();
        for (Direction face : faces) {
            int ax1, ay1, az1, ax2, ay2, az2;
            switch (face) {
                case DOWN, UP     -> { ax1=1; ay1=0; az1=0; ax2=0; ay2=0; az2=1; }
                case NORTH, SOUTH -> { ax1=1; ay1=0; az1=0; ax2=0; ay2=1; az2=0; }
                default           -> { ax1=0; ay1=0; az1=1; ax2=0; ay2=1; az2=0; }
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

    @Unique
    //? if >=1.21.6 {
    /*private static int computeAoKey(RenderSectionRegion region, BlockPos.MutableBlockPos pos,
    *///?} else {
    private static int computeAoKey(RenderChunkRegion region, BlockPos.MutableBlockPos pos,
    //?}
                                     int worldX, int worldY, int worldZ, Direction face) {
        int[][] offsets = AO_OFFSETS[face.ordinal()];
        int key = 0;
        for (int i = 0; i < 8; i++) {
            pos.set(worldX + offsets[i][0], worldY + offsets[i][1], worldZ + offsets[i][2]);
            BlockState neighbor = region.getBlockState(pos);
            if (neighbor.isViewBlocking(region, pos)
                    //? if UNOBFUSCATED {
                    /*&& neighbor.getLightDampening() != 0
                    *///?} else if >=1.21.2 {
                    /*&& neighbor.getLightBlock() != 0
                    *///?} else {
                    && neighbor.getLightBlock(region, pos) != 0
                    //?}
            ) {
                key |= (1 << i);
            }
        }
        return key;
    }

    /** Max sub-quad size for lighting subdivision. Larger merged quads are split into
     *  sub-quads of at most this size so lighting gets sampled every LIGHT_STEP blocks. */
    private static final int LIGHT_STEP = 4;

    private static int emitGreedyQuad(
            VertexConsumer consumer, GreedyMesher.GreedyQuad quad,
            float u0, float u1, float v0, float v1,
            //? if >=1.21.6 {
            /*boolean applyTint, int tintIndex, RenderSectionRegion region,
            *///?} else {
            boolean applyTint, int tintIndex, RenderChunkRegion region,
            //?}
            int baseX, int baseY, int baseZ,
            GreedyVanillaWorkState work
    ) {
        int W = quad.width();
        int H = quad.height();

        // Single-block: emit directly with full AO
        if (W == 1 && H == 1) {
            emitSubQuad(consumer, quad, 0, 0, 1, 1, u0, u1, v0, v1,
                    applyTint, tintIndex, region, baseX, baseY, baseZ, work, true);
            return 1;
        }

        // Subdivide into LIGHT_STEP x LIGHT_STEP sub-quads for better lighting
        int count = 0;
        for (int sv = 0; sv < H; sv += LIGHT_STEP) {
            int sh = Math.min(LIGHT_STEP, H - sv);
            for (int su = 0; su < W; su += LIGHT_STEP) {
                int sw = Math.min(LIGHT_STEP, W - su);
                emitSubQuad(consumer, quad, su, sv, sw, sh, u0, u1, v0, v1,
                        applyTint, tintIndex, region, baseX, baseY, baseZ, work, false);
                count++;
            }
        }
        return count;
    }

    @Unique
    private static void emitSubQuad(
            VertexConsumer consumer, GreedyMesher.GreedyQuad quad,
            int offU, int offV, int subW, int subH,
            float u0, float u1, float v0, float v1,
            //? if >=1.21.6 {
            /*boolean applyTint, int tintIndex, RenderSectionRegion region,
            *///?} else {
            boolean applyTint, int tintIndex, RenderChunkRegion region,
            //?}
            int baseX, int baseY, int baseZ,
            GreedyVanillaWorkState work,
            boolean fullAO
    ) {
        // Build a temporary sub-quad with offset position and sub-dimensions
        Direction face = quad.face();
        int qx = quad.x(), qy = quad.y(), qz = quad.z();

        // Offset the origin based on face direction's U/V axes
        int sx, sy, sz;
        switch (face) {
            case NORTH, SOUTH -> { sx = qx + offU; sy = qy + offV; sz = qz; }
            case WEST, EAST   -> { sx = qx; sy = qy + offV; sz = qz + offU; }
            case DOWN, UP     -> { sx = qx + offU; sy = qy; sz = qz + offV; }
            default -> { sx = qx; sy = qy; sz = qz; }
        }

        GreedyMesher.GreedyQuad sub = new GreedyMesher.GreedyQuad(face, sx, sy, sz, subW, subH, quad.state());

        float[] c = work.scratchCorners;
        fillCorners(c, sub);
        float faceAlpha = (246.0f + face.ordinal()) / 255.0f;
        float nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();

        BlockColors blockColors = applyTint ? Minecraft.getInstance().getBlockColors() : null;
        BlockPos.MutableBlockPos tintPos = work.scratchTintPos;
        GreedyLighting.Scratch lighting = work.scratchLighting;

        float[] brightness = work.scratchBrightness;
        int[] lightmap = work.scratchLightmap;
        float[] tintR = work.scratchTintR;
        float[] tintG = work.scratchTintG;
        float[] tintB = work.scratchTintB;

        int[][] cornerBlocks = work.scratchCornerBlocks;
        greedyMeshing$fillCornerBlockPositions(cornerBlocks, sub, baseX, baseY, baseZ);

        for (int i = 0; i < 4; i++) {
            int wx = cornerBlocks[i][0];
            int wy = cornerBlocks[i][1];
            int wz = cornerBlocks[i][2];
            GreedyLighting.computeTileLighting(region, quad.state(), face, wx, wy, wz, lighting);
            brightness[i] = lighting.brightness[i];
            lightmap[i] = lighting.lightmap[i];

            int tint = applyTint ? tintColorForTile(quad.state(), region, wx, wy, wz, tintPos, blockColors, tintIndex) : 0xFFFFFF;
            tintR[i] = ((tint >> 16) & 0xFF) / 255.0f;
            tintG[i] = ((tint >> 8) & 0xFF) / 255.0f;
            tintB[i] = (tint & 0xFF) / 255.0f;
        }

        float cu = (u0 + u1) * 0.5f;
        float cv = (v0 + v1) * 0.5f;

        int a = (int) (faceAlpha * 255.0f) << 24;
        for (int vi = 0; vi < 4; vi++) {
            int ci = vi * 3;
            float br = brightness[vi];
            int packedColor = a
                    | ((int) (br * tintR[vi] * 255.0f) << 16)
                    | ((int) (br * tintG[vi] * 255.0f) << 8)
                    | (int) (br * tintB[vi] * 255.0f);
            consumer.addVertex(c[ci], c[ci + 1], c[ci + 2], packedColor, cu, cv, 0, lightmap[vi], nx, ny, nz);
        }
    }

    /**
     * Shader-pack fallback: emits one quad per block tile within the merged area.
     * Each tile gets correct per-block UVs and lighting, so external shaders
     * render them properly without needing custom UV tiling logic.
     * Still benefits from face culling (hidden internal faces already removed).
     */
    @Unique
    private static int emitTiledQuads(
            VertexConsumer consumer, GreedyMesher.GreedyQuad quad,
            float u0, float u1, float v0, float v1,
            //? if >=1.21.6 {
            /*boolean applyTint, int tintIndex, RenderSectionRegion region,
            *///?} else {
            boolean applyTint, int tintIndex, RenderChunkRegion region,
            //?}
            int baseX, int baseY, int baseZ,
            GreedyVanillaWorkState work
    ) {
        float nx = quad.face().getStepX(), ny = quad.face().getStepY(), nz = quad.face().getStepZ();
        boolean flipV = quad.face().getAxis().isHorizontal();
        float tv0 = flipV ? v1 : v0, tv1 = flipV ? v0 : v1;

        BlockColors blockColors = applyTint ? Minecraft.getInstance().getBlockColors() : null;
        BlockPos.MutableBlockPos tintPos = work.scratchTintPos;
        GreedyLighting.Scratch lighting = work.scratchLighting;

        int W = quad.width();
        int H = quad.height();
        float[] c = work.scratchCorners;
        fillCorners(c, quad);
        int count = 0;

        for (int tv = 0; tv < H; tv++) {
            for (int tu = 0; tu < W; tu++) {
                float fu0 = (float) tu / W, fu1 = (float) (tu + 1) / W;
                float fv0 = (float) tv / H, fv1 = (float) (tv + 1) / H;

                float x00 = greedyMeshing$interpolate(c, fu0, fv0, 0), y00 = greedyMeshing$interpolate(c, fu0, fv0, 1), z00 = greedyMeshing$interpolate(c, fu0, fv0, 2);
                float x10 = greedyMeshing$interpolate(c, fu1, fv0, 0), y10 = greedyMeshing$interpolate(c, fu1, fv0, 1), z10 = greedyMeshing$interpolate(c, fu1, fv0, 2);
                float x11 = greedyMeshing$interpolate(c, fu1, fv1, 0), y11 = greedyMeshing$interpolate(c, fu1, fv1, 1), z11 = greedyMeshing$interpolate(c, fu1, fv1, 2);
                float x01 = greedyMeshing$interpolate(c, fu0, fv1, 0), y01 = greedyMeshing$interpolate(c, fu0, fv1, 1), z01 = greedyMeshing$interpolate(c, fu0, fv1, 2);

                int wx = baseX + (int) Math.floor(greedyMeshing$interpolate(c, fu0 + 0.001f, fv0 + 0.001f, 0));
                int wy = baseY + (int) Math.floor(greedyMeshing$interpolate(c, fu0 + 0.001f, fv0 + 0.001f, 1));
                int wz = baseZ + (int) Math.floor(greedyMeshing$interpolate(c, fu0 + 0.001f, fv0 + 0.001f, 2));

                GreedyLighting.computeTileLighting(region, quad.state(), quad.face(), wx, wy, wz, lighting);

                int tint = applyTint ? tintColorForTile(quad.state(), region, wx, wy, wz, tintPos, blockColors, tintIndex) : 0xFFFFFF;
                float tintR = ((tint >> 16) & 0xFF) / 255.0f;
                float tintG = ((tint >> 8) & 0xFF) / 255.0f;
                float tintB = (tint & 0xFF) / 255.0f;

                for (int vi = 0; vi < 4; vi++) {
                    float br = lighting.brightness[vi];
                    int packedColor = 0xFF000000
                            | ((int) (br * tintR * 255.0f) << 16)
                            | ((int) (br * tintG * 255.0f) << 8)
                            | (int) (br * tintB * 255.0f);
                    float px, py, pz, pu, pv;
                    switch (vi) {
                        case 0 -> { px = x00; py = y00; pz = z00; pu = u0; pv = tv0; }
                        case 1 -> { px = x10; py = y10; pz = z10; pu = u1; pv = tv0; }
                        case 2 -> { px = x11; py = y11; pz = z11; pu = u1; pv = tv1; }
                        default -> { px = x01; py = y01; pz = z01; pu = u0; pv = tv1; }
                    }
                    consumer.addVertex(px, py, pz, packedColor, pu, pv, 0, lighting.lightmap[vi], nx, ny, nz);
                }
                count++;
            }
        }
        return count;
    }

    @Unique
    private static float greedyMeshing$interpolate(float[] c, float fu, float fv, int axis) {
        float bottom = c[0 + axis] + (c[3 + axis] - c[0 + axis]) * fu;
        float top    = c[9 + axis] + (c[6 + axis] - c[9 + axis]) * fu;
        return bottom + (top - bottom) * fv;
    }

    @Unique
    private static void greedyMeshing$fillCornerBlockPositions(int[][] out, GreedyMesher.GreedyQuad quad, int baseX, int baseY, int baseZ) {
        int x = baseX + quad.x();
        int y = baseY + quad.y();
        int z = baseZ + quad.z();
        int w = quad.width();
        int h = quad.height();
        switch (quad.face()) {
            case NORTH -> { out[0][0]=x+w-1; out[0][1]=y; out[0][2]=z; out[1][0]=x; out[1][1]=y; out[1][2]=z; out[2][0]=x; out[2][1]=y+h-1; out[2][2]=z; out[3][0]=x+w-1; out[3][1]=y+h-1; out[3][2]=z; }
            case SOUTH -> { out[0][0]=x; out[0][1]=y; out[0][2]=z; out[1][0]=x+w-1; out[1][1]=y; out[1][2]=z; out[2][0]=x+w-1; out[2][1]=y+h-1; out[2][2]=z; out[3][0]=x; out[3][1]=y+h-1; out[3][2]=z; }
            case WEST -> { out[0][0]=x; out[0][1]=y; out[0][2]=z; out[1][0]=x; out[1][1]=y; out[1][2]=z+w-1; out[2][0]=x; out[2][1]=y+h-1; out[2][2]=z+w-1; out[3][0]=x; out[3][1]=y+h-1; out[3][2]=z; }
            case EAST -> { out[0][0]=x; out[0][1]=y; out[0][2]=z+w-1; out[1][0]=x; out[1][1]=y; out[1][2]=z; out[2][0]=x; out[2][1]=y+h-1; out[2][2]=z; out[3][0]=x; out[3][1]=y+h-1; out[3][2]=z+w-1; }
            case DOWN -> { out[0][0]=x; out[0][1]=y; out[0][2]=z; out[1][0]=x+w-1; out[1][1]=y; out[1][2]=z; out[2][0]=x+w-1; out[2][1]=y; out[2][2]=z+h-1; out[3][0]=x; out[3][1]=y; out[3][2]=z+h-1; }
            case UP -> { out[0][0]=x; out[0][1]=y; out[0][2]=z+h-1; out[1][0]=x+w-1; out[1][1]=y; out[1][2]=z+h-1; out[2][0]=x+w-1; out[2][1]=y; out[2][2]=z; out[3][0]=x; out[3][1]=y; out[3][2]=z; }
        }
    }

    private static int tintColorForTile(
            BlockState state, BlockAndTintGetter region,
            int worldX, int worldY, int worldZ,
            BlockPos.MutableBlockPos samplePos, BlockColors blockColors, int tintIndex
    ) {
        samplePos.set(worldX, worldY, worldZ);
        //? if UNOBFUSCATED {
        /*int tint = blockColors.getTintSource(state, tintIndex).colorInWorld(state, region, samplePos);
        *///?} else {
        int tint = blockColors.getColor(state, region, samplePos, tintIndex);
        //?}
        return tint == -1 ? 0xFFFFFF : tint;
    }

    private static float[] corners(GreedyMesher.GreedyQuad quad) {
        float[] out = new float[12];
        fillCorners(out, quad);
        return out;
    }

    private static void fillCorners(float[] out, GreedyMesher.GreedyQuad quad) {
        float x0 = quad.x(), y0 = quad.y(), z0 = quad.z();
        switch (quad.face()) {
            case NORTH -> { float x1 = x0 + quad.width(), y1 = y0 + quad.height(); out[0]=x1; out[1]=y0; out[2]=z0; out[3]=x0; out[4]=y0; out[5]=z0; out[6]=x0; out[7]=y1; out[8]=z0; out[9]=x1; out[10]=y1; out[11]=z0; }
            case SOUTH -> { float x1 = x0 + quad.width(), y1 = y0 + quad.height(), z1 = z0 + 1; out[0]=x0; out[1]=y0; out[2]=z1; out[3]=x1; out[4]=y0; out[5]=z1; out[6]=x1; out[7]=y1; out[8]=z1; out[9]=x0; out[10]=y1; out[11]=z1; }
            case WEST -> { float y1 = y0 + quad.height(), z1 = z0 + quad.width(); out[0]=x0; out[1]=y0; out[2]=z0; out[3]=x0; out[4]=y0; out[5]=z1; out[6]=x0; out[7]=y1; out[8]=z1; out[9]=x0; out[10]=y1; out[11]=z0; }
            case EAST -> { float x1 = x0 + 1, y1 = y0 + quad.height(), z1 = z0 + quad.width(); out[0]=x1; out[1]=y0; out[2]=z1; out[3]=x1; out[4]=y0; out[5]=z0; out[6]=x1; out[7]=y1; out[8]=z0; out[9]=x1; out[10]=y1; out[11]=z1; }
            case DOWN -> { float x1 = x0 + quad.width(), z1 = z0 + quad.height(); out[0]=x0; out[1]=y0; out[2]=z0; out[3]=x1; out[4]=y0; out[5]=z0; out[6]=x1; out[7]=y0; out[8]=z1; out[9]=x0; out[10]=y0; out[11]=z1; }
            case UP -> { float x1 = x0 + quad.width(), y1 = y0 + 1, z1 = z0 + quad.height(); out[0]=x0; out[1]=y1; out[2]=z1; out[3]=x1; out[4]=y1; out[5]=z1; out[6]=x1; out[7]=y1; out[8]=z0; out[9]=x0; out[10]=y1; out[11]=z0; }
        }
    }

    //? if UNOBFUSCATED {
    /*private record FaceAppearance(TextureAtlasSprite sprite, boolean tinted, ChunkSectionLayer layer, int tintIndex) {}
    *///?} else {
    private record FaceAppearance(TextureAtlasSprite sprite, boolean tinted, int tintIndex) {}
    //?}

    // GreedyVanillaWorkState moved to hi.sierra.greedy_meshing.client package
    // to avoid mixin inner class loading restrictions
}
