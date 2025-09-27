package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;

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
        
        // Handle proxy speakers
        handleProxySpeakerStateUpdate(pkt);
        
        // Handle regular speakers
        handleRegularSpeakerStateUpdate(pkt);
    }
    
    private static void handleProxySpeakerStateUpdate(SpeakerStateUpdatePacketS2C pkt) {
        // Find all proxy speakers with the matching speaker ID in the current level
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().player != null) {
            // Get player position to limit search area
            BlockPos playerPos = Minecraft.getInstance().player.blockPosition();
            int playerChunkX = playerPos.getX() >> 4;
            int playerChunkZ = playerPos.getZ() >> 4;
            
            // Define search radius (in chunks) - using simulation distance
            int searchRadius = Minecraft.getInstance().level.getServerSimulationDistance();
            if (searchRadius <= 0) {
                searchRadius = 8; // Fallback radius
            }
            
            ClientChunkCache chunkSource = Minecraft.getInstance().level.getChunkSource();
            
            // Iterate through chunks in the search area
            for (int cx = playerChunkX - searchRadius; cx <= playerChunkX + searchRadius; cx++) {
                for (int cz = playerChunkZ - searchRadius; cz <= playerChunkZ + searchRadius; cz++) {
                    LevelChunk chunk = chunkSource.getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk != null) {
                        // Process each block entity in the chunk
                        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                            if (blockEntity instanceof ProxySpeakerBlockEntity proxySpeaker) {
                                if (pkt.speakerId.equals(proxySpeaker.getSpeakerId())) {
                                    BlockPos pos = proxySpeaker.getBlockPos();
                                    SimplySpeakers.LOGGER.info("CLIENT: Found proxy speaker at {} with matching speakerId: '{}'", pos, pkt.speakerId);
                                    
                                    if ("play".equals(pkt.action)) {
                                        SimplySpeakers.LOGGER.info("CLIENT: Playing audio for proxy speaker at {}", pos);
                                        AudioFileMetadata metadata = new AudioFileMetadata(pkt.audioId, pkt.audioFilename);
                                        
                                        // Calculate playback position based on playback start tick
                                        float playbackPositionSeconds = 0.0f;
                                        if (pkt.playbackStartTick > 0) {
                                            long currentTick = Minecraft.getInstance().level.getGameTime();
                                            long ticksElapsed = currentTick - pkt.playbackStartTick;
                                            playbackPositionSeconds = ticksElapsed / 20.0f; // 20 ticks per second
                                            if (playbackPositionSeconds < 0) playbackPositionSeconds = 0;
                                        }
                                        
                                        ClientAudioPlayer.play(pos, metadata, playbackPositionSeconds, pkt.isLooping);
                                    } else if ("stop".equals(pkt.action)) {
                                        SimplySpeakers.LOGGER.info("CLIENT: Stopping audio for proxy speaker at {}", pos);
                                        ClientAudioPlayer.stop(pos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private static void handleRegularSpeakerStateUpdate(SpeakerStateUpdatePacketS2C pkt) {
        // Update the client-side speaker registry with the new state
        SimplySpeakers.LOGGER.info("CLIENT: Updating client-side speaker state for speakerId: '{}'", pkt.speakerId);
        
        // Get or create the speaker state in the client registry
        com.nstut.simplyspeakers.SpeakerState state = com.nstut.simplyspeakers.SpeakerRegistry.getOrCreateSpeakerState(pkt.speakerId);
        if (state != null) {
            state.setAudioId(pkt.audioId);
            state.setAudioFilename(pkt.audioFilename);
            state.setPlaybackStartTick(pkt.playbackStartTick);
            state.setLooping(pkt.isLooping);
            SimplySpeakers.LOGGER.info("CLIENT: Updated speaker state - audioId: {}, isLooping: {}", pkt.audioId, pkt.isLooping);
        }
        
        // Handle specific actions
        if ("play".equals(pkt.action)) {
            SimplySpeakers.LOGGER.info("CLIENT: Play action received for speakerId: '{}'", pkt.speakerId);
            if (state != null) {
                state.setPlaying(true);
            }
        } else if ("stop".equals(pkt.action)) {
            SimplySpeakers.LOGGER.info("CLIENT: Stop action received for speakerId: '{}'", pkt.speakerId);
            if (state != null) {
                state.setPlaying(false);
                state.setPlaybackStartTick(-1);
            }
        } else if ("update".equals(pkt.action)) {
            SimplySpeakers.LOGGER.info("CLIENT: Update action received for speakerId: '{}'", pkt.speakerId);
            // For update action, we've already updated the state above
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