package hi.sierra.greedy_meshing.mixin.client;

import hi.sierra.greedy_meshing.GreedyConfig;
import hi.sierra.greedy_meshing.client.GreedyPerformanceStats;
import hi.sierra.greedy_meshing.client.GreedyRuntimeState;
//? if UNOBFUSCATED {
/*import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = DebugScreenEntries.class, remap = false)
public abstract class DebugScreenOverlayMixin {

    @Shadow
    private static Identifier register(Identifier id, DebugScreenEntry entry) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void greedyMeshing$registerDebugEntry(CallbackInfo ci) {
        register(Identifier.fromNamespaceAndPath("greedy_meshing", "stats"), new DebugScreenEntry() {
            @Override
            public void display(DebugScreenDisplayer displayer, Level level, LevelChunk clientChunk, LevelChunk serverChunk) {
                if (!GreedyConfig.enabled()) return;

                GreedyPerformanceStats.Snapshot stats = GreedyPerformanceStats.snapshot();
                boolean active = GreedyRuntimeState.isRuntimeGreedyActive();

                long vanillaQuads = stats.vanillaEquivalentQuads();
                long greedyQuads = stats.emittedQuads();
                long saved = vanillaQuads - greedyQuads;
                float pct = vanillaQuads > 0 ? (saved * 100.0f / vanillaQuads) : 0;

                Identifier groupId = Identifier.fromNamespaceAndPath("greedy_meshing", "stats_group");
                java.util.List<String> lines = new java.util.ArrayList<>();
                lines.add("[Greedy Meshing] " + (active ? "Active" : "Inactive"));
                lines.add(String.format("Quads: %s -> %s (-%s, %.1f%%)",
                        fmt(vanillaQuads), fmt(greedyQuads), fmt(saved), pct));
                lines.add(String.format("Sections: %d  Blocks: %s",
                        stats.greedySections(), fmt(stats.eligibleBlocks())));
                if (GreedyRuntimeState.isShaderPackActive()) {
                    lines.add("Shader: tiled mode (face culling only)");
                }
                displayer.addToGroup(groupId, lines);
            }

            private String fmt(long n) {
                if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
                if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
                return Long.toString(n);
            }
        });
    }
}
*///?} else if >=1.21.9 && <1.21.11 {
/*import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DebugScreenEntries.class)
public abstract class DebugScreenOverlayMixin {

    @Shadow
    private static ResourceLocation register(ResourceLocation id, DebugScreenEntry entry) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void greedyMeshing$registerDebugEntry(CallbackInfo ci) {
        register(ResourceLocation.fromNamespaceAndPath("greedy_meshing", "stats"), new DebugScreenEntry() {
            @Override
            public void display(DebugScreenDisplayer displayer, Level level, LevelChunk clientChunk, LevelChunk serverChunk) {
                if (!GreedyConfig.enabled()) return;

                GreedyPerformanceStats.Snapshot stats = GreedyPerformanceStats.snapshot();
                boolean active = GreedyRuntimeState.isRuntimeGreedyActive();

                long vanillaQuads = stats.vanillaEquivalentQuads();
                long greedyQuads = stats.emittedQuads();
                long saved = vanillaQuads - greedyQuads;
                float pct = vanillaQuads > 0 ? (saved * 100.0f / vanillaQuads) : 0;

                ResourceLocation groupId = ResourceLocation.fromNamespaceAndPath("greedy_meshing", "stats_group");
                java.util.List<String> lines = new java.util.ArrayList<>();
                lines.add("[Greedy Meshing] " + (active ? "Active" : "Inactive"));
                lines.add(String.format("Quads: %s -> %s (-%s, %.1f%%)",
                        fmt(vanillaQuads), fmt(greedyQuads), fmt(saved), pct));
                lines.add(String.format("Sections: %d  Blocks: %s",
                        stats.greedySections(), fmt(stats.eligibleBlocks())));
                if (GreedyRuntimeState.isShaderPackActive()) {
                    lines.add("Shader: tiled mode (face culling only)");
                }
                displayer.addToGroup(groupId, lines);
            }

            private String fmt(long n) {
                if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
                if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
                return Long.toString(n);
            }
        });
    }
}
*///?} else if >=1.21.11 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;renderLines(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;Z)V"),
            require = 0
    )
    private void greedyMeshing$wrapRenderLines(DebugScreenOverlay instance, GuiGraphics graphics, List<String> lines, boolean rightSide, Operation<Void> original) {
        if (!rightSide) {
            List<String> mutable = new ArrayList<>(lines);
            greedyMeshing$addLines(mutable);
            original.call(instance, graphics, mutable, rightSide);
        } else {
            original.call(instance, graphics, lines, rightSide);
        }
    }

    @Unique
    private static void greedyMeshing$addLines(List<String> lines) {
        if (!GreedyConfig.enabled()) return;

        GreedyPerformanceStats.Snapshot stats = GreedyPerformanceStats.snapshot();
        boolean active = GreedyRuntimeState.isRuntimeGreedyActive();

        long vanillaQuads = stats.vanillaEquivalentQuads();
        long greedyQuads = stats.emittedQuads();
        long saved = vanillaQuads - greedyQuads;
        float pct = vanillaQuads > 0 ? (saved * 100.0f / vanillaQuads) : 0;

        lines.add("");
        lines.add("[Greedy Meshing] " + (active ? "Active" : "Inactive: " + GreedyRuntimeState.inactiveReason()));
        lines.add(String.format("Quads: %s -> %s (-%s, %.1f%%)",
                greedyMeshing$fmt(vanillaQuads), greedyMeshing$fmt(greedyQuads), greedyMeshing$fmt(saved), pct));
        lines.add(String.format("Sections: %d  Blocks: %s",
                stats.greedySections(), greedyMeshing$fmt(stats.eligibleBlocks())));

        if (GreedyRuntimeState.isShaderPackActive()) {
            lines.add("Shader: tiled mode (face culling only)");
        }
    }

    @Unique
    private static String greedyMeshing$fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return Long.toString(n);
    }
}
*///?} else {
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {

    @Inject(method = "getGameInformation", at = @At("RETURN"), require = 0)
    private void greedyMeshing$appendDebugInfo(CallbackInfoReturnable<List<String>> cir) {
        if (!GreedyConfig.enabled()) return;

        List<String> lines = cir.getReturnValue();
        GreedyPerformanceStats.Snapshot stats = GreedyPerformanceStats.snapshot();
        boolean active = GreedyRuntimeState.isRuntimeGreedyActive();

        long vanillaQuads = stats.vanillaEquivalentQuads();
        long greedyQuads = stats.emittedQuads();
        long saved = vanillaQuads - greedyQuads;
        float pct = vanillaQuads > 0 ? (saved * 100.0f / vanillaQuads) : 0;

        lines.add("");
        lines.add("[Greedy Meshing] " + (active ? "Active" : "Inactive: " + GreedyRuntimeState.inactiveReason()));
        lines.add(String.format("Quads: %s -> %s (-%s, %.1f%%)",
                greedyMeshing$fmt(vanillaQuads), greedyMeshing$fmt(greedyQuads), greedyMeshing$fmt(saved), pct));
        lines.add(String.format("Sections: %d  Blocks: %s",
                stats.greedySections(), greedyMeshing$fmt(stats.eligibleBlocks())));

        if (GreedyRuntimeState.isShaderPackActive()) {
            lines.add("Shader: tiled mode (face culling only)");
        }
    }

    @Unique
    private static String greedyMeshing$fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return Long.toString(n);
    }
}
//?}
