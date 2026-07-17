package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.client.DeferredTaskQueue;
import dev.architectury.networking.NetworkManager; // Changed import


import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
// Removed: import net.minecraftforge.api.distmarker.Dist;
// Removed: import net.minecraftforge.fml.DistExecutor;
// Removed: import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayAudioPacketS2C {
    private static final DeferredTaskQueue PENDING_PLAYS = new DeferredTaskQueue();
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
        context.queue(() -> playOrDefer(pkt));
        // For S2C packets, Architectury handles setPacketHandled implicitly when queueing on the client.
    }

    private static void playOrDefer(PlayAudioPacketS2C packet) {
        if (Minecraft.getInstance().level == null) {
            SimplySpeakers.LOGGER.debug("Deferring speaker playback at {} until the client world is ready", packet.pos);
            PENDING_PLAYS.defer(() -> play(packet));
            return;
        }
        play(packet);
    }

    private static void play(PlayAudioPacketS2C packet) {
        SimplySpeakers.LOGGER.info("CLIENT: Received PlayAudioPacketS2C for pos: {}, audioId: {}, filename: {}, start: {}s, looping: {}", packet.pos, packet.audioId, packet.audioFilename, packet.playbackPositionSeconds, packet.isLooping);
        AudioFileMetadata metadata = new AudioFileMetadata(packet.audioId, packet.audioFilename);
        ClientAudioPlayer.play(packet.pos, metadata, packet.playbackPositionSeconds, packet.isLooping);
    }

    public static void processPendingPlays() {
        if (Minecraft.getInstance().level != null) {
            PENDING_PLAYS.drain();
        }
    }

    public static void clearPendingPlays() {
        PENDING_PLAYS.clear();
    }
}
