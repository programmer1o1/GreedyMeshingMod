package hi.sierra.greedy_meshing.mixin.client.vulkan;

//? if VULKANMOD {
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.build.RenderRegion;
import net.vulkanmod.render.chunk.build.renderer.BlockRenderer;
import net.vulkanmod.render.chunk.build.renderer.FluidRenderer;
import net.vulkanmod.render.chunk.build.task.BuildTask;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import net.vulkanmod.render.chunk.cull.QuadFacing;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import net.vulkanmod.render.vertex.TerrainBuilder;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.joml.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
//? if UNOBFUSCATED {
/*import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
*///?} else if >=1.21.5 {
/*import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.world.level.BlockAndTintGetter;
*///?} else {
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.BlockAndTintGetter;
//?}
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
// Greedy Water is not yet supported on the UNOBFUSCATED (26.x) branch (see
// GreedyEligibility.isGreedyWaterSource) — the fabric-api fluid-rendering module's jar-in-jar
// resolution differs there and hasn't been verified.
//? if UNOBFUSCATED {
/*
*///?} else {
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
//?}
import hi.sierra.greedy_meshing.GreedyConfig;
import hi.sierra.greedy_meshing.GreedyEligibility;
import hi.sierra.greedy_meshing.GreedyMesher;
import hi.sierra.greedy_meshing.client.GreedyDebugStore;
import hi.sierra.greedy_meshing.client.GreedyEmissiveSupport;
import hi.sierra.greedy_meshing.client.GreedyLighting;
import hi.sierra.greedy_meshing.client.GreedyPerformanceStats;
import hi.sierra.greedy_meshing.client.GreedyRuntimeState;
import hi.sierra.greedy_meshing.client.GreedySpriteSupport;
import hi.sierra.greedy_meshing.client.vulkan.GreedyVulkanWorkState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * VulkanMod greedy-meshing integration (PROTOTYPE).
 *
 * <p>VulkanMod replaces both Sodium's and vanilla's chunk renderers with its own pipeline, so
 * neither {@code SodiumChunkBuilderMeshingTaskMixin} nor {@code SectionCompilerMixin} ever runs
 * under VulkanMod. This mixin is the third integration path, hooking VulkanMod's
 * {@code BuildTask.compile(...)} the same way the Sodium path hooks {@code ChunkBuilderMeshingTask}:
 *
 * <ol>
 *   <li>HEAD: set up the per-thread work state, capture the {@link BuilderResources} and section origin.</li>
 *   <li>{@link Redirect} on {@code BlockRenderer.renderBlock}: capture greedy-eligible opaque cubes
 *       and skip VulkanMod's normal per-block rendering for them.</li>
 *   <li>Before the first {@code TerrainBuilder.endDrawing()} (buffer finalization): run the greedy
 *       mesher and emit the result into VulkanMod's per-facing SOLID buffers.</li>
 * </ol>
 *
 * <p>This emits genuinely <em>merged</em> quads: one quad per greedy run (lighting-subdivided into
 * at most {@code LIGHT_STEP}-sized sub-quads so AO/light stays smooth), with UV set to the sprite
 * <em>centre</em> and the face id packed into colour alpha (IDs 246..251 = 246 + Direction.ordinal).
 * {@link VulkanShaderMixin} patches VulkanMod's terrain shaders to decode that face id and re-tile
 * the UV per block from {@code fract(position)} — the SPIR-V equivalent of the Sodium path's
 * {@code SodiumShaderLoaderMixin}. That's where the actual vertex-count reduction comes from.
 *
 * <p><b>Verify points on real hardware</b> (no Vulkan GPU in CI): (a) colour channel order in
 * {@code TerrainBufferBuilder.vertex}'s packed int — alpha is the high byte in both ARGB/ABGR so the
 * face id is safe, but RGB tint order is assumed ARGB like vanilla; (b) that the SOLID buffers are in
 * a building state at emit time (begun by {@code setupBufferBuilders} at compile start); (c) that
 * {@code fract(position)} in VulkanMod's reconstructed vertex position is block-aligned (ModelOffset +
 * baseOffset are integer block offsets, so it is).
 */
@Mixin(BuildTask.class)
public abstract class VulkanBuildTaskMixin {
    // BuildTask's own region field (set at construction, valid throughout compile).
    // RenderRegion implements BlockAndTintGetter, so the greedy helpers read it directly.
    // (section lives on the parent ChunkTask — read via VulkanChunkTaskAccessor, not @Shadow.)
    @Shadow
    private RenderRegion region;

    @Unique
    private static final ThreadLocal<GreedyVulkanWorkState> GREEDY_MESHING$STATE = ThreadLocal.withInitial(GreedyVulkanWorkState::new);
    @Unique
    private static final Direction[] GREEDY_MESHING$FACES = Direction.values();
    @Unique
    private static final ThreadLocal<BitSet[]> GREEDY_MESHING$VISIBLE = ThreadLocal.withInitial(GreedyMesher::createFaceMaskArray);

