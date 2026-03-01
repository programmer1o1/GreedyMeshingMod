package org.Jean.greedy_meshing.mixin.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.Jean.greedy_meshing.GreedyConfig;
import org.Jean.greedy_meshing.GreedyEligibility;
import org.Jean.greedy_meshing.GreedyMesher;
import org.Jean.greedy_meshing.client.GreedyDebugStore;
import org.Jean.greedy_meshing.client.GreedyRuntimeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    @Inject(method = "compile", at = @At("RETURN"))
    private void greedyMeshing$compileFastPath(
            SectionPos sectionPos,
            RenderSectionRegion renderSectionRegion,
            VertexSorting vertexSorting,
            SectionBufferBuilderPack sectionBufferBuilderPack,
            CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        GreedyDebugStore.clearSection(sectionPos.asLong());
        boolean captureDebug = GreedyConfig.debugWireframe() || GreedyConfig.debugTrianglesHud();

        // Vanilla smooth-lighting parity mode:
        // custom greedy output is disabled when AO/smooth lighting is active.
        if (!GreedyRuntimeState.isRuntimeGreedyActive()) {
            return;
        }

        SectionCompiler.Results results = cir.getReturnValue();
        if (results == null) {
            return;
        }

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        BlockState[] sectionStates = new BlockState[GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE];
        List<NonGreedySolidBlock> nonGreedySolidBlocks = new ArrayList<>();
        int eligibleCount = 0;

        int baseX = sectionPos.minBlockX();
        int baseY = sectionPos.minBlockY();
        int baseZ = sectionPos.minBlockZ();

        for (int y = 0; y < GreedyMesher.SECTION_SIZE; y++) {
            for (int z = 0; z < GreedyMesher.SECTION_SIZE; z++) {
                for (int x = 0; x < GreedyMesher.SECTION_SIZE; x++) {
                    worldPos.set(baseX + x, baseY + y, baseZ + z);
                    BlockState state = renderSectionRegion.getBlockState(worldPos);
                    if (state.isAir()) {
                        continue;
                    }

                    if (ItemBlockRenderTypes.getChunkRenderType(state) != ChunkSectionLayer.SOLID) {
                        continue;
                    }

                    if (!GreedyEligibility.isGreedyOpaqueCube(state, renderSectionRegion, worldPos)) {
                        nonGreedySolidBlocks.add(new NonGreedySolidBlock(x, y, z, state));
                        continue;
                    }

                    sectionStates[GreedyMesher.index(x, y, z)] = state;
                    eligibleCount++;
                }
            }
        }

        if (eligibleCount == 0) {
            return;
        }

        List<GreedyMesher.GreedyQuad> merged = GreedyMesher.mesh(sectionStates, (x, y, z, face, state) -> {
            worldPos.set(baseX + x + face.getStepX(), baseY + y + face.getStepY(), baseZ + z + face.getStepZ());
            BlockState neighbor = renderSectionRegion.getBlockState(worldPos);
            return Block.shouldRenderFace(state, neighbor, face);
        });

        BufferBuilder builder = new BufferBuilder(
                sectionBufferBuilderPack.buffer(ChunkSectionLayer.SOLID),
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.BLOCK
        );
        emitVanillaNonGreedySolids(builder, nonGreedySolidBlocks, renderSectionRegion, baseX, baseY, baseZ);

        Map<SpriteKey, TextureAtlasSprite> spriteCache = new HashMap<>();
        List<GreedyDebugStore.DebugQuad> debugQuads = captureDebug ? new ArrayList<>(merged.size()) : List.of();
        for (GreedyMesher.GreedyQuad quad : merged) {
            TextureAtlasSprite sprite = spriteCache.computeIfAbsent(new SpriteKey(quad.state(), quad.face()), key -> {
                BlockStateModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(key.state());
                return resolveFaceSprite(model, key.face());
            });
            float u0 = sprite.getU0();
            float u1 = sprite.getU1();
            float v0 = sprite.getV0();
            float v1 = sprite.getV1();
            emitTiledQuad(builder, quad, u0, u1, v0, v1, renderSectionRegion, baseX, baseY, baseZ);
            if (captureDebug) {
                debugQuads.add(toDebugQuad(quad, baseX, baseY, baseZ));
            }
        }

        MeshData meshData = builder.build();
        if (meshData == null) {
            return;
        }

        SectionCompilerResultsAccessor accessor = (SectionCompilerResultsAccessor) (Object) results;
        Map<ChunkSectionLayer, MeshData> layers = accessor.greedyMeshing$getRenderedLayers();
        layers.put(ChunkSectionLayer.SOLID, meshData);

        if (captureDebug) {
            GreedyDebugStore.setSectionQuads(sectionPos.asLong(), debugQuads);
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

    private static TextureAtlasSprite resolveFaceSprite(BlockStateModel model, Direction face) {
        RandomSource random = RandomSource.create(0L);
        List<BlockModelPart> parts = model.collectParts(random);

        for (BlockModelPart part : parts) {
            List<BakedQuad> quads = part.getQuads(face);
            if (!quads.isEmpty()) {
                return quads.get(0).sprite();
            }
        }

        for (BlockModelPart part : parts) {
            List<BakedQuad> unculled = part.getQuads(null);
            for (BakedQuad quad : unculled) {
                if (quad.direction() == face) {
                    return quad.sprite();
                }
            }
        }

        if (!parts.isEmpty()) {
            return parts.get(0).particleIcon();
        }

        return model.particleIcon();
    }

    private static void emitTiledQuad(
            VertexConsumer consumer,
            GreedyMesher.GreedyQuad quad,
            float u0,
            float u1,
            float v0,
            float v1,
            RenderSectionRegion region,
            int baseX,
            int baseY,
            int baseZ
    ) {
        float[] full = corners(quad);
        int tilesU = Math.max(1, quad.width());
        int tilesV = Math.max(1, quad.height());
        BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();

        for (int tv = 0; tv < tilesV; tv++) {
            for (int tu = 0; tu < tilesU; tu++) {
                float fu0 = (float) tu / (float) tilesU;
                float fu1 = (float) (tu + 1) / (float) tilesU;
                float fv0 = (float) tv / (float) tilesV;
                float fv1 = (float) (tv + 1) / (float) tilesV;

                float[] c = new float[]{
                        interpolate(full, fu0, fv0, 0), interpolate(full, fu0, fv0, 1), interpolate(full, fu0, fv0, 2),
                        interpolate(full, fu1, fv0, 0), interpolate(full, fu1, fv0, 1), interpolate(full, fu1, fv0, 2),
                        interpolate(full, fu1, fv1, 0), interpolate(full, fu1, fv1, 1), interpolate(full, fu1, fv1, 2),
                        interpolate(full, fu0, fv1, 0), interpolate(full, fu0, fv1, 1), interpolate(full, fu0, fv1, 2)
                };

                emitOneQuad(consumer, c, quad.face(), u0, u1, v0, v1, region, baseX, baseY, baseZ, lightPos);
            }
        }
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

    private static void emitOneQuad(
            VertexConsumer consumer,
            float[] c,
            Direction face,
            float u0,
            float u1,
            float v0,
            float v1,
            RenderSectionRegion region,
            int baseX,
            int baseY,
            int baseZ,
            BlockPos.MutableBlockPos lightPos
    ) {
        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();
        float baseShade = region.getShade(face, true);
        int packedLight = packedLightAtQuadCenter(region, baseX, baseY, baseZ, c, face, lightPos);
        emitVertex(consumer, c[0], c[1], c[2], u0, v0, nx, ny, nz, packedLight, baseShade);
        emitVertex(consumer, c[3], c[4], c[5], u1, v0, nx, ny, nz, packedLight, baseShade);
        emitVertex(consumer, c[6], c[7], c[8], u1, v1, nx, ny, nz, packedLight, baseShade);
        emitVertex(consumer, c[9], c[10], c[11], u0, v1, nx, ny, nz, packedLight, baseShade);
    }

    private static void emitVertex(VertexConsumer consumer, float x, float y, float z, float u, float v, float nx, float ny, float nz, int packedLight, float shade) {
        consumer.addVertex(x, y, z)
                .setColor(shade, shade, shade, 1.0f)
                .setUv(u, v)
                .setLight(packedLight)
                .setNormal(nx, ny, nz);
    }

    private static int packedLightAtVertex(
            RenderSectionRegion region,
            int baseX,
            int baseY,
            int baseZ,
            float localX,
            float localY,
            float localZ,
            Direction face,
            BlockPos.MutableBlockPos samplePos
    ) {
        int sampleX = (int) Math.floor(baseX + localX + 0.5f * face.getStepX());
        int sampleY = (int) Math.floor(baseY + localY + 0.5f * face.getStepY());
        int sampleZ = (int) Math.floor(baseZ + localZ + 0.5f * face.getStepZ());
        samplePos.set(sampleX, sampleY, sampleZ);
        return LevelRenderer.getLightColor(region, samplePos);
    }

    private static int packedLightAtQuadCenter(
            RenderSectionRegion region,
            int baseX,
            int baseY,
            int baseZ,
            float[] c,
            Direction face,
            BlockPos.MutableBlockPos samplePos
    ) {
        float localX = (c[0] + c[3] + c[6] + c[9]) * 0.25f;
        float localY = (c[1] + c[4] + c[7] + c[10]) * 0.25f;
        float localZ = (c[2] + c[5] + c[8] + c[11]) * 0.25f;
        return packedLightAtVertex(region, baseX, baseY, baseZ, localX, localY, localZ, face, samplePos);
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
}
