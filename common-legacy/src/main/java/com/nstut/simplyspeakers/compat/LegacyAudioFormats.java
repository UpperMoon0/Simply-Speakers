package com.nstut.simplyspeakers.compat;
import java.io.*;
import javax.sound.sampled.*;
/** Normalize Java8's truncated RIFF failure to the maintained decoder contract. */
public final class LegacyAudioFormats {
    private LegacyAudioFormats() {}
    public static AudioInputStream open(File file) throws IOException,UnsupportedAudioFileException {
        try { return AudioSystem.getAudioInputStream(file); }
        catch(EOFException error) { throw invalid(error); }
    }
    public static AudioInputStream open(InputStream input) throws IOException,UnsupportedAudioFileException {
        try { return AudioSystem.getAudioInputStream(input); }
        catch(EOFException error) { throw invalid(error); }
    }
    private static UnsupportedAudioFileException invalid(EOFException cause) {
        UnsupportedAudioFileException error=new UnsupportedAudioFileException("Truncated audio header");
        error.initCause(cause);return error;
    }
}
