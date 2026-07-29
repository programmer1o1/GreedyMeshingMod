package hi.sierra.greedy_meshing.mixin.client.vulkan;

//? if VULKANMOD {
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Patches VulkanMod's terrain shaders so greedy-merged quads tile their texture per block.
 *
 * <p>VulkanMod compiles GLSL→SPIR-V through {@code SPIRVUtils.compileShader(filename, source, kind)}.
 * We rewrite the {@code source} for {@code terrain.vsh} / {@code terrain.fsh} /
 * {@code terrain_earlyZ.fsh} before compilation — the SPIR-V analogue of the Sodium path's
 * {@code SodiumShaderLoaderMixin}.
 *
 * <p>{@link VulkanBuildTaskMixin} emits one quad per greedy run with UV = sprite centre and the face
 * id packed into colour alpha (246 + Direction.ordinal). The vertex shader forwards the reconstructed
 * block position and the face id; the fragment shader, for face ids 246..251, rebuilds the per-block
 * UV from {@code fract(position)} mapped into the sprite (assumed 16×16 texels, as Sodium does).
 * Non-greedy geometry (alpha != 246..251) is untouched.
 *
 * <p><b>Terrain shaders are identified by unique source markers</b>, NOT by filename and NOT by generic
 * content like {@code Sampler0}/{@code texCoord0} (which appear in entity/cutout/solid shaders too, and
 * even in a commented-out block of {@code rendertype_clouds.fsh}). Patching the wrong shader is fatal:
 * injecting our varyings into e.g. {@code entity_solid} (which has 5 existing varyings) produces a
 * fragment input at locations 5/6 that the unpatched vertex stage never writes →
 * {@code VK_ERROR_INITIALIZATION_FAILED} at pipeline creation. The markers used here were verified
 * unique to the terrain shaders across every supported VulkanMod build (0.5.3 → 0.6.8):
 * <ul>
 *   <li>vertex: {@code getVertexPosition} — the compressed-position helper, only in {@code terrain.vsh}
 *       (note 0.5.3's terrain.vsh has no {@code PackedColor}, so that can't be the marker).</li>
 *   <li>fragment: {@code AlphaCutout} ({@code terrain.fsh}) or {@code early_fragment_tests}
 *       ({@code terrain_earlyZ.fsh}). The vertex check MUST come first: 0.6.x {@code terrain.vsh} also
 *       declares an {@code AlphaCutout} uniform.</li>
 * </ul>
 * The vsh {@code out} layout mirrors the fsh {@code in} layout for terrain, so
 * {@link #greedyMeshing$nextFreeLocation} lands both stages on the same base location.
 */
@Mixin(SPIRVUtils.class)
public abstract class VulkanShaderMixin {

    @ModifyVariable(method = "compileShader", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false)
    private static String greedyMeshing$patchTerrainShader(String source) {
        if (source == null || !source.contains("void main()")) {
            return source;
        }
        // Vertex first: terrain.vsh uniquely defines getVertexPosition(). (Must precede the fragment
        // check — 0.6.x terrain.vsh also declares an AlphaCutout uniform.)
        if (source.contains("getVertexPosition")) {
            return greedyMeshing$injectVertex(source);
        }
        // Fragment: terrain.fsh has AlphaCutout, terrain_earlyZ.fsh has early_fragment_tests. Both are
        // unique to the terrain fragment shaders — no entity/cutout/solid/clouds shader carries either.
        if (source.contains("early_fragment_tests") || source.contains("AlphaCutout")) {
            return greedyMeshing$injectFragment(source);
        }
        // Line shader (rendertype_lines.vsh, only bundled by VulkanMod 0.5.x): fix a degenerate
        // normalize that explodes our debug wireframe into giant triangles. Unrelated to greedy
        // meshing geometry, but the wireframe is unusable without it on 0.5.x + MoltenVK.
        if (source.contains("lineScreenDirection")) {
            return greedyMeshing$fixLineShader(source);
        }
        return source;
    }

    /**
     * VulkanMod 0.5.x's {@code rendertype_lines.vsh} expands each line into a screen-facing quad using
     * {@code normalize((ndc2.xy - ndc1.xy) * ScreenSize)}. When a line points toward/away from the
     * camera the two endpoints project to the same screen point, so the argument is ~0 — on desktop GL
     * {@code normalize(0)} tends to yield 0 (line collapses harmlessly), but on MoltenVK/Metal it yields
     * a huge value, so {@code lineOffset} blows up and the line becomes a giant triangle streaking across
     * the view. Guarding the length restores a sane (effectively zero-width) line for the degenerate case.
     * 0.6.x doesn't bundle this shader, so the {@code lineScreenDirection} marker never matches there.
     */
    @Unique
    private static String greedyMeshing$fixLineShader(String source) {
        return source.replace(
                "vec2 lineScreenDirection = normalize((ndc2.xy - ndc1.xy) * ScreenSize);",
                "vec2 _gm_lineDelta = (ndc2.xy - ndc1.xy) * ScreenSize;\n"
                        + "    float _gm_lineLen = length(_gm_lineDelta);\n"
                        + "    vec2 lineScreenDirection = _gm_lineLen > 1.0e-4 ? _gm_lineDelta / _gm_lineLen : vec2(1.0, 0.0);"
        );
    }

    /**
     * Highest {@code layout (location = N) <qualifier>} already used, + 1. VulkanMod's terrain shader
     * layout differs by version — 0.5.x uses out-locations 0..2, but 0.6.x added
     * {@code cylindricalVertexDistance}/{@code fadeFactor} and uses 0..4. Hardcoding 3/4 collides on
     * 0.6.x and breaks shader compilation, so derive the free slot instead.
     *
     * <p>Counts only the matching qualifier ({@code out} for the vsh, {@code in} for the fsh) — the
     * space our forwarded varyings actually occupy. Counting every {@code location =} would be wrong:
     * the vsh's {@code #else} (non-compressed) branch declares an {@code in} at location 4 that is
     * {@code #ifdef}'d out, which would desync the vsh {@code out} base from the fsh {@code in} base.
     * Because the vsh {@code out} layout mirrors the fsh {@code in} layout, both stages arrive at the
     * same base and the varyings stay matched.
     */
    @Unique
    private static int greedyMeshing$nextFreeLocation(String source, String qualifier) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("location\\s*=\\s*(\\d+)\\s*\\)\\s*" + qualifier + "\\b").matcher(source);
        int max = -1;
        while (m.find()) {
            max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return max + 1;
    }

    @Unique
    private static String greedyMeshing$injectVertex(String source) {
        if (source.contains("v_GreedyFaceId") || !source.contains("void main()")) {
            return source;
        }
        // Forward the block-aligned position and the face id (from the Color attribute/local) on the
        // first two free varying locations (see greedyMeshing$nextFreeLocation).
        int loc = greedyMeshing$nextFreeLocation(source, "out");
        source = source.replace(
                "void main() {",
                "layout (location = " + loc + ") out vec3 v_BlockPos;\n"
                        + "layout (location = " + (loc + 1) + ") out float v_GreedyFaceId;\n\n"
                        + "void main() {"
        );
        int lastBrace = source.lastIndexOf('}');
        if (lastBrace < 0) {
            return source;
        }
        // Use the section-local position (Position * scale), NOT the reconstructed `pos`:
        // `pos` adds ModelOffset, which is camera-relative, so fract(pos) slides as the camera moves.
        // The raw compressed position is camera-independent and block-aligned → stable UVs.
        //
        // The scale factor 1/2048 is hardcoded as a literal — NOT as `POSITION_INV` by name. The
        // constant name exists in 0.5.x shaders but the 0.5.6 shaderc toolchain (1.21.4/1.21.5)
        // fails with "POSITION_INV : undeclared identifier" even though it is defined at global scope,
        // likely a scope-resolution quirk triggered by the #include directives. Using the literal is
        // safe and version-independent (the value is the same across all known VulkanMod releases).
        //
        // `Color` is always available here: in <=0.5.3 it is a vec4 input attribute; in 0.5.6+ it
        // is declared as `const vec4 Color = unpackUnorm4x8(PackedColor)` inside main before this
        // injection point. Both cases keep it in scope for `Color.a`.
        source = source.substring(0, lastBrace)
                + "\n    v_BlockPos = vec3(Position.xyz) * vec3(1.0 / 2048.0);\n"
                + "    v_GreedyFaceId = Color.a * 255.0;\n"
                + source.substring(lastBrace);
        return source;
    }

    @Unique
    private static String greedyMeshing$injectFragment(String source) {
        if (source.contains("v_GreedyFaceId") || !source.contains("void main()")) {
            return source;
        }
        int loc = greedyMeshing$nextFreeLocation(source, "in");
        source = source.replace(
                "void main() {",
                "layout (location = " + loc + ") in vec3 v_BlockPos;\n"
                        + "layout (location = " + (loc + 1) + ") in float v_GreedyFaceId;\n\n"
                        + "void main() {"
        );

        int mainIdx = source.indexOf("void main()");
        int bodyStart = source.indexOf('{', mainIdx);
        if (bodyStart < 0) {
            return source;
        }
        String header = source.substring(0, bodyStart + 1);
        String body = source.substring(bodyStart + 1);
        // Route texture sampling through our re-tiled coord. The declaration (location 1) is untouched
        // because we only rewrite the body.
        body = body.replaceAll("(?<![a-zA-Z0-9_])texCoord0(?![a-zA-Z0-9_])", "_gm_TexCoord");

        String injection =
                "\n    // ---- Greedy Meshing UV tiling ----\n"
                        + "    int _gm_faceId = int(round(v_GreedyFaceId));\n"
                        + "    vec2 _gm_TexCoord = texCoord0;\n"
                        + "    if (_gm_faceId >= 246 && _gm_faceId <= 251) {\n"
                        + "        int _gm_face = _gm_faceId - 246;\n"
                        + "        vec2 _gm_spriteSize = 16.0 / vec2(textureSize(Sampler0, 0));\n"
                        + "        vec2 _gm_spriteOrigin = texCoord0 - _gm_spriteSize * 0.5;\n"
                        + "        vec2 _gm_local;\n"
                        + "        if      (_gm_face == 0) _gm_local = vec2(fract(v_BlockPos.x), 1.0 - fract(v_BlockPos.z));\n"
                        + "        else if (_gm_face == 1) _gm_local = fract(v_BlockPos.xz);\n"
                        + "        else if (_gm_face == 2) _gm_local = vec2(1.0 - fract(v_BlockPos.x), 1.0 - fract(v_BlockPos.y));\n"
                        + "        else if (_gm_face == 3) _gm_local = vec2(fract(v_BlockPos.x), 1.0 - fract(v_BlockPos.y));\n"
                        + "        else if (_gm_face == 4) _gm_local = vec2(fract(v_BlockPos.z), 1.0 - fract(v_BlockPos.y));\n"
                        + "        else                    _gm_local = vec2(1.0 - fract(v_BlockPos.z), 1.0 - fract(v_BlockPos.y));\n"
                        + "        vec2 _gm_uv = _gm_spriteOrigin + _gm_local * _gm_spriteSize;\n"
                        + "        vec2 _gm_halfTexel = 0.5 / vec2(textureSize(Sampler0, 0));\n"
                        + "        _gm_TexCoord = clamp(_gm_uv, _gm_spriteOrigin + _gm_halfTexel, _gm_spriteOrigin + _gm_spriteSize - _gm_halfTexel);\n"
                        + "    }\n"
                        + "    // ---- End Greedy Meshing ----\n";

        return header + injection + body;
    }
}
//?}
