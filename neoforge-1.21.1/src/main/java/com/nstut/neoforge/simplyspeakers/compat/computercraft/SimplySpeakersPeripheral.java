package com.nstut.neoforge.simplyspeakers.compat.computercraft;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.api.SpeakerApi;
import com.nstut.simplyspeakers.api.SpeakerEvents;
import com.nstut.simplyspeakers.SpeakerPermissions;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.audio.StreamTracks;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.speakers.ServerSpeakerControlService;
import dan200.computercraft.api.lua.ILuaCallback;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * CC:Tweaked peripheral exposed by every main Speaker block entity. Methods run
 * on the main server thread and delegate through {@link SpeakerApi}. Lifecycle
 * events are queued to attached computers as speaker_started, speaker_paused,
 * speaker_resumed, speaker_stopped, speaker_track_changed, and speaker_finished.
 */
public class SimplySpeakersPeripheral implements IPeripheral {

    private static final Set<SimplySpeakersPeripheral> LIVE = ConcurrentHashMap.newKeySet();
    private static volatile SpeakerEvents.Listener eventListener = null;
    private static volatile boolean listenerRegistered = false;

    private final SpeakerBlockEntity speaker;
    private final Set<IComputerAccess> attachedComputers = ConcurrentHashMap.newKeySet();

    public SimplySpeakersPeripheral(SpeakerBlockEntity speaker) {
        this.speaker = speaker;
        registerListenerOnce();
    }

    /** Clears all peripheral instances and unregisters the shared event listener. Call on
     *  server/world shutdown so the listener is not registered twice on top of the previous
     *  one (SpeakerEvents keeps listeners in a list, so re-registering without unregistering
     *  would duplicate every event after each world/server reset). */
    public static void reset() {
        SpeakerEvents.Listener listener = eventListener;
        if (listener != null) {
            SpeakerEvents.unregister(listener);
            eventListener = null;
        }
        listenerRegistered = false;
        LIVE.clear();
    }

    private static void registerListenerOnce() {
        if (listenerRegistered) return;
        synchronized (SimplySpeakersPeripheral.class) {
            if (listenerRegistered) return;
            SpeakerEvents.Listener listener = (type, stateKey, networkName, audioId) -> {
                String eventName = switch (type) {
                    case STARTED -> "speaker_started";
                    case PAUSED -> "speaker_paused";
                    case RESUMED -> "speaker_resumed";
                    case STOPPED -> "speaker_stopped";
                    case TRACK_CHANGED -> "speaker_track_changed";
                    case FINISHED -> "speaker_finished";
                };
                for (SimplySpeakersPeripheral peripheral : LIVE) {
                    if (peripheral.attachedComputers.isEmpty()) continue;
                    if (!peripheral.speaker.getStateKey().equals(stateKey) && !peripheral.speaker.getFullStateKey().equals(stateKey)) continue;
                    for (IComputerAccess computer : peripheral.attachedComputers) {
                        computer.queueEvent(eventName, computer.getAttachmentName(), audioId, networkName);
                    }
                }
            };
            SpeakerEvents.register(listener);
            eventListener = listener;
            listenerRegistered = true;
        }
    }

