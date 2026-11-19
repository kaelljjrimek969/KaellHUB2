// KaellHUB FSR-lite upscaler — works on Mali G57 (OpenGL ES 3.1)
// Simplified AMD FSR 1.0 EASU pass — no proprietary dependency
#version 310 es
precision mediump float;

uniform sampler2D uTexture;
uniform vec2 uInputSize;   // render resolution
uniform vec2 uOutputSize;  // display resolution
in vec2 vTexCoord;
out vec4 fragColor;

// RCAS sharpening strength 0.0 (none) → 1.0 (max)
uniform float uSharpness;

vec4 sampleLinear(vec2 uv) {
    return texture(uTexture, uv);
}

void main() {
    vec2 scale = uInputSize / uOutputSize;
    vec2 srcUv = vTexCoord * scale;

    // 5-tap Lanczos-lite for upscale
    vec4 c  = sampleLinear(srcUv);
    vec4 n  = sampleLinear(srcUv + vec2(0.0,  1.0) / uInputSize);
    vec4 s  = sampleLinear(srcUv + vec2(0.0, -1.0) / uInputSize);
    vec4 e  = sampleLinear(srcUv + vec2( 1.0, 0.0) / uInputSize);
    vec4 w  = sampleLinear(srcUv + vec2(-1.0, 0.0) / uInputSize);

    // RCAS-lite sharpening
    float maxLuma = max(max(n.r, s.r), max(e.r, w.r));
    float minLuma = min(min(n.r, s.r), min(e.r, w.r));
    float amp = clamp((min(maxLuma, 1.0 - minLuma)) / maxLuma, 0.0, 1.0);
    float weight = -(amp * uSharpness * 0.25);
    float rcp = 1.0 / (1.0 + 4.0 * weight);

    fragColor = clamp((c + weight * (n + s + e + w)) * rcp, 0.0, 1.0);
}
