package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamTracksTest {
    @Test
    void acceptsDirectHttpAudioUrls() {
        assertTrue(StreamTracks.isHttpAudioUrl("https://example.com/radio.mp3"));
        assertTrue(StreamTracks.isHttpAudioUrl("http://example.com:8000/stream.wav"));
        assertTrue(StreamTracks.hasSupportedExtension("https://example.com/radio.mp3"));
    }

    @Test
    void rejectsNonHttpSchemesAndMalformedInput() {
        assertFalse(StreamTracks.isHttpAudioUrl(null));
        assertFalse(StreamTracks.isHttpAudioUrl(""));
        assertFalse(StreamTracks.isHttpAudioUrl("ftp://example.com/song.mp3"));
        assertFalse(StreamTracks.isHttpAudioUrl("https://"));
        assertFalse(StreamTracks.isHttpAudioUrl("https://exa mple.com/a.mp3"));
        assertFalse(StreamTracks.isHttpAudioUrl("file:///home/user/music.mp3"));
    }

    @Test
    void rejectsUnsupportedExtensionsAndLengths() {
        assertFalse(StreamTracks.hasSupportedExtension("https://example.com/radio.ogg"));
        assertFalse(StreamTracks.hasSupportedExtension("https://example.com/radio"));
        String tooLong = "https://example.com/" + "a".repeat(3000) + ".mp3";
        assertFalse(StreamTracks.isHttpAudioUrl(tooLong));
    }


    @Test
    void acceptsQueryStringsAndCaseInsensitiveSchemes() {
        assertTrue(StreamTracks.isHttpAudioUrl("https://example.com/live.mp3?token=abc"));
        assertTrue(StreamTracks.isHttpAudioUrl("HTTPS://EXAMPLE.COM/A.MP3"));
        assertTrue(StreamTracks.hasSupportedExtension("https://example.com/a.MP3#frag".replace("#frag", "")));
        assertTrue(StreamTracks.hasSupportedExtension("https://example.com/STREAM.WAV?nocache=1"));
    }

    @Test
    void rejectsControlCharacters() {
        assertFalse(StreamTracks.isHttpAudioUrl("https://example.com/a\u0000.mp3"));
        assertFalse(StreamTracks.isHttpAudioUrl("https://example.com/a b.mp3"));
    }

    @Test
    void extensionDetectionIgnoresQueryAndFragment() {
        assertTrue(StreamTracks.hasSupportedExtension("https://example.com/song.mp3?token=abc"));
        assertTrue(StreamTracks.hasSupportedExtension("https://example.com/song.mp3#frag"));
        assertTrue(StreamTracks.hasSupportedExtension("https://example.com/song.mp3?token=abc&x=1#frag"));
        assertTrue(StreamTracks.hasSupportedExtension("https://example.com/SONG.MP3?token=abc"));
        assertFalse(StreamTracks.hasSupportedExtension("https://example.com/play?file=song.mp3"));
        assertFalse(StreamTracks.hasSupportedExtension("https://example.com/watch?v=1#song.wav"));
    }

    @Test
    void mp3UrlDetectionIgnoresQueryAndFragment() {
        assertTrue(StreamTracks.isMp3Url("https://example.com/song.mp3?token=abc"));
        assertTrue(StreamTracks.isMp3Url("https://example.com/song.MP3#frag"));
        assertFalse(StreamTracks.isMp3Url("https://example.com/song.wav?token=abc"));
        assertFalse(StreamTracks.isMp3Url("https://example.com/play?file=song.mp3"));
    }

    @Test
    void sanitizesStartPosition() {
        assertEquals(0.0f, StreamTracks.sanitizeStartPosition(Float.NaN), 0.0f);
        assertEquals(0.0f, StreamTracks.sanitizeStartPosition(Float.POSITIVE_INFINITY), 0.0f);
        assertEquals(0.0f, StreamTracks.sanitizeStartPosition(Float.NEGATIVE_INFINITY), 0.0f);
        assertEquals(0.0f, StreamTracks.sanitizeStartPosition(-12.5f), 0.0f);
        assertEquals(0.0f, StreamTracks.sanitizeStartPosition(0.0f), 0.0f);
        assertEquals(12.5f, StreamTracks.sanitizeStartPosition(12.5f), 0.0f);
    }

    @Test
    void acceptsPublicRemoteStreamUrls() {
        assertTrue(StreamTracks.isRemoteStreamUrlAllowed("https://example.com/radio.mp3", false));
        assertTrue(StreamTracks.isRemoteStreamUrlAllowed("http://cdn.example.com:8000/stream.wav", false));
        // Public literal IPs are allowed without DNS
        assertTrue(StreamTracks.isRemoteStreamUrlAllowed("https://93.184.216.34/song.mp3", false));
        assertTrue(StreamTracks.isRemoteStreamUrlAllowed("http://[2001:db8::10]:8000/stream.mp3", false));
    }

    @Test
    void rejectsLoopbackAndLocalhostTargets() {
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://127.0.0.1/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://127.254.9.9/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://localhost/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://localhost:8000/stream.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("https://music.localhost/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://0.0.0.0/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://0.1.2.3/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://[::1]/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://[::]/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://[0:0:0:0:0:0:0:1]/song.mp3", false));
    }

    @Test
    void rejectsPrivateAndLinkLocalTargets() {
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://10.1.2.3/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://172.16.0.1/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://172.31.255.255/song.mp3", false));
        assertTrue(StreamTracks.isRemoteStreamUrlAllowed("http://172.32.0.1/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://192.168.1.50/stream.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://169.254.169.254/latest/meta-data", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://[fe80::1]/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://[febf::a]/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://[fc00::1]/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://[fdff::1]/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://[::ffff:10.0.0.1]/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://[::ffff:169.254.169.254]/song.mp3", false));
        // Decimal and octal encodings of loopback must never slip through
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://2130706433/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://0177.0.0.1/song.mp3", false));
    }

    @Test
    void rejectsReservedInternalHostnames() {
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://myhost.local/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://myhost.internal/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("http://metadata.local/", false));
    }

    @Test
    void rejectsNonHttpUrlHostsAndMalformed() {
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("ftp://example.com/song.mp3", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("file:///etc/passwd", false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed(null, false));
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("", false));
        assertFalse(StreamTracks.isHostAllowed(null, false));
        assertFalse(StreamTracks.isHostAllowed("", false));
        assertFalse(StreamTracks.isHostAllowed("[::1]", false));
        assertFalse(StreamTracks.isHostAllowed("127.0.0.1", false));
        assertTrue(StreamTracks.isHostAllowed("example.com", false));
    }

    private static StreamTracks.DnsResolver resolving(java.net.InetAddress... addresses) {
        return host -> addresses;
    }

    private static java.net.InetAddress address(byte... octets) throws Exception {
        return java.net.InetAddress.getByAddress(octets);
    }

    @Test
    void dnsResolutionFailureFailsClosed() {
        StreamTracks.DnsResolver unknownHost = host -> {
            throw new java.net.UnknownHostException(host);
        };
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("https://example.com/radio.mp3", true, unknownHost));
        assertFalse(StreamTracks.isHostAllowed("example.com", true, unknownHost));
        // The no-DNS preflight variant must stay permissive for the same host.
        assertTrue(StreamTracks.isHostAllowed("example.com", false));
    }

    @Test
    void dnsResolverErrorsFailClosed() {
        StreamTracks.DnsResolver broken = host -> {
            throw new RuntimeException("resolver unavailable");
        };
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("https://example.com/radio.mp3", true, broken));
        assertFalse(StreamTracks.isHostAllowed("example.com", true, broken));
    }

    @Test
    void nullAndEmptyResolutionResultsFailClosed() {
        StreamTracks.DnsResolver nullResult = host -> null;
        StreamTracks.DnsResolver emptyResult = host -> new java.net.InetAddress[0];
        assertFalse(StreamTracks.isHostAllowed("example.com", true, nullResult));
        assertFalse(StreamTracks.isHostAllowed("example.com", true, emptyResult));
    }

    @Test
    void resolvedPrivateAddressIsRejected() throws Exception {
        java.net.InetAddress priv = address((byte) 10, (byte) 0, (byte) 0, (byte) 5);
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("https://example.com/radio.mp3", true, resolving(priv)));
        // A single private address in the answer set poisons the whole host.
        java.net.InetAddress pub = address((byte) 93, (byte) 184, (byte) 216, (byte) 34);
        assertFalse(StreamTracks.isRemoteStreamUrlAllowed("https://example.com/radio.mp3", true, resolving(pub, priv)));
    }

    @Test
    void resolvedPublicAddressesAreAllowed() throws Exception {
        java.net.InetAddress pub = address((byte) 93, (byte) 184, (byte) 216, (byte) 34);
        assertTrue(StreamTracks.isRemoteStreamUrlAllowed("https://example.com/radio.mp3", true, resolving(pub)));
    }
}
