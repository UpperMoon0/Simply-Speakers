package com.nstut.simplyspeakers.audio;

import net.minecraft.network.FriendlyByteBuf;

public class AudioFileMetadata {
    private final String uuid;
    private final String originalFilename;

    public AudioFileMetadata(String uuid, String originalFilename) {
        this.uuid = uuid;
        this.originalFilename = originalFilename;
    }

    public String getUuid() {
        return uuid;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(uuid);
        buf.writeUtf(originalFilename);
    }

    public static AudioFileMetadata decode(FriendlyByteBuf buf) {
        return new AudioFileMetadata(buf.readUtf(), buf.readUtf());
    }
}