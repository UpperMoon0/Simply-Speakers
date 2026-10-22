package com.nstut.simplyspeakers.testing;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.speakers.ServerPlaybackManager;
import com.nstut.simplyspeakers.speakers.ServerSpeakerControlService;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import java.io.ByteArrayInputStream;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Opt-in native game fixture. Never registers commands or changes a normal world. */
public final class LegacyRuntimeAcceptance {
    public static final String PROPERTY = "simplyspeakers.legacyRuntimeAcceptance";
    public static final String SPEAKER_ID = "__simplyspeakers_1122_verify";
    private static final String[] PHASES = {"started", "paused", "resumed", "stopped"};
    private static MinecraftServer current;
    private static EntityPlayerMP participant;
    private static SpeakerBlockEntity speaker;
    private static WorldServer world;
    private static String key;
    private static int phase, ticks, joinedTicks;
    private static boolean disconnected;
    private LegacyRuntimeAcceptance() { }
    public static boolean enabled() { return Boolean.getBoolean(PROPERTY); }

    public static void tick(MinecraftServer server) {
        if (!enabled()) return;
        if (current != server) {
            current = server; participant = null; speaker = null; phase = ticks = joinedTicks = 0; disconnected = false;
            RuntimeReceipt.emit("server");
            require("1.8".equals(System.getProperty("java.specification.version")), "game server is not Java 8");
            ((CommandHandler) server.getCommandManager()).registerCommand(acknowledgementCommand());
            System.out.println("SIMPLYSPEAKERS_1122_SERVER_DISCOVERY_PASS clientClassesRequired=false");
        }
        if (disconnected) return;
        require(++ticks < 2400, "server acceptance timed out at phase " + phase);
        if (participant == null) {
            EntityPlayerMP candidate = server.getPlayerList().getPlayerByUsername("SS1122Client");
            if (candidate == null || ++joinedTicks < 20) return;
            participant = candidate; world = candidate.getServerWorld();
            try {
                BlockPos pos = candidate.getPosition().add(2, 1, 0);
                require(world.setBlockState(pos, BlockRegistries.SPEAKER.get().getDefaultState(), 3), "speaker placement failed");
                speaker = (SpeakerBlockEntity) world.getTileEntity(pos);
                require(speaker != null, "native speaker tile not created");
                speaker.setSpeakerId(SPEAKER_ID); key = speaker.getFullStateKey();
                AudioFileMetadata audio = SimplySpeakers.getAudioFileManager().saveFile(
                        new ByteArrayInputStream(WaveFixture.tone(30)), "native-1122-verification.wav", candidate.getUniqueID().toString());
                SpeakerState state = state();
                state.setOwnerUuid(candidate.getUniqueID()); state.setAudioId(audio.getUuid());
                state.setAudioFilename(audio.getOriginalFilename()); state.setMaxRange(64);
                state.setMaxVolume(.2f); state.setLooping(true);
                ServerSpeakerRegistry.updateSpeakerStateByFullKey(key, state);
                require(ServerSpeakerControlService.play(server, world, key), "initial play rejected");
                speaker.updateEmitterSnapshot();
                System.out.println("SIMPLYSPEAKERS_1122_SERVER_JOIN_PASS player=" + candidate.getName());
            } catch (Exception error) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL native server setup", error); }
        } else if (!server.getPlayerList().getPlayers().contains(participant)) {
            require(phase == PHASES.length, "client left before transport acceptance");
            require(ServerPlaybackManager.getEmitterLocationsForPlayer(participant.getUniqueID()).isEmpty(), "disconnect kept subscriptions");
            disconnected = true;
            System.out.println("SIMPLYSPEAKERS_1122_SERVER_DISCONNECT_PASS");
            System.out.println("SIMPLYSPEAKERS_1122_SERVER_ACCEPTANCE_PASS");
        }
    }

    static CommandBase acknowledgementCommand() {
        return new CommandBase() {
            public String getName() { return "simplyspeakers_1122_accept"; }
            public String getUsage(ICommandSender sender) { return getName() + " <phase>"; }
            public int getRequiredPermissionLevel() { return 0; }
            // Native 1.12 checks operator membership even for level-zero custom
            // commands. This test-only acknowledgement needs no operator grant:
            // only the exact participant on this opted-in server may call it.
            public boolean checkPermission(MinecraftServer srv, ICommandSender sender) {
                return enabled() && current != null && participant != null && srv == current && sender == participant;
            }
            public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
                require(checkPermission(srv, sender) && args.length == 1, "unexpected acceptance sender or arguments");
                acknowledge(args[0]);
            }
        };
    }

    private static SpeakerState state() { return ServerSpeakerRegistry.getSpeakerStateByFullKey(key); }
    private static void acknowledge(String observed) {
        require(phase < PHASES.length && PHASES[phase].equals(observed), "out-of-order phase " + observed);
        if (phase == 0 || phase == 2) {
            require(state().isPlaying() && !state().isPaused(), "authoritative playback not active");
            require(ServerPlaybackManager.getEmitterLocationsForPlayer(participant.getUniqueID()).size() == 1, "wrong native emitter audience");
            require(phase == 0 ? ServerSpeakerControlService.pause(current, world, key)
                    : ServerSpeakerControlService.stop(current, world, key), "transport mutation rejected");
        } else if (phase == 1) {
            require(state().isPaused(), "paused reload changed authoritative playback");
            require(ServerPlaybackManager.getEmitterLocationsForPlayer(participant.getUniqueID()).isEmpty(), "paused emitter retained");
            require(ServerSpeakerControlService.play(current, world, key), "resume rejected");
        } else {
            require(!state().isPlaying(), "stop did not settle");
            require(ServerPlaybackManager.getEmitterLocationsForPlayer(participant.getUniqueID()).isEmpty(), "stopped emitter retained");
        }
        System.out.println("SIMPLYSPEAKERS_1122_SERVER_PHASE_PASS " + observed);
        phase++;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL " + message);
    }
}
