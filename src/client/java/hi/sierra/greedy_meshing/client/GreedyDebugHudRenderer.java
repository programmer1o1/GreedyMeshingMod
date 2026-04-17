package hi.sierra.greedy_meshing.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
//? if UNOBFUSCATED {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import hi.sierra.greedy_meshing.GreedyConfig;

public final class GreedyDebugHudRenderer {

    private GreedyDebugHudRenderer() {
    }

    //? if UNOBFUSCATED {
    /*public static void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
    *///?} else {
    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
    //?}
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }

        if (!GreedyConfig.debugComparison()) {
            return;
        }

        // Center dividing line + labels for split-screen comparison
        int screenW = guiGraphics.guiWidth();
        int screenH = guiGraphics.guiHeight();
        int centerX = screenW / 2;
        guiGraphics.fill(centerX - 1, 0, centerX + 1, screenH, 0xBBFFFFFF);
        int labelY = 4;
        //? if UNOBFUSCATED {
        /*guiGraphics.text(mc.font, "Vanilla", centerX - 50, labelY, 0xFFFF5555, true);
        guiGraphics.text(mc.font, "Greedy Meshing", centerX + 10, labelY, 0xFF55FF55, true);
        *///?} else {
        guiGraphics.drawString(mc.font, "Vanilla", centerX - 50, labelY, 0xFFFF5555, true);
        guiGraphics.drawString(mc.font, "Greedy Meshing", centerX + 10, labelY, 0xFF55FF55, true);
        //?}
    }

    private static String fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return Long.toString(n);
    }
}
