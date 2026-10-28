#version 150
in vec3 lineColor;
noperspective in float edge;
uniform float Occluded;
out vec4 fragColor;
void main() {
    // Hidden terrain sections are dim and dashed; visible sections remain solid.
    if (Occluded > 0.5 && mod(floor(gl_FragCoord.x + gl_FragCoord.y), 12.0) > 5.0) discard;
    float alpha = 1.0 - smoothstep(0.5, 1.0, abs(edge));
    fragColor = vec4(lineColor, alpha * mix(0.92, 0.14, Occluded));
}