    @Nullable
    private ServerLevel serverLevel() {
        return speaker.getLevel() instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    private BlockPos pos() {
        return speaker.getBlockPos();
    }

    /**
     * Runs a read-only snapshot on the main server thread and resumes the Lua caller
     * with the result, keeping mutable server state off the Lua thread. The Lua method
     * yields until the snapshot completes (interruptible by "terminate").
     */
    private MethodResult readOnServerThread(@Nullable ServerLevel level, Supplier<Object> snapshot) {
        if (level == null) return MethodResult.of(snapshot.get());
        CompletableFuture<Object> future = new CompletableFuture<>();
        level.getServer().execute(() -> {
            try {
                future.complete(snapshot.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return MethodResult.pullEvent(null, new ILuaCallback() {
            @Override
            public MethodResult resume(Object[] args) throws LuaException {
                if (!future.isDone()) return MethodResult.pullEvent(null, this);
                try {
                    return MethodResult.of(future.join());
                } catch (CompletionException e) {
                    throw new LuaException("Failed to read speaker state");
                }
            }
        });
    }

    /** Whether untrusted automation (this CC peripheral) may drive transport/settings. */
    private boolean mayAutomationControl() {
        ServerLevel level = serverLevel();
        return level != null && SpeakerPermissions.canAutomationControl(SpeakerApi.getState(level, pos()));
    }

    /** Whether untrusted automation may rename/manage the network identity. */
    private boolean mayAutomationManage() {
        ServerLevel level = serverLevel();
        return level != null && SpeakerPermissions.canAutomationManage(SpeakerApi.getState(level, pos()));
    }




    /**
     * Registers the NeoForge peripheral capability for the speaker block entity.
     * Called from the mod constructor only when CC:Tweaked is present; keeping
     * the CC API references inside this class (and out of the mod class's
     * constant pool) prevents class-loading failures when CC:Tweaked is absent.
     */
    public static void registerProvider(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(
                    dan200.computercraft.api.peripheral.PeripheralCapability.get(),
                    com.nstut.simplyspeakers.blocks.entities.BlockEntityRegistries.SPEAKER.get(),
                    (be, side) -> new SimplySpeakersPeripheral(be));
        });
    }

    @Override
    public String getType() {
        return "simply_speaker";
    }

    @Override
    public void attach(IComputerAccess computer) {
        attachedComputers.add(computer);
        if (attachedComputers.size() == 1) {
            LIVE.add(this);
        }
    }

    @Override
    public void detach(IComputerAccess computer) {
        attachedComputers.remove(computer);
        if (attachedComputers.isEmpty()) {
            LIVE.remove(this);
        }
    }

    @Nullable
    @Override
    public Object getTarget() {
        return speaker;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof SimplySpeakersPeripheral peripheral && peripheral.speaker == this.speaker;
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    @LuaFunction(mainThread = true)
    public final void play() {
        if (!mayAutomationControl()) return;
        SpeakerApi.play(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final void pause() {
        if (!mayAutomationControl()) return;
        SpeakerApi.pause(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final void togglePause() {
        if (!mayAutomationControl()) return;
        SpeakerApi.togglePause(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final void stop() {
        if (!mayAutomationControl()) return;
        SpeakerApi.stop(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final void restart() {
        if (!mayAutomationControl()) return;
        SpeakerApi.restart(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final void next() {
        if (!mayAutomationControl()) return;
        SpeakerApi.next(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final void previous() {
        if (!mayAutomationControl()) return;
        SpeakerApi.previous(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final void seek(double seconds) {
        if (!mayAutomationControl()) return;
        SpeakerApi.seek(serverLevel(), pos(), (float) seconds);
    }

    /**
     * Selects a track on this speaker without necessarily starting playback — the same
     * effect as picking an entry in the speaker GUI (an already-playing network switches
     * to the new track, an idle one stays idle). An empty string clears the selection.
     * Library tracks must exist in the server manifest and the filename is always
     * derived from that manifest, never from the caller; URL tracks must pass the same
     * remote-stream policy the network layer applies (HTTP(S) URL, supported extension,
     * SSRF check, server config). Returns true when the selection was applied, false
     * when the speaker has no state or the track was rejected.
     *
     * <p>Ownership of library tracks is intentionally not re-checked here: CC:Tweaked
     * methods have no player actor, and invoking peripheral methods requires physical
     * access to the block, so the computer is treated like a system actor.</p>
     */
    @LuaFunction(mainThread = true)
    public final boolean setTrack(String audioId) {
        if (!mayAutomationControl()) return false;
        ServerLevel level = serverLevel();
        if (level == null) return false;
        String fullStateKey = ServerSpeakerControlService.resolveFullStateKey(level, pos());
        if (fullStateKey == null) return false;
        if (audioId == null || audioId.isEmpty()) {
            return ServerSpeakerControlService.selectAudio(level.getServer(), level, fullStateKey, "", "");
        }
        if (StreamTracks.isHttpAudioUrl(audioId)) {
            if (!Config.isRemoteStreamingAllowed()
                    || !StreamTracks.hasSupportedExtension(audioId)
                    || !StreamTracks.isRemoteStreamUrlAllowed(audioId, false)) {
                return false;
            }
            return ServerSpeakerControlService.selectAudio(level.getServer(), level, fullStateKey, audioId, audioId);
        }
        AudioFileManager manager = SimplySpeakers.getAudioFileManager();
        if (manager == null) return false;
        AudioFileMetadata meta = manager.getManifest().get(audioId);
        if (meta == null) return false;
        return ServerSpeakerControlService.selectAudio(level.getServer(), level, fullStateKey,
                meta.getUuid(), meta.getOriginalFilename());
    }

    /** Lua-friendly playback status snapshot. */
    @LuaFunction
    public final MethodResult getStatus(IComputerAccess computer) {
        ServerLevel level = serverLevel();
        return readOnServerThread(level, () -> buildStatus(level));
    }

    private Map<String, Object> buildStatus(@Nullable ServerLevel level) {
        Map<String, Object> result = new HashMap<>();
        var state = level != null ? SpeakerApi.getState(level, pos()) : null;
        result.put("playing", state != null && state.isPlaying() && !state.isPaused());
        result.put("paused", state != null && state.isPaused());
        result.put("position", (double) (state != null && level != null
                ? state.getPlaybackPositionSeconds(level.getGameTime()) : 0.0f));
        result.put("track", state != null ? state.getAudioFilename() : "");
        result.put("trackId", state != null ? state.getAudioId() : "");
        result.put("looping", state != null && state.isLooping());
        result.put("network", state != null ? state.getNetworkName() : "");
        return result;
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    @LuaFunction(mainThread = true)
    public final void setVolume(double volume) {
        if (!mayAutomationControl()) return;
        SpeakerApi.setVolume(serverLevel(), pos(), (float) volume);
    }

    @LuaFunction
    public final MethodResult getVolume(IComputerAccess computer) {
        ServerLevel level = serverLevel();
        return readOnServerThread(level, () -> {
            var state = level != null ? SpeakerApi.getState(level, pos()) : null;
            return (Object) (state != null ? state.getMaxVolume() : 1.0);
        });
    }

    @LuaFunction(mainThread = true)
    public final void setRange(int range) {
        if (!mayAutomationControl()) return;
        SpeakerApi.setRange(serverLevel(), pos(), range);
    }

    @LuaFunction
    public final MethodResult getRange(IComputerAccess computer) {
        ServerLevel level = serverLevel();
        return readOnServerThread(level, () -> {
            var state = level != null ? SpeakerApi.getState(level, pos()) : null;
            return (Object) (state != null ? state.getMaxRange() : 16);
        });
    }

    @LuaFunction(mainThread = true)
    public final void setLooping(boolean looping) {
        if (!mayAutomationControl()) return;
        SpeakerApi.setLooping(serverLevel(), pos(), looping);
    }

    @LuaFunction
    public final MethodResult isLooping(IComputerAccess computer) {
        ServerLevel level = serverLevel();
        return readOnServerThread(level, () -> {
            var state = level != null ? SpeakerApi.getState(level, pos()) : null;
            return (Object) (state != null && state.isLooping());
        });
    }

    // ------------------------------------------------------------------
    // Playlist
    // ------------------------------------------------------------------

    @LuaFunction(mainThread = true)
    public final void setShuffle(boolean shuffle) {
        if (!mayAutomationControl()) return;
        SpeakerApi.playlistSetShuffle(serverLevel(), pos(), shuffle);
    }

    @LuaFunction(mainThread = true)
    public final void setRepeatMode(String mode) {
        if (!mayAutomationControl()) return;
        SpeakerApi.playlistSetRepeat(serverLevel(), pos(),
                com.nstut.simplyspeakers.playlist.RepeatMode.parse(mode));
    }

    @LuaFunction(mainThread = true)
    public final void queueNext(String audioId) {
        if (!mayAutomationControl()) return;
        SpeakerApi.playlistQueueNext(serverLevel(), pos(), audioId);
    }

    /** Returns playlist tracks with 1-based slot numbers. */
    @LuaFunction
    public final MethodResult getPlaylist(IComputerAccess computer) {
        ServerLevel level = serverLevel();
        return readOnServerThread(level, () -> {
            List<Object> result = new ArrayList<>();
            var state = level != null ? SpeakerApi.getState(level, pos()) : null;
            if (state != null && state.hasPlaylist()) {
                var playlist = state.getPlaylist();
                int slot = 1;
                for (var track : playlist.getTracks()) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("slot", slot++);
                    entry.put("name", track.getFilename());
                    entry.put("id", track.getAudioId());
                    result.add(entry);
                }
            }
            return (Object) result;
        });
    }

    // ------------------------------------------------------------------
    // Network identity
    // ------------------------------------------------------------------

    @LuaFunction
    public final MethodResult getNetworkName(IComputerAccess computer) {
        ServerLevel level = serverLevel();
        return readOnServerThread(level, () -> {
            var state = level != null ? SpeakerApi.getState(level, pos()) : null;
            return (Object) (state != null ? state.getNetworkName() : "");
        });
    }

    @LuaFunction(mainThread = true)
    public final void setNetworkName(String name) {
        if (!mayAutomationManage()) return;
        SpeakerApi.setNetworkName(serverLevel(), pos(), name);
    }
}
