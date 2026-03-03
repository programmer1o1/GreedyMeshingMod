package org.Jean.greedy_meshing.mixin.client.sodium;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.Jean.greedy_meshing.GreedyEligibility;
import org.Jean.greedy_meshing.GreedyMesher;
import org.Jean.greedy_meshing.client.GreedyDebugStore;
import org.Jean.greedy_meshing.client.GreedyLighting;
import org.Jean.greedy_meshing.client.GreedyRuntimeState;
import org.Jean.greedy_meshing.client.sodium.GreedySodiumSpriteKey;
import org.Jean.greedy_meshing.client.sodium.GreedySodiumWorkState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(ChunkBuilderMeshingTask.class)
public abstract class SodiumChunkBuilderMeshingTaskMixin {
    @Unique
    private static final ThreadLocal<GreedySodiumWorkState> GREEDY_MESHING$STATE = ThreadLocal.withInitial(GreedySodiumWorkState::new);

    @Inject(method = "execute", at = @At("HEAD"))
    private void greedyMeshing$beginSodiumTask(
            ChunkBuildContext buildContext,
            CancellationToken cancellationToken,
            CallbackInfoReturnable<ChunkBuildOutput> cir
    ) {
        RenderSection render = ((SodiumChunkBuilderTaskAccessor) (Object) this).greedyMeshing$getRender();
        GreedySodiumWorkState work = GREEDY_MESHING$STATE.get();
        work.reset(buildContext);
        work.sectionOrigin(new BlockPos(render.getOriginX(), render.getOriginY(), render.getOriginZ()));
        work.sectionKey(SectionPos.asLong(render.getChunkX(), render.getChunkY(), render.getChunkZ()));
        GreedyDebugStore.clearSection(work.sectionKey());
        work.world(getWorldSlice(buildContext));
    }

    @Inject(method = "execute", at = @At("RETURN"))
    private void greedyMeshing$endSodiumTask(
            ChunkBuildContext buildContext,
            CancellationToken cancellationToken,
            CallbackInfoReturnable<ChunkBuildOutput> cir
    ) {
        GREEDY_MESHING$STATE.remove();
    }

    @Redirect(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;renderModel(Lnet/minecraft/client/renderer/block/model/BlockStateModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V"
            )
    )
    private void greedyMeshing$redirectSodiumRenderModel(
            BlockRenderer renderer,
            BlockStateModel model,
            BlockState state,
            BlockPos worldPos,
            BlockPos modelOffset
    ) {
        if (!GreedyRuntimeState.isRuntimeGreedyActive()) {
            renderer.renderModel(model, state, worldPos, modelOffset);
            return;
        }

        GreedySodiumWorkState work = GREEDY_MESHING$STATE.get();
        work.blockRenderer(renderer);
        if (work.world() == null) {
            work.world(getWorldSlice(work.buildContext()));
        }

        if (work.world() == null || work.sectionOrigin() == null) {
            renderer.renderModel(model, state, worldPos, modelOffset);
            return;
        }

        if (!GreedyEligibility.isGreedyOpaqueCube(state, work.world(), worldPos)) {
            renderer.renderModel(model, state, worldPos, modelOffset);
            return;
        }

        int localX = modelOffset.getX();
        int localY = modelOffset.getY();
        int localZ = modelOffset.getZ();
        if (localX < 0 || localX >= 16 || localY < 0 || localY >= 16 || localZ < 0 || localZ >= 16) {
            renderer.renderModel(model, state, worldPos, modelOffset);
            return;
        }
        int idx = GreedyMesher.index(localX, localY, localZ);
        if (work.sectionStates()[idx] == null) {
            work.incrementEligibleCount();
        }
        work.sectionStates()[idx] = state;
    }

