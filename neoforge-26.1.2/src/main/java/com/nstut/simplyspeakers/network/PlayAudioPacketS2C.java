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
import net.minecraft.resources.Identifier;

public class PlayAudioPacketS2C implements CustomPacketPayload {
    private static final DeferredTaskQueue PENDING_PLAYS = new DeferredTaskQueue();
    
    public static final CustomPacketPayload.Type<PlayAudioPacketS2C> TYPE = 
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "play_audio"));
    
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
        SimplySpeakers.LOGGER.info("CLIENT: Received PlayAudioPacketS2C for pos: {}, audioId: {}, filename: {}, start: {}s, looping: {}", packet.pos, packet.audioId, packet.audioFilename, packet.playbackPositionSeconds, packet.isLooping);
        AudioFileMetadata metadata = new AudioFileMetadata(packet.audioId, packet.audioFilename);
        ClientAudioPlayer.play(packet.pos, metadata, packet.playbackPositionSeconds, packet.isLooping);
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
                    BlockPos.ZERO, LiveJoinTestProtocol.PROBE_AUDIO_ID, "probe.wav", 0.0f, false));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}



