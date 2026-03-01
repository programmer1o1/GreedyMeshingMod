package org.Jean.greedy_meshing.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.Jean.greedy_meshing.GreedyMeshing;

public final class GreedyMeshingClient implements ClientModInitializer {
    private static final Identifier DEBUG_HUD_ID = Identifier.fromNamespaceAndPath(GreedyMeshing.MOD_ID, "debug_hud");
    private static Object lastAmbientOcclusion;
    private static Object lastGamma;

    @Override
    public void onInitializeClient() {
        WorldRenderEvents.AFTER_ENTITIES.register(GreedyWireframeRenderer::render);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, DEBUG_HUD_ID, GreedyDebugHudRenderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(GreedyMeshingClient::onClientTick);
    }

    private static void onClientTick(Minecraft mc) {
        if (mc.options == null) {
            return;
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

    private static Object readOptionValue(Object options, String methodName) {
        try {
            Object option = options.getClass().getMethod(methodName).invoke(options);
            if (option == null) {
                return null;
            }
            return option.getClass().getMethod("get").invoke(option);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
