package hi.sierra.greedy_meshing;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GreedyEligibility {
    private static final ConcurrentHashMap<BlockState, Boolean> CACHE = new ConcurrentHashMap<>();

    /**
     * "Fancy grass" / connected-grass mods (e.g. BetterGrassify, LambdaBetterGrass) rewrite a
     * block's side quads based on neighbouring blocks — the grass-top texture wraps down the sides.
     * Greedy meshing intercepts these blocks before those mods run and re-emits them from the
     * static baked model, silently discarding the effect (see issue #2). When such a mod is present
     * we leave the affected blocks to the normal render path so the mod can do its job.
     */
    private static final Set<String> FANCY_GRASS_MOD_IDS = Set.of(
            "bettergrass",        // BetterGrassify (its mod id is "bettergrass", not the slug)
            "lambdabettergrass",  // LambdaBetterGrass (and the "Refabricated" fork — same mod id)
            "ardagrass"           // ArdaGrass
    );

    private static final boolean FANCY_GRASS_MOD_PRESENT = fancyGrassModPresent();

    /** Full-cube vanilla blocks whose sides fancy-grass mods rewrite from neighbours. */
    private static final Set<Block> FANCY_GRASS_BLOCKS = Set.of(
            Blocks.GRASS_BLOCK,
            Blocks.PODZOL,
            Blocks.MYCELIUM,
            Blocks.CRIMSON_NYLIUM,
            Blocks.WARPED_NYLIUM
    );

    /**
     * The greedy emitter reproduces a block as six axis-aligned, unit-square faces. A full
     * collision shape is not enough to establish that this is faithful: pistons, observers, and
     * many modded blocks are full cubes while their baked model changes with orientation. Those
     * models must remain on the renderer's normal path (issues #5 and #6).
     *
     * <p>Use property names rather than the individual {@code BlockStateProperties} constants so
     * this common source keeps compiling across the supported Minecraft mappings. These names are
     * the standard directional model properties used by vanilla and Fabric/NeoForge content.</p>
     */
    private static final Set<String> ORIENTATION_PROPERTIES = Set.of("facing", "axis", "rotation");

    private GreedyEligibility() {
    }

    private static boolean fancyGrassModPresent() {
        FabricLoader loader = FabricLoader.getInstance();
        for (String id : FANCY_GRASS_MOD_IDS) {
            if (loader.isModLoaded(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * VulkanMod depth-sorts translucent geometry per emitted quad (one sort-order point per quad,
     * re-sorted as the camera moves — see {@code QuadSorter}), with no equivalent to Sodium's
     * {@code TranslucentGeometryCollector}. Merging water into large quads collapses many blocks'
     * worth of independent sort points into one, which visibly breaks back-to-front ordering against
     * neighbouring geometry as the camera moves (confirmed in-game: merged water faces flicker/
     * disappear on VulkanMod). Shrinking merged quads back down to avoid this would remove basically
     * all the benefit of merging water in the first place, so greedy water is soft-disabled here.
     */
    private static final boolean VULKANMOD_PRESENT = FabricLoader.getInstance().isModLoaded("vulkanmod");

    //? if UNOBFUSCATED {
    /*public static final boolean GREEDY_WATER_SUPPORTED = false;
    public static final String GREEDY_WATER_UNSUPPORTED_TOOLTIP = "Not supported yet on this Minecraft version.";
    *///?} else {
    public static final boolean GREEDY_WATER_SUPPORTED = !VULKANMOD_PRESENT;
    public static final String GREEDY_WATER_UNSUPPORTED_TOOLTIP = VULKANMOD_PRESENT
            ? "Not supported on VulkanMod: translucency depth-sort breaks with large merged water quads."
            : null;
    //?}

    public static boolean isGreedyOpaqueCube(BlockState state, BlockGetter level, BlockPos pos) {
        Boolean cached = CACHE.get(state);
        if (cached != null) {
            return cached;
        }
        boolean result = !state.isAir()
                && state.getRenderShape() == RenderShape.MODEL
                && state.getFluidState().isEmpty()
                && !state.hasBlockEntity()
                //? if >=1.21.2 {
                && state.isSolidRender()
                //?} else {
                /*&& state.isSolidRender(level, pos)
                *///?}
                && state.isCollisionShapeFullBlock(level, pos)
                && !hasOrientationProperty(state)
                && !(FANCY_GRASS_MOD_PRESENT && FANCY_GRASS_BLOCKS.contains(state.getBlock()));
        CACHE.put(state, result);
        return result;
    }

    private static boolean hasOrientationProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (ORIENTATION_PROPERTIES.contains(property.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True for plain still-water source blocks when the "Greedy Water" option is on. Deliberately
     * excludes waterlogged blocks (stairs, kelp, sea pickles, etc.) — those keep rendering their
     * water layer through the normal per-block fluid path — and flowing water, which this method
     * can't tell apart from a source block's own slope (that distinction is left to the per-face
     * flatness check each mixin runs before merging).
     */
    public static boolean isGreedyWaterSource(BlockState state, BlockGetter level, BlockPos pos) {
        //? if UNOBFUSCATED {
        /*// Not yet supported on this branch: BlockRenderDispatcher's package and the fabric-api
        // fluid-rendering module's jar-in-jar resolution both differ here and haven't been verified.
        return false;
        *///?} else {
        if (!GreedyConfig.greedyWater() || VULKANMOD_PRESENT) {
            return false;
        }
        if (!state.is(Blocks.WATER)) {
            return false;
        }
        FluidState fluid = state.getFluidState();
        return fluid.isSource() && fluid.getType() == Fluids.WATER;
        //?}
    }

    /** Clear the eligibility cache (e.g. on resource reload). */
    public static void clearCache() {
        CACHE.clear();
    }
}
