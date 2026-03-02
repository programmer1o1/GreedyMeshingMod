package org.Jean.greedy_meshing.client.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.Jean.greedy_meshing.GreedyConfig;
import org.Jean.greedy_meshing.GreedyMesher;

import java.util.Arrays;

public final class GreedySodiumWorkState {
    private final BlockState[] sectionStates = new BlockState[GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE * GreedyMesher.SECTION_SIZE];
    private ChunkBuildContext buildContext;
    private BlockAndTintGetter world;
    private BlockRenderer blockRenderer;
    private BlockPos sectionOrigin;
    private long sectionKey = Long.MIN_VALUE;
    private int eligibleCount;
    private boolean captureDebug;

    public void reset(ChunkBuildContext context) {
        this.buildContext = context;
        this.world = null;
        this.blockRenderer = null;
        this.sectionOrigin = null;
        this.sectionKey = Long.MIN_VALUE;
        this.eligibleCount = 0;
        this.captureDebug = GreedyConfig.debugWireframe() || GreedyConfig.debugTrianglesHud();
        Arrays.fill(this.sectionStates, null);
    }

    public BlockState[] sectionStates() {
        return sectionStates;
    }

    public ChunkBuildContext buildContext() {
        return buildContext;
    }

    public BlockAndTintGetter world() {
        return world;
    }

    public void world(BlockAndTintGetter world) {
        this.world = world;
    }

    public BlockRenderer blockRenderer() {
        return blockRenderer;
    }

    public void blockRenderer(BlockRenderer blockRenderer) {
        this.blockRenderer = blockRenderer;
    }

    public BlockPos sectionOrigin() {
        return sectionOrigin;
    }

    public void sectionOrigin(BlockPos sectionOrigin) {
        this.sectionOrigin = sectionOrigin;
    }

    public long sectionKey() {
        return sectionKey;
    }

    public void sectionKey(long sectionKey) {
        this.sectionKey = sectionKey;
    }

    public int eligibleCount() {
        return eligibleCount;
    }

    public void incrementEligibleCount() {
        this.eligibleCount++;
    }

    public boolean captureDebug() {
        return captureDebug;
    }
}
