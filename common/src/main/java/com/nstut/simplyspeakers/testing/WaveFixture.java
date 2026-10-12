package com.nstut.simplyspeakers.testing;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Deterministic PCM fixture shared by decoder checks and opt-in runtime verification. */
public final class WaveFixture {
    private WaveFixture() {}
    public static byte[] tone(int seconds) {
        int rate = 22050, samples = Math.multiplyExact(rate, seconds), size = Math.multiplyExact(samples, 2);
        ByteBuffer buffer = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + size);
        buffer.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1);
        buffer.putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(size);
        for (int i = 0; i < samples; i++) buffer.putShort((short) (1200 * Math.sin(2 * Math.PI * 440 * i / rate)));
        return buffer.array();
    }
    /** Exact-size large fixture without a trigonometric call for every sample. */
    public static byte[] toneBytes(int totalBytes) {
        if (totalBytes < 46 || (totalBytes & 1) != 0) throw new IllegalArgumentException("Invalid WAV size");
        byte[] second = tone(1);
        byte[] result = new byte[totalBytes];
        System.arraycopy(second, 0, result, 0, 44);
        ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).putInt(4, totalBytes - 8).putInt(40, totalBytes - 44);
        for (int offset = 44; offset < totalBytes;) {
            int count = Math.min(second.length - 44, totalBytes - offset);
            System.arraycopy(second, 44, result, offset, count);
            offset += count;
        }
        return result;
    }

}
