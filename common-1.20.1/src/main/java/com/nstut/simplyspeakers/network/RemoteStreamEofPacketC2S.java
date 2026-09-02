package com.nstut.simplyspeakers.network;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * Client -> server report that a direct HTTP(S) audio stream reached natural EOF.
 * The report carries the server-assigned shared-state key and playback session
 * generation from the originating {@code PlayAudioPacketS2C}. The server honors
 * it only when the reporter is a subscribed player of the referenced state,
 * which must currently be playing the reported (non-looping) URL track with the
 * reported generation, before advancing its playlist or stopping playback.
 */
public class RemoteStreamEofPacketC2S {
    private final String fullStateKey;
    private final int playbackGeneration;
    private final String audioId;

    public RemoteStreamEofPacketC2S(String fullStateKey, int playbackGeneration, String audioId) {
        this.fullStateKey = fullStateKey != null ? fullStateKey : "";
        this.playbackGeneration = playbackGeneration;
        this.audioId = audioId != null ? audioId : "";
    }

    public RemoteStreamEofPacketC2S(FriendlyByteBuf buf) {
        this.fullStateKey = buf.readUtf();
        this.playbackGeneration = buf.readVarInt();
        this.audioId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.fullStateKey);
        buf.writeVarInt(this.playbackGeneration);
        buf.writeUtf(this.audioId);
    }

    public static void handle(RemoteStreamEofPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            com.nstut.simplyspeakers.speakers.ServerPlaybackManager.handleRemoteStreamEofReport(player, pkt.fullStateKey, pkt.playbackGeneration, pkt.audioId);
        });
    }
}
