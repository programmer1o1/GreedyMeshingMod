package org.Jean.greedy_meshing.client;

import net.minecraft.client.Minecraft;
import org.Jean.greedy_meshing.GreedyConfig;

import java.util.Locale;

public final class GreedyRuntimeState {
    private GreedyRuntimeState() {
    }

    public static boolean isRuntimeGreedyActive() {
        return GreedyConfig.enabled();
    }

    public static String inactiveReason() {
        if (!GreedyConfig.enabled()) {
            return "Config disabled";
        }
        return "Unknown";
    }

    public static boolean isSmoothLightingEnabled() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return true;
        }
        Object aoValue = readOptionValue(mc.options, "ambientOcclusion");
        if (aoValue == null) {
            return true;
        }

        if (aoValue instanceof Boolean bool) {
            return bool;
        }
        if (aoValue instanceof Number number) {
            return number.intValue() != 0;
        }

        String normalized = String.valueOf(aoValue).trim().toUpperCase(Locale.ROOT);
        return !normalized.equals("OFF")
                && !normalized.equals("FALSE")
                && !normalized.equals("0");
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
