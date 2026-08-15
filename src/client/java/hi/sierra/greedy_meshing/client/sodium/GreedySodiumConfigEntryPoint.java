package hi.sierra.greedy_meshing.client.sodium;

//? if SODIUM_NEW_API {
/*import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.option.ControlValueFormatter;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.network.chat.Component;
import hi.sierra.greedy_meshing.GreedyConfig;
import hi.sierra.greedy_meshing.GreedyEligibility;

public final class GreedySodiumConfigEntryPoint implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        GreedyConfig.Data[] draft = { GreedyConfig.snapshot() };

        var storage = (net.caffeinemc.mods.sodium.api.config.StorageEventHandler) () -> {
            GreedyConfig.apply(draft[0]);
            draft[0] = GreedyConfig.snapshot();
        };

        var page = builder.createOptionPage()
                .setName(Component.literal("Greedy Meshing"));

        // --- General options ---
        var general = builder.createOptionGroup();

        general.addOption(builder.createBooleanOption(GreedySodiumIds.of("enabled"))
                .setName(Component.literal("Enabled"))
                .setTooltip(Component.literal("Enable greedy meshing. When off, the mod does nothing and vanilla chunk rendering is used."))
                .setDefaultValue(true)
                .setBinding(v -> draft[0].enabled = v, () -> draft[0].enabled)
                .setStorageHandler(storage)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD));

        general.addOption(builder.createBooleanOption(GreedySodiumIds.of("aggressive_greedy"))
                .setName(Component.literal("Aggressive Greedy (Absolute)"))
                .setTooltip(Component.literal("Merge same-block faces ignoring AO boundaries. Fewer quads; slightly coarser lighting on large flat surfaces."))
                .setDefaultValue(false)
                .setBinding(v -> draft[0].aggressiveGreedy = v, () -> draft[0].aggressiveGreedy)
                .setStorageHandler(storage)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD));

        boolean greedyWaterUnsupported = !GreedyEligibility.GREEDY_WATER_SUPPORTED;
        String greedyWaterTooltip = greedyWaterUnsupported && GreedyEligibility.GREEDY_WATER_UNSUPPORTED_TOOLTIP != null
                ? GreedyEligibility.GREEDY_WATER_UNSUPPORTED_TOOLTIP
                : "Merge flat still-water faces into larger quads (open ocean/lake interiors). EXPERIMENTAL — some surfaces may render with missing or black faces.";
        general.addOption(builder.createBooleanOption(GreedySodiumIds.of("greedy_water"))
                .setName(Component.literal("Greedy Water (Flat Surfaces)"))
                .setTooltip(Component.literal(greedyWaterTooltip))
                .setDefaultValue(false)
                .setBinding(v -> draft[0].greedyWater = v, () -> draft[0].greedyWater)
                .setStorageHandler(storage)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .setEnabled(!greedyWaterUnsupported));

        general.addOption(builder.createBooleanOption(GreedySodiumIds.of("merge_oriented_blocks"))
                .setName(Component.literal("Merge Oriented Blocks"))
                .setTooltip(Component.literal("Allow blocks with a facing/axis/rotation property (e.g. some modded blocks) to merge, but only once their model is verified to be a plain six-face cube with standard UVs. Raises the merge rate on normal terrain. EXPERIMENTAL: broadens what greedy meshing touches; report any block that renders wrong once merged."))
                .setDefaultValue(false)
                .setBinding(v -> draft[0].mergeOrientedBlocks = v, () -> draft[0].mergeOrientedBlocks)
                .setStorageHandler(storage)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD));

        general.addOption(builder.createBooleanOption(GreedySodiumIds.of("mobile_gpu_crack_fix"))
                .setName(Component.literal("Mobile GPU Crack Fix"))
                .setTooltip(Component.literal("On detected MobileGlues renderers, use crack-safe per-block visible faces. Prevents sky-colored holes but reduces geometry optimization; disable only if your device renders correctly without it."))
                .setDefaultValue(true)
                .setBinding(v -> draft[0].mobileGpuCrackFix = v, () -> draft[0].mobileGpuCrackFix)
                .setStorageHandler(storage)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD));

        page.addOptionGroup(general);

        // --- Debug options ---
        var debug = builder.createOptionGroup()
                .setName(Component.literal("Debug"));

        debug.addOption(builder.createBooleanOption(GreedySodiumIds.of("debug_wireframe"))
                .setName(Component.literal("Debug Wireframe"))
                .setTooltip(Component.literal("Render a wireframe overlay showing merged quad boundaries."))
                .setDefaultValue(false)
                .setBinding(v -> draft[0].debugWireframe = v, () -> draft[0].debugWireframe)
                .setStorageHandler(storage)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD));

        debug.addOption(builder.createBooleanOption(GreedySodiumIds.of("debug_comparison"))
                .setName(Component.literal("Debug Comparison (Split-Screen)"))
                .setTooltip(Component.literal("Split-screen comparison: left half uses greedy meshing, right half uses vanilla rendering."))
                .setDefaultValue(false)
                .setBinding(v -> draft[0].debugComparison = v, () -> draft[0].debugComparison)
                .setStorageHandler(storage)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD));

        debug.addOption(builder.createBooleanOption(GreedySodiumIds.of("debug_triangles_hud"))
                .setName(Component.literal("Debug Triangles (Overlay)"))
                .setTooltip(Component.literal("Show a HUD overlay with triangle/quad count statistics."))
                .setDefaultValue(false)
                .setBinding(v -> draft[0].debugTrianglesHud = v, () -> draft[0].debugTrianglesHud)
                .setStorageHandler(storage));

        debug.addOption(builder.createIntegerOption(GreedySodiumIds.of("mesh_opacity"))
                .setName(Component.literal("Wireframe Opacity"))
                .setTooltip(Component.literal("Opacity of the debug wireframe overlay (0–100%)."))
                .setDefaultValue(35)
                .setRange(0, 100, 1)
                .setValueFormatter(v -> Component.literal(v + "%"))
                .setBinding(v -> draft[0].meshOpacity = v / 100.0f, () -> Math.round(draft[0].meshOpacity * 100))
                .setStorageHandler(storage));

        page.addOptionGroup(debug);

        builder.registerOwnModOptions().addPage(page);
    }
}
*///?} else {
public final class GreedySodiumConfigEntryPoint {
    // Stub when neither the primary nor secondary Sodium pin for this Minecraft version exposes the
    // new config API — sodium:config_api_user is never invoked in that case.
}
//?}
