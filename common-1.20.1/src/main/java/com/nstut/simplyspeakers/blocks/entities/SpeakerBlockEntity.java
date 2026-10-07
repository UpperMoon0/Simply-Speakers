package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.RedstoneLogic;
import com.nstut.simplyspeakers.RedstoneMode;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerAccess;
import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerSettings;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.audio.DirectionalAudio;
import com.nstut.simplyspeakers.blocks.SpeakerBlock;
import com.nstut.simplyspeakers.client.ClientSpeakerRegistry;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C;
import com.nstut.simplyspeakers.speakers.ServerEmitter;
import com.nstut.simplyspeakers.speakers.ServerPlaybackManager;
import com.nstut.simplyspeakers.speakers.ServerSpeakerControlService;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import com.nstut.simplyspeakers.speakers.SpeakerLocation;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import java.util.List;
import java.util.UUID;
import com.nstut.simplyspeakers.playlist.Playlist;
import com.nstut.simplyspeakers.network.PlaylistSyncPacketS2C;

/**
 * Block entity for the Speaker block.
 */
@Getter
public class SpeakerBlockEntity extends BlockEntity {

    private static final String NBT_SPEAKER_ID = "SpeakerID";
    private static final String NBT_INTERNAL_ID = "InternalStateId";
    private static final String NBT_LAST_REDSTONE_SIGNAL = "LastRedstoneSignal";

    private UUID internalStateId = UUID.randomUUID();
    private String speakerId = "";
    private String registeredKey = "";

    /** Last observed redstone strength for edge-triggered modes. Persisted so a world
     * reload does not fabricate a rising edge (0 -> current signal). */
    private int lastRedstoneSignal = 0;

    private static final int COMPARATOR_UPDATE_INTERVAL_TICKS = 10;

