package hi.sierra.greedy_meshing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import hi.sierra.greedy_meshing.GreedyEligibility;
//? if UNOBFUSCATED {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Vector3fc;
//?} else if >=1.21.5 {
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
//?} else {
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class GreedySpriteSupport {
    private static final ConcurrentHashMap<BlockState, Boolean> CACHE = new ConcurrentHashMap<>();

    /** Vanilla BLOCK vertex layout: x, y, z, colour, u, v, light, normal. */
    private static final int BLOCK_VERTEX_INTS = 8;
    private static final int POS_OFFSET = 0;
    private static final int UV_OFFSET = 4;
    private static final float EPSILON = 1.0e-4f;

    private GreedySpriteSupport() {
    }

    public static boolean supportsGreedySpriteSizes(BlockState state) {
        Boolean cached = CACHE.get(state);
        if (cached != null) {
            return cached;
        }

        boolean result = state.is(Blocks.WATER) || supportsModelSprites(state);
        // Blocks carrying an orientation property only reach here at all when the
        // mergeOrientedBlocks relaxation is on. The property name is a weak proxy, so verify the
        // model really is a plain cube whose UVs match what the greedy shader reconstructs.
        if (result && !state.is(Blocks.WATER) && GreedyEligibility.hasOrientationProperty(state)) {
            result = hasStandardCubeGeometry(state);
        }
        CACHE.put(state, result);
        return result;
    }

    /** Dropped when config changes, since the oriented-block verdict depends on a config flag. */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * True when every face of the baked model is a single full unit square carrying the standard
     * cube UV mapping.
     *
     * <p>The greedy emitter replaces a block with six axis-aligned unit faces and relies on
     * {@code terrain.fsh} to rebuild per-block UVs from world position. A model that insets a face,
     * omits one, or rotates a face's UVs would therefore render wrong once merged. Rather than
     * guess from the blockstate, this compares the baked UVs against exactly the mapping the shader
     * implements, so the check and the shader cannot drift apart silently.</p>
     */
    private static boolean isFullCubeFace(int[] vertices, Direction face, TextureAtlasSprite sprite) {
        if (vertices.length != BLOCK_VERTEX_INTS * 4) {
            return false;
        }
        int cornersSeen = 0;
        for (int v = 0; v < 4; v++) {
            int base = v * BLOCK_VERTEX_INTS;
            float x = Float.intBitsToFloat(vertices[base + POS_OFFSET]);
            float y = Float.intBitsToFloat(vertices[base + POS_OFFSET + 1]);
            float z = Float.intBitsToFloat(vertices[base + POS_OFFSET + 2]);
            float u = Float.intBitsToFloat(vertices[base + UV_OFFSET]);
            float uv = Float.intBitsToFloat(vertices[base + UV_OFFSET + 1]);
            int corner = checkVertex(x, y, z, u, uv, face, sprite);
            if (corner < 0) {
                return false;
            }
            cornersSeen |= corner;
        }
        // All four distinct corners, so the quad spans the face rather than degenerating.
        return cornersSeen == 0b1111;
    }

    //? if UNOBFUSCATED {
    /**
     * 26.x's {@code BakedQuad} carries per-vertex data as {@code position(int)}/{@code packedUV(int)}
     * rather than the classic {@code int[] vertices()} blob the other branches read, so it has its
     * own extraction here; both funnel into the same {@link #checkVertex} logic.
     */
    private static boolean isFullCubeFace(BakedQuad quad, Direction face, TextureAtlasSprite sprite) {
        int cornersSeen = 0;
        for (int v = 0; v < 4; v++) {
            Vector3fc pos = quad.position(v);
            long packedUV = quad.packedUV(v);
            float u = Float.intBitsToFloat((int) (packedUV >>> 32));
            float uv = Float.intBitsToFloat((int) packedUV);
            int corner = checkVertex(pos.x(), pos.y(), pos.z(), u, uv, face, sprite);
            if (corner < 0) {
                return false;
            }
            cornersSeen |= corner;
        }
        return cornersSeen == 0b1111;
    }
    //?} else if >=1.21.11 {
    /*
    // 1.21.11 already carries the position(int)/packedUV(int) shape 26.x has; only materialInfo()
    // vs. a direct sprite() accessor differs, which the caller (faceIsFullCube) supplies.
    private static boolean isFullCubeFace(BakedQuad quad, Direction face, TextureAtlasSprite sprite) {
        int cornersSeen = 0;
        for (int v = 0; v < 4; v++) {
            org.joml.Vector3fc pos = quad.position(v);
            long packedUV = quad.packedUV(v);
            float u = Float.intBitsToFloat((int) (packedUV >>> 32));
            float uv = Float.intBitsToFloat((int) packedUV);
            int corner = checkVertex(pos.x(), pos.y(), pos.z(), u, uv, face, sprite);
            if (corner < 0) {
                return false;
            }
            cornersSeen |= corner;
        }
        return cornersSeen == 0b1111;
    }
    *///?}

    /**
     * True when every face of the baked model is a single full unit square carrying the standard
     * cube UV mapping.
     *
     * <p>The greedy emitter replaces a block with six axis-aligned unit faces and relies on
     * {@code terrain.fsh} to rebuild per-block UVs from world position. A model that insets a face,
     * omits one, or rotates a face's UVs would therefore render wrong once merged. Rather than
     * guess from the blockstate, this compares the baked UVs against exactly the mapping the shader
     * implements, so the check and the shader cannot drift apart silently.</p>
     *
     * @return a bit identifying which of the face's four corners this vertex is, or -1 if the
     *         vertex disqualifies the face from being treated as a plain full cube face.
     */
    private static int checkVertex(float x, float y, float z, float u, float v, Direction face, TextureAtlasSprite sprite) {
        if (!isUnit(x) || !isUnit(y) || !isUnit(z)) {
            return -1;
        }
        // The vertex must sit on the face's own plane, not somewhere inside the cube.
        float alongNormal = switch (face.getAxis()) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
        float expectedPlane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0f : 0.0f;
        if (Math.abs(alongNormal - expectedPlane) > EPSILON) {
            return -1;
        }

        // Mirrors the per-face local-UV derivation in terrain.fsh.
        float localU;
        float localV;
        switch (face) {
            case DOWN  -> { localU = x;        localV = 1.0f - z; }
            case UP    -> { localU = x;        localV = z; }
            case NORTH -> { localU = 1.0f - x; localV = 1.0f - y; }
            case SOUTH -> { localU = x;        localV = 1.0f - y; }
            case WEST  -> { localU = z;        localV = 1.0f - y; }
            default    -> { localU = 1.0f - z; localV = 1.0f - y; }
        }

        float expectedU = sprite.getU0() + localU * (sprite.getU1() - sprite.getU0());
        float expectedV = sprite.getV0() + localV * (sprite.getV1() - sprite.getV0());
        // Tolerance is relative to sprite extent so it holds regardless of atlas size.
        float uTolerance = Math.abs(sprite.getU1() - sprite.getU0()) * 0.01f + EPSILON;
        float vTolerance = Math.abs(sprite.getV1() - sprite.getV0()) * 0.01f + EPSILON;
        if (Math.abs(u - expectedU) > uTolerance || Math.abs(v - expectedV) > vTolerance) {
            return -1;
        }

        return 1 << (Math.round(localU) * 2 + Math.round(localV));
    }

    private static boolean isUnit(float value) {
        return Math.abs(value) < EPSILON || Math.abs(value - 1.0f) < EPSILON;
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

    /** Every one of the six faces must be present and be a plain full-face quad. */
    private static boolean hasStandardCubeGeometry(BlockState state) {
        //? if UNOBFUSCATED {
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        RandomSource random = RandomSource.create(0L);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        for (Direction face : Direction.values()) {
            if (!faceIsFullCube(parts, face)) {
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
            if (!faceIsFullCube(parts, face)) {
                return false;
            }
        }
        return true;
        *///?} else {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        RandomSource random = RandomSource.create(0L);
        for (Direction face : Direction.values()) {
            if (!faceIsFullCube(model, state, face, random)) {
                return false;
            }
        }
        return true;
        //?}
    }

    //? if UNOBFUSCATED {
    private static boolean faceIsFullCube(List<BlockStateModelPart> parts, Direction face) {
        boolean found = false;
        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(face)) {
                found = true;
                if (!isFullCubeFace(quad, face, quad.materialInfo().sprite())) {
                    return false;
                }
            }
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == face) {
                    found = true;
                    if (!isFullCubeFace(quad, face, quad.materialInfo().sprite())) {
                        return false;
                    }
                }
            }
        }
        return found;
    }
    //?} else if >=1.21.11 {
    /*
    // 1.21.11 moved BakedQuad from a raw int[] vertex blob to position(int)/packedUV(int)
    // accessors, the same restructure the 26.x line has, but sprite() stays a direct accessor
    // here rather than moving behind materialInfo() as it does on 26.x.
    private static boolean faceIsFullCube(List<BlockModelPart> parts, Direction face) {
        boolean found = false;
        for (BlockModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(face)) {
                found = true;
                if (!isFullCubeFace(quad, face, quad.sprite())) {
                    return false;
                }
            }
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == face) {
                    found = true;
                    if (!isFullCubeFace(quad, face, quad.sprite())) {
                        return false;
                    }
                }
            }
        }
        return found;
    }
    *///?} else if >=1.21.5 {
    /*private static boolean faceIsFullCube(List<BlockModelPart> parts, Direction face) {
        boolean found = false;
        for (BlockModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(face)) {
                found = true;
                if (!isFullCubeFace(quad.vertices(), face, quad.sprite())) {
                    return false;
                }
            }
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == face) {
                    found = true;
                    if (!isFullCubeFace(quad.vertices(), face, quad.sprite())) {
                        return false;
                    }
                }
            }
        }
        return found;
    }
    *///?} else {
    private static boolean faceIsFullCube(BakedModel model, BlockState state, Direction face, RandomSource random) {
        boolean found = false;
        for (BakedQuad quad : model.getQuads(state, face, random)) {
            found = true;
            if (!isFullCubeFace(quad.getVertices(), face, quad.getSprite())) {
                return false;
            }
        }
        for (BakedQuad quad : model.getQuads(state, null, random)) {
            if (quad.getDirection() == face) {
                found = true;
                if (!isFullCubeFace(quad.getVertices(), face, quad.getSprite())) {
                    return false;
                }
            }
        }
        return found;
    }
    //?}
}
