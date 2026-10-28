#version 150
in vec3 Start;
in vec3 End;
in vec3 Color;
in vec2 Corner;
uniform mat4 ViewProjection;
uniform vec3 Origin;
uniform vec2 Rotation;
uniform vec2 Viewport;
out vec3 lineColor;
noperspective out float edge;
vec3 world(vec3 p) {
    return Origin + vec3(p.x * Rotation.x - p.z * Rotation.y, p.y,
                         p.x * Rotation.y + p.z * Rotation.x);
}
void main() {
    vec4 a = ViewProjection * vec4(world(Start), 1.0);
    vec4 b = ViewProjection * vec4(world(End), 1.0);
    // Clip to the homogeneous near plane before screen-space extrusion.
    float da = a.z + a.w, db = b.z + b.w;
    if (da < 0.0 && db < 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    } else {
        if (da < 0.0) a = mix(a, b, da / (da - db));
        if (db < 0.0) b = mix(b, a, db / (db - max(da, 0.0)));
        vec2 delta = (b.xy / max(b.w, 0.00001) - a.xy / max(a.w, 0.00001)) * Viewport;
        vec2 normal = vec2(-delta.y, delta.x) / max(length(delta), 0.00001);
        vec4 p = mix(a, b, Corner.x);
        p.xy += normal * Corner.y * 3.0 / Viewport * p.w;
        gl_Position = p;
    }
    lineColor = Color;
    edge = Corner.y;
}
