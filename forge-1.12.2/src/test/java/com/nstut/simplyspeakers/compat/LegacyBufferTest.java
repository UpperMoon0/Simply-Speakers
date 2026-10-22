package com.nstut.simplyspeakers.compat;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;
public class LegacyBufferTest {
    @Test public void uuidAndUnicodeRoundTrip() {
        LegacyBuffer buffer = new LegacyBuffer(Unpooled.buffer());
        try {
            UUID id = UUID.randomUUID();
            buffer.writeUUID(id).writeUtf("Speaker \u266b", 32);
            assertEquals(id, buffer.readUUID());
            assertEquals("Speaker \u266b", buffer.readUtf(32));
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }
    @Test public void characterLimitDoesNotMistakeUnicodeByteLengthForCharacterCount() {
        char[] chars = new char[12000]; java.util.Arrays.fill(chars, '\u754c');
        String value = new String(chars);
        LegacyBuffer buffer = new LegacyBuffer(Unpooled.buffer());
        try { buffer.writeUtf(value,12000); assertEquals(value,buffer.readUtf(12000)); }
        finally { buffer.release(); }
    }
    @Test public void byteArraysRejectNegativeOversizedAndTruncatedLengths() {
        for (int length : new int[]{-1, 17, 8}) {
            LegacyBuffer buffer = new LegacyBuffer(Unpooled.buffer());
            try {
                buffer.writeVarInt(length); buffer.writeByte(1);
                try { buffer.readByteArray(16); fail("length=" + length); }
                catch (IllegalArgumentException expected) { }
            } finally { buffer.release(); }
        }
    }
    @Test public void byteArrayRoundTrip() {
        LegacyBuffer buffer = new LegacyBuffer(Unpooled.buffer());
        try { buffer.writeByteArray(new byte[]{1,2,3}); assertArrayEquals(new byte[]{1,2,3}, buffer.readByteArray(3)); }
        finally { buffer.release(); }
    }
    @Test public void stringLimitCheckedBeforeWriting() {
        LegacyBuffer buffer = new LegacyBuffer(Unpooled.buffer());
        try {
            try { buffer.writeUtf("1234", 3); fail(); } catch (IllegalArgumentException expected) { }
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test public void wireRejectsTruncatedAndOversizedFrames() {
        for (int size : new int[]{0, 3, 1024*1024+5}) {
            io.netty.buffer.ByteBuf buffer = Unpooled.buffer(size).writeZero(size);
            try { try { new LegacyChannel.Wire().fromBytes(buffer); fail(); } catch (IllegalArgumentException expected) { } }
            finally { buffer.release(); }
        }
    }
    @Test public void wireRoundTripPreservesTypeAndBytes() {
        io.netty.buffer.ByteBuf buffer = Unpooled.buffer();
        try {
            new LegacyChannel.Wire(12, new byte[]{4,5,6}).toBytes(buffer);
            LegacyChannel.Wire decoded = new LegacyChannel.Wire(); decoded.fromBytes(buffer);
            assertEquals(12, decoded.id); assertArrayEquals(new byte[]{4,5,6}, decoded.bytes);
        } finally { buffer.release(); }
    }
}
