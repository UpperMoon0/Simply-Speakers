package com.nstut.simplyspeakers.audio;

import java.util.ArrayList;
import java.util.List;

/** Bounded samples: cyan range sphere, gain-coloured interior and white direction guides. */
public final class DirectionalPreviewGeometry {
    public enum Kind { RANGE, LEVEL, DIRECTION }
    public record Point(double x, double y, double z, float gain, Kind kind) {
        public boolean boundary() { return kind == Kind.RANGE; }
    }
    private DirectionalPreviewGeometry() {}
    public static List<Point> samples(int range, float dropoff, float directionality, float angle,
                                      float rear, int facingOrdinal) {
        range = Math.max(1, Math.min(512, range));
        double[] facing = DirectionalAudio.facingFromOrdinal(facingOrdinal);
        var cone = new SpatialAudioCalculator.ConeSettings(directionality, angle, rear);
        List<Point> points = new ArrayList<>();
        for (int ring = 1; ring <= 3; ring++) {
            double radius = range * ring / 3.0;
            for (int step = 0; step < 96; step++) {
                double radians = step * Math.PI * 2 / 96;
                double x = Math.cos(radians), z = Math.sin(radians);
                float gain = SpatialAudioCalculator.calculateDistanceGain(radius, range,
                    1, dropoff, facing[0], facing[1], x, z, cone);
                points.add(new Point(x * radius, 0, z * radius, gain, ring == 3 ? Kind.RANGE : Kind.LEVEL));
            }
        }
        // Two vertical hoops communicate that range includes height, not just the ground plane.
        for (int axis = 0; axis < 2; axis++) for (int step = 0; step < 64; step++) {
            double radians = step * Math.PI * 2 / 64;
            double horizontal = Math.cos(radians) * range;
            points.add(new Point(axis == 0 ? horizontal : 0, Math.sin(radians) * range,
                axis == 1 ? horizontal : 0, 0, Kind.RANGE));
        }
        if (directionality > 0) {
            double forward = Math.atan2(facing[1], facing[0]);
            double halfAngle = Math.toRadians(Math.max(5, Math.min(355, angle)) / 2);
            for (int side : new int[]{-1, 1}) for (int step = 1; step <= 24; step++) {
                double radians = forward + side * halfAngle;
                double radius = range * step / 24.0;
                points.add(new Point(Math.cos(radians)*radius, 0, Math.sin(radians)*radius, 1, Kind.DIRECTION));
            }
            double length = Math.min(6, range * .35);
            for (int step = 1; step <= 16; step++) {
                double radius = length * step / 16;
                points.add(new Point(facing[0]*radius, 0, facing[1]*radius, 1, Kind.DIRECTION));
            }
            for (int side : new int[]{-1, 1}) for (int step = 0; step <= 8; step++) {
                double back = length * .25 * step / 8;
                points.add(new Point(facing[0]*(length-back)-facing[1]*back*side, 0,
                    facing[1]*(length-back)+facing[0]*back*side, 1, Kind.DIRECTION));
            }
        }
        return List.copyOf(points);
    }
    public static int color(Point point) {
        if (point.kind() == Kind.RANGE) return 0x5EE3CB;
        if (point.kind() == Kind.DIRECTION) return 0xF4F7FF;
        float gain = point.gain();
        return (int)(235-145*gain) << 16 | (int)(166+61*gain) << 8 | (int)(60+100*gain);
    }
}
