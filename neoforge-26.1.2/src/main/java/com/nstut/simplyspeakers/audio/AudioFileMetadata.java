package com.nstut.simplyspeakers.audio;

import net.minecraft.network.FriendlyByteBuf;

public class AudioFileMetadata {
    private final String uuid;
    private final String originalFilename;
    private final String ownerUUID;

    public AudioFileMetadata(String uuid, String originalFilename) {
        this(uuid, originalFilename, null);
    }

    public AudioFileMetadata(String uuid, String originalFilename, String ownerUUID) {
        this.uuid = uuid;
        this.originalFilename = originalFilename;
        this.ownerUUID = ownerUUID;
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

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(uuid);
        buf.writeUtf(originalFilename);
        buf.writeBoolean(ownerUUID != null);
        if (ownerUUID != null) {
            buf.writeUtf(ownerUUID);
        }
    }

    public static AudioFileMetadata decode(FriendlyByteBuf buf) {
        String uuid = buf.readUtf();
        String originalFilename = buf.readUtf();
        String ownerUUID = buf.readBoolean() ? buf.readUtf() : null;
        return new AudioFileMetadata(uuid, originalFilename, ownerUUID);
    }
}

