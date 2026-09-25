#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;
layout(location = 5) in vec3 blockPos;
layout(location = 6) in float greedyFaceId;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

// Vanilla's sampleNearest (texture_sampling.glsl) plus a guard: a projected UV axis can become
// zero (or denormal-small) at particular camera angles. Avoid infinities/NaNs in the
// nearest-filter correction on mobile GLSL.
vec4 greedySampleNearest(sampler2D tex, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, vec2 texelScreenSize) {
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;
    texelOffset = (texelOffset - 0.5f) * pixelSize / max(texelScreenSize, vec2(1.0e-8)) + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);
    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(tex, uv, du, dv);
}

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

void main() {
    int faceId = int(round(greedyFaceId));
    bool isGreedy = faceId >= 234 && faceId <= 251;

    vec4 color;
    if (isGreedy) {
        int spritePixels;
        int face; // 0=DOWN 1=UP 2=NORTH 3=SOUTH 4=WEST 5=EAST
        if (faceId >= 234 && faceId <= 239) {
            spritePixels = 64;
            face = faceId - 234;
        } else if (faceId >= 240 && faceId <= 245) {
            spritePixels = 32;
            face = faceId - 240;
        } else {
            spritePixels = 16;
            face = faceId - 246;
        }

        vec2 pixelSize = 1.0 / vec2(TextureSize);
        vec2 spriteSize = float(spritePixels) * pixelSize;
        // texCoord0 is the sprite CENTER (all 4 vertices share the same UV).
        // Center minus half-size gives the exact sprite origin regardless of
        // atlas padding from any mipmap level.
        vec2 spriteOrigin = texCoord0 - spriteSize * 0.5;

        // Compute per-block local UV from world position.
        // V is inverted for side faces: texture V=0 is top of block, but Y increases upward.
        vec2 local;
        if (face == 0) {        // DOWN:  U=+X, V=-Z
            local = vec2(fract(blockPos.x), 1.0 - fract(blockPos.z));
        } else if (face == 1) { // UP:    U=+X, V=+Z
            local = fract(blockPos.xz);
        } else if (face == 2) { // NORTH: U=-X, V=-Y
            local = vec2(1.0 - fract(blockPos.x), 1.0 - fract(blockPos.y));
        } else if (face == 3) { // SOUTH: U=+X, V=-Y
            local = vec2(fract(blockPos.x), 1.0 - fract(blockPos.y));
        } else if (face == 4) { // WEST:  U=+Z, V=-Y
            local = vec2(fract(blockPos.z), 1.0 - fract(blockPos.y));
        } else {                // EAST:  U=-Z, V=-Y
            local = vec2(1.0 - fract(blockPos.z), 1.0 - fract(blockPos.y));
        }

        vec2 uv = spriteOrigin + local * spriteSize;
        // Reversed axes intentionally produce local == 1.0 at integer block edges.
        // Keep those samples inside this sprite rather than touching its atlas neighbour.
        vec2 halfTexel = pixelSize * 0.5;
        uv = clamp(uv, spriteOrigin + halfTexel, spriteOrigin + spriteSize - halfTexel);

        // Smooth derivatives from blockPos (no fract() discontinuities at block edges)
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
        vec2 texelScreenSize = sqrt(dPdx * dPdx + dPdy * dPdy);

        // The custom nearest/RGSS path is calibrated for vanilla 16x sprites.  Applying
        // that filter to a 32x/64x atlas region overestimates the footprint and can collapse
        // high-resolution textures into a flat colour.  Let the sampler use the resource
        // pack's filtering for high-resolution sprites; the UV reconstruction above remains
        // exact and still repeats one complete sprite per block.
        // Use the smooth block-position gradients for high-resolution sprites too.  The UV
        // itself contains fract() at every block boundary, so implicit derivatives from uv can
        // spike there and make distant mip selection flicker on patterned textures such as ores.
        color = (spritePixels > 16
                ? textureGrad(Sampler0, uv, dPdx, dPdy)
                : greedySampleNearest(Sampler0, uv, pixelSize, dPdx, dPdy, texelScreenSize))
                * vec4(vertexColor.rgb, 1.0);
        // Vertex alpha carries the greedy face/sprite marker. It must never alter
        // texture alpha, especially before the cutout threshold is evaluated.
        // Restore full opacity for solid blocks (face alpha was only a flag).
        // But preserve texture alpha for cutout layers (grass overlay transparency).
#ifndef ALPHA_CUTOUT
        color.a = 1.0;
#endif
    } else {
        // Vanilla sampling for non-greedy blocks
        vec2 uv = texCoord0;
        color = (UseRgss == 1 ? sampleRGSS(Sampler0, uv, 1.0f / TextureSize) : sampleNearest(Sampler0, uv, 1.0f / TextureSize)) * vertexColor;
    }
    #ifndef OIT_ALPHA_ONLY
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);
    #endif
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
