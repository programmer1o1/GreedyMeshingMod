package hi.sierra.greedy_meshing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}

/** Resolves the conventional OptiFine/Iris {@code _e} companion texture for a block sprite. */
public final class GreedyEmissiveSupport {
    private GreedyEmissiveSupport() {
    }

    public static TextureAtlasSprite find(TextureAtlasSprite base) {
        //? if UNOBFUSCATED {
        /*Minecraft mc = Minecraft.getInstance();
        Identifier baseId = base.contents().name();
        Identifier emissiveId = Identifier.fromNamespaceAndPath(baseId.getNamespace(), baseId.getPath() + "_e");
        Identifier textureId = emissiveId.withPath(path -> "textures/" + path + ".png");
        if (mc.getResourceManager().getResource(textureId).isEmpty()) {
            return null;
        }
        return mc.getAtlasManager().getAtlasOrThrow(Identifier.fromNamespaceAndPath("minecraft", "blocks")).getSprite(emissiveId);
*///?} else if >=1.21.11 {
        /*var mc = Minecraft.getInstance();
        Identifier baseId = base.contents().name();
        Identifier emissiveId = Identifier.fromNamespaceAndPath(baseId.getNamespace(), baseId.getPath() + "_e");
        Identifier textureId = emissiveId.withPath(path -> "textures/" + path + ".png");
        if (mc.getResourceManager().getResource(textureId).isEmpty()) {
            return null;
        }
        return mc.getAtlasManager().getAtlasOrThrow(Identifier.fromNamespaceAndPath("minecraft", "blocks")).getSprite(emissiveId);
        *///?} else if >=1.21.9 {
        /*var mc = Minecraft.getInstance();
        ResourceLocation baseId = base.contents().name();
        ResourceLocation emissiveId = ResourceLocation.fromNamespaceAndPath(baseId.getNamespace(), baseId.getPath() + "_e");
        ResourceLocation textureId = emissiveId.withPath(path -> "textures/" + path + ".png");
        if (mc.getResourceManager().getResource(textureId).isEmpty()) {
            return null;
        }
        return mc.getAtlasManager().getAtlasOrThrow(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks")).getSprite(emissiveId);
        *///?} else {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation baseId = base.contents().name();
        ResourceLocation emissiveId = ResourceLocation.fromNamespaceAndPath(baseId.getNamespace(), baseId.getPath() + "_e");
        ResourceLocation textureId = emissiveId.withPath(path -> "textures/" + path + ".png");
        if (mc.getResourceManager().getResource(textureId).isEmpty()) {
            return null;
        }
        return mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(emissiveId);
        //?}
    }
}
