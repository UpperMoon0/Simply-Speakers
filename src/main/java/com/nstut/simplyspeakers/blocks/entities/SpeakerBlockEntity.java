// Language: java
package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.blocks.BlockRegistries;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.network.PlayAudioPacketS2C;
import com.nstut.simplyspeakers.network.SpeakerBlockEntityPacketS2C;
import com.nstut.simplyspeakers.network.StopAudioPacketS2C;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

@Getter
public class SpeakerBlockEntity extends BlockEntity {

    private static final Logger LOGGER = Logger.getLogger(SpeakerBlockEntity.class.getName());

    private String audioPath = "";

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistries.SPEAKER.get(), pos, state);
    }

    public void setAudioPath(String audioPath) {
        // Server side only
        if (level != null && !level.isClientSide) {
            this.audioPath = audioPath;
            setChanged(); // Mark the block entity as changed for saving
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); // Notify clients of standard update

            // Also send explicit packet to all clients for immediate sync (reverting to simpler method)
            SpeakerBlockEntityPacketS2C packet = new SpeakerBlockEntityPacketS2C(getBlockPos(), audioPath);
            PacketRegistries.sendToClients(packet); // Use helper method again
        }
    }

    // Called when the block is broken or removed
    @Override
    public void setRemoved() {
        // Ensure stop packet is sent before removing
        // Ensure stop packet is sent before removing
        if (level != null && !level.isClientSide) {
            StopAudioPacketS2C packet = new StopAudioPacketS2C(getBlockPos());
            PacketRegistries.sendToClients(packet); // Use helper method again
        }
        super.setRemoved();
    }

    public void playAudio() {
        if (level == null || level.isClientSide) {
            return;
        }

        if (audioPath == null || audioPath.isEmpty()) {
            LOGGER.warning("Audio path is empty.");
            return;
        }

        // Send a packet to trigger all clients to play the external audio file (reverting to simpler method)
        PlayAudioPacketS2C packet = new PlayAudioPacketS2C(getBlockPos(), audioPath);
        PacketRegistries.sendToClients(packet); // Restore packet sending
    }

    public void stopAudio() {
        if (level == null || level.isClientSide) {
            return; // Should not execute on client
        }

        // Send packet to all clients to stop audio (reverting to simpler method)
        StopAudioPacketS2C packet = new StopAudioPacketS2C(getBlockPos());
        PacketRegistries.sendToClients(packet); // Use helper method again
    }

    // Removed redstone activation logic; UI buttons will now call playAudio or stop
    public void tick() {
        if (level == null || level.isClientSide) {
            return;
        }

        // Validate block state: if the block at this position is not a speaker, reset playback state.
        if (!level.getBlockState(worldPosition).is(BlockRegistries.SPEAKER.get())) {
            stopAudio();
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("AudioPath")) {
            audioPath = tag.getString("AudioPath");
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("AudioPath", audioPath);
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putString("AudioPath", audioPath);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        if (tag.contains("AudioPath")) {
            audioPath = tag.getString("AudioPath");
        }
    }
}
