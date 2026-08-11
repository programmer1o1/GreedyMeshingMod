#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 blockPos;
in float greedyFaceId;

out vec4 fragColor;

vec4 sampleNearest(sampler2D tex, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, vec2 texelScreenSize) {
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;
    texelOffset = (texelOffset - 0.5f) * pixelSize / texelScreenSize + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);
    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(tex, uv, du, dv);
}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
}

vec4 sampleRGSS(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);

    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    float maxTexelSize = max(texelScreenSize.x, texelScreenSize.y);
    float minPixelSize = min(pixelSize.x, pixelSize.y);
    float transitionStart = minPixelSize * 1.0;
    float transitionEnd = minPixelSize * 2.0;
    float blendFactor = smoothstep(transitionStart, transitionEnd, maxTexelSize);

    float duLength = length(du);
    float dvLength = length(dv);
    float minDerivative = min(duLength, dvLength);
    float maxDerivative = max(duLength, dvLength);
    float effectiveDerivative = sqrt(minDerivative * maxDerivative);
    float mipLevelExact = max(0.0, log2(effectiveDerivative / minPixelSize));
    float mipLevelLow = floor(mipLevelExact);
    float mipLevelHigh = mipLevelLow + 1.0;
    float mipBlend = fract(mipLevelExact);

    const vec2 offsets[4] = vec2[](
        vec2(0.125, 0.375),
        vec2(-0.125, -0.375),
        vec2(0.375, -0.125),
        vec2(-0.375, 0.125)
    );

    vec4 rgssColorLow = vec4(0.0);
    vec4 rgssColorHigh = vec4(0.0);
    for (int i = 0; i < 4; ++i) {
        vec2 sampleUV = uv + offsets[i] * pixelSize;
        rgssColorLow += textureLod(source, sampleUV, mipLevelLow);
        rgssColorHigh += textureLod(source, sampleUV, mipLevelHigh);
    }
    rgssColorLow *= 0.25;
    rgssColorHigh *= 0.25;

    vec4 rgssColor = mix(rgssColorLow, rgssColorHigh, mipBlend);
    vec4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
    return mix(nearestColor, rgssColor, blendFactor);
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
                : sampleNearest(Sampler0, uv, pixelSize, dPdx, dPdy, texelScreenSize)) * vertexColor;
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

    color = mix(FogColor * vec4(1, 1, 1, color.a), color, ChunkVisibility);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
