package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;

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
            context.queue(() -> {
                // Handle the speaker state update on the client
                handleSpeakerStateUpdate(packet);
            });
        }
    }
    
    private static void handleSpeakerStateUpdate(SpeakerStateUpdatePacketS2C packet) {
        // Handle proxy speakers
        handleProxySpeakerStateUpdate(packet);
        
        // Handle regular speakers
        handleRegularSpeakerStateUpdate(packet);
    }
    
    private static void handleProxySpeakerStateUpdate(SpeakerStateUpdatePacketS2C packet) {
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
                                if (packet.speakerId.equals(proxySpeaker.getSpeakerId())) {
                                    BlockPos pos = proxySpeaker.getBlockPos();
                                    
                                    if ("play".equals(packet.action)) {
                                        AudioFileMetadata metadata = new AudioFileMetadata(packet.audioId, packet.audioFilename);
                                        
                                        // Calculate playback position based on playback start tick
                                        float playbackPositionSeconds = 0.0f;
                                        if (packet.playbackStartTick > 0) {
                                            long currentTick = Minecraft.getInstance().level.getGameTime();
                                            long ticksElapsed = currentTick - packet.playbackStartTick;
                                            playbackPositionSeconds = ticksElapsed / 20.0f; // 20 ticks per second
                                            if (playbackPositionSeconds < 0) playbackPositionSeconds = 0;
                                        }
                                        
                                        ClientAudioPlayer.play(pos, metadata, playbackPositionSeconds, packet.isLooping);
                                    } else if ("stop".equals(packet.action)) {
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
    
    private static void handleRegularSpeakerStateUpdate(SpeakerStateUpdatePacketS2C packet) {
        // Update the client-side speaker registry with the new state
        
        // Get or create the speaker state in the client registry
        com.nstut.simplyspeakers.SpeakerState state = com.nstut.simplyspeakers.SpeakerRegistry.getOrCreateSpeakerState(packet.speakerId);
        if (state != null) {
            state.setAudioId(packet.audioId);
            state.setAudioFilename(packet.audioFilename);
            state.setPlaybackStartTick(packet.playbackStartTick);
            state.setLooping(packet.isLooping);
        }
        
        // Handle specific actions
        if ("play".equals(packet.action)) {
            if (state != null) {
                state.setPlaying(true);
            }
        } else if ("stop".equals(packet.action)) {
            if (state != null) {
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
