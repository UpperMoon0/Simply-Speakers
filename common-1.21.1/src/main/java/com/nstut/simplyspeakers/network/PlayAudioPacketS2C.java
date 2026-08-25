package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.client.DeferredTaskQueue;
import com.nstut.simplyspeakers.testing.LiveJoinTestProtocol;
import dev.architectury.networking.NetworkManager;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class PlayAudioPacketS2C implements CustomPacketPayload {
    private static final DeferredTaskQueue PENDING_PLAYS = new DeferredTaskQueue();
    
    public static final CustomPacketPayload.Type<PlayAudioPacketS2C> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "play_audio"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayAudioPacketS2C> STREAM_CODEC = 
        StreamCodec.of(PlayAudioPacketS2C::encode, PlayAudioPacketS2C::decode);

    private final BlockPos pos;
    private final String speakerId;
    private final String audioId;
    private final String audioFilename;
    private final float playbackPositionSeconds;
    private final boolean isLooping;
    private final int maxRange;
    private final float maxVolume;
    private final float audioDropoff;

    /** Optional directional cone settings; null keeps omnidirectional behaviour. */
    private com.nstut.simplyspeakers.audio.DirectionalAudio.Extras extras;


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

    public static void encode(RegistryFriendlyByteBuf buffer, PlayAudioPacketS2C packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUtf(packet.speakerId);
        buffer.writeUtf(packet.audioId);
        buffer.writeUtf(packet.audioFilename);
        buffer.writeFloat(packet.playbackPositionSeconds);
        buffer.writeBoolean(packet.isLooping);
        buffer.writeVarInt(packet.maxRange);
        buffer.writeFloat(packet.maxVolume);
        buffer.writeFloat(packet.audioDropoff);
        boolean hasExtras = packet.extras != null;
        buffer.writeBoolean(hasExtras);
        if (hasExtras) {
            buffer.writeFloat(packet.extras.directionality());
            buffer.writeFloat(packet.extras.coneAngleDegrees());
            buffer.writeFloat(packet.extras.rearAttenuation());
            buffer.writeByte(packet.extras.facingOrdinal());
        }
    }

    public static PlayAudioPacketS2C decode(RegistryFriendlyByteBuf buffer) {
        PlayAudioPacketS2C packet = new PlayAudioPacketS2C(
            buffer.readBlockPos(),
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readFloat(),
            buffer.readBoolean(),
            buffer.readVarInt(),
            buffer.readFloat(),
            buffer.readFloat()
        );
        if (buffer.readableBytes() >= 1 && buffer.readBoolean()) {
            packet.extras = new com.nstut.simplyspeakers.audio.DirectionalAudio.Extras(
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readByte());
        }
        return packet;
    }

    public static void handle(PlayAudioPacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> playOrDefer(packet));
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
        ClientAudioPlayer.play(packet.pos, packet.speakerId, metadata, packet.playbackPositionSeconds, packet.isLooping, packet.maxRange, packet.maxVolume, packet.audioDropoff, packet.getExtras());
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


    public void attachExtras(com.nstut.simplyspeakers.audio.DirectionalAudio.Extras directionalExtras) {
        this.extras = directionalExtras;
    }

    public com.nstut.simplyspeakers.audio.DirectionalAudio.Extras getExtras() {
        return extras;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
