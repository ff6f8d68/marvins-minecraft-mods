#version 150

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec4 vColor;
out vec3 localUpUniverseSpace;
out vec3 viewDir;
out vec2 uv;

uniform mat4 ViewMat;
uniform mat4 ModelMat;
uniform mat4 WorldMat;
uniform mat4 ProjMat;

uniform vec3 WarpMovement;
uniform ivec2 ScreenSize;

void main() {
    float BoxSize = 50000;
    float scale = 1.0;

    vec3 staticCenter = Position - (Normal * scale);
    vec3 relativeCenter = (ModelMat * vec4(staticCenter, 1.0)).xyz;

    // Spherical wrapping: wrap distance from camera, not per-axis
    float distFromCamera = length(relativeCenter);
    vec3 wrappedCenter;
    if (distFromCamera > BoxSize * 0.5) {
        vec3 dir = relativeCenter / distFromCamera;
        float wrappedDist = mod(distFromCamera, BoxSize * 0.5);
        wrappedCenter = dir * wrappedDist;
    } else {
        wrappedCenter = relativeCenter;
    }

    float distToCamera = length(wrappedCenter);

    // Billboard: orient quad to face camera
    vec3 camRight = normalize(vec3(ViewMat[0][0], ViewMat[1][0], ViewMat[2][0]));
    vec3 camUp = normalize(vec3(ViewMat[0][1], ViewMat[1][1], ViewMat[2][1]));

    // Size scales with distance so stars remain visible, with a minimum pixel size
    float screenSizeY = float(ScreenSize.y);
    float desiredPixelSize = 2.0;
    float distFactor = distToCamera / (ProjMat[1][1] * screenSizeY / desiredPixelSize);
    float starSize = max(scale, distFactor * 0.5);

    vec3 offset = camRight * Normal.x * starSize + camUp * Normal.y * starSize;

    // Warp stretch
    vec3 stretchOffset = vec3(0.0);
    float speed = length(WarpMovement);
    if (speed > 0.001) {
        vec3 dir = WarpMovement / speed;
        float alignment = dot(normalize(wrappedCenter), dir);
        stretchOffset = WarpMovement * alignment * 0.3;
    }

    vec3 finalPos = wrappedCenter + offset + stretchOffset;
    gl_Position = ProjMat * ViewMat * WorldMat * vec4(finalPos, 1.0);

    mat3 rotWorldInv = transpose(mat3(WorldMat));
    localUpUniverseSpace = normalize(rotWorldInv * vec3(0,1,0));
    viewDir = distToCamera > 0.001 ? normalize(finalPos) : vec3(0, 0, 1);

    // Brightness: always visible with a strong minimum
    float proximityFade = 1.0 - smoothstep(BoxSize * 0.3, BoxSize * 0.5, distToCamera);
    proximityFade = max(proximityFade, 0.2);

    vColor = Color;
    vColor *= (1.0 + speed / (1 + speed) * 5);
    vColor *= proximityFade;

    uv = Normal.xy;
}
