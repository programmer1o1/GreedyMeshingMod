package org.Jean.greedy_meshing.client;

import java.util.concurrent.atomic.LongAdder;

public final class GreedyPerformanceStats {
    private static final LongAdder VANILLA_COMPILE_HOOKS = new LongAdder();
    private static final LongAdder SODIUM_TASK_HOOKS = new LongAdder();
    private static final LongAdder GREEDY_SECTIONS = new LongAdder();
    private static final LongAdder ELIGIBLE_BLOCKS = new LongAdder();
    private static final LongAdder MERGED_QUADS = new LongAdder();
    private static final LongAdder EMITTED_QUADS = new LongAdder();

    private GreedyPerformanceStats() {
    }

    public static void onVanillaCompileHook() {
        VANILLA_COMPILE_HOOKS.increment();
    }

    public static void onSodiumTaskHook() {
        SODIUM_TASK_HOOKS.increment();
    }

    public static void onGreedySectionBuilt(int eligibleBlocks, int mergedQuads, int emittedQuads) {
        GREEDY_SECTIONS.increment();
        ELIGIBLE_BLOCKS.add(Math.max(0, eligibleBlocks));
        MERGED_QUADS.add(Math.max(0, mergedQuads));
        EMITTED_QUADS.add(Math.max(0, emittedQuads));
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                VANILLA_COMPILE_HOOKS.sum(),
                SODIUM_TASK_HOOKS.sum(),
                GREEDY_SECTIONS.sum(),
                ELIGIBLE_BLOCKS.sum(),
                MERGED_QUADS.sum(),
                EMITTED_QUADS.sum()
        );
    }

    public record Snapshot(
            long vanillaCompileHooks,
            long sodiumTaskHooks,
            long greedySections,
            long eligibleBlocks,
            long mergedQuads,
            long emittedQuads
    ) {
    }
}
