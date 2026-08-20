package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerRegistry;
import com.nstut.simplyspeakers.SpeakerSettings;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.blocks.SpeakerBlock;
import com.nstut.simplyspeakers.client.ClientSpeakerRegistry;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.network.PlayAudioPacketS2C;
import com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C;
import com.nstut.simplyspeakers.network.StopAudioPacketS2C;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
    private static final String NBT_INTERNAL_ID = "InternalStateId";

    private final Set<UUID> listeningPlayers = new HashSet<>();
    private UUID internalStateId = UUID.randomUUID();
    private String speakerId = "";
    private String registeredKey = "";

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistries.SPEAKER.get(), pos, state);
        if (level != null && !level.isClientSide()) {
            registeredKey = getStateKey();
            ServerSpeakerRegistry.registerSpeaker(level, pos, registeredKey);
        }
    }

    public String getStateKey() {
        if (SpeakerLink.isLinkableId(speakerId)) {
            return "net_" + speakerId.trim();
        }
        return "internal_" + internalStateId.toString();
    }

    public String getSpeakerId() {
        return speakerId;
    }

    public void setSpeakerId(String speakerId) {
        String newSpeakerId = speakerId == null ? "" : speakerId.trim();
        if (level != null && !level.isClientSide()) {
            String oldKey = getStateKey();
            boolean resumePlayback = isPlaying();
            if (resumePlayback) {
                stopAudio();
            }
            this.speakerId = newSpeakerId;
            String newKey = getStateKey();

            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

            if (!oldKey.equals(newKey)) {
                SpeakerRegistry.updateSpeakerId(level, worldPosition, oldKey, newKey);
                registeredKey = newKey;
            }
            if (resumePlayback) {
                playAudio();
            }
        } else if (level != null) {
            this.speakerId = newSpeakerId;
        }
    }

    public void setSpeakerIdClient(String speakerId) {
        this.speakerId = speakerId == null ? "" : speakerId.trim();
    }

    public SpeakerState getSpeakerState() {
        String key = getStateKey();
        if (level != null && !level.isClientSide()) {
            return ServerSpeakerRegistry.getOrCreateSpeakerState(level, key);
        } else if (level != null && level.isClientSide()) {
            return ClientSpeakerRegistry.getOrCreateState(key);
        }
        return null;
    }

    public void updateSpeakerState(SpeakerState state) {
        if (level != null && !level.isClientSide()) {
            ServerSpeakerRegistry.updateSpeakerState(level, getStateKey(), state);
        }
    }

    public void setSelectedAudio(String audioId, String filename) {
        if (level != null && !level.isClientSide()) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setAudioId(audioId);
                state.setAudioFilename(filename);
                updateSpeakerState(state);
                if (state.isPlaying()) {
                    stopAudio();
                }
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpeakerBlockEntity blockEntity) {
        blockEntity.tick(level, pos, state);
    }

    public void playAudio() {
        if (level == null || level.isClientSide()) {
            return;
        }

        SpeakerState state = getSpeakerState();
        if (state == null || state.isPlaying()) {
            return;
        }
        if (state.getAudioId() == null || state.getAudioId().isEmpty()) {
            return;
        }

        state.setPlaying(true);
        state.setPlaybackStartTick(level.getGameTime());
        updateSpeakerState(state);
        listeningPlayers.clear();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

        notifyProxySpeakers("play");
    }

    public void stopAudio() {
        if (level == null || level.isClientSide()) {
            return;
        }

        SpeakerState state = getSpeakerState();
        if (state != null) {
            state.setPlaying(false);
            state.setPlaybackStartTick(-1);
            updateSpeakerState(state);
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

        if (level instanceof ServerLevel serverLevel) {
            StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(worldPosition);
            for (UUID playerId : new HashSet<>(listeningPlayers)) {
                ServerPlayer p = (ServerPlayer) serverLevel.getPlayerByUUID(playerId);
                if (p != null) {
                    PacketRegistries.CHANNEL.sendToPlayer(p, stopPacket);
                }
            }

            int range = state != null ? state.getMaxRange() : Config.speakerRange;
            double maxRangeSq = (double) range * range;
            Vec3 speakerCenterPos = Vec3.atCenterOf(worldPosition);
            for (ServerPlayer player : serverLevel.getPlayers(p -> p.position().distanceToSqr(speakerCenterPos) <= maxRangeSq)) {
                if (!listeningPlayers.contains(player.getUUID())) {
                    PacketRegistries.CHANNEL.sendToPlayer(player, stopPacket);
                }
            }
        }
        listeningPlayers.clear();

        notifyProxySpeakers("stop");
    }

    private void notifyProxySpeakers(String action) {
        if (level instanceof ServerLevel serverLevel && SpeakerLink.isLinkableId(speakerId)) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                SpeakerStateUpdatePacketS2C updatePacket = new SpeakerStateUpdatePacketS2C(
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

    private void notifyClientsOfStateChange() {
        if (level instanceof ServerLevel serverLevel && SpeakerLink.isLinkableId(speakerId)) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                SpeakerStateUpdatePacketS2C updatePacket = new SpeakerStateUpdatePacketS2C(
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

    private void tick(Level currentLevel, BlockPos currentPos, BlockState currentState) {
        if (currentLevel == null || currentLevel.isClientSide()) {
            return;
        }

        if (!currentState.is(com.nstut.simplyspeakers.blocks.BlockRegistries.SPEAKER.get())) {
            SpeakerState state = getSpeakerState();
            if (state != null && state.isPlaying()) stopAudio();
            return;
        }

        boolean isPowered = currentState.getValue(SpeakerBlock.POWERED);
        if (!isPowered) {
            if (!listeningPlayers.isEmpty()) {
                if (currentLevel instanceof ServerLevel serverLevel) {
                    for (UUID playerId : listeningPlayers) {
                        ServerPlayer serverPlayer = (ServerPlayer) serverLevel.getPlayerByUUID(playerId);
                        if (serverPlayer != null) {
                            StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(currentPos);
                            PacketRegistries.CHANNEL.sendToPlayer(serverPlayer, stopPacket);
                        }
                    }
                }
                listeningPlayers.clear();
            }

            SpeakerState state = getSpeakerState();
            if (state != null && state.isPlaying()) {
                state.setPlaying(false);
                state.setPlaybackStartTick(-1);
                updateSpeakerState(state);
                setChanged();
                currentLevel.sendBlockUpdated(currentPos, currentState, currentState, 3);
            }
            return;
        }

        SpeakerState state = getSpeakerState();
        if (state == null) return;

        if (!state.isPlaying()) {
            if (!listeningPlayers.isEmpty()) {
                listeningPlayers.clear();
            }
            return;
        }

        // Natural EOF check for non-looping audio
        if (!state.isLooping() && state.getPlaybackStartTick() > 0) {
            float elapsedSeconds = (currentLevel.getGameTime() - state.getPlaybackStartTick()) / 20.0f;
            AudioFileManager audioFileManager = SimplySpeakers.getAudioFileManager();
            if (audioFileManager != null) {
                AudioFileMetadata meta = audioFileManager.getManifest().get(state.getAudioId());
                if (meta != null && meta.getDurationSeconds() > 0.0f && elapsedSeconds >= meta.getDurationSeconds()) {
                    stopAudio();
                    return;
                }
            }
        }

        if (!(currentLevel instanceof ServerLevel serverLevel)) {
            return;
        }

        // Rate-limit listener scanning across ticks
        long gameTime = currentLevel.getGameTime();
        if ((gameTime + currentPos.hashCode()) % 4 != 0 && !listeningPlayers.isEmpty()) {
            return;
        }

        double maxRangeSq = (double) state.getMaxRange() * state.getMaxRange();
        Vec3 speakerCenterPos = Vec3.atCenterOf(currentPos);
        Set<UUID> playersInRange = new HashSet<>();

        for (ServerPlayer player : serverLevel.getPlayers(p -> p.position().distanceToSqr(speakerCenterPos) <= maxRangeSq)) {
            playersInRange.add(player.getUUID());

            if (!listeningPlayers.contains(player.getUUID())) {
                float playbackPositionSeconds = state.getPlaybackPositionSeconds(currentLevel.getGameTime());
                if (playbackPositionSeconds < 0) playbackPositionSeconds = 0;

                PlayAudioPacketS2C playPacket = new PlayAudioPacketS2C(
                        currentPos,
                        this.speakerId,
                        state.getAudioId(),
                        state.getAudioFilename(),
                        playbackPositionSeconds,
                        state.isLooping(),
                        state.getMaxRange(),
                        state.getMaxVolume(),
                        state.getAudioDropoff()
                );
                PacketRegistries.CHANNEL.sendToPlayer(player, playPacket);
                listeningPlayers.add(player.getUUID());
            }
        }

        if (!listeningPlayers.isEmpty()) {
            Set<UUID> playersToStop = new HashSet<>(listeningPlayers);
            playersToStop.removeAll(playersInRange);

            for (UUID playerId : playersToStop) {
                ServerPlayer serverPlayerInstance = (ServerPlayer) serverLevel.getPlayerByUUID(playerId);
                if (serverPlayerInstance != null) {
                    StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(currentPos);
                    PacketRegistries.CHANNEL.sendToPlayer(serverPlayerInstance, stopPacket);
                }
                listeningPlayers.remove(playerId);
            }
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide()) {
            com.nstut.simplyspeakers.client.ClientAudioPlayer.stop(worldPosition);
            ClientSpeakerRegistry.unregisterSpeaker(worldPosition, getStateKey());
        }
        super.setRemoved();
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);

        if (tag.hasUUID(NBT_INTERNAL_ID)) {
            internalStateId = tag.getUUID(NBT_INTERNAL_ID);
        } else if (tag.contains(NBT_INTERNAL_ID)) {
            try {
                internalStateId = UUID.fromString(tag.getString(NBT_INTERNAL_ID));
            } catch (Exception e) {
                internalStateId = UUID.randomUUID();
            }
        } else {
            internalStateId = UUID.randomUUID();
        }

        speakerId = tag.contains(NBT_SPEAKER_ID) ? tag.getString(NBT_SPEAKER_ID) : "";

        if (level != null && !level.isClientSide()) {
            SpeakerState persistedState = ServerSpeakerRegistry.getOrCreateSpeakerState(level, getStateKey());
            SpeakerSettings.read(
                    (key, fallback) -> tag.contains(key) ? tag.getFloat(key) : fallback,
                    (key, fallback) -> tag.contains(key) ? tag.getInt(key) : fallback,
                    SpeakerSettings.from(persistedState)).applyTo(persistedState);

            String currentKey = getStateKey();
            if (!registeredKey.isEmpty() && !registeredKey.equals(currentKey)) {
                ServerSpeakerRegistry.updateSpeakerKey(level, worldPosition, registeredKey, currentKey);
            } else {
                ServerSpeakerRegistry.registerSpeaker(level, worldPosition, currentKey);
            }
            registeredKey = currentKey;

            SpeakerState state = ServerSpeakerRegistry.getSpeakerState(level, currentKey);
            if (state != null && state.isPlaying()) {
                notifyProxySpeakers("play");
            }
        }

        listeningPlayers.clear();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putUUID(NBT_INTERNAL_ID, internalStateId);

        if (!speakerId.isEmpty()) {
            tag.putString(NBT_SPEAKER_ID, speakerId);
        }

        SpeakerState persistedState = getSpeakerState();
        if (persistedState != null) {
            SpeakerSettings.from(persistedState).write(tag::putFloat, tag::putInt);
        }
    }

    public void setLooping(boolean looping) {
        if (level != null && !level.isClientSide()) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setLooping(looping);
                updateSpeakerState(state);
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                notifyClientsOfStateChange();
            }
        }
    }

    public void setAudio(String audioId, String filename) {
        if (level != null && !level.isClientSide()) {
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

    public void setAudioId(String audioId) {
        setAudio(audioId, "");
    }

    public boolean isLooping() {
        SpeakerState state = getSpeakerState();
        return state != null && state.isLooping();
    }

    public boolean isPlaying() {
        SpeakerState state = getSpeakerState();
        return state != null && state.isPlaying();
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

    public void setLoopingClient(boolean looping) {
        if (this.level != null && this.level.isClientSide()) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setLooping(looping);
            }
        }
    }

    public void setAudioIdClient(String audioId, String filename) {
        if (this.level != null && this.level.isClientSide()) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setAudioId(audioId);
                state.setAudioFilename(filename);
            }
        }
    }

    public void setMaxVolumeClient(float maxVolume) {
        if (this.level != null && this.level.isClientSide()) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setMaxVolume(Math.max(0.0f, Math.min(1.0f, maxVolume)));
            }
        }
    }

    public void setMaxRangeClient(int maxRange) {
        if (this.level != null && this.level.isClientSide()) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setMaxRange(Math.max(1, Math.min(Config.speakerRange, maxRange)));
            }
        }
    }

    public void setAudioDropoffClient(float audioDropoff) {
        if (this.level != null && this.level.isClientSide()) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setAudioDropoff(Math.max(0.0f, Math.min(1.0f, audioDropoff)));
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

    public void setMaxVolume(float maxVolume) {
        if (level != null && !level.isClientSide()) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setMaxVolume(maxVolume);
                updateSpeakerState(state);
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public void setMaxRange(int maxRange) {
        if (level != null && !level.isClientSide()) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setMaxRange(maxRange);
                updateSpeakerState(state);
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public void setAudioDropoff(float audioDropoff) {
        if (level != null && !level.isClientSide()) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                state.setAudioDropoff(audioDropoff);
                updateSpeakerState(state);
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public float getMaxVolume() {
        SpeakerState state = getSpeakerState();
        return state != null ? state.getMaxVolume() : 1.0f;
    }

    public int getMaxRange() {
        SpeakerState state = getSpeakerState();
        return state != null ? state.getMaxRange() : 16;
    }

    public float getAudioDropoff() {
        SpeakerState state = getSpeakerState();
        return state != null ? state.getAudioDropoff() : 1.0f;
    }
}
