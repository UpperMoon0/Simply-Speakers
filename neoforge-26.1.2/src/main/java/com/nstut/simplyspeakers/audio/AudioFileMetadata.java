package com.nstut.simplyspeakers.audio;

import net.minecraft.network.FriendlyByteBuf;

/** Metadata for one audio library entry, including 0.8.x organization fields. */
public class AudioFileMetadata {
    private final String uuid;
    private final String originalFilename;
    private final String ownerUUID;
    private final float durationSeconds;

    /** Library organization data; nested Gson object keeps the manifest format stable. */
    private AudioLibraryInfo library = new AudioLibraryInfo();

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

    public String getUuid() { return uuid; }
    public String getOriginalFilename() { return originalFilename; }
    public String getOwnerUUID() { return ownerUUID; }
    public float getDurationSeconds() { return durationSeconds; }

    public String getDisplayName() { return library().getDisplayName(); }
    public String effectiveDisplayName() { return library().effectiveDisplayName(originalFilename); }
    public AudioFileMetadata withDisplayName(String newDisplayName) {
        AudioFileMetadata copy = copy();
        copy.library().setDisplayName(newDisplayName);
        return copy;
    }

    public String getCategory() { return library().getCategory(); }
    public AudioFileMetadata withCategory(String newCategory) {
        AudioFileMetadata copy = copy();
        copy.library().setCategory(newCategory);
        return copy;
    }

    public java.util.List<String> getTags() { return library().getTags(); }
    public AudioFileMetadata withTags(java.util.List<String> newTags) {
        AudioFileMetadata copy = copy();
        copy.library().setTags(newTags);
        return copy;
    }

    public boolean hasTag(String needle) { return library().matchesQuery(needle, originalFilename); }

    public long getUploadedAt() { return library().getUploadedAt(); }
    public AudioFileMetadata withUploadedAt(long newUploadedAt) {
        AudioFileMetadata copy = copy();
        copy.library().setUploadedAt(newUploadedAt);
        return copy;
    }

    public String getUploaderName() { return library().getUploaderName(); }
    public AudioFileMetadata withUploaderName(String newUploaderName) {
        AudioFileMetadata copy = copy();
        copy.library().setUploaderName(newUploaderName);
        return copy;
    }

    public AudioFileMetadata withDuration(float newDurationSeconds) {
        AudioFileMetadata replaced = new AudioFileMetadata(uuid, originalFilename, ownerUUID, newDurationSeconds);
        replaced.library = library.copy();
        return replaced;
    }

    private AudioLibraryInfo library() {
        if (library == null) library = new AudioLibraryInfo(); // Gson may omit the nested object
        return library;
    }

    private AudioFileMetadata copy() {
        AudioFileMetadata clone = new AudioFileMetadata(uuid, originalFilename, ownerUUID, durationSeconds);
        clone.library = library().copy();
        return clone;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(uuid);
        buf.writeUtf(originalFilename);
        buf.writeBoolean(ownerUUID != null);
        if (ownerUUID != null) {
            buf.writeUtf(ownerUUID);
        }
        buf.writeFloat(durationSeconds);

        boolean hasLibraryData = library().hasLibraryData();
        buf.writeBoolean(hasLibraryData);
        if (hasLibraryData) {
            buf.writeUtf(getDisplayName());
            buf.writeUtf(getCategory());
            buf.writeVarInt(getTags().size());
            for (String tag : getTags()) {
                buf.writeUtf(tag);
            }
            buf.writeLong(getUploadedAt());
            buf.writeUtf(getUploaderName());
        }
    }

    public static AudioFileMetadata decode(FriendlyByteBuf buf) {
        String uuid = buf.readUtf();
        String originalFilename = buf.readUtf();
        String ownerUUID = buf.readBoolean() ? buf.readUtf() : null;
        float durationSeconds = buf.readableBytes() >= 4 ? buf.readFloat() : 0.0f;
        AudioFileMetadata metadata = new AudioFileMetadata(uuid, originalFilename, ownerUUID, durationSeconds);

        if (buf.readableBytes() >= 1 && buf.readBoolean()) {
            metadata.library().setDisplayName(buf.readUtf(256));
            metadata.library().setCategory(buf.readUtf(64));
            int tagCount = Math.min(buf.readVarInt(), 32);
            java.util.List<String> parsedTags = new java.util.ArrayList<>();
            for (int i = 0; i < tagCount && buf.readableBytes() > 0; i++) {
                parsedTags.add(buf.readUtf(32));
            }
            metadata.library().setTags(parsedTags);
            metadata.library().setUploadedAt(buf.readLong());
            metadata.library().setUploaderName(buf.readUtf(64));
        }
        return metadata;
    }
}
