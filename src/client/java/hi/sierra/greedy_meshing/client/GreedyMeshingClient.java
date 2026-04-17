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
*///?} else if <1.21.9 {
/*import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
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

    @Override
    public void onInitializeClient() {
        //? if UNOBFUSCATED {
        /*LevelRenderEvents.AFTER_SOLID_FEATURES.register(GreedyWireframeRenderer::render);
        *///?} else if <1.21.9 {
        /*WorldRenderEvents.AFTER_ENTITIES.register(GreedyWireframeRenderer::render);
        *///?}
        // Note: wireframe debug not available on 1.21.9-1.21.10 (WorldRenderEvents removed, FabricRenderPipeline needed)
        //? if >=1.21.9 {
        /*HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, DEBUG_HUD_ID, GreedyDebugHudRenderer::render);
        *///?} else {
        HudRenderCallback.EVENT.register(GreedyDebugHudRenderer::render);
        //?}
        ClientTickEvents.END_CLIENT_TICK.register(GreedyMeshingClient::onClientTick);
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

        Object ambientOcclusion = readOptionValue(mc.options, "ambientOcclusion");
        Object gamma = readOptionValue(mc.options, "gamma");
        if (lastAmbientOcclusion == null && lastGamma == null) {
            lastAmbientOcclusion = ambientOcclusion;
            lastGamma = gamma;
            return;
        }

        boolean aoChanged = !java.util.Objects.equals(lastAmbientOcclusion, ambientOcclusion);
        boolean gammaChanged = !java.util.Objects.equals(lastGamma, gamma);
        if ((aoChanged || gammaChanged) && mc.levelRenderer != null) {
            lastAmbientOcclusion = ambientOcclusion;
            lastGamma = gamma;
            mc.levelRenderer.allChanged();
        }
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
