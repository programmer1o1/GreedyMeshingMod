package hi.sierra.greedy_meshing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GreedyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("greedy_meshing.json");
    private static Data data = new Data();

    private GreedyConfig() {
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) {
                loaded.clamp();
                data = loaded;
            }
        } catch (IOException ignored) {
        }
    }

    public static void save() {
        data.clamp();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static boolean enabled() {
        return data.enabled;
    }

    public static boolean aggressiveGreedy() {
        return data.aggressiveGreedy;
    }

    public static boolean greedyWater() {
        return data.greedyWater;
    }

    public static boolean debugWireframe() {
        return data.debugWireframe;
    }

    public static boolean debugComparison() {
        return data.debugComparison;
    }

    public static boolean debugTrianglesHud() {
        return data.debugTrianglesHud;
    }

    public static boolean debugScreenOverlay() {
        return data.debugScreenOverlay;
    }

    public static float meshOpacity() {
        return data.meshOpacity;
    }

    public static Data snapshot() {
        Data copy = new Data();
        copy.enabled = data.enabled;
        copy.aggressiveGreedy = data.aggressiveGreedy;
        copy.greedyWater = data.greedyWater;
        copy.debugWireframe = data.debugWireframe;
        copy.debugComparison = data.debugComparison;
        copy.debugTrianglesHud = data.debugTrianglesHud;
        copy.debugScreenOverlay = data.debugScreenOverlay;
        copy.meshOpacity = data.meshOpacity;
        return copy;
    }

    public static void apply(Data newData) {
        newData.clamp();
        data = newData;
        save();
    }

    public static final class Data {
        public boolean enabled = true;
        /** Merge coplanar faces of the same block regardless of their ambient-occlusion signature,
         *  producing the largest possible quads ("absolute" greedy). Fewer quads at the cost of
         *  slightly coarser per-vertex lighting on big merged surfaces. */
        public boolean aggressiveGreedy = false;
        /** Merge flat interior-lake/ocean still-water top, side, and bottom faces into larger quads,
         *  the same way solid blocks are merged. Only applies where the water surface is provably
         *  flat (no slope) — shorelines and flowing water always fall back to normal per-block
         *  rendering, so this never changes how non-flat water looks. No effect on VulkanMod (its
         *  per-quad translucency depth-sort breaks visibly when water is merged into large quads). */
        public boolean greedyWater = false;
        public boolean debugWireframe = false;
        public boolean debugComparison = false;
        public boolean debugTrianglesHud = false;
        public boolean debugScreenOverlay = true;
        public float meshOpacity = 0.35f;
        public void clamp() {
            if (meshOpacity < 0.0f) {
                meshOpacity = 0.0f;
            } else if (meshOpacity > 1.0f) {
                meshOpacity = 1.0f;
            }
        }
    }
}
