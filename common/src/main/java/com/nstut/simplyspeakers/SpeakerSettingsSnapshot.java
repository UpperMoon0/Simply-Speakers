package com.nstut.simplyspeakers;

import java.io.*;

/** Authoritative settings, including effective controller gain, for client replicas. */
public record SpeakerSettingsSnapshot(String name, RedstoneMode redstone, float volume,
        float effectiveVolume, int range, float dropoff, float directionality, int cone, float rear) {
    public static final int MAX_BYTES = 65580;
    public static SpeakerSettingsSnapshot capture(SpeakerState state) {
        return new SpeakerSettingsSnapshot(state.getNetworkName(),state.getRedstoneMode(),
            state.getConfiguredMaxVolume(),state.getMaxVolume(),state.getMaxRange(),state.getAudioDropoff(),
            state.getDirectionality(),state.getConeAngleDegrees(),state.getRearAttenuation());
    }
    public void apply(SpeakerState state) {
        state.setNetworkName(name);state.setRedstoneMode(redstone);state.setMaxVolume(volume);
        state.setControllerVolume(effectiveVolume);state.setMaxRange(range);state.setAudioDropoff(dropoff);
        state.setDirectionality(directionality);state.setConeAngleDegrees(cone);state.setRearAttenuation(rear);
    }
    public byte[] encode() {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            out.writeUTF(name);out.writeByte(redstone.ordinal());out.writeFloat(volume);out.writeFloat(effectiveVolume);
            out.writeInt(range);out.writeFloat(dropoff);out.writeFloat(directionality);out.writeInt(cone);out.writeFloat(rear);
            return bytes.toByteArray();
        } catch(IOException e) {throw new IllegalArgumentException(e);}
    }
    public static SpeakerSettingsSnapshot decode(byte[] bytes) {
        if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Settings snapshot size");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(bytes));String name=in.readUTF();int mode=in.readUnsignedByte();
            if(mode>=RedstoneMode.values().length)throw new IllegalArgumentException("Redstone mode");
            var result=new SpeakerSettingsSnapshot(name,RedstoneMode.values()[mode],in.readFloat(),in.readFloat(),
                in.readInt(),in.readFloat(),in.readFloat(),in.readInt(),in.readFloat());
            if(in.available()!=0)throw new IllegalArgumentException("Trailing settings data");return result;
        } catch(IOException e) {throw new IllegalArgumentException(e);}
    }
}
