package com.nstut.simplyspeakers.blocks.entities;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerRegistry;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.client.ClientSpeakerRegistry;
import com.nstut.simplyspeakers.network.PlayAudioPacketS2C;
import com.nstut.simplyspeakers.network.StopAudioPacketS2C;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.blocks.ProxySpeakerBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Block entity for the Proxy Speaker block.
 */
@Getter
public class ProxySpeakerBlockEntity extends BlockEntity {

    private static final String NBT_SPEAKER_ID = "SpeakerID";
    private static final String NBT_PROXY_PLAYING = "ProxyPlaying";
    
    private final Set<UUID> listeningPlayers = new HashSet<>(); // Track players currently hearing the sound
    private String speakerId = "";
    private String initialSpeakerId = ""; // Store the initial speakerId for comparison
    private boolean isProxyPlaying = false; // Track proxy speaker's individual playing state
    private float maxVolume = 1.0f; // Volume from 0.0 (silent) to 1.0 (full)
    private int maxRange = 16; // Range from 1 to Config.MAX_RANGE
    private float audioDropoff = 1.0f; // Dropoff from 0.0 (no dropoff) to 1.0 (linear)
    
    /**
     * Gets the proxy playing state.
     *
     * @return true if the proxy speaker is playing, false otherwise
     */
    public boolean isProxyPlaying() {
        return isProxyPlaying;
    }
    
