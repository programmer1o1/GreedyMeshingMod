package hi.sierra.greedy_meshing.mixin.client.sodium;

//? if SODIUM {
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        source = header + body;

        // 3. Insert greedy UV computation right after "void main() {"
        //    This code reads the ORIGINAL v_TexCoord varying (untouched in declarations)
        //    and writes to _gm_TexCoord which the rest of the shader now uses.
        String injection =
                "\n    // ---- Greedy Meshing UV tiling ----\n"
                + "    int _gm_faceId = int(round(v_GreedyFaceId));\n"
                + "    bool _gm_isGreedy = _gm_faceId >= 246 && _gm_faceId <= 251;\n"
                + "    vec2 _gm_TexCoord = v_TexCoord;\n"
                + "    if (_gm_isGreedy) {\n"
                + "        int _gm_face = _gm_faceId - 246;\n"
                + "        vec2 _gm_spriteSize = 16.0 / vec2(textureSize(" + samplerName + ", 0));\n"
                + "        vec2 _gm_spriteOrigin = v_TexCoord - _gm_spriteSize * 0.5;\n"
                + "        vec2 _gm_local;\n"
                + "        if      (_gm_face == 0) _gm_local = fract(v_BlockPos.xz);\n"
                + "        else if (_gm_face == 1) _gm_local = vec2(fract(v_BlockPos.x), 1.0 - fract(v_BlockPos.z));\n"
                + "        else if (_gm_face == 2) _gm_local = vec2(1.0 - fract(v_BlockPos.x), 1.0 - fract(v_BlockPos.y));\n"
                + "        else if (_gm_face == 3) _gm_local = vec2(fract(v_BlockPos.x), 1.0 - fract(v_BlockPos.y));\n"
                + "        else if (_gm_face == 4) _gm_local = vec2(fract(v_BlockPos.z), 1.0 - fract(v_BlockPos.y));\n"
                + "        else                    _gm_local = vec2(1.0 - fract(v_BlockPos.z), 1.0 - fract(v_BlockPos.y));\n"
                + "        vec2 _gm_uv = _gm_spriteOrigin + _gm_local * _gm_spriteSize;\n"
                + "        vec2 _gm_halfTexel = 0.5 / vec2(textureSize(" + samplerName + ", 0));\n"
                + "        _gm_TexCoord = clamp(_gm_uv, _gm_spriteOrigin + _gm_halfTexel, _gm_spriteOrigin + _gm_spriteSize - _gm_halfTexel);\n"
                + "    }\n"
                + "    // ---- End Greedy Meshing ----\n";

        source = source.replace("void main() {", "void main() {" + injection);

        return source;
    }
}
//?}
