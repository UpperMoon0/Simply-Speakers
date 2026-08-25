package com.nstut.simplyspeakers.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * On-demand incremental audio decoders for streaming MP3 and WAV files without
 * accumulating hundreds of megabytes of decoded PCM into RAM.
 */
public final class IncrementalAudioDecoders {

    private IncrementalAudioDecoders() {
    }

    public static AudioInputStream createMonoPcmStream(File audioFile) throws UnsupportedAudioFileException, IOException {
        String name = audioFile.getName().toLowerCase();
        if (name.endsWith(".mp3")) {
            return new IncrementalMp3AudioInputStream(audioFile);
        } else {
            AudioInputStream initialStream = AudioSystem.getAudioInputStream(audioFile);
            return createIncrementalMonoWavStream(initialStream);
        }
    }

    public static AudioInputStream openPcmStream(File audioFile) throws UnsupportedAudioFileException, IOException {
        return createMonoPcmStream(audioFile);
    }

    /**
     * Opens an incremental mono PCM stream directly from a network connection.
     *
     * @param rawStream live HTTP(S) body stream; closed by the returned stream
     * @param url       stream URL, used only to pick the decoder
     */
    public static AudioInputStream openPcmStreamFromUrl(java.io.InputStream rawStream, String url)
            throws UnsupportedAudioFileException, IOException {
        String lower = url.toLowerCase();
        if (lower.endsWith(".mp3")) {
            return new IncrementalMp3AudioInputStream(rawStream);
        }
        java.io.InputStream buffered = new BufferedInputStream(rawStream);
        AudioInputStream source = AudioSystem.getAudioInputStream(buffered);
        return createIncrementalMonoWavStream(source);
    }

    private static AudioInputStream createIncrementalMonoWavStream(AudioInputStream sourceStream) throws UnsupportedAudioFileException, IOException {
        AudioFormat srcFormat = sourceStream.getFormat();
        float sampleRate = srcFormat.getSampleRate();
        int channels = srcFormat.getChannels();

        if (channels == 1 && srcFormat.getSampleSizeInBits() == 16
                && srcFormat.getEncoding() == AudioFormat.Encoding.PCM_SIGNED
                && !srcFormat.isBigEndian()) {
            return sourceStream;
        }

        AudioInputStream pcmStream = sourceStream;
        if (srcFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
                || srcFormat.getSampleSizeInBits() != 16
                || srcFormat.isBigEndian()) {
            AudioFormat targetPcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    channels,
                    channels * 2,
                    sampleRate,
                    false
            );
            if (AudioSystem.isConversionSupported(targetPcmFormat, srcFormat)) {
                pcmStream = AudioSystem.getAudioInputStream(targetPcmFormat, sourceStream);
            } else {
                try {
                    sourceStream.close();
                } catch (IOException ignored) {}
                throw new UnsupportedAudioFileException("Conversion to PCM_SIGNED 16-bit Little Endian not supported for format: " + srcFormat);
            }
        }

