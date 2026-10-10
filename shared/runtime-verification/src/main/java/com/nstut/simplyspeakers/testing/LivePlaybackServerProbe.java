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
    public static final UUID PORTABLE_ID = UUID.fromString("739eaafe-4c28-4f70-968c-25a4aabb863f");
    private static final List<String> PHASES = List.of("started", "paused", "resumed", "seeked", "restarted", "stopped", "redstone",
            "portable_started", "portable_moved", "portable_paused", "portable_resumed",
            "portable_stopped", "portable_restarted", "portable_removed");
    private static com.nstut.simplyspeakers.portable.PortableSpeakerEndpoint portable;
    private static net.minecraft.world.phys.Vec3 portableStart;
    private static String portableKey;
    private static final int PORTABLE_SLOT = 35;
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
            level.setBlockAndUpdate(origin.offset(0,0,3),BlockRegistries.REDSTONE_CONTROLLER.get().defaultBlockState());
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
                level.setBlockAndUpdate(first.getBlockPos().below(),net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
                require(!state().isPlaying(), "speaker still consumes native redstone");
                level.removeBlock(first.getBlockPos().below(),false);
                BlockPos pos=first.getBlockPos().offset(0,0,2);
                level.setBlockAndUpdate(pos,BlockRegistries.REDSTONE_CONTROLLER.get().defaultBlockState());
                var controller=(com.nstut.simplyspeakers.blocks.entities.RedstoneControllerBlockEntity)level.getBlockEntity(pos);
                require(controller.configure(player,ID,false,BlockPos.ZERO,com.nstut.simplyspeakers.control.ControllerAction.TOGGLE,"",false,1),"controller configuration rejected");
                level.setBlockAndUpdate(pos.offset(1,0,0),net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
                controller.observeSignal(level.getBestNeighborSignal(pos));
                com.nstut.simplyspeakers.blocks.entities.ControllerCoordinator.flush(currentServer);
                require(state().isPlaying() && !state().isPaused(), "controller pulse did not start playback");
                level.removeBlock(pos.offset(1,0,0),false);
                level.removeBlock(pos,false);
                first.updateEmitterSnapshot(); second.updateEmitterSnapshot();
            }
            case "redstone" -> {
                verifyPeripheral();
                verifyController();
                require(ServerSpeakerControlService.playlistControl(currentServer, level, key,
                        PlaylistControlPacketC2S.OP_CLEAR, 0, false, "", ""), "playlist clear rejected");
                require(state().getPlaylist().size() == 0, "playlist did not clear");
                require(ServerSpeakerControlService.stop(currentServer, level, key), "block fixture stop rejected");
                startPortableFixture();
            }
            case "portable_started" -> {
                require(ServerPlaybackManager.getSubscribers(portable.location()).contains(player.getUUID()), "portable holder did not subscribe");
                portableStart = player.position();
                teleportHolder(portableStart.x + 8, portableStart.y, portableStart.z);
            }
            case "portable_moved" -> {
                require(player.position().distanceToSqr(portableStart) >= 36, "portable holder did not move");
                var pose = com.nstut.simplyspeakers.portable.PortableSpeakerManager.emitterPosition(level, portable.getBlockPos());
                require(pose != null && pose.distanceToSqr(player.position().add(0, 1, 0)) < .01, "portable server emitter did not follow inventory holder");
                require(ServerSpeakerControlService.pause(currentServer, level, portableKey), "portable pause rejected");
            }
            case "portable_paused" -> {
                require(portable.getSpeakerState().isPaused(), "portable paused state missing");
                require(ServerSpeakerControlService.play(currentServer, level, portableKey), "portable resume rejected");
            }
            case "portable_resumed" -> require(ServerSpeakerControlService.stop(currentServer, level, portableKey), "portable stop rejected");
            case "portable_stopped" -> require(ServerSpeakerControlService.play(currentServer, level, portableKey), "portable restart rejected");
            case "portable_restarted" -> {
                player.getInventory().setItem(PORTABLE_SLOT, net.minecraft.world.item.ItemStack.EMPTY);
                com.nstut.simplyspeakers.portable.PortableSpeakerManager.serverTick(currentServer);
                require(com.nstut.simplyspeakers.portable.PortableSpeakerManager.getEndpoint(PORTABLE_ID) == null, "removed inventory speaker kept its endpoint");
                require(ServerPlaybackManager.getSubscribers(portable.location()).isEmpty(), "removed portable retained listeners");
                require(ServerSpeakerRegistry.getSpeakerStateByFullKey(portableKey).isPaused(), "removed standalone speaker did not preserve paused state");
            }
            case "portable_removed" -> {
                ServerSpeakerRegistry.flushDirty();
                done = true;
                System.out.println("SIMPLYSPEAKERS_SERVER_PLAYBACK_PASS");
            }
        }
        System.out.println("SIMPLYSPEAKERS_SERVER_PHASE_PASS " + observed);
        phase++;
    }

    private static void startPortableFixture() {
        var stack = new net.minecraft.world.item.ItemStack(com.nstut.simplyspeakers.items.ItemRegistries.PORTABLE_SPEAKER.get());
        com.nstut.simplyspeakers.items.PortableSpeakerItem.setIdentity(stack, PORTABLE_ID);
        player.getInventory().setItem(PORTABLE_SLOT, stack);
        com.nstut.simplyspeakers.portable.PortableSpeakerManager.serverTick(currentServer);
        portable = com.nstut.simplyspeakers.portable.PortableSpeakerManager.getEndpoint(PORTABLE_ID);
        require(portable != null, "inventory speaker endpoint was not created");
        portableKey = portable.getFullStateKey();
        var state = portable.getSpeakerState();
        state.setOwnerUuid(player.getUUID()); state.setAudioId(audio.getUuid());
        state.setAudioFilename(audio.getOriginalFilename()); state.setMaxRange(64);
        state.setMaxVolume(.5f); state.setLooping(true);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey(portableKey, state);
        portable.updateEmitterSnapshot();
        require(ServerSpeakerControlService.play(currentServer, level, portableKey), "portable initial playback rejected");
    }

    private static void teleportHolder(double x, double y, double z) {
        try {
            // The five-scalar connection teleport exists across the supported versions;
            // reflection keeps the fixture independent of overload additions in mappings.
            var teleport = player.connection.getClass().getMethod("teleport", double.class, double.class,
                    double.class, float.class, float.class);
            teleport.invoke(player.connection, x, y, z, player.getYRot(), player.getXRot());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL portable holder teleport", error);
        }
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

    private static void verifyController() {
        BlockPos pos=first.getBlockPos().offset(0,0,2);
        level.setBlockAndUpdate(pos,BlockRegistries.REDSTONE_CONTROLLER.get().defaultBlockState());
        var controller=(com.nstut.simplyspeakers.blocks.entities.RedstoneControllerBlockEntity)level.getBlockEntity(pos);
        require(controller != null,"controller block entity missing");
        controller.claim(player.getUUID());
        state().setRedstoneMode(RedstoneMode.IGNORE);
        require(controller.configure(player,ID,false,BlockPos.ZERO,
                com.nstut.simplyspeakers.control.ControllerAction.TOGGLE,"",false,1),"controller configuration rejected");
        controller.observeSignal(15); com.nstut.simplyspeakers.blocks.entities.ControllerCoordinator.flush(currentServer); require(state().isPaused(),"controller pulse did not pause");
        controller.observeSignal(15); com.nstut.simplyspeakers.blocks.entities.ControllerCoordinator.flush(currentServer); require(state().isPaused(),"held controller repeated pulse");
        controller.observeSignal(0); controller.observeSignal(15); com.nstut.simplyspeakers.blocks.entities.ControllerCoordinator.flush(currentServer); require(!state().isPaused(),"controller pulse did not resume");
        require(controller.configure(player,ID,false,BlockPos.ZERO,
                com.nstut.simplyspeakers.control.ControllerAction.VOLUME,"",false,0.5f),"volume controller rejected");
        controller.observeSignal(12); com.nstut.simplyspeakers.blocks.entities.ControllerCoordinator.flush(currentServer); require(Math.abs(state().getMaxVolume()-0.4f)<0.001,"analog controller volume incorrect");
        level.removeBlock(pos,false);
        com.nstut.simplyspeakers.blocks.entities.ControllerCoordinator.flush(currentServer);
        ServerSpeakerControlService.setVolume(currentServer,level,key,0);
        verifyControllerCooperation();
        System.out.println("SIMPLYSPEAKERS_CONTROLLER_PASS");
    }

    private static com.nstut.simplyspeakers.blocks.entities.RedstoneControllerBlockEntity controllerAt(BlockPos pos,com.nstut.simplyspeakers.control.ControllerAction mode,float ceiling) {
        level.setBlockAndUpdate(pos,BlockRegistries.REDSTONE_CONTROLLER.get().defaultBlockState());
        var c=(com.nstut.simplyspeakers.blocks.entities.RedstoneControllerBlockEntity)level.getBlockEntity(pos);
        require(c!=null && c.configure(player,ID,false,BlockPos.ZERO,mode,"",false,ceiling),"cooperation controller configuration failed");return c;
    }
    private static void power(BlockPos pos,boolean enabled) {
        if(enabled) level.setBlockAndUpdate(pos.below(),net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        else level.removeBlock(pos.below(),false);
    }
    private static void resolveControllers() { com.nstut.simplyspeakers.blocks.entities.ControllerCoordinator.flush(currentServer); }
    private static void verifyControllerCooperation() {
        var mode=com.nstut.simplyspeakers.control.ControllerAction.ENABLED;
        BlockPos a=first.getBlockPos().offset(0,0,2),b=a.offset(3,0,0),stopPos=a.offset(0,0,3);
        var left=controllerAt(a,mode,1);var right=controllerAt(b,mode,1);
        power(a,true);resolveControllers();require(state().isPlaying()&&!state().isPaused(),"first playback input did not enable shared network");
        power(b,true);power(a,false);resolveControllers();require(!state().isPaused(),"unpowered duplicate incorrectly paused powered network");
        power(b,false);resolveControllers();require(state().isPaused(),"all playback inputs off did not pause");
        level.removeBlock(a,false);level.removeBlock(b,false);resolveControllers();
        require(ServerSpeakerControlService.stop(currentServer,level,key),"cooperation reset failed");
        left=controllerAt(a,com.nstut.simplyspeakers.control.ControllerAction.TOGGLE,1);
        right=controllerAt(b,com.nstut.simplyspeakers.control.ControllerAction.TOGGLE,1);
        power(a,true);power(b,true);resolveControllers();require(state().isPlaying()&&!state().isPaused(),"duplicate physical toggle pulses cancelled");
        power(a,false);power(b,false);
        controllerAt(stopPos,com.nstut.simplyspeakers.control.ControllerAction.STOP,1);
        power(a,true);power(stopPos,true);resolveControllers();require(!state().isPlaying(),"physical Stop lost to competing Toggle");
        power(a,false);power(stopPos,false);level.removeBlock(stopPos,false);
        require(left.configure(player,ID,false,BlockPos.ZERO,com.nstut.simplyspeakers.control.ControllerAction.VOLUME,"",false,0.5f),"first volume configuration failed");
        require(right.configure(player,ID,false,BlockPos.ZERO,com.nstut.simplyspeakers.control.ControllerAction.VOLUME,"",false,0.8f),"second volume configuration failed");
        power(a,true);power(b,true);resolveControllers();require(Math.abs(state().getMaxVolume()-0.8f)<0.001,"physical volume inputs did not use maximum");
        power(b,false);resolveControllers();require(Math.abs(state().getMaxVolume()-0.5f)<0.001,"remaining volume input was not retained");
        power(a,false);level.removeBlock(a,false);level.removeBlock(b,false);resolveControllers();
        require(state().getMaxVolume()==state().getConfiguredMaxVolume(),"removing physical controllers lost saved manual volume");
        try {
            var secondClip=SimplySpeakers.getAudioFileManager().saveFile(new ByteArrayInputStream(WaveFixture.tone(2)),"cooperation-second.wav",player.getUUID().toString());
            ServerSpeakerControlService.playlistControl(currentServer,level,key,PlaylistControlPacketC2S.OP_CLEAR,0,false,"","");
            state().getPlaylist().add(audio.getUuid(),audio.getOriginalFilename());state().getPlaylist().add(secondClip.getUuid(),secondClip.getOriginalFilename());
            controllerAt(a,com.nstut.simplyspeakers.control.ControllerAction.TRACK,1);power(a,true);resolveControllers();
            require(state().getPlaylist().getCurrentIndex()==1 && state().getAudioId().equals(secondClip.getUuid()),"physical analog track selector did not bound strength to playlist size");
            power(a,false);level.removeBlock(a,false);resolveControllers();
        } catch(Exception error) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL track selection fixture",error); }
        System.out.println("SIMPLYSPEAKERS_CONTROLLER_COOPERATION_PASS");
    }

    private static void require(boolean condition, String message) { if (!condition) fail(message); }
    private static void fail(String message) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL " + message); }
}
