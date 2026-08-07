package hi.sierra.greedy_meshing.client;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}
import hi.sierra.greedy_meshing.GreedyEligibility;
import hi.sierra.greedy_meshing.GreedyMeshing;

/**
 * A resource pack swap can change which sprites back a block's model (different pixel dimensions,
 * or a plain texture swapped for a non-tileable one), which invalidates GreedyEligibility's and
 * GreedySpriteSupport's per-BlockState caches. Nothing else clears those on a pack swap — only the
 * config screen does, as a side effect of applying settings — so without this listener, greedy
 * meshing keeps merging blocks using verdicts computed against the previous pack's sprites (e.g.
 * every block still greedy-merged as "16x-compatible" after switching to a 64x resource pack).
 */
public final class GreedyResourceReloadListener implements SimpleSynchronousResourceReloadListener {
    //? if >=1.21.11 {
    /*private static final Identifier ID = Identifier.fromNamespaceAndPath(GreedyMeshing.MOD_ID, "resource_reload");
    *///?} else {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(GreedyMeshing.MOD_ID, "resource_reload");
    //?}

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new GreedyResourceReloadListener());
    }

    //? if >=1.21.11 {
    /*@Override
    public Identifier getFabricId() {
        return ID;
    }
    *///?} else {
    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
    //?}

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        GreedyEligibility.clearCache();
        GreedySpriteSupport.clearCache();
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        /*if (mc.levelExtractor != null) {
            mc.levelExtractor.allChanged();
        }
        *///?} else {
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
        //?}
    }
}
