package com.nstut.simplyspeakers.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlayAudioPacketSerializationTest {

    @Test
    public void testPlayAudioPacketWithSpeakerId() {
        BlockPos pos = new BlockPos(10, 64, -20);
        PlayAudioPacketS2C packet = new PlayAudioPacketS2C(pos, "living_room", "audio_123", "track.mp3", 12.5f, true);

        assertEquals(pos, packet.getPos());
        assertEquals("living_room", packet.getSpeakerId());
        assertEquals("audio_123", packet.getAudioId());
        assertEquals("track.mp3", packet.getAudioFilename());
        assertEquals(12.5f, packet.getPlaybackPositionSeconds());
        assertTrue(packet.isLooping());
    }

    @Test
    public void testPlayAudioPacketNullSpeakerIdDefaultsToEmpty() {
        BlockPos pos = new BlockPos(0, 0, 0);
        PlayAudioPacketS2C packet = new PlayAudioPacketS2C(pos, null, "audio_456", "track.wav", 0.0f, false);

        assertEquals("", packet.getSpeakerId());
    }
}
