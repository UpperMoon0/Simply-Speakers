package com.nstut.simplyspeakers.audio;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Metadata for one audio library entry, including 0.8.x organization fields. */
public class AudioFileMetadata {
    private final String uuid;
    private final String originalFilename;
    private final String ownerUUID;
    private final float durationSeconds;

    private String displayName = "";
    private String category = "";
    private List<String> tags = new ArrayList<>();
    private long uploadedAt = 0L;
    private String uploaderName = "";

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

    public String getDisplayName() {
        return displayName != null ? displayName : "";
    }

    /** Name shown in UIs; falls back to the original filename when unset. */
    public String effectiveDisplayName() {
        String name = getDisplayName();
        return name.isEmpty() ? originalFilename : name;
    }

    public AudioFileMetadata withDisplayName(String newDisplayName) {
        AudioFileMetadata copy = copy();
        copy.displayName = sanitize(newDisplayName);
        return copy;
    }

    public String getCategory() {
        return category != null ? category : "";
    }

    public AudioFileMetadata withCategory(String newCategory) {
        AudioFileMetadata copy = copy();
        copy.category = sanitize(newCategory);
        return copy;
    }

    public List<String> getTags() {
        if (tags == null) tags = new ArrayList<>();
        return tags;
    }

    public AudioFileMetadata withTags(List<String> newTags) {
        AudioFileMetadata copy = copy();
        copy.tags = new ArrayList<>();
        if (newTags != null) {
            for (String tag : newTags) {
                String clean = sanitize(tag);
                if (!clean.isEmpty()) copy.tags.add(clean);
            }
        }
        return copy;
    }

    public boolean hasTag(String needle) {
        if (needle == null || needle.isBlank()) return false;
        String lower = needle.toLowerCase(Locale.ROOT).trim();
        for (String tag : getTags()) {
            if (tag.toLowerCase(Locale.ROOT).contains(lower)) return true;
        }
        return effectiveDisplayName().toLowerCase(Locale.ROOT).contains(lower)
                || getCategory().toLowerCase(Locale.ROOT).contains(lower)
                || originalFilename.toLowerCase(Locale.ROOT).contains(lower);
    }

    public long getUploadedAt() {
        return uploadedAt;
    }

    public AudioFileMetadata withUploadedAt(long newUploadedAt) {
        AudioFileMetadata copy = copy();
        copy.uploadedAt = newUploadedAt;
        return copy;
    }

    public String getUploaderName() {
        return uploaderName != null ? uploaderName : "";
    }

    public AudioFileMetadata withUploaderName(String newUploaderName) {
        AudioFileMetadata copy = copy();
        copy.uploaderName = sanitize(newUploaderName);
        return copy;
    }

    public AudioFileMetadata withDuration(float newDurationSeconds) {
        AudioFileMetadata copy = copy();
        AudioFileMetadata replaced = new AudioFileMetadata(copy.uuid, copy.originalFilename, copy.ownerUUID, newDurationSeconds);
        replaced.displayName = copy.displayName;
        replaced.category = copy.category;
        replaced.tags = new ArrayList<>(copy.tags);
        replaced.uploadedAt = copy.uploadedAt;
        replaced.uploaderName = copy.uploaderName;
        return replaced;
    }

    private AudioFileMetadata copy() {
        AudioFileMetadata clone = new AudioFileMetadata(uuid, originalFilename, ownerUUID, durationSeconds);
        clone.displayName = getDisplayName();
        clone.category = getCategory();
        clone.tags = new ArrayList<>(getTags());
        clone.uploadedAt = uploadedAt;
        clone.uploaderName = getUploaderName();
        return clone;
    }

    private static String sanitize(String value) {
        return value != null ? value.trim() : "";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(uuid);
        buf.writeUtf(originalFilename);
        buf.writeBoolean(ownerUUID != null);
        if (ownerUUID != null) {
            buf.writeUtf(ownerUUID);
        }
        buf.writeFloat(durationSeconds);

        boolean hasLibraryData = !getDisplayName().isEmpty() || !getCategory().isEmpty()
                || !getTags().isEmpty() || uploadedAt != 0L || !getUploaderName().isEmpty();
        buf.writeBoolean(hasLibraryData);
        if (hasLibraryData) {
            buf.writeUtf(getDisplayName());
            buf.writeUtf(getCategory());
            buf.writeVarInt(getTags().size());
            for (String tag : getTags()) {
                buf.writeUtf(tag);
            }
            buf.writeLong(uploadedAt);
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
            metadata.displayName = buf.readUtf(256);
            metadata.category = buf.readUtf(64);
            int tagCount = Math.min(buf.readVarInt(), 32);
            List<String> parsedTags = new ArrayList<>();
            for (int i = 0; i < tagCount && buf.readableBytes() > 0; i++) {
                parsedTags.add(buf.readUtf(32));
            }
            metadata.tags = parsedTags;
            metadata.uploadedAt = buf.readLong();
            metadata.uploaderName = buf.readUtf(64);
        }
        return metadata;
    }
}
