package com.nstut.simplyspeakers.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure library-organization data for one audio entry: display name, category,
 * tags, upload timestamp, and uploader name. Owned by version-module
 * {@code AudioFileMetadata}, which delegates persistence and wire encoding.
 */
public class AudioLibraryInfo {
    private String displayName = "";
    private String category = "";
    private List<String> tags = new ArrayList<>();
    private long uploadedAt = 0L;
    private String uploaderName = "";

    public String getDisplayName() {
        return displayName != null ? displayName : "";
    }

    /** Name shown in UIs; falls back to the original filename when unset. */
    public String effectiveDisplayName(String originalFilename) {
        String name = getDisplayName();
        return name.isEmpty() ? originalFilename : name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = sanitize(displayName);
    }

    public String getCategory() {
        return category != null ? category : "";
    }

    public void setCategory(String category) {
        this.category = sanitize(category);
    }

    public List<String> getTags() {
        if (tags == null) tags = new ArrayList<>();
        return tags;
    }

    /** Replaces tags with trimmed, de-blanked entries. */
    public void setTags(List<String> newTags) {
        this.tags = new ArrayList<>();
        if (newTags != null) {
            for (String tag : newTags) {
                String clean = sanitize(tag);
                if (!clean.isEmpty()) this.tags.add(clean);
            }
        }
    }

    /**
     * Case-insensitive text search across tags, display name, category, and
     * the original filename.
     */
    public boolean matchesQuery(String needle, String originalFilename) {
        if (needle == null || needle.isBlank()) return false;
        String lower = needle.toLowerCase(Locale.ROOT).trim();
        for (String tag : getTags()) {
            if (tag.toLowerCase(Locale.ROOT).contains(lower)) return true;
        }
        return effectiveDisplayName(originalFilename).toLowerCase(Locale.ROOT).contains(lower)
                || getCategory().toLowerCase(Locale.ROOT).contains(lower)
                || (originalFilename != null && originalFilename.toLowerCase(Locale.ROOT).contains(lower));
    }

    public long getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(long uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getUploaderName() {
        return uploaderName != null ? uploaderName : "";
    }

    public void setUploaderName(String uploaderName) {
        this.uploaderName = sanitize(uploaderName);
    }

    /** True when any organizational field differs from its default. */
    public boolean hasLibraryData() {
        return !getDisplayName().isEmpty() || !getCategory().isEmpty()
                || !getTags().isEmpty() || uploadedAt != 0L || !getUploaderName().isEmpty();
    }

    public AudioLibraryInfo copy() {
        AudioLibraryInfo clone = new AudioLibraryInfo();
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
}
