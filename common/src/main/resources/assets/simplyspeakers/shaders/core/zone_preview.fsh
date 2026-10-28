#version 330
layout(std140) uniform ZonePreview {
    mat4 ViewProjection;
    vec4 Origin;
    vec4 RotationViewport;
    vec4 Style;
};
in vec3 lineColor;
noperspective in float edge;
out vec4 fragColor;
void main() {
    if(Style.x>0.5 && mod(floor(gl_FragCoord.x+gl_FragCoord.y),12.0)>5.0) discard;
    float alpha=1.0-smoothstep(0.5,1.0,abs(edge));
    fragColor=vec4(lineColor,alpha*mix(0.92,0.14,Style.x));
}
