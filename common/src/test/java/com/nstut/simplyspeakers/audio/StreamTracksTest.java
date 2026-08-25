package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

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
}
