package hi.sierra.greedy_meshing.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import hi.sierra.greedy_meshing.GreedyConfig;

public final class GreedyConfigScreen {
    private GreedyConfigScreen() {
    }

    public static Screen create(Screen parent) {
        GreedyConfig.Data draft = GreedyConfig.snapshot();
        boolean wasGreedyWaterEnabled = GreedyConfig.greedyWater();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Greedy Meshing"))
                .setSavingRunnable(() -> {
                    GreedyConfig.apply(draft);
                    Minecraft mc = Minecraft.getInstance();
                    //? if >=26.2 {
                    /*if (mc.levelExtractor != null) {
                        mc.levelExtractor.allChanged();
                    }
                    *///?} else {
                    if (mc.levelRenderer != null) {
                        mc.levelRenderer.allChanged();
                    }
                    //?}
                    if (draft.greedyWater && !wasGreedyWaterEnabled) {
                        // Known-buggy: some merged water surfaces render black/missing faces on
                        // Sodium (root cause not yet found — CPU-side geometry is correct per the
                        // debug wireframe, so it's somewhere in the actual draw data). Surfaced as a
                        // toast rather than silently shipping broken-looking water, per user request.
                        Component title = Component.literal("Greedy Water is experimental");
                        Component message = Component.literal("Known issue: some water surfaces may render with missing or black faces. Turn it off if you see this.");
                        //? if >=26.2 {
                        /*SystemToast.add(mc.gui.toastManager(), new SystemToast.SystemToastId(), title, message);
                        *///?} else if >=1.21.2 {
                        SystemToast.add(mc.getToastManager(), new SystemToast.SystemToastId(), title, message);
                        //?} else {
                        /*SystemToast.add(mc.getToasts(), new SystemToast.SystemToastId(), title, message);
                        *///?}
                    }
                });

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        ConfigEntryBuilder entries = builder.entryBuilder();

        general.addEntry(entries.startBooleanToggle(Component.literal("Enabled"), draft.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> draft.enabled = v)
                .build());

        general.addEntry(entries.startBooleanToggle(Component.literal("Aggressive Greedy (Absolute)"), draft.aggressiveGreedy)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Merge same-block faces into the largest possible quads, ignoring ambient-occlusion boundaries. Fewer quads; slightly coarser lighting on large flat surfaces."))
                .setSaveConsumer(v -> draft.aggressiveGreedy = v)
                .build());

        // Greedy Water has no effect on these two backends (see GreedyEligibility.isGreedyWaterSource):
        // VulkanMod's per-quad translucency depth-sort visibly breaks when water is merged into large
        // quads (confirmed in-game), and 26.x isn't wired up yet (BlockRenderDispatcher's package and
        // the fabric-api fluid-rendering module's jar-in-jar resolution both differ there). Disable the
        // toggle itself rather than silently no-op-ing, so it isn't confusing to turn on and see nothing.
        boolean greedyWaterUnsupported;
        String greedyWaterTooltip;
        //? if UNOBFUSCATED {
        /*greedyWaterUnsupported = true;
        greedyWaterTooltip = "Not supported yet on this Minecraft version.";
        *///?} else {
        greedyWaterUnsupported = FabricLoader.getInstance().isModLoaded("vulkanmod");
        greedyWaterTooltip = greedyWaterUnsupported
                ? "Not supported on VulkanMod: its translucency depth-sort visibly breaks when water is merged into large quads."
                : "Merge still-water faces into larger quads, but ONLY where the water surface is perfectly flat (e.g. open ocean/lake interiors). Shorelines and flowing water are unaffected. EXPERIMENTAL — known issue: some water surfaces render with missing or black faces on Sodium; root cause not yet found.";
        //?}
        BooleanListEntry greedyWaterEntry = entries.startBooleanToggle(Component.literal("Greedy Water (Flat Surfaces)"), draft.greedyWater)
                .setDefaultValue(false)
                .setTooltip(Component.literal(greedyWaterTooltip))
                .setSaveConsumer(v -> draft.greedyWater = v)
                .build();
        greedyWaterEntry.setEditable(!greedyWaterUnsupported);
        general.addEntry(greedyWaterEntry);

        general.addEntry(entries.startBooleanToggle(Component.literal("Debug Wireframe"), draft.debugWireframe)
                .setDefaultValue(false)
                .setSaveConsumer(v -> draft.debugWireframe = v)
                .build());

        general.addEntry(entries.startBooleanToggle(Component.literal("Debug Comparison (Split-Screen)"), draft.debugComparison)
                .setDefaultValue(false)
                .setSaveConsumer(v -> draft.debugComparison = v)
                .build());

        general.addEntry(entries.startBooleanToggle(Component.literal("Debug Triangles (Overlay)"), draft.debugTrianglesHud)
                .setDefaultValue(false)
                .setSaveConsumer(v -> draft.debugTrianglesHud = v)
                .build());

        general.addEntry(entries.startBooleanToggle(Component.literal("Debug Screen Overlay"), draft.debugScreenOverlay)
                .setDefaultValue(true)
                .setSaveConsumer(v -> draft.debugScreenOverlay = v)
                .build());

        general.addEntry(entries.startFloatField(Component.literal("Mesh Opacity"), draft.meshOpacity)
                .setDefaultValue(0.35f)
                .setMin(0.0f)
                .setMax(1.0f)
                .setSaveConsumer(v -> draft.meshOpacity = v)
                .build());

        return builder.build();
    }
}
