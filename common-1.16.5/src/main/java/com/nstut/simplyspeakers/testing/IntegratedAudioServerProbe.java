package com.nstut.simplyspeakers.testing;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.speakers.ServerPlaybackManager;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

/**
 * Small opt-in logical-server fixture, only after vanilla has entered a singleplayer world.
 * This is NOT dedicated-server, remote-network, two-client, or audible-output evidence.
 * No world creation, connection, account/service flags, or playback is driven here.
 */
public final class IntegratedAudioServerProbe {
    public static final String ID = "__simplyspeakers_integrated";
    public static final String KEY = "net_" + ID;
    public static final String COMMAND = "simplyspeakers_integrated_ack";
    public static final String SETTINGS_NAME = "Integrated acceptance";
    private static final String[] PHASES = {
            "playing_reload", "paused_reload", "resumed", "seeked", "restarted", "stopped", "ui"
    };
    private static MinecraftServer currentServer;
    private static ServerPlayer participant;
    private static Object participantConnection;
    private static ServerLevel level;
    private static SpeakerBlockEntity speaker;
    private static String fullKey, audioId;
    private static int phase, joinedTicks, generation;
    private static long startedNanos;
    private static boolean finished;

    private IntegratedAudioServerProbe() { }

    public static boolean enabled() { return Boolean.getBoolean("simplyspeakers.integratedAcceptance"); }

