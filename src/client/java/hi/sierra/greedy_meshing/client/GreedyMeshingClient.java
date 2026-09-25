package hi.sierra.greedy_meshing.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=1.21.9 {
/*import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//?}
//? if UNOBFUSCATED {
/*import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
*///?} else if <1.21.2 {
/*import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
*///?} else if >=1.21.11 {
/*import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
*///?}

import net.minecraft.client.Minecraft;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}
import hi.sierra.greedy_meshing.GreedyConfig;
import hi.sierra.greedy_meshing.GreedyMeshing;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class GreedyMeshingClient implements ClientModInitializer {
    //? if >=1.21.11 {
    /*private static final Identifier DEBUG_HUD_ID = Identifier.fromNamespaceAndPath(GreedyMeshing.MOD_ID, "debug_hud");
    *///?} else {
    private static final ResourceLocation DEBUG_HUD_ID = ResourceLocation.fromNamespaceAndPath(GreedyMeshing.MOD_ID, "debug_hud");
    //?}
    private static Object lastAmbientOcclusion;
    private static Object lastGamma;
    private static Object lastLevel;
    // Player section column and min-merge distance the current merge zone was last built for.
    private static int zonePlayerSectionX = Integer.MIN_VALUE;
    private static int zonePlayerSectionZ;
    private static int zoneMinDistance;
    private static int prunePendingTicks;

    @Override
    public void onInitializeClient() {
        //? if UNOBFUSCATED {
        /*LevelRenderEvents.AFTER_SOLID_FEATURES.register(GreedyWireframeRenderer::render);
        *///?} else if <1.21.2 {
        /*WorldRenderEvents.AFTER_ENTITIES.register(GreedyWireframeRenderer::render);
        *///?} else if >=1.21.11 {
        /*WorldRenderEvents.AFTER_ENTITIES.register(GreedyWireframeRenderer::render);
        *///?}
        // Wireframe overlay paths: 1.21/1.21.1 -> WorldRenderEvents (old package); 1.21.2-1.21.10 ->
        // DebugRenderer.render TAIL hook (DebugRendererMixin), since WorldRenderEvents shimmers under
        // VulkanMod 0.5.x and DebugRenderer.render was the only stable hook there; 1.21.11 -> the new
        // .world.WorldRenderEvents (vanilla removed DebugRenderer.render); 26.x -> LevelRenderEvents.
        //? if >=1.21.9 {
        /*HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, DEBUG_HUD_ID, GreedyDebugHudRenderer::render);
        *///?} else {
        HudRenderCallback.EVENT.register(GreedyDebugHudRenderer::render);
        //?}
        ClientTickEvents.END_CLIENT_TICK.register(GreedyMeshingClient::onClientTick);
        GreedyResourceReloadListener.register();
    }

    private static void onClientTick(Minecraft mc) {
        if (mc.options == null) {
            return;
        }

        // Reset stats when the world changes (join/leave)
        Object currentLevel = mc.level;
        if (currentLevel != lastLevel) {
            lastLevel = currentLevel;
            GreedyPerformanceStats.reset();
            GreedyDebugStore.clear();
            zonePlayerSectionX = Integer.MIN_VALUE;
        }

        greedyMeshing$refreshMergeZone(mc);
        greedyMeshing$pruneUnloadedSections(mc);
    }

    /**
     * Min Merge Distance (issue #19) is decided per section at build time, so walking around would
     * otherwise leave the per-block zone where the player was when each section was built. When the
     * player enters a new chunk column, rebuild exactly the columns whose near/far status flipped:
     * a one-chunk step touches only the zone's leading and trailing edges. Config changes already
     * rebuild everything, so a distance change just re-anchors the zone.
     */
    private static void greedyMeshing$refreshMergeZone(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            zonePlayerSectionX = Integer.MIN_VALUE;
            return;
        }
        int minDistance = GreedyConfig.enabled() ? GreedyConfig.minMeshDistance() : 0;
        int playerX = mc.player.getBlockX() >> 4;
        int playerZ = mc.player.getBlockZ() >> 4;
        int oldX = zonePlayerSectionX;
        int oldZ = zonePlayerSectionZ;
        int oldMinDistance = zoneMinDistance;
        zonePlayerSectionX = playerX;
        zonePlayerSectionZ = playerZ;
        zoneMinDistance = minDistance;
        if (oldX == Integer.MIN_VALUE || minDistance <= 0 || minDistance != oldMinDistance
                || (oldX == playerX && oldZ == playerZ)) {
            return;
        }

        // Columns outside render distance aren't built; dirtying them would only alias onto
        // whichever loaded section currently occupies that slot of the render grid.
        int renderDistance = mc.options.getEffectiveRenderDistance();
        int minSectionY = mc.level.getSectionYFromSectionIndex(0);
        int maxSectionY = mc.level.getSectionYFromSectionIndex(mc.level.getSectionsCount() - 1);
        // Only columns within minDistance of the old or new position can change state. Scan both
        // squares, skipping the overlap on the second pass so no column is dirtied twice.
        for (int pass = 0; pass < 2; pass++) {
            int centerX = pass == 0 ? oldX : playerX;
            int centerZ = pass == 0 ? oldZ : playerZ;
            for (int x = centerX - minDistance; x <= centerX + minDistance; x++) {
                for (int z = centerZ - minDistance; z <= centerZ + minDistance; z++) {
                    if (pass == 1 && Math.max(Math.abs(x - oldX), Math.abs(z - oldZ)) <= minDistance) {
                        continue;
                    }
                    boolean wasNear = Math.max(Math.abs(x - oldX), Math.abs(z - oldZ)) < minDistance;
                    boolean isNear = Math.max(Math.abs(x - playerX), Math.abs(z - playerZ)) < minDistance;
                    if (wasNear == isNear
                            || Math.max(Math.abs(x - playerX), Math.abs(z - playerZ)) > renderDistance) {
                        continue;
                    }
                    for (int y = minSectionY; y <= maxSectionY; y++) {
                        // Sodium and VulkanMod both redirect this vanilla entry point to their own
                        // rebuild scheduling, so one call covers every backend.
                        //? if >=26.2 {
                        /*mc.levelExtractor.setSectionDirty(x, y, z);
                        *///?} else {
                        mc.levelRenderer.setSectionDirty(x, y, z);
                        //?}
                    }
                }
            }
        }
    }

    /**
     * The live geometry totals are keyed by section and cleared when a section rebuilds, but no
     * backend gives us an unload callback. Sections that scroll out of render distance would
     * otherwise linger in the totals forever, so sweep them out periodically. Every two seconds is
     * far more often than render distance can meaningfully change.
     */
    private static void greedyMeshing$pruneUnloadedSections(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        if (--prunePendingTicks > 0) {
            return;
        }
        prunePendingTicks = 40;

        // One section of slack so a section right at the edge is not dropped and immediately re-added.
        int radius = mc.options.getEffectiveRenderDistance() + 1;
        int playerSectionX = mc.player.getBlockX() >> 4;
        int playerSectionZ = mc.player.getBlockZ() >> 4;
        GreedyPerformanceStats.pruneBeyond(playerSectionX, playerSectionZ, radius);

        if (mc.level != null && GreedyDebugStore.countSections() > 0) {
            GreedyDebugStore.removeSectionsIf(key -> {
                int sx = net.minecraft.core.SectionPos.x(key);
                int sy = net.minecraft.core.SectionPos.y(key);
                int sz = net.minecraft.core.SectionPos.z(key);
                if (Math.abs(sx - playerSectionX) > radius || Math.abs(sz - playerSectionZ) > radius) return true;
                if (!mc.level.hasChunk(sx, sz)) return true;
                int idx = mc.level.getSectionIndexFromSectionY(sy);
                if (idx < 0 || idx >= mc.level.getSectionsCount()) return true;
                return mc.level.getChunk(sx, sz).getSection(idx).hasOnlyAir();
            });
        }

        Object ambientOcclusion = readOptionValue(mc.options, "ambientOcclusion");
        Object gamma = readOptionValue(mc.options, "gamma");
        if (lastAmbientOcclusion == null && lastGamma == null) {
            lastAmbientOcclusion = ambientOcclusion;
            lastGamma = gamma;
            return;
        }

        boolean aoChanged = !java.util.Objects.equals(lastAmbientOcclusion, ambientOcclusion);
        boolean gammaChanged = !java.util.Objects.equals(lastGamma, gamma);
        if (!aoChanged && !gammaChanged) {
            return;
        }
        lastAmbientOcclusion = ambientOcclusion;
        lastGamma = gamma;
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

    private static final ConcurrentHashMap<String, Method[]> METHOD_CACHE = new ConcurrentHashMap<>();

    private static Object readOptionValue(Object options, String methodName) {
        try {
            String cacheKey = options.getClass().getName() + "." + methodName;
            Method[] methods = METHOD_CACHE.computeIfAbsent(cacheKey, k -> {
                try {
                    Method optionMethod = options.getClass().getMethod(methodName);
                    optionMethod.setAccessible(true);
                    Object sample = optionMethod.invoke(options);
                    if (sample == null) return null;
                    Method getMethod = sample.getClass().getMethod("get");
                    getMethod.setAccessible(true);
                    return new Method[]{optionMethod, getMethod};
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            });
            if (methods == null) return null;
            Object option = methods[0].invoke(options);
            if (option == null) return null;
            return methods[1].invoke(option);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
