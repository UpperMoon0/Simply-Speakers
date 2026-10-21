package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.network.RequestUploadAudioPacketC2S;
import com.nstut.simplyspeakers.testing.LivePlaybackServerProbe;
import net.minecraft.client.Minecraft;
import java.io.RandomAccessFile;
import java.util.UUID;

/** Only runs in the isolated live verification world. Uses real upload packets. */
public final class LiveUploadClientProbe {
    private static final long SIZE = 60L * 1024 * 1024;
    private static UUID transaction;
    private static boolean preparing, sent, passed;
    private static int ticks;
    private static volatile java.io.File ready;
    private LiveUploadClientProbe() { }
    public static void tick(Minecraft client) {
        if (!LivePlaybackServerProbe.enabled() || "observer".equals(System.getProperty("simplyspeakers.livePlaybackRole"))
                || client.player == null || client.level == null || passed) return;
        if (++ticks > 6500) throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL upload timed out");
        if (!preparing) {
            preparing = true;
            Thread worker = new Thread(() -> {
                try {
                    java.io.File file = new java.io.File(client.gameDirectory, "__upload_load_test.wav");
                    try (RandomAccessFile out = new RandomAccessFile(file, "rw")) {
                        out.setLength(SIZE);
                        out.writeBytes("RIFF"); writeLE(out, (int) SIZE - 8, 4); out.writeBytes("WAVEfmt ");
                        writeLE(out, 16, 4); writeLE(out, 1, 2); writeLE(out, 1, 2);
                        writeLE(out, 44100, 4); writeLE(out, 88200, 4);
                        writeLE(out, 2, 2); writeLE(out, 16, 2);
                        out.writeBytes("data"); writeLE(out, (int) SIZE - 44, 4);
                    }
                    ready = file;
                } catch (Exception error) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL upload fixture", error); }
            }, "Simply Speakers Upload Fixture");
            worker.setDaemon(true); worker.start();
        }
        if (ready == null || sent || ticks < 100) return;
        var pos = client.player.blockPosition().offset(2, 1, 0);
        if (!(client.level.getBlockEntity(pos) instanceof com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity)) return;
        sent = true;
        transaction = ClientAudioPlayer.startUpload(ready);
        try {
            var screen = new com.nstut.simplyspeakers.client.screens.SpeakerScreen(pos);
            var send = java.util.Arrays.stream(screen.getClass().getDeclaredMethods())
                    .filter(method -> method.getName().equals("sendToServer")).findFirst().orElseThrow();
            send.setAccessible(true);
            send.invoke(screen, new RequestUploadAudioPacketC2S(pos, transaction, "__upload_load_test.wav", SIZE));
        } catch (Exception error) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL upload dispatch", error); }
    }
    public static void acknowledged(UUID id, boolean success) {
        if (!LivePlaybackServerProbe.enabled() || transaction == null || !transaction.equals(id)) return;
        if (!success) throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL 60 MiB upload rejected");
        passed = true;
        System.out.println("SIMPLYSPEAKERS_CLIENT_UPLOAD_PASS bytes=" + SIZE);
        if (ready != null) ready.delete();
    }
    private static void writeLE(RandomAccessFile out, int value, int bytes) throws java.io.IOException {
        for (int i = 0; i < bytes; i++) out.write(value >>> (8 * i));
    }
}
