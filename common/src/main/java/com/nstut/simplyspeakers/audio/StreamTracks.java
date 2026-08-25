package com.nstut.simplyspeakers.audio;

import java.net.URI;
import java.util.Locale;

/**
 * Validation helpers for direct HTTP(S) internet audio streams. 0.8.x supports
 * only direct file-style stream URLs; no playlist scraping or transcoding.
 */
public final class StreamTracks {

    public static final int MAX_URL_LENGTH = 2048;
    private static final String[] SUPPORTED_EXTENSIONS = {".mp3", ".wav"};

    private StreamTracks() {
    }

    /** True when the id looks like a supported direct HTTP(S) audio URL. */
    public static boolean isHttpAudioUrl(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_URL_LENGTH) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x20 || c == 0x7F) return false;
        }
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            return host != null && !host.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    /** True when the URL path ends with an extension the client can decode. */
    public static boolean hasSupportedExtension(String url) {
        if (!isHttpAudioUrl(url)) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }
}
