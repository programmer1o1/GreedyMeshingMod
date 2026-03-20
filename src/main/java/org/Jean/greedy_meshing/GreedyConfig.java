package org.Jean.greedy_meshing;

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

    public static boolean debugWireframe() {
        return data.debugWireframe;
    }

    public static boolean debugTrianglesHud() {
        return data.debugTrianglesHud;
    }

    public static boolean experimentalMergedQuads() {
        return data.experimentalMergedQuads;
    }

    public static float meshOpacity() {
        return data.meshOpacity;
    }

    public static Data snapshot() {
        Data copy = new Data();
        copy.enabled = data.enabled;
        copy.debugWireframe = data.debugWireframe;
        copy.debugTrianglesHud = data.debugTrianglesHud;
        copy.experimentalMergedQuads = data.experimentalMergedQuads;
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
        public boolean debugWireframe = false;
        public boolean debugTrianglesHud = false;
        public boolean experimentalMergedQuads = false;
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
