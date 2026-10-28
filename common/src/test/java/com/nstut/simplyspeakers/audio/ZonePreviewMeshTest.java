package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZonePreviewMeshTest {
    @Test void costIsFixedForSmallAndLargeRanges() {
        for (int range : new int[]{1, 16, 128, 512, Integer.MAX_VALUE}) {
            ZonePreviewMesh mesh = ZonePreviewMesh.create(range, 1, 1, 90, .9f, 2);
            assertEquals(ZonePreviewMesh.MAX_SEGMENTS * 6, mesh.vertexCount());
            assertEquals(ZonePreviewMesh.MAX_SEGMENTS * 6 * ZonePreviewMesh.FLOATS_PER_VERTEX,
                mesh.copyVertices().length);
            for (float value : mesh.copyVertices()) assertTrue(Float.isFinite(value));
        }
    }

    @Test void rangeHoopsAreContinuousClosedSpheresAndDoNotEncodeWorldCoordinates() {
        int stride = ZonePreviewMesh.FLOATS_PER_VERTEX * 6;
        float[] data = ZonePreviewMesh.create(512, 1, 0, 90, 1, 2).copyVertices();
        for (int hoop = 2; hoop < 5; hoop++) {
            for (int step = 0; step < ZonePreviewMesh.STEPS; step++) {
                int current = (hoop * ZonePreviewMesh.STEPS + step) * stride;
                int next = (hoop * ZonePreviewMesh.STEPS + (step + 1) % ZonePreviewMesh.STEPS) * stride;
                double radiusSquared = 0;
                for (int axis = 0; axis < 3; axis++) {
                    radiusSquared += data[current + axis] * data[current + axis];
                    assertEquals(data[current + 3 + axis], data[next + axis], .001);
                }
                assertEquals(512, Math.sqrt(radiusSquared), .001);
                assertEquals(0x5E / 255f, data[current + 6], .0001);
                assertEquals(0xE3 / 255f, data[current + 7], .0001);
                assertEquals(0xCB / 255f, data[current + 8], .0001);
            }
        }
    }

    @Test void onlyDirectionalSpeakersHaveConeEdgesAndArrow() {
        for (int facing = 2; facing <= 5; facing++) {
            ZonePreviewMesh directional = ZonePreviewMesh.create(16, 1, 1, 90, .9f, facing);
            ZonePreviewMesh omni = ZonePreviewMesh.create(16, 1, 0, 90, .9f, facing);
            assertEquals(5 * 6, directional.vertexCount() - omni.vertexCount());
            float[] data = directional.copyVertices();
            int arrow = (5 * ZonePreviewMesh.STEPS + 2) * 6 * ZonePreviewMesh.FLOATS_PER_VERTEX;
            double[] direction = DirectionalAudio.facingFromOrdinal(facing);
            assertEquals(direction[0] * 5.6, data[arrow + 3], .001);
            assertEquals(direction[1] * 5.6, data[arrow + 5], .001);
        }
    }

    @Test void callersCannotMutateRetainedGeometry() {
        ZonePreviewMesh mesh = ZonePreviewMesh.create(16, 1, 1, 90, .9f, 2);
        float original = mesh.copyVertices()[0];
        float[] copy = mesh.copyVertices(); copy[0] = 123456;
        assertEquals(original, mesh.copyVertices()[0]);
    }
}
