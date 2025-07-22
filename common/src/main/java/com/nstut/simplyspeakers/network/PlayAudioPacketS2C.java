package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager; // Changed import


import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
// Removed: import net.minecraftforge.api.distmarker.Dist;
// Removed: import net.minecraftforge.fml.DistExecutor;
// Removed: import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayAudioPacketS2C {
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

    public PlayAudioPacketS2C(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.audioId = buf.readUtf();
        this.audioFilename = buf.readUtf();
        this.playbackPositionSeconds = buf.readFloat();
        this.isLooping = buf.readBoolean();
    }

    public static void encode(PlayAudioPacketS2C pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.audioId);
        buf.writeUtf(pkt.audioFilename);
        buf.writeFloat(pkt.playbackPositionSeconds);
        buf.writeBoolean(pkt.isLooping);
    }

    // Updated handle method for Architectury
    public static void handle(PlayAudioPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        // Ensure this code runs only on the client side
        if (Minecraft.getInstance().level.isClientSide) {
            context.queue(() -> {
                SimplySpeakers.LOGGER.info("CLIENT: Received PlayAudioPacketS2C for pos: {}, audioId: {}, filename: {}, start: {}s, looping: {}", pkt.pos, pkt.audioId, pkt.audioFilename, pkt.playbackPositionSeconds, pkt.isLooping);
                // Play the audio for the specific speaker block, passing the start position and looping state.
                AudioFileMetadata metadata = new AudioFileMetadata(pkt.audioId, pkt.audioFilename);
                ClientAudioPlayer.play(pkt.pos, metadata, pkt.playbackPositionSeconds, pkt.isLooping);
            });
        }
        // For S2C packets, Architectury handles setPacketHandled implicitly when queueing on the client.
    }
}
