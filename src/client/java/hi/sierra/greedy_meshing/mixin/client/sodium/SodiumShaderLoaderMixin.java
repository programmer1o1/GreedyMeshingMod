package hi.sierra.greedy_meshing.mixin.client.sodium;

//? if SODIUM {
//? if >=26.2 {
/*import net.minecraft.client.renderer.ShaderManager;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;
*///?} else {
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

//? if >=26.2 {
/*// Sodium 0.9.x for 26.2 dropped its own GL shader-loading abstraction (ShaderLoader/GlShader are
// gone — raw OpenGL access was removed) and builds a vanilla Blaze3D RenderPipeline instead, which
// reads shader source through vanilla's own ShaderManager keyed on (Identifier, ShaderType). Sodium
// registers ONE Identifier ("sodium:blocks/block_layer_opaque") for both stages, told apart by type.
@Mixin(ShaderManager.class)
public abstract class SodiumShaderLoaderMixin {

    @Inject(method = "getShader", at = @At("RETURN"), cancellable = true, remap = false)
    private void greedyMeshing$injectGreedyShaderCode(
            Identifier identifier,
            ShaderType type,
            CallbackInfoReturnable<String> cir
    ) {
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        boolean sodiumTerrainShader = "sodium".equals(namespace)
                && "blocks/block_layer_opaque".equals(path);
        // Milkshade Dynamics redirects Sodium's terrain pipeline to its own shader resource. Its
        // replacement preserves Sodium's v_TexCoord/u_BlockTex interface, so apply the same greedy
        // UV reconstruction to it. Without this, every vertex keeps the sprite-centre UV emitted
        // by the greedy path and merged faces render as a single flat colour (issue #5).
        boolean milkshadeTerrainShader = "milkshade".equals(namespace)
                && "sodium/block_layer_opaque".equals(path);
        if (!sodiumTerrainShader && !milkshadeTerrainShader) {
            return;
        }
        String source = cir.getReturnValue();
        if (source == null) {
            return;
        }

        if (type == ShaderType.VERTEX) {
            cir.setReturnValue(greedyMeshing$injectVertexShader(source));
        } else if (type == ShaderType.FRAGMENT) {
            cir.setReturnValue(greedyMeshing$injectFragmentShader(source));
        }
    }
*///?} else {
@Mixin(ShaderLoader.class)
public abstract class SodiumShaderLoaderMixin {

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, remap = false)
    private static void greedyMeshing$injectGreedyShaderCode(
            //? if >=1.21.11 {
            /*Identifier identifier,
            *///?} else {
            ResourceLocation identifier,
            //?}
            CallbackInfoReturnable<String> cir
    ) {
        if (!"sodium".equals(identifier.getNamespace())) {
            return;
        }
        String path = identifier.getPath();
        String source = cir.getReturnValue();
        if (source == null) {
            return;
        }

        if ("blocks/block_layer_opaque.vsh".equals(path)) {
            cir.setReturnValue(greedyMeshing$injectVertexShader(source));
        } else if ("blocks/block_layer_opaque.fsh".equals(path)) {
            cir.setReturnValue(greedyMeshing$injectFragmentShader(source));
        }
    }
    //?}

    @Unique
    private static String greedyMeshing$injectVertexShader(String source) {
        // Skip injection if the shader already has greedy code
        if (source.contains("v_GreedyFaceId")) {
            return source;
        }

        // Add varying declarations before void main()
        source = source.replace(
                "void main() {",
                "out vec3 v_BlockPos;\nout float v_GreedyFaceId;\n\nvoid main() {"
        );

        // Add assignments at the end of main()
        int lastBrace = source.lastIndexOf('}');
        if (lastBrace < 0) return source;
        source = source.substring(0, lastBrace)
                + "\n    v_BlockPos = _vert_position;\n"
                + "    v_GreedyFaceId = _vert_color.a * 255.0;\n"
                + source.substring(lastBrace);

        return source;
    }

    @Unique
    private static final Pattern GREEDY_MESHING$SAMPLER_PATTERN = Pattern.compile("uniform\\s+sampler2D\\s+(\\w+)");

    @Unique
    private static String greedyMeshing$injectFragmentShader(String source) {
        // Skip injection if the shader already has greedy code
        if (source.contains("v_GreedyFaceId")) {
            return source;
        }

        // Find the block texture sampler name
        String samplerName = "u_BlockTex";
        Matcher m = GREEDY_MESHING$SAMPLER_PATTERN.matcher(source);
        if (m.find()) {
            samplerName = m.group(1);
        }

        // 1. Add varying declarations before void main()
        source = source.replace(
                "void main() {",
                "in vec3 v_BlockPos;\nin float v_GreedyFaceId;\n\nvoid main() {"
        );

        // 2. Replace v_TexCoord with _gm_TexCoord in main() body only
        //    Split at void main() so declarations stay untouched
        int mainIdx = source.indexOf("void main() {");
        if (mainIdx < 0) return source;
        int bodyStart = source.indexOf('{', mainIdx);
        String header = source.substring(0, bodyStart + 1);
        String body = source.substring(bodyStart + 1);
        // Use word-boundary-aware replacement to avoid partial matches
        body = body.replaceAll("(?<![a-zA-Z0-9_])v_TexCoord(?![a-zA-Z0-9_])", "_gm_TexCoord");
        boolean hasVertexColor = source.contains("v_Color");
        if (hasVertexColor) {
            // Alpha contains our face marker, not opacity. Route only main's uses through a
            // corrected copy so cutout discard still sees the texture's unmodified alpha.
            body = body.replaceAll("(?<![a-zA-Z0-9_])v_Color(?![a-zA-Z0-9_])", "_gm_VertexColor");
        }
        source = header + body;

        // 3. Insert greedy UV computation right after "void main() {"
        //    This code reads the ORIGINAL v_TexCoord varying (untouched in declarations)
        //    and writes to _gm_TexCoord which the rest of the shader now uses.
        String injection =
                "\n    // ---- Greedy Meshing UV tiling ----\n"
                + "    int _gm_faceId = int(round(v_GreedyFaceId));\n"
                + "    bool _gm_isGreedy = _gm_faceId >= 234 && _gm_faceId <= 251;\n"
                + (hasVertexColor
                    ? "    vec4 _gm_VertexColor = v_Color;\n    if (_gm_isGreedy) _gm_VertexColor.a = 1.0;\n"
                    : "")
                + "    vec2 _gm_TexCoord = v_TexCoord;\n"
                + "    if (_gm_isGreedy) {\n"
                + "        int _gm_spritePixels;\n"
                + "        int _gm_face;\n"
                + "        if (_gm_faceId >= 234 && _gm_faceId <= 239) { _gm_spritePixels = 64; _gm_face = _gm_faceId - 234; }\n"
                + "        else if (_gm_faceId >= 240 && _gm_faceId <= 245) { _gm_spritePixels = 32; _gm_face = _gm_faceId - 240; }\n"
                + "        else { _gm_spritePixels = 16; _gm_face = _gm_faceId - 246; }\n"
                + "        vec2 _gm_spriteSize = float(_gm_spritePixels) / vec2(textureSize(" + samplerName + ", 0));\n"
                + "        vec2 _gm_spriteOrigin = v_TexCoord - _gm_spriteSize * 0.5;\n"
                + "        vec2 _gm_local;\n"
                + "        if      (_gm_face == 0) _gm_local = vec2(fract(v_BlockPos.x), 1.0 - fract(v_BlockPos.z));\n"
                + "        else if (_gm_face == 1) _gm_local = fract(v_BlockPos.xz);\n"
                + "        else if (_gm_face == 2) _gm_local = vec2(1.0 - fract(v_BlockPos.x), 1.0 - fract(v_BlockPos.y));\n"
                + "        else if (_gm_face == 3) _gm_local = vec2(fract(v_BlockPos.x), 1.0 - fract(v_BlockPos.y));\n"
                + "        else if (_gm_face == 4) _gm_local = vec2(fract(v_BlockPos.z), 1.0 - fract(v_BlockPos.y));\n"
                + "        else                    _gm_local = vec2(1.0 - fract(v_BlockPos.z), 1.0 - fract(v_BlockPos.y));\n"
                + "        vec2 _gm_uv = _gm_spriteOrigin + _gm_local * _gm_spriteSize;\n"
                + "        vec2 _gm_halfTexel = 0.5 / vec2(textureSize(" + samplerName + ", 0));\n"
                + "        _gm_TexCoord = clamp(_gm_uv, _gm_spriteOrigin + _gm_halfTexel, _gm_spriteOrigin + _gm_spriteSize - _gm_halfTexel);\n"
                + "    }\n"
                + "    // Derive gradients from smooth block coordinates, not the fract() UV.\n"
                + "    // The latter has a discontinuity at every block boundary and can make\n"
                + "    // distant patterned textures choose unstable mip levels.\n"
                + "    vec2 _gm_du = dFdx(v_TexCoord);\n"
                + "    vec2 _gm_dv = dFdy(v_TexCoord);\n"
                + "    vec2 _gm_texelScreenSize = sqrt(_gm_du * _gm_du + _gm_dv * _gm_dv);\n"
                + "    if (_gm_isGreedy) {\n"
                + "        int _gm_spritePixels = _gm_faceId >= 234 && _gm_faceId <= 239 ? 64 : (_gm_faceId >= 240 && _gm_faceId <= 245 ? 32 : 16);\n"
                + "        vec2 _gm_spriteSize = float(_gm_spritePixels) / vec2(textureSize(" + samplerName + ", 0));\n"
                + "        int _gm_face = _gm_faceId >= 234 && _gm_faceId <= 239 ? _gm_faceId - 234 : (_gm_faceId >= 240 && _gm_faceId <= 245 ? _gm_faceId - 240 : _gm_faceId - 246);\n"
                + "        if (_gm_face <= 1) { _gm_du = dFdx(v_BlockPos.xz) * _gm_spriteSize; _gm_dv = dFdy(v_BlockPos.xz) * _gm_spriteSize; }\n"
                + "        else if (_gm_face <= 3) { _gm_du = dFdx(v_BlockPos.xy) * _gm_spriteSize; _gm_dv = dFdy(v_BlockPos.xy) * _gm_spriteSize; }\n"
                + "        else { _gm_du = dFdx(v_BlockPos.zy) * _gm_spriteSize; _gm_dv = dFdy(v_BlockPos.zy) * _gm_spriteSize; }\n"
                + "        _gm_texelScreenSize = sqrt(_gm_du * _gm_du + _gm_dv * _gm_dv);\n"
                + "    }\n"
                + "    // ---- End Greedy Meshing ----\n";

        source = source.replace("void main() {", "void main() {" + injection);

        // Sodium's stock sampler computes derivatives from the interpolated texture coordinate.
        // For greedy faces use the explicit smooth gradients above; retain Sodium's RGSS path
        // for ordinary vanilla faces.
        String stockSampling = "vec4 color = u_UseRGSS ? sampleRGSS(" + samplerName
                + ", _gm_TexCoord, u_TexelSize) : sampleNearest(" + samplerName
                + ", _gm_TexCoord, u_TexelSize);";
        String stableSampling = "vec4 color = _gm_isGreedy ? sampleNearest(" + samplerName
                + ", _gm_TexCoord, u_TexelSize, _gm_du, _gm_dv, _gm_texelScreenSize)"
                + " : (u_UseRGSS ? sampleRGSS(" + samplerName + ", _gm_TexCoord, u_TexelSize)"
                + " : sampleNearest(" + samplerName + ", _gm_TexCoord, u_TexelSize));";
        source = source.replace(stockSampling, stableSampling);

        // Sodium 0.6.x uses a direct texture() call rather than the newer sampleNearest/RGSS
        // expression above. Give greedy faces the same stable gradients on that path as well.
        String legacySampling = "texture(" + samplerName + ", _gm_TexCoord, v_MaterialMipBias)";
        String stableLegacySampling = "(_gm_isGreedy ? textureGrad(" + samplerName
                + ", _gm_TexCoord, _gm_du, _gm_dv) : texture(" + samplerName
                + ", _gm_TexCoord, v_MaterialMipBias))";
        source = source.replace(legacySampling, stableLegacySampling);

        return source;
    }
}
//?}