    /**
     * Sets the proxy playing state.
     *
     * @param proxyPlaying The new proxy playing state
     */
    public void setProxyPlaying(boolean proxyPlaying) {
        this.isProxyPlaying = proxyPlaying;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        
        if (!level.isClientSide) {
            if (isProxyPlaying) {
                // When turning on, check if the main speaker is still playing
                SpeakerState state = getSpeakerState();
                if (state != null && state.isPlaying()) {
                    // Continue playing from where the main speaker left off
                    float playbackPositionSeconds = state.getPlaybackPositionSeconds(level.getGameTime());
                    playAudio(playbackPositionSeconds);
                }
            } else {
                // When turning off, stop audio for all players who might be listening
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    // Send stop packet to all players in range
                    double maxRangeSq = Config.speakerRange * Config.speakerRange;
                    Vec3 speakerCenterPos = Vec3.atCenterOf(worldPosition);
                    
                    for (ServerPlayer player : serverLevel.getPlayers(p -> p.position().distanceToSqr(speakerCenterPos) <= maxRangeSq)) {
                        StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(worldPosition);
                        PacketRegistries.CHANNEL.sendToPlayer(player, stopPacket);
                    }
                }
                
                // Clear the listening players but don't stop the main speaker
                listeningPlayers.clear();
            }
        }
    }
    
    /**
     * Updates the max volume setting.
     *
     * @param maxVolume The new max volume (0.0 to 1.0)
     */
    public void setMaxVolume(float maxVolume) {
        if (level != null && !level.isClientSide) {
            this.maxVolume = Math.max(0.0f, Math.min(1.0f, maxVolume)); // Clamp between 0.0 and 1.0
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        } else if (level != null) { // Client side
            this.maxVolume = Math.max(0.0f, Math.min(1.0f, maxVolume)); // Clamp between 0.0 and 1.0
        }
    }
    
    /**
     * Updates the max volume setting on the client side for optimistic UI updates.
     * This method should only be called on the client.
     *
     * @param maxVolume The new max volume (0.0 to 1.0)
     */
    public void setMaxVolumeClient(float maxVolume) {
        if (this.level != null && this.level.isClientSide) {
            this.maxVolume = Math.max(0.0f, Math.min(1.0f, maxVolume)); // Clamp between 0.0 and 1.0
        }
    }
    
    /**
     * Updates the max range setting.
     *
     * @param maxRange The new max range (1 to Config.MAX_RANGE)
     */
    public void setMaxRange(int maxRange) {
        if (level != null && !level.isClientSide) {
            this.maxRange = Math.max(1, Math.min(Config.MAX_RANGE, maxRange)); // Clamp between 1 and MAX_RANGE
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        } else if (level != null) { // Client side
            this.maxRange = Math.max(1, Math.min(Config.MAX_RANGE, maxRange)); // Clamp between 1 and MAX_RANGE
        }
    }
    
    /**
     * Updates the max range setting on the client side for optimistic UI updates.
     * This method should only be called on the client.
     *
     * @param maxRange The new max range (1 to Config.MAX_RANGE)
     */
    public void setMaxRangeClient(int maxRange) {
        if (this.level != null && this.level.isClientSide) {
            this.maxRange = Math.max(1, Math.min(Config.MAX_RANGE, maxRange)); // Clamp between 1 and MAX_RANGE
        }
    }
    
    /**
     * Updates the audio dropoff setting.
     *
     * @param audioDropoff The new audio dropoff (0.0 to 1.0)
     */
    public void setAudioDropoff(float audioDropoff) {
        if (level != null && !level.isClientSide) {
            this.audioDropoff = Math.max(0.0f, Math.min(1.0f, audioDropoff)); // Clamp between 0.0 and 1.0
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        } else if (level != null) { // Client side
            this.audioDropoff = Math.max(0.0f, Math.min(1.0f, audioDropoff)); // Clamp between 0.0 and 1.0
        }
    }
    
    /**
     * Updates the audio dropoff setting on the client side for optimistic UI updates.
     * This method should only be called on the client.
     *
     * @param audioDropoff The new audio dropoff (0.0 to 1.0)
     */
    public void setAudioDropoffClient(float audioDropoff) {
        if (this.level != null && this.level.isClientSide) {
            this.audioDropoff = Math.max(0.0f, Math.min(1.0f, audioDropoff)); // Clamp between 0.0 and 1.0
        }
    }
    
    /**
     * Gets the max volume setting.
     *
     * @return The max volume (0.0 to 1.0)
     */
    public float getMaxVolume() {
        return maxVolume;
    }
    
    /**
     * Gets the max range setting.
     *
     * @return The max range (1 to Config.MAX_RANGE)
     */
    public int getMaxRange() {
        return maxRange;
    }
    
    /**
     * Gets the audio dropoff setting.
     *
     * @return The audio dropoff (0.0 to 1.0)
     */
    public float getAudioDropoff() {
        return audioDropoff;
    }
    
    /**
     * Constructs a ProxySpeakerBlockEntity.
     *
     * @param pos The block position
     * @param state The block state
     */
    public ProxySpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistries.PROXY_SPEAKER.get(), pos, state);
        SimplySpeakers.LOGGER.info("Creating ProxySpeakerBlockEntity at {} with speakerId: '{}'", pos, speakerId);
        // Register with the registry for new instances
        if (level != null) {
            if (!level.isClientSide) {
                SimplySpeakers.LOGGER.info("Registering proxy speaker at {} with server registry", pos);
                SpeakerRegistry.registerProxySpeaker(level, pos, speakerId);
                initialSpeakerId = speakerId; // Store the initial speakerId
            } else {
                SimplySpeakers.LOGGER.info("Registering proxy speaker at {} with client registry", pos);
                ClientSpeakerRegistry.registerProxySpeaker(pos, speakerId);
                initialSpeakerId = speakerId; // Store the initial speakerId for client side as well
            }
        } else {
            SimplySpeakers.LOGGER.info("Level is null for proxy speaker at {}", pos);
        }
    }

    /**
     * Gets the speaker ID.
     *
     * @return The speaker ID
     */
    public String getSpeakerId() {
        return speakerId;
    }

    /**
     * Sets the speaker ID.
     *
     * @param speakerId The speaker ID
     */
    public void setSpeakerId(String speakerId) {
        if (level != null && !level.isClientSide) {
            String oldSpeakerId = this.speakerId;
            this.speakerId = speakerId;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            
            // Update server registry
            if (!oldSpeakerId.equals(speakerId)) {
                SpeakerRegistry.updateProxySpeakerId(level, worldPosition, oldSpeakerId, speakerId);
            }
        } else if (level != null) { // Client side
            this.speakerId = speakerId;
        }
    }
    
    /**
     * Gets the current speaker state from the registry.
     *
     * @return The speaker state
     */
    public SpeakerState getSpeakerState() {
        if (level != null && !level.isClientSide) {
            return SpeakerRegistry.getOrCreateSpeakerState(speakerId);
        }
        return null;
    }
    
    /**
     * Updates the speaker state in the registry.
     *
     * @param state The new speaker state
     */
    public void updateSpeakerState(SpeakerState state) {
        if (level != null && !level.isClientSide) {
            SpeakerRegistry.updateSpeakerState(speakerId, state);
        }
    }

    public void setSelectedAudio(String audioId, String filename) {
        if (level != null && !level.isClientSide) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setAudioId(audioId);
                state.setAudioFilename(filename);
                updateSpeakerState(state);
                // Stop any currently playing audio.
                if (state.isPlaying()) {
                    stopAudio();
                }
            }
        }
    }

    /**
     * Server-side tick method.
     * 
     * @param level The level
     * @param pos The block position
     * @param state The block state
     * @param blockEntity The block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ProxySpeakerBlockEntity blockEntity) {
        blockEntity.tick(level, pos, state); // Pass parameters to tick method
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            if (!level.isClientSide) {
                stopAudio(); // Ensure stop logic runs
                // Unregister from the server registry
                SpeakerRegistry.unregisterProxySpeaker(level, worldPosition, speakerId);
            } else {
                // Stop client audio if playing
                com.nstut.simplyspeakers.client.ClientAudioPlayer.stop(worldPosition);
                // Unregister from the client registry
                ClientSpeakerRegistry.unregisterProxySpeaker(worldPosition, speakerId);
            }
        }
        super.setRemoved();
    }
    
    /**
     * Starts playing the audio.
     */
    public void playAudio() {
        SimplySpeakers.LOGGER.debug("playAudio called for proxy speaker at {}", worldPosition);
        if (level == null || level.isClientSide) {
            SimplySpeakers.LOGGER.debug("playAudio exit: Level is null or client side. isClientSide={}", level != null && level.isClientSide);
            return;
        }
        
        SpeakerState state = getSpeakerState();
        if (state == null) {
            SimplySpeakers.LOGGER.warn("playAudio exit: Could not get speaker state for proxy speaker at {}", getBlockPos());
            return;
        }
        
        if (state.getAudioId() == null || state.getAudioId().isEmpty()) {
            SimplySpeakers.LOGGER.warn("playAudio exit: Audio ID is empty for proxy speaker at {}, cannot play.", getBlockPos());
            return;
        }

        listeningPlayers.clear(); // Reset listeners when starting fresh
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            
        SimplySpeakers.LOGGER.info("SERVER: Started audio playback: '{}' at tick {} at {}. Looping: {}",
            state.getAudioId(), state.getPlaybackStartTick(), worldPosition, state.isLooping());
    }
    
    /**
     * Starts playing the audio with specific parameters (used for synchronization).
     */
    public void playAudio(String audioId, String audioFilename, boolean looping) {
        if (level == null || level.isClientSide) {
            return;
        }
        
        SpeakerState state = getSpeakerState();
        if (state != null) {
            state.setAudioId(audioId);
            state.setAudioFilename(audioFilename);
            state.setLooping(looping);
            updateSpeakerState(state);
            playAudio();
        }
    }
    
    /**
     * Starts playing the audio at a specific position (used for synchronization).
     */
    public void playAudio(float playbackPositionSeconds) {
        if (level == null || level.isClientSide) {
            return;
        }
        
        SpeakerState state = getSpeakerState();
        if (state != null) {
            // Set the playback start tick based on the playback position
            if (playbackPositionSeconds > 0) {
                long ticksElapsed = (long) (playbackPositionSeconds * 20); // 20 ticks per second
                state.setPlaybackStartTick(level.getGameTime() - ticksElapsed);
            } else {
                state.setPlaybackStartTick(level.getGameTime());
            }
            updateSpeakerState(state);
            playAudio();
        }
    }

    /**
     * Stops playing the audio.
     */
    public void stopAudio() {
        SimplySpeakers.LOGGER.debug("stopAudio called for proxy speaker at {}", worldPosition);
        if (level == null || level.isClientSide) {
            SimplySpeakers.LOGGER.debug("stopAudio exit: Level is null or client side.");
            return;
        }
        
        SpeakerState state = getSpeakerState();
        if (state == null) {
            SimplySpeakers.LOGGER.warn("stopAudio exit: Could not get speaker state for proxy speaker at {}", getBlockPos());
            return;
        }

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

        // Send stop packet to all players who were listening
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(worldPosition);
            Set<UUID> playersToNotify = new HashSet<>(listeningPlayers); // Iterate over a copy
            int notifiedCount = 0;
            for (UUID playerId : playersToNotify) {
                net.minecraft.world.entity.player.Player genericPlayer = serverLevel.getPlayerByUUID(playerId);
                if (genericPlayer instanceof net.minecraft.server.level.ServerPlayer serverPlayerInstance) {
                    PacketRegistries.CHANNEL.sendToPlayer(serverPlayerInstance, stopPacket);
                    notifiedCount++;
                }
            }
            
            // Also send stop packets to all players in range to ensure audio stops completely
            double maxRangeSq = Config.speakerRange * Config.speakerRange;
            Vec3 speakerCenterPos = Vec3.atCenterOf(worldPosition);
            
            for (ServerPlayer player : serverLevel.getPlayers(p -> p.position().distanceToSqr(speakerCenterPos) <= maxRangeSq)) {
                // Only send to players not already in our listening list to avoid duplicate packets
                if (!playersToNotify.contains(player.getUUID())) {
                    PacketRegistries.CHANNEL.sendToPlayer(player, stopPacket);
                    notifiedCount++;
                }
            }
            
            SimplySpeakers.LOGGER.info("SERVER: Stopped audio at {} and sent stop packets to {} players.", worldPosition, notifiedCount);
        }
        listeningPlayers.clear(); // Clear the server-side tracking list
    }

    /**
     * Ticks the block entity.
     */
    private void tick(Level currentLevel, BlockPos currentPos, BlockState currentState) {
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }

        // Validate block state is still correct
        if (!currentState.is(com.nstut.simplyspeakers.blocks.BlockRegistries.PROXY_SPEAKER.get())) {
            if (getSpeakerState().isPlaying()) stopAudio(); // Stop audio if the block is no longer a proxy speaker
            return;
        }

        // Check if the proxy speaker is set to playing
        if (!isProxyPlaying) {
            // Stop audio for all players who might be listening
            if (currentLevel instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                // Send stop packet to all players in range
                double maxRangeSq = Config.speakerRange * Config.speakerRange;
                Vec3 speakerCenterPos = Vec3.atCenterOf(currentPos);
                
                for (ServerPlayer player : serverLevel.getPlayers(p -> p.position().distanceToSqr(speakerCenterPos) <= maxRangeSq)) {
                    StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(currentPos);
                    PacketRegistries.CHANNEL.sendToPlayer(player, stopPacket);
                }
            }
            
            // Clear the listening players
            if (!listeningPlayers.isEmpty()) {
                listeningPlayers.clear();
            }
            return;
        }

        SpeakerState state = getSpeakerState();
        if (state == null) return;
        
        // Check if the main speaker is actually playing
        if (!state.isPlaying()) {
            // If not playing, ensure no players are marked as listening (e.g., after a stop command)
            if (!listeningPlayers.isEmpty()) {
                SimplySpeakers.LOGGER.debug("Audio stopped at {}, but {} players were still in listeningPlayers set. Clearing.", worldPosition, listeningPlayers.size());
                listeningPlayers.clear();
            }
            return;
        }

        // If proxy is playing and main speaker is playing, manage listeners
        if (!(currentLevel instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }

        // PERFORMANCE FIX: Cache these expensive calculations
        double maxRangeSq = Config.speakerRange * Config.speakerRange;
        Vec3 speakerCenterPos = Vec3.atCenterOf(currentPos);
        Set<UUID> playersInRange = new HashSet<>();

        // PERFORMANCE FIX: Use getNearbyPlayers for better performance than iterating all players
        for (ServerPlayer player : serverLevel.getPlayers(p -> p.position().distanceToSqr(speakerCenterPos) <= maxRangeSq)) {
            playersInRange.add(player.getUUID());

            if (!listeningPlayers.contains(player.getUUID())) {
                // Player entered range or was not previously listening
                float playbackPositionSeconds = state.getPlaybackPositionSeconds(currentLevel.getGameTime());
                if (playbackPositionSeconds < 0) playbackPositionSeconds = 0; // Should not happen
                
                PlayAudioPacketS2C playPacket = new PlayAudioPacketS2C(currentPos, state.getAudioId(), state.getAudioFilename(), playbackPositionSeconds, state.isLooping());
                PacketRegistries.CHANNEL.sendToPlayer(player, playPacket);
                listeningPlayers.add(player.getUUID());
                SimplySpeakers.LOGGER.debug("Player {} entered range of proxy speaker at {}. Sending play packet with offset {}s.", player.getName().getString(), currentPos, playbackPositionSeconds);
            }
        }

        // PERFORMANCE FIX: Optimize player exit detection
        if (!listeningPlayers.isEmpty()) {
            Set<UUID> playersToStop = new HashSet<>(listeningPlayers);
            playersToStop.removeAll(playersInRange); // Players who were listening but are no longer in range

            for (UUID playerId : playersToStop) {
                net.minecraft.world.entity.player.Player genericPlayer = serverLevel.getPlayerByUUID(playerId);
                if (genericPlayer instanceof net.minecraft.server.level.ServerPlayer serverPlayerInstance) {
                    StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(currentPos);
                    PacketRegistries.CHANNEL.sendToPlayer(serverPlayerInstance, stopPacket);
                    SimplySpeakers.LOGGER.debug("Player {} left range of proxy speaker at {}. Sending stop packet.", serverPlayerInstance.getName().getString(), currentPos);
                }
                listeningPlayers.remove(playerId);
            }
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        
        // Load speaker ID
        speakerId = tag.contains(NBT_SPEAKER_ID) ? tag.getString(NBT_SPEAKER_ID) : "";
        
        
        // Load proxy playing state
        isProxyPlaying = tag.contains(NBT_PROXY_PLAYING) ? tag.getBoolean(NBT_PROXY_PLAYING) : false;
        
        // Load settings
        maxVolume = tag.contains("MaxVolume") ? tag.getFloat("MaxVolume") : 1.0f;
        maxRange = tag.contains("MaxRange") ? tag.getInt("MaxRange") : 16;
        audioDropoff = tag.contains("AudioDropoff") ? tag.getFloat("AudioDropoff") : 1.0f;
        
        // Clear runtime data on load - listeningPlayers should not persist across saves
        listeningPlayers.clear();
        // Register with the registry when loaded from NBT
        if (level != null) {
            if (!level.isClientSide) {
                SimplySpeakers.LOGGER.info("Loading proxy speaker at {} with speakerId: '{}'", worldPosition, speakerId);
                // Update registry if speakerId has changed from initial value
                if (!initialSpeakerId.equals(speakerId)) {
                    SpeakerRegistry.updateProxySpeakerId(level, worldPosition, initialSpeakerId, speakerId);
                    initialSpeakerId = speakerId; // Update the initial speakerId
                } else {
                    SpeakerRegistry.registerProxySpeaker(level, worldPosition, speakerId);
                }
                
                // If this proxy speaker was set to playing, check if we should continue playing
                if (isProxyPlaying) {
                    SpeakerState state = SpeakerRegistry.getSpeakerState(speakerId);
                    if (state != null && state.isPlaying()) {
                        SimplySpeakers.LOGGER.info("Proxy speaker at {} was set to playing, continuing to play based on main speaker state", worldPosition);
                        // Calculate playback position based on main speaker's start tick
                        float playbackPositionSeconds = state.getPlaybackPositionSeconds(level.getGameTime());
                        // Continue playing at the correct position
                        playAudio(playbackPositionSeconds);
                    } else {
                        SimplySpeakers.LOGGER.info("Proxy speaker at {} is set to playing but main speaker is not playing. isPlaying: {}, speakerId: '{}'",
                            worldPosition, state != null ? state.isPlaying() : false, speakerId);
                    }
                } else {
                    SimplySpeakers.LOGGER.info("Proxy speaker at {} is not set to playing. isProxyPlaying: {}, speakerId: '{}'",
                        worldPosition, isProxyPlaying, speakerId);
                }
            } else {
                // Update client registry if speakerId has changed from initial value
                if (!initialSpeakerId.equals(speakerId)) {
                    ClientSpeakerRegistry.updateProxySpeakerId(worldPosition, initialSpeakerId, speakerId);
                    initialSpeakerId = speakerId; // Update the initial speakerId
                } else {
                    ClientSpeakerRegistry.registerProxySpeaker(worldPosition, speakerId);
                }
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        
        // Save speaker ID
        if (!speakerId.isEmpty()) {
            tag.putString(NBT_SPEAKER_ID, speakerId);
        }
        
        // Save proxy playing state
        tag.putBoolean(NBT_PROXY_PLAYING, isProxyPlaying);
        
        // Save settings
        tag.putFloat("MaxVolume", maxVolume);
        tag.putInt("MaxRange", maxRange);
        tag.putFloat("AudioDropoff", audioDropoff);
        
        // PERFORMANCE FIX: Don't save listeningPlayers set to NBT as it's runtime-only data
        // This prevents accumulation of player UUIDs in save files
    }

    public boolean isLooping() {
        SpeakerState state = getSpeakerState();
        // Only return looping state if we're actually playing
        return (state != null && isProxyPlaying) ? state.isLooping() : false;
    }

    public boolean isPlaying() {
        // Return the proxy speaker's individual playing state
        return isProxyPlaying;
    }

    public String getAudioId() {
        SpeakerState state = getSpeakerState();
        // Only return audio ID if we're actually playing
        return (state != null && isProxyPlaying) ? state.getAudioId() : "";
    }

    public String getAudioFilename() {
        SpeakerState state = getSpeakerState();
        // Only return audio filename if we're actually playing
        return (state != null && isProxyPlaying) ? state.getAudioFilename() : "";
    }

    public long getPlaybackStartTick() {
        SpeakerState state = getSpeakerState();
        // Only return playback start tick if we're actually playing
        return (state != null && isProxyPlaying) ? state.getPlaybackStartTick() : -1;
    }

    public void setLooping(boolean looping) {
        if (level != null && !level.isClientSide) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setLooping(looping);
                updateSpeakerState(state);
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    /**
     * Updates the looping state on the client side for optimistic UI updates.
     * This method should only be called on the client.
     *
     * @param looping The new looping state.
     */
    public void setLoopingClient(boolean looping) {
        if (this.level != null && this.level.isClientSide) {
            // Client-side update for UI responsiveness
        }
    }

    /**
     * Updates the audio ID on the client side for optimistic UI updates.
     * This method should only be called on the client.
     *
     * @param audioId The new audio ID.
     */
    public void setAudioIdClient(String audioId, String filename) {
        if (this.level != null && this.level.isClientSide) {
            // Client-side update for UI responsiveness
        }
    }
    
    /**
     * Updates the speaker ID on the client side for optimistic UI updates.
     * This method should only be called on the client.
     *
     * @param speakerId The new speaker ID.
     */
    public void setSpeakerIdClient(String speakerId) {
        if (this.level != null && this.level.isClientSide) {
            this.speakerId = speakerId;
        }
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }
}