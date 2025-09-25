package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import java.util.function.Supplier;

/**
 * Packet sent from server to client to synchronize proxy speakers.
 * This is used to notify clients about changes to speaker networks.
 * 
 * NOTE: This packet is kept for backward compatibility but the new SpeakerStateUpdatePacketS2C
 * should be used for new implementations.
 */
public class SyncProxySpeakerPacketS2C {
    private final BlockPos pos;
    private final String speakerId;
    private final String action; // "play" or "stop"
    private final String audioId;
    private final String audioFilename;
    private final long playbackStartTick;
    private final boolean isLooping;
    
    public SyncProxySpeakerPacketS2C(BlockPos pos, String speakerId, String action, String audioId, String audioFilename, long playbackStartTick, boolean isLooping) {
        this.pos = pos;
        this.speakerId = speakerId;
        this.action = action;
        this.audioId = audioId;
        this.audioFilename = audioFilename;
        this.playbackStartTick = playbackStartTick;
        this.isLooping = isLooping;
    }
    
    public SyncProxySpeakerPacketS2C(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.speakerId = buf.readUtf();
        this.action = buf.readUtf();
        this.audioId = buf.readUtf();
        this.audioFilename = buf.readUtf();
        this.playbackStartTick = buf.readLong();
        this.isLooping = buf.readBoolean();
    }
    
    public static void encode(SyncProxySpeakerPacketS2C pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.speakerId);
        buf.writeUtf(pkt.action);
        buf.writeUtf(pkt.audioId);
        buf.writeUtf(pkt.audioFilename);
        buf.writeLong(pkt.playbackStartTick);
        buf.writeBoolean(pkt.isLooping);
    }

    public static void handle(SyncProxySpeakerPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        // Ensure this code runs only on the client side
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.isClientSide) {
            context.queue(() -> {
                SimplySpeakers.LOGGER.info("CLIENT: Received SyncProxySpeakerPacketS2C for pos: {}, speakerId: '{}', action: {}", pkt.pos, pkt.speakerId, pkt.action);
                
                // Handle the synchronization on the client
                // In the new system, we would use SpeakerStateUpdatePacketS2C instead
                // But we keep this for backward compatibility
                handleProxySpeakerSync(pkt);
            });
        }
    }
    
    private static void handleProxySpeakerSync(SyncProxySpeakerPacketS2C pkt) {
        // This method will be called on the client thread
        SimplySpeakers.LOGGER.info("CLIENT: Handling proxy speaker sync for speakerId: '{}', action: {}", pkt.speakerId, pkt.action);
        
        // In the new system, we would find proxy speakers differently
        // For now, we'll just handle the audio playback directly at the specified position
        // This is a simplified approach for backward compatibility
        if ("play".equals(pkt.action)) {
            SimplySpeakers.LOGGER.info("CLIENT: Playing audio for proxy speaker at {}", pkt.pos);
            AudioFileMetadata metadata = new AudioFileMetadata(pkt.audioId, pkt.audioFilename);
            
            // Calculate playback position based on playback start tick
            float playbackPositionSeconds = 0.0f;
            if (pkt.playbackStartTick > 0) {
                long currentTick = Minecraft.getInstance().level.getGameTime();
                long ticksElapsed = currentTick - pkt.playbackStartTick;
                playbackPositionSeconds = ticksElapsed / 20.0f; // 20 ticks per second
                if (playbackPositionSeconds < 0) playbackPositionSeconds = 0;
            }
            
            ClientAudioPlayer.play(pkt.pos, metadata, playbackPositionSeconds, pkt.isLooping);
        } else if ("stop".equals(pkt.action)) {
            SimplySpeakers.LOGGER.info("CLIENT: Stopping audio for proxy speaker at {}", pkt.pos);
            ClientAudioPlayer.stop(pkt.pos);
        }
    }
    
    // Getters
    public BlockPos getPos() {
        return pos;
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
}