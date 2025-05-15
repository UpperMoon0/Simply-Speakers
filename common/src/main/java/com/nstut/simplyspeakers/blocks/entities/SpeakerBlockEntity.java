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
        blockEntity.tick();
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

        // Send packet to all clients to play the audio
        float playbackPosition = 0.0f; // Start from the beginning
        com.nstut.simplyspeakers.network.PlayAudioPacketS2C packet = 
            new com.nstut.simplyspeakers.network.PlayAudioPacketS2C(worldPosition, audioPath, playbackPosition);
        
        // Send to all clients in the same dimension
        dev.architectury.networking.NetworkManager.sendToClients(
            (net.minecraft.server.level.ServerLevel)level, 
            com.nstut.simplyspeakers.network.PacketRegistries.CHANNEL, 
            packet);
            
        LOGGER.info("Playing audio: " + audioPath);
    }    /**
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

        listeningPlayers.clear(); // Clear the server-side tracking list
        
        // Send packet to all clients to stop the audio
        com.nstut.simplyspeakers.network.StopAudioPacketS2C packet = 
            new com.nstut.simplyspeakers.network.StopAudioPacketS2C(worldPosition);
        
        // Send to all clients in the same dimension
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            dev.architectury.networking.NetworkManager.sendToClients(serverLevel, 
                com.nstut.simplyspeakers.network.PacketRegistries.CHANNEL, packet);
            LOGGER.info("Stopping audio and sending stop packet");
        }
    }

    /**
     * Ticks the block entity.
     */
    private void tick() {
        if (level == null || level.isClientSide || !isPlaying) {
            return;
        }

        // We'll implement player tracking and packet sending in Step 2
        // Validate block state is still correct
        // Use the block instance from the registry for comparison
        if (!getBlockState().is(com.nstut.simplyspeakers.blocks.BlockRegistries.SPEAKER.get())) {
            stopAudio();
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