    @Inject(method = "compile", at = @At("HEAD"))
    private void greedyMeshing$beginVulkanTask(
            float camX, float camY, float camZ, BuilderResources builderResources,
            CallbackInfoReturnable<?> cir
    ) {
        GreedyVulkanWorkState work = GREEDY_MESHING$STATE.get();
        work.reset(builderResources);
        work.world(this.region);
        RenderSection section = ((VulkanChunkTaskAccessor) (Object) this).greedyMeshing$getSection();
        if (section == null) {
            return;
        }
        BlockPos origin = new BlockPos(section.xOffset(), section.yOffset(), section.zOffset());
        work.sectionOrigin(origin);
        work.sectionKey(SectionPos.asLong(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4));
        GreedyMesher.clearFaceMaskArray(GREEDY_MESHING$VISIBLE.get());
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void greedyMeshing$endVulkanTask(
            float camX, float camY, float camZ, BuilderResources builderResources,
            CallbackInfoReturnable<?> cir
    ) {
        GREEDY_MESHING$STATE.remove();
    }

    /**
     * Capture greedy-eligible opaque cubes instead of letting VulkanMod render them per-block.
     * Everything else falls through to the original render path unchanged.
     */
    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/vulkanmod/render/chunk/build/renderer/BlockRenderer;renderBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lorg/joml/Vector3f;)V"
            )
    )
    private void greedyMeshing$redirectVulkanRenderBlock(
            BlockRenderer renderer,
            BlockState state,
            BlockPos worldPos,
            Vector3f pos
    ) {
        if (!GreedyRuntimeState.isRuntimeGreedyActive()) {
            renderer.renderBlock(state, worldPos, pos);
            return;
        }

        GreedyVulkanWorkState work = GREEDY_MESHING$STATE.get();
        BlockAndTintGetter world = work.world();
        BlockPos origin = work.sectionOrigin();
        if (world == null || origin == null
                || !GreedyEligibility.isGreedyOpaqueCube(state, world, worldPos)
                || !GreedySpriteSupport.supportsGreedySpriteSizes(state)) {
            renderer.renderBlock(state, worldPos, pos);
            return;
        }

        int localX = worldPos.getX() - origin.getX();
        int localY = worldPos.getY() - origin.getY();
        int localZ = worldPos.getZ() - origin.getZ();
        if (localX < 0 || localX >= 16 || localY < 0 || localY >= 16 || localZ < 0 || localZ >= 16) {
            renderer.renderBlock(state, worldPos, pos);
            return;
        }

        int idx = GreedyMesher.index(localX, localY, localZ);
        if (work.sectionStates()[idx] == null) {
            work.incrementEligibleCount();
        }
        work.sectionStates()[idx] = state;
        // Eligible cube captured: SKIP VulkanMod's per-block render — the merged quad replaces it.
    }

    // Water's fluid quads are emitted through builderResources.fluidRenderer.renderLiquid(...), a
    // code path entirely separate from BlockRenderer.renderBlock above — BlockState.getRenderShape()
    // for water is INVISIBLE so it never reaches that redirect. Only plain still-water source blocks
    // that pass GreedyEligibility.isGreedyWaterSource are captured; everything else falls through to
    // VulkanMod's normal per-block fluid rendering. Not yet wired up on the UNOBFUSCATED (26.x)
    // branch — see the import guard above and GreedyEligibility.isGreedyWaterSource, which always
    // returns false there, making this omission a no-op rather than a behavior change.
    //
    // NOTE: this redirect + the merge-key/emission logic below are currently dead code on ALL
    // versions — GreedyEligibility.isGreedyWaterSource also unconditionally returns false whenever
    // VulkanMod is present (a separate, non-version-gated check), because VulkanMod's per-quad
    // translucency depth-sort visibly breaks (flickering/disappearing faces, confirmed in-game) when
    // water is merged into large quads. Left in place rather than deleted in case a future fix (e.g.
    // capping water sub-quad size to preserve sort granularity) makes this viable — see the
    // VULKANMOD_PRESENT comment in GreedyEligibility.java for the full explanation.
    //? if UNOBFUSCATED {
    /*
    *///?} else {
    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/vulkanmod/render/chunk/build/renderer/FluidRenderer;renderLiquid(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/core/BlockPos;)V"
            )
    )
    private void greedyMeshing$redirectVulkanRenderLiquid(
            FluidRenderer renderer,
            BlockState state,
            FluidState fluidState,
            BlockPos pos
    ) {
        if (!GreedyRuntimeState.isRuntimeGreedyActive()) {
            renderer.renderLiquid(state, fluidState, pos);
            return;
        }

        GreedyVulkanWorkState work = GREEDY_MESHING$STATE.get();
        BlockAndTintGetter world = work.world();
        BlockPos origin = work.sectionOrigin();
        if (world == null || origin == null || !GreedyEligibility.isGreedyWaterSource(state, world, pos)) {
            renderer.renderLiquid(state, fluidState, pos);
            return;
        }

        int localX = pos.getX() - origin.getX();
        int localY = pos.getY() - origin.getY();
        int localZ = pos.getZ() - origin.getZ();
        if (localX < 0 || localX >= 16 || localY < 0 || localY >= 16 || localZ < 0 || localZ >= 16) {
            renderer.renderLiquid(state, fluidState, pos);
            return;
        }

        int idx = GreedyMesher.index(localX, localY, localZ);
        if (work.sectionStates()[idx] == null) {
            work.incrementEligibleCount();
        }
        work.sectionStates()[idx] = state;
    }
    //?}

    /**
     * Right before VulkanMod finalizes its buffers, mesh the captured blocks and emit the result
     * into the SOLID buffer. ordinal = 0 fires before the first {@code end()} in the finalization
     * loop, i.e. after every {@code renderBlock} call but before any buffer is closed.
     */
    @Inject(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/vulkanmod/render/vertex/TerrainBuilder;endDrawing()Lnet/vulkanmod/render/vertex/TerrainBuilder$DrawState;",
                    ordinal = 0
            )
    )
    private void greedyMeshing$emitGreedyBeforeVulkanFinalize(
            float camX, float camY, float camZ, BuilderResources builderResources,
            CallbackInfoReturnable<?> cir
    ) {
        if (!GreedyRuntimeState.isRuntimeGreedyActive()) {
            return;
        }

        GreedyVulkanWorkState work = GREEDY_MESHING$STATE.get();
        // endDrawing() is called once per TerrainRenderType inside compile()'s finalization loop, and
        // this @Inject fires on every one of those calls. Emit exactly once per compile, otherwise the
        // merged geometry is appended into the buffer ~5 times over (the duplicate-chunk artifact).
        if (work.hasEmitted()) {
            return;
        }
        work.markEmitted();

        // Drop any stale wireframe quads for this section before re-capturing (mirrors the Sodium path,
        // so a section that stops being greedy doesn't keep its old outline).
        if (work.captureDebug()) {
            GreedyDebugStore.clearSection(work.sectionKey());
            GreedyPerformanceStats.clearSectionGeometry(work.sectionKey());
        }

        BlockAndTintGetter world = work.world();
        BlockPos origin = work.sectionOrigin();
        if (work.eligibleCount() <= 0 || world == null || origin == null) {
            return;
        }

        int baseX = origin.getX();
        int baseY = origin.getY();
        int baseZ = origin.getZ();
        work.scratchLighting.applyDirectionalShade =
                !GreedyRuntimeState.isInSableSubLevel(baseX >> 4, baseZ >> 4);
        BitSet[] visibleFaces = GREEDY_MESHING$VISIBLE.get();
        long[][] mergeKeys = work.faceMergeKeys();
        populateFaceVisibility(world, work.sectionStates(), visibleFaces, mergeKeys, baseX, baseY, baseZ);
        List<GreedyMesher.GreedyQuad> merged = GreedyMesher.meshWithKeys(
                work.sectionStates(), visibleFaces, mergeKeys
        );
        if (merged.isEmpty()) {
            return;
        }

        // SOLID / TRANSLUCENT layer — but VulkanMod compacts render types (e.g. SOLID -> CUTOUT_MIPPED)
        // and only *begins/uploads* the compacted buffers. Writing to the raw builder would land in a
        // buffer that's never begun or uploaded (=> vertices silently lost). Apply VulkanMod's own
        // remapping, exactly as its emit path does, to hit the buffer that actually gets drawn.
        TerrainBuilder solid = builderResources.builderPack.builder(TerrainRenderType.getRemapped(TerrainRenderType.SOLID));
        TerrainBuilder translucent = builderResources.builderPack.builder(TerrainRenderType.getRemapped(TerrainRenderType.TRANSLUCENT));

        boolean captureDebug = work.captureDebug();
        List<GreedyDebugStore.DebugQuad> debugQuads = captureDebug ? new ArrayList<>(merged.size()) : List.of();

        int emittedQuads = 0;
        int vanillaEquivalent = 0;
        for (GreedyMesher.GreedyQuad quad : merged) {
            List<FaceAppearance> layers = greedyMeshing$resolveFaceLayers(quad.state(), quad.face());
            int blockFaces = quad.width() * quad.height();
            TerrainBuilder builder = quad.state().is(Blocks.WATER) ? translucent : solid;
            for (FaceAppearance layer : layers) {
                if (GreedySpriteSupport.supportsMergedShaderSprite(layer.sprite())) {
                    emittedQuads += emitMergedQuad(builder, quad,
                            layer.sprite().getU0(), layer.sprite().getU1(),
                            layer.sprite().getV0(), layer.sprite().getV1(),
                            layer.tinted(), layer.tintIndex(), world, baseX, baseY, baseZ, work,
                            layer.sprite().contents().width());
                } else {
                    emittedQuads += emitVulkanTiledQuad(builder, quad,
                            layer.sprite().getU0(), layer.sprite().getU1(),
                            layer.sprite().getV0(), layer.sprite().getV1(),
                            layer.tinted(), layer.tintIndex(), world, baseX, baseY, baseZ, work, false);
                }
                TextureAtlasSprite emissive = GreedyEmissiveSupport.find(layer.sprite());
                if (emissive != null) {
                    emittedQuads += emitVulkanTiledQuad(builder, quad,
                            emissive.getU0(), emissive.getU1(), emissive.getV0(), emissive.getV1(),
                            false, -1, world, baseX, baseY, baseZ, work, true);
                }
                vanillaEquivalent += blockFaces;
            }
            if (captureDebug) {
                debugQuads.add(greedyMeshing$toDebugQuad(quad, baseX, baseY, baseZ, world, work.scratchTintPos));
            }
        }
        GreedyPerformanceStats.onGreedySectionBuilt(work.eligibleCount(), merged.size(), emittedQuads, vanillaEquivalent);
        GreedyPerformanceStats.setSectionGeometry(work.sectionKey(), emittedQuads, vanillaEquivalent);
        if (captureDebug) {
            GreedyDebugStore.setSectionQuads(work.sectionKey(), debugQuads);
        }
    }

    // ------------------------------------------------------------------
    // Face-layer resolution (sprite + tint per block face). Ported from the Sodium/vanilla paths.
    // ------------------------------------------------------------------

    @Unique
    private static List<FaceAppearance> greedyMeshing$resolveFaceLayers(BlockState state, Direction face) {
        if (state.is(Blocks.WATER)) {
            return greedyMeshing$resolveWaterFaceLayers(state);
        }
        //? if UNOBFUSCATED {
        /*BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        RandomSource random = RandomSource.create(0L);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        List<FaceAppearance> layers = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(face)) {
                layers.add(new FaceAppearance(quad.materialInfo().sprite(), quad.materialInfo().isTinted(), quad.materialInfo().tintIndex()));
            }
        }
        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == face) {
                    layers.add(new FaceAppearance(quad.materialInfo().sprite(), quad.materialInfo().isTinted(), quad.materialInfo().tintIndex()));
                }
            }
        }
        if (layers.isEmpty()) {
            layers.add(new FaceAppearance(model.particleMaterial().sprite(), false, -1));
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

    /**
     * Water's BlockState.getRenderShape() is INVISIBLE, so it has no real baked model — the
     * generic greedyMeshing$resolveFaceLayers path above (which looks up a baked model) doesn't
     * apply. Resolve the still-water sprite via Fabric's fluid-rendering API instead; the world
     * position doesn't matter here (Fabric's default water handler ignores it when picking the
     * sprite), only the FluidState, so this is safe to cache per-BlockState like the opaque path.
     */
    @Unique
    private static List<FaceAppearance> greedyMeshing$resolveWaterFaceLayers(BlockState state) {
        //? if UNOBFUSCATED {
        /*// Not yet wired up on this branch — see GreedyEligibility.isGreedyWaterSource, which
        // always returns false here, so this is unreachable rather than a behavior change.
        return List.of();
        *///?} else {
        // Fabric API registers a default handler for vanilla water unconditionally on init, so this
        // is never actually null in practice — GreedyEligibility.isGreedyWaterSource already gates
        // out anything that isn't plain Blocks.WATER before this is ever called.
        FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(Fluids.WATER);
        if (handler == null) {
            return List.of();
        }
        TextureAtlasSprite sprite = handler.getFluidSprites(null, null, state.getFluidState())[0];
        return List.of(new FaceAppearance(sprite, true, -1));
        //?}
    }

    // ------------------------------------------------------------------
    // Face visibility + AO merge keys. Mirrors the Sodium path.
    // ------------------------------------------------------------------

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
                        if (Block.shouldRenderFace(state, neighbor, face)) {
                        //?} else {
                        /*if (Block.shouldRenderFace(state, world, new BlockPos(worldX, worldY, worldZ), face, samplePos)) {
                        *///?}
                            visibleFaces[face.ordinal()].set(idx);
                            int mergeKey;
                            if (state.is(Blocks.WATER)) {
                                // Water only merges within the interior of a flat, uncovered still-water
                                // body (see GreedyEligibility.isGreedyWaterSource + isFlatWaterSurface) —
                                // vanilla renders a sloped, per-corner-averaged surface everywhere else,
                                // which this mod's flat GreedyQuad rectangles can't represent. The bottom
                                // face is always an exact flat square regardless of the top surface's
                                // slope, so it skips the check; every other face (including the sides,
                                // whose top edge follows the same slope as the top face) needs it.
                                // Non-flat faces get a unique key so they fall back to individual 1x1
                                // quads — pixel-identical to un-merged vanilla rendering, never a
                                // regression.
                                boolean flat = face == Direction.DOWN
                                        || isFlatWaterSurface(world, worldX, worldY, worldZ, samplePos);
                                mergeKey = flat ? 0 : (0x40000000 | idx);
                            } else {
                                // Aggressive ("absolute") greedy ignores the AO signature so same-block
                                // faces merge into the largest possible quads; lighting is still sampled
                                // per sub-quad.
                                mergeKey = GreedyConfig.aggressiveGreedy()
                                        ? 0
                                        : computeAoKey(world, samplePos, worldX, worldY, worldZ, face);
                            }
                            faceMergeKeys[face.ordinal()][idx] = mergeKey;
                        }
                    }
                }
            }
        }
    }

    /**
     * True iff the 3x3 horizontal neighbourhood centered on (worldX, worldY, worldZ) is entirely
     * uncovered still-water source blocks — the union of the four lattice corners vanilla's own
     * per-corner height averaging touches for this block's top face. Checking the superset rather
     * than each corner's exact 2x2 footprint is conservative: a handful of tiles that would actually
     * render flat get misclassified as non-flat (and simply don't merge), which is acceptable —
     * this check only ever removes merge opportunities, never approves an incorrect one.
     */
    @Unique
    private static boolean isFlatWaterSurface(
            BlockAndTintGetter world, int worldX, int worldY, int worldZ, BlockPos.MutableBlockPos scratch
    ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                scratch.set(worldX + dx, worldY, worldZ + dz);
                BlockState neighborState = world.getBlockState(scratch);
                if (!neighborState.is(Blocks.WATER)) {
                    return false;
                }
                FluidState neighborFluid = neighborState.getFluidState();
                if (!neighborFluid.isSource() || neighborFluid.getType() != Fluids.WATER) {
                    return false;
                }
                scratch.set(worldX + dx, worldY + 1, worldZ + dz);
                if (world.getBlockState(scratch).getFluidState().getType() == Fluids.WATER) {
                    return false;
                }
            }
        }
        return true;
    }

    @Unique
    private static final int[][][] GREEDY_MESHING$AO_OFFSETS = new int[6][8][3];
    static {
        for (Direction face : Direction.values()) {
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
                    && neighbor.getLightBlock() != 0
                    //?} else {
                    /*&& neighbor.getLightBlock(world, pos) != 0
                    *///?}
            ) {
                key |= (1 << i);
            }
        }
        pos.set(worldX + face.getStepX(), worldY + face.getStepY(), worldZ + face.getStepZ());
        int packedLight = GreedyLighting.mergeLightKey(world, pos);
        key |= ((packedLight >>> 4) & 0xF) << 8;
        key |= ((packedLight >>> 20) & 0xF) << 12;
        return key;
    }

    // ------------------------------------------------------------------
    // Merged emit: one quad per greedy run (UV = sprite centre, face id in colour alpha). The shader
    // (VulkanShaderMixin) re-tiles the UV per block. Large runs are split into LIGHT_STEP-sized
    // sub-quads only so AO/lighting stays smooth — each sub-quad still spans many blocks.
    // ------------------------------------------------------------------

    /** Max sub-quad size for lighting subdivision (blocks). */
    @Unique
    private static final int GREEDY_MESHING$LIGHT_STEP = 4;

    /** Emits one ordinary Vulkan terrain quad per block tile, preserving arbitrary sprite UVs. */
    @Unique
    private static int emitVulkanTiledQuad(
            TerrainBuilder builder, GreedyMesher.GreedyQuad quad,
            float u0, float u1, float v0, float v1,
            boolean applyTint, int tintIndex, BlockAndTintGetter world,
            int baseX, int baseY, int baseZ,
            GreedyVulkanWorkState work, boolean fullbright
    ) {
        TerrainBufferBuilder consumer = builder.getBufferBuilder(QuadFacing.UNDEFINED.ordinal());
        float[] c = work.scratchCorners;
        float top = greedyMeshing$waterSurfaceTop(world, quad, baseX, baseY, baseZ, work.scratchTintPos);
        fillCorners(c, quad, top);
        boolean flipV = quad.face().getAxis().isHorizontal();
        float tv0 = flipV ? v1 : v0, tv1 = flipV ? v0 : v1;
        BlockColors colors = applyTint ? Minecraft.getInstance().getBlockColors() : null;
        int count = 0;
        for (int tv = 0; tv < quad.height(); tv++) {
            for (int tu = 0; tu < quad.width(); tu++) {
                float fu0 = (float) tu / quad.width(), fu1 = (float) (tu + 1) / quad.width();
                float fv0 = (float) tv / quad.height(), fv1 = (float) (tv + 1) / quad.height();
                float x00 = interpolate(c, fu0, fv0, 0), y00 = interpolate(c, fu0, fv0, 1), z00 = interpolate(c, fu0, fv0, 2);
                float x10 = interpolate(c, fu1, fv0, 0), y10 = interpolate(c, fu1, fv0, 1), z10 = interpolate(c, fu1, fv0, 2);
                float x11 = interpolate(c, fu1, fv1, 0), y11 = interpolate(c, fu1, fv1, 1), z11 = interpolate(c, fu1, fv1, 2);
                float x01 = interpolate(c, fu0, fv1, 0), y01 = interpolate(c, fu0, fv1, 1), z01 = interpolate(c, fu0, fv1, 2);
                int wx = baseX + (int) Math.floor(interpolate(c, fu0 + 0.001f, fv0 + 0.001f, 0));
                int wy = baseY + (int) Math.floor(interpolate(c, fu0 + 0.001f, fv0 + 0.001f, 1));
                int wz = baseZ + (int) Math.floor(interpolate(c, fu0 + 0.001f, fv0 + 0.001f, 2));
                if (fullbright) {
                    java.util.Arrays.fill(work.scratchLighting.brightness, 1.0f);
                    java.util.Arrays.fill(work.scratchLighting.lightmap, 0xF000F0);
                } else {
                    GreedyLighting.computeTileLighting(world, quad.state(), quad.face(), wx, wy, wz, work.scratchLighting);
                }
                int tint = applyTint ? tintColorForTile(quad.state(), world, wx, wy, wz, work.scratchTintPos, colors, tintIndex) : 0xFFFFFF;
                float tr = ((tint >> 16) & 0xFF) / 255.0f;
                float tg = ((tint >> 8) & 0xFF) / 255.0f;
                float tb = (tint & 0xFF) / 255.0f;
                float[] px = {x00, x10, x11, x01}, py = {y00, y10, y11, y01}, pz = {z00, z10, z11, z01};
                float[] pu = {u0, u1, u1, u0}, pv = {tv0, tv0, tv1, tv1};
                if (fullbright) {
                    for (int i = 0; i < 4; i++) {
                        // Keep the emissive companion in front of the base face at long range.
                        px[i] += quad.face().getStepX() * 0.05f;
                        py[i] += quad.face().getStepY() * 0.05f;
                        pz[i] += quad.face().getStepZ() * 0.05f;
                    }
                }
                consumer.ensureCapacity();
                for (int i = 0; i < 4; i++) {
                    float br = work.scratchLighting.brightness[i];
                    int argb = 0xFF000000
                            | ((int) (br * tr * 255.0f) << 16)
                            | ((int) (br * tg * 255.0f) << 8)
                            | (int) (br * tb * 255.0f);
                    consumer.vertex(px[i], py[i], pz[i], ColorUtil.ARGB.toRGBA(argb), pu[i], pv[i], work.scratchLighting.lightmap[i], 0);
                }
                count++;
            }
        }
        return count;
    }

    @Unique
    private static float interpolate(float[] c, float fu, float fv, int axis) {
        float bottom = c[axis] + (c[3 + axis] - c[axis]) * fu;
        float top = c[9 + axis] + (c[6 + axis] - c[9 + axis]) * fu;
        return bottom + (top - bottom) * fv;
    }

    @Unique
    private static int emitMergedQuad(
            TerrainBuilder builder, GreedyMesher.GreedyQuad quad,
            float u0, float u1, float v0, float v1,
            boolean applyTint, int tintIndex, BlockAndTintGetter world,
            int baseX, int baseY, int baseZ,
            GreedyVulkanWorkState work,
            int spriteSize
    ) {
        // Bucket choice mirrors VulkanMod's draw path, not its build path. The opaque draw reads:
        //   - back-face culling OFF -> ONLY the UNDEFINED bucket (facing index 6),
        //   - back-face culling ON  -> the directional buckets selected by getMask(), which ALWAYS
        //                              includes the UNDEFINED bit (it's getMask's seed value).
        // So the UNDEFINED bucket is drawn in BOTH modes, while a directional bucket is drawn only
        // when culling is on AND the camera is on the right side. Emitting merged quads into the
        // directional buckets therefore made them vanish whenever culling was off. Routing every
        // merged quad through UNDEFINED makes it render in both modes — the same bucket VulkanMod
        // itself uses for opaque geometry when culling is disabled. (Our quads are pre-merged spans;
        // we don't want per-facing back-face culling on them anyway.)
        TerrainBufferBuilder consumer = builder.getBufferBuilder(QuadFacing.UNDEFINED.ordinal());

        int W = quad.width(), H = quad.height();
        if (W == 1 && H == 1) {
            emitVulkanSubQuad(consumer, quad, 0, 0, 1, 1, W, H, u0, u1, v0, v1,
                    applyTint, tintIndex, world, baseX, baseY, baseZ, work, spriteSize);
            return 1;
        }
        int count = 0;
        for (int sv = 0; sv < H; sv += GREEDY_MESHING$LIGHT_STEP) {
            int sh = Math.min(GREEDY_MESHING$LIGHT_STEP, H - sv);
            for (int su = 0; su < W; su += GREEDY_MESHING$LIGHT_STEP) {
                int sw = Math.min(GREEDY_MESHING$LIGHT_STEP, W - su);
                emitVulkanSubQuad(consumer, quad, su, sv, sw, sh, W, H, u0, u1, v0, v1,
                        applyTint, tintIndex, world, baseX, baseY, baseZ, work, spriteSize);
                count++;
            }
        }
        return count;
    }

    /** World-space overlap nudged onto a merged quad's outer edge to hide sub-pixel raster
     *  gaps ("T-junction cracks") some GPU drivers leave between it and neighboring quads that
     *  were meshed independently. Small enough to be visually imperceptible. */
    @Unique
    private static final float GREEDY_MESHING$CRACK_SKIRT = 1f / 128f;

    // (uSide, vSide) per corner index, matching the fillCorners winding below: +1 means the
    // corner sits on that axis's max edge, -1 its min edge. Indexed by Direction.ordinal()
    // (DOWN, UP, NORTH, SOUTH, WEST, EAST).
    @Unique
    private static final int[][] GREEDY_MESHING$CORNER_UV_SIDES = {
            { -1, -1,  1, -1,  1,  1, -1,  1 }, // DOWN
            { -1,  1,  1,  1,  1, -1, -1, -1 }, // UP
            {  1, -1, -1, -1, -1,  1,  1,  1 }, // NORTH
            { -1, -1,  1, -1,  1,  1, -1,  1 }, // SOUTH
            { -1, -1,  1, -1,  1,  1, -1,  1 }, // WEST
            {  1, -1, -1, -1, -1,  1,  1,  1 }, // EAST
    };

    @Unique
    private static void greedyMeshing$skirtCorners(float[] c, Direction face, boolean atUMin, boolean atUMax, boolean atVMin, boolean atVMax) {
        if (!atUMin && !atUMax && !atVMin && !atVMax) {
            return;
        }
        int axisU = (face == Direction.WEST || face == Direction.EAST) ? 2 : 0;
        int axisV = (face == Direction.DOWN || face == Direction.UP) ? 2 : 1;
        int[] sides = GREEDY_MESHING$CORNER_UV_SIDES[face.ordinal()];
        for (int i = 0; i < 4; i++) {
            int uSide = sides[i * 2];
            int vSide = sides[i * 2 + 1];
            int ci = i * 3;
            if ((uSide < 0 && atUMin) || (uSide > 0 && atUMax)) {
                c[ci + axisU] += uSide * GREEDY_MESHING$CRACK_SKIRT;
            }
            if ((vSide < 0 && atVMin) || (vSide > 0 && atVMax)) {
                c[ci + axisV] += vSide * GREEDY_MESHING$CRACK_SKIRT;
            }
        }
    }

    @Unique
    private static void emitVulkanSubQuad(
            TerrainBufferBuilder consumer, GreedyMesher.GreedyQuad quad,
            int offU, int offV, int subW, int subH, int fullW, int fullH,
            float u0, float u1, float v0, float v1,
            boolean applyTint, int tintIndex, BlockAndTintGetter world,
            int baseX, int baseY, int baseZ,
            GreedyVulkanWorkState work,
            int spriteSize
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
        BlockPos.MutableBlockPos tintPos = work.scratchTintPos;
        float top = greedyMeshing$waterSurfaceTop(world, sub, baseX, baseY, baseZ, tintPos);
        fillCorners(c, sub, top);
        boolean fullAO = offU == 0 && offV == 0 && subW == fullW && subH == fullH;
        if (!fullAO && GreedyRuntimeState.requiresCrackSafeSubdivision()) {
            greedyMeshing$skirtCorners(c, face, offU == 0, offU + subW == fullW, offV == 0, offV + subH == fullH);
        }

        BlockColors blockColors = applyTint ? Minecraft.getInstance().getBlockColors() : null;
        GreedyLighting.Scratch lighting = work.scratchLighting;
        int[][] cb = work.scratchCornerBlocks;
        cornerBlockPositionsInto(sub, baseX, baseY, baseZ, cb);
        float[] brightness = work.scratchBrightness;
        int[] lightmap = work.scratchLightmap;
        float[] tintR = work.scratchTintR, tintG = work.scratchTintG, tintB = work.scratchTintB;

        for (int i = 0; i < 4; i++) {
            GreedyLighting.computeTileLighting(world, quad.state(), face, cb[i][0], cb[i][1], cb[i][2], lighting);
            brightness[i] = lighting.brightness[i];
            lightmap[i] = lighting.lightmap[i];
            int tint = applyTint ? tintColorForTile(quad.state(), world, cb[i][0], cb[i][1], cb[i][2], tintPos, blockColors, tintIndex) : 0xFFFFFF;
            tintR[i] = ((tint >> 16) & 0xFF) / 255.0f;
            tintG[i] = ((tint >> 8) & 0xFF) / 255.0f;
            tintB[i] = (tint & 0xFF) / 255.0f;
        }

        // UV = sprite centre; the shader reconstructs per-block UVs from fract(position) + this centre.
        boolean flipV = face.getAxis().isHorizontal();
        float vv0 = flipV ? v1 : v0, vv1 = flipV ? v0 : v1;
        float cu = (u0 + u1) * 0.5f, cv = (vv0 + vv1) * 0.5f;
        // Face id 246..251 in the ARGB alpha byte (toRGBA preserves alpha, so the shader still reads
        // it as Color.a). VulkanMod's own bufferQuad iterates vertices in natural order, so no winding
        // flip. Colour is ARGB built here then converted with VulkanMod's own ColorUtil.ARGB.toRGBA,
        // exactly as bufferQuad does — passing raw ARGB swaps the R/B channels.
        int aByte = (int) (GreedySpriteSupport.mergedFaceAlpha(face, spriteSize) * 255.0f) << 24;
        // Grow the buffer before writing — VulkanMod's own bufferQuad calls ensureCapacity() per quad.
        // Without it, appending past the capacity VulkanMod sized for its own geometry overflows into
        // adjacent section memory (=> other chunks duplicated/cut off at the wrong location).
        consumer.ensureCapacity();
        for (int i = 0; i < 4; i++) {
            int ci = i * 3;
            float br = brightness[i];
            int argb = aByte
                    | ((int) (br * tintR[i] * 255.0f) << 16)
                    | ((int) (br * tintG[i] * 255.0f) << 8)
                    | (int) (br * tintB[i] * 255.0f);
            // No normal element in VulkanMod's compressed terrain format; vertex(...) advances internally.
            consumer.vertex(c[ci], c[ci + 1], c[ci + 2], ColorUtil.ARGB.toRGBA(argb), cu, cv, lightmap[i], 0);
        }
    }

    /** World-space block position of each of the 4 corners (matches Sodium path corner ordering). */
    @Unique
    private static void cornerBlockPositionsInto(GreedyMesher.GreedyQuad quad, int baseX, int baseY, int baseZ, int[][] cb) {
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
            BlockState state, BlockAndTintGetter world,
            int worldX, int worldY, int worldZ,
            BlockPos.MutableBlockPos samplePos, BlockColors blockColors, int tintIndex
    ) {
        samplePos.set(worldX, worldY, worldZ);
        if (state.is(Blocks.WATER)) {
            //? if UNOBFUSCATED {
            /*// Not yet wired up on this branch — see GreedyEligibility.isGreedyWaterSource, which
            // always returns false here, so this is unreachable rather than a behavior change.
            return 0xFFFFFF;
            *///?} else {
            // Water's biome tint is applied by the fluid renderer directly (BiomeColors.getAverageWaterColor),
            // not through the generic BlockColors registry the rest of this method uses.
            FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(Fluids.WATER);
            return handler != null ? handler.getFluidColor(world, samplePos, state.getFluidState()) : 0xFFFFFF;
            //?}
        }
        //? if UNOBFUSCATED {
        /*int tint = blockColors.getTintSource(state, tintIndex).colorInWorld(state, world, samplePos);
        *///?} else {
        int tint = blockColors.getColor(state, world, samplePos, tintIndex);
        //?}
        return tint == -1 ? 0xFFFFFF : tint;
    }

    /** World-space outline of a whole merged quad (not the lighting-subdivided sub-quads) for the
     *  wireframe overlay. Uses the same corner layout as the emit path so the outline matches. */
    @Unique
    private static GreedyDebugStore.DebugQuad greedyMeshing$toDebugQuad(
            GreedyMesher.GreedyQuad quad, int baseX, int baseY, int baseZ,
            BlockAndTintGetter world, BlockPos.MutableBlockPos scratch
    ) {
        float[] c = new float[12];
        float top = greedyMeshing$waterSurfaceTop(world, quad, baseX, baseY, baseZ, scratch);
        fillCorners(c, quad, top);
        return new GreedyDebugStore.DebugQuad(
                c[0] + baseX, c[1] + baseY, c[2] + baseZ,
                c[3] + baseX, c[4] + baseY, c[5] + baseZ,
                c[6] + baseX, c[7] + baseY, c[8] + baseZ,
                c[9] + baseX, c[10] + baseY, c[11] + baseZ
        );
    }

    /**
     * The height of a water quad's top edge: a full block (1.0) for every non-water quad AND for
     * water that has more water directly above it (an internal boundary, not a real surface — vanilla
     * renders these at full height too, see LiquidBlockRenderer.getHeight's "covered" branch). Only a
     * water block genuinely exposed to something other than water above uses its own (usually ~0.889)
     * exposed-surface height. Must be checked per quad, NOT inferred from `state.is(Blocks.WATER)`
     * alone — a submerged water block still has BlockState water/LEVEL=0 like a surface block, so
     * blindly shrinking every water quad left a ~0.11-block gap at every internal layer boundary in
     * water deeper than 1 block (issue: merged water quads left gaps in multi-layer-deep water).
     */
    @Unique
    private static float greedyMeshing$waterSurfaceTop(
            BlockAndTintGetter world, GreedyMesher.GreedyQuad quad, int baseX, int baseY, int baseZ,
            BlockPos.MutableBlockPos scratch
    ) {
        if (!quad.state().is(Blocks.WATER)) {
            return 1.0f;
        }
        scratch.set(baseX + quad.x(), baseY + quad.y() + 1, baseZ + quad.z());
        if (world.getBlockState(scratch).getFluidState().getType() == Fluids.WATER) {
            return 1.0f;
        }
        return quad.state().getFluidState().getOwnHeight();
    }

    @Unique
    private static void fillCorners(float[] out, GreedyMesher.GreedyQuad quad, float top) {
        float x0 = quad.x(), y0 = quad.y(), z0 = quad.z();
        switch (quad.face()) {
            case NORTH -> { float x1 = x0 + quad.width(), y1 = y0 + quad.height() - 1 + top; out[0]=x1; out[1]=y0; out[2]=z0; out[3]=x0; out[4]=y0; out[5]=z0; out[6]=x0; out[7]=y1; out[8]=z0; out[9]=x1; out[10]=y1; out[11]=z0; }
            case SOUTH -> { float x1 = x0 + quad.width(), y1 = y0 + quad.height() - 1 + top, z1 = z0 + 1; out[0]=x0; out[1]=y0; out[2]=z1; out[3]=x1; out[4]=y0; out[5]=z1; out[6]=x1; out[7]=y1; out[8]=z1; out[9]=x0; out[10]=y1; out[11]=z1; }
            case WEST -> { float y1 = y0 + quad.height() - 1 + top, z1 = z0 + quad.width(); out[0]=x0; out[1]=y0; out[2]=z0; out[3]=x0; out[4]=y0; out[5]=z1; out[6]=x0; out[7]=y1; out[8]=z1; out[9]=x0; out[10]=y1; out[11]=z0; }
            case EAST -> { float x1 = x0 + 1, y1 = y0 + quad.height() - 1 + top, z1 = z0 + quad.width(); out[0]=x1; out[1]=y0; out[2]=z1; out[3]=x1; out[4]=y0; out[5]=z0; out[6]=x1; out[7]=y1; out[8]=z0; out[9]=x1; out[10]=y1; out[11]=z1; }
            case DOWN -> { float x1 = x0 + quad.width(), z1 = z0 + quad.height(); out[0]=x0; out[1]=y0; out[2]=z0; out[3]=x1; out[4]=y0; out[5]=z0; out[6]=x1; out[7]=y0; out[8]=z1; out[9]=x0; out[10]=y0; out[11]=z1; }
            case UP -> { float x1 = x0 + quad.width(), y1 = y0 + top, z1 = z0 + quad.height(); out[0]=x0; out[1]=y1; out[2]=z1; out[3]=x1; out[4]=y1; out[5]=z1; out[6]=x1; out[7]=y1; out[8]=z0; out[9]=x0; out[10]=y1; out[11]=z0; }
        }
    }

    @Unique
    private record FaceAppearance(TextureAtlasSprite sprite, boolean tinted, int tintIndex) {}
}
//?}
