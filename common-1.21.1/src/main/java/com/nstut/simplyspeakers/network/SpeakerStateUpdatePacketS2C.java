package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.client.ClientSpeakerRegistry;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet sent from server to client to update speaker state information.
 * This is used to notify clients about changes to speaker networks and standalone speakers.
 */
public class SpeakerStateUpdatePacketS2C implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpeakerStateUpdatePacketS2C> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "speaker_state_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpeakerStateUpdatePacketS2C> STREAM_CODEC =
            StreamCodec.of(SpeakerStateUpdatePacketS2C::encode, SpeakerStateUpdatePacketS2C::decode);

    private final BlockPos blockPos;
    private final boolean hasBlockPos;
    private final String speakerId;
    private final String action; // "play", "pause", "stop", "update"
    private final String audioId;
    private final String audioFilename;
    private final long playbackStartTick;
    private final boolean isLooping;
    /** Dimension-qualified registry key of the authoritative state; may be empty. */
    private final String fullStateKey;

    public SpeakerStateUpdatePacketS2C(BlockPos blockPos, String speakerId, String action, String audioId, String audioFilename, long playbackStartTick, boolean isLooping) {
        this(blockPos, speakerId, action, audioId, audioFilename, playbackStartTick, isLooping, "");
    }

    public SpeakerStateUpdatePacketS2C(BlockPos blockPos, String speakerId, String action, String audioId, String audioFilename, long playbackStartTick, boolean isLooping, String fullStateKey) {
        this(blockPos != null ? blockPos : BlockPos.ZERO, blockPos != null, speakerId, action, audioId, audioFilename, playbackStartTick, isLooping, fullStateKey);
    }

    private SpeakerStateUpdatePacketS2C(BlockPos blockPos, boolean hasBlockPos, String speakerId, String action, String audioId, String audioFilename, long playbackStartTick, boolean isLooping, String fullStateKey) {
        this.blockPos = blockPos;
        this.hasBlockPos = hasBlockPos;
        this.speakerId = speakerId != null ? speakerId : "";
        this.action = action != null ? action : "update";
        this.audioId = audioId != null ? audioId : "";
        this.audioFilename = audioFilename != null ? audioFilename : "";
        this.playbackStartTick = playbackStartTick;
        this.isLooping = isLooping;
        this.fullStateKey = fullStateKey != null ? fullStateKey : "";
    }

    public SpeakerStateUpdatePacketS2C(String speakerId, String action, String audioId, String audioFilename, long playbackStartTick, boolean isLooping) {
        this(BlockPos.ZERO, false, speakerId, action, audioId, audioFilename, playbackStartTick, isLooping, "");
    }

    public static void encode(RegistryFriendlyByteBuf buffer, SpeakerStateUpdatePacketS2C packet) {
        buffer.writeBlockPos(packet.blockPos);
        buffer.writeBoolean(packet.hasBlockPos);
        buffer.writeUtf(packet.speakerId);
        buffer.writeUtf(packet.action);
        buffer.writeUtf(packet.audioId);
        buffer.writeUtf(packet.audioFilename);
        buffer.writeLong(packet.playbackStartTick);
        buffer.writeBoolean(packet.isLooping);
        buffer.writeUtf(packet.fullStateKey, 256);
    }

    public static SpeakerStateUpdatePacketS2C decode(RegistryFriendlyByteBuf buffer) {
        return new SpeakerStateUpdatePacketS2C(
                buffer.readBlockPos(),
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readBoolean(),
                buffer.readUtf(256)
        );
    }

    public static void handle(SpeakerStateUpdatePacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.isClientSide) {
                handleSpeakerStateUpdate(packet);
            }
        });
    }

    private static void handleSpeakerStateUpdate(SpeakerStateUpdatePacketS2C pkt) {
        boolean linked = SpeakerLink.isLinkableId(pkt.speakerId);
        String audioKey = linked ? "net_" + pkt.speakerId.trim() : "pos_" + pkt.blockPos.asLong();
        ClientAudioPlayer.setLooping(audioKey, pkt.isLooping);
        ClientSpeakerRegistry.setLooping(audioKey, pkt.isLooping);

        if (linked) {
            String linkKey = "net_" + pkt.speakerId.trim();
            SpeakerState state = ClientSpeakerRegistry.getOrCreateState(linkKey);
            state.setAudioId(pkt.audioId);
            state.setAudioFilename(pkt.audioFilename);
            state.setPlaybackStartTick(pkt.playbackStartTick);
            state.setLooping(pkt.isLooping);

            if ("play".equals(pkt.action)) {
                state.setPlaying(true);
                state.setPaused(false);
            } else if ("pause".equals(pkt.action)) {
                state.setPlaying(true);
                state.setPaused(true);
                ClientAudioPlayer.stopNetwork(linkKey);
            } else if ("stop".equals(pkt.action)) {
                state.setPlaying(false);
                state.setPaused(false);
                state.setPlaybackStartTick(-1);
                ClientAudioPlayer.stopNetwork(linkKey);
            }
            ClientSpeakerRegistry.updateState(linkKey, state);
        } else if (pkt.hasBlockPos && Minecraft.getInstance().level != null) {
            if ("stop".equals(pkt.action) || "pause".equals(pkt.action)) ClientAudioPlayer.stop(pkt.blockPos);
            var be = Minecraft.getInstance().level.getBlockEntity(pkt.blockPos);
            if (be instanceof SpeakerBlockEntity speakerBE) {
                SpeakerState state = ClientSpeakerRegistry.getOrCreateState(speakerBE.getStateKey());
                state.setAudioId(pkt.audioId);
                state.setAudioFilename(pkt.audioFilename);
                state.setPlaybackStartTick(pkt.playbackStartTick);
                state.setLooping(pkt.isLooping);

                if ("play".equals(pkt.action)) {
                    state.setPlaying(true);
                    state.setPaused(false);
                } else if ("pause".equals(pkt.action)) {
                    state.setPlaying(true);
                    state.setPaused(true);
                } else if ("stop".equals(pkt.action)) {
                    state.setPlaying(false);
                    state.setPaused(false);
                    state.setPlaybackStartTick(-1);
                }
                ClientSpeakerRegistry.updateState(speakerBE.getStateKey(), state);
            }
        }

        if (Minecraft.getInstance().screen instanceof SpeakerScreen screen) {
            // Match by full state key first so network-wide broadcasts (which may carry a
            // physical position of any linked speaker, or none) still reach the open GUI.
            boolean matchesScreen = (pkt.hasBlockPos && pkt.blockPos.equals(screen.getBlockEntityPos()))
                    || (!pkt.fullStateKey.isEmpty() && pkt.fullStateKey.equals(screen.getFullStateKey()))
                    || (linked && pkt.speakerId.trim().equals(screen.getSpeakerId().trim()));
            if (matchesScreen) {
                screen.refreshFromState(pkt.audioId, pkt.audioFilename, pkt.isLooping);
            }
        }
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public boolean hasBlockPos() {
        return hasBlockPos;
    }

    public String getFullStateKey() {
        return fullStateKey;
    }

    public String getSpeakerId() {
        return speakerId;
    }

    public String getAction() {
        return action;
    }

    public String getAudioId() {
        return audioId;
    }

    public String getAudioFilename() {
        return audioFilename;
    }

    public long getPlaybackStartTick() {
        return playbackStartTick;
    }

    public boolean isLooping() {
        return isLooping;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
