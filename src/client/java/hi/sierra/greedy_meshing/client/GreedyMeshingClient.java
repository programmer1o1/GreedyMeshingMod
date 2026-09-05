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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
//?} else if >=1.21.11 {
/*import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
*///?}

import net.minecraft.client.Minecraft;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}
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
    private static int prunePendingTicks;

    @Override
    public void onInitializeClient() {
        //? if UNOBFUSCATED {
        /*LevelRenderEvents.AFTER_SOLID_FEATURES.register(GreedyWireframeRenderer::render);
        *///?} else if <1.21.2 {
        WorldRenderEvents.AFTER_ENTITIES.register(GreedyWireframeRenderer::render);
        //?} else if >=1.21.11 {
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
        }

        greedyMeshing$pruneUnloadedSections(mc);
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
