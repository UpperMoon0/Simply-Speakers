package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerSettings;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.client.ClientSpeakerRegistry;
import com.nstut.simplyspeakers.speakers.ServerEmitter;
import com.nstut.simplyspeakers.speakers.ServerPlaybackManager;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import com.nstut.simplyspeakers.speakers.SpeakerLocation;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the Proxy Speaker block.
 */
@Getter
public class ProxySpeakerBlockEntity extends BlockEntity {

    private static final String NBT_SPEAKER_ID = "SpeakerID";
    private static final String NBT_PROXY_PLAYING = "ProxyPlaying";

    private String speakerId = "";
    private String registeredId = "";
    private boolean isProxyPlaying = false;
    private float maxVolume = 1.0f;
    private int maxRange = 16;
    private float audioDropoff = 1.0f;

    public ProxySpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistries.PROXY_SPEAKER.get(), pos, state);
        if (level != null && !level.isClientSide() && SpeakerLink.isLinkableId(speakerId)) {
            registeredId = speakerId.trim();
            ServerSpeakerRegistry.registerProxySpeaker(level, pos, registeredId);
        }
    }

    public void setProxyPlaying(boolean proxyPlaying) {
        this.isProxyPlaying = proxyPlaying;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            if (!level.isClientSide()) {
                updateEmitterSnapshot();
                if (!isProxyPlaying) {
                    stopAudio();
                } else {
                    startCentralScan();
                }
            }
        }
    }

    public void setMaxVolume(float maxVolume) {
        float val = Math.max(0.0f, Math.min(1.0f, maxVolume));
        if (level != null && !level.isClientSide()) {
            this.maxVolume = val;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            updateEmitterSnapshot();
        } else if (level != null) {
            this.maxVolume = val;
        }
    }

    public void setMaxVolumeClient(float maxVolume) {
        if (this.level != null && this.level.isClientSide()) {
            this.maxVolume = Math.max(0.0f, Math.min(1.0f, maxVolume));
        }
    }

    public void setMaxRange(int maxRange) {
        int val = Math.max(1, Math.min(Config.speakerRange, maxRange));
        if (level != null && !level.isClientSide()) {
            this.maxRange = val;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            updateEmitterSnapshot();
        } else if (level != null) {
            this.maxRange = val;
        }
    }

    public void setMaxRangeClient(int maxRange) {
        if (this.level != null && this.level.isClientSide()) {
            this.maxRange = Math.max(1, Math.min(Config.speakerRange, maxRange));
        }
    }

    public void setAudioDropoff(float audioDropoff) {
        float val = Math.max(0.0f, Math.min(1.0f, audioDropoff));
        if (level != null && !level.isClientSide()) {
            this.audioDropoff = val;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            updateEmitterSnapshot();
        } else if (level != null) {
            this.audioDropoff = val;
        }
    }

    public void setAudioDropoffClient(float audioDropoff) {
        if (this.level != null && this.level.isClientSide()) {
            this.audioDropoff = Math.max(0.0f, Math.min(1.0f, audioDropoff));
        }
    }

    public String getSpeakerId() {
        return speakerId;
    }

    public void setSpeakerId(String speakerId) {
        String newSpeakerId = speakerId == null ? "" : speakerId.trim();
        if (level != null && !level.isClientSide()) {
            String oldSpeakerId = this.speakerId;
            if (!oldSpeakerId.equals(newSpeakerId)) {
                stopAudio();
            }
            this.speakerId = newSpeakerId;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

            if (!oldSpeakerId.equals(newSpeakerId)) {
                if (SpeakerLink.isLinkableId(oldSpeakerId)) {
                    ServerSpeakerRegistry.unregisterProxySpeaker(level, worldPosition, oldSpeakerId);
                }
                if (SpeakerLink.isLinkableId(newSpeakerId)) {
                    ServerSpeakerRegistry.registerProxySpeaker(level, worldPosition, newSpeakerId);
                }
                registeredId = newSpeakerId;
                updateEmitterSnapshot();
            }
        } else if (level != null) {
            this.speakerId = newSpeakerId;
        }
    }

    public void setSpeakerIdClient(String speakerId) {
        this.speakerId = speakerId == null ? "" : speakerId.trim();
    }

    public SpeakerState getSpeakerState() {
        if (!SpeakerLink.isLinkableId(speakerId)) {
            return null;
        }
        String netKey = "net_" + speakerId.trim();
        if (level != null && !level.isClientSide()) {
            return ServerSpeakerRegistry.getOrCreateSpeakerState(level, netKey);
        } else if (level != null && level.isClientSide()) {
            return ClientSpeakerRegistry.getOrCreateState(netKey);
        }
        return null;
    }

    public void updateSpeakerState(SpeakerState state) {
        if (level != null && !level.isClientSide() && SpeakerLink.isLinkableId(speakerId)) {
            ServerSpeakerRegistry.updateSpeakerState(level, "net_" + speakerId.trim(), state);
        }
    }

    public void ensureServerRegistration() {
        if (level != null && !level.isClientSide() && SpeakerLink.isLinkableId(speakerId)) {
            String currentId = speakerId.trim();
            if (registeredId.isEmpty() || !registeredId.equals(currentId)) {
                if (!registeredId.isEmpty()) {
                    ServerSpeakerRegistry.unregisterProxySpeaker(level, worldPosition, registeredId);
                }
                ServerSpeakerRegistry.registerProxySpeaker(level, worldPosition, currentId);
                registeredId = currentId;
            }
            updateEmitterSnapshot();
        }
    }

    private SpeakerLocation emitterLocation() {
        return new SpeakerLocation(ServerSpeakerRegistry.getDimension(level), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }

    /**
     * Publishes this proxy speaker's emitter snapshot to the {@link ServerSpeakerRegistry}.
     * Proxy settings (range/volume/dropoff) live on the block entity, so the snapshot is
     * what lets the centralized {@link ServerPlaybackManager} keep managing listeners for
     * this proxy while its chunk is unloaded.
     */
    private void updateEmitterSnapshot() {
        if (level == null || level.isClientSide() || !SpeakerLink.isLinkableId(speakerId)) return;
        boolean powered = getBlockState().hasProperty(com.nstut.simplyspeakers.blocks.ProxySpeakerBlock.POWERED)
                && getBlockState().getValue(com.nstut.simplyspeakers.blocks.ProxySpeakerBlock.POWERED);
        ServerSpeakerRegistry.upsertEmitter(new ServerEmitter(
                emitterLocation(),
                "net_" + speakerId.trim(),
                maxRange,
                maxVolume,
                audioDropoff,
                true,
                isProxyPlaying && powered));
    }

    private void startCentralScan() {
        if (level instanceof ServerLevel serverLevel) {
            ServerEmitter emitter = ServerSpeakerRegistry.getEmitter(emitterLocation());
            if (emitter != null) {
                ServerPlaybackManager.onEmitterActivated(serverLevel.getServer(), emitter);
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ProxySpeakerBlockEntity blockEntity) {
        blockEntity.ensureServerRegistration();
        blockEntity.tick(level, pos, state);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide()) {
            stopAudio();
            ServerSpeakerRegistry.unregisterProxySpeaker(level, pos, speakerId);
        }
        super.preRemoveSideEffects(pos, state);
    }

    public void playAudio() {
        if (level == null || level.isClientSide()) return;
        updateEmitterSnapshot();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void stopAudio() {
        if (level == null || level.isClientSide()) return;
        updateEmitterSnapshot();
        if (level instanceof ServerLevel serverLevel) {
            ServerPlaybackManager.stopEmitter(serverLevel.getServer(), emitterLocation());
        }
    }

    private void tick(Level currentLevel, BlockPos currentPos, BlockState currentState) {
        if (currentLevel == null || currentLevel.isClientSide()) return;

        if (!currentState.is(com.nstut.simplyspeakers.blocks.BlockRegistries.PROXY_SPEAKER.get())) {
            stopAudio();
            return;
        }

        // Listener scanning is centralized in ServerPlaybackManager; the proxy block
        // entity only refreshes its emitter snapshot (playing/power intent and settings).
        updateEmitterSnapshot();
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);

        speakerId = tag.getStringOr(NBT_SPEAKER_ID, "");
        isProxyPlaying = tag.getBooleanOr(NBT_PROXY_PLAYING, false);

        SpeakerSettings settings = SpeakerSettings.read(tag::getFloatOr, tag::getIntOr, new SpeakerSettings(maxVolume, maxRange, audioDropoff));
        maxVolume = settings.maxVolume();
        maxRange = settings.maxRange();
        audioDropoff = settings.audioDropoff();

        if (level != null && !level.isClientSide()) {
            ensureServerRegistration();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);

        if (!speakerId.isEmpty()) {
            tag.putString(NBT_SPEAKER_ID, speakerId);
        }
        tag.putBoolean(NBT_PROXY_PLAYING, isProxyPlaying);
        new SpeakerSettings(maxVolume, maxRange, audioDropoff).write(tag::putFloat, tag::putInt);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        // BlockEntity#getUpdateTag is empty by default in 26.1.x. Serialize our
        // custom fields so a joining client receives the persisted settings.
        return saveCustomOnly(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
