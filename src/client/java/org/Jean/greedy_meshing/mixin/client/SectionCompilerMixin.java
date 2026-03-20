package org.Jean.greedy_meshing.mixin.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.Jean.greedy_meshing.GreedyConfig;
import org.Jean.greedy_meshing.GreedyEligibility;
import org.Jean.greedy_meshing.GreedyMesher;
import org.Jean.greedy_meshing.client.GreedyDebugStore;
import org.Jean.greedy_meshing.client.GreedyLighting;
import org.Jean.greedy_meshing.client.GreedyPerformanceStats;
import org.Jean.greedy_meshing.client.GreedyRuntimeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    @Unique
    private static final ThreadLocal<GreedyVanillaWorkState> GREEDY_MESHING$STATE = ThreadLocal.withInitial(GreedyVanillaWorkState::new);
    @Unique
    private static final Map<SpriteKey, FaceAppearance> GREEDY_MESHING$FACE_CACHE = new ConcurrentHashMap<>();
    @Unique
    private static final Direction[] GREEDY_MESHING$FACES = Direction.values();

    @Inject(method = "compile", at = @At("HEAD"))
    private void greedyMeshing$beginCompile(
            SectionPos sectionPos,
            RenderSectionRegion renderSectionRegion,
            VertexSorting vertexSorting,
            SectionBufferBuilderPack sectionBufferBuilderPack,
            CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        GreedyVanillaWorkState work = GREEDY_MESHING$STATE.get();
        work.reset(sectionPos);
        GreedyDebugStore.clearSection(sectionPos.asLong());
        GreedyPerformanceStats.onVanillaCompileHook();
    }

    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLjava/util/List;)V"
            )
    )
    private void greedyMeshing$redirectVanillaSolidRender(
            BlockRenderDispatcher blockRenderer,
            BlockState state,
            BlockPos worldPos,
            BlockAndTintGetter level,
            PoseStack poseStack,
            VertexConsumer consumer,
            boolean cull,
            List<BlockModelPart> parts
    ) {
        if (!GreedyRuntimeState.isRuntimeGreedyActive()) {
            blockRenderer.renderBatched(state, worldPos, level, poseStack, consumer, cull, parts);
            return;
        }

        if (ItemBlockRenderTypes.getChunkRenderType(state) != ChunkSectionLayer.SOLID) {
            blockRenderer.renderBatched(state, worldPos, level, poseStack, consumer, cull, parts);
            return;
        }

        GreedyVanillaWorkState work = GREEDY_MESHING$STATE.get();
        if (!work.initialized()) {
            blockRenderer.renderBatched(state, worldPos, level, poseStack, consumer, cull, parts);
            return;
        }

        int localX = worldPos.getX() - work.baseX();
        int localY = worldPos.getY() - work.baseY();
        int localZ = worldPos.getZ() - work.baseZ();
        if (localX < 0 || localX >= GreedyMesher.SECTION_SIZE
                || localY < 0 || localY >= GreedyMesher.SECTION_SIZE
                || localZ < 0 || localZ >= GreedyMesher.SECTION_SIZE) {
            blockRenderer.renderBatched(state, worldPos, level, poseStack, consumer, cull, parts);
            return;
        }

        int idx = GreedyMesher.index(localX, localY, localZ);
        if (!work.markVisited(idx)) {
            return;
        }

        if (GreedyEligibility.isGreedyOpaqueCube(state, level, worldPos)) {
            work.addEligible(idx, state);
            return;
        }

        work.addNonGreedy(localX, localY, localZ, state);
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void greedyMeshing$compileFastPath(
            SectionPos sectionPos,
            RenderSectionRegion renderSectionRegion,
            VertexSorting vertexSorting,
            SectionBufferBuilderPack sectionBufferBuilderPack,
            CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        GreedyVanillaWorkState work = GREEDY_MESHING$STATE.get();
        try {
            if (!GreedyRuntimeState.isRuntimeGreedyActive()) {
                return;
            }

            SectionCompiler.Results results = cir.getReturnValue();
            if (results == null) {
                return;
            }

            List<NonGreedySolidBlock> nonGreedySolidBlocks = work.nonGreedySolidBlocks();
            if (work.eligibleCount() <= 0 && nonGreedySolidBlocks.isEmpty()) {
                return;
            }

            int baseX = work.baseX();
            int baseY = work.baseY();
            int baseZ = work.baseZ();
            List<GreedyMesher.GreedyQuad> merged;
            if (work.eligibleCount() > 0) {
                populateFaceVisibility(renderSectionRegion, work.sectionStates(), work.visibleFaces(), baseX, baseY, baseZ);
                merged = GreedyMesher.mesh(work.sectionStates(), work.visibleFaces());
            } else {
                merged = List.of();
            }

            BufferBuilder builder = new BufferBuilder(
                    sectionBufferBuilderPack.buffer(ChunkSectionLayer.SOLID),
                    VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.BLOCK
            );
            emitVanillaNonGreedySolids(builder, nonGreedySolidBlocks, renderSectionRegion, baseX, baseY, baseZ);

            boolean captureDebug = GreedyConfig.debugWireframe() || GreedyConfig.debugTrianglesHud();
            boolean mergedQuadsMode = GreedyConfig.experimentalMergedQuads();
            List<GreedyDebugStore.DebugQuad> debugQuads = captureDebug ? new ArrayList<>(merged.size()) : List.of();
            int emittedQuads = 0;
            for (GreedyMesher.GreedyQuad quad : merged) {
                FaceAppearance appearance = greedyMeshing$cachedFaceAppearance(new SpriteKey(quad.state(), quad.face()));
                float u0 = appearance.sprite().getU0();
                float u1 = appearance.sprite().getU1();
                float v0 = appearance.sprite().getV0();
                float v1 = appearance.sprite().getV1();
                if (mergedQuadsMode) {
                    emitMergedQuad(builder, quad, u0, u1, v0, v1, appearance.tinted(), renderSectionRegion, baseX, baseY, baseZ);
                    emittedQuads++;
                } else {
                    emitTiledQuad(builder, quad, u0, u1, v0, v1, appearance.tinted(), renderSectionRegion, baseX, baseY, baseZ);
                    emittedQuads += quad.width() * quad.height();
                }
                if (captureDebug) {
                    debugQuads.add(toDebugQuad(quad, baseX, baseY, baseZ));
                }
            }
            if (!merged.isEmpty()) {
                GreedyPerformanceStats.onGreedySectionBuilt(work.eligibleCount(), merged.size(), emittedQuads);
            }

            SectionCompilerResultsAccessor accessor = (SectionCompilerResultsAccessor) (Object) results;
            Map<ChunkSectionLayer, MeshData> layers = accessor.greedyMeshing$getRenderedLayers();
            MeshData meshData = builder.build();
            if (meshData == null) {
                layers.remove(ChunkSectionLayer.SOLID);
            } else {
                layers.put(ChunkSectionLayer.SOLID, meshData);
            }

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

    private static void emitVanillaNonGreedySolids(
            BufferBuilder builder,
            List<NonGreedySolidBlock> nonGreedySolidBlocks,
            RenderSectionRegion renderSectionRegion,
            int baseX,
            int baseY,
            int baseZ
    ) {
        if (nonGreedySolidBlocks.isEmpty()) {
            return;
        }

        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        PoseStack poseStack = new PoseStack();
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        RandomSource random = RandomSource.create();

        for (NonGreedySolidBlock block : nonGreedySolidBlocks) {
            int worldX = baseX + block.x();
            int worldY = baseY + block.y();
            int worldZ = baseZ + block.z();
            worldPos.set(worldX, worldY, worldZ);

            poseStack.pushPose();
            poseStack.translate(block.x(), block.y(), block.z());
            random.setSeed(block.state().getSeed(worldPos));
            List<BlockModelPart> parts = blockRenderer.getBlockModel(block.state()).collectParts(random);
            blockRenderer.renderBatched(block.state(), worldPos, renderSectionRegion, poseStack, builder, true, parts);
            poseStack.popPose();
        }
    }

    private static FaceAppearance resolveFaceAppearance(BlockStateModel model, Direction face) {
        RandomSource random = RandomSource.create(0L);
        List<BlockModelPart> parts = model.collectParts(random);

        for (BlockModelPart part : parts) {
            List<BakedQuad> quads = part.getQuads(face);
            if (!quads.isEmpty()) {
                BakedQuad quad = quads.get(0);
                return new FaceAppearance(quad.sprite(), quad.isTinted());
            }
        }

        for (BlockModelPart part : parts) {
            List<BakedQuad> unculled = part.getQuads(null);
            for (BakedQuad quad : unculled) {
                if (quad.direction() == face) {
                    return new FaceAppearance(quad.sprite(), quad.isTinted());
                }
            }
        }

        if (!parts.isEmpty()) {
            return new FaceAppearance(parts.get(0).particleIcon(), false);
        }

        return new FaceAppearance(model.particleIcon(), false);
    }

    @Unique
    private static FaceAppearance greedyMeshing$cachedFaceAppearance(SpriteKey key) {
        return GREEDY_MESHING$FACE_CACHE.computeIfAbsent(key, k -> {
            BlockStateModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(k.state());
            return resolveFaceAppearance(model, k.face());
        });
    }

    @Unique
    private static void populateFaceVisibility(
            RenderSectionRegion region,
            BlockState[] sectionStates,
            BitSet[] visibleFaces,
            int baseX,
            int baseY,
            int baseZ
    ) {
        GreedyMesher.clearFaceMaskArray(visibleFaces);
        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < GreedyMesher.SECTION_SIZE; y++) {
            for (int z = 0; z < GreedyMesher.SECTION_SIZE; z++) {
                for (int x = 0; x < GreedyMesher.SECTION_SIZE; x++) {
                    int idx = GreedyMesher.index(x, y, z);
                    BlockState state = sectionStates[idx];
                    if (state == null) {
                        continue;
                    }

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
                        if (Block.shouldRenderFace(state, neighbor, face)) {
                            visibleFaces[face.ordinal()].set(idx);
                        }
                    }
                }
            }
        }
    }

    private static void emitTiledQuad(
            VertexConsumer consumer,
            GreedyMesher.GreedyQuad quad,
            float u0,
            float u1,
            float v0,
            float v1,
            boolean applyTint,
            RenderSectionRegion region,
            int baseX,
            int baseY,
            int baseZ
    ) {
        float[] full = corners(quad);
        int tilesU = Math.max(1, quad.width());
        int tilesV = Math.max(1, quad.height());
        BlockPos.MutableBlockPos tintPos = new BlockPos.MutableBlockPos();
        GreedyLighting.Scratch lighting = new GreedyLighting.Scratch();
        float[] c = new float[12];
        BlockColors blockColors = applyTint ? Minecraft.getInstance().getBlockColors() : null;

        for (int tv = 0; tv < tilesV; tv++) {
            for (int tu = 0; tu < tilesU; tu++) {
                float fu0 = (float) tu / (float) tilesU;
                float fu1 = (float) (tu + 1) / (float) tilesU;
                float fv0 = (float) tv / (float) tilesV;
                float fv1 = (float) (tv + 1) / (float) tilesV;

                c[0] = interpolate(full, fu0, fv0, 0);
                c[1] = interpolate(full, fu0, fv0, 1);
                c[2] = interpolate(full, fu0, fv0, 2);
                c[3] = interpolate(full, fu1, fv0, 0);
                c[4] = interpolate(full, fu1, fv0, 1);
                c[5] = interpolate(full, fu1, fv0, 2);
                c[6] = interpolate(full, fu1, fv1, 0);
                c[7] = interpolate(full, fu1, fv1, 1);
                c[8] = interpolate(full, fu1, fv1, 2);
                c[9] = interpolate(full, fu0, fv1, 0);
                c[10] = interpolate(full, fu0, fv1, 1);
                c[11] = interpolate(full, fu0, fv1, 2);

                int worldX = tileBlockCoord(baseX, c, 0, quad.face().getStepX());
                int worldY = tileBlockCoord(baseY, c, 1, quad.face().getStepY());
                int worldZ = tileBlockCoord(baseZ, c, 2, quad.face().getStepZ());

                int tint = applyTint ? tintColorForTile(quad.state(), region, worldX, worldY, worldZ, tintPos, blockColors) : 0xFFFFFF;
                float tintR = ((tint >> 16) & 0xFF) / 255.0f;
                float tintG = ((tint >> 8) & 0xFF) / 255.0f;
                float tintB = (tint & 0xFF) / 255.0f;

                emitOneQuad(
                        consumer,
                        c,
                        quad.state(),
                        quad.face(),
                        u0,
                        u1,
                        v0,
                        v1,
                        region,
                        worldX,
                        worldY,
                        worldZ,
                        lighting,
                        tintR,
                        tintG,
                        tintB
                );
            }
        }
    }

    private static void emitMergedQuad(
            VertexConsumer consumer,
            GreedyMesher.GreedyQuad quad,
            float u0,
            float u1,
            float v0,
            float v1,
            boolean applyTint,
            RenderSectionRegion region,
            int baseX,
            int baseY,
            int baseZ
    ) {
        float[] full = corners(quad);
        GreedyLighting.Scratch lighting = new GreedyLighting.Scratch();
        BlockColors blockColors = applyTint ? Minecraft.getInstance().getBlockColors() : null;
        BlockPos.MutableBlockPos tintPos = new BlockPos.MutableBlockPos();

        int worldX = tileBlockCoord(baseX, full, 0, quad.face().getStepX());
        int worldY = tileBlockCoord(baseY, full, 1, quad.face().getStepY());
        int worldZ = tileBlockCoord(baseZ, full, 2, quad.face().getStepZ());
        int tint = applyTint ? tintColorForTile(quad.state(), region, worldX, worldY, worldZ, tintPos, blockColors) : 0xFFFFFF;
        float tintR = ((tint >> 16) & 0xFF) / 255.0f;
        float tintG = ((tint >> 8) & 0xFF) / 255.0f;
        float tintB = (tint & 0xFF) / 255.0f;

        emitOneQuad(
                consumer,
                full,
                quad.state(),
                quad.face(),
                u0,
                u1,
                v0,
                v1,
                region,
                worldX,
                worldY,
                worldZ,
                lighting,
                tintR,
                tintG,
                tintB
        );
    }

    private static int tintColorForTile(
            BlockState state,
            RenderSectionRegion region,
            int worldX,
            int worldY,
            int worldZ,
            BlockPos.MutableBlockPos samplePos,
            BlockColors blockColors
    ) {
        samplePos.set(worldX, worldY, worldZ);
        int tint = blockColors.getColor(state, region, samplePos, 0);
        return tint == -1 ? 0xFFFFFF : tint;
    }

    private static float interpolate(float[] c, float fu, float fv, int axis) {
        float c0 = c[axis];
        float c1 = c[3 + axis];
        float c2 = c[6 + axis];
        float c3 = c[9 + axis];
        return c0 * (1.0f - fu) * (1.0f - fv)
                + c1 * fu * (1.0f - fv)
                + c2 * fu * fv
                + c3 * (1.0f - fu) * fv;
    }

    private static int tileBlockCoord(int base, float[] c, int axis, int faceStep) {
        float center = (c[axis] + c[3 + axis] + c[6 + axis] + c[9 + axis]) * 0.25f;
        return (int) Math.floor(base + center - 0.5f * faceStep);
    }

    private static void emitOneQuad(
            VertexConsumer consumer,
            float[] c,
            BlockState state,
            Direction face,
            float u0,
            float u1,
            float v0,
            float v1,
            RenderSectionRegion region,
            int worldX,
            int worldY,
            int worldZ,
            GreedyLighting.Scratch lighting,
            float tintR,
            float tintG,
            float tintB
    ) {
        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();
        GreedyLighting.computeTileLighting(region, state, face, worldX, worldY, worldZ, lighting);
        emitVertex(consumer, c[0], c[1], c[2], u0, v0, nx, ny, nz, lighting.lightmap[0], lighting.brightness[0], tintR, tintG, tintB);
        emitVertex(consumer, c[3], c[4], c[5], u1, v0, nx, ny, nz, lighting.lightmap[1], lighting.brightness[1], tintR, tintG, tintB);
        emitVertex(consumer, c[6], c[7], c[8], u1, v1, nx, ny, nz, lighting.lightmap[2], lighting.brightness[2], tintR, tintG, tintB);
        emitVertex(consumer, c[9], c[10], c[11], u0, v1, nx, ny, nz, lighting.lightmap[3], lighting.brightness[3], tintR, tintG, tintB);
    }

    private static void emitVertex(
            VertexConsumer consumer,
            float x,
            float y,
            float z,
            float u,
            float v,
            float nx,
            float ny,
            float nz,
            int packedLight,
            float brightness,
            float tintR,
            float tintG,
            float tintB
    ) {
        consumer.addVertex(x, y, z)
                .setColor(brightness * tintR, brightness * tintG, brightness * tintB, 1.0f)
                .setUv(u, v)
                .setLight(packedLight)
                .setNormal(nx, ny, nz);
    }

    private static float[] corners(GreedyMesher.GreedyQuad quad) {
        float x0 = quad.x();
        float y0 = quad.y();
        float z0 = quad.z();
        float x1;
        float y1;
        float z1;

        return switch (quad.face()) {
            case NORTH -> {
                x1 = x0 + quad.width();
                y1 = y0 + quad.height();
                z1 = z0;
                yield new float[]{x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1};
            }
            case SOUTH -> {
                x1 = x0 + quad.width();
                y1 = y0 + quad.height();
                z1 = z0 + 1.0f;
                yield new float[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1};
            }
            case WEST -> {
                y1 = y0 + quad.height();
                z1 = z0 + quad.width();
                x1 = x0;
                yield new float[]{x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0};
            }
            case EAST -> {
                y1 = y0 + quad.height();
                z1 = z0 + quad.width();
                x1 = x0 + 1.0f;
                yield new float[]{x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1};
            }
            case DOWN -> {
                x1 = x0 + quad.width();
                z1 = z0 + quad.height();
                y1 = y0;
                yield new float[]{x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1};
            }
            case UP -> {
                x1 = x0 + quad.width();
                z1 = z0 + quad.height();
                y1 = y0 + 1.0f;
                yield new float[]{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0};
            }
        };
    }

    private record SpriteKey(BlockState state, Direction face) {
    }

    private record NonGreedySolidBlock(int x, int y, int z, BlockState state) {
    }

    private record FaceAppearance(TextureAtlasSprite sprite, boolean tinted) {
    }

    private static final class GreedyVanillaWorkState {
        private final BlockState[] sectionStates = new BlockState[GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE];
        private final BitSet[] visibleFaces = GreedyMesher.createFaceMaskArray();
        private final List<NonGreedySolidBlock> nonGreedySolidBlocks = new ArrayList<>();
        private final BitSet visitedSolid = new BitSet(GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE);
        private int baseX;
        private int baseY;
        private int baseZ;
        private int eligibleCount;
        private boolean initialized;

        void reset(SectionPos sectionPos) {
            Arrays.fill(sectionStates, null);
            GreedyMesher.clearFaceMaskArray(visibleFaces);
            nonGreedySolidBlocks.clear();
            visitedSolid.clear();
            baseX = sectionPos.minBlockX();
            baseY = sectionPos.minBlockY();
            baseZ = sectionPos.minBlockZ();
            eligibleCount = 0;
            initialized = true;
        }

        boolean initialized() {
            return initialized;
        }

        int baseX() {
            return baseX;
        }

        int baseY() {
            return baseY;
        }

        int baseZ() {
            return baseZ;
        }

        BlockState[] sectionStates() {
            return sectionStates;
        }

        BitSet[] visibleFaces() {
            return visibleFaces;
        }

        List<NonGreedySolidBlock> nonGreedySolidBlocks() {
            return nonGreedySolidBlocks;
        }

        int eligibleCount() {
            return eligibleCount;
        }

        boolean markVisited(int idx) {
            if (visitedSolid.get(idx)) {
                return false;
            }
            visitedSolid.set(idx);
            return true;
        }

        void addEligible(int idx, BlockState state) {
            if (sectionStates[idx] == null) {
                eligibleCount++;
            }
            sectionStates[idx] = state;
        }

        void addNonGreedy(int x, int y, int z, BlockState state) {
            nonGreedySolidBlocks.add(new NonGreedySolidBlock(x, y, z, state));
        }
    }
}
