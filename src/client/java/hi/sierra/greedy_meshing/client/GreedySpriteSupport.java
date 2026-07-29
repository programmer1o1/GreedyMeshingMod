package hi.sierra.greedy_meshing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
//? if UNOBFUSCATED {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else if >=1.21.5 {
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
//?} else {
import net.minecraft.client.resources.model.BakedModel;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class GreedySpriteSupport {
    private static final ConcurrentHashMap<BlockState, Boolean> CACHE = new ConcurrentHashMap<>();

    private GreedySpriteSupport() {
    }

    public static boolean supportsGreedySpriteSizes(BlockState state) {
        Boolean cached = CACHE.get(state);
        if (cached != null) {
            return cached;
        }

        boolean result = state.is(Blocks.WATER) || supportsModelSprites(state);
        CACHE.put(state, result);
        return result;
    }

    private static boolean supportsModelSprites(BlockState state) {
        //? if UNOBFUSCATED {
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        RandomSource random = RandomSource.create(0L);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        for (Direction face : Direction.values()) {
            if (!supportsFaceLayers(parts, face)) {
                return false;
            }
        }
        return true;
        //?} else if >=1.21.5 {
        /*BlockStateModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        RandomSource random = RandomSource.create(0L);
        List<BlockModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        for (Direction face : Direction.values()) {
            if (!supportsFaceLayers(parts, face)) {
                return false;
            }
        }
        return true;
        *///?} else {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        RandomSource random = RandomSource.create(0L);
        for (Direction face : Direction.values()) {
            if (!supportsFaceLayers(model, state, face, random)) {
                return false;
            }
        }
        return true;
        //?}
    }

    //? if UNOBFUSCATED {
    private static boolean supportsFaceLayers(List<BlockStateModelPart> parts, Direction face) {
        boolean found = false;
        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(face)) {
                found = true;
                if (!isGreedyCompatible(quad.materialInfo().sprite())) {
                    return false;
                }
            }
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == face) {
                    found = true;
                    if (!isGreedyCompatible(quad.materialInfo().sprite())) {
                        return false;
                    }
                }
            }
        }
        return found;
    }
    //?} else if >=1.21.5 {
    /*private static boolean supportsFaceLayers(List<BlockModelPart> parts, Direction face) {
        boolean found = false;
        for (BlockModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(face)) {
                found = true;
                if (!isGreedyCompatible(quad.sprite())) {
                    return false;
                }
            }
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == face) {
                    found = true;
                    if (!isGreedyCompatible(quad.sprite())) {
                        return false;
                    }
                }
            }
        }
        return found;
    }
    *///?} else {
    private static boolean supportsFaceLayers(BakedModel model, BlockState state, Direction face, RandomSource random) {
        boolean found = false;
        for (BakedQuad quad : model.getQuads(state, face, random)) {
            found = true;
            if (!isGreedyCompatible(quad.getSprite())) {
                return false;
            }
        }
        for (BakedQuad quad : model.getQuads(state, null, random)) {
            if (quad.getDirection() == face) {
                found = true;
                if (!isGreedyCompatible(quad.getSprite())) {
                    return false;
                }
            }
        }
        return found;
    }
    //?}

    private static boolean isGreedyCompatible(TextureAtlasSprite sprite) {
        return sprite.contents().width() == 16 && sprite.contents().height() == 16;
    }
}
