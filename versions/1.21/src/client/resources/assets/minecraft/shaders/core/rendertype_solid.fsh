#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
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
        color = texture(Sampler0, uv) * vertexColor * ColorModulator;
        color.a = 1.0;
    } else {
        color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    }

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