    public static void tick(MinecraftServer server) {
        if (!enabled()) return;
        require(!server.isDedicatedServer(), "integrated lane cannot run on a dedicated server");
        require(server.isSameThread(), "fixture ran outside the logical-server thread");
        if (server != currentServer) {
            currentServer = server;
            participant = null; participantConnection = null; speaker = null; level = null;
            fullKey = null; audioId = null; phase = 0; joinedTicks = 0; generation = 0; finished = false;
            startedNanos = System.nanoTime();
            server.getCommands().getDispatcher().register(Commands.literal(COMMAND)
                    .then(Commands.argument("phase", StringArgumentType.word())
                            .then(Commands.argument("audio", StringArgumentType.word()).executes(context -> {
                                acknowledge(context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "phase"),
                                        StringArgumentType.getString(context, "audio"));
                                return 1;
                            }))));
        }
        if (finished) return;
        require(System.nanoTime() - startedNanos < TimeUnit.SECONDS.toNanos(180),
                "logical-server timeout at phase " + phase);
        if (participant != null) {
            require(server.getPlayerList().getPlayers().size() == 1
                            && server.getPlayerList().getPlayers().contains(participant)
                            && participant.connection == participantConnection,
                    "singleplayer participant disconnected or changed");
            return;
        }
        if (server.getPlayerList().getPlayers().size() != 1) return;
        ServerPlayer candidate = server.getPlayerList().getPlayers().get(0);
        if (!"SSIClient".equals(candidate.getName().getString())) return;
        if (++joinedTicks < 20) return;
        participant = candidate;
        participantConnection = candidate.connection;
        level = candidate.getLevel();
        try {
            BlockPos position = emptyFixturePosition(candidate);
            level.setBlockAndUpdate(position, BlockRegistries.SPEAKER.get().defaultBlockState());
            require(level.getBlockEntity(position) instanceof SpeakerBlockEntity, "fixture speaker missing");
            speaker = (SpeakerBlockEntity) level.getBlockEntity(position);
            speaker.setSpeakerId(ID);
            fullKey = speaker.getFullStateKey();
            AudioFileMetadata audio = SimplySpeakers.getAudioFileManager().saveFile(
                    new ByteArrayInputStream(WaveFixture.tone(30)), "integrated-acceptance.wav",
                    participant.getUUID().toString());
            audioId = audio.getUuid();
            SpeakerState state = state();
            state.setOwnerUuid(participant.getUUID());
            state.setAudioId(audioId); state.setAudioFilename(audio.getOriginalFilename());
            state.setMaxRange(32); state.setMaxVolume(0.5f); state.setLooping(true);
            ServerSpeakerRegistry.updateSpeakerStateByFullKey(fullKey, state);
            speaker.setChanged();
            level.sendBlockUpdated(position, speaker.getBlockState(), speaker.getBlockState(), 3);
            require(!state.isPlaying(), "fixture must wait for production client transport control");
            System.out.println("SIMPLYSPEAKERS_INTEGRATED_FIXTURE_READY participant=" + participant.getUUID()
                    + " audio=" + audioId + " key=" + fullKey + " wavBytes=1323044 speakers=1");
        } catch (Exception error) {
            throw new IllegalStateException("SIMPLYSPEAKERS_INTEGRATED_FAIL fixture setup", error);
        }
    }

    private static BlockPos emptyFixturePosition(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        // Normal vanilla terrain may contain trees. Search only loaded air within
        // interaction reach; never replace terrain or place inside the participant.
        for (int y = 1; y <= 4; y++) for (int radius = 1; radius <= 4; radius++) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                BlockPos candidate = center.offset(x, y, z);
                if (level.hasChunkAt(candidate) && level.isEmptyBlock(candidate)
                        && player.distanceToSqr(candidate.getX() + .5, candidate.getY() + .5, candidate.getZ() + .5) < 64)
                    return candidate;
            }
        }
        throw new IllegalStateException("SIMPLYSPEAKERS_INTEGRATED_FAIL no nearby loaded empty fixture position");
    }

    private static SpeakerState state() { return ServerSpeakerRegistry.getSpeakerStateByFullKey(fullKey); }

    /** Reached only by the normal vanilla C2S command handler, never from the client probe directly. */
    private static void acknowledge(ServerPlayer sender, String observed, String observedAudio) {
        require(enabled() && currentServer != null && currentServer.isSameThread()
                        && !currentServer.isDedicatedServer(), "receipt outside integrated logical server");
        require(participant != null && sender == participant && sender.connection == participantConnection
                        && sender.getUUID().equals(participant.getUUID()) && sender.getLevel() == level,
                "wrong acknowledgement participant, connection, or dimension");
        require(!finished && phase < PHASES.length && PHASES[phase].equals(observed),
                "unexpected acknowledgement phase " + observed);
        require(audioId.equals(observedAudio), "stale or foreign audio acknowledgement identity");
        SpeakerState state = state();
        require(state != null && audioId.equals(state.getAudioId())
                        && participant.getUUID().equals(state.getOwnerUuid()), "authoritative audio/owner identity changed");
        boolean silent = phase == 1 || phase >= 5;
        require(ServerPlaybackManager.getEmitterLocationsForPlayer(participant.getUUID()).size() == (silent ? 0 : 1),
                "logical-server emitter subscription disagrees with " + observed);
        if (phase == 1) {
            require(state.isPlaying() && state.isPaused(), "pause C2S did not suspend authoritative transport");
        } else if (phase >= 5) {
            require(!state.isPlaying() && !state.isPaused(), "stop C2S did not stop authoritative transport");
        } else {
            require(state.isPlaying() && !state.isPaused(), "play C2S did not start authoritative transport");
        }
        if (phase == 0) generation = state.getPlaybackSessionGeneration();
        if (phase == 1 || phase == 2) require(state.getPlaybackSessionGeneration() == generation,
                "pause/resume changed playback occurrence");
        if (phase == 3) {
            require(state.getPlaybackPositionSeconds(level.getGameTime()) >= 4,
                    "seek C2S did not advance authoritative timeline");
            generation = state.getPlaybackSessionGeneration();
        }
        if (phase == 4) require(state.getPlaybackSessionGeneration() > generation,
                "restart C2S did not replace playback occurrence");
        if (phase == 6) require(SETTINGS_NAME.equals(state.getNetworkName()),
                "OpenUI settings C2S did not persist on logical server");
        System.out.println("SIMPLYSPEAKERS_INTEGRATED_SERVER_PHASE_PASS " + observed
                + " participant=" + sender.getUUID() + " audio=" + audioId + " logicalServer=true dedicated=false");
        phase++;
        if (phase == PHASES.length) {
            ServerSpeakerRegistry.flushDirty();
            finished = true;
            System.out.println("SIMPLYSPEAKERS_INTEGRATED_SERVER_PASS phases=" + PHASES.length
                    + " participants=1 productionC2S=true productionS2CObservedByClient=true dedicated=false twoClient=false");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("SIMPLYSPEAKERS_INTEGRATED_FAIL " + message);
    }
}
