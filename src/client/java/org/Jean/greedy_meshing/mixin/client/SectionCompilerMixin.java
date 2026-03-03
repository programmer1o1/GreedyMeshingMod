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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.Jean.greedy_meshing.GreedyConfig;
import org.Jean.greedy_meshing.GreedyEligibility;
import org.Jean.greedy_meshing.GreedyMesher;
import org.Jean.greedy_meshing.client.GreedyDebugStore;
import org.Jean.greedy_meshing.client.GreedyLighting;
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

        Map<SpriteKey, FaceAppearance> spriteCache = new HashMap<>();
        List<GreedyDebugStore.DebugQuad> debugQuads = captureDebug ? new ArrayList<>(merged.size()) : List.of();
        for (GreedyMesher.GreedyQuad quad : merged) {
            FaceAppearance appearance = spriteCache.computeIfAbsent(new SpriteKey(quad.state(), quad.face()), key -> {
                BlockStateModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(key.state());
                return resolveFaceAppearance(model, key.face());
            });
            float u0 = appearance.sprite().getU0();
            float u1 = appearance.sprite().getU1();
            float v0 = appearance.sprite().getV0();
            float v1 = appearance.sprite().getV1();
            emitTiledQuad(builder, quad, u0, u1, v0, v1, appearance.tinted(), renderSectionRegion, baseX, baseY, baseZ);
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
}