    @Inject(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;release()V"
            )
    )
    private void greedyMeshing$emitGreedyBeforeSodiumMeshBuild(
            ChunkBuildContext buildContext,
            CancellationToken cancellationToken,
            CallbackInfoReturnable<ChunkBuildOutput> cir
    ) {
        if (!GreedyRuntimeState.isRuntimeGreedyActive()) {
            return;
        }

        GreedySodiumWorkState work = GREEDY_MESHING$STATE.get();
        if (work.eligibleCount() <= 0 || work.sectionOrigin() == null || work.blockRenderer() == null) {
            return;
        }

        if (work.world() == null) {
            work.world(getWorldSlice(buildContext));
            if (work.world() == null) {
                return;
            }
        }

        ChunkBuildBuffers buffers = ((SodiumChunkBuildContextAccessor) (Object) buildContext).greedyMeshing$getBuffers();
        TranslucentGeometryCollector collector = ((SodiumBlockRendererAccessor) (Object) work.blockRenderer()).greedyMeshing$getCollector();
        ChunkModelBuilder modelBuilder = buffers.get(DefaultMaterials.SOLID);
        VertexConsumer consumer = modelBuilder.asFallbackVertexConsumer(DefaultMaterials.SOLID, collector);
        if (consumer == null) {
            return;
        }

        int baseX = work.sectionOrigin().getX();
        int baseY = work.sectionOrigin().getY();
        int baseZ = work.sectionOrigin().getZ();
        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();

        List<GreedyMesher.GreedyQuad> merged = GreedyMesher.mesh(work.sectionStates(), (x, y, z, face, state) -> {
            samplePos.set(baseX + x + face.getStepX(), baseY + y + face.getStepY(), baseZ + z + face.getStepZ());
            BlockState neighbor = work.world().getBlockState(samplePos);
            return Block.shouldRenderFace(state, neighbor, face);
        });

        Map<GreedySodiumSpriteKey, FaceAppearance> spriteCache = new HashMap<>();
        List<GreedyDebugStore.DebugQuad> debugQuads = work.captureDebug() ? new ArrayList<>(merged.size()) : List.of();
        for (GreedyMesher.GreedyQuad quad : merged) {
            FaceAppearance appearance = spriteCache.computeIfAbsent(new GreedySodiumSpriteKey(quad.state(), quad.face()), key -> {
                BlockStateModel quadModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(key.state());
                return resolveFaceAppearance(quadModel, key.face());
            });
            emitTiledQuad(
                    consumer,
                    quad,
                    appearance.sprite().getU0(),
                    appearance.sprite().getU1(),
                    appearance.sprite().getV0(),
                    appearance.sprite().getV1(),
                    appearance.tinted(),
                    work.world(),
                    baseX,
                    baseY,
                    baseZ
            );
            if (work.captureDebug()) {
                debugQuads.add(toDebugQuad(quad, baseX, baseY, baseZ));
            }
        }

        if (work.captureDebug()) {
            GreedyDebugStore.setSectionQuads(work.sectionKey(), debugQuads);
        }
    }

    @Unique
    private static BlockAndTintGetter getWorldSlice(ChunkBuildContext context) {
        BlockRenderCache cache = ((SodiumChunkBuildContextAccessor) (Object) context).greedyMeshing$getCache();
        return cache.getWorldSlice();
    }

    @Unique
    private static GreedyDebugStore.DebugQuad toDebugQuad(GreedyMesher.GreedyQuad quad, int baseX, int baseY, int baseZ) {
        float[] c = corners(quad);
        return new GreedyDebugStore.DebugQuad(
                c[0] + baseX, c[1] + baseY, c[2] + baseZ,
                c[3] + baseX, c[4] + baseY, c[5] + baseZ,
                c[6] + baseX, c[7] + baseY, c[8] + baseZ,
                c[9] + baseX, c[10] + baseY, c[11] + baseZ
        );
    }

    @Unique
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
            List<BakedQuad> quads = part.getQuads(null);
            for (BakedQuad quad : quads) {
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
    private static void emitTiledQuad(
            VertexConsumer consumer,
            GreedyMesher.GreedyQuad quad,
            float u0,
            float u1,
            float v0,
            float v1,
            boolean applyTint,
            BlockAndTintGetter world,
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

                int tint = applyTint ? tintColorForTile(quad.state(), world, worldX, worldY, worldZ, tintPos, blockColors) : 0xFFFFFF;
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
                        world,
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

    @Unique
    private static int tintColorForTile(
            BlockState state,
            BlockAndTintGetter world,
            int worldX,
            int worldY,
            int worldZ,
            BlockPos.MutableBlockPos samplePos,
            BlockColors blockColors
    ) {
        samplePos.set(worldX, worldY, worldZ);
        int tint = blockColors.getColor(state, world, samplePos, 0);
        return tint == -1 ? 0xFFFFFF : tint;
    }

    @Unique
    private static void emitOneQuad(
            VertexConsumer consumer,
            float[] c,
            BlockState state,
            Direction face,
            float u0,
            float u1,
            float v0,
            float v1,
            BlockAndTintGetter world,
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
        GreedyLighting.computeTileLighting(world, state, face, worldX, worldY, worldZ, lighting);

        // Side faces use the opposite V direction to keep textures upright (e.g. grass side overlay).
        boolean flipV = face.getAxis().isHorizontal();
        float vv0 = flipV ? v1 : v0;
        float vv1 = flipV ? v0 : v1;

        emitVertex(consumer, c[0], c[1], c[2], u0, vv0, nx, ny, nz, lighting.lightmap[0], lighting.brightness[0], tintR, tintG, tintB);
        emitVertex(consumer, c[3], c[4], c[5], u1, vv0, nx, ny, nz, lighting.lightmap[1], lighting.brightness[1], tintR, tintG, tintB);
        emitVertex(consumer, c[6], c[7], c[8], u1, vv1, nx, ny, nz, lighting.lightmap[2], lighting.brightness[2], tintR, tintG, tintB);
        emitVertex(consumer, c[9], c[10], c[11], u0, vv1, nx, ny, nz, lighting.lightmap[3], lighting.brightness[3], tintR, tintG, tintB);
    }

    @Unique
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

    @Unique
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

    @Unique
    private static int tileBlockCoord(int base, float[] c, int axis, int faceStep) {
        float center = (c[axis] + c[3 + axis] + c[6 + axis] + c[9 + axis]) * 0.25f;
        return (int) Math.floor(base + center - 0.5f * faceStep);
    }

    @Unique
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

    @Unique
    private record FaceAppearance(TextureAtlasSprite sprite, boolean tinted) {
    }

}
