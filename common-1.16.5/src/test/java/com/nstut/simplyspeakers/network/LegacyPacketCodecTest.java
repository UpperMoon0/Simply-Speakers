package com.nstut.simplyspeakers.network;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import com.nstut.simplyspeakers.portable.PortableEmitterSnapshot;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class LegacyPacketCodecTest {
    @Test void maximumUploadChunkRoundTripsWithNativeWireFormat() {
        FriendlyByteBuf source=new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf encoded=new FriendlyByteBuf(Unpooled.buffer());
        try {
            byte[] chunk=new byte[32000];new java.util.Random(9).nextBytes(chunk);
            new UploadAudioDataPacketC2S(UUID.fromString("00000000-0000-0000-0000-000000000009"),17,chunk).encode(source);
            new UploadAudioDataPacketC2S(source).encode(encoded);
            assertEquals(source.writerIndex(),encoded.writerIndex());
            for(int index=0;index<source.writerIndex();index++)assertEquals(source.getByte(index),encoded.getByte(index));
        } finally {source.release();encoded.release();}
    }
    @Test void oversizedAndTruncatedUploadChunksAreRejected() {
        for(int count:new int[]{32000,32001}) {
            FriendlyByteBuf source=new FriendlyByteBuf(Unpooled.buffer());
            try {source.writeUUID(UUID.randomUUID());source.writeVarInt(0);source.writeVarInt(count);
                assertThrows(RuntimeException.class,()->new UploadAudioDataPacketC2S(source));
            } finally {source.release();}
        }
    }
    @Test void portablePoseIdentityAndDimensionSurviveTheWire() {
        FriendlyByteBuf source=new FriendlyByteBuf(Unpooled.buffer());
        try {PortableEmitterSnapshot value=new PortableEmitterSnapshot(UUID.randomUUID(),UUID.randomUUID(),"minecraft:overworld",1.25,72,-3.5,180);
            PortableEmitterCodec.write(source,value);assertEquals(value,PortableEmitterCodec.read(source));assertEquals(0,source.readableBytes());
        } finally {source.release();}
    }
}
