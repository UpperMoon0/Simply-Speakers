package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerRegistry;
import com.nstut.simplyspeakers.SpeakerState;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet sent from server to client to update speaker state information.
 * This is used to notify clients about changes to speaker networks.
 */
public class SpeakerStateUpdatePacketS2C implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<SpeakerStateUpdatePacketS2C> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "speaker_state_update"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, SpeakerStateUpdatePacketS2C> STREAM_CODEC = 
        StreamCodec.of(SpeakerStateUpdatePacketS2C::encode, SpeakerStateUpdatePacketS2C::decode);

    private final String speakerId;
    private final String action; // "play" or "stop"
    private final String audioId;
    private final String audioFilename;
    private final long playbackStartTick;
    private final boolean isLooping;
    
    public SpeakerStateUpdatePacketS2C(String speakerId, String action, String audioId, String audioFilename, long playbackStartTick, boolean isLooping) {
        this.speakerId = speakerId;
        this.action = action;
        this.audioId = audioId;
        this.audioFilename = audioFilename;
        this.playbackStartTick = playbackStartTick;
        this.isLooping = isLooping;
    }
    
    public static void encode(RegistryFriendlyByteBuf buffer, SpeakerStateUpdatePacketS2C packet) {
        buffer.writeUtf(packet.speakerId);
        buffer.writeUtf(packet.action);
        buffer.writeUtf(packet.audioId);
        buffer.writeUtf(packet.audioFilename);
        buffer.writeLong(packet.playbackStartTick);
        buffer.writeBoolean(packet.isLooping);
    }
    
    public static SpeakerStateUpdatePacketS2C decode(RegistryFriendlyByteBuf buffer) {
        return new SpeakerStateUpdatePacketS2C(
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readLong(),
            buffer.readBoolean()
        );
    }
    
    public static void handle(SpeakerStateUpdatePacketS2C packet, NetworkManager.PacketContext context) {
        // Ensure this code runs only on the client side
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.isClientSide) {
            context.queue(() -> handleSpeakerStateUpdate(packet));
        }
    }
    
    private static void handleSpeakerStateUpdate(SpeakerStateUpdatePacketS2C packet) {
        String netKey = SpeakerLink.isLinkableId(packet.speakerId) ? "net_" + packet.speakerId.trim() : packet.speakerId;
        com.nstut.simplyspeakers.client.ClientAudioPlayer.setLooping(netKey, packet.isLooping);

        SpeakerState state = com.nstut.simplyspeakers.client.ClientSpeakerRegistry.getOrCreateState(netKey);
        state.setAudioId(packet.audioId);
        state.setAudioFilename(packet.audioFilename);
        state.setPlaybackStartTick(packet.playbackStartTick);
        state.setLooping(packet.isLooping);
        
        if ("play".equals(packet.action)) {
            state.setPlaying(true);
        } else if ("stop".equals(packet.action)) {
            state.setPlaying(false);
            state.setPlaybackStartTick(-1);
        }
        com.nstut.simplyspeakers.client.ClientSpeakerRegistry.updateState(netKey, state);
    }
    
    // Getters
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
