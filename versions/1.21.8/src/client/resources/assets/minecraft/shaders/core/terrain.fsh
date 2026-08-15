#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 blockPos;
in float greedyFaceId;

out vec4 fragColor;

void main() {
    int faceId = int(round(greedyFaceId));
    bool isGreedy = faceId >= 234 && faceId <= 251;

    vec4 color;
    if (isGreedy) {
        int spritePixels = faceId >= 234 && faceId <= 239 ? 64 : (faceId >= 240 && faceId <= 245 ? 32 : 16);
        int face = faceId >= 234 && faceId <= 239 ? faceId - 234 : (faceId >= 240 && faceId <= 245 ? faceId - 240 : faceId - 246);
        ivec2 atlasSize = textureSize(Sampler0, 0);
        vec2 spriteSize = float(spritePixels) / vec2(atlasSize);
        vec2 spriteOrigin = texCoord0 - spriteSize * 0.5;

        vec2 local;
        if (face == 0) {
            local = vec2(fract(blockPos.x), 1.0 - fract(blockPos.z));
        } else if (face == 1) {
            local = fract(blockPos.xz);
        } else if (face == 2) {
            local = vec2(1.0 - fract(blockPos.x), 1.0 - fract(blockPos.y));
        } else if (face == 3) {
            local = vec2(fract(blockPos.x), 1.0 - fract(blockPos.y));
        } else if (face == 4) {
            local = vec2(fract(blockPos.z), 1.0 - fract(blockPos.y));
        } else {
            local = vec2(1.0 - fract(blockPos.z), 1.0 - fract(blockPos.y));
        }

        vec2 uv = spriteOrigin + local * spriteSize;
        vec2 halfTexel = 0.5 / vec2(atlasSize);
        uv = clamp(uv, spriteOrigin + halfTexel, spriteOrigin + spriteSize - halfTexel);
        // Use smooth block-position gradients instead of implicit derivatives of the fract()-based
        // UV. The latter spikes at block boundaries and can make distant patterned textures flicker.
        vec2 dPdx, dPdy;
        if (face <= 1) {
            dPdx = dFdx(blockPos.xz) * spriteSize;
            dPdy = dFdy(blockPos.xz) * spriteSize;
        } else if (face <= 3) {
            dPdx = dFdx(blockPos.xy) * spriteSize;
            dPdy = dFdy(blockPos.xy) * spriteSize;
        } else {
            dPdx = dFdx(blockPos.zy) * spriteSize;
            dPdy = dFdy(blockPos.zy) * spriteSize;
        }
        color = textureGrad(Sampler0, uv, dPdx, dPdy) * vec4(vertexColor.rgb, 1.0) * ColorModulator;
#ifdef ALPHA_CUTOUT
        // Keep texture alpha for cutout
#else
        color.a = 1.0;
#endif
    } else {
        color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    }

#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
