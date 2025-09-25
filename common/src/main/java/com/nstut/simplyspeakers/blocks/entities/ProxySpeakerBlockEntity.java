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
    
    private final Set<UUID> listeningPlayers = new HashSet<>(); // Track players currently hearing the sound
    private String speakerId = "";
    private String initialSpeakerId = ""; // Store the initial speakerId for comparison

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
        if (level != null) {
            String oldSpeakerId = this.speakerId;
            this.speakerId = speakerId;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            
            if (!level.isClientSide) {
                // Update server registry
                if (!oldSpeakerId.equals(speakerId)) {
                    SpeakerRegistry.updateProxySpeakerId(level, worldPosition, oldSpeakerId, speakerId);
                }
            }
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
        
        if (state.isPlaying()) {
            SimplySpeakers.LOGGER.debug("playAudio exit: Already playing audio '{}' at {}", state.getAudioId(), worldPosition);
            return;
        }
        if (state.getAudioId() == null || state.getAudioId().isEmpty()) {
            SimplySpeakers.LOGGER.warn("playAudio exit: Audio ID is empty for proxy speaker at {}, cannot play.", getBlockPos());
            return;
        }

        state.setPlaying(true);
        // Only set playbackStartTick if it hasn't been set already
        if (state.getPlaybackStartTick() == -1) {
            state.setPlaybackStartTick(level.getGameTime()); // Record start tick
        }
        updateSpeakerState(state);
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
        
        if (!state.isPlaying()) {
            SimplySpeakers.LOGGER.debug("stopAudio exit: Not currently playing at {}.", worldPosition);
            return;
        }

        state.setPlaying(false);
        state.setPlaybackStartTick(-1); // Reset start tick
        updateSpeakerState(state);
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

        // Check if the block is powered by redstone
        boolean isPowered = currentState.getValue(ProxySpeakerBlock.POWERED);
        
        // If not powered, ensure audio is stopped for all players
        if (!isPowered) {
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
            
            // Also ensure our internal state is consistent
            SpeakerState state = getSpeakerState();
            if (state != null && state.isPlaying()) {
                state.setPlaying(false);
                state.setPlaybackStartTick(-1);
                updateSpeakerState(state);
                listeningPlayers.clear();
                setChanged();
                currentLevel.sendBlockUpdated(currentPos, currentState, currentState, 3);
                SimplySpeakers.LOGGER.info("Proxy speaker at {} stopped due to redstone signal off", currentPos);
            } else if (!listeningPlayers.isEmpty()) {
                listeningPlayers.clear();
            }
            return;
        }

        SpeakerState state = getSpeakerState();
        if (state == null) return;
        
        if (!state.isPlaying()) {
            // If not playing, ensure no players are marked as listening (e.g., after a stop command)
            if (!listeningPlayers.isEmpty()) {
                SimplySpeakers.LOGGER.debug("Audio stopped at {}, but {} players were still in listeningPlayers set. Clearing.", worldPosition, listeningPlayers.size());
                listeningPlayers.clear();
            }
            return;
        }

        // If playing and powered, manage listeners
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
                
                // If this proxy speaker was playing, continue playing based on own state
                SpeakerState state = SpeakerRegistry.getSpeakerState(speakerId);
                if (state != null && state.isPlaying()) {
                    SimplySpeakers.LOGGER.info("Proxy speaker at {} was playing, continuing to play based on own state", worldPosition);
                    // Calculate playback position based on our own start tick
                    float playbackPositionSeconds = state.getPlaybackPositionSeconds(level.getGameTime());
                    // Continue playing at the correct position
                    playAudio(playbackPositionSeconds);
                } else {
                    SimplySpeakers.LOGGER.info("Proxy speaker at {} is not playing or has empty speakerId. isPlaying: {}, speakerId: '{}'", 
                        worldPosition, state != null ? state.isPlaying() : false, speakerId);
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
        
        // PERFORMANCE FIX: Don't save listeningPlayers set to NBT as it's runtime-only data
        // This prevents accumulation of player UUIDs in save files
    }

    public boolean isLooping() {
        SpeakerState state = getSpeakerState();
        return state != null ? state.isLooping() : false;
    }

    public boolean isPlaying() {
        SpeakerState state = getSpeakerState();
        return state != null ? state.isPlaying() : false;
    }

    public String getAudioId() {
        SpeakerState state = getSpeakerState();
        return state != null ? state.getAudioId() : "";
    }

    public String getAudioFilename() {
        SpeakerState state = getSpeakerState();
        return state != null ? state.getAudioFilename() : "";
    }

    public long getPlaybackStartTick() {
        SpeakerState state = getSpeakerState();
        return state != null ? state.getPlaybackStartTick() : -1;
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