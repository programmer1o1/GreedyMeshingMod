package hi.sierra.greedy_meshing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.ConcurrentHashMap;

public final class GreedyEligibility {
    private static final ConcurrentHashMap<BlockState, Boolean> CACHE = new ConcurrentHashMap<>();

    private GreedyEligibility() {
    }

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
                /*&& state.isSolidRender()
                *///?} else {
                && state.isSolidRender(level, pos)
                //?}
                && state.isCollisionShapeFullBlock(level, pos);
        CACHE.put(state, result);
        return result;
    }

    /** Clear the eligibility cache (e.g. on resource reload). */
    public static void clearCache() {
        CACHE.clear();
    }
}
