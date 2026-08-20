package com.nstut.simplyspeakers.audio;

import net.minecraft.network.FriendlyByteBuf;

public class AudioFileMetadata {
    private final String uuid;
    private final String originalFilename;
    private final String ownerUUID;
    private final float durationSeconds;

    public AudioFileMetadata(String uuid, String originalFilename) {
        this(uuid, originalFilename, null, 0.0f);
    }

    public AudioFileMetadata(String uuid, String originalFilename, String ownerUUID) {
        this(uuid, originalFilename, ownerUUID, 0.0f);
    }

    public AudioFileMetadata(String uuid, String originalFilename, String ownerUUID, float durationSeconds) {
        this.uuid = uuid;
        this.originalFilename = originalFilename;
        this.ownerUUID = ownerUUID;
        this.durationSeconds = durationSeconds;
    }

    public String getUuid() {
        return uuid;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getOwnerUUID() {
        return ownerUUID;
    }

    public float getDurationSeconds() {
        return durationSeconds;
    }

    public AudioFileMetadata withDuration(float newDurationSeconds) {
        return new AudioFileMetadata(this.uuid, this.originalFilename, this.ownerUUID, newDurationSeconds);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(uuid);
        buf.writeUtf(originalFilename);
        buf.writeBoolean(ownerUUID != null);
        if (ownerUUID != null) {
            buf.writeUtf(ownerUUID);
        }
        buf.writeFloat(durationSeconds);
    }

    public static AudioFileMetadata decode(FriendlyByteBuf buf) {
        String uuid = buf.readUtf();
        String originalFilename = buf.readUtf();
        String ownerUUID = buf.readBoolean() ? buf.readUtf() : null;
        float durationSeconds = buf.readableBytes() >= 4 ? buf.readFloat() : 0.0f;
        return new AudioFileMetadata(uuid, originalFilename, ownerUUID, durationSeconds);
    }
}
