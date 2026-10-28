#version 330
layout(std140) uniform ZonePreview {
    mat4 ViewProjection;
    vec4 Origin;
    vec4 RotationViewport;
    vec4 Style;
};
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;
out vec3 lineColor;
noperspective out float edge;
vec3 world(vec3 p) {
    return Origin.xyz + vec3(p.x * RotationViewport.x - p.z * RotationViewport.y, p.y,
                             p.x * RotationViewport.y + p.z * RotationViewport.x);
}
void main() {
    vec4 p=ViewProjection*vec4(world(Position),1.0);
    vec4 q=ViewProjection*vec4(world(Position+Normal),1.0);
    vec2 delta=(q.xy* p.w - p.xy*q.w)*RotationViewport.zw;
    vec2 perpendicular=vec2(-delta.y,delta.x)/max(length(delta),0.00001);
    p.xy+=perpendicular*UV0.x*3.0/RotationViewport.zw*p.w;
    gl_Position=p;
    lineColor=Color.rgb;
    edge=UV0.x;
}
