package hi.sierra.greedy_meshing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code /greedybench [rounds]}: for each round, resets the build-time counters, rebuilds every
 * loaded section and waits for the rebuild to go quiet, then prints the per-section greedy build
 * time. After the last round it prints the median and spread. It exists because F3+L's
 * compile-queue metric is blind to Sodium's chunk builder.
 */
public final class GreedyBench {
    /** No new sections for this long means a round has settled (2 seconds). */
    private static final int QUIET_TICKS = 40;
    private static final int TIMEOUT_TICKS = 20 * 90;

    private static boolean active;
    private static int totalRounds;
    private static int round;
    private static final List<Double> ROUND_AVERAGES = new ArrayList<>();
    private static long lastCount;
    private static int quietTicks;
    private static int elapsedTicks;

    private GreedyBench() {
    }

    public static void start(Minecraft mc, int rounds) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        totalRounds = Math.max(1, rounds);
        round = 0;
        ROUND_AVERAGES.clear();
        say(mc, "Greedy bench: " + totalRounds + " round(s), stand still...");
        beginRound(mc);
    }

    private static void beginRound(Minecraft mc) {
        round++;
        GreedyPerformanceStats.reset();
        active = true;
        lastCount = 0;
        quietTicks = 0;
        elapsedTicks = 0;
        //? if >=26.2 {
        /*if (mc.levelExtractor != null) {
            mc.levelExtractor.allChanged();
        }
        *///?} else {
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
        //?}
    }

    public static void tick(Minecraft mc) {
        if (!active) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            active = false;
            return;
        }
        elapsedTicks++;
        long count = GreedyPerformanceStats.buildCount();
        if (count > 0 && count == lastCount) {
            quietTicks++;
        } else {
            quietTicks = 0;
            lastCount = count;
        }
        boolean timedOut = elapsedTicks >= TIMEOUT_TICKS;
        if (quietTicks < QUIET_TICKS && !timedOut) {
            return;
        }
        active = false;
        double avg = GreedyPerformanceStats.buildAverageMillis();
        ROUND_AVERAGES.add(avg);
        say(mc, String.format("Round %d/%d: avg %.3f ms over %d sections%s",
                round, totalRounds, avg, count, timedOut ? " (timed out)" : ""));
        if (round < totalRounds) {
            beginRound(mc);
        } else {
            summarize(mc);
        }
    }

    private static void summarize(Minecraft mc) {
        List<Double> sorted = new ArrayList<>(ROUND_AVERAGES);
        Collections.sort(sorted);
        double median = sorted.size() % 2 == 1
                ? sorted.get(sorted.size() / 2)
                : (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.0;
        // The first round is dominated by JIT warm-up, so also report the mean without it.
        double warmMean = 0;
        int warmCount = 0;
        for (int i = 1; i < ROUND_AVERAGES.size(); i++) {
            warmMean += ROUND_AVERAGES.get(i);
            warmCount++;
        }
        say(mc, String.format("Greedy bench done: median %.3f ms, min %.3f, max %.3f%s",
                median, sorted.get(0), sorted.get(sorted.size() - 1),
                warmCount > 0 ? String.format(", mean w/o round 1 %.3f", warmMean / warmCount) : ""));
    }

    private static void say(Minecraft mc, String text) {
        if (mc.player == null) {
            return;
        }
        //? if UNOBFUSCATED {
        /*mc.player.sendSystemMessage(Component.literal(text));
        *///?} else {
        mc.player.displayClientMessage(Component.literal(text), false);
        //?}
    }
}