    /** Last comparator level pushed to neighbours; recalculated on a periodic cadence. */
    private int lastComparatorLevel = 0;
    private long lastComparatorCheckTick = -COMPARATOR_UPDATE_INTERVAL_TICKS;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        this(BlockEntityRegistries.SPEAKER.get(), pos, state);
    }
    SpeakerBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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
    public void updateEmitterSnapshot() {
        if (level == null || level.isClientSide()) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerState(level, getStateKey());
        Direction facing = getBlockState().hasProperty(SpeakerBlock.FACING)
                ? getBlockState().getValue(SpeakerBlock.FACING)
                : Direction.NORTH;

        boolean active = state != null && state.isPlaying() && !state.isPaused();
        DirectionalAudio.Extras extras = state != null && state.getDirectionality() > 0.001f
                ? new DirectionalAudio.Extras(state.getDirectionality(), state.getConeAngleDegrees(), state.getRearAttenuation(), (byte) facing.ordinal())
                : null;
        ServerSpeakerRegistry.upsertEmitter(new ServerEmitter(
                emitterLocation(),
                getStateKey(),
                state != null ? state.getMaxRange() : 16,
                state != null ? state.getMaxVolume() : 1.0f,
                state != null ? state.getAudioDropoff() : 1.0f,
                false,
                active,
                extras));
    }

    public String getStateKey() {
        if (SpeakerLink.isLinkableId(speakerId)) {
            return "net_" + speakerId.trim();
        }
        return "internal_" + internalStateId.toString();
    }

    public String getFullStateKey() {
        return ServerSpeakerRegistry.getRegistryKey(level, getStateKey());
    }

    public String getSpeakerId() {
        return speakerId;
    }

    public void setSpeakerId(String speakerId) {
        String newSpeakerId = speakerId == null ? "" : speakerId.trim();
        if (level != null && !level.isClientSide()) {
            // ID assignment can precede the first block tick on a fresh placement.
            ensureServerRegistration();
            getSpeakerState();
            String oldKey = getStateKey();
            String prospectiveKey = SpeakerLink.isLinkableId(newSpeakerId) ? "net_" + newSpeakerId : "internal_" + internalStateId;
            if (!oldKey.equals(prospectiveKey)) detachEmitterForPowerOff();
            this.speakerId = newSpeakerId;
            String newKey = getStateKey();

            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

            if (!oldKey.equals(newKey)) {
                ServerSpeakerRegistry.updateSpeakerId(level, worldPosition, oldKey, newKey);
                registeredKey = newKey;
            }
        } else if (level != null) {
            this.speakerId = newSpeakerId;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
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
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.selectAudio(serverLevel.getServer(), serverLevel, getFullStateKey(), audioId, filename);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpeakerBlockEntity blockEntity) {
        blockEntity.ensureServerRegistration();
        blockEntity.updateComparatorOutput();
    }

    // ==================================================================
    // 0.8.x transport, playlists, redstone automation, and policy
    // ==================================================================

    public void transportAction(Level currentLevel, byte action, float seekSeconds) {
        if (currentLevel instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.applyTransport(serverLevel.getServer(), serverLevel, getFullStateKey(), action, seekSeconds);
            setChanged();
            currentLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void pauseAudio() {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.pause(serverLevel.getServer(), serverLevel, getFullStateKey());
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        updateComparatorOutput();
    }

    public void resumeAudio() {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.play(serverLevel.getServer(), serverLevel, getFullStateKey());
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void togglePause() {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.togglePause(serverLevel.getServer(), serverLevel, getFullStateKey());
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void seekTo(float seconds) {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.seek(serverLevel.getServer(), serverLevel, getFullStateKey(), seconds);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void nextTrack() {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.next(serverLevel.getServer(), serverLevel, getFullStateKey());
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void previousTrack() {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.previous(serverLevel.getServer(), serverLevel, getFullStateKey());
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void selectAndPlay(String audioId, String filename) {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.selectAudio(serverLevel.getServer(), serverLevel, getFullStateKey(), audioId, filename);
            ServerSpeakerControlService.play(serverLevel.getServer(), serverLevel, getFullStateKey());
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void playlistControl(Level currentLevel, byte op, int index, boolean flagValue,
                                String audioId, String filename) {
        playlistControl(currentLevel,op,index,flagValue,audioId,filename,"");
    }
    public boolean playlistControl(Level currentLevel, byte op, int index, boolean flagValue,
                                String audioId,String filename,String playlistId) {
        if (currentLevel instanceof ServerLevel serverLevel) {
            boolean changed=ServerSpeakerControlService.playlistControl(serverLevel.getServer(), serverLevel, getFullStateKey(), op, index, flagValue, audioId, filename,playlistId);
            if(!changed)return false;
            setChanged();
            currentLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            return true;
        }
        return false;
    }

    /** Compatibility entry point: speakers no longer consume redstone input. */
    public void handleRedstoneChange(int newSignal) { }

    public int getComparatorOutput() {
        SpeakerState state = getSpeakerState();
        if (state == null || level == null || !state.isPlaying() || state.isPaused()) return 0;
        AudioFileManager audioFileManager = SimplySpeakers.getAudioFileManager();
        float duration = 0.0f;
        if (audioFileManager != null) {
            AudioFileMetadata meta = audioFileManager.getManifest().get(state.getAudioId());
            if (meta != null) duration = meta.getDurationSeconds();
        }
        float elapsed = state.getPlaybackPositionSeconds(level.getGameTime());
        return RedstoneLogic.comparatorLevel(state.isPlaying() && !state.isPaused(), elapsed, duration);
    }

    private void updateComparatorOutput() {
        if (level == null || level.isClientSide()) return;
        long now = level.getGameTime();
        if (now - lastComparatorCheckTick < COMPARATOR_UPDATE_INTERVAL_TICKS) return;
        lastComparatorCheckTick = now;
        int computed = getComparatorOutput();
        if (computed != lastComparatorLevel) {
            lastComparatorLevel = computed;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            setChanged();
        }
    }

    public String getNetworkName() {
        SpeakerState state = getSpeakerState();
        return state != null && state.getNetworkName() != null ? state.getNetworkName() : "";
    }

    public RedstoneMode getRedstoneMode() {
        SpeakerState state = getSpeakerState();
        return state != null && state.getRedstoneMode() != null ? state.getRedstoneMode() : RedstoneMode.DEFAULT;
    }

    public void setNetworkName(String networkName) {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.policyControl(serverLevel.getServer(), serverLevel, getFullStateKey(),
                    com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_NETWORK_NAME,
                    networkName, 0, 0.0f, null);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setRedstoneMode(RedstoneMode mode) {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.policyControl(serverLevel.getServer(), serverLevel, getFullStateKey(),
                    com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_REDSTONE_MODE,
                    "", mode != null ? mode.ordinal() : 0, 0.0f, null);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setAccessMode(SpeakerAccess access) {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.policyControl(serverLevel.getServer(), serverLevel, getFullStateKey(),
                    com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_ACCESS_MODE,
                    "", access != null ? access.ordinal() : 0, 0.0f, null);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void modifyTrust(UUID playerUuid, boolean add) {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.policyControl(serverLevel.getServer(), serverLevel, getFullStateKey(),
                    com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_TRUST_CHANGE,
                    "", add ? 1 : 0, 0.0f, playerUuid);
        }
    }

    public void sendPlaylistSync(ServerPlayer player) {
        if (player == null || level == null || level.isClientSide()) return;
        SpeakerState state = getSpeakerState();
        if (state == null) return;
        PlaylistSyncPacketS2C sync = PlaylistSyncPacketS2C.fromState(worldPosition, getFullStateKey(), state, level.getGameTime(), com.nstut.simplyspeakers.SimplySpeakers.getAudioFileManager());
        PlaylistSyncPacketS2C.sendToPlayer(player,sync);
    }

    public void claimOwnership(UUID playerUuid) {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.policyControl(serverLevel.getServer(), serverLevel, getFullStateKey(),
                    com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_CLAIM_OWNER,
                    "", 0, 0.0f, playerUuid);
        }
    }

    public void setDirectionality(float directionality) {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.policyControl(serverLevel.getServer(), serverLevel, getFullStateKey(),
                    com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_DIRECTIONALITY,
                    "", 0, directionality, null);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            updateEmitterSnapshot();
        }
    }

    public void setConeAngleDegrees(int coneAngleDegrees) {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.policyControl(serverLevel.getServer(), serverLevel, getFullStateKey(),
                    com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_CONE_ANGLE,
                    "", coneAngleDegrees, 0.0f, null);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            updateEmitterSnapshot();
        }
    }

    public void setRearAttenuation(float rearAttenuation) {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.policyControl(serverLevel.getServer(), serverLevel, getFullStateKey(),
                    com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_REAR_ATTENUATION,
                    "", 0, rearAttenuation, null);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            updateEmitterSnapshot();
        }
    }

    public void playAudio() {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerRegistry.setSpeakerPowered(level, worldPosition, getStateKey(), true);
            ServerSpeakerControlService.play(serverLevel.getServer(), serverLevel, getFullStateKey());
            updateEmitterSnapshot();
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void stopAudio() {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerRegistry.setSpeakerPowered(level, worldPosition, getStateKey(), false);
            ServerSpeakerControlService.stop(serverLevel.getServer(), serverLevel, getFullStateKey());
            updateEmitterSnapshot();
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        updateComparatorOutput();
    }

    public void detachEmitterForPowerOff() {
        if (level == null || level.isClientSide()) return;
        if (level instanceof ServerLevel serverLevel) {
            ServerPlaybackManager.stopEmitter(serverLevel.getServer(), emitterLocation());
        }
        ServerSpeakerRegistry.removeEmitter(emitterLocation());
    }

    private void notifyClientsOfStateChange() {
        if (level instanceof ServerLevel serverLevel) {
            SpeakerState state = getSpeakerState();
            if (state != null) {
                String action = (state.isPlaying() && !state.isPaused()) ? "play" : (state.isPaused() ? "pause" : "stop");
                SpeakerStateUpdatePacketS2C updatePacket = new SpeakerStateUpdatePacketS2C(
                        worldPosition,
                        speakerId,
                        action,
                        state.getAudioId(),
                        state.getAudioFilename(),
                        state.getPlaybackStartTick(),
                        state.isLooping(),
                        getFullStateKey()
                );
                PacketRegistries.CHANNEL.sendToPlayers(serverLevel.players(), updatePacket);
            }
        }
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
        lastRedstoneSignal = tag.contains(NBT_LAST_REDSTONE_SIGNAL) ? tag.getInt(NBT_LAST_REDSTONE_SIGNAL) : 0;

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
            if(tag.contains("NetworkName"))clientState.setNetworkName(tag.getString("NetworkName"));
            if(tag.contains("Directionality"))clientState.setDirectionality(tag.getFloat("Directionality"));
            if(tag.contains("ConeAngleDegrees"))clientState.setConeAngleDegrees(tag.getInt("ConeAngleDegrees"));
            if(tag.contains("RearAttenuation"))clientState.setRearAttenuation(tag.getFloat("RearAttenuation"));
            SpeakerSettings.read(
                    (key, fallback) -> tag.contains(key) ? tag.getFloat(key) : fallback,
                    (key, fallback) -> tag.contains(key) ? tag.getInt(key) : fallback,
                    SpeakerSettings.from(clientState)).applyTo(clientState);
            if (tag.contains("AudioId")) {
                clientState.setAudioId(tag.getString("AudioId"));
                clientState.setAudioFilename(tag.getString("AudioFilename"));
                clientState.setPlaying(tag.getBoolean("IsPlaying"));
                clientState.getPlaylist().setRepeatMode(com.nstut.simplyspeakers.playlist.RepeatMode.fromIndex(tag.contains("RepeatMode")?tag.getInt("RepeatMode"):tag.getBoolean("IsLooping")?1:0));
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
        tag.putInt(NBT_LAST_REDSTONE_SIGNAL, lastRedstoneSignal);

        SpeakerState persistedState = getSpeakerState();
        if (persistedState != null) {
            SpeakerSettings.from(persistedState).write(tag::putFloat, tag::putInt);
        }
    }

    public void setLooping(boolean looping) {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.setLooping(serverLevel.getServer(), serverLevel, getFullStateKey(), looping);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setAudio(String audioId, String filename) {
        setSelectedAudio(audioId, filename);
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

    public boolean isPaused() {
        SpeakerState state = getSpeakerState();
        return state != null && state.isPaused();
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
            tag.putInt("RepeatMode", persistedState.getPlaylist().getRepeatMode().ordinal());
            tag.putString("NetworkName",persistedState.getNetworkName());
            tag.putFloat("Directionality",persistedState.getDirectionality());
            tag.putInt("ConeAngleDegrees",persistedState.getConeAngleDegrees());
            tag.putFloat("RearAttenuation",persistedState.getRearAttenuation());
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
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.setVolume(serverLevel.getServer(), serverLevel, getFullStateKey(), maxVolume);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            updateEmitterSnapshot();
        }
    }

    public void setMaxRange(int maxRange) {
        if (level instanceof ServerLevel serverLevel) {
            ServerSpeakerControlService.setRange(serverLevel.getServer(), serverLevel, getFullStateKey(), maxRange);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            updateEmitterSnapshot();
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
                if (level instanceof ServerLevel serverLevel)
                    com.nstut.simplyspeakers.speakers.ServerPlaybackManager.refreshSettings(serverLevel.getServer(), serverLevel, getFullStateKey());
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
