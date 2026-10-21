package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.testing.LivePlaybackServerProbe;
import java.util.concurrent.TimeUnit;

/** Independent uncached request-to-decoded-OpenAL latency in each real client. */
final class LiveDownloadClientProbe {
    private static String audioId, networkKey;
    private static long requested, downloaded;
    private static boolean passed;
    private LiveDownloadClientProbe() { }
    static void requested(String id, String filename) {
        if (!LivePlaybackServerProbe.enabled() || !"verification.wav".equals(filename) || requested != 0) return;
        audioId = id;
        requested = System.nanoTime();
    }
    static void downloaded(String id, long bytes) {
        if (!id.equals(audioId)) return;
        if (bytes != 60L * 1024 * 1024)
            throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL download fixture size " + bytes);
        downloaded = System.nanoTime();
    }
    static void playing(String id, String key) { if (id.equals(audioId)) networkKey = key; }
    static void tick() {
        if (requested == 0 || passed) return;
        long now = System.nanoTime();
        if (now - requested > TimeUnit.SECONDS.toNanos(30))
            throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL uncached download-to-play exceeded 30 seconds");
        if (downloaded == 0 || networkKey == null) return;
        var snapshot = ClientAudioPlayer.verificationSnapshot(networkKey);
        if (!snapshot.playing() || snapshot.decodedBytes() <= 0 || snapshot.sources() != 1) return;
        passed = true;
        System.out.println("SIMPLYSPEAKERS_DOWNLOAD_PLAY_PASS bytes=62914560 role="
                + System.getProperty("simplyspeakers.livePlaybackRole", "carrier")
                + " download-ms=" + TimeUnit.NANOSECONDS.toMillis(downloaded - requested)
                + " request-to-decoded-play-ms=" + TimeUnit.NANOSECONDS.toMillis(now - requested));
    }
}
