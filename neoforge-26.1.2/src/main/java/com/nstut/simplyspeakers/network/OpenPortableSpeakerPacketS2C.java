package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.ClientPortableSpeakers;
import com.nstut.simplyspeakers.portable.PortableEmitterSnapshot;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Opens the same player screen for a server-authorized inventory endpoint. */
public final class OpenPortableSpeakerPacketS2C implements CustomPacketPayload {
    public static final Type<OpenPortableSpeakerPacketS2C> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "open_portable_speaker"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPortableSpeakerPacketS2C> STREAM_CODEC = StreamCodec.of(OpenPortableSpeakerPacketS2C::encode, OpenPortableSpeakerPacketS2C::decode);
    private final BlockPos pos;
    private final UUID identity;
    private final String speakerId;
    private final String fullStateKey;

    public OpenPortableSpeakerPacketS2C(BlockPos pos, UUID identity, String speakerId, String fullStateKey) {
        this.pos = pos.immutable();
        this.identity = identity;
        this.speakerId = speakerId == null ? "" : speakerId;
        this.fullStateKey = fullStateKey;
    }
    public static void encode(RegistryFriendlyByteBuf buffer, OpenPortableSpeakerPacketS2C packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUUID(packet.identity);
        buffer.writeUtf(packet.speakerId, 256);
        buffer.writeUtf(packet.fullStateKey, 512);
    }
    public static OpenPortableSpeakerPacketS2C decode(RegistryFriendlyByteBuf buffer) {
        return new OpenPortableSpeakerPacketS2C(buffer.readBlockPos(), buffer.readUUID(), buffer.readUtf(256), buffer.readUtf(512));
    }
    public static void handle(OpenPortableSpeakerPacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            var client = Minecraft.getInstance();
            if (client.level == null || client.player == null || !ClientPortableSpeakers.isPortableToken(packet.pos)) return;
            var endpoint = new com.nstut.simplyspeakers.portable.PortableSpeakerEndpoint(packet.identity, packet.pos, packet.speakerId);
            endpoint.setLevel(client.level);
            // Ignore delayed opens after the client changed dimensions.
            if (!endpoint.getFullStateKey().equals(packet.fullStateKey)) return;
            if (client.screen instanceof com.nstut.simplyspeakers.client.screens.SpeakerScreen screen
                    && screen.refreshPortableEndpoint(endpoint)) return;
            client.setScreen(new com.nstut.simplyspeakers.client.screens.SpeakerScreen(endpoint));
        });
    }
    public static void sendToPlayer(ServerPlayer player, OpenPortableSpeakerPacketS2C packet) { NetworkManager.sendToPlayer(player, packet); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public BlockPos getPos() { return pos; }
    public UUID getIdentity() { return identity; }
    public String getSpeakerId() { return speakerId; }
    public String getFullStateKey() { return fullStateKey; }
}
