package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerRegistry;
import com.nstut.simplyspeakers.SpeakerSettings;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.RedstoneLogic;
import com.nstut.simplyspeakers.RedstoneMode;
import com.nstut.simplyspeakers.SpeakerAccess;
import com.nstut.simplyspeakers.api.SpeakerEvents;
import com.nstut.simplyspeakers.audio.SpatialAudioCalculator;
import com.nstut.simplyspeakers.network.PlaylistControlPacketC2S;
import com.nstut.simplyspeakers.network.PlaylistSyncPacketS2C;
import com.nstut.simplyspeakers.network.TransportControlPacketC2S;
import com.nstut.simplyspeakers.playlist.Playlist;
import com.nstut.simplyspeakers.playlist.PlaylistTrack;
import com.nstut.simplyspeakers.playlist.RepeatMode;
import com.nstut.simplyspeakers.blocks.SpeakerBlock;
import com.nstut.simplyspeakers.client.ClientSpeakerRegistry;
import com.nstut.simplyspeakers.network.PlayAudioPacketS2C;
import com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C;
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

    /** Last observed redstone strength for edge-triggered modes (not persisted). */
    private int lastRedstoneSignal = 0;

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

    private void sendPlaylistSyncTo(ServerPlayer player, PlaylistSyncPacketS2C packet) {
        NetworkManager.sendToPlayer(player, packet);
    }

    private void sendPlaylistSyncToAll(ServerLevel serverLevel, PlaylistSyncPacketS2C packet) {
        NetworkManager.sendToPlayers(serverLevel.players(), packet);
    }

    // ==================================================================
    // 0.8.x transport, playlists, redstone automation, and policy
    // ==================================================================

    /** Applies a transport action (see TransportControlPacketC2S constants). */
    public void transportAction(Level currentLevel, byte action, float seekSeconds) {
        switch (action) {
            case TransportControlPacketC2S.ACTION_PLAY -> {
                SpeakerState state = getSpeakerState();
                if (state != null && state.isPaused()) resumeAudio();
                else playAudio();
            }
            case TransportControlPacketC2S.ACTION_PAUSE -> pauseAudio();
            case TransportControlPacketC2S.ACTION_TOGGLE -> togglePause();
            case TransportControlPacketC2S.ACTION_STOP -> stopAudio();
            case TransportControlPacketC2S.ACTION_RESTART -> seekTo(0.0f);
            case TransportControlPacketC2S.ACTION_NEXT -> nextTrack();
            case TransportControlPacketC2S.ACTION_PREVIOUS -> previousTrack();
            case TransportControlPacketC2S.ACTION_SEEK -> seekTo(seekSeconds);
            default -> { }
        }
    }

    /** Suspends playback while preserving the current position. */
    public void pauseAudio() {
        if (level == null || level.isClientSide()) return;
        SpeakerState state = getSpeakerState();
        if (state == null || !state.isPlaying() || state.isPaused()) return;
        state.pauseAt(level.getGameTime());
        updateSpeakerState(state);
        sendStopToListeners(level);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        notifyClientsOfStateChange();
        broadcastPlaylistSync();
        SpeakerEvents.fire(SpeakerEvents.Type.PAUSED, getStateKey(), state.getNetworkName(), state.getAudioId());
    }

    /** Resumes previously paused playback from the preserved position. */
    public void resumeAudio() {
        if (level == null || level.isClientSide()) return;
        SpeakerState state = getSpeakerState();
        if (state == null || !state.isPaused()) return;
        state.resumeAt(level.getGameTime());
        updateSpeakerState(state);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        notifyClientsOfStateChange();
        if (level instanceof ServerLevel serverLevel) resyncListeners(serverLevel);
        broadcastPlaylistSync();
        SpeakerEvents.fire(SpeakerEvents.Type.RESUMED, getStateKey(), state.getNetworkName(), state.getAudioId());
    }

    public void togglePause() {
        SpeakerState state = getSpeakerState();
        if (state != null && state.isPlaying()) {
            if (state.isPaused()) resumeAudio();
            else pauseAudio();
        } else {
            playAudio();
        }
    }

    /** Seeks to an absolute position; live networks re-issue streams at the offset. */
    public void seekTo(float seconds) {
        if (level == null || level.isClientSide()) return;
        SpeakerState state = getSpeakerState();
        if (state == null || !state.hasAudio()) return;
        boolean wasPlaying = state.isPlaying() && !state.isPaused();
        state.seekTo(seconds, level.getGameTime(), trackDurationSeconds(state));
        updateSpeakerState(state);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        notifyClientsOfStateChange();
        if (wasPlaying && level instanceof ServerLevel serverLevel) resyncListeners(serverLevel);
    }

    public void nextTrack() {
        advanceTrack(1);
    }

    public void previousTrack() {
        advanceTrack(-1);
    }

    private void advanceTrack(int direction) {
        if (level == null || level.isClientSide()) return;
        SpeakerState state = getSpeakerState();
        if (state == null || !state.hasPlaylist()) return;
        Playlist playlist = state.getPlaylist();
        Playlist.Advance advance = direction >= 0 ? playlist.next() : playlist.previous();
        if (advance.hasTrack()) {
            startTrackPlayback(state, advance.track().getAudioId(), advance.track().getFilename());
        } else {
            stopAudio();
            SpeakerEvents.fire(SpeakerEvents.Type.FINISHED, getStateKey(), state.getNetworkName(), state.getAudioId());
        }
        broadcastPlaylistSync();
    }

    /** Selects a track and immediately plays it from the start. */
    public void selectAndPlay(String audioId, String filename) {
        if (level == null || level.isClientSide() || audioId == null || audioId.isEmpty()) return;
        SpeakerState state = getSpeakerState();
        if (state == null) return;
        Playlist playlist = state.hasPlaylist() ? state.getPlaylist() : null;
        PlaylistTrack track = playlist != null ? playlist.selectAudioId(audioId) : null;
        String resolvedFilename = filename != null ? filename
                : (track != null ? track.getFilename() : "");
        startTrackPlayback(state, audioId, resolvedFilename);
        broadcastPlaylistSync();
    }

    private void startTrackPlayback(SpeakerState state, String audioId, String filename) {
        if (level == null || level.isClientSide()) return;
        state.setAudioId(audioId);
        state.setAudioFilename(filename);
        state.startPlaybackAt(level.getGameTime(), 0.0f);
        updateSpeakerState(state);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        notifyClientsOfStateChange();
        if (level instanceof ServerLevel serverLevel) resyncListeners(serverLevel);
        SpeakerEvents.fire(SpeakerEvents.Type.TRACK_CHANGED, getStateKey(), state.getNetworkName(), audioId);
    }

    private float trackDurationSeconds(SpeakerState state) {
        AudioFileManager fileManager = SimplySpeakers.getAudioFileManager();
        if (fileManager == null) return 0.0f;
        AudioFileMetadata meta = fileManager.getManifest().get(state.getAudioId());
        return meta != null ? meta.getDurationSeconds() : 0.0f;
    }

    private void sendStopToListeners(Level currentLevel) {
        if (!(currentLevel instanceof ServerLevel serverLevel)) return;
        StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(worldPosition);
        for (UUID playerId : listeningPlayers) {
            ServerPlayer player = (ServerPlayer) serverLevel.getPlayerByUUID(playerId);
            if (player != null) sendStopPacket(player, stopPacket);
        }
        listeningPlayers.clear();
    }

    /** Forces every listener to restart its stream at the current position. */
    private void resyncListeners(ServerLevel serverLevel) {
        SpeakerState state = getSpeakerState();
        if (state == null || !state.isPlaying() || !state.hasAudio()) return;
        StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(worldPosition);
        for (UUID playerId : listeningPlayers) {
            ServerPlayer player = (ServerPlayer) serverLevel.getPlayerByUUID(playerId);
            if (player != null) sendStopPacket(player, stopPacket);
        }
        listeningPlayers.clear();
        scanAndStartListeners(serverLevel, worldPosition, state);
    }

    // ------------------------------------------------------------------
    // Playlist mutations
    // ------------------------------------------------------------------

    /** Handles a playlist mutation op (see PlaylistControlPacketC2S constants). */
    public void playlistControl(Level currentLevel, byte op, int index, boolean flagValue,
                                String audioId, String filename) {
        SpeakerState state = getSpeakerState();
        if (state == null) return;
        Playlist playlist = state.getPlaylist();
        switch (op) {
            case PlaylistControlPacketC2S.OP_ADD -> {
                if (audioId != null && !audioId.isEmpty()) playlist.add(audioId, filename);
            }
            case PlaylistControlPacketC2S.OP_REMOVE_AUDIO -> playlist.removeByAudioId(audioId);
            case PlaylistControlPacketC2S.OP_SELECT_INDEX -> {
                PlaylistTrack track = playlist.selectIndex(index);
                if (track != null && flagValue && state.isPlaying()) {
                    startTrackPlayback(state, track.getAudioId(), track.getFilename());
                }
            }
            case PlaylistControlPacketC2S.OP_MOVE_UP -> playlist.moveUp(index);
            case PlaylistControlPacketC2S.OP_MOVE_DOWN -> playlist.moveDown(index);
            case PlaylistControlPacketC2S.OP_CLEAR -> playlist.clear();
            case PlaylistControlPacketC2S.OP_QUEUE_NEXT -> playlist.queueNext(audioId);
            case PlaylistControlPacketC2S.OP_SET_SHUFFLE -> playlist.setShuffle(flagValue);
            case PlaylistControlPacketC2S.OP_SET_REPEAT -> playlist.setRepeatMode(RepeatMode.fromIndex(index));
            default -> { }
        }
        updateSpeakerState(state);
        broadcastPlaylistSync();
    }

    /** Updates shuffle and repeat mode together. */
    public void setPlaylistModes(Level currentLevel, boolean shuffle, RepeatMode repeatMode) {
        SpeakerState state = getSpeakerState();
        if (state == null) return;
        Playlist playlist = state.getPlaylist();
        playlist.setShuffle(shuffle);
        playlist.setRepeatMode(repeatMode);
        updateSpeakerState(state);
        broadcastPlaylistSync();
    }

    /** Sends the current playlist snapshot to one player. */
    public void sendPlaylistSync(ServerPlayer player) {
        PlaylistSyncPacketS2C packet = buildPlaylistSync();
        if (packet != null) sendPlaylistSyncTo(player, packet);
    }

    private void broadcastPlaylistSync() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        PlaylistSyncPacketS2C packet = buildPlaylistSync();
        if (packet != null) sendPlaylistSyncToAll(serverLevel, packet);
    }

    private PlaylistSyncPacketS2C buildPlaylistSync() {
        SpeakerState state = getSpeakerState();
        if (state == null || !state.hasPlaylist()) return null;
        Playlist playlist = state.getPlaylist();
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (PlaylistTrack track : playlist.getTracks()) {
            ids.add(track.getAudioId());
            names.add(track.getFilename());
        }
        int playingIndex = state.isPlaying() ? playlist.getCurrentIndex() : -1;
        return new PlaylistSyncPacketS2C(worldPosition, ids, names,
                playlist.getCurrentIndex(), playlist.isShuffle(),
                playlist.getRepeatMode().ordinal(), playingIndex, state.isPaused());
    }

    // ------------------------------------------------------------------
    // Redstone automation
    // ------------------------------------------------------------------

    /**
     * Handles a redstone signal change according to the configured mode.
     * Called from the block's neighbour-changed hook with the strongest signal.
     */
    public void handleRedstoneChange(int newSignal) {
        if (level == null || level.isClientSide()) return;
        SpeakerState state = getSpeakerState();
        if (state == null) return;
        int previous = lastRedstoneSignal;
        lastRedstoneSignal = newSignal;
        RedstoneMode mode = state.getRedstoneMode();
        int trackCount = state.hasPlaylist() ? state.getPlaylist().size() : 0;
        RedstoneLogic.RedstoneResult result = RedstoneLogic.evaluate(mode, previous, newSignal, trackCount);
        switch (result.action()) {
            case PLAY -> {
                if (state.isPaused()) resumeAudio();
                else playAudio();
            }
            case STOP -> detachEmitterForPowerOff();
            case RESTART -> seekTo(0.0f);
            case TOGGLE_PAUSE -> togglePause();
            case NEXT_TRACK -> nextTrack();
            case SET_VOLUME -> setMaxVolume(result.payload() / (float) RedstoneMode.MAX_ANALOG_SLOTS);
            case SELECT_TRACK -> {
                PlaylistTrack track = state.getPlaylist().selectIndex(result.payload());
                if (track != null) startTrackPlayback(state, track.getAudioId(), track.getFilename());
            }
            default -> { }
        }
    }

    /** Comparator output exposing playback progress (0 stopped .. 15 finished). */
    public int getComparatorOutput() {
        SpeakerState state = getSpeakerState();
        if (state == null || level == null) return 0;
        return RedstoneLogic.comparatorLevel(state.isPlaying(),
                state.getPlaybackPositionSeconds(level.getGameTime()),
                trackDurationSeconds(state));
    }

    // ------------------------------------------------------------------
    // Policy: network naming, ownership, access, directionality
    // ------------------------------------------------------------------

    public void setNetworkName(String networkName) {
        if (level == null || level.isClientSide()) return;
        SpeakerState state = getSpeakerState();
        if (state == null) return;
        state.setNetworkName(networkName);
        updateSpeakerState(state);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        notifyClientsOfStateChange();
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.saveRegistry();
    }

    public void setRedstoneMode(RedstoneMode mode) {
        if (level == null || level.isClientSide()) return;
        SpeakerState state = getSpeakerState();
        if (state == null) return;
        state.setRedstoneMode(mode);
        updateSpeakerState(state);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        notifyClientsOfStateChange();
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.saveRegistry();
    }

    public void setAccessMode(SpeakerAccess access) {
        if (level == null || level.isClientSide()) return;
        SpeakerState state = getSpeakerState();
        if (state == null) return;
        state.setAccessMode(access);
        updateSpeakerState(state);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        notifyClientsOfStateChange();
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.saveRegistry();
    }

    public void modifyTrust(UUID playerUuid, boolean add) {
        if (level == null || level.isClientSide() || playerUuid == null) return;
        SpeakerState state = getSpeakerState();
        if (state == null) return;
        if (add) state.trustPlayer(playerUuid);
        else state.distrustPlayer(playerUuid);
        updateSpeakerState(state);
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.saveRegistry();
    }

    /** Claims ownership on behalf of the given player when unowned. */
    public void claimOwnership(UUID playerUuid) {
        if (level == null || level.isClientSide() || playerUuid == null) return;
        SpeakerState state = getSpeakerState();
        if (state == null || state.getOwnerUuid() != null) return;
        state.setOwnerUuid(playerUuid);
        updateSpeakerState(state);
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.saveRegistry();
    }

    public void setDirectionality(float directionality) {
        if (level == null || level.isClientSide()) return;
        SpeakerState state = getSpeakerState();
        if (state == null) return;
        state.setDirectionality(directionality);
        updateSpeakerState(state);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        notifyClientsOfStateChange();
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.saveRegistry();
    }

    public void setConeAngleDegrees(int coneAngleDegrees) {
        if (level == null || level.isClientSide()) return;
        SpeakerState state = getSpeakerState();
        if (state == null) return;
        state.setConeAngleDegrees(coneAngleDegrees);
        updateSpeakerState(state);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        notifyClientsOfStateChange();
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.saveRegistry();
    }

    public void setRearAttenuation(float rearAttenuation) {
        if (level == null || level.isClientSide()) return;
        SpeakerState state = getSpeakerState();
        if (state == null) return;
        state.setRearAttenuation(rearAttenuation);
        updateSpeakerState(state);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        notifyClientsOfStateChange();
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.saveRegistry();
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
            scanAndStartListeners(level, worldPosition, state);
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
        scanAndStartListeners(level, worldPosition, state);
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

        if (level instanceof ServerLevel serverLevel) {
            StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(worldPosition);
            for (ServerPlayer p : serverLevel.players()) {
                sendStopPacket(p, stopPacket);
            }
        }
        listeningPlayers.clear();

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        notifyClientsOfStateChange();
    }

    public void detachEmitterForPowerOff() {
        if (level == null || level.isClientSide()) return;
        ServerSpeakerRegistry.setSpeakerPowered(level, worldPosition, getStateKey(), false);
        if (!ServerSpeakerRegistry.hasOtherPoweredMain(level, worldPosition, getStateKey())) {
            stopAudio();
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            StopAudioPacketS2C packet = new StopAudioPacketS2C(worldPosition);
            for (UUID playerId : listeningPlayers) {
                ServerPlayer player = (ServerPlayer) serverLevel.getPlayerByUUID(playerId);
                if (player != null) sendStopPacket(player, packet);
            }
        }
        listeningPlayers.clear();
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

        boolean isPowered = currentState.getValue(SpeakerBlock.POWERED);
        ServerSpeakerRegistry.setSpeakerPowered(currentLevel, currentPos, getStateKey(), isPowered);

        if (!isPowered) {
            if (!listeningPlayers.isEmpty()) {
                if (currentLevel instanceof ServerLevel serverLevel) {
                    for (UUID playerId : listeningPlayers) {
                        ServerPlayer serverPlayer = (ServerPlayer) serverLevel.getPlayerByUUID(playerId);
                        if (serverPlayer != null) {
                            StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(currentPos);
                            sendStopPacket(serverPlayer, stopPacket);
                        }
                    }
                }
                listeningPlayers.clear();
            }

            return;
        }

        SpeakerState state = getSpeakerState();
        if (state == null) return;

        if (!state.isPlaying()) {
            if (!listeningPlayers.isEmpty()) {
                if (currentLevel instanceof ServerLevel serverLevel) {
                    for (UUID playerId : listeningPlayers) {
                        ServerPlayer serverPlayer = (ServerPlayer) serverLevel.getPlayerByUUID(playerId);
                        if (serverPlayer != null) {
                            StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(currentPos);
                            sendStopPacket(serverPlayer, stopPacket);
                        }
                    }
                }
                listeningPlayers.clear();
            }
            return;
        }

        // Natural EOF check for non-looping audio
        if (!state.isLooping() && state.getPlaybackStartTick() > 0) {
            float elapsedSeconds = state.getPlaybackPositionSeconds(currentLevel.getGameTime());
            AudioFileManager audioFileManager = SimplySpeakers.getAudioFileManager();
            if (audioFileManager != null) {
                AudioFileMetadata meta = audioFileManager.getManifest().get(state.getAudioId());
                if (meta != null && meta.getDurationSeconds() > 0.0f && elapsedSeconds >= meta.getDurationSeconds()) {
                    if (state.hasPlaylist()) {
                        advanceTrack(1);
                    } else {
                        stopAudio();
                        SpeakerEvents.fire(SpeakerEvents.Type.FINISHED, getStateKey(), "", state.getAudioId());
                    }
                    return;
                }
            }
        }

        // Unconditional 4-tick throttle for listener scanning
        long gameTime = currentLevel.getGameTime();
        if ((gameTime + currentPos.hashCode()) % 4 != 0) {
            return;
        }

        scanAndStartListeners(currentLevel, currentPos, state);
    }

    private void scanAndStartListeners(Level currentLevel, BlockPos currentPos, SpeakerState state) {
        if (!(currentLevel instanceof ServerLevel serverLevel)) {
            return;
        }

        int effectiveRange = SpeakerSettings.effectiveRange(state.getMaxRange());
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
                        state.getMaxVolume(),
                        state.getAudioDropoff()
                );
                playPacket.attachExtras(directionalExtras(currentLevel, currentPos, state));
                if (audioFileManager != null) audioFileManager.grantPlaybackDownload(player, state.getAudioId());
                sendPlayPacket(player, playPacket);
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
                    sendStopPacket(serverPlayerInstance, stopPacket);
                }
            }
            listeningPlayers.removeAll(playersToStop);
        }
    }

    private void sendStopPacket(ServerPlayer player, StopAudioPacketS2C packet) {
        NetworkManager.sendToPlayer(player, packet);
    }

    private void sendPlayPacket(ServerPlayer player, PlayAudioPacketS2C packet) {
        NetworkManager.sendToPlayer(player, packet);
    }

    private void sendStateUpdatePacket(ServerPlayer player, SpeakerStateUpdatePacketS2C packet) {
        NetworkManager.sendToPlayer(player, packet);
    }

    private void sendStateUpdatePacketToAll(ServerLevel serverLevel, SpeakerStateUpdatePacketS2C packet) {
        NetworkManager.sendToPlayers(serverLevel.players(), packet);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide()) {
            detachEmitterForPowerOff();
            ServerSpeakerRegistry.unregisterSpeaker(level, pos, getStateKey());
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);

        String uuidStr = tag.getStringOr(NBT_INTERNAL_ID, "");
        boolean migratedInternalId = uuidStr.isEmpty();
        if (!uuidStr.isEmpty()) {
            try {
                internalStateId = UUID.fromString(uuidStr);
            } catch (Exception e) {
                internalStateId = UUID.randomUUID();
            }
        } else {
            internalStateId = UUID.randomUUID();
            setChanged();
        }

        speakerId = tag.getStringOr(NBT_SPEAKER_ID, "");

        if (level != null && !level.isClientSide()) {
            if (migratedInternalId) ServerSpeakerRegistry.applyLegacyStandaloneTemplate(level, getStateKey());
            SpeakerState persistedState = ServerSpeakerRegistry.getOrCreateSpeakerState(level, getStateKey());
            SpeakerSettings.read(tag::getFloatOr, tag::getIntOr, SpeakerSettings.from(persistedState)).applyTo(persistedState);

            ensureServerRegistration();

            SpeakerState state = ServerSpeakerRegistry.getSpeakerState(level, getStateKey());
            if (state != null && state.isPlaying()) {
                notifyClientsOfStateChange();
            }
        } else {
            SpeakerState clientState = ClientSpeakerRegistry.getOrCreateState(getStateKey());
            SpeakerSettings.read(tag::getFloatOr, tag::getIntOr, SpeakerSettings.from(clientState)).applyTo(clientState);
            String audioId = tag.getStringOr("AudioId", "");
            if (!audioId.isEmpty()) {
                clientState.setAudioId(audioId);
                clientState.setAudioFilename(tag.getStringOr("AudioFilename", ""));
                clientState.setPlaying(tag.getBooleanOr("IsPlaying", false));
                clientState.setLooping(tag.getBooleanOr("IsLooping", false));
                clientState.setPlaybackStartTick(tag.getLongOr("PlaybackStartTick", -1L));
            }
        }

        listeningPlayers.clear();
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);

        tag.putString(NBT_INTERNAL_ID, internalStateId.toString());

        if (!speakerId.isEmpty()) {
            tag.putString(NBT_SPEAKER_ID, speakerId);
        }

        SpeakerState persistedState = getSpeakerState();
        if (persistedState != null) {
            SpeakerSettings.from(persistedState).write(tag::putFloat, tag::putInt);
            tag.putString("AudioId", persistedState.getAudioId() != null ? persistedState.getAudioId() : "");
            tag.putString("AudioFilename", persistedState.getAudioFilename() != null ? persistedState.getAudioFilename() : "");
            tag.putBoolean("IsPlaying", persistedState.isPlaying());
            tag.putBoolean("IsLooping", persistedState.isLooping());
            tag.putLong("PlaybackStartTick", persistedState.getPlaybackStartTick());
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
