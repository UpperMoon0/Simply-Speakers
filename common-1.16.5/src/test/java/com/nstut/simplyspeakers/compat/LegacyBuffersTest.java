package com.nstut.simplyspeakers.compat;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
class LegacyBuffersTest {
    @Test void usesNativeVarIntCollectionWireFormat() {
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
        try { LegacyBuffers.writeCollection(buffer,Arrays.asList("a","界"),(b,s)->b.writeUtf(s));
            assertEquals(2,buffer.getUnsignedByte(0));
            assertEquals(Arrays.asList("a","界"),LegacyBuffers.readList(buffer,b->b.readUtf(64)));
            assertEquals(0,buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void unicodeUsesCharacterBoundsWithoutChangingWireShape() {
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {String text=com.nstut.simplyspeakers.compat.Java8.repeat("界",256);
            LegacyBuffers.writeUtf(buffer,text,256);assertEquals(text,buffer.readUtf(256));
            assertThrows(io.netty.handler.codec.EncoderException.class,()->LegacyBuffers.writeUtf(buffer,text+"x",256));
        } finally {buffer.release();}
    }
    @Test void rejectsNegativeOversizedAndTruncatedCountsBeforeAllocation() {
        for(int size:new int[]{-1,LegacyBuffers.MAX_ENTRIES+1,100}) {
            FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {buffer.writeVarInt(size);assertThrows(IllegalArgumentException.class,()->LegacyBuffers.readList(buffer,b->b.readUtf(64)));}
            finally {buffer.release();}
        }
    }
}
