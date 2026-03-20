package org.Jean.greedy_meshing.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.Jean.greedy_meshing.GreedyConfig;

public final class GreedyDebugHudRenderer {
    private static final int RADIUS = 1;

    private GreedyDebugHudRenderer() {
    }

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!GreedyConfig.debugTrianglesHud() && !GreedyConfig.debugWireframe()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }

        Vec3 playerPos = mc.player.position();
        int sectionX = SectionPos.blockToSectionCoord(Mth.floor(playerPos.x));
        int sectionY = SectionPos.blockToSectionCoord(Mth.floor(playerPos.y));
        int sectionZ = SectionPos.blockToSectionCoord(Mth.floor(playerPos.z));

        int quads = GreedyDebugStore.countQuadsNear(sectionX, sectionY, sectionZ, RADIUS);
        int triangles = quads * 2;
        int sections = GreedyDebugStore.countSectionsNear(sectionX, sectionY, sectionZ, RADIUS);
        int totalQuads = GreedyDebugStore.countQuads();
        int totalTriangles = totalQuads * 2;
        int totalSections = GreedyDebugStore.countSections();

        int x = 6;
        int lineHeight = 10;
        int color = 0x90FF90;
        boolean runtimeActive = GreedyRuntimeState.isRuntimeGreedyActive();
        int statusColor = runtimeActive ? color : 0xFF8080;
        int totalLines = runtimeActive ? 11 : 12;
        int y = Math.max(6, guiGraphics.guiHeight() - totalLines * lineHeight - 6);

        guiGraphics.drawString(mc.font, "Greedy Meshing", x, y, color, true);
        guiGraphics.drawString(mc.font, "Config: " + (GreedyConfig.enabled() ? "Enabled" : "Disabled"), x, y + lineHeight, color, true);
        guiGraphics.drawString(mc.font, "Runtime: " + (runtimeActive ? "Active" : "Inactive"), x, y + lineHeight * 2, statusColor, true);
        GreedyPerformanceStats.Snapshot stats = GreedyPerformanceStats.snapshot();
        guiGraphics.drawString(mc.font, "Hooks V/S: " + stats.vanillaCompileHooks() + "/" + stats.sodiumTaskHooks(), x, y + lineHeight * 3, color, true);
        if (!runtimeActive) {
            guiGraphics.drawString(mc.font, "Reason: " + GreedyRuntimeState.inactiveReason(), x, y + lineHeight * 4, statusColor, true);
        }
        int metricsStart = runtimeActive ? 4 : 5;
        guiGraphics.drawString(mc.font, "Near sections: " + sections, x, y + lineHeight * metricsStart, color, true);
        guiGraphics.drawString(mc.font, "Near quads: " + quads, x, y + lineHeight * (metricsStart + 1), color, true);
        guiGraphics.drawString(mc.font, "Near triangles: " + triangles, x, y + lineHeight * (metricsStart + 2), color, true);
        guiGraphics.drawString(mc.font, "Tracked sections: " + totalSections, x, y + lineHeight * (metricsStart + 3), color, true);
        guiGraphics.drawString(mc.font, "Tracked triangles: " + totalTriangles, x, y + lineHeight * (metricsStart + 4), color, true);
        guiGraphics.drawString(mc.font, "Greedy sections: " + stats.greedySections(), x, y + lineHeight * (metricsStart + 5), color, true);
        guiGraphics.drawString(mc.font, "Merged->Emitted quads: " + stats.mergedQuads() + "->" + stats.emittedQuads(), x, y + lineHeight * (metricsStart + 6), color, true);
    }
}
