package hi.sierra.greedy_meshing.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import hi.sierra.greedy_meshing.GreedyConfig;

import java.lang.reflect.Method;
import java.util.Locale;

public final class GreedyRuntimeState {
    private static Method irisGetInstance;
    private static Method irisIsShaderPackInUse;
    private static boolean irisChecked;

    private static Object sableCompanionInstance;
    private static Method sableGetContaining;
    private static boolean sableChecked;

    private static final String BACKEND_NAME = FabricLoader.getInstance().isModLoaded("vulkanmod") ? "VulkanMod"
            : FabricLoader.getInstance().isModLoaded("sodium") ? "Sodium"
            : "Vanilla";

    private GreedyRuntimeState() {
    }

    public static boolean isRuntimeGreedyActive() {
        return GreedyConfig.enabled();
    }

    /** Which chunk-render backend our mixins are hooking into, for the F3 overlay. */
    public static String backendName() {
        return BACKEND_NAME;
    }

    /** Uses matching per-block edges to avoid view-dependent T-junction gaps (issue also seen on desktop GPUs). */
    public static boolean requiresCrackSafeSubdivision() {
        return GreedyConfig.gpuCrackFix();
    }

    public static boolean isShaderPackActive() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisGetInstance = irisApi.getMethod("getInstance");
                Object instance = irisGetInstance.invoke(null);
                irisIsShaderPackInUse = instance.getClass().getMethod("isShaderPackInUse");
            } catch (Exception ignored) {
                irisGetInstance = null;
                irisIsShaderPackInUse = null;
            }
        }
        if (irisGetInstance == null || irisIsShaderPackInUse == null) return false;
        try {
            Object instance = irisGetInstance.invoke(null);
            return (boolean) irisIsShaderPackInUse.invoke(instance);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * True when the given chunk (section x/z) lies inside a Sable sub-level's plot grid.
     * Sable applies its own dynamic directional shading to sub-level geometry (built without
     * baked shading), so our own {@code world.getShade} on top of that double-darkens it (issue #4).
     * Soft dependency via reflection on sable-companion — a fixed API published specifically
     * for optional third-party mod compatibility, with a safe no-op fallback when Sable is absent.
     */
    public static boolean isInSableSubLevel(int chunkX, int chunkZ) {
        if (!sableChecked) {
            sableChecked = true;
            try {
                Class<?> api = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                sableCompanionInstance = api.getField("INSTANCE").get(null);
                sableGetContaining = api.getMethod("getContaining",
                        net.minecraft.world.level.Level.class, int.class, int.class);
            } catch (Throwable ignored) {
                sableCompanionInstance = null;
                sableGetContaining = null;
            }
        }
        if (sableGetContaining == null) {
            return false;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return false;
            }
            return sableGetContaining.invoke(sableCompanionInstance, mc.level, chunkX, chunkZ) != null;
        } catch (Throwable ignored) {
            return false;
        }
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

    private static Method[] cachedOptionMethods;
    private static String cachedOptionClass;

    private static Object readOptionValue(Object options, String methodName) {
        try {
            String className = options.getClass().getName();
            if (cachedOptionMethods == null || !className.equals(cachedOptionClass)) {
                cachedOptionClass = className;
                Method optionMethod = options.getClass().getMethod(methodName);
                optionMethod.setAccessible(true);
                Object sample = optionMethod.invoke(options);
                if (sample == null) { cachedOptionMethods = null; return null; }
                Method getMethod = sample.getClass().getMethod("get");
                getMethod.setAccessible(true);
                cachedOptionMethods = new Method[]{optionMethod, getMethod};
            }
            Object option = cachedOptionMethods[0].invoke(options);
            if (option == null) return null;
            return cachedOptionMethods[1].invoke(option);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
