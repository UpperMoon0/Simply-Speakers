package com.nstut.simplyspeakers.model;

import java.nio.ByteBuffer;

public class AudioData {
    public final ByteBuffer pcmBuffer;
    public final int sampleRate;
    public final int channels;

    public AudioData(ByteBuffer pcmBuffer, int sampleRate, int channels) {
        this.pcmBuffer = pcmBuffer;
        this.sampleRate = sampleRate;
        this.channels = channels;
    }
}