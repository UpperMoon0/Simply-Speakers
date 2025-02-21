package com.nstut.simplyspeakers.client;

import net.minecraft.core.BlockPos;
import org.lwjgl.openal.AL10;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientAudioPlayer {

    private static final Map<BlockPos, Integer> speakerSources = new ConcurrentHashMap<>();

    public static void play(BlockPos pos, String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            System.err.println("Audio path is empty for speaker at " + pos + ". Skipping playback.");
            return;
        }

        Thread audioThread = new Thread(() -> {
            File audioFile = new File(filePath);
            if (!audioFile.exists()) {
                System.err.println("Audio file not found: " + filePath);
                return;
            }
            try {
                AudioInputStream ais = AudioSystem.getAudioInputStream(audioFile);
                AudioFormat baseFormat = ais.getFormat();
                AudioFormat decodedFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false
                );

                try (AudioInputStream dais = AudioSystem.getAudioInputStream(decodedFormat, ais)) {
                    byte[] data = dais.readAllBytes();
                    ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
                    buffer.order(ByteOrder.nativeOrder());
                    buffer.put(data);
                    buffer.flip();

                    int alFormat = getOpenALFormat(decodedFormat);
                    int bufferID = AL10.alGenBuffers();
                    AL10.alBufferData(bufferID, alFormat, buffer, (int) decodedFormat.getSampleRate());

                    int sourceID = AL10.alGenSources();
                    speakerSources.put(pos, sourceID);
                    AL10.alSourcei(sourceID, AL10.AL_BUFFER, bufferID);
                    AL10.alSourcePlay(sourceID);
                }
            } catch (IOException | UnsupportedAudioFileException e) {
                e.printStackTrace();
            }
        });
        audioThread.setDaemon(true);
        audioThread.start();
    }

    public static void stop(BlockPos pos) {
        Integer sourceID = speakerSources.get(pos);
        if (sourceID != null) {
            AL10.alSourceStop(sourceID);
            AL10.alDeleteSources(sourceID);
            speakerSources.remove(pos);
        }
    }

    private static int getOpenALFormat(AudioFormat format) {
        int channels = format.getChannels();
        if (channels == 1) {
            return AL10.AL_FORMAT_MONO16;
        } else if (channels == 2) {
            return AL10.AL_FORMAT_STEREO16;
        } else {
            throw new IllegalArgumentException("Unsupported number of channels: " + channels);
        }
    }
}