package hi.sierra.greedy_meshing.client;

import net.minecraft.core.SectionPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class GreedyPerformanceStats {
    private static final LongAdder VANILLA_COMPILE_HOOKS = new LongAdder();
    private static final LongAdder SODIUM_TASK_HOOKS = new LongAdder();
    private static final LongAdder GREEDY_SECTIONS = new LongAdder();
    private static final LongAdder ELIGIBLE_BLOCKS = new LongAdder();
    private static final LongAdder MERGED_QUADS = new LongAdder();
    private static final LongAdder EMITTED_QUADS = new LongAdder();
    private static final LongAdder VANILLA_EQUIVALENT_QUADS = new LongAdder();

    /**
     * Per-section geometry for sections that are currently built, keyed by
     * {@link SectionPos#asLong()} and holding {@code {emittedQuads, vanillaEquivalentQuads}}.
     *
     * <p>The counters above are cumulative for the whole world session, which makes them fine for
     * a reduction ratio but meaningless as an absolute memory figure: they keep climbing as you
     * play and include sections unloaded long ago. This map instead tracks only what is built right
     * now, so the byte figure describes loaded terrain.</p>
     */
    private static final Map<Long, long[]> LIVE_SECTIONS = new ConcurrentHashMap<>();
    private static final AtomicLong LIVE_EMITTED = new AtomicLong();
    private static final AtomicLong LIVE_VANILLA_EQUIVALENT = new AtomicLong();

    private GreedyPerformanceStats() {
    }

    public static void reset() {
        VANILLA_COMPILE_HOOKS.reset();
        SODIUM_TASK_HOOKS.reset();
        GREEDY_SECTIONS.reset();
        ELIGIBLE_BLOCKS.reset();
        MERGED_QUADS.reset();
        EMITTED_QUADS.reset();
        VANILLA_EQUIVALENT_QUADS.reset();
        LIVE_SECTIONS.clear();
        LIVE_EMITTED.set(0);
        LIVE_VANILLA_EQUIVALENT.set(0);
    }

    /**
     * Record what a section contributes after a (re)build. Replaces any previous value for that
     * section. {@code compute} keeps the running totals consistent when several chunk-build threads
     * touch the same key.
     */
    public static void setSectionGeometry(long sectionKey, long emittedQuads, long vanillaEquivalentQuads) {
        LIVE_SECTIONS.compute(sectionKey, (key, previous) -> {
            if (previous != null) {
                LIVE_EMITTED.addAndGet(-previous[0]);
                LIVE_VANILLA_EQUIVALENT.addAndGet(-previous[1]);
            }
            LIVE_EMITTED.addAndGet(emittedQuads);
            LIVE_VANILLA_EQUIVALENT.addAndGet(vanillaEquivalentQuads);
            return new long[]{emittedQuads, vanillaEquivalentQuads};
        });
    }

    /** Drop a section's contribution, called at the start of a rebuild and on unload. */
    public static void clearSectionGeometry(long sectionKey) {
        LIVE_SECTIONS.computeIfPresent(sectionKey, (key, previous) -> {
            LIVE_EMITTED.addAndGet(-previous[0]);
            LIVE_VANILLA_EQUIVALENT.addAndGet(-previous[1]);
            return null;
        });
    }

    /**
     * Sections are never explicitly unloaded through this class (the three renderer backends have
     * no shared unload hook), so stale entries are pruned by distance instead. Anything further
     * than the render distance from the player cannot still be built.
     */
    public static void pruneBeyond(int centerSectionX, int centerSectionZ, int radiusSections) {
        if (LIVE_SECTIONS.isEmpty()) {
            return;
        }
        for (Long key : LIVE_SECTIONS.keySet()) {
            int dx = Math.abs(SectionPos.x(key) - centerSectionX);
            int dz = Math.abs(SectionPos.z(key) - centerSectionZ);
            if (dx > radiusSections || dz > radiusSections) {
                clearSectionGeometry(key);
            }
        }
    }

    public static void onVanillaCompileHook() {
        VANILLA_COMPILE_HOOKS.increment();
    }

    public static void onSodiumTaskHook() {
        SODIUM_TASK_HOOKS.increment();
    }

    public static void onGreedySectionBuilt(int eligibleBlocks, int mergedQuads, int emittedQuads, int vanillaEquivalent) {
        GREEDY_SECTIONS.increment();
        ELIGIBLE_BLOCKS.add(Math.max(0, eligibleBlocks));
        MERGED_QUADS.add(Math.max(0, mergedQuads));
        EMITTED_QUADS.add(Math.max(0, emittedQuads));
        VANILLA_EQUIVALENT_QUADS.add(Math.max(0, vanillaEquivalent));
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                VANILLA_COMPILE_HOOKS.sum(),
                SODIUM_TASK_HOOKS.sum(),
                GREEDY_SECTIONS.sum(),
                ELIGIBLE_BLOCKS.sum(),
                MERGED_QUADS.sum(),
                EMITTED_QUADS.sum(),
                VANILLA_EQUIVALENT_QUADS.sum(),
                LIVE_SECTIONS.size(),
                LIVE_EMITTED.get(),
                LIVE_VANILLA_EQUIVALENT.get()
        );
    }

    /**
     * Vanilla terrain vertex stride: position 3f + colour 4ub + uv0 2f + uv2 2s + normal 4b.
     * Sodium packs terrain vertices smaller than this, so the byte figure derived from it is an
     * upper-bound estimate and is labelled as such wherever it is shown.
     */
    public static final int VANILLA_VERTEX_BYTES = 32;

    /**
     * One-line summary of the geometry the merge removed. Formatted here rather than at the call
     * sites because the debug overlay has four version-specific copies of the same block.
     */
    public static String formatVertexSavings(Snapshot stats) {
        return String.format("Loaded: -%s verts (~%s, est.) across %d sections",
                fmtCount(stats.liveSavedVertices()),
                fmtBytes(stats.liveApproxBytesSaved()),
                stats.liveSections());
    }

    private static String fmtCount(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return Long.toString(n);
    }

    private static String fmtBytes(long bytes) {
        if (bytes >= 1L << 20) return String.format("%.1f MiB", bytes / (double) (1L << 20));
        if (bytes >= 1L << 10) return String.format("%.1f KiB", bytes / (double) (1L << 10));
        return bytes + " B";
    }

    public record Snapshot(
            long vanillaCompileHooks,
            long sodiumTaskHooks,
            long greedySections,
            long eligibleBlocks,
            long mergedQuads,
            long emittedQuads,
            long vanillaEquivalentQuads,
            int liveSections,
            long liveEmittedQuads,
            long liveVanillaEquivalentQuads
    ) {
        /**
         * Cumulative quads the merge removed since joining the world. Useful as a ratio against
         * {@link #vanillaEquivalentQuads()}; not a description of current terrain.
         */
        public long savedQuads() {
            return Math.max(0L, vanillaEquivalentQuads - emittedQuads);
        }

        /** Quads removed across the sections that are built right now. */
        public long liveSavedQuads() {
            return Math.max(0L, liveVanillaEquivalentQuads - liveEmittedQuads);
        }

        /** Each quad is four vertices, so this is exact and independent of vertex format. */
        public long liveSavedVertices() {
            return liveSavedQuads() * 4L;
        }

        /** Upper-bound estimate, assumes the vanilla stride; Sodium's is smaller. */
        public long liveApproxBytesSaved() {
            return liveSavedVertices() * VANILLA_VERTEX_BYTES;
        }
    }
}
