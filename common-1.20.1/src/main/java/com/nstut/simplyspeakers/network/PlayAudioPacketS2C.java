package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.client.DeferredTaskQueue;
import com.nstut.simplyspeakers.testing.LiveJoinTestProtocol;
import dev.architectury.networking.NetworkManager;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

public class PlayAudioPacketS2C {
    private static final DeferredTaskQueue PENDING_PLAYS = new DeferredTaskQueue();
    private final BlockPos pos;
    private final String speakerId;
    private final String audioId;
    private final String audioFilename;
    private final float playbackPositionSeconds;
    private final boolean isLooping;
    private final int maxRange;
    private final float maxVolume;
    private final float audioDropoff;

    public PlayAudioPacketS2C(BlockPos pos, String speakerId, String audioId, String audioFilename, float playbackPositionSeconds, boolean isLooping, int maxRange, float maxVolume, float audioDropoff) {
        this.pos = pos;
        this.speakerId = speakerId != null ? speakerId : "";
        this.audioId = audioId;
        this.audioFilename = audioFilename;
        this.playbackPositionSeconds = playbackPositionSeconds;
        this.isLooping = isLooping;
        this.maxRange = maxRange;
        this.maxVolume = maxVolume;
        this.audioDropoff = audioDropoff;
    }

    public PlayAudioPacketS2C(BlockPos pos, String speakerId, String audioId, String audioFilename, float playbackPositionSeconds, boolean isLooping) {
        this(pos, speakerId, audioId, audioFilename, playbackPositionSeconds, isLooping, 64, 1.0f, 1.0f);
    }

    public PlayAudioPacketS2C(BlockPos pos, String audioId, String audioFilename, float playbackPositionSeconds, boolean isLooping) {
        this(pos, "", audioId, audioFilename, playbackPositionSeconds, isLooping, 64, 1.0f, 1.0f);
    }

    public PlayAudioPacketS2C(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.speakerId = buf.readUtf();
        this.audioId = buf.readUtf();
        this.audioFilename = buf.readUtf();
        this.playbackPositionSeconds = buf.readFloat();
        this.isLooping = buf.readBoolean();
        this.maxRange = buf.readVarInt();
        this.maxVolume = buf.readFloat();
        this.audioDropoff = buf.readFloat();
    }

    public static void encode(PlayAudioPacketS2C pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.speakerId);
        buf.writeUtf(pkt.audioId);
        buf.writeUtf(pkt.audioFilename);
        buf.writeFloat(pkt.playbackPositionSeconds);
        buf.writeBoolean(pkt.isLooping);
        buf.writeVarInt(pkt.maxRange);
        buf.writeFloat(pkt.maxVolume);
        buf.writeFloat(pkt.audioDropoff);
    }

    public static void handle(PlayAudioPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        context.queue(() -> playOrDefer(pkt));
    }

    private static void playOrDefer(PlayAudioPacketS2C packet) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            SimplySpeakers.LOGGER.debug("Deferring speaker playback at {} until the client world is ready", packet.pos);
            if (LiveJoinTestProtocol.PROBE_AUDIO_ID.equals(packet.audioId)) {
                LiveJoinTestProtocol.markDeferred();
            }
            PENDING_PLAYS.defer(() -> play(packet));
            return;
        }
        play(packet);
    }

    private static void play(PlayAudioPacketS2C packet) {
        if (LiveJoinTestProtocol.PROBE_AUDIO_ID.equals(packet.audioId)) {
            LiveJoinTestProtocol.markCompleted();
            return;
        }
        SimplySpeakers.LOGGER.info("CLIENT: Received PlayAudioPacketS2C for pos: {}, speakerId: '{}', audioId: {}, filename: {}, start: {}s, looping: {}, range: {}, volume: {}, dropoff: {}",
                packet.pos, packet.speakerId, packet.audioId, packet.audioFilename, packet.playbackPositionSeconds, packet.isLooping, packet.maxRange, packet.maxVolume, packet.audioDropoff);
        AudioFileMetadata metadata = new AudioFileMetadata(packet.audioId, packet.audioFilename);
        ClientAudioPlayer.play(packet.pos, packet.speakerId, metadata, packet.playbackPositionSeconds, packet.isLooping, packet.maxRange, packet.maxVolume, packet.audioDropoff);
    }

    public static void processPendingPlays() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.level != null) {
            PENDING_PLAYS.drain();
        }
    }

    public static void clearPendingPlays() {
        if (!LiveJoinTestProtocol.hasPendingProbe()) {
            PENDING_PLAYS.clear();
        }
    }

    public static void startLiveJoinProbe() {
        if (LiveJoinTestProtocol.isEnabled()) {
            LiveJoinTestProtocol.reset();
            playOrDefer(new PlayAudioPacketS2C(
                    BlockPos.ZERO, "", LiveJoinTestProtocol.PROBE_AUDIO_ID, "probe.wav", 0.0f, false, 64, 1.0f, 1.0f));
        }
    }

    public BlockPos getPos() {
        return pos;
    }

    public String getSpeakerId() {
        return speakerId;
    }

    public String getAudioId() {
        return audioId;
    }

    public String getAudioFilename() {
        return audioFilename;
    }

    public float getPlaybackPositionSeconds() {
        return playbackPositionSeconds;
    }

    public boolean isLooping() {
        return isLooping;
    }

    public int getMaxRange() {
        return maxRange;
    }

    public float getMaxVolume() {
        return maxVolume;
    }

    public float getAudioDropoff() {
        return audioDropoff;
    }
}
