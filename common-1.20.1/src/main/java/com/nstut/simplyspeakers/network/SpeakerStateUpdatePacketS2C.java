package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerRegistry;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Packet sent from server to client to update speaker state information.
 * This is used to notify clients about changes to speaker networks.
 */
public class SpeakerStateUpdatePacketS2C {
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
    
    public SpeakerStateUpdatePacketS2C(FriendlyByteBuf buf) {
        this.speakerId = buf.readUtf();
        this.action = buf.readUtf();
        this.audioId = buf.readUtf();
        this.audioFilename = buf.readUtf();
        this.playbackStartTick = buf.readLong();
        this.isLooping = buf.readBoolean();
    }
    
    public static void encode(SpeakerStateUpdatePacketS2C pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.speakerId);
        buf.writeUtf(pkt.action);
        buf.writeUtf(pkt.audioId);
        buf.writeUtf(pkt.audioFilename);
        buf.writeLong(pkt.playbackStartTick);
        buf.writeBoolean(pkt.isLooping);
    }
    
    public static void handle(SpeakerStateUpdatePacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        // Ensure this code runs only on the client side
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.isClientSide) {
            context.queue(() -> handleSpeakerStateUpdate(pkt));
        }
    }
    
    private static void handleSpeakerStateUpdate(SpeakerStateUpdatePacketS2C pkt) {
        if (!SpeakerLink.isLinkableId(pkt.speakerId)) {
            return;
        }
        
        ClientAudioPlayer.setLooping("net_" + pkt.speakerId.trim(), pkt.isLooping);

        SpeakerState state = SpeakerRegistry.getOrCreateSpeakerState(pkt.speakerId);
        if (state != null) {
            state.setAudioId(pkt.audioId);
            state.setAudioFilename(pkt.audioFilename);
            state.setPlaybackStartTick(pkt.playbackStartTick);
            state.setLooping(pkt.isLooping);
            
            if ("play".equals(pkt.action)) {
                state.setPlaying(true);
            } else if ("stop".equals(pkt.action)) {
                state.setPlaying(false);
                state.setPlaybackStartTick(-1);
            }
        }
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
}