        AudioFormat monoFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                1,
                2,
                sampleRate,
                false
        );

        if (channels == 1) {
            return pcmStream;
        }

        long frameLength = sourceStream.getFrameLength();
        InputStream downmixStream = new IncrementalDownmixInputStream(pcmStream, channels);
        return new AudioInputStream(downmixStream, monoFormat, frameLength);
    }

    /**
     * Incremental MP3 decoder stream that decodes frames on-demand.
     */
    public static class IncrementalMp3AudioInputStream extends AudioInputStream {
        private final InputStream fileInputStream;
        private final Bitstream bitstream;
        private final Decoder decoder;
        private final int channels;
        private byte[] frameBuffer = new byte[0];
        private int frameBufferPos = 0;
        private boolean eof = false;

        public IncrementalMp3AudioInputStream(File file) throws IOException {
            this(new BufferedInputStream(new FileInputStream(file)));
        }

        /** Streams MP3 frames incrementally from any source, including HTTP. */
        public IncrementalMp3AudioInputStream(InputStream sourceStream) throws IOException {
            super(new InputStream() {
                @Override
                public int read() {
                    return -1;
                }
            }, new AudioFormat(44100, 16, 1, true, false), AudioSystem.NOT_SPECIFIED);

            this.fileInputStream = sourceStream;
            this.bitstream = new Bitstream(fileInputStream);
            this.decoder = new Decoder();

            try {
                Header firstHeader = bitstream.readFrame();
                if (firstHeader == null) {
                    throw new IOException("Empty or invalid MP3 stream");
                }
                int sampleRate = firstHeader.frequency();
                this.channels = (firstHeader.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;

                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(firstHeader, bitstream);
                decodeSampleBufferToFrameBuffer(output);
                bitstream.closeFrame();

                this.format = new AudioFormat(sampleRate, 16, 1, true, false);
            } catch (BitstreamException | DecoderException e) {
                try {
                    bitstream.close();
                } catch (Exception ignored) {}
                throw new IOException("Failed to initialize MP3 decoder", e);
            }
        }

        private void decodeSampleBufferToFrameBuffer(SampleBuffer output) {
            short[] pcmShorts = output.getBuffer();
            int samplesRead = output.getBufferLength();
            if (channels == 2) {
                short[] monoShorts = PcmAudioDownmixer.downmixStereoToMono(pcmShorts, samplesRead);
                this.frameBuffer = PcmAudioDownmixer.shortsToBytesLE(monoShorts, monoShorts.length);
            } else {
                this.frameBuffer = PcmAudioDownmixer.shortsToBytesLE(pcmShorts, samplesRead);
            }
            this.frameBufferPos = 0;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int r = read(single, 0, 1);
            return r > 0 ? (single[0] & 0xFF) : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) throw new NullPointerException();
            if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
            if (len == 0) return 0;

            int bytesCopied = 0;
            while (bytesCopied < len) {
                if (frameBufferPos < frameBuffer.length) {
                    int available = frameBuffer.length - frameBufferPos;
                    int toCopy = Math.min(available, len - bytesCopied);
                    System.arraycopy(frameBuffer, frameBufferPos, b, off + bytesCopied, toCopy);
                    frameBufferPos += toCopy;
                    bytesCopied += toCopy;
                } else {
                    if (eof) break;
                    try {
                        Header frame = bitstream.readFrame();
                        if (frame == null) {
                            eof = true;
                            break;
                        }
                        SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frame, bitstream);
                        decodeSampleBufferToFrameBuffer(output);
                        bitstream.closeFrame();
                    } catch (BitstreamException | DecoderException e) {
                        eof = true;
                        break;
                    }
                }
            }

            return (bytesCopied > 0 || !eof) ? bytesCopied : -1;
        }

        @Override
        public long skip(long n) throws IOException {
            if (n <= 0) return 0;
            byte[] skipBuffer = new byte[(int) Math.min(n, 4096)];
            long totalSkipped = 0;
            while (totalSkipped < n) {
                int read = read(skipBuffer, 0, (int) Math.min(skipBuffer.length, n - totalSkipped));
                if (read <= 0) break;
                totalSkipped += read;
            }
            return totalSkipped;
        }

        @Override
        public void close() throws IOException {
            try {
                bitstream.close();
            } catch (BitstreamException e) {
                throw new IOException(e);
            } finally {
                fileInputStream.close();
            }
        }
    }

    /**
     * Incremental stream downmixer that converts multi-channel 16-bit PCM into mono on-the-fly.
     */
    public static class IncrementalDownmixInputStream extends InputStream {
        private final AudioInputStream source;
        private final int channels;
        private final byte[] inputBuffer;

        public IncrementalDownmixInputStream(AudioInputStream source, int channels) {
            this.source = source;
            this.channels = channels;
            this.inputBuffer = new byte[channels * 2 * 1024]; // 1024 frames buffer
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int r = read(single, 0, 1);
            return r > 0 ? (single[0] & 0xFF) : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) throw new NullPointerException();
            if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
            if (len == 0) return 0;

            // Make sure we read in multiples of 2 bytes (1 16-bit mono sample)
            int monoSamplesRequested = len / 2;
            if (monoSamplesRequested == 0) monoSamplesRequested = 1;

            int bytesPerFrame = channels * 2;
            int framesToRead = Math.min(monoSamplesRequested, inputBuffer.length / bytesPerFrame);
            int bytesToReadFromSource = framesToRead * bytesPerFrame;

            int bytesRead = source.read(inputBuffer, 0, bytesToReadFromSource);
            if (bytesRead <= 0) {
                return -1;
            }

            int framesRead = bytesRead / bytesPerFrame;
            int outOffset = off;
            int bytesWritten = 0;

            for (int i = 0; i < framesRead; i++) {
                if (bytesWritten + 2 > len) break;
                int sum = 0;
                for (int ch = 0; ch < channels; ch++) {
                    int sampleIdx = i * bytesPerFrame + ch * 2;
                    short sample = (short) ((inputBuffer[sampleIdx] & 0xFF) | (inputBuffer[sampleIdx + 1] << 8));
                    sum += sample;
                }
                short monoSample = (short) (sum / channels);
                b[outOffset++] = (byte) (monoSample & 0xFF);
                b[outOffset++] = (byte) ((monoSample >> 8) & 0xFF);
                bytesWritten += 2;
            }

            return bytesWritten;
        }

        @Override
        public void close() throws IOException {
            source.close();
        }
    }
}
