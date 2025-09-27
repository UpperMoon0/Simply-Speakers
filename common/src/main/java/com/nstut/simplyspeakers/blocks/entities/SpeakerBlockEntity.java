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
import com.nstut.simplyspeakers.blocks.SpeakerBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Block entity for the Speaker block.
 */
@Getter
public class SpeakerBlockEntity extends BlockEntity {

    private static final String NBT_SPEAKER_ID = "SpeakerID";
    
    private final Set<UUID> listeningPlayers = new HashSet<>(); // Track players currently hearing the sound
    private String speakerId = "";
    private String initialSpeakerId = ""; // Store the initial speakerId for comparison

    /**
     * Constructs a SpeakerBlockEntity.
     *
     * @param pos The block position
     * @param state The block state
     */
    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistries.SPEAKER.get(), pos, state);
        // Register with the registry for new instances
        if (level != null && !level.isClientSide) {
            SpeakerRegistry.registerSpeaker(level, pos, speakerId);
            initialSpeakerId = speakerId; // Store the initial speakerId
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

            // Update registry
            if (!oldSpeakerId.equals(speakerId)) {
                SpeakerRegistry.updateSpeakerId(level, worldPosition, oldSpeakerId, speakerId);
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
    
    /**
     * Sets the selected audio for this speaker.
     *
     * @param audioId The audio ID
     * @param filename The audio filename
     */
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
    public static void serverTick(Level level, BlockPos pos, BlockState state, SpeakerBlockEntity blockEntity) {
        blockEntity.tick(level, pos, state); // Pass parameters to tick method
    }
    
    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            stopAudio(); // Ensure stop logic runs
            // Unregister from the registry
            SpeakerRegistry.unregisterSpeaker(level, worldPosition, speakerId);
        }
        super.setRemoved();
    }
    
    /**
     * Starts playing the audio.
     */
    public void playAudio() {
        SimplySpeakers.LOGGER.debug("playAudio called for speaker at {}", worldPosition);
        if (level == null || level.isClientSide) {
            SimplySpeakers.LOGGER.debug("playAudio exit: Level is null or client side. isClientSide={}", level != null && level.isClientSide);
            return;
        }
        
        SpeakerState state = getSpeakerState();
        if (state == null) {
            SimplySpeakers.LOGGER.warn("playAudio exit: Could not get speaker state for speaker at {}", getBlockPos());
            return;
        }
        
        if (state.isPlaying()) {
            SimplySpeakers.LOGGER.debug("playAudio exit: Already playing audio '{}' at {}", state.getAudioId(), worldPosition);
            return;
        }
        if (state.getAudioId() == null || state.getAudioId().isEmpty()) {
            SimplySpeakers.LOGGER.warn("playAudio exit: Audio ID is empty for speaker at {}, cannot play.", getBlockPos());
            return;
        }

        state.setPlaying(true);
        state.setPlaybackStartTick(level.getGameTime()); // Record start tick
        updateSpeakerState(state);
        listeningPlayers.clear(); // Reset listeners when starting fresh
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            
        SimplySpeakers.LOGGER.info("SERVER: Started audio playback: '{}' at tick {} at {}. Looping: {}", 
            state.getAudioId(), state.getPlaybackStartTick(), worldPosition, state.isLooping());
        
        // Notify all proxy speakers in the network
        notifyProxySpeakers("play");
    }

    /**
     * Stops playing the audio.
     */
    public void stopAudio() {
        SimplySpeakers.LOGGER.debug("stopAudio called for speaker at {}", worldPosition);
        if (level == null || level.isClientSide) {
            SimplySpeakers.LOGGER.debug("stopAudio exit: Level is null or client side.");
            return;
        }
        
        SpeakerState state = getSpeakerState();
        if (state == null) {
            SimplySpeakers.LOGGER.warn("stopAudio exit: Could not get speaker state for speaker at {}", getBlockPos());
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
        
        // Notify all proxy speakers in the network
        notifyProxySpeakers("stop");
    }
    
    /**
     * Notifies all proxy speakers in the network about state changes.
     */
    private void notifyProxySpeakers(String action) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && !speakerId.isEmpty()) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                // Send notification packet to all players
                com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C updatePacket = 
                    new com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C(
                        speakerId,
                        action,
                        state.getAudioId(),
                        state.getAudioFilename(),
                        state.getPlaybackStartTick(),
                        state.isLooping()
                    );
                PacketRegistries.CHANNEL.sendToPlayers(serverLevel.players(), updatePacket);
            }
        }
    }

    /**
     * Ticks the block entity.
     */
    private void tick(Level currentLevel, BlockPos currentPos, BlockState currentState) {
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }

        // Validate block state is still correct
        if (!currentState.is(com.nstut.simplyspeakers.blocks.BlockRegistries.SPEAKER.get())) {
            if (getSpeakerState().isPlaying()) stopAudio(); // Stop audio if the block is no longer a speaker
            return;
        }

        // Check if the block is powered by redstone
        boolean isPowered = currentState.getValue(SpeakerBlock.POWERED);
        
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
                SimplySpeakers.LOGGER.info("Speaker at {} stopped due to redstone signal off", currentPos);
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
                SimplySpeakers.LOGGER.debug("Player {} entered range of speaker at {}. Sending play packet with offset {}s.", player.getName().getString(), currentPos, playbackPositionSeconds);
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
                    SimplySpeakers.LOGGER.debug("Player {} left range of speaker at {}. Sending stop packet.", serverPlayerInstance.getName().getString(), currentPos);
                }
                listeningPlayers.remove(playerId);
            }
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        
        SimplySpeakers.LOGGER.info("SpeakerBlockEntity.load called for speaker at {}", worldPosition);
        
        // Load speaker ID
        speakerId = tag.contains(NBT_SPEAKER_ID) ? tag.getString(NBT_SPEAKER_ID) : "";
        
        // Clear runtime data on load - listeningPlayers should not persist across saves
        listeningPlayers.clear();
        
        // Register with the registry when loaded from NBT
        if (level != null && !level.isClientSide) {
            SimplySpeakers.LOGGER.info("Loading speaker at {} with speakerId: '{}'", worldPosition, speakerId);
            // Update registry if speakerId has changed from initial value
            if (!initialSpeakerId.equals(speakerId)) {
                SpeakerRegistry.updateSpeakerId(level, worldPosition, initialSpeakerId, speakerId);
                initialSpeakerId = speakerId; // Update the initial speakerId
            } else {
                SpeakerRegistry.registerSpeaker(level, worldPosition, speakerId);
            }
            
            // If this speaker was playing, notify proxy speakers
            SpeakerState state = SpeakerRegistry.getSpeakerState(speakerId);
            if (state != null && state.isPlaying()) {
                SimplySpeakers.LOGGER.info("Speaker at {} was playing, notifying proxy speakers", worldPosition);
                notifyProxySpeakers("play");
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

    /**
     * Updates the looping state.
     */
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
     * Updates the audio ID.
     */
    public void setAudio(String audioId, String filename) {
        // Server side only
        if (level != null && !level.isClientSide) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setAudioId(audioId);
                state.setAudioFilename(filename);
                updateSpeakerState(state);
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

                SimplySpeakers.LOGGER.info("Setting audio to: id={}, filename={} for speaker at {}", audioId, filename, worldPosition);
            }
        }
    }
    
    /**
     * Sets the audio ID.
     */
    public void setAudioId(String audioId) {
        setAudio(audioId, ""); // Set with an empty filename
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