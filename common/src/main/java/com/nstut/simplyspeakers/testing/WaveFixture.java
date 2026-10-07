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
}
