package com.nstut.simplyspeakers.forge.compat.computercraft;

import com.nstut.simplyspeakers.api.SpeakerApi;
import com.nstut.simplyspeakers.api.SpeakerEvents;
import com.nstut.simplyspeakers.SpeakerPermissions;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
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

/**
 * CC:Tweaked peripheral exposed by every main Speaker block entity. Methods run
 * on the main server thread and delegate through {@link SpeakerApi}. Lifecycle
 * events are queued to attached computers as speaker_started, speaker_paused,
 * speaker_resumed, speaker_stopped, speaker_track_changed, and speaker_finished.
 */
public class SimplySpeakersPeripheral implements IPeripheral {

    private static final Set<SimplySpeakersPeripheral> LIVE = ConcurrentHashMap.newKeySet();
    private static volatile boolean listenerRegistered = false;

    private final SpeakerBlockEntity speaker;
    private final Set<IComputerAccess> attachedComputers = ConcurrentHashMap.newKeySet();

    public SimplySpeakersPeripheral(SpeakerBlockEntity speaker) {
        this.speaker = speaker;
        registerListenerOnce();
    }

    /** Clears all peripheral instances and the shared event listener registration. Call on
     *  server/world shutdown so a long-running, automation-heavy server does not accumulate
     *  stale peripheral references across speaker reloads. */
    public static void reset() {
        LIVE.clear();
        listenerRegistered = false;
    }

    private static void registerListenerOnce() {
        if (listenerRegistered) return;
        synchronized (SimplySpeakersPeripheral.class) {
            if (listenerRegistered) return;
            listenerRegistered = true;
            SpeakerEvents.register((type, stateKey, networkName, audioId) -> {
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
            });
        }
    }

    @Nullable
    private ServerLevel serverLevel() {
        return speaker.getLevel() instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    private BlockPos pos() {
        return speaker.getBlockPos();
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
     * Registers this peripheral with CC:Tweaked's Forge provider API.
     * Called from the mod constructor only when CC:Tweaked is present.
     */
    public static void registerProvider() {
        dan200.computercraft.api.ForgeComputerCraftAPI.registerPeripheralProvider((level, pos, side) -> {
            if (level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
                SimplySpeakersPeripheral peripheral = new SimplySpeakersPeripheral(speaker);
                return net.minecraftforge.common.util.LazyOptional.of(() -> peripheral);
            }
            return net.minecraftforge.common.util.LazyOptional.empty();
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

    /** Lua-friendly playback status snapshot. */
    @LuaFunction
    public final Map<String, Object> getStatus() {
        ServerLevel level = serverLevel();
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
    public final double getVolume() {
        var state = serverLevel() != null ? SpeakerApi.getState(serverLevel(), pos()) : null;
        return state != null ? state.getMaxVolume() : 1.0;
    }

    @LuaFunction(mainThread = true)
    public final void setRange(int range) {
        if (!mayAutomationControl()) return;
        SpeakerApi.setRange(serverLevel(), pos(), range);
    }

    @LuaFunction
    public final int getRange() {
        var state = serverLevel() != null ? SpeakerApi.getState(serverLevel(), pos()) : null;
        return state != null ? state.getMaxRange() : 16;
    }

    @LuaFunction(mainThread = true)
    public final void setLooping(boolean looping) {
        if (!mayAutomationControl()) return;
        SpeakerApi.setLooping(serverLevel(), pos(), looping);
    }

    @LuaFunction
    public final boolean isLooping() {
        var state = serverLevel() != null ? SpeakerApi.getState(serverLevel(), pos()) : null;
        return state != null && state.isLooping();
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
    public final List<Object> getPlaylist() {
        List<Object> result = new ArrayList<>();
        var state = serverLevel() != null ? SpeakerApi.getState(serverLevel(), pos()) : null;
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
        return result;
    }

    // ------------------------------------------------------------------
    // Network identity
    // ------------------------------------------------------------------

    @LuaFunction
    public final String getNetworkName() {
        var state = serverLevel() != null ? SpeakerApi.getState(serverLevel(), pos()) : null;
        return state != null ? state.getNetworkName() : "";
    }

    @LuaFunction(mainThread = true)
    public final void setNetworkName(String name) {
        if (!mayAutomationManage()) return;
        SpeakerApi.setNetworkName(serverLevel(), pos(), name);
    }
}
