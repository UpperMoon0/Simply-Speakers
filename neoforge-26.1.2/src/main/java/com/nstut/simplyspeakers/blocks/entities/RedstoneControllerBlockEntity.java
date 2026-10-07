package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.*;
import com.nstut.simplyspeakers.control.ControllerAction;
import com.nstut.simplyspeakers.network.SpeakerPacketSecurity;
import com.nstut.simplyspeakers.network.PlaylistControlPacketC2S;
import com.nstut.simplyspeakers.speakers.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import java.util.UUID;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** A linked controller never loads the target chunk or creates an unknown network. */
public class RedstoneControllerBlockEntity extends BlockEntity {
    private String networkId = "";
    private boolean proxyTarget;
    private BlockPos proxyPos = BlockPos.ZERO;
    private ControllerAction action = ControllerAction.TOGGLE;
    private String audioId = "";
    private String audioName = "";
    private boolean restartAnnouncement;
    private float volumeCeiling = 1;
    private UUID owner;
    private UUID actor;
    private int lastSignal;
    private int pulseSignal;
    private boolean initialized;
    private boolean awaitingFirstObservation;
    private String status = "unlinked";

    public RedstoneControllerBlockEntity(BlockPos pos, BlockState state) {
        this(BlockEntityRegistries.REDSTONE_CONTROLLER.get(), pos, state);
    }
    RedstoneControllerBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    public String getNetworkId() { return networkId; }
    public boolean isProxyTarget() { return proxyTarget; }
    public BlockPos getProxyPos() { return proxyPos; }
    public ControllerAction getAction() { return action; }
    public String getAudioId() { return audioId; }
    public String getAudioName() { return audioName; }
    public boolean isRestartAnnouncement() { return restartAnnouncement; }
    public float getVolumeCeiling() { return volumeCeiling; }
    public int getLastSignal() { return lastSignal; }
    public String getStatus() { return status; }
    public void claim(UUID player) { if (owner == null) { owner = player; setChanged(); } }
    public boolean canConfigure(ServerPlayer player) {
        return player != null && !player.isRemoved() && level == player.level()
            && player.distanceToSqr(worldPosition.getX()+0.5, worldPosition.getY()+0.5, worldPosition.getZ()+0.5) <= 64
            && level.hasChunkAt(worldPosition) && level.mayInteract(player, worldPosition)
            && (owner == null || owner.equals(player.getUUID()) || player.level().getServer() != null && player.level().getServer().getPlayerList().isOp(player.nameAndId()));
    }
    public boolean configure(ServerPlayer player, String id, boolean proxy, BlockPos target,
                             ControllerAction job, String clip, boolean restart, float ceiling) {
        if (!canConfigure(player) || job == null || (proxy && !job.supportsProxy())) return false;
        String trimmed = id == null ? "" : id.trim();
        if (trimmed.length() > 64 || trimmed.contains("/") || trimmed.startsWith("internal_")) return reject(player, "invalid_target");
        SpeakerState state = trimmed.isEmpty() ? null : ServerSpeakerRegistry.getSpeakerState(level, "net_" + trimmed);
        if (!trimmed.isEmpty() && (state == null || !SpeakerPermissions.canControl(state, player.getUUID(), player.level().getServer() != null && player.level().getServer().getPlayerList().isOp(player.nameAndId()))))
            return reject(player, "denied");
        if (proxy && !trimmed.isEmpty()) {
            if (!level.hasChunkAt(target) || !(level.getBlockEntity(target) instanceof ProxySpeakerBlockEntity speaker)
                || !trimmed.equals(speaker.getSpeakerId())) return reject(player, "missing_proxy");
        }
        String approvedId = "", approvedName = "";
        if (job == ControllerAction.ANNOUNCEMENT && !trimmed.isEmpty()) {
            var track = SpeakerPacketSecurity.resolveAuthorizedTrack(player, clip);
            // One-shot announcements require server-known duration; URL EOF depends on listeners.
            var files = SimplySpeakers.getAudioFileManager();
            var meta = files == null || track == null ? null : files.getManifest().get(track.audioId());
            if (meta == null || meta.getDurationSeconds() <= 0) return reject(player, "invalid_clip");
            approvedId = track.audioId(); approvedName = track.filename();
        }
        ControllerCoordinator.remove(this);
        claim(player.getUUID());
        actor = player.getUUID();
        networkId = trimmed;
        proxyTarget = proxy; proxyPos = target.immutable(); action = job;
        audioId = approvedId; audioName = approvedName;
        restartAnnouncement = restart;
        volumeCeiling = Float.isFinite(ceiling) ? Math.max(0, Math.min(1, ceiling)) : 1;
        lastSignal = ControllerAction.clamp(level.getBestNeighborSignal(worldPosition));
        initialized = true; awaitingFirstObservation = false;
        status = networkId.isEmpty() ? "unlinked" : "ready";
        ControllerCoordinator.register(this);
        sync();
        return true;
    }
    private boolean reject(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.translatable("gui.simplyspeakers.controller.status." + reason));
        return false;
    }
    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, RedstoneControllerBlockEntity controller) {
        controller.observeSignal(level.getBestNeighborSignal(pos));
    }
    public void observeSignal(int rawSignal) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        int signal = ControllerAction.clamp(rawSignal);
        boolean apply = initialized && !awaitingFirstObservation && action.shouldApply(lastSignal, signal);
        if (!initialized) { initialized = true; }
        awaitingFirstObservation = false;
        if (lastSignal != signal) { lastSignal = signal; sync(); }
        ControllerCoordinator.register(this);
        if (apply && !action.continuous()) { pulseSignal=signal; ControllerCoordinator.pulse(this); }
    }
    @Override public void setRemoved() {
        awaitingFirstObservation = true;
        ControllerCoordinator.remove(this); super.setRemoved();
    }
    boolean coordinatorEligible() {
        if (!(level instanceof ServerLevel) || networkId.isEmpty()) { setStatus("unlinked"); return false; }
        SpeakerState state=ServerSpeakerRegistry.getSpeakerState(level,"net_"+networkId);
        if(state==null) { setStatus("missing_target"); return false; }
        if(actor==null || !SpeakerPermissions.canControl(state,actor,false)) { setStatus("denied"); return false; }
        if(proxyTarget && (!level.hasChunkAt(proxyPos) || !(level.getBlockEntity(proxyPos) instanceof ProxySpeakerBlockEntity proxy)
            || !networkId.equals(proxy.getSpeakerId()))) { setStatus("missing_proxy"); return false; }
        return true;
    }
    void coordinatedReady() { setStatus("ready"); }
    boolean applyCoordinated(ServerLevel level,float value) { return apply(level,value>0?15:0,value); }
    boolean applyPulse(ServerLevel level) { return apply(level,action==ControllerAction.TRACK?pulseSignal:15,null); }
    private boolean apply(ServerLevel serverLevel, int signal, Float requestedVolume) {
        if (networkId.isEmpty()) { setStatus("unlinked"); return false; }
        SpeakerState state = ServerSpeakerRegistry.getSpeakerState(level, "net_" + networkId);
        if (state == null) { setStatus("missing_target"); return false; }
        // Re-check current permissions on every signal; revocation invalidates old links.
        if (actor == null || !SpeakerPermissions.canControl(state, actor, false)) { setStatus("denied"); return false; }
        if (proxyTarget) {
            if (!action.supportsProxy() || !level.hasChunkAt(proxyPos)
                || !(level.getBlockEntity(proxyPos) instanceof ProxySpeakerBlockEntity proxy)
                || !networkId.equals(proxy.getSpeakerId())) { setStatus("missing_proxy"); return false; }
            if (action == ControllerAction.ENABLED) proxy.setControllerPlaying(signal > 0);
            else proxy.setControllerVolume(requestedVolume != null ? requestedVolume : ControllerAction.volume(signal, volumeCeiling));
            setStatus("ready"); return true;
        }
        String key = ServerSpeakerRegistry.getRegistryKey(level, "net_" + networkId);
        var server = serverLevel.getServer();
        boolean success = switch (action) {
            case TOGGLE -> ServerSpeakerControlService.togglePause(server, serverLevel, key);
            case TRACK -> ServerSpeakerControlService.playlistControl(server,serverLevel,key,PlaylistControlPacketC2S.OP_SELECT_INDEX,ControllerAction.trackIndex(signal,state.getPlaylist().size()),true,"","");
            case NEXT -> ServerSpeakerControlService.next(server, serverLevel, key);
            case PREVIOUS -> ServerSpeakerControlService.previous(server, serverLevel, key);
            case STOP -> ServerSpeakerControlService.stop(server, serverLevel, key);
            case RESTART -> ServerSpeakerControlService.restart(server, serverLevel, key);
            case ENABLED -> signal > 0 ? ServerSpeakerControlService.play(server, serverLevel, key)
                                      : (!state.isPlaying() || state.isPaused() || ServerSpeakerControlService.pause(server, serverLevel, key));
            case VOLUME -> ServerSpeakerControlService.setControllerVolume(server, serverLevel, key, requestedVolume != null ? requestedVolume : ControllerAction.volume(signal, volumeCeiling));
            case ANNOUNCEMENT -> ServerSpeakerControlService.playAnnouncement(server, serverLevel, key, audioId, actor, restartAnnouncement);
        };
        setStatus(success ? "ready" : "unavailable");
        return success;
    }
    private void setStatus(String value) { if (!status.equals(value)) { status = value; sync(); } }

