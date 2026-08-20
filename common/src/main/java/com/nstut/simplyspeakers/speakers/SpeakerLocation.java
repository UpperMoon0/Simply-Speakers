package com.nstut.simplyspeakers.speakers;

import java.util.Objects;

/**
 * Dimension-aware location for a speaker or proxy speaker.
 */
public record SpeakerLocation(String dimension, long packedPos) {

    public SpeakerLocation(String dimension, int x, int y, int z) {
        this(dimension != null ? dimension : "", pack(x, y, z));
    }

    public static long pack(int x, int y, int z) {
        return (((long) x & 0x3FFFFFF) << 38) | (((long) z & 0x3FFFFFF) << 12) | ((long) y & 0xFFF);
    }

    public int getX() {
        return (int) (packedPos >> 38);
    }

    public int getY() {
        return (int) (packedPos << 52 >> 52);
    }

    public int getZ() {
        return (int) (packedPos << 26 >> 38);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpeakerLocation that = (SpeakerLocation) o;
        return packedPos == that.packedPos && Objects.equals(dimension, that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, packedPos);
    }

    @Override
    public String toString() {
        return dimension + "[" + getX() + ", " + getY() + ", " + getZ() + "]";
    }
}
