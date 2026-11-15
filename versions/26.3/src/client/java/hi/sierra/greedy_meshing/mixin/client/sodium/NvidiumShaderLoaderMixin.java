package hi.sierra.greedy_meshing.mixin.client.sodium;

import hi.sierra.greedy_meshing.GreedyConfig;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.cortex.nvidium.sodiumCompat.ShaderLoader", remap = false)
public class NvidiumShaderLoaderMixin {
    @Unique
    private static final String greedyMeshing$SAMPLING = "vec4 color = useRGSS() ? sampleRGSS(uv, du, dv, texelScreenSize) : sampleNearest(uv, du, dv, texelScreenSize);";

    @Inject(method = "parse(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/ShaderDefines$Builder;)Ljava/lang/String;",
            at = @At("RETURN"), cancellable = true)
    private static void greedyMeshing$patchTerrain(Identifier path, ShaderDefines.Builder defines,
                                                   CallbackInfoReturnable<String> callback) {
        if (!GreedyConfig.experimentalNvidium() || !path.getNamespace().equals("nvidium")) return;

        String source = callback.getReturnValue();
        switch (path.getPath()) {
            case "terrain/mesh.glsl" -> callback.setReturnValue(greedyMeshing$patchMesh(source, "id"));
            case "terrain/translucent/mesh.glsl" -> callback.setReturnValue(greedyMeshing$patchMesh(source, "outId"));
            case "terrain/frag.frag" -> {
                callback.setReturnValue(greedyMeshing$patchFragment(source));
                GreedyConfig.markNvidiumShaderPatched();
            }
        }
    }

    @Unique
    private static String greedyMeshing$patchFragment(String source) {
        source = greedyMeshing$replace(source, "vec2 uv;", "vec2 uv;\n    vec3 gmBlockPos;");
        return greedyMeshing$replace(source, greedyMeshing$SAMPLING, """
            #ifdef USE_SODIUM_VERTEX_FORMAT
                uint gmMarker = V0.color >> 24u;
                vec2 gmCenter = decodeVertexRawUV(V0);
            #else
                uint gmMarker = ((V0.y >> 19u) & 31u) + 233u;
                vec2 gmCenter = decodeVertexUV(V0);
            #endif
            #ifdef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
                vec3 gmBlockPos = gl_BaryCoordNV.x * decodeVertexPosition(V0)
                                + gl_BaryCoordNV.y * decodeVertexPosition(Vp)
                                + gl_BaryCoordNV.z * decodeVertexPosition(V2);
            #endif
                vec3 gmDx = dFdx(gmBlockPos);
                vec3 gmDy = dFdy(gmBlockPos);
                vec4 color;
                if (gmMarker >= 234u && gmMarker <= 251u
                        && all(equal(decodeVertexUV(V0), decodeVertexUV(Vp)))
                        && all(equal(decodeVertexUV(V0), decodeVertexUV(V2)))) {
                    uint gmCode = gmMarker - 234u;
                    uint gmFace = gmCode %% 6u;
                    float gmPixels = float(64u >> (gmCode / 6u));
                    vec2 gmTexel = 1.0 / vec2(textureSize(tex_diffuse, 0));
                    vec2 gmSize = gmPixels * gmTexel;
                    vec2 gmLocal;
                    if      (gmFace == 0u) gmLocal = vec2(gmBlockPos.x, -gmBlockPos.z);
                    else if (gmFace == 1u) gmLocal = gmBlockPos.xz;
                    else if (gmFace == 2u) gmLocal = -gmBlockPos.xy;
                    else if (gmFace == 3u) gmLocal = vec2(gmBlockPos.x, -gmBlockPos.y);
                    else if (gmFace == 4u) gmLocal = vec2(gmBlockPos.z, -gmBlockPos.y);
                    else                   gmLocal = -gmBlockPos.zy;
                    vec2 gmInset = (gmSize - gmTexel) * 0.5;
                    vec2 gmUV = gmCenter + clamp((fract(gmLocal) - 0.5) * gmSize, -gmInset, gmInset);
                    vec2 gmGradX = gmFace < 2u ? gmDx.xz : (gmFace < 4u ? gmDx.xy : gmDx.zy);
                    vec2 gmGradY = gmFace < 2u ? gmDy.xz : (gmFace < 4u ? gmDy.xy : gmDy.zy);
                    color = textureGrad(tex_diffuse, gmUV, gmGradX * gmSize, gmGradY * gmSize);
                } else {
                    %s
                }
            """.formatted(greedyMeshing$SAMPLING.replace("vec4 color", "color")));
    }

    @Unique
    private static String greedyMeshing$patchMesh(String source, String vertexId) {
        source = greedyMeshing$replace(source, "vec2 uv;", "vec2 uv;\n    vec3 gmBlockPos;");
        String assignment = "OUT[" + vertexId + "].uv = decodeVertexUV(V);";
        return greedyMeshing$replace(source, assignment,
                assignment + "\n    OUT[" + vertexId + "].gmBlockPos = decodeVertexPosition(V);");
    }

    @Unique
    private static String greedyMeshing$replace(String source, String anchor, String replacement) {
        int index = source.indexOf(anchor);
        if (index < 0 || source.indexOf(anchor, index + anchor.length()) >= 0) {
            throw new IllegalStateException("Unexpected Nvidium shader layout at " + anchor);
        }
        return source.substring(0, index) + replacement + source.substring(index + anchor.length());
    }
}
