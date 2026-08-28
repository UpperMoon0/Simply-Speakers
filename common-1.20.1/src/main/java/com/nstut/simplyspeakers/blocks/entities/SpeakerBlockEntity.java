package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerRegistry;
import com.nstut.simplyspeakers.SpeakerSettings;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.blocks.SpeakerBlock;
import com.nstut.simplyspeakers.client.ClientSpeakerRegistry;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C;
import com.nstut.simplyspeakers.speakers.ServerEmitter;
import com.nstut.simplyspeakers.speakers.ServerPlaybackManager;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import com.nstut.simplyspeakers.speakers.SpeakerLocation;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Block entity for the Speaker block.
 */
@Getter
public class SpeakerBlockEntity extends BlockEntity {

    private static final String NBT_SPEAKER_ID = "SpeakerID";
    private static final String NBT_INTERNAL_ID = "InternalStateId";

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

    public void ensureServerRegistration() {
        if (level != null && !level.isClientSide()) {
            String currentKey = getStateKey();
            if (registeredKey.isEmpty() || !registeredKey.equals(currentKey)) {
                if (!registeredKey.isEmpty()) {
                    ServerSpeakerRegistry.updateSpeakerKey(level, worldPosition, registeredKey, currentKey);
                } else {
                    ServerSpeakerRegistry.registerSpeaker(level, worldPosition, currentKey);
                }
                registeredKey = currentKey;
            }
            updateEmitterSnapshot();
        }
    }

