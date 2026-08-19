package hi.sierra.greedy_meshing.client.sodium;

//? if SODIUM_OLD_API {
import com.google.common.collect.ImmutableList;
import net.caffeinemc.mods.sodium.client.gui.options.OptionFlag;
import net.caffeinemc.mods.sodium.client.gui.options.OptionGroup;
import net.caffeinemc.mods.sodium.client.gui.options.OptionImpl;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.TickBoxControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlValueFormatter;
import net.caffeinemc.mods.sodium.client.gui.options.storage.OptionStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import hi.sierra.greedy_meshing.GreedyConfig;
import hi.sierra.greedy_meshing.client.GreedySpriteSupport;

public final class GreedySodiumOptionsPage {
    private GreedySodiumOptionsPage() {
    }

    public static OptionPage create() {
        OptionStorage<GreedyConfig.Data> storage = new OptionStorage<>() {
            private GreedyConfig.Data data;

            @Override
            public GreedyConfig.Data getData() {
                if (data == null) data = GreedyConfig.snapshot();
                return data;
            }

            @Override
            public void save() {
                if (data != null) {
                    GreedyConfig.apply(data);
                    // GreedyConfig.apply() only clears GreedyEligibility's cache (same sourceset).
                    // GreedySpriteSupport's cache lives in the client sourceset and has to be
                    // dropped here too, or settings like mergeOrientedBlocks keep serving stale
                    // verdicts after Sodium's REQUIRES_RENDERER_RELOAD rebuild runs.
                    GreedySpriteSupport.clearCache();
                    data = null;
                }
            }
        };

        boolean greedyWaterUnsupported = FabricLoader.getInstance().isModLoaded("vulkanmod");

        OptionGroup general = OptionGroup.createBuilder()
                .add(OptionImpl.<GreedyConfig.Data, Boolean>createBuilder(Boolean.class, storage)
                        .setName(Component.literal("Enabled"))
                        .setTooltip(Component.literal("Enable greedy meshing. When off, vanilla chunk rendering is used."))
                        .setBinding((d, v) -> d.enabled = v, d -> d.enabled)
                        .setControl(TickBoxControl::new)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.<GreedyConfig.Data, Boolean>createBuilder(Boolean.class, storage)
                        .setName(Component.literal("Aggressive Greedy (Absolute)"))
                        .setTooltip(Component.literal("Merge same-block faces ignoring AO boundaries. Fewer quads; slightly coarser lighting on large surfaces."))
                        .setBinding((d, v) -> d.aggressiveGreedy = v, d -> d.aggressiveGreedy)
                        .setControl(TickBoxControl::new)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.<GreedyConfig.Data, Boolean>createBuilder(Boolean.class, storage)
                        .setName(Component.literal("Greedy Water (Flat Surfaces)"))
                        .setTooltip(greedyWaterUnsupported
                                ? Component.literal("Not supported on VulkanMod: translucency depth-sort breaks with large merged water quads.")
                                : Component.literal("Merge flat still-water faces into larger quads. EXPERIMENTAL — some surfaces may render with missing or black faces."))
                        .setBinding((d, v) -> d.greedyWater = v, d -> d.greedyWater)
                        .setControl(TickBoxControl::new)
                        .setEnabled(() -> !greedyWaterUnsupported)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.<GreedyConfig.Data, Boolean>createBuilder(Boolean.class, storage)
                        .setName(Component.literal("Merge Oriented Blocks"))
                        .setTooltip(Component.literal("Allow blocks with a facing/axis/rotation property (e.g. some modded blocks) to merge, but only once their model is verified to be a plain six-face cube with standard UVs. Raises the merge rate on normal terrain. EXPERIMENTAL: broadens what greedy meshing touches; report any block that renders wrong once merged."))
                        .setBinding((d, v) -> d.mergeOrientedBlocks = v, d -> d.mergeOrientedBlocks)
                        .setControl(TickBoxControl::new)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.<GreedyConfig.Data, Boolean>createBuilder(Boolean.class, storage)
                        .setName(Component.literal("GPU Crack Fix"))
                        .setTooltip(Component.literal("Nudges merged faces' outer edges outward by a tiny amount to prevent view-dependent sky-colored holes, seen on some mobile and desktop GPU drivers (including Apple M-series). Adds no geometry, so it's cheap to leave on; disable only for debugging."))
                        .setBinding((d, v) -> d.gpuCrackFix = v, d -> d.gpuCrackFix)
                        .setControl(TickBoxControl::new)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build();

        OptionGroup debug = OptionGroup.createBuilder()
                .add(OptionImpl.<GreedyConfig.Data, Boolean>createBuilder(Boolean.class, storage)
                        .setName(Component.literal("Debug Wireframe"))
                        .setTooltip(Component.literal("Render a wireframe overlay showing merged quad boundaries."))
                        .setBinding((d, v) -> d.debugWireframe = v, d -> d.debugWireframe)
                        .setControl(TickBoxControl::new)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.<GreedyConfig.Data, Boolean>createBuilder(Boolean.class, storage)
                        .setName(Component.literal("Debug Comparison (Split-Screen)"))
                        .setTooltip(Component.literal("Split-screen: left half greedy meshing, right half vanilla."))
                        .setBinding((d, v) -> d.debugComparison = v, d -> d.debugComparison)
                        .setControl(TickBoxControl::new)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.<GreedyConfig.Data, Boolean>createBuilder(Boolean.class, storage)
                        .setName(Component.literal("Debug Triangles (Overlay)"))
                        .setTooltip(Component.literal("Show a HUD overlay with triangle/quad count statistics."))
                        .setBinding((d, v) -> d.debugTrianglesHud = v, d -> d.debugTrianglesHud)
                        .setControl(TickBoxControl::new)
                        .build())
                .add(OptionImpl.<GreedyConfig.Data, Integer>createBuilder(Integer.class, storage)
                        .setName(Component.literal("Wireframe Opacity"))
                        .setTooltip(Component.literal("Opacity of the debug wireframe overlay (0–100%)."))
                        .setBinding((d, v) -> d.meshOpacity = v / 100.0f, d -> Math.round(d.meshOpacity * 100))
                        .setControl(opt -> new SliderControl(opt, 0, 100, 1, ControlValueFormatter.percentage()))
                        .build())
                .build();

        return new OptionPage(Component.literal("Greedy Meshing"), ImmutableList.of(general, debug));
    }
}
//?}
