package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerSettings;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.audio.SpatialAudioCalculator;
import com.nstut.simplyspeakers.client.ClientSpeakerRegistry;
import com.nstut.simplyspeakers.network.PlayAudioPacketS2C;
import com.nstut.simplyspeakers.network.StopAudioPacketS2C;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import dev.architectury.networking.NetworkManager;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Block entity for the Proxy Speaker block.
 */
@Getter
public class ProxySpeakerBlockEntity extends BlockEntity {

    private static final String NBT_SPEAKER_ID = "SpeakerID";
    private static final String NBT_PROXY_PLAYING = "ProxyPlaying";

    final Set<UUID> listeningPlayers = new HashSet<>();
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
            if (!level.isClientSide() && !isProxyPlaying) {
                stopAudio();
            }
        }
    }

    public void setMaxVolume(float maxVolume) {
        float val = Math.max(0.0f, Math.min(1.0f, maxVolume));
        if (level != null && !level.isClientSide()) {
            this.maxVolume = val;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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
        listeningPlayers.clear();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void stopAudio() {
        if (level == null || level.isClientSide()) return;

        if (level instanceof ServerLevel serverLevel) {
            StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(worldPosition);
            for (ServerPlayer player : serverLevel.players()) {
                NetworkManager.sendToPlayer(player, stopPacket);
            }
        }
        listeningPlayers.clear();
    }

    private void tick(Level currentLevel, BlockPos currentPos, BlockState currentState) {
        if (currentLevel == null || currentLevel.isClientSide()) return;

        if (!currentState.is(com.nstut.simplyspeakers.blocks.BlockRegistries.PROXY_SPEAKER.get())) {
            stopAudio();
            return;
        }

        if (!isProxyPlaying) {
            if (!listeningPlayers.isEmpty()) {
                if (currentLevel instanceof ServerLevel serverLevel) {
                    for (UUID playerId : listeningPlayers) {
                        ServerPlayer p = (ServerPlayer) serverLevel.getPlayerByUUID(playerId);
                        if (p != null) {
                            StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(currentPos);
                            NetworkManager.sendToPlayer(p, stopPacket);
                        }
                    }
                }
                listeningPlayers.clear();
            }
            return;
        }

        boolean isPowered = currentState.getValue(com.nstut.simplyspeakers.blocks.ProxySpeakerBlock.POWERED);
        SpeakerState state = getSpeakerState();

        if (!isPowered || state == null || !state.isPlaying() || state.isPaused() || state.getAudioId() == null || state.getAudioId().isEmpty()) {
            if (!listeningPlayers.isEmpty()) {
                if (currentLevel instanceof ServerLevel serverLevel) {
                    for (UUID playerId : listeningPlayers) {
                        ServerPlayer p = (ServerPlayer) serverLevel.getPlayerByUUID(playerId);
                        if (p != null) {
                            StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(currentPos);
                            NetworkManager.sendToPlayer(p, stopPacket);
                        }
                    }
                }
                listeningPlayers.clear();
            }
            return;
        }

        if (!(currentLevel instanceof ServerLevel serverLevel)) return;

        long gameTime = currentLevel.getGameTime();
        if ((gameTime + currentPos.hashCode()) % 4 != 0) {
            return;
        }

        int effectiveRange = SpeakerSettings.effectiveRange(maxRange);
        Vec3 speakerCenterPos = Vec3.atCenterOf(currentPos);
        Set<UUID> playersInRange = new HashSet<>();

        for (ServerPlayer player : serverLevel.players()) {
            double distanceSq = player.position().distanceToSqr(speakerCenterPos);
            if (!com.nstut.simplyspeakers.audio.ListenerRangePolicy.shouldListen(distanceSq, effectiveRange, listeningPlayers.contains(player.getUUID()))) {
                continue;
            }
            playersInRange.add(player.getUUID());

            if (!listeningPlayers.contains(player.getUUID())) {
                float elapsedSeconds = state.getPlaybackPositionSeconds(currentLevel.getGameTime());
                if (elapsedSeconds < 0) elapsedSeconds = 0;
                float playbackPositionSeconds = elapsedSeconds;
                AudioFileManager audioFileManager = SimplySpeakers.getAudioFileManager();
                if (audioFileManager != null) {
                    AudioFileMetadata meta = audioFileManager.getManifest().get(state.getAudioId());
                    if (meta != null && meta.getDurationSeconds() > 0.0f && state.isLooping()) {
                        playbackPositionSeconds = elapsedSeconds % meta.getDurationSeconds();
                    }
                }

                PlayAudioPacketS2C playPacket = new PlayAudioPacketS2C(
                        currentPos,
                        this.speakerId,
                        state.getAudioId(),
                        state.getAudioFilename(),
                        playbackPositionSeconds,
                        state.isLooping(),
                        effectiveRange,
                        this.maxVolume,
                        this.audioDropoff
                );
                playPacket.attachExtras(directionalExtras(currentLevel, currentPos, state));
                if (audioFileManager != null) audioFileManager.grantPlaybackDownload(player, state.getAudioId());
                NetworkManager.sendToPlayer(player, playPacket);
                listeningPlayers.add(player.getUUID());
            }
        }

        if (!listeningPlayers.isEmpty()) {
            Set<UUID> playersToStop = new HashSet<>(listeningPlayers);
            playersToStop.removeAll(playersInRange);

            for (UUID playerId : playersToStop) {
                ServerPlayer p = (ServerPlayer) serverLevel.getPlayerByUUID(playerId);
                if (p != null) {
                    StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(currentPos);
                    NetworkManager.sendToPlayer(p, stopPacket);
                }
                listeningPlayers.remove(playerId);
            }
        }
    }


    private com.nstut.simplyspeakers.audio.DirectionalAudio.Extras directionalExtras(Level lvl, BlockPos pos, SpeakerState st) {
        if (st == null || st.getDirectionality() <= 0.0f) return null;
        BlockState bs = lvl.getBlockState(pos);
        byte facingOrdinal = 2; // NORTH fallback
        if (bs.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            facingOrdinal = (byte) bs.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).ordinal();
        }
        return new com.nstut.simplyspeakers.audio.DirectionalAudio.Extras(
                st.getDirectionality(), st.getConeAngleDegrees(), st.getRearAttenuation(), facingOrdinal);
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

        listeningPlayers.clear();

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
