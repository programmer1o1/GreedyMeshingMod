package hi.sierra.greedy_meshing.client;

import net.minecraft.client.Minecraft;
import hi.sierra.greedy_meshing.GreedyConfig;

import java.lang.reflect.Method;
import java.util.Locale;

public final class GreedyRuntimeState {
    private static Method irisGetInstance;
    private static Method irisIsShaderPackInUse;
    private static boolean irisChecked;

    private GreedyRuntimeState() {
    }

    public static boolean isRuntimeGreedyActive() {
        return GreedyConfig.enabled();
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
