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
        } else if (level != null && level.isClientSide()) {
            // For client-side, we need to get the state from the client registry
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
        if (level == null || level.isClientSide) {
            return;
        }
        
        SpeakerState state = getSpeakerState();
        if (state == null) {
            return;
        }
        
        if (state.isPlaying()) {
            return;
        }
        if (state.getAudioId() == null || state.getAudioId().isEmpty()) {
            return;
        }

        state.setPlaying(true);
        state.setPlaybackStartTick(level.getGameTime()); // Record start tick
        updateSpeakerState(state);
        listeningPlayers.clear(); // Reset listeners when starting fresh
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        
        // Notify all proxy speakers in the network
        notifyProxySpeakers("play");
    }

    /**
     * Stops playing the audio.
     */
    public void stopAudio() {
        if (level == null || level.isClientSide) {
            return;
        }
        
        SpeakerState state = getSpeakerState();
        if (state == null) {
            return;
        }
        
        if (!state.isPlaying()) {
            return;
        }

        state.setPlaying(false);
        state.setPlaybackStartTick(-1); // Reset start tick
        updateSpeakerState(state);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

        // Send stop packet to all players who were listening

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
     * Notifies all clients about the current speaker state.
     */
    private void notifyClientsOfStateChange() {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && !speakerId.isEmpty()) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                // Send notification packet to all players
                // We'll use "update" as the action to indicate a state change
                com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C updatePacket =
                    new com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C(
                        speakerId,
                        "update",
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
        if (level != null && !level.isClientSide) {
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
                
                // Notify clients of the loop state change
                notifyClientsOfStateChange();
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
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setLooping(looping);
            }
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
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setAudioId(audioId);
                state.setAudioFilename(filename);
            }
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
    
    /**
     * Updates the max volume setting.
     *
     * @param maxVolume The new max volume (0.0 to 1.0)
     */
    public void setMaxVolume(float maxVolume) {
        if (level != null && !level.isClientSide) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setMaxVolume(maxVolume);
                updateSpeakerState(state);
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }
    
    /**
     * Updates the max range setting.
     *
     * @param maxRange The new max range (1 to Config.MAX_RANGE)
     */
    public void setMaxRange(int maxRange) {
        if (level != null && !level.isClientSide) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setMaxRange(maxRange);
                updateSpeakerState(state);
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }
    
    /**
     * Updates the audio dropoff setting.
     *
     * @param audioDropoff The new audio dropoff (0.0 to 1.0)
     */
    public void setAudioDropoff(float audioDropoff) {
        if (level != null && !level.isClientSide) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setAudioDropoff(audioDropoff);
                updateSpeakerState(state);
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }
    
    /**
     * Gets the max volume setting.
     *
     * @return The max volume (0.0 to 1.0)
     */
    public float getMaxVolume() {
        SpeakerState state = getSpeakerState();
        return state != null ? state.getMaxVolume() : 1.0f;
    }
    
    /**
     * Gets the max range setting.
     *
     * @return The max range (1 to Config.MAX_RANGE)
     */
    public int getMaxRange() {
        SpeakerState state = getSpeakerState();
        return state != null ? state.getMaxRange() : 16;
    }
    
    /**
     * Gets the audio dropoff setting.
     *
     * @return The audio dropoff (0.0 to 1.0)
     */
    public float getAudioDropoff() {
        SpeakerState state = getSpeakerState();
        return state != null ? state.getAudioDropoff() : 1.0f;
    }
}