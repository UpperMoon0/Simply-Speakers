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
import com.nstut.simplyspeakers.network.PlayAudioPacketS2C; 
import com.nstut.simplyspeakers.network.StopAudioPacketS2C; 
import com.nstut.simplyspeakers.network.PacketRegistries; 
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Block entity for the Speaker block.
 */
@Getter
public class SpeakerBlockEntity extends BlockEntity {
    private static final Logger LOGGER = Logger.getLogger(SpeakerBlockEntity.class.getName());

    private static final String NBT_AUDIO_PATH = "AudioPath";
    private static final String NBT_IS_PLAYING = "IsPlaying";
    private static final String NBT_START_TICK = "PlaybackStartTick";

    private String audioPath = "";
    private boolean isPlaying = false;
    private long playbackStartTick = -1; // Tick when playback started, -1 if not playing
    private final Set<UUID> listeningPlayers = new HashSet<>(); // Track players currently hearing the sound

    /**
     * Constructs a SpeakerBlockEntity.
     * 
     * @param pos The block position
     * @param state The block state
     */
    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistries.SPEAKER.get(), pos, state);
    }

    /**
     * Sets the audio path.
     * 
     * @param audioPath The path to the audio file
     */
    public void setAudioPath(String audioPath) {
        // Server side only
        if (level != null && !level.isClientSide) {
            this.audioPath = audioPath;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

            // We'll implement network packet sending in Step 2
            LOGGER.info("Setting audio path to: " + audioPath);
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
        }
        super.setRemoved();
    }    /**
     * Starts playing the audio.
     */
    public void playAudio() {
        if (level == null || level.isClientSide || isPlaying) {
            return;
        }
        if (audioPath == null || audioPath.isEmpty()) {
            LOGGER.warning("Audio path is empty, cannot play.");
            return;
        }

        isPlaying = true;
        playbackStartTick = level.getGameTime(); // Record start tick
        listeningPlayers.clear(); // Reset listeners when starting fresh
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            
        LOGGER.info("Starting audio: " + audioPath + " at tick " + playbackStartTick);
    }

    /**
     * Stops playing the audio.
     */
    public void stopAudio() {
        if (level == null || level.isClientSide || !isPlaying) {
            return;
        }

        isPlaying = false;
        playbackStartTick = -1; // Reset start tick
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
            LOGGER.info("Stopping audio and sent stop packets to " + notifiedCount + " former listeners.");
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
        if (!currentState.is(com.nstut.simplyspeakers.blocks.BlockRegistries.SPEAKER.get())) {
            if (isPlaying) stopAudio(); // Stop audio if the block is no longer a speaker
            return;
        }

        if (!isPlaying) {
            // If not playing, ensure no players are marked as listening (e.g., after a stop command)
            if (!listeningPlayers.isEmpty()) {
                LOGGER.fine("Audio stopped, but " + listeningPlayers.size() + " players were still in listeningPlayers set. Clearing.");
                listeningPlayers.clear();
            }
            return;
        }

        // If playing, manage listeners
        if (!(currentLevel instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }

        double maxRangeSq = Config.speakerRange * Config.speakerRange;
        Vec3 speakerCenterPos = Vec3.atCenterOf(currentPos);
        Set<UUID> playersInRange = new HashSet<>();

        for (ServerPlayer player : serverLevel.getPlayers(p -> true)) { // Iterate all players in the dimension
            if (player.position().distanceToSqr(speakerCenterPos) <= maxRangeSq) {
                playersInRange.add(player.getUUID());

                if (!listeningPlayers.contains(player.getUUID())) {
                    // Player entered range or was not previously listening
                    float playbackPositionSeconds = 0.0f;
                    if (playbackStartTick >= 0) {
                        long ticksElapsed = currentLevel.getGameTime() - playbackStartTick;
                        playbackPositionSeconds = ticksElapsed / 20.0f; // 20 ticks per second
                        if (playbackPositionSeconds < 0) playbackPositionSeconds = 0; // Should not happen
                    }
                    
                    PlayAudioPacketS2C playPacket = new PlayAudioPacketS2C(currentPos, audioPath, playbackPositionSeconds);
                    PacketRegistries.CHANNEL.sendToPlayer(player, playPacket);
                    listeningPlayers.add(player.getUUID());
                    LOGGER.fine("Player " + player.getName().getString() + " entered range. Sending play packet with offset " + playbackPositionSeconds + "s.");
                }
            }
        }

        // Check for players who left the range
        Set<UUID> playersToStop = new HashSet<>(listeningPlayers);
        playersToStop.removeAll(playersInRange); // Players who were listening but are no longer in range

        for (UUID playerId : playersToStop) {
            net.minecraft.world.entity.player.Player genericPlayer = serverLevel.getPlayerByUUID(playerId);
            if (genericPlayer instanceof net.minecraft.server.level.ServerPlayer serverPlayerInstance) {
                StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(currentPos);
                PacketRegistries.CHANNEL.sendToPlayer(serverPlayerInstance, stopPacket);
                LOGGER.fine("Player " + serverPlayerInstance.getName().getString() + " left range. Sending stop packet.");
            }
            listeningPlayers.remove(playerId);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        audioPath = tag.getString(NBT_AUDIO_PATH);
        isPlaying = tag.getBoolean(NBT_IS_PLAYING);
        playbackStartTick = tag.getLong(NBT_START_TICK);

        // Ensure consistency: if not playing, start tick should be -1
        if (!isPlaying) {
            playbackStartTick = -1;
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(NBT_AUDIO_PATH, audioPath);
        tag.putBoolean(NBT_IS_PLAYING, isPlaying);
        
        // Only save start tick if actually playing
        if (isPlaying && playbackStartTick != -1) {
            tag.putLong(NBT_START_TICK, playbackStartTick);
        } else {
            tag.putLong(NBT_START_TICK, -1L);
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
