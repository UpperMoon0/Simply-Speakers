package com.nstut.simplyspeakers.network;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * Client -> server report that a direct HTTP(S) audio stream reached natural EOF.
 * The server validates the reporter is a subscribed player of a state currently
 * playing this URL track before advancing its playlist or stopping playback.
 */
public class RemoteStreamEofPacketC2S {
    private final String audioId;

    public RemoteStreamEofPacketC2S(String audioId) {
        this.audioId = audioId != null ? audioId : "";
    }

    public RemoteStreamEofPacketC2S(FriendlyByteBuf buf) {
        this.audioId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.audioId);
    }

    public static void handle(RemoteStreamEofPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            com.nstut.simplyspeakers.speakers.ServerPlaybackManager.handleRemoteStreamEofReport(player, pkt.audioId);
        });
    }
}
