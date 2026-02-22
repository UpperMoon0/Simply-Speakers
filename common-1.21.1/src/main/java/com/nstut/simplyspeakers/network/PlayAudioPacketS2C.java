package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class PlayAudioPacketS2C implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<PlayAudioPacketS2C> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "play_audio"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayAudioPacketS2C> STREAM_CODEC = 
        StreamCodec.of(PlayAudioPacketS2C::encode, PlayAudioPacketS2C::decode);

    private final BlockPos pos;
    private final String audioId;
    private final String audioFilename;
    private final float playbackPositionSeconds;
    private final boolean isLooping;

    public PlayAudioPacketS2C(BlockPos pos, String audioId, String audioFilename, float playbackPositionSeconds, boolean isLooping) {
        this.pos = pos;
        this.audioId = audioId;
        this.audioFilename = audioFilename;
        this.playbackPositionSeconds = playbackPositionSeconds;
        this.isLooping = isLooping;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, PlayAudioPacketS2C packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUtf(packet.audioId);
        buffer.writeUtf(packet.audioFilename);
        buffer.writeFloat(packet.playbackPositionSeconds);
        buffer.writeBoolean(packet.isLooping);
    }

    public static PlayAudioPacketS2C decode(RegistryFriendlyByteBuf buffer) {
        return new PlayAudioPacketS2C(
            buffer.readBlockPos(),
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readFloat(),
            buffer.readBoolean()
        );
    }

    public static void handle(PlayAudioPacketS2C packet, NetworkManager.PacketContext context) {
        // Ensure this code runs only on the client side
        if (Minecraft.getInstance().level.isClientSide) {
            context.queue(() -> {
                SimplySpeakers.LOGGER.info("CLIENT: Received PlayAudioPacketS2C for pos: {}, audioId: {}, filename: {}, start: {}s, looping: {}", packet.pos, packet.audioId, packet.audioFilename, packet.playbackPositionSeconds, packet.isLooping);
                // Play the audio for the specific speaker block, passing the start position and looping state.
                AudioFileMetadata metadata = new AudioFileMetadata(packet.audioId, packet.audioFilename);
                ClientAudioPlayer.play(packet.pos, metadata, packet.playbackPositionSeconds, packet.isLooping);
            });
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
