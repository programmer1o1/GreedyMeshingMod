package org.Jean.greedy_meshing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public final class GreedyEligibility {
    private GreedyEligibility() {
    }

    public static boolean isGreedyOpaqueCube(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return !state.isAir()
                && state.getRenderShape() == RenderShape.MODEL
                && state.getFluidState().isEmpty()
                && !state.hasBlockEntity()
                && state.isSolidRender()
                && state.isCollisionShapeFullBlock(level, pos);
    }
}
