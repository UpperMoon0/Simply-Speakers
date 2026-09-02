package com.nstut.simplyspeakers.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.nstut.simplyspeakers.RedstoneMode;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerAccess;
import com.nstut.simplyspeakers.api.SpeakerApi;
import com.nstut.simplyspeakers.playlist.RepeatMode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** /simplyspeakers (alias /ss) admin and automation command tree. Operators only. */
public final class SpeakerCommands {

    private SpeakerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(net.minecraft.commands.Commands.literal("simplyspeakers")
                .requires(source -> source.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("networks")
                        .executes(SpeakerCommands::listNetworks))
                .then(net.minecraft.commands.Commands.literal("network")
                        .then(net.minecraft.commands.Commands.argument("name", StringArgumentType.string())
                                .then(net.minecraft.commands.Commands.literal("info").executes(SpeakerCommands::networkInfo))
                                .then(net.minecraft.commands.Commands.literal("play").executes(ctx -> transport(ctx, SpeakerApi.ACTION_PLAY)))
                                .then(net.minecraft.commands.Commands.literal("pause").executes(ctx -> transport(ctx, SpeakerApi.ACTION_PAUSE)))
                                .then(net.minecraft.commands.Commands.literal("stop").executes(ctx -> transport(ctx, SpeakerApi.ACTION_STOP)))
                                .then(net.minecraft.commands.Commands.literal("restart").executes(ctx -> transport(ctx, SpeakerApi.ACTION_RESTART)))
                                .then(net.minecraft.commands.Commands.literal("next").executes(ctx -> transport(ctx, SpeakerApi.ACTION_NEXT)))
                                .then(net.minecraft.commands.Commands.literal("previous").executes(ctx -> transport(ctx, SpeakerApi.ACTION_PREVIOUS)))
                                .then(net.minecraft.commands.Commands.literal("seek")
                                        .then(net.minecraft.commands.Commands.argument("seconds", FloatArgumentType.floatArg(0.0f))
                                                .executes(SpeakerCommands::networkSeek)))
                                .then(net.minecraft.commands.Commands.literal("volume")
                                        .then(net.minecraft.commands.Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                                .executes(SpeakerCommands::setVolumePercent)))
                                .then(net.minecraft.commands.Commands.literal("range")
                                        .then(net.minecraft.commands.Commands.argument("blocks", IntegerArgumentType.integer(1, 512))
                                                .executes(SpeakerCommands::setRange)))
                                .then(net.minecraft.commands.Commands.literal("loop")
                                        .then(boolArg().executes(SpeakerCommands::setLoop)))
                                .then(net.minecraft.commands.Commands.literal("shuffle")
                                        .then(boolArg().executes(SpeakerCommands::setShuffle)))
                                .then(net.minecraft.commands.Commands.literal("repeat")
                                        .then(wordArg("none", "track", "playlist").executes(SpeakerCommands::setRepeat)))
                                .then(net.minecraft.commands.Commands.literal("redstone")
                                        .then(redstoneArg().executes(SpeakerCommands::setRedstoneMode)))))
                .then(net.minecraft.commands.Commands.literal("speaker")
                        .then(net.minecraft.commands.Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(net.minecraft.commands.Commands.literal("info").executes(SpeakerCommands::speakerInfo))
                                .then(net.minecraft.commands.Commands.literal("play").executes(ctx -> speakerTransport(ctx, SpeakerApi.ACTION_PLAY)))
                                .then(net.minecraft.commands.Commands.literal("pause").executes(ctx -> speakerTransport(ctx, SpeakerApi.ACTION_PAUSE)))
                                .then(net.minecraft.commands.Commands.literal("stop").executes(ctx -> speakerTransport(ctx, SpeakerApi.ACTION_STOP)))
                                .then(net.minecraft.commands.Commands.literal("restart").executes(ctx -> speakerTransport(ctx, SpeakerApi.ACTION_RESTART)))
                                .then(net.minecraft.commands.Commands.literal("next").executes(ctx -> speakerTransport(ctx, SpeakerApi.ACTION_NEXT)))
                                .then(net.minecraft.commands.Commands.literal("previous").executes(ctx -> speakerTransport(ctx, SpeakerApi.ACTION_PREVIOUS)))
                                .then(net.minecraft.commands.Commands.literal("seek")
                                        .then(net.minecraft.commands.Commands.argument("seconds", FloatArgumentType.floatArg(0.0f))
                                                .executes(SpeakerCommands::speakerSeek)))
                                .then(net.minecraft.commands.Commands.literal("access")
                                        .then(accessArg().executes(SpeakerCommands::setAccessMode)))))
                .then(net.minecraft.commands.Commands.literal("audio")
                        .then(net.minecraft.commands.Commands.literal("list")
                                .executes(SpeakerCommands::listAudio))
                        .then(net.minecraft.commands.Commands.literal("rename")
                                .then(net.minecraft.commands.Commands.argument("audioId", StringArgumentType.string())
                                        .then(net.minecraft.commands.Commands.argument("displayName", StringArgumentType.string())
                                                .executes(SpeakerCommands::renameAudio))))));
        // Alias
        dispatcher.register(net.minecraft.commands.Commands.literal("ss")
                .requires(source -> source.hasPermission(2))
                .redirect(dispatcher.getRoot().getChild("simplyspeakers")));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> boolArg() {
        return wordArg("true", "false");
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> wordArg(String... options) {
        return net.minecraft.commands.Commands.argument("value", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (String option : options) builder.suggest(option);
                    return builder.buildFuture();
                });
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> redstoneArg() {
        return net.minecraft.commands.Commands.argument("mode", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (RedstoneMode mode : RedstoneMode.values()) builder.suggest(mode.id());
                    return builder.buildFuture();
                });
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> accessArg() {
        return net.minecraft.commands.Commands.argument("mode", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (SpeakerAccess access : SpeakerAccess.values()) builder.suggest(access.id());
                    return builder.buildFuture();
                });
    }

    private static boolean boolValue(CommandContext<CommandSourceStack> ctx) {
        return Boolean.parseBoolean(StringArgumentType.getString(ctx, "value"));
    }

    // ------------------------------------------------------------------

    private static String resolveNetworkKey(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        String fullKey = com.nstut.simplyspeakers.speakers.ServerSpeakerControlService.resolveFullStateKeyByNetwork(ctx.getSource().getLevel(), name);
        if (fullKey == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.literal("No named network '" + name + "' found in this dimension")).create();
        }
        return fullKey;
    }

    private static int listNetworks(CommandContext<CommandSourceStack> ctx) {
        Map<String, String> networks = SpeakerApi.listNamedNetworks(ctx.getSource().getLevel());
        if (networks.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "No named networks yet. Set one in a speaker's GUI or via /ss speaker <pos> after naming it.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(networks.size() + " named network(s):"), false);
        for (Map.Entry<String, String> entry : networks.entrySet()) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("- " + entry.getKey() + " — " + entry.getValue()).withStyle(ChatFormatting.GRAY), false);
        }
        return networks.size();
    }

    private static int networkInfo(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String fullKey = resolveNetworkKey(ctx);
        var state = com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.getSpeakerStateByFullKey(fullKey);
        if (state == null) {
            ctx.getSource().sendFailure(Component.literal("No network state found for " + fullKey));
            return 0;
        }
        float position = state.getPlaybackPositionSeconds(ctx.getSource().getLevel().getGameTime());
        String status = state.isPaused() ? "paused at " + position + "s"
                : state.isPlaying() ? "playing at " + position + "s" : "stopped";
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Network [" + fullKey + "]"
                        + "\n  name: " + (state.hasNetworkName() ? state.getNetworkName() : "(unnamed)")
                        + "\n  track: " + state.getAudioFilename() + " (" + state.getAudioId() + ")"
                        + "\n  status: " + status
                        + "\n  loop: " + state.isLooping()
                        + ", volume: " + Math.round(state.getMaxVolume() * 100) + "%"
                        + ", range: " + state.getMaxRange()
                        + ", redstone: " + state.getRedstoneMode().id()
                        + ", access: " + state.getAccessMode().id()
                        + "\n  playlist: " + (state.hasPlaylist()
                            ? state.getPlaylist().size() + " track(s)"
                              + (state.getPlaylist().isShuffle() ? ", shuffle" : "")
                              + ", repeat=" + state.getPlaylist().getRepeatMode().id()
                            : "(none)")), false);
        return 1;
    }

    private static int speakerInfo(CommandContext<CommandSourceStack> ctx) {
        return speakerInfoAt(ctx, BlockPosArgument.getBlockPos(ctx, "pos"));
    }

    private static int speakerInfoAt(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        var state = SpeakerApi.getState(ctx.getSource().getLevel(), pos);
        if (state == null) {
            ctx.getSource().sendFailure(Component.literal("No speaker registered or block entity at " + pos.toShortString()));
            return 0;
        }
        float position = SpeakerApi.getPositionSeconds(ctx.getSource().getLevel(), pos);
        String status = state.isPaused() ? "paused at " + position + "s"
                : state.isPlaying() ? "playing at " + position + "s" : "stopped";
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Speaker " + pos.toShortString()
                        + "\n  network: " + (state.hasNetworkName() ? state.getNetworkName() : "(unnamed)")
                        + "\n  track: " + state.getAudioFilename() + " (" + state.getAudioId() + ")"
                        + "\n  status: " + status
                        + "\n  loop: " + state.isLooping()
                        + ", volume: " + Math.round(state.getMaxVolume() * 100) + "%"
                        + ", range: " + state.getMaxRange()
                        + ", redstone: " + state.getRedstoneMode().id()
                        + ", access: " + state.getAccessMode().id()
                        + "\n  playlist: " + (state.hasPlaylist()
                            ? state.getPlaylist().size() + " track(s)"
                              + (state.getPlaylist().isShuffle() ? ", shuffle" : "")
                              + ", repeat=" + state.getPlaylist().getRepeatMode().id()
                            : "(none)")), false);
        return 1;
    }

    private static int transport(CommandContext<CommandSourceStack> ctx, byte action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String fullKey = resolveNetworkKey(ctx);
        var level = ctx.getSource().getLevel();
        com.nstut.simplyspeakers.speakers.ServerSpeakerControlService.applyTransport(level.getServer(), level, fullKey, action, 0.0f);
        return 1;
    }

    private static int networkSeek(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String fullKey = resolveNetworkKey(ctx);
        var level = ctx.getSource().getLevel();
        com.nstut.simplyspeakers.speakers.ServerSpeakerControlService.seek(level.getServer(), level, fullKey, FloatArgumentType.getFloat(ctx, "seconds"));
        return 1;
    }

    private static int speakerTransport(CommandContext<CommandSourceStack> ctx, byte action) {
        SpeakerApi.applyTransport(ctx.getSource().getLevel(), BlockPosArgument.getBlockPos(ctx, "pos"), action);
        return 1;
    }

    private static int speakerSeek(CommandContext<CommandSourceStack> ctx) {
        SpeakerApi.seek(ctx.getSource().getLevel(), BlockPosArgument.getBlockPos(ctx, "pos"),
                FloatArgumentType.getFloat(ctx, "seconds"));
        return 1;
    }

    private static int setVolumePercent(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String fullKey = resolveNetworkKey(ctx);
        var level = ctx.getSource().getLevel();
        int percent = IntegerArgumentType.getInteger(ctx, "percent");
        com.nstut.simplyspeakers.speakers.ServerSpeakerControlService.setVolume(level.getServer(), level, fullKey, percent / 100.0f);
        ctx.getSource().sendSuccess(() -> Component.literal("Volume set to " + percent + "%"), false);
        return 1;
    }

    private static int setRange(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String fullKey = resolveNetworkKey(ctx);
        var level = ctx.getSource().getLevel();
        int blocks = IntegerArgumentType.getInteger(ctx, "blocks");
        com.nstut.simplyspeakers.speakers.ServerSpeakerControlService.setRange(level.getServer(), level, fullKey, blocks);
        ctx.getSource().sendSuccess(() -> Component.literal("Range set to " + blocks), false);
        return 1;
    }

    private static int setLoop(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String fullKey = resolveNetworkKey(ctx);
        var level = ctx.getSource().getLevel();
        boolean enabled = boolValue(ctx);
        com.nstut.simplyspeakers.speakers.ServerSpeakerControlService.setLooping(level.getServer(), level, fullKey, enabled);
        ctx.getSource().sendSuccess(() -> Component.literal("Looping " + (enabled ? "on" : "off")), false);
        return 1;
    }

    private static int setShuffle(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String fullKey = resolveNetworkKey(ctx);
        var level = ctx.getSource().getLevel();
        boolean enabled = boolValue(ctx);
        com.nstut.simplyspeakers.speakers.ServerSpeakerControlService.playlistControl(level.getServer(), level, fullKey, (byte) 7, -1, enabled, "", "");
        ctx.getSource().sendSuccess(() -> Component.literal("Shuffle " + (enabled ? "on" : "off")), false);
        return 1;
    }

    private static int setRepeat(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String fullKey = resolveNetworkKey(ctx);
        var level = ctx.getSource().getLevel();
        RepeatMode mode = RepeatMode.parse(StringArgumentType.getString(ctx, "value"));
        com.nstut.simplyspeakers.speakers.ServerSpeakerControlService.playlistControl(level.getServer(), level, fullKey, (byte) 8, mode.ordinal(), false, "", "");
        ctx.getSource().sendSuccess(() -> Component.literal("Repeat mode: " + mode.id()), false);
        return 1;
    }

    private static int setRedstoneMode(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String fullKey = resolveNetworkKey(ctx);
        var level = ctx.getSource().getLevel();
        RedstoneMode mode = RedstoneMode.byId(StringArgumentType.getString(ctx, "mode"));
        com.nstut.simplyspeakers.speakers.ServerSpeakerControlService.policyControl(level.getServer(), level, fullKey,
                com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_REDSTONE_MODE,
                "", mode != null ? mode.ordinal() : 0, 0.0f, null);
        ctx.getSource().sendSuccess(() -> Component.literal("Redstone mode: " + (mode != null ? mode.id() : "default")), false);
        return 1;
    }

    private static int setAccessMode(CommandContext<CommandSourceStack> ctx) {
        SpeakerAccess access = SpeakerAccess.byId(StringArgumentType.getString(ctx, "mode"));
        SpeakerApi.setAccessMode(ctx.getSource().getLevel(), BlockPosArgument.getBlockPos(ctx, "pos"), access);
        ctx.getSource().sendSuccess(() -> Component.literal("Access mode: " + (access != null ? access.id() : "public")), false);
        return 1;
    }

    private static int listAudio(CommandContext<CommandSourceStack> ctx) {
        var fileManager = SimplySpeakers.getAudioFileManager();
        if (fileManager == null || fileManager.getManifest().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Audio library is empty."), false);
            return 0;
        }
        for (var meta : fileManager.getManifest().values()) {
            String label = meta.effectiveDisplayName()
                    + (meta.getCategory().isEmpty() ? "" : " [" + meta.getCategory() + "]");
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "- " + label + " — " + meta.getUuid() + " (" + meta.getOriginalFilename() + ")"), false);
        }
        return fileManager.getManifest().size();
    }

    private static int renameAudio(CommandContext<CommandSourceStack> ctx) {
        String audioId = StringArgumentType.getString(ctx, "audioId");
        String displayName = StringArgumentType.getString(ctx, "displayName");
        var fileManager = SimplySpeakers.getAudioFileManager();
        if (fileManager == null || !fileManager.getManifest().containsKey(audioId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown audio id."));
            return 0;
        }
        fileManager.updateAudioMetadata(audioId,
                fileManager.getManifest().get(audioId).withDisplayName(displayName));
        ctx.getSource().sendSuccess(() -> Component.literal("Renamed to " + displayName), false);
        return 1;
    }
}
