package org.Jean.greedy_meshing.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
    @Inject(method = "onLightUpdate", at = @At("TAIL"))
    private void greedyMeshing$markSectionDirtyOnLightUpdate(LightLayer layer, SectionPos sectionPos, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) {
            mc.levelRenderer.setSectionDirtyWithNeighbors(sectionPos.x(), sectionPos.y(), sectionPos.z());
        }
    }
}
