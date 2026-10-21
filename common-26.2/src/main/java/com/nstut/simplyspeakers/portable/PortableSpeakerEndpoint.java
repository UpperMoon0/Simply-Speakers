package com.nstut.simplyspeakers.portable;

import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.audio.DirectionalAudio;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.speakers.ServerEmitter;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import com.nstut.simplyspeakers.speakers.SpeakerLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/**
 * Inventory-backed adapter for the existing speaker controls. Its position is a
 * session routing token, never a real block, and no chunk or redstone I/O occurs.
 */
public final class PortableSpeakerEndpoint extends SpeakerBlockEntity {
    public static final int MAX_SPEAKER_ID_LENGTH = 128;
    private final UUID identity;
    private String portableSpeakerId;

    public PortableSpeakerEndpoint(UUID identity, BlockPos token, String speakerId) {
        super(token, BlockRegistries.SPEAKER.get().defaultBlockState());
        this.identity = identity;
        this.portableSpeakerId = speakerId == null ? "" : speakerId.trim();
    }

    PortableSpeakerEndpoint(UUID identity, BlockPos token, String speakerId,
                            BlockEntityType<?> type, BlockState state) {
        super(type, token, state);
        this.identity = identity;
        this.portableSpeakerId = speakerId == null ? "" : speakerId.trim();
    }

    public UUID getIdentity() { return identity; }
    @Override protected boolean isPhysicalSpeaker() { return false; }
    @Override public void setChanged() { PortableSpeakerManager.persist(this); }
    @Override public String getSpeakerId() { return portableSpeakerId; }
    @Override public String getStateKey() {
        return SpeakerLink.isLinkableId(portableSpeakerId) ? "net_" + portableSpeakerId : "portable_" + identity;
    }
    @Override public void setSpeakerIdClient(String speakerId) {
        portableSpeakerId = speakerId == null ? "" : speakerId.trim();
    }

    @Override public void ensureServerRegistration() {
        if (level == null || level.isClientSide()) return;
        ServerSpeakerRegistry.registerSpeaker(level, getBlockPos(), getStateKey());
        updateEmitterSnapshot();
    }

    @Override public void setSpeakerId(String speakerId) {
        String next = speakerId == null ? "" : speakerId.trim();
        if (next.length() > MAX_SPEAKER_ID_LENGTH || next.equals(portableSpeakerId)) return;
        if (level == null || level.isClientSide()) { portableSpeakerId = next; return; }
        ensureServerRegistration();
        getSpeakerState();
        String oldKey = getStateKey();
        detachEmitterForPowerOff();
        portableSpeakerId = next;
        ServerSpeakerRegistry.updateSpeakerKey(level, getBlockPos(), oldKey, getStateKey());
        PortableSpeakerManager.linkChanged(this);
        updateEmitterSnapshot();
    }

    @Override public void updateEmitterSnapshot() {
        if (level == null || level.isClientSide()) return;
        SpeakerState state = getSpeakerState();
        if (state == null) return;
        var snapshot = PortableSpeakerManager.snapshot(level, getBlockPos());
        byte facing = (byte) Direction.NORTH.ordinal();
        if (snapshot != null) facing = (byte) Direction.fromYRot(snapshot.yaw()).ordinal();
        DirectionalAudio.Extras extras = state.getDirectionality() > 0.001f
                ? new DirectionalAudio.Extras(state.getDirectionality(), state.getConeAngleDegrees(), state.getRearAttenuation(), facing)
                : null;
        ServerSpeakerRegistry.upsertEmitter(new ServerEmitter(location(), getStateKey(), state.getMaxRange(),
                state.getMaxVolume(), state.getAudioDropoff(), false, true, extras));
    }

    public SpeakerLocation location() {
        return new SpeakerLocation(ServerSpeakerRegistry.getDimension(level), getBlockPos().getX(),
                getBlockPos().getY(), getBlockPos().getZ());
    }
}
