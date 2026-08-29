package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client -> server report that a direct HTTP(S) audio stream reached natural EOF.
 * The server validates the reporter is a subscribed player of a state currently
 * playing this URL track before advancing its playlist or stopping playback.
 */
public class RemoteStreamEofPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RemoteStreamEofPacketC2S> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "remote_stream_eof"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteStreamEofPacketC2S> STREAM_CODEC =
            StreamCodec.of(RemoteStreamEofPacketC2S::encode, RemoteStreamEofPacketC2S::decode);

    private final String audioId;

    public RemoteStreamEofPacketC2S(String audioId) {
        this.audioId = audioId != null ? audioId : "";
    }

    public static void encode(RegistryFriendlyByteBuf buffer, RemoteStreamEofPacketC2S packet) {
        buffer.writeUtf(packet.audioId);
    }

    public static RemoteStreamEofPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new RemoteStreamEofPacketC2S(buffer.readUtf());
    }

    public static void handle(RemoteStreamEofPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            com.nstut.simplyspeakers.speakers.ServerPlaybackManager.handleRemoteStreamEofReport(player, packet.audioId);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
