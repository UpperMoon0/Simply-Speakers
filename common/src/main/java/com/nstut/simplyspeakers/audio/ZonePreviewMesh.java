package com.nstut.simplyspeakers.audio;

/** Immutable speaker-local ribbons. Built only when settings change; never queries a world. */
public final class ZonePreviewMesh {
    public static final int STEPS = 128;
    public static final int FLOATS_PER_VERTEX = 11;
    public static final int MAX_SEGMENTS = STEPS * 5 + 5;
    private final float[] vertices;
    private int cursor;

    private ZonePreviewMesh(int segments) {
        vertices = new float[segments * 6 * FLOATS_PER_VERTEX];
    }

    public static ZonePreviewMesh create(int range, float dropoff, float strength, float angle,
                                         float rear, int facingOrdinal) {
        range = Math.max(1, Math.min(512, range));
        boolean directional = strength > 0;
        ZonePreviewMesh mesh = new ZonePreviewMesh(STEPS * 5 + (directional ? 5 : 0));
        double[] facing = DirectionalAudio.facingFromOrdinal(facingOrdinal);
        SpatialAudioCalculator.ConeSettings cone = new SpatialAudioCalculator.ConeSettings(strength, angle, rear);
        for (int hoop = 0; hoop < 5; hoop++) {
            double radius = range * (hoop < 2 ? (hoop + 1) / 3.0 : 1);
            for (int step = 0; step < STEPS; step++) {
                double a = step * Math.PI * 2 / STEPS, b = (step + 1) * Math.PI * 2 / STEPS;
                double ax = Math.cos(a) * radius, ay = 0, az = Math.sin(a) * radius;
                double bx = Math.cos(b) * radius, by = 0, bz = Math.sin(b) * radius;
                if (hoop == 3) { ay = az; az = 0; by = bz; bz = 0; }
                if (hoop == 4) { ay = ax; ax = 0; by = bx; bx = 0; }
                int color = 0x5EE3CB;
                if (hoop < 2) {
                    double mid = (a + b) / 2;
                    float gain = SpatialAudioCalculator.calculateDistanceGain(radius, range, 1, dropoff,
                        facing[0], facing[1], Math.cos(mid), Math.sin(mid), cone);
                    color = DirectionalPreviewGeometry.color(new DirectionalPreviewGeometry.Point(0, 0, 0,
                        gain, DirectionalPreviewGeometry.Kind.LEVEL));
                }
                mesh.segment(ax, ay, az, bx, by, bz, color);
            }
        }
        if (directional) {
            double forward = Math.atan2(facing[1], facing[0]);
            double halfAngle = Math.toRadians(Math.max(5, Math.min(355, angle)) / 2);
            for (int side : new int[]{-1, 1}) {
                double a = forward + side * halfAngle;
                mesh.segment(0, 0, 0, Math.cos(a) * range, 0, Math.sin(a) * range, 0xF4F7FF);
            }
            double length = Math.min(6, range * .35), back = length * .25;
            double x = facing[0] * length, z = facing[1] * length;
            mesh.segment(0, 0, 0, x, 0, z, 0xF4F7FF);
            for (int side : new int[]{-1, 1}) {
                mesh.segment(x, 0, z, x - facing[0] * back - facing[1] * back * side, 0,
                    z - facing[1] * back + facing[0] * back * side, 0xF4F7FF);
            }
        }
        if (mesh.cursor != mesh.vertices.length) throw new IllegalStateException("Incomplete preview mesh");
        return mesh;
    }

    private void segment(double ax, double ay, double az, double bx, double by, double bz, int color) {
        vertex(ax, ay, az, bx, by, bz, color, 0, -1);
        vertex(ax, ay, az, bx, by, bz, color, 1, -1);
        vertex(ax, ay, az, bx, by, bz, color, 1, 1);
        vertex(ax, ay, az, bx, by, bz, color, 0, -1);
        vertex(ax, ay, az, bx, by, bz, color, 1, 1);
        vertex(ax, ay, az, bx, by, bz, color, 0, 1);
    }

    private void vertex(double ax, double ay, double az, double bx, double by, double bz,
                        int color, int end, int side) {
        vertices[cursor++] = (float) ax; vertices[cursor++] = (float) ay; vertices[cursor++] = (float) az;
        vertices[cursor++] = (float) bx; vertices[cursor++] = (float) by; vertices[cursor++] = (float) bz;
        vertices[cursor++] = ((color >> 16) & 255) / 255f;
        vertices[cursor++] = ((color >> 8) & 255) / 255f;
        vertices[cursor++] = (color & 255) / 255f;
        vertices[cursor++] = end; vertices[cursor++] = side;
    }

    public int vertexCount() { return vertices.length / FLOATS_PER_VERTEX; }
    /** Upload-time copy. The render loop retains only the GPU buffer. */
    public float[] copyVertices() { return vertices.clone(); }
}
