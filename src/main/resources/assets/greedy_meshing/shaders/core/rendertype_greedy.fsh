#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec2 quadSize;
in vec2 spriteOffset;
in vec2 spriteSize;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float eps = 1.0 / 1024.0;
    vec2 tiled = clamp(fract(texCoord0 * quadSize), eps, 1.0 - eps);
    vec2 finalUv = tiled * spriteSize + spriteOffset;
    fragColor = texture(Sampler0, finalUv) * vertexColor;
}
