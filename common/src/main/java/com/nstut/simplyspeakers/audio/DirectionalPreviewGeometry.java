package com.nstut.simplyspeakers.audio;

import java.util.ArrayList;
import java.util.List;

/** Bounded horizontal samples of the same range and attenuation used by playback. */
public final class DirectionalPreviewGeometry {
    public record Point(double x, double z, float gain, boolean boundary) {}
    private DirectionalPreviewGeometry() {}
    public static List<Point> samples(int range, float dropoff, float directionality, float angle,
                                      float rear, int facingOrdinal) {
        range = Math.max(1, Math.min(512, range));
        double[] facing = DirectionalAudio.facingFromOrdinal(facingOrdinal);
        var cone = new SpatialAudioCalculator.ConeSettings(directionality, angle, rear);
        List<Point> points = new ArrayList<>();
        for (int ring = 1; ring <= 4; ring++) {
            double radius = ring == 4 ? range : Math.min(range * ring / 4.0, ring * 8.0);
            for (int step = 0; step < 72; step++) {
                double radians = step * Math.PI * 2 / 72;
                double x = Math.cos(radians), z = Math.sin(radians);
                float gain = SpatialAudioCalculator.calculateDistanceGain(Math.min(radius, range * .98), range,
                    1, dropoff, facing[0], facing[1], x, z, cone);
                points.add(new Point(x * radius, z * radius, gain, ring == 4));
            }
        }
        if (directionality > 0) {
            double forward = Math.atan2(facing[1], facing[0]);
            for (int side : new int[]{-1, 1}) for (int step = 1; step <= 32; step++) {
                double radians = forward + side * Math.toRadians(angle / 2);
                double radius = range * step / 32.0;
                points.add(new Point(Math.cos(radians)*radius, Math.sin(radians)*radius, 1, true));
            }
        }
        return List.copyOf(points);
    }
}
