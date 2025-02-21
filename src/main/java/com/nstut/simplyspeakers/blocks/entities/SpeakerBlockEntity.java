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

public class SpeakerBlockEntity extends BlockEntity {

    private static final Logger LOGGER = Logger.getLogger(SpeakerBlockEntity.class.getName());

    @Getter
    private String audioPath = "";
    private boolean isPlaying = false;

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
        if (audioPath == null || audioPath.isEmpty()) {
            LOGGER.warning("Audio path is empty.");
            return;
        }
        File file = new File(audioPath);
        if (!file.exists()) {
            LOGGER.warning("Audio file not found: " + audioPath);
            return;
        }
        // Send a packet to trigger the client to play the external audio file.
        PlayAudioPacketS2C packet = new PlayAudioPacketS2C(getBlockPos(), audioPath);
        PacketRegistries.sendToClients(packet);
        LOGGER.info("Sent play packet for file: " + audioPath);
    }

    public void tick() {
        if (level == null || level.isClientSide) return;

        boolean isPowered = level.hasNeighborSignal(worldPosition);

        if (isPowered && !isPlaying) {
            playAudio();
            isPlaying = true;
        } else if (!isPowered && isPlaying) {
            StopAudioPacketS2C packet = new StopAudioPacketS2C(getBlockPos());
            PacketRegistries.sendToClients(packet);
            isPlaying = false;
        }

        if (!level.getBlockState(worldPosition).is(BlockRegistries.SPEAKER.get())) {
            StopAudioPacketS2C packet = new StopAudioPacketS2C(getBlockPos());
            PacketRegistries.sendToClients(packet);
            isPlaying = false;
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