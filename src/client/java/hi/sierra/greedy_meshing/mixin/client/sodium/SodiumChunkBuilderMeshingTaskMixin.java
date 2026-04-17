package hi.sierra.greedy_meshing.mixin.client.sodium;

//? if SODIUM {
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
//? if UNOBFUSCATED {
/*import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
*///?} else {
import net.minecraft.client.renderer.block.model.BakedQuad;
//? if >=1.21.5 {
/*import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
*///?} else {
import net.minecraft.client.resources.model.BakedModel;
//?}
import net.minecraft.world.level.BlockAndTintGetter;
//?}
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import hi.sierra.greedy_meshing.GreedyEligibility;
import hi.sierra.greedy_meshing.GreedyMesher;
import hi.sierra.greedy_meshing.client.GreedyDebugStore;
import hi.sierra.greedy_meshing.client.GreedyLighting;
import hi.sierra.greedy_meshing.client.GreedyPerformanceStats;
import hi.sierra.greedy_meshing.client.GreedyRuntimeState;
import hi.sierra.greedy_meshing.client.sodium.GreedySodiumWorkState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

@Mixin(ChunkBuilderMeshingTask.class)
public abstract class SodiumChunkBuilderMeshingTaskMixin {
    @Unique
    private static final ThreadLocal<GreedySodiumWorkState> GREEDY_MESHING$STATE = ThreadLocal.withInitial(GreedySodiumWorkState::new);
    @Unique
    private static final Direction[] GREEDY_MESHING$FACES = Direction.values();
    @Unique
    private static final ThreadLocal<BitSet[]> GREEDY_MESHING$VISIBLE = ThreadLocal.withInitial(GreedyMesher::createFaceMaskArray);
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
        GreedyMesher.clearFaceMaskArray(GREEDY_MESHING$VISIBLE.get());
        GreedyPerformanceStats.onSodiumTaskHook();
    }

    @Inject(method = "execute", at = @At("RETURN"))
    private void greedyMeshing$endSodiumTask(
            ChunkBuildContext buildContext,
            CancellationToken cancellationToken,
            CallbackInfoReturnable<ChunkBuildOutput> cir
    ) {
        GREEDY_MESHING$STATE.remove();
    }

    //? if UNOBFUSCATED {
    /*@Redirect(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;renderModel(Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V"
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
    *///?} else if >=1.21.5 {
    /*@Redirect(
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
    *///?} else {
    @Redirect(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;renderModel(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V"
            )
    )
    private void greedyMeshing$redirectSodiumRenderModel(
            BlockRenderer renderer,
            BakedModel model,
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
    //?}

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

        int baseX = work.sectionOrigin().getX();
        int baseY = work.sectionOrigin().getY();
        int baseZ = work.sectionOrigin().getZ();
        BitSet[] visibleFaces = GREEDY_MESHING$VISIBLE.get();
        long[][] mergeKeys = work.faceMergeKeys();
        populateFaceVisibility(work.world(), work.sectionStates(), visibleFaces, mergeKeys, baseX, baseY, baseZ);
        List<GreedyMesher.GreedyQuad> merged = GreedyMesher.meshWithKeys(
                work.sectionStates(), visibleFaces, mergeKeys
        );

        List<GreedyDebugStore.DebugQuad> debugQuads = work.captureDebug() ? new ArrayList<>(merged.size()) : List.of();
        int emittedQuads = 0;
        int vanillaEquivalent = 0;
        boolean shaderPackActive = GreedyRuntimeState.isShaderPackActive();
        for (GreedyMesher.GreedyQuad quad : merged) {
            List<FaceAppearance> layers = greedyMeshing$resolveFaceLayers(quad.state(), quad.face());
            int blockFaces = quad.width() * quad.height();
            for (FaceAppearance layer : layers) {
                // Get the correct Sodium material/buffer for this block type
                //? if UNOBFUSCATED {
                /*net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material material = DefaultMaterials.forChunkLayer(layer.layer());
                *///?} else {
                net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material material = DefaultMaterials.forBlockState(quad.state());
                //?}
                ChunkModelBuilder modelBuilder = buffers.get(material);
                VertexConsumer consumer = modelBuilder.asFallbackVertexConsumer(material, collector);
                if (consumer == null) continue;

                if (shaderPackActive) {
                    emittedQuads += emitTiledQuads(consumer, quad,
                            layer.sprite().getU0(), layer.sprite().getU1(),
                            layer.sprite().getV0(), layer.sprite().getV1(),
                            layer.tinted(), layer.tintIndex(), work.world(), baseX, baseY, baseZ, work);
                } else {
                    emittedQuads += emitMergedQuad(consumer, quad,
                            layer.sprite().getU0(), layer.sprite().getU1(),
                            layer.sprite().getV0(), layer.sprite().getV1(),
                            layer.tinted(), layer.tintIndex(), work.world(), baseX, baseY, baseZ, work);
                }
                vanillaEquivalent += blockFaces;
            }
            if (work.captureDebug()) {
                debugQuads.add(toDebugQuad(quad, baseX, baseY, baseZ));
            }
        }
        if (!merged.isEmpty()) {
            GreedyPerformanceStats.onGreedySectionBuilt(work.eligibleCount(), merged.size(), emittedQuads, vanillaEquivalent);
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
    private static List<FaceAppearance> greedyMeshing$resolveFaceLayers(BlockState state, Direction face) {
        //? if UNOBFUSCATED {
        /*BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        RandomSource random = RandomSource.create(0L);
        List<BlockStateModelPart> parts = new java.util.ArrayList<>();
        model.collectParts(random, parts);
        List<FaceAppearance> layers = new java.util.ArrayList<>();
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
        List<FaceAppearance> layers = new java.util.ArrayList<>();
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
        List<FaceAppearance> layers = new java.util.ArrayList<>();
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
            BlockAndTintGetter world,
            BlockState[] sectionStates,
            BitSet[] visibleFaces,
            long[][] faceMergeKeys,
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
                        BlockState neighbor = world.getBlockState(samplePos);
                        //? if >=1.21.2 {
                        /*if (Block.shouldRenderFace(state, neighbor, face)) {
                        *///?} else {
                        if (Block.shouldRenderFace(state, world, new BlockPos(worldX, worldY, worldZ), face, samplePos)) {
                        //?}
                            visibleFaces[face.ordinal()].set(idx);
                            int aoKey = computeAoKey(world, samplePos, worldX, worldY, worldZ, face);
                            faceMergeKeys[face.ordinal()][idx] = aoKey;
                        }
                    }
                }
            }
        }
    }

    @Unique
    private static final int[][][] GREEDY_MESHING$AO_OFFSETS = new int[6][8][3];
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
                    GREEDY_MESHING$AO_OFFSETS[face.ordinal()][idx][0] = face.getStepX() + a * ax1 + b * ax2;
                    GREEDY_MESHING$AO_OFFSETS[face.ordinal()][idx][1] = face.getStepY() + a * ay1 + b * ay2;
                    GREEDY_MESHING$AO_OFFSETS[face.ordinal()][idx][2] = face.getStepZ() + a * az1 + b * az2;
                    idx++;
                }
            }
        }
    }

    @Unique
    private static int computeAoKey(BlockAndTintGetter world, BlockPos.MutableBlockPos pos,
                                    int worldX, int worldY, int worldZ, Direction face) {
        int[][] offsets = GREEDY_MESHING$AO_OFFSETS[face.ordinal()];
        int key = 0;
        for (int i = 0; i < 8; i++) {
            pos.set(worldX + offsets[i][0], worldY + offsets[i][1], worldZ + offsets[i][2]);
            BlockState neighbor = world.getBlockState(pos);
            if (neighbor.isViewBlocking(world, pos)
                    //? if UNOBFUSCATED {
                    /*&& neighbor.getLightDampening() != 0
                    *///?} else if >=1.21.2 {
                    /*&& neighbor.getLightBlock() != 0
                    *///?} else {
                    && neighbor.getLightBlock(world, pos) != 0
                    //?}
            ) {
                key |= (1 << i);
            }
        }
        return key;
    }

    private static final int GREEDY_MESHING$LIGHT_STEP = 4;

    @Unique
    private static int emitMergedQuad(
            VertexConsumer consumer, GreedyMesher.GreedyQuad quad,
            float u0, float u1, float v0, float v1,
            boolean applyTint, int tintIndex, BlockAndTintGetter world,
            int baseX, int baseY, int baseZ,
            GreedySodiumWorkState work
    ) {
        int W = quad.width(), H = quad.height();
        if (W == 1 && H == 1) {
            emitSodiumSubQuad(consumer, quad, 0, 0, 1, 1, u0, u1, v0, v1,
                    applyTint, tintIndex, world, baseX, baseY, baseZ, work, true);
            return 1;
        }
        int count = 0;
        for (int sv = 0; sv < H; sv += GREEDY_MESHING$LIGHT_STEP) {
            int sh = Math.min(GREEDY_MESHING$LIGHT_STEP, H - sv);
            for (int su = 0; su < W; su += GREEDY_MESHING$LIGHT_STEP) {
                int sw = Math.min(GREEDY_MESHING$LIGHT_STEP, W - su);
                emitSodiumSubQuad(consumer, quad, su, sv, sw, sh, u0, u1, v0, v1,
                        applyTint, tintIndex, world, baseX, baseY, baseZ, work, false);
                count++;
            }
        }
        return count;
    }

    @Unique
    private static void emitSodiumSubQuad(
            VertexConsumer consumer, GreedyMesher.GreedyQuad quad,
            int offU, int offV, int subW, int subH,
            float u0, float u1, float v0, float v1,
            boolean applyTint, int tintIndex, BlockAndTintGetter world,
            int baseX, int baseY, int baseZ,
            GreedySodiumWorkState work,
            boolean fullAO
    ) {
        Direction face = quad.face();
        int qx = quad.x(), qy = quad.y(), qz = quad.z();
        int sx, sy, sz;
        switch (face) {
            case NORTH, SOUTH -> { sx = qx + offU; sy = qy + offV; sz = qz; }
            case WEST, EAST   -> { sx = qx; sy = qy + offV; sz = qz + offU; }
            case DOWN, UP     -> { sx = qx + offU; sy = qy; sz = qz + offV; }
            default -> { sx = qx; sy = qy; sz = qz; }
        }

        GreedyMesher.GreedyQuad sub = new GreedyMesher.GreedyQuad(face, sx, sy, sz, subW, subH, quad.state());

        float[] c = work.scratchCorners;
        cornersInto(sub, c);
        float nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
        boolean flipV = face.getAxis().isHorizontal();
        float vv0 = flipV ? v1 : v0, vv1 = flipV ? v0 : v1;
        BlockColors blockColors = applyTint ? Minecraft.getInstance().getBlockColors() : null;
        BlockPos.MutableBlockPos tintPos = work.scratchTintPos;
        GreedyLighting.Scratch lighting = work.scratchLighting;
        int[][] cb = work.scratchCornerBlocks;
        greedyMeshing$cornerBlockPositionsInto(sub, baseX, baseY, baseZ, cb);
        float[] brightness = work.scratchBrightness;
        float[] tintR = work.scratchTintR, tintG = work.scratchTintG, tintB = work.scratchTintB;
        int[] lightmap = work.scratchLightmap;

        for (int i = 0; i < 4; i++) {
            GreedyLighting.computeTileLighting(world, quad.state(), face, cb[i][0], cb[i][1], cb[i][2], lighting);
            brightness[i] = lighting.brightness[i];
            lightmap[i] = lighting.lightmap[i];
            int tint = applyTint ? tintColorForTile(quad.state(), world, cb[i][0], cb[i][1], cb[i][2], tintPos, blockColors, tintIndex) : 0xFFFFFF;
            tintR[i] = ((tint >> 16) & 0xFF) / 255.0f;
            tintG[i] = ((tint >> 8) & 0xFF) / 255.0f;
            tintB[i] = (tint & 0xFF) / 255.0f;
        }

        float faceAlpha = (246.0f + face.ordinal()) / 255.0f;
        float cu = (u0 + u1) * 0.5f, cv = (vv0 + vv1) * 0.5f;
        consumer.addVertex(c[0],c[1],c[2]).setColor(brightness[0]*tintR[0],brightness[0]*tintG[0],brightness[0]*tintB[0],faceAlpha).setUv(cu,cv).setLight(lightmap[0]).setNormal(nx,ny,nz);
        consumer.addVertex(c[3],c[4],c[5]).setColor(brightness[1]*tintR[1],brightness[1]*tintG[1],brightness[1]*tintB[1],faceAlpha).setUv(cu,cv).setLight(lightmap[1]).setNormal(nx,ny,nz);
        consumer.addVertex(c[6],c[7],c[8]).setColor(brightness[2]*tintR[2],brightness[2]*tintG[2],brightness[2]*tintB[2],faceAlpha).setUv(cu,cv).setLight(lightmap[2]).setNormal(nx,ny,nz);
        consumer.addVertex(c[9],c[10],c[11]).setColor(brightness[3]*tintR[3],brightness[3]*tintG[3],brightness[3]*tintB[3],faceAlpha).setUv(cu,cv).setLight(lightmap[3]).setNormal(nx,ny,nz);
    }

    private static int emitTiledQuads(
            VertexConsumer consumer,
            GreedyMesher.GreedyQuad quad,
            float u0,
            float u1,
            float v0,
            float v1,
            boolean applyTint,
            int tintIndex,
            BlockAndTintGetter world,
            int baseX,
            int baseY,
            int baseZ,
            GreedySodiumWorkState work
    ) {
        float[] c = work.scratchCorners;
        cornersInto(quad, c);
        float nx = quad.face().getStepX(), ny = quad.face().getStepY(), nz = quad.face().getStepZ();

        BlockColors blockColors = applyTint ? Minecraft.getInstance().getBlockColors() : null;
        BlockPos.MutableBlockPos tintPos = work.scratchTintPos;
        GreedyLighting.Scratch lighting = work.scratchLighting;

        int W = quad.width();
        int H = quad.height();
        int count = 0;

        // Side faces need V-flip to keep textures upright (e.g., grass overlay)
        boolean flipV = quad.face().getAxis().isHorizontal();
        float vv0 = flipV ? v1 : v0;
        float vv1 = flipV ? v0 : v1;

        for (int tv = 0; tv < H; tv++) {
            for (int tu = 0; tu < W; tu++) {
                float fu0 = (float) tu / W, fu1 = (float) (tu + 1) / W;
                float fv0 = (float) tv / H, fv1 = (float) (tv + 1) / H;

                // Interpolate the 4 corners of this tile from the merged quad corners
                float x00 = interpolate(c, fu0, fv0, 0), y00 = interpolate(c, fu0, fv0, 1), z00 = interpolate(c, fu0, fv0, 2);
                float x10 = interpolate(c, fu1, fv0, 0), y10 = interpolate(c, fu1, fv0, 1), z10 = interpolate(c, fu1, fv0, 2);
                float x11 = interpolate(c, fu1, fv1, 0), y11 = interpolate(c, fu1, fv1, 1), z11 = interpolate(c, fu1, fv1, 2);
                float x01 = interpolate(c, fu0, fv1, 0), y01 = interpolate(c, fu0, fv1, 1), z01 = interpolate(c, fu0, fv1, 2);

                // Compute the world block position for this tile for lighting/tint
                int wx = tileBlockCoord(baseX, c, 0, quad.face().getStepX(), fu0, fv0);
                int wy = tileBlockCoord(baseY, c, 1, quad.face().getStepY(), fu0, fv0);
                int wz = tileBlockCoord(baseZ, c, 2, quad.face().getStepZ(), fu0, fv0);

                GreedyLighting.computeTileLighting(world, quad.state(), quad.face(), wx, wy, wz, lighting);
                int tint = applyTint ? tintColorForTile(quad.state(), world, wx, wy, wz, tintPos, blockColors, tintIndex) : 0xFFFFFF;
                float tintR = ((tint >> 16) & 0xFF) / 255.0f;
                float tintG = ((tint >> 8) & 0xFF) / 255.0f;
                float tintB = (tint & 0xFF) / 255.0f;

                consumer.addVertex(x00, y00, z00)
                        .setColor(lighting.brightness[0] * tintR, lighting.brightness[0] * tintG, lighting.brightness[0] * tintB, 1.0f)
                        .setUv(u0, vv0).setLight(lighting.lightmap[0]).setNormal(nx, ny, nz);
                consumer.addVertex(x10, y10, z10)
                        .setColor(lighting.brightness[1] * tintR, lighting.brightness[1] * tintG, lighting.brightness[1] * tintB, 1.0f)
                        .setUv(u1, vv0).setLight(lighting.lightmap[1]).setNormal(nx, ny, nz);
                consumer.addVertex(x11, y11, z11)
                        .setColor(lighting.brightness[2] * tintR, lighting.brightness[2] * tintG, lighting.brightness[2] * tintB, 1.0f)
                        .setUv(u1, vv1).setLight(lighting.lightmap[2]).setNormal(nx, ny, nz);
                consumer.addVertex(x01, y01, z01)
                        .setColor(lighting.brightness[3] * tintR, lighting.brightness[3] * tintG, lighting.brightness[3] * tintB, 1.0f)
                        .setUv(u0, vv1).setLight(lighting.lightmap[3]).setNormal(nx, ny, nz);

                count++;
            }
        }
        return count;
    }

    /**
     * Bilinear interpolation within the merged quad's 4 corners.
     * c is a 12-element array: [x0,y0,z0, x1,y1,z1, x2,y2,z2, x3,y3,z3]
     * Corners are ordered: 0=bottom-left, 1=bottom-right, 2=top-right, 3=top-left
     * fu goes along edge 0->1, fv goes along edge 0->3.
     */
    @Unique
    private static float interpolate(float[] c, float fu, float fv, int axis) {
        float bottom = c[0 + axis] + (c[3 + axis] - c[0 + axis]) * fu;
        float top    = c[9 + axis] + (c[6 + axis] - c[9 + axis]) * fu;
        return bottom + (top - bottom) * fv;
    }

    /**
     * Compute the world block coordinate for a tile at fractional position (fu, fv)
     * within the merged quad. We floor the interpolated local position and offset
     * inward along the face normal so the lighting sample hits the correct block.
     */
    @Unique
    private static int tileBlockCoord(int base, float[] c, int axis, int faceStep, float fu, float fv) {
        float center = interpolate(c, fu + 0.001f, fv + 0.001f, axis);
        int local = (int) Math.floor(center);
        // Offset by face step to sample the block behind the face (the solid block)
        if (faceStep > 0) {
            local = Math.max(0, local - 1 + faceStep);
        }
        return base + local;
    }

    @Unique
    private static int[][] greedyMeshing$cornerBlockPositions(GreedyMesher.GreedyQuad quad, int baseX, int baseY, int baseZ) {
        int[][] cb = new int[4][3];
        greedyMeshing$cornerBlockPositionsInto(quad, baseX, baseY, baseZ, cb);
        return cb;
    }

    @Unique
    private static void greedyMeshing$cornerBlockPositionsInto(GreedyMesher.GreedyQuad quad, int baseX, int baseY, int baseZ, int[][] cb) {
        int x = baseX + quad.x();
        int y = baseY + quad.y();
        int z = baseZ + quad.z();
        int w = quad.width();
        int h = quad.height();
        switch (quad.face()) {
            case NORTH -> {
                cb[0][0]=x+w-1; cb[0][1]=y;     cb[0][2]=z;
                cb[1][0]=x;     cb[1][1]=y;     cb[1][2]=z;
                cb[2][0]=x;     cb[2][1]=y+h-1; cb[2][2]=z;
                cb[3][0]=x+w-1; cb[3][1]=y+h-1; cb[3][2]=z;
            }
            case SOUTH -> {
                cb[0][0]=x;     cb[0][1]=y;     cb[0][2]=z;
                cb[1][0]=x+w-1; cb[1][1]=y;     cb[1][2]=z;
                cb[2][0]=x+w-1; cb[2][1]=y+h-1; cb[2][2]=z;
                cb[3][0]=x;     cb[3][1]=y+h-1; cb[3][2]=z;
            }
            case WEST -> {
                cb[0][0]=x; cb[0][1]=y;     cb[0][2]=z;
                cb[1][0]=x; cb[1][1]=y;     cb[1][2]=z+w-1;
                cb[2][0]=x; cb[2][1]=y+h-1; cb[2][2]=z+w-1;
                cb[3][0]=x; cb[3][1]=y+h-1; cb[3][2]=z;
            }
            case EAST -> {
                cb[0][0]=x; cb[0][1]=y;     cb[0][2]=z+w-1;
                cb[1][0]=x; cb[1][1]=y;     cb[1][2]=z;
                cb[2][0]=x; cb[2][1]=y+h-1; cb[2][2]=z;
                cb[3][0]=x; cb[3][1]=y+h-1; cb[3][2]=z+w-1;
            }
            case DOWN -> {
                cb[0][0]=x;     cb[0][1]=y; cb[0][2]=z;
                cb[1][0]=x+w-1; cb[1][1]=y; cb[1][2]=z;
                cb[2][0]=x+w-1; cb[2][1]=y; cb[2][2]=z+h-1;
                cb[3][0]=x;     cb[3][1]=y; cb[3][2]=z+h-1;
            }
            case UP -> {
                cb[0][0]=x;     cb[0][1]=y; cb[0][2]=z+h-1;
                cb[1][0]=x+w-1; cb[1][1]=y; cb[1][2]=z+h-1;
                cb[2][0]=x+w-1; cb[2][1]=y; cb[2][2]=z;
                cb[3][0]=x;     cb[3][1]=y; cb[3][2]=z;
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
            BlockColors blockColors,
            int tintIndex
    ) {
        samplePos.set(worldX, worldY, worldZ);
        //? if UNOBFUSCATED {
        /*int tint = blockColors.getTintSource(state, tintIndex).colorInWorld(state, world, samplePos);
        *///?} else {
        int tint = blockColors.getColor(state, world, samplePos, tintIndex);
        //?}
        return tint == -1 ? 0xFFFFFF : tint;
    }

    @Unique
    private static float[] corners(GreedyMesher.GreedyQuad quad) {
        float[] c = new float[12];
        cornersInto(quad, c);
        return c;
    }

    @Unique
    private static void cornersInto(GreedyMesher.GreedyQuad quad, float[] c) {
        float x0 = quad.x();
        float y0 = quad.y();
        float z0 = quad.z();
        float x1, y1, z1;
        switch (quad.face()) {
            case NORTH -> {
                x1 = x0 + quad.width(); y1 = y0 + quad.height(); z1 = z0;
                c[0]=x1; c[1]=y0; c[2]=z1; c[3]=x0; c[4]=y0; c[5]=z1;
                c[6]=x0; c[7]=y1; c[8]=z1; c[9]=x1; c[10]=y1; c[11]=z1;
            }
            case SOUTH -> {
                x1 = x0 + quad.width(); y1 = y0 + quad.height(); z1 = z0 + 1.0f;
                c[0]=x0; c[1]=y0; c[2]=z1; c[3]=x1; c[4]=y0; c[5]=z1;
                c[6]=x1; c[7]=y1; c[8]=z1; c[9]=x0; c[10]=y1; c[11]=z1;
            }
            case WEST -> {
                y1 = y0 + quad.height(); z1 = z0 + quad.width(); x1 = x0;
                c[0]=x1; c[1]=y0; c[2]=z0; c[3]=x1; c[4]=y0; c[5]=z1;
                c[6]=x1; c[7]=y1; c[8]=z1; c[9]=x1; c[10]=y1; c[11]=z0;
            }
            case EAST -> {
                y1 = y0 + quad.height(); z1 = z0 + quad.width(); x1 = x0 + 1.0f;
                c[0]=x1; c[1]=y0; c[2]=z1; c[3]=x1; c[4]=y0; c[5]=z0;
                c[6]=x1; c[7]=y1; c[8]=z0; c[9]=x1; c[10]=y1; c[11]=z1;
            }
            case DOWN -> {
                x1 = x0 + quad.width(); z1 = z0 + quad.height(); y1 = y0;
                c[0]=x0; c[1]=y1; c[2]=z0; c[3]=x1; c[4]=y1; c[5]=z0;
                c[6]=x1; c[7]=y1; c[8]=z1; c[9]=x0; c[10]=y1; c[11]=z1;
            }
            case UP -> {
                x1 = x0 + quad.width(); z1 = z0 + quad.height(); y1 = y0 + 1.0f;
                c[0]=x0; c[1]=y1; c[2]=z1; c[3]=x1; c[4]=y1; c[5]=z1;
                c[6]=x1; c[7]=y1; c[8]=z0; c[9]=x0; c[10]=y1; c[11]=z0;
            }
        }
    }

    //? if UNOBFUSCATED {
    /*@Unique
    private record FaceAppearance(TextureAtlasSprite sprite, boolean tinted, ChunkSectionLayer layer, int tintIndex) {
    }
    *///?} else {
    @Unique
    private record FaceAppearance(TextureAtlasSprite sprite, boolean tinted, int tintIndex) {
    }
    //?}

}
//?}
