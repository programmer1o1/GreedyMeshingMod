package hi.sierra.greedy_meshing.mixin.client;

//? if UNOBFUSCATED {
/*import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = DebugScreenEntryList.class, remap = false)
public abstract class DebugScreenEntryListMixin {

    @Shadow
    private Map<Identifier, DebugScreenEntryStatus> allStatuses;

    // After any profile is loaded / statuses are reset, ensure our entry is enabled
    @Inject(method = "resetStatuses", at = @At("TAIL"))
    private void greedyMeshing$enableEntry(Map<Identifier, DebugScreenEntryStatus> statuses, CallbackInfo ci) {
        allStatuses.put(Identifier.fromNamespaceAndPath("greedy_meshing", "stats"), DebugScreenEntryStatus.IN_OVERLAY);
    }
}
*///?} else if >=1.21.9 && <1.21.11 {
/*import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(DebugScreenEntryList.class)
public abstract class DebugScreenEntryListMixin {

    @Shadow
    private Map<ResourceLocation, DebugScreenEntryStatus> allStatuses;

    @Inject(method = "load", at = @At("TAIL"))
    private void greedyMeshing$enableEntry(CallbackInfo ci) {
        allStatuses.putIfAbsent(ResourceLocation.fromNamespaceAndPath("greedy_meshing", "stats"), DebugScreenEntryStatus.IN_F3);
    }
}
*///?} else {
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;

// Stub for versions without DebugScreenEntryList
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenEntryListMixin {
}
//?}