    private SpeakerLocation emitterLocation() {
        return new SpeakerLocation(ServerSpeakerRegistry.getDimension(level), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }

    /**
     * Publishes this speaker's emitter snapshot to the {@link ServerSpeakerRegistry}.
     * The snapshot carries the last known powered/playing intent so the centralized
     * {@link ServerPlaybackManager} keeps managing playback even if this chunk unloads.
     */
    private void updateEmitterSnapshot() {
        if (level == null || level.isClientSide()) return;
        boolean active = getBlockState().hasProperty(SpeakerBlock.POWERED)
                && getBlockState().getValue(SpeakerBlock.POWERED);
        SpeakerState state = ServerSpeakerRegistry.getSpeakerState(level, getStateKey());
        ServerSpeakerRegistry.upsertEmitter(new ServerEmitter(
                emitterLocation(),
                getStateKey(),
                state != null ? state.getMaxRange() : 16,
                state != null ? state.getMaxVolume() : 1.0f,
                state != null ? state.getAudioDropoff() : 1.0f,
                false,
                active));
    }

    private void startCentralScan() {
        if (level instanceof ServerLevel serverLevel) {
            ServerEmitter emitter = ServerSpeakerRegistry.getEmitter(emitterLocation());
            if (emitter != null) {
                ServerPlaybackManager.onEmitterActivated(serverLevel.getServer(), emitter);
            }
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
            boolean physicallyPowered = getBlockState().hasProperty(SpeakerBlock.POWERED)
                    && getBlockState().getValue(SpeakerBlock.POWERED);
            String prospectiveKey = SpeakerLink.isLinkableId(newSpeakerId) ? "net_" + newSpeakerId : "internal_" + internalStateId;
            SpeakerState destinationBeforeRelink = ServerSpeakerRegistry.getSpeakerState(level, prospectiveKey);
            if (!oldKey.equals(prospectiveKey)) detachEmitterForPowerOff();
            this.speakerId = newSpeakerId;
            String newKey = getStateKey();

            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

            if (!oldKey.equals(newKey)) {
                SpeakerRegistry.updateSpeakerId(level, worldPosition, oldKey, newKey);
                registeredKey = newKey;
                if (!physicallyPowered && (destinationBeforeRelink == null || !destinationBeforeRelink.isPlaying())) {
                    SpeakerState newState = getSpeakerState();
                    newState.setPlaying(false);
                    newState.setPlaybackStartTick(-1);
                    updateSpeakerState(newState);
                    notifyClientsOfStateChange();
                }
            }
            if (physicallyPowered && !oldKey.equals(newKey)) {
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
                notifyClientsOfStateChange();
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpeakerBlockEntity blockEntity) {
        blockEntity.ensureServerRegistration();
        blockEntity.tick(level, pos, state);
    }

    public void playAudio() {
        if (level == null || level.isClientSide()) {
            return;
        }

        SpeakerState state = getSpeakerState();
        if (state == null) {
            return;
        }
        if (state.isPlaying()) {
            ServerSpeakerRegistry.setSpeakerPowered(level, worldPosition, getStateKey(), true);
            updateEmitterSnapshot();
            startCentralScan();
            return;
        }

        String audioId = state.getAudioId();
        if (audioId == null || audioId.isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        state.setPlaying(true);
        state.setPlaybackStartTick(gameTime);
        updateSpeakerState(state);

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        notifyClientsOfStateChange();
        ServerSpeakerRegistry.setSpeakerPowered(level, worldPosition, getStateKey(), true);
        updateEmitterSnapshot();
        startCentralScan();
    }

    public void stopAudio() {
        if (level == null || level.isClientSide()) {
            return;
        }

        ServerSpeakerRegistry.setSpeakerPowered(level, worldPosition, getStateKey(), false);
        SpeakerState state = getSpeakerState();
        if (state != null) {
            state.setPlaying(false);
            state.setPlaybackStartTick(-1);
            updateSpeakerState(state);
        }

        updateEmitterSnapshot();
        if (level instanceof ServerLevel serverLevel) {
            ServerPlaybackManager.stopEmitter(serverLevel.getServer(), emitterLocation());
        }

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        notifyClientsOfStateChange();
    }

    public void detachEmitterForPowerOff() {
        if (level == null || level.isClientSide()) return;
        ServerSpeakerRegistry.setSpeakerPowered(level, worldPosition, getStateKey(), false);
        updateEmitterSnapshot();
        if (!ServerSpeakerRegistry.hasOtherPoweredMain(level, worldPosition, getStateKey())) {
            stopAudio();
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            ServerPlaybackManager.stopEmitter(serverLevel.getServer(), emitterLocation());
        }
        notifyClientsOfStateChange();
    }

    private void notifyClientsOfStateChange(ServerPlayer player) {
        if (level instanceof ServerLevel) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                SpeakerStateUpdatePacketS2C updatePacket = new SpeakerStateUpdatePacketS2C(
                        worldPosition,
                        speakerId,
                        state.isPlaying() ? "play" : "stop",
                        state.getAudioId(),
                        state.getAudioFilename(),
                        state.getPlaybackStartTick(),
                        state.isLooping()
                );
                sendStateUpdatePacket(player, updatePacket);
            }
        }
    }

    private void notifyClientsOfStateChange() {
        if (level instanceof ServerLevel serverLevel) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                SpeakerStateUpdatePacketS2C updatePacket = new SpeakerStateUpdatePacketS2C(
                        worldPosition,
                        speakerId,
                        state.isPlaying() ? "play" : "stop",
                        state.getAudioId(),
                        state.getAudioFilename(),
                        state.getPlaybackStartTick(),
                        state.isLooping()
                );
                sendStateUpdatePacketToAll(serverLevel, updatePacket);
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

        // Listener scanning and natural EOF handling are centralized in
        // ServerPlaybackManager; the block entity only keeps its emitter
        // snapshot (power/playing intent) up to date.
        boolean isPowered = currentState.getValue(SpeakerBlock.POWERED);
        ServerSpeakerRegistry.setSpeakerPowered(currentLevel, currentPos, getStateKey(), isPowered);
        updateEmitterSnapshot();
    }

    private void sendStateUpdatePacket(ServerPlayer player, SpeakerStateUpdatePacketS2C packet) {
        PacketRegistries.CHANNEL.sendToPlayer(player, packet);
    }

    private void sendStateUpdatePacketToAll(ServerLevel serverLevel, SpeakerStateUpdatePacketS2C packet) {
        PacketRegistries.CHANNEL.sendToPlayers(serverLevel.players(), packet);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);

        boolean migratedInternalId = !tag.contains(NBT_INTERNAL_ID);
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
            setChanged();
        }

        speakerId = tag.contains(NBT_SPEAKER_ID) ? tag.getString(NBT_SPEAKER_ID) : "";

        if (level != null && !level.isClientSide()) {
            if (migratedInternalId) ServerSpeakerRegistry.applyLegacyStandaloneTemplate(level, getStateKey());
            SpeakerState persistedState = ServerSpeakerRegistry.getOrCreateSpeakerState(level, getStateKey());
            SpeakerSettings.read(
                    (key, fallback) -> tag.contains(key) ? tag.getFloat(key) : fallback,
                    (key, fallback) -> tag.contains(key) ? tag.getInt(key) : fallback,
                    SpeakerSettings.from(persistedState)).applyTo(persistedState);

            ensureServerRegistration();

            SpeakerState state = ServerSpeakerRegistry.getSpeakerState(level, getStateKey());
            if (state != null && state.isPlaying()) {
                notifyClientsOfStateChange();
            }
        } else {
            SpeakerState clientState = ClientSpeakerRegistry.getOrCreateState(getStateKey());
            SpeakerSettings.read(
                    (key, fallback) -> tag.contains(key) ? tag.getFloat(key) : fallback,
                    (key, fallback) -> tag.contains(key) ? tag.getInt(key) : fallback,
                    SpeakerSettings.from(clientState)).applyTo(clientState);
            if (tag.contains("AudioId")) {
                clientState.setAudioId(tag.getString("AudioId"));
                clientState.setAudioFilename(tag.getString("AudioFilename"));
                clientState.setPlaying(tag.getBoolean("IsPlaying"));
                clientState.setLooping(tag.getBoolean("IsLooping"));
                clientState.setPlaybackStartTick(tag.getLong("PlaybackStartTick"));
            }
        }
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
                notifyClientsOfStateChange();
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
        SpeakerState persistedState = getSpeakerState();
        if (persistedState != null) {
            if (persistedState.getAudioId() != null) {
                tag.putString("AudioId", persistedState.getAudioId());
            }
            if (persistedState.getAudioFilename() != null) {
                tag.putString("AudioFilename", persistedState.getAudioFilename());
            }
            tag.putBoolean("IsPlaying", persistedState.isPlaying());
            tag.putBoolean("IsLooping", persistedState.isLooping());
            tag.putLong("PlaybackStartTick", persistedState.getPlaybackStartTick());
        }
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
                updateEmitterSnapshot();
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
                updateEmitterSnapshot();
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
                updateEmitterSnapshot();
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
