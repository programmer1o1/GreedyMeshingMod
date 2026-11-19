package hi.sierra.greedy_meshing.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import hi.sierra.greedy_meshing.GreedyConfig;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final boolean FLAT_LIGHTING_LOADED = FabricLoader.getInstance().isModLoaded("flat-lighting");
    private static volatile boolean flatLighting;
    private static volatile long flatLightingCheckNanos;
    private static volatile long flatLightingModified = Long.MIN_VALUE;

    /**
     * True when Flat Lighting is installed and its cardinal lighting is off (reads its config only).
     * The file is stat'ed at most once a second and only re-parsed when it changed. Flat Lighting
     * defaults cardinalLighting to true and only writes the file once the option is toggled, so a
     * missing, unreadable or key-less file means "not flat".
     */
    public static boolean isFlatLighting() {
        if (!FLAT_LIGHTING_LOADED) {
            return false;
        }
        long now = System.nanoTime();
        if (now - flatLightingCheckNanos > 1_000_000_000L) {
            flatLightingCheckNanos = now;
            Path file = FabricLoader.getInstance().getConfigDir().resolve("flat-lighting.json");
            try {
                long modified = Files.getLastModifiedTime(file).toMillis();
                if (modified != flatLightingModified) {
                    flatLightingModified = modified;
                    JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    flatLighting = json.has("cardinalLighting") && !json.get("cardinalLighting").getAsBoolean();
                }
            } catch (Exception e) {
                flatLightingModified = Long.MIN_VALUE;
                flatLighting = false;
            }
        }
        return flatLighting;
    }

    public static boolean isRuntimeGreedyActive() {
        return GreedyConfig.enabled();
    }

    /** Null-origin overload for call sites that haven't resolved a section position yet — treated
     *  as active so behavior matches the pre-distance-check default. */
    public static boolean isRuntimeGreedyActive(BlockPos sectionOrigin) {
        if (sectionOrigin == null) {
            return isRuntimeGreedyActive();
        }
        return isRuntimeGreedyActive(sectionOrigin.getX() >> 4, sectionOrigin.getZ() >> 4);
    }

    /** True when greedy meshing should apply to the section at (sectionX, sectionZ) — i.e. it's
     *  enabled and, if a minimum distance is configured, the section is far enough from the player
     *  that merged-quad texture-rotation artifacts (issue #19) aren't visible up close. */
    public static boolean isRuntimeGreedyActive(int sectionX, int sectionZ) {
        if (!GreedyConfig.enabled()) {
            return false;
        }
        int minDistance = GreedyConfig.minMeshDistance();
        if (minDistance <= 0) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return true;
        }
        int dx = sectionX - (mc.player.getBlockX() >> 4);
        int dz = sectionZ - (mc.player.getBlockZ() >> 4);
        return Math.max(Math.abs(dx), Math.abs(dz)) >= minDistance;
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
