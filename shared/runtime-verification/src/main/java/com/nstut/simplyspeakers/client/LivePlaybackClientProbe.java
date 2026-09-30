package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.testing.LivePlaybackServerProbe;
import net.minecraft.client.Minecraft;

/** Client evidence comes from actual decoder/OpenAL resources, never a synthetic packet. */
final class LivePlaybackClientProbe {
    private static final String KEY = "net___simplyspeakers_verify";
    private static final String[] PHASES = {"started", "paused", "resumed", "seeked", "restarted", "stopped", "redstone"};
    private static int phase, ticks;
    private LivePlaybackClientProbe() {}
    static void tick(Minecraft client) {
        if (!LivePlaybackServerProbe.enabled() || phase == PHASES.length) return;
        if (++ticks > 2400) throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL client phase " + phase);
        var snapshot = ClientAudioPlayer.verificationSnapshot(KEY);
        boolean observed = switch (phase) {
            case 1, 5 -> snapshot.sources() == 0 && snapshot.emitters() == 0;
            case 3 -> snapshot.playing() && snapshot.decodedBytes() > 0 && Math.abs(snapshot.offset() - 4) < 0.01f;
            case 4, 6 -> snapshot.playing() && snapshot.decodedBytes() > 0 && snapshot.offset() < 1;
            default -> snapshot.playing() && snapshot.decodedBytes() > 0;
        };
        if (!observed) return;
        if (phase != 1 && phase != 5 && (snapshot.sources() != 1 || snapshot.emitters() != 2)) return;
        System.out.println("SIMPLYSPEAKERS_CLIENT_PHASE_PASS " + PHASES[phase]
                + " sources=" + snapshot.sources() + " emitters=" + snapshot.emitters()
                + " decodedBytes=" + snapshot.decodedBytes() + " offset=" + snapshot.offset());
        client.getConnection().sendCommand("simplyspeakers_verify " + PHASES[phase]);
        phase++;
        if (phase == PHASES.length) System.out.println("SIMPLYSPEAKERS_CLIENT_PLAYBACK_PASS");
    }
}
