package com.nstut.simplyspeakers.audio;

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
}