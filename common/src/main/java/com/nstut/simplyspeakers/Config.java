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
}