@Override protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        networkId = tag.getStringOr("Network", "");
        proxyTarget = tag.getBooleanOr("Proxy", false);
        proxyPos = new BlockPos(tag.getIntOr("TargetX", 0), tag.getIntOr("TargetY", 0), tag.getIntOr("TargetZ", 0));
        action = ControllerAction.byId(tag.getStringOr("Action", ""));
        audioId = tag.getStringOr("Audio", ""); audioName = tag.getStringOr("AudioName", "");
        restartAnnouncement = tag.getBooleanOr("Restart", false); volumeCeiling = tag.getFloatOr("Ceiling", 1);
        lastSignal = ControllerAction.clamp(tag.getIntOr("Signal", 0));
        initialized = tag.getBooleanOr("Initialized", false); awaitingFirstObservation = true;
        status = tag.getStringOr("Status", "unlinked");
        try { owner = UUID.fromString(tag.getStringOr("Owner", "")); } catch (IllegalArgumentException ex) { owner = null; }
        try { actor = UUID.fromString(tag.getStringOr("Actor", "")); } catch (IllegalArgumentException ex) { actor = null; }
    }
    @Override protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putString("Network", networkId); tag.putBoolean("Proxy", proxyTarget);
        tag.putInt("TargetX", proxyPos.getX()); tag.putInt("TargetY", proxyPos.getY()); tag.putInt("TargetZ", proxyPos.getZ());
        tag.putString("Action", action.id()); tag.putString("Audio", audioId); tag.putString("AudioName", audioName);
        tag.putBoolean("Restart", restartAnnouncement); tag.putFloat("Ceiling", volumeCeiling);
        tag.putInt("Signal", lastSignal); tag.putBoolean("Initialized", initialized); tag.putString("Status", status);
        if (owner != null) tag.putString("Owner", owner.toString());
        if (actor != null) tag.putString("Actor", actor.toString());
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        return saveCustomOnly(lookup);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
