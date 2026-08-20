package com.nstut.simplyspeakers.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlayAudioPacketSerializationTest {

    @Test
    public void testPlayAudioPacketWithAllEmitterProperties() {
        BlockPos pos = new BlockPos(10, 64, -20);
        PlayAudioPacketS2C packet = new PlayAudioPacketS2C(pos, "living_room", "audio_123", "track.mp3", 12.5f, true, 32, 0.75f, 0.5f);

        assertEquals(pos, packet.getPos());
        assertEquals("living_room", packet.getSpeakerId());
        assertEquals("audio_123", packet.getAudioId());
        assertEquals("track.mp3", packet.getAudioFilename());
        assertEquals(12.5f, packet.getPlaybackPositionSeconds());
        assertTrue(packet.isLooping());
        assertEquals(32, packet.getMaxRange());
        assertEquals(0.75f, packet.getMaxVolume());
        assertEquals(0.5f, packet.getAudioDropoff());
    }

    @Test
    public void testPlayAudioPacketNullSpeakerIdDefaultsToEmpty() {
        BlockPos pos = new BlockPos(0, 0, 0);
        PlayAudioPacketS2C packet = new PlayAudioPacketS2C(pos, null, "audio_456", "track.wav", 0.0f, false);

        assertEquals("", packet.getSpeakerId());
        assertEquals(64, packet.getMaxRange());
        assertEquals(1.0f, packet.getMaxVolume());
        assertEquals(1.0f, packet.getAudioDropoff());
    }
}
