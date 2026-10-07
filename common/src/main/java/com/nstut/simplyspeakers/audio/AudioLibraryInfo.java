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
    public static final int MAX_DISPLAY_NAME_LENGTH = 256;
    private String displayName = "";
    private String category = "";
    private List<String> tags = new ArrayList<>();
    private long uploadedAt = 0L;
    private String uploaderName = "";

    public String getDisplayName() {
        return bounded(displayName, MAX_DISPLAY_NAME_LENGTH);
    }

    /** Name shown in UIs; falls back to the original filename when unset. */
    public String effectiveDisplayName(String originalFilename) {
        String name = getDisplayName();
        return name.isEmpty() ? originalFilename : name;
    }

    public void setDisplayName(String displayName) {
        String clean = sanitize(displayName);
        if (clean.length() > MAX_DISPLAY_NAME_LENGTH) throw new IllegalArgumentException("Audio name exceeds 256 characters");
        this.displayName = clean;
    }

    public String getCategory() {
        return bounded(category, 64);
    }

    public void setCategory(String category) {
        this.category = bounded(sanitize(category), 64);
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
        return bounded(uploaderName, 64);
    }

    public void setUploaderName(String uploaderName) {
        this.uploaderName = bounded(sanitize(uploaderName), 64);
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

    /** Gson bypasses setters, so legacy persisted data must also obey wire limits. */
    private static String bounded(String value, int limit) {
        if (value == null) return "";
        if (value.length() <= limit) return value;
        int end = limit;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }
}
