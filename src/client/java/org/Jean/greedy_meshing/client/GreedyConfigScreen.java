package org.Jean.greedy_meshing.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.Jean.greedy_meshing.GreedyConfig;

public final class GreedyConfigScreen {
    private GreedyConfigScreen() {
    }

    public static Screen create(Screen parent) {
        GreedyConfig.Data draft = GreedyConfig.snapshot();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Greedy Meshing"))
                .setSavingRunnable(() -> {
                    GreedyConfig.apply(draft);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.levelRenderer != null) {
                        mc.levelRenderer.allChanged();
                    }
                });

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        ConfigEntryBuilder entries = builder.entryBuilder();

        general.addEntry(entries.startBooleanToggle(Component.literal("Enabled"), draft.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> draft.enabled = v)
                .build());

        general.addEntry(entries.startBooleanToggle(Component.literal("Debug Wireframe"), draft.debugWireframe)
                .setDefaultValue(false)
                .setSaveConsumer(v -> draft.debugWireframe = v)
                .build());

        general.addEntry(entries.startBooleanToggle(Component.literal("Debug Triangles (Overlay + HUD)"), draft.debugTrianglesHud)
                .setDefaultValue(false)
                .setSaveConsumer(v -> draft.debugTrianglesHud = v)
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
