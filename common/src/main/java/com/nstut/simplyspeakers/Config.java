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
     * Whether clients may stream remote HTTP(S) audio URLs through this server.
     * Independent of {@link #disableUpload}; when false the server refuses to
     * select URL tracks and clients refuse to open remote HTTP connections.
     */
    public static boolean allowRemoteStreams = false;

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
    public static final int MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB hard limit
    
    /**
     * Whether to enable debug logging for troubleshooting.
     */
    public static boolean debugLogging = false;

    /**
     * Maximum client-side audio cache size in bytes before LRU eviction.
     */
    public static long clientCacheLimitBytes = 500L * 1024L * 1024L; // 500MB

    // Local configuration cache for client restoration after disconnecting from a server
    private static int localSpeakerRange = 64;
    private static boolean localDisableUpload = false;
    private static boolean localAllowRemoteStreams = false;
    private static int localMaxUploadSize = 5 * 1024 * 1024;
    private static volatile boolean isRemoteServerActive = false;

    /**
     * Updates the local configuration values (read from the local config file)
     * and initializes active values if not connected to a remote server.
     */
    public static void setLocalConfig(int range, boolean disableUp, int maxUpSize) {
        setLocalConfig(range, disableUp, maxUpSize, localAllowRemoteStreams);
    }

    /**
     * Updates the local configuration values (read from the local config file)
     * and initializes active values if not connected to a remote server.
     */
    public static void setLocalConfig(int range, boolean disableUp, int maxUpSize, boolean allowRemote) {
        localSpeakerRange = Math.max(MIN_RANGE, Math.min(MAX_RANGE, range));
        localDisableUpload = disableUp;
        localAllowRemoteStreams = allowRemote;
        localMaxUploadSize = Math.max(MIN_UPLOAD_SIZE, Math.min(MAX_UPLOAD_SIZE, maxUpSize));

        if (!isRemoteServerActive) {
            speakerRange = localSpeakerRange;
            disableUpload = localDisableUpload;
            allowRemoteStreams = localAllowRemoteStreams;
            maxUploadSize = localMaxUploadSize;
        }
    }

    /**
     * Applies authoritative configuration received from the server.
     */
    public static void applyServerConfig(int range, boolean disableUp, int maxUpSize) {
        applyServerConfig(range, disableUp, maxUpSize, allowRemoteStreams);
    }

    /**
     * Applies authoritative configuration received from the server.
     */
    public static void applyServerConfig(int range, boolean disableUp, int maxUpSize, boolean allowRemote) {
        isRemoteServerActive = true;
        speakerRange = Math.max(MIN_RANGE, Math.min(MAX_RANGE, range));
        disableUpload = disableUp;
        allowRemoteStreams = allowRemote;
        maxUploadSize = Math.max(MIN_UPLOAD_SIZE, Math.min(MAX_UPLOAD_SIZE, maxUpSize));
    }

    /**
     * Restores configuration back to local settings when disconnecting from a remote server.
     */
    public static void restoreLocalConfig() {
        isRemoteServerActive = false;
        speakerRange = localSpeakerRange;
        disableUpload = localDisableUpload;
        allowRemoteStreams = localAllowRemoteStreams;
        maxUploadSize = localMaxUploadSize;
    }

    public static boolean isRemoteServerActive() {
        return isRemoteServerActive;
    }

    /** Static policy query for packet/service code deciding whether URL tracks are permitted. */
    public static boolean isRemoteStreamingAllowed() {
        return allowRemoteStreams;
    }

    public static int getLocalSpeakerRange() {
        return localSpeakerRange;
    }

    public static boolean isLocalDisableUpload() {
        return localDisableUpload;
    }

    public static boolean isLocalAllowRemoteStreams() {
        return localAllowRemoteStreams;
    }

    public static int getLocalMaxUploadSize() {
        return localMaxUploadSize;
    }
}
