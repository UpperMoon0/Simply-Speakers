package com.nstut.simplyspeakers.fabric.compat.computercraft;

import com.nstut.simplyspeakers.api.SpeakerApi;
import com.nstut.simplyspeakers.api.SpeakerEvents;
import com.nstut.simplyspeakers.SpeakerPermissions;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
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
     * Snapshot helpers run under CC's main-thread task scheduler.
     */
    private MethodResult readOnServerThread(@Nullable ServerLevel level, Supplier<Object> snapshot) {
        // CC schedules @LuaFunction(mainThread=true), including its completion wakeup.
        return MethodResult.of(snapshot.get());
    }

    /**
     * Registers the Fabric peripheral lookup for the speaker block entity.
     * Called from the mod initializer only when CC:Tweaked is present; keeping
     * the CC API references inside this class (and out of the entrypoint's
     * constant pool) prevents class-loading failures when CC:Tweaked is absent.
     */
    public static void registerProvider() {
        dan200.computercraft.api.peripheral.PeripheralLookup.get().registerForBlockEntity(
                (be, side) -> new SimplySpeakersPeripheral(be),
                com.nstut.simplyspeakers.blocks.entities.BlockEntityRegistries.SPEAKER.get());
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
    public final boolean play() {
        return SpeakerApi.play(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final boolean pause() {
        return SpeakerApi.pause(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final boolean togglePause() {
        return SpeakerApi.togglePause(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final boolean stop() {
        return SpeakerApi.stop(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final boolean restart() {
        return SpeakerApi.restart(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final boolean next() {
        return SpeakerApi.next(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final boolean previous() {
        return SpeakerApi.previous(serverLevel(), pos());
    }

    @LuaFunction(mainThread = true)
    public final boolean seek(double seconds) {
        return SpeakerApi.seek(serverLevel(), pos(), (float) seconds);
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
     * <p>Automation is untrusted: CC:Tweaked methods have no player actor, so the
     * computer acts on behalf of the speaker network's owner. Library tracks are
     * accepted only when owned by that owner (unowned networks accept no library
     * tracks), keeping the library-ownership boundary consistent with the packet
     * paths.</p>
     */
    @LuaFunction(mainThread = true)
    public final boolean setTrack(String audioId) {
        return SpeakerApi.selectTrack(serverLevel(),pos(),audioId);
    }

    /** Lua-friendly playback status snapshot. */
    @LuaFunction(mainThread = true)
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
        result.put("speakerId",speaker.getSpeakerId());
        result.put("repeatMode",state==null?"none":state.getPlaylist().getRepeatMode().id());
        result.put("shuffle",state!=null && state.getPlaylist().isShuffle());
        result.put("playlistIndex",state==null?0:state.getPlaylist().getCurrentIndex()+1);
        result.put("canControl",state!=null && SpeakerPermissions.canAutomationControl(state));
        result.put("canManage",state!=null && SpeakerPermissions.canAutomationManage(state));
        return result;
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    @LuaFunction(mainThread = true)
    public final boolean setVolume(double volume) {
        return SpeakerApi.setVolume(serverLevel(), pos(), (float) volume);
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getVolume(IComputerAccess computer) {
        ServerLevel level = serverLevel();
        return readOnServerThread(level, () -> {
            var state = level != null ? SpeakerApi.getState(level, pos()) : null;
            return (Object) (state != null ? state.getMaxVolume() : 1.0);
        });
    }

    @LuaFunction(mainThread = true)
    public final boolean setRange(int range) {
        return SpeakerApi.setRange(serverLevel(), pos(), range);
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getRange(IComputerAccess computer) {
        ServerLevel level = serverLevel();
        return readOnServerThread(level, () -> {
            var state = level != null ? SpeakerApi.getState(level, pos()) : null;
            return (Object) (state != null ? state.getMaxRange() : 16);
        });
    }

    @LuaFunction(mainThread = true)
    public final boolean setLooping(boolean looping) {
        return SpeakerApi.setLooping(serverLevel(), pos(), looping);
    }

    @LuaFunction(mainThread = true)
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
    public final boolean setShuffle(boolean shuffle) {
        return SpeakerApi.playlistSetShuffle(serverLevel(), pos(), shuffle);
    }

    @LuaFunction(mainThread = true)
    public final boolean setRepeatMode(String mode) {
        for(var repeat:com.nstut.simplyspeakers.playlist.RepeatMode.values())
            if(repeat.id().equalsIgnoreCase(mode.trim())) return SpeakerApi.playlistSetRepeat(serverLevel(),pos(),repeat);
        return false;
    }

    @LuaFunction(mainThread = true)
    public final boolean queueNext(String audioId) {
        return SpeakerApi.playlistQueueNext(serverLevel(), pos(), audioId);
    }

    /** Returns playlist tracks with 1-based slot numbers. */
    @LuaFunction(mainThread = true)
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

    @LuaFunction(mainThread = true)
    public final MethodResult getNetworkName(IComputerAccess computer) {
        ServerLevel level = serverLevel();
        return readOnServerThread(level, () -> {
            var state = level != null ? SpeakerApi.getState(level, pos()) : null;
            return (Object) (state != null ? state.getNetworkName() : "");
        });
    }

    @LuaFunction(mainThread = true)
    public final boolean setNetworkName(String name) {
        return SpeakerApi.setNetworkName(serverLevel(), pos(), name);
    }
    @LuaFunction(mainThread = true)
    public final boolean queueLast(String id) {return SpeakerApi.playlistQueueLast(serverLevel(),pos(),id);}
    @LuaFunction(mainThread = true)
    public final boolean clearQueue() {return SpeakerApi.playlistClearQueue(serverLevel(),pos());}
    @LuaFunction(mainThread = true)
    public final boolean removeQueued(int slot) {return SpeakerApi.playlistRemoveQueued(serverLevel(),pos(),slot-1);}
    @LuaFunction(mainThread = true)
    public final boolean moveQueued(int slot,int direction) {return SpeakerApi.playlistMoveQueued(serverLevel(),pos(),slot-1,direction);}
    @LuaFunction(mainThread = true)
    public final MethodResult getQueue() {
        var state=SpeakerApi.getState(serverLevel(),pos());
        return MethodResult.of((Object)(state==null?List.of():List.copyOf(state.getPlaylist().getQueue())));
    }
    @LuaFunction(mainThread = true)
    public final boolean addToPlaylist(String id) {return SpeakerApi.playlistAdd(serverLevel(),pos(),id,"");}
    @LuaFunction(mainThread = true)
    public final boolean removeFromPlaylist(String id) {return SpeakerApi.playlistRemove(serverLevel(),pos(),id);}
    @LuaFunction(mainThread = true)
    public final boolean clearPlaylist() {return SpeakerApi.playlistClear(serverLevel(),pos());}
    @LuaFunction(mainThread = true)
    public final boolean playPlaylist() {return SpeakerApi.playlistPlay(serverLevel(),pos());}
    @LuaFunction(mainThread = true)
    public final boolean selectPlaylistTrack(int slot) {return SpeakerApi.playlistSelect(serverLevel(),pos(),slot-1);}
    @LuaFunction(mainThread = true)
    public final boolean setAudioDropoff(double value) {return SpeakerApi.setAudioDropoff(serverLevel(),pos(),(float)value);}
    @LuaFunction(mainThread = true)
    public final boolean setDirectionality(double value) {return SpeakerApi.setDirectionality(serverLevel(),pos(),(float)value);}
    @LuaFunction(mainThread = true)
    public final boolean setConeAngle(int degrees) {return SpeakerApi.setConeAngle(serverLevel(),pos(),degrees);}
    @LuaFunction(mainThread = true)
    public final boolean setRearAttenuation(double value) {return SpeakerApi.setRearAttenuation(serverLevel(),pos(),(float)value);}
    @LuaFunction(mainThread = true)
    public final MethodResult getSettings() {
        var state=SpeakerApi.getState(serverLevel(),pos());var result=new HashMap<String,Object>();
        if(state!=null) {
            result.put("volume",(double)state.getMaxVolume());result.put("range",state.getMaxRange());
            result.put("audioDropoff",(double)state.getAudioDropoff());result.put("directionality",(double)state.getDirectionality());
            result.put("coneAngle",state.getConeAngleDegrees());result.put("rearAttenuation",(double)state.getRearAttenuation());
        }
        return MethodResult.of(result);
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getSavedPlaylists() {
        var result=new ArrayList<Object>();
        for(var entry:SpeakerApi.getSavedPlaylists(serverLevel(),pos()))
            result.add(Map.of("id",entry.id(),"name",entry.name(),"count",entry.audioIds().size()));
        return MethodResult.of((Object)result);
    }
    @LuaFunction(mainThread = true)
    public final boolean playSavedPlaylist(String id) {return SpeakerApi.playSavedPlaylist(serverLevel(),pos(),id);}

    @LuaFunction(mainThread = true)
    public final MethodResult getLibrary() {
        var result=new ArrayList<Object>();
        for(var audio:SpeakerApi.getLibrary(serverLevel(),pos()))
            result.add(Map.of("id",audio.getUuid(),"name",audio.getOriginalFilename(),"duration",audio.getDurationSeconds()));
        return MethodResult.of((Object)result);
    }

}
