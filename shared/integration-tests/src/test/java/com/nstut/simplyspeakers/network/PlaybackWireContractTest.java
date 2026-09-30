package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.audio.DirectionalAudio;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** One wire contract compiled against each version's actual packet implementations. */
class PlaybackWireContractTest {
    @SuppressWarnings("unchecked")
    private <T> T roundTrip(T packet) throws Exception {
        FriendlyByteBuf buffer;
        Class<?> bufferType;
        try {
            bufferType = Class.forName("net.minecraft.network.RegistryFriendlyByteBuf");
            Class<?> access = Class.forName("net.minecraft.core.RegistryAccess");
            buffer = (FriendlyByteBuf) bufferType.getConstructor(io.netty.buffer.ByteBuf.class, access)
                    .newInstance(Unpooled.buffer(), access.getField("EMPTY").get(null));
        } catch (ClassNotFoundException legacy) {
            bufferType = FriendlyByteBuf.class; buffer = new FriendlyByteBuf(Unpooled.buffer());
        }
        try {
            Class<?> type = packet.getClass();
            T decoded;
            if (bufferType == FriendlyByteBuf.class) {
                if (packet instanceof PlayAudioPacketS2C) {
                    type.getMethod("encode", type, bufferType).invoke(null, packet, buffer);
                } else { type.getMethod("encode", bufferType).invoke(packet, buffer); }
                decoded = (T) type.getConstructor(bufferType).newInstance(buffer);
            } else {
                type.getMethod("encode", bufferType, type).invoke(null, buffer, packet);
                decoded = (T) type.getMethod("decode", bufferType).invoke(null, buffer);
            }
            assertEquals(0, buffer.readableBytes(), "decoder must consume the full packet");
            return decoded;
        } finally { buffer.release(); }
    }

    @Test void playPreservesDimensionIdentityOccurrenceFacingAndTransportOffset() throws Exception {
        var pos = new BlockPos(-123, 64, 567);
        var packet = new PlayAudioPacketS2C(pos, "station", "https://example.invalid/a.wav", "a.wav",
                12.25f, false, 32, 0.75f, 0.3f)
                .withRemoteIdentity("minecraft:the_nether/net_station", 17)
                .withExtras(new DirectionalAudio.Extras(0.8f, 120, 0.5f, (byte) 5));
        var decoded = roundTrip(packet);
        assertEquals(pos, decoded.getPos()); assertEquals(packet.getAudioId(), decoded.getAudioId());
        assertEquals(12.25f, decoded.getPlaybackPositionSeconds());
        assertEquals(32, decoded.getMaxRange()); assertEquals(0.75f, decoded.getMaxVolume());
        assertEquals(0.3f, decoded.getAudioDropoff()); assertEquals(packet.getExtras(), decoded.getExtras());
        assertEquals("minecraft:the_nether/net_station", decoded.getFullStateKey());
        assertEquals(17, decoded.getPlaybackGeneration());
    }

    @Test void emptyPlaylistIsARealWireSnapshotWithPausedAndPlayingIndex() throws Exception {
        var decoded = roundTrip(new PlaylistSyncPacketS2C(new BlockPos(1, 2, 3),
                "minecraft:overworld/net_station", List.of(), List.of(), -1, true, 2, -1, true));
        assertEquals("minecraft:overworld/net_station", decoded.getFullStateKey());
        assertTrue(decoded.getAudioIds().isEmpty());
    }
}
