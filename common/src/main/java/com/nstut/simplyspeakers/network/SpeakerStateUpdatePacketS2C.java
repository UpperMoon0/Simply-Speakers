package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
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
            context.queue(() -> {
                SimplySpeakers.LOGGER.info("CLIENT: Received SpeakerStateUpdatePacketS2C for speakerId: '{}', action: {}", pkt.speakerId, pkt.action);
                
                // Handle the speaker state update on the client
                handleSpeakerStateUpdate(pkt);
            });
        }
    }
    
    private static void handleSpeakerStateUpdate(SpeakerStateUpdatePacketS2C pkt) {
        // This method will be called on the client thread
        SimplySpeakers.LOGGER.info("CLIENT: Handling speaker state update for speakerId: '{}', action: {}", pkt.speakerId, pkt.action);
        
        // For now, we'll use a simpler approach that doesn't require iterating through all block entities
        // In a more sophisticated implementation, we might want to maintain a client-side registry
        // or use a more efficient method to track proxy speakers
        
        // Since we don't have an efficient way to find all proxy speakers with a specific speaker ID,
        // we'll log a message and let the client handle this in a different way
        SimplySpeakers.LOGGER.warn("CLIENT: SpeakerStateUpdatePacketS2C received but no efficient way to find proxy speakers. " +
            "This implementation needs to be improved.");
        
        // TODO: Implement a more efficient way to find and update proxy speakers
        // This might involve maintaining a client-side registry or using a different approach
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