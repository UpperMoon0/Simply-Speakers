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
    private static final List<String> RELOAD_CASES = List.of("normal_playing", "normal_paused", "portable_playing", "portable_paused");
    private static int reloadCase;
    private static final List<String> OBSERVER_PHASES = List.of("observer_started", "observer_farther", "observer_out_of_range",
            "observer_reentered", "observer_paused", "observer_resumed", "observer_stopped", "observer_restarted", "observer_removed");
    private static final java.util.Set<String> acknowledgements = new java.util.HashSet<>();
    private static com.nstut.simplyspeakers.portable.PortableSpeakerEndpoint portable;
    private static net.minecraft.world.phys.Vec3 portableStart, observerAnchor;
    private static int observerPhase, portableStage, returnAtTick;
    private static String portableKey;
    private static final int PORTABLE_SLOT = 35;
    private static MinecraftServer currentServer;
    private static ServerLevel level;
    private static ServerPlayer player, observer;
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
            currentServer = server; player = null; observer = null; first = null; portable = null;
            phase = 0; reloadCase = 0; observerPhase = 0; portableStage = 0; ticks = 0; joinedTicks = 0; done = false;
            acknowledgements.clear();
            server.getCommands().getDispatcher().register(Commands.literal("simplyspeakers_verify")
                    .then(Commands.argument("phase", StringArgumentType.word()).executes(ctx -> {
                        var sender = ctx.getSource().getPlayer();
                        String observed = StringArgumentType.getString(ctx, "phase");
                        if (sender != null && sender == player) acknowledge(observed);
                        else if (sender != null && sender == observer) acknowledgeObserver(observed);
                        else fail("unexpected acknowledgement sender");
                        return 1;
                    })));
            server.getCommands().getDispatcher().register(Commands.literal("simplyspeakers_verify_reload")
                    .then(Commands.argument("case", StringArgumentType.word()).executes(ctx -> {
                        require(player != null && ctx.getSource().getPlayer() == player, "unexpected sound reload acknowledgement sender");
                        acknowledgeReload(StringArgumentType.getString(ctx, "case"));
                        return 1;
                    })));
        }
        if (done) return;
        if (player != null) {
            if (++ticks > 2400) fail("runtime fixture timed out at carrier phase " + phase + ", observer phase " + observerPhase + ", portable stage " + portableStage);
            require(server.getPlayerList().getPlayers().contains(player) && server.getPlayerList().getPlayers().contains(observer),
                    "carrier or observer disconnected before verification completed");
            advancePortable();
            return;
        }
        ServerPlayer carrierCandidate = participant(server, "SSCarrier"), observerCandidate = participant(server, "SSObserver");
        if (carrierCandidate == null || observerCandidate == null) return;
        if (++joinedTicks < 20) return;
        player = carrierCandidate; observer = observerCandidate;
        require(!player.getUUID().equals(observer.getUUID()), "carrier and observer are not distinct players");
        level = (ServerLevel) player.level();
        require(observer.level() == level, "observer joined a different dimension");
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

    private static ServerPlayer participant(MinecraftServer server, String name) {
        return server.getPlayerList().getPlayers().stream().filter(p -> p.getName().getString().equals(name)).findFirst().orElse(null);
    }
    private static SpeakerState state() { return ServerSpeakerRegistry.getSpeakerStateByFullKey(key); }
    private static void acknowledgeReload(String observed) {
        require(!done && reloadCase < RELOAD_CASES.size() && RELOAD_CASES.get(reloadCase).equals(observed),
                "unexpected sound reload case " + observed);
        int expectedPhase = switch (reloadCase) { case 0 -> 0; case 1 -> 1; case 2 -> 7; default -> 9; };
        require(phase == expectedPhase, "sound reload acknowledged in the wrong transport phase");
        SpeakerState state = reloadCase < 2 ? state() : portable.getSpeakerState();
        boolean playing = reloadCase % 2 == 0;
        require(playing ? state.isPlaying() && !state.isPaused() : state.isPaused(),
                "client sound reload changed authoritative transport");
        int emitters = ServerPlaybackManager.getEmitterLocationsForPlayer(player.getUUID()).size();
        require(emitters == (playing ? (reloadCase == 0 ? 2 : 1) : 0),
                "client sound reload changed server emitter subscriptions");
        acknowledgements.add("reload_" + observed); reloadCase++;
        System.out.println("SIMPLYSPEAKERS_SERVER_AUDIO_RELOAD_PASS " + observed + " emitters=" + emitters);
    }
    private static void acknowledge(String observed) {
        require(!done && phase < PHASES.size() && PHASES.get(phase).equals(observed), "unexpected phase " + observed);
        String expectedReload = switch (observed) {
            case "started" -> "normal_playing"; case "paused" -> "normal_paused";
            case "portable_started" -> "portable_playing"; case "portable_paused" -> "portable_paused";
            default -> null;
        };
        if (expectedReload != null) require(acknowledgements.contains("reload_" + expectedReload), "missing actual sound reload evidence");
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
            case "portable_started" -> require(ServerPlaybackManager.getSubscribers(portable.location()).contains(player.getUUID()), "portable holder did not subscribe");
            case "portable_moved" -> {
                require(player.position().distanceToSqr(portableStart) >= 36, "portable holder did not move");
                var pose = com.nstut.simplyspeakers.portable.PortableSpeakerManager.emitterPosition(level, portable.getBlockPos());
                require(pose != null && pose.distanceToSqr(player.position().add(0, 1, 0)) < .01, "portable server emitter did not follow inventory holder");
            }
            case "portable_paused" -> require(portable.getSpeakerState().isPaused(), "portable paused state missing");
            case "portable_resumed", "portable_restarted" -> require(portable.getSpeakerState().isPlaying() && !portable.getSpeakerState().isPaused(), "portable playing state missing");
            case "portable_stopped" -> require(!portable.getSpeakerState().isPlaying(), "portable stopped state missing");
            case "portable_removed" -> require(com.nstut.simplyspeakers.portable.PortableSpeakerManager.getEndpoint(PORTABLE_ID) == null, "removed inventory speaker kept its endpoint");
        }
        System.out.println("SIMPLYSPEAKERS_SERVER_PHASE_PASS " + observed);
        acknowledgements.add(observed);
        phase++;
        advancePortable();
    }

    private static void acknowledgeObserver(String observed) {
        require(!done && portable != null && observerPhase < OBSERVER_PHASES.size()
                && OBSERVER_PHASES.get(observerPhase).equals(observed), "unexpected observer phase " + observed);
        int expectedStage = switch (observerPhase) { case 0 -> 1; case 1 -> 2; case 2 -> 3; case 3 -> 5; default -> observerPhase + 2; };
        require(portableStage == expectedStage, "observer acknowledged an inactive stage " + observed);
        boolean subscribed = ServerPlaybackManager.getSubscribers(portable.location()).contains(observer.getUUID());
        boolean silent = observerPhase == 2 || observerPhase == 4 || observerPhase == 6 || observerPhase == 8;
        require(subscribed != silent, "observer subscription disagrees with " + observed);
        if (observerPhase == 2) require(ServerPlaybackManager.getSubscribers(portable.location()).contains(player.getUUID()),
                "range exit incorrectly silenced the inventory holder");
        System.out.println("SIMPLYSPEAKERS_SERVER_PHASE_PASS " + observed);
        acknowledgements.add(observed); observerPhase++;
        advancePortable();
    }

    private static boolean both(String carrierPhase, String observerPhase) {
        return acknowledgements.contains(carrierPhase) && acknowledgements.contains(observerPhase);
    }
    /** Every transport change waits for independent evidence from both clients. */
    private static void advancePortable() {
        switch (portableStage) {
            case 1 -> {
                if (!both("portable_started", "observer_started")) return;
                portableStage = 2; moveCarrier(32);
            }
            case 2 -> {
                if (!both("portable_moved", "observer_farther")) return;
                portableStage = 3; moveCarrier(96);
            }
            case 3 -> {
                if (!acknowledgements.contains("observer_out_of_range")) return;
                portableStage = 4; returnAtTick = ticks + 30;
            }
            case 4 -> {
                if (ticks < returnAtTick) return;
                portableStage = 5; moveCarrier(12);
            }
            case 5 -> {
                if (!acknowledgements.contains("observer_reentered")) return;
                portableStage = 6;
                require(ServerSpeakerControlService.pause(currentServer, level, portableKey), "portable pause rejected");
            }
            case 6 -> {
                if (!both("portable_paused", "observer_paused")) return;
                portableStage = 7;
                require(ServerSpeakerControlService.play(currentServer, level, portableKey), "portable resume rejected");
            }
            case 7 -> {
                if (!both("portable_resumed", "observer_resumed")) return;
                portableStage = 8;
                require(ServerSpeakerControlService.stop(currentServer, level, portableKey), "portable stop rejected");
            }
            case 8 -> {
                if (!both("portable_stopped", "observer_stopped")) return;
                portableStage = 9;
                require(ServerSpeakerControlService.play(currentServer, level, portableKey), "portable restart rejected");
            }
            case 9 -> {
                if (!both("portable_restarted", "observer_restarted")) return;
                portableStage = 10;
                player.getInventory().setItem(PORTABLE_SLOT, net.minecraft.world.item.ItemStack.EMPTY);
                com.nstut.simplyspeakers.portable.PortableSpeakerManager.serverTick(currentServer);
                require(com.nstut.simplyspeakers.portable.PortableSpeakerManager.getEndpoint(PORTABLE_ID) == null, "removed inventory speaker kept its endpoint");
                require(ServerPlaybackManager.getSubscribers(portable.location()).isEmpty(), "removed portable retained listeners");
                require(ServerSpeakerRegistry.getSpeakerStateByFullKey(portableKey).isPaused(), "removed standalone speaker did not preserve paused state");
            }
            case 10 -> {
                if (!both("portable_removed", "observer_removed")) return;
                portableStage = 11; ServerSpeakerRegistry.flushDirty(); done = true;
                System.out.println("SIMPLYSPEAKERS_SERVER_PLAYBACK_PASS");
            }
        }
    }
    private static void moveCarrier(double distance) {
        teleport(player, observerAnchor.x + distance, observerAnchor.y, observerAnchor.z);
    }

    private static void startPortableFixture() {
        observerAnchor = player.position().add(0, 0, 12);
        teleport(observer, observerAnchor.x, observerAnchor.y, observerAnchor.z);
        moveCarrier(8); portableStart = player.position(); portableStage = 1;
        var stack = new net.minecraft.world.item.ItemStack(com.nstut.simplyspeakers.items.ItemRegistries.PORTABLE_SPEAKER.get());
        require(stack.getItem() instanceof com.nstut.simplyspeakers.items.PortableSpeakerItem && stack.getMaxStackSize() == 1,
                "registered portable item must be non-stackable");
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

    private static void teleport(ServerPlayer target, double x, double y, double z) {
        try {
            // The five-scalar connection teleport exists across the supported versions;
            // reflection keeps the fixture independent of overload additions in mappings.
            var teleport = target.connection.getClass().getMethod("teleport", double.class, double.class,
                    double.class, float.class, float.class);
            teleport.invoke(target.connection, x, y, z, target.getYRot(), target.getXRot());
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
