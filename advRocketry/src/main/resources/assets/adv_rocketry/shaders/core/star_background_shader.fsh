#version 150

uniform float playerHeight;
uniform float planetSkyHeight;
uniform float LocalAtmDensity;
uniform float BrightnessModifier;

#moj_import "adv_rocketry:atm_filter.glsl"

in vec4 vColor;
in vec3 localUpUniverseSpace;
in vec3 viewDir;
in vec2 uv;

out vec4 fragColor;

void main() {
    // Soft circular star with glow
    float dist = length(uv);
    if (dist > 1.0) discard;

    float glow = exp(-dist * dist * 4.0);
    float core = pow(max(0.0, 1.0 - dist * 2.0), 3.0);
    float alpha = core + glow * 0.4;

    vec3 U = normalize(localUpUniverseSpace);
    vec3 V = normalize(viewDir);

    float relativeHeight = clamp((planetSkyHeight - playerHeight) / planetSkyHeight, 0, 1);
    float atmThickness = getAtmThickness(relativeHeight, U, V, LocalAtmDensity);
    // Gentle extinction so stars remain visible even through atmosphere
    float atmFilter = exp(-atmThickness * 0.3);

    vec4 color = vColor * alpha * atmFilter * BrightnessModifier;
    fragColor = color;
}
