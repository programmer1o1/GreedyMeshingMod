package hi.sierra.greedy_meshing.client;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Soft compatibility with ColorLight (reflection only, no compile-time dependency).
 *
 * <p>ColorLight has two ways of colouring light:</p>
 * <ul>
 *   <li><b>GPU pipeline</b>: lit per pixel in a shader from a world-space volume. Meshes are
 *   uncoloured, so merged quads are fine as long as the greedy UV patch is also applied to
 *   ColorLight's copy of the terrain shader (see {@code SodiumShaderLoaderMixin}).</li>
 *   <li><b>Vertex colours</b> (fallback when the pipeline is off, unsupported, or an Iris shader
 *   pack is active): ColorLight bakes the colour into each block's model quads while Sodium
 *   meshes them. Greedy quads never go through the model, so they would come out uncoloured.
 *   In that mode blocks that sit next to coloured light are left to the normal per-block path;
 *   everything in light-free areas still merges.</li>
 * </ul>
 */
public final class GreedyColorLightCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("greedy_meshing");
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("colorlight");

    private static volatile boolean resolved;
    private static volatile boolean broken;
    private static MethodHandle gpuActive;     // () -> boolean
    private static MethodHandle engineGet;     // () -> ColorLightEngine (may be null)
    private static MethodHandle lightFreeAround; // (ColorLightEngine, int, int, int) -> boolean

    private GreedyColorLightCompat() {
    }

    /**
     * True when this block must keep going through the model path because ColorLight has to bake
     * coloured light into its vertices and there is coloured light around it.
     */
    public static boolean mustStayPerBlock(int worldX, int worldY, int worldZ) {
        if (!LOADED || broken) {
            return false;
        }
        if (!resolved) {
            resolve();
            if (broken) {
                return false;
            }
        }
        try {
            if ((boolean) gpuActive.invokeExact()) {
                return false;
            }
            Object engine = engineGet.invoke();
            if (engine == null) {
                return false;
            }
            return !(boolean) lightFreeAround.invoke(engine, worldX, worldY, worldZ);
        } catch (Throwable t) {
            markBroken(t);
            return false;
        }
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        try {
            ClassLoader loader = GreedyColorLightCompat.class.getClassLoader();
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> gpu = Class.forName("me.mrhikmen.colorlight.client.core.render.gpu.ColorLightGpu", true, loader);
            Class<?> holder = Class.forName("me.mrhikmen.colorlight.client.core.light.engine.ColorLightEngineHolder", true, loader);
            Class<?> engine = Class.forName("me.mrhikmen.colorlight.client.core.light.engine.ColorLightEngine", true, loader);
            gpuActive = lookup.findStatic(gpu, "isActive", MethodType.methodType(boolean.class));
            engineGet = lookup.findStatic(holder, "get", MethodType.methodType(engine));
            lightFreeAround = lookup.findVirtual(engine, "isLightFreeAround",
                    MethodType.methodType(boolean.class, int.class, int.class, int.class))
                    .asType(MethodType.methodType(boolean.class, Object.class, int.class, int.class, int.class));
            resolved = true;
        } catch (Throwable t) {
            markBroken(t);
        }
    }

    private static void markBroken(Throwable t) {
        if (!broken) {
            broken = true;
            resolved = true;
            LOGGER.warn("Greedy Meshing: ColorLight detected but its API could not be read ({}); "
                    + "vertex-colour mode will not keep lit blocks per-block.", t.toString());
        }
    }
}
