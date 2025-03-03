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

import java.io.File;
import java.util.logging.Logger;

@Getter
public class SpeakerBlockEntity extends BlockEntity {

    private static final Logger LOGGER = Logger.getLogger(SpeakerBlockEntity.class.getName());

    private String audioPath = "";

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistries.SPEAKER.get(), pos, state);
    }

    public void setAudioPath(String audioPath) {
        if (level != null && !level.isClientSide) {
            this.audioPath = audioPath;
            SpeakerBlockEntityPacketS2C packet = new SpeakerBlockEntityPacketS2C(getBlockPos(), audioPath);
            PacketRegistries.sendToClients(packet);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            StopAudioPacketS2C packet = new StopAudioPacketS2C(getBlockPos());
            PacketRegistries.sendToClients(packet);
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

        // Send a packet to trigger the client to play the external audio file.
        PlayAudioPacketS2C packet = new PlayAudioPacketS2C(getBlockPos(), audioPath);
        PacketRegistries.sendToClients(packet);
    }

    public void stopAudio() {
        if (level == null || level.isClientSide) {
            return;
        }

        StopAudioPacketS2C packet = new StopAudioPacketS2C(getBlockPos());
        PacketRegistries.sendToClients(packet);
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