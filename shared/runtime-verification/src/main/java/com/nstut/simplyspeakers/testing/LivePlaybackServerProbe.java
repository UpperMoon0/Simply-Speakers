package com.nstut.simplyspeakers.testing;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.nstut.simplyspeakers.*;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.network.PlaylistControlPacketC2S;
import com.nstut.simplyspeakers.speakers.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

/** Opt-in runtime fixture. Client acknowledgements require decoded, playing audio.
 * Never registers commands or changes worlds outside the dedicated verification run. */
public final class LivePlaybackServerProbe {
    private static final String ID = "__simplyspeakers_verify";
    private static final List<String> PHASES = List.of("started", "paused", "resumed", "seeked", "restarted", "stopped", "redstone");
    private static MinecraftServer currentServer;
    private static ServerLevel level;
    private static ServerPlayer player;
    private static SpeakerBlockEntity first, second;
    private static String key;
    private static AudioFileMetadata audio;
    private static int phase, ticks, joinedTicks;
    private static boolean done;
    private LivePlaybackServerProbe() {}
    public static boolean enabled() { return Boolean.getBoolean("simplyspeakers.livePlaybackTest"); }

    public static void tick(MinecraftServer server) {
        if (!enabled()) return;
        if (server != currentServer) {
            currentServer = server; player = null; first = null; phase = 0; ticks = 0; joinedTicks = 0; done = false;
            server.getCommands().getDispatcher().register(Commands.literal("simplyspeakers_verify")
                    .then(Commands.argument("phase", StringArgumentType.word()).executes(ctx -> {
                        require(ctx.getSource().getPlayer() == player, "unexpected acknowledgement sender");
                        acknowledge(StringArgumentType.getString(ctx, "phase")); return 1;
                    })));
        }
        if (done) return;
        if (player != null) {
            if (++ticks > 2400) fail("runtime fixture timed out at phase " + phase);
            require(server.getPlayerList().getPlayers().contains(player), "client disconnected before verification completed");
            return;
        }
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        if (++joinedTicks < 20) return;
        player = server.getPlayerList().getPlayers().get(0);
        level = (ServerLevel) player.level();
        try {
            BlockPos origin = player.blockPosition().offset(2, 1, 0);
            level.setBlockAndUpdate(origin, BlockRegistries.SPEAKER.get().defaultBlockState());
            level.setBlockAndUpdate(origin.offset(1, 0, 0), BlockRegistries.SPEAKER.get().defaultBlockState());
            first = (SpeakerBlockEntity) level.getBlockEntity(origin);
            second = (SpeakerBlockEntity) level.getBlockEntity(origin.offset(1, 0, 0));
            require(first != null && second != null, "speaker block entities were not created");
            first.setSpeakerId(ID); second.setSpeakerId(ID); key = first.getFullStateKey();
            audio = SimplySpeakers.getAudioFileManager().saveFile(new ByteArrayInputStream(WaveFixture.tone(30)),
                    "verification.wav", player.getUUID().toString());
            SpeakerState state = state();
            state.setOwnerUuid(player.getUUID()); state.setRedstoneMode(RedstoneMode.PULSE);
            state.setAudioId(audio.getUuid()); state.setAudioFilename(audio.getOriginalFilename());
            state.setMaxRange(64); state.setMaxVolume(0); state.setLooping(true);
            ServerSpeakerRegistry.updateSpeakerStateByFullKey(key, state);
            require(ServerSpeakerControlService.play(server, level, key), "initial play rejected");
            first.updateEmitterSnapshot(); second.updateEmitterSnapshot();
        } catch (Exception error) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL setup", error); }
    }

    private static SpeakerState state() { return ServerSpeakerRegistry.getSpeakerStateByFullKey(key); }
    private static void acknowledge(String observed) {
        require(!done && phase < PHASES.size() && PHASES.get(phase).equals(observed), "unexpected phase " + observed);
        switch (observed) {
            case "started" -> {
                require(ServerPlaybackManager.getEmitterLocationsForPlayer(player.getUUID()).size() == 2, "linked emitter audience missing");
                require(ServerSpeakerControlService.pause(currentServer, level, key), "pause rejected");
                require(state().isPaused(), "pause state missing");
            }
            case "paused" -> require(ServerSpeakerControlService.play(currentServer, level, key), "resume rejected");
            case "resumed" -> require(ServerSpeakerControlService.seek(currentServer, level, key, 4), "seek rejected");
            case "seeked" -> require(ServerSpeakerControlService.restart(currentServer, level, key), "restart rejected");
            case "restarted" -> require(ServerSpeakerControlService.stop(currentServer, level, key), "stop rejected");
            case "stopped" -> {
                first.handleRedstoneChange(0); first.handleRedstoneChange(15);
                require(state().isPlaying() && !state().isPaused(), "redstone pulse did not start playback");
                first.updateEmitterSnapshot(); second.updateEmitterSnapshot();
            }
            case "redstone" -> {
                verifyPeripheral();
                require(ServerSpeakerControlService.playlistControl(currentServer, level, key,
                        PlaylistControlPacketC2S.OP_CLEAR, 0, false, "", ""), "playlist clear rejected");
                require(state().getPlaylist().size() == 0, "playlist did not clear");
                ServerSpeakerRegistry.flushDirty();
                done = true;
                System.out.println("SIMPLYSPEAKERS_SERVER_PLAYBACK_PASS");
            }
        }
        System.out.println("SIMPLYSPEAKERS_SERVER_PHASE_PASS " + observed);
        phase++;
    }

    private static void verifyPeripheral() {
        if (!Boolean.getBoolean("simplyspeakers.requireComputerCraft")) return;
        try {
            Class<?> type = null;
            for (String name : List.of("com.nstut.simplyspeakers.fabric.compat.computercraft.SimplySpeakersPeripheral",
                    "com.nstut.simplyspeakers.forge.compat.computercraft.SimplySpeakersPeripheral",
                    "com.nstut.fabric.simplyspeakers.compat.computercraft.SimplySpeakersPeripheral",
                    "com.nstut.neoforge.simplyspeakers.compat.computercraft.SimplySpeakersPeripheral")) {
                try { type = Class.forName(name); break; } catch (ClassNotFoundException ignored) {}
            }
            require(type != null, "CC:Tweaked peripheral adapter is missing");
            Object peripheral = type.getConstructor(SpeakerBlockEntity.class).newInstance(first);
            require("simply_speaker".equals(type.getMethod("getType").invoke(peripheral)), "peripheral type changed");
            require(Boolean.TRUE.equals(type.getMethod("setTrack", String.class).invoke(peripheral, audio.getUuid())), "owner's track rejected");
            var foreign = SimplySpeakers.getAudioFileManager().saveFile(new ByteArrayInputStream(WaveFixture.tone(1)),
                    "foreign.wav", UUID.randomUUID().toString());
            require(Boolean.FALSE.equals(type.getMethod("setTrack", String.class).invoke(peripheral, foreign.getUuid())), "foreign track accepted");
            require(state().getAudioId().equals(audio.getUuid()), "rejected track mutated playback");
            state().setAccessMode(SpeakerAccess.OWNER_ONLY);
            type.getMethod("pause").invoke(peripheral);
            require(!state().isPaused(), "untrusted automation bypassed owner-only policy");
            state().setAccessMode(SpeakerAccess.PUBLIC);
            type.getMethod("pause").invoke(peripheral); require(state().isPaused(), "public peripheral pause failed");
            type.getMethod("play").invoke(peripheral); require(!state().isPaused(), "public peripheral resume failed");
            System.out.println("SIMPLYSPEAKERS_PERIPHERAL_PASS");
        } catch (Exception error) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL peripheral", error); }
    }

    private static void require(boolean condition, String message) { if (!condition) fail(message); }
    private static void fail(String message) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL " + message); }
}
