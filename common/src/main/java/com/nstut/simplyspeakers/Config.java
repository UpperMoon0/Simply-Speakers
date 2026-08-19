package com.nstut.simplyspeakers;

/**
 * Configuration for Simply Speakers.
 */
public class Config {
    /**
     * The range of the speaker block.
     */
    public static int speakerRange = 64;
    
    /**
     * The minimum range that can be set.
     */
    public static final int MIN_RANGE = 1;
    
    /**
     * The maximum range that can be set.
     */
    public static final int MAX_RANGE = 512;

    /**
     * Whether to disable audio uploads.
     */
    public static boolean disableUpload = false;

    /**
     * The maximum upload size in bytes.
     */
    public static int maxUploadSize = 5 * 1024 * 1024; // 5MB

    /**
     * The minimum upload size that can be set.
     */
    public static final int MIN_UPLOAD_SIZE = 1024; // 1KB

    /**
     * The maximum upload size that can be set.
     */
    public static final int MAX_UPLOAD_SIZE = 100 * 1024 * 1024; // 100MB
    
    /**
     * Whether to enable debug logging for troubleshooting.
     */
    public static boolean debugLogging = false;

    // Local configuration cache for client restoration after disconnecting from a server
    private static int localSpeakerRange = 64;
    private static boolean localDisableUpload = false;
    private static int localMaxUploadSize = 5 * 1024 * 1024;

    /**
     * Updates the local configuration values (read from the local config file)
     * and initializes active values.
     */
    public static void setLocalConfig(int range, boolean disableUp, int maxUpSize) {
        localSpeakerRange = Math.max(MIN_RANGE, Math.min(MAX_RANGE, range));
        localDisableUpload = disableUp;
        localMaxUploadSize = Math.max(MIN_UPLOAD_SIZE, Math.min(MAX_UPLOAD_SIZE, maxUpSize));

        speakerRange = localSpeakerRange;
        disableUpload = localDisableUpload;
        maxUploadSize = localMaxUploadSize;
    }

    /**
     * Applies authoritative configuration received from the server.
     */
    public static void applyServerConfig(int range, boolean disableUp, int maxUpSize) {
        speakerRange = Math.max(MIN_RANGE, Math.min(MAX_RANGE, range));
        disableUpload = disableUp;
        maxUploadSize = Math.max(MIN_UPLOAD_SIZE, Math.min(MAX_UPLOAD_SIZE, maxUpSize));
    }

    /**
     * Restores configuration back to local settings when disconnecting from a remote server.
     */
    public static void restoreLocalConfig() {
        speakerRange = localSpeakerRange;
        disableUpload = localDisableUpload;
        maxUploadSize = localMaxUploadSize;
    }

    public static int getLocalSpeakerRange() {
        return localSpeakerRange;
    }

    public static boolean isLocalDisableUpload() {
        return localDisableUpload;
    }

    public static int getLocalMaxUploadSize() {
        return localMaxUploadSize;
    }
}
