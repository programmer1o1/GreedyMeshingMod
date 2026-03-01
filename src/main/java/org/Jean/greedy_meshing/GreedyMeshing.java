package org.Jean.greedy_meshing;

import net.fabricmc.api.ModInitializer;

public final class GreedyMeshing implements ModInitializer {
    public static final String MOD_ID = "greedy_meshing";

    @Override
    public void onInitialize() {
        GreedyConfig.load();
    }
}
