package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.ClientPortableSpeakers;
import com.nstut.simplyspeakers.portable.PortableEmitterSnapshot;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import java.util.function.Supplier;

/** Opens the same player screen for a server-authorized inventory endpoint. */
public final class OpenPortableSpeakerPacketS2C {
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
    public OpenPortableSpeakerPacketS2C(FriendlyByteBuf buffer) { this(buffer.readBlockPos(), buffer.readUUID(), buffer.readUtf(256), buffer.readUtf(512)); }
    public static void encode(OpenPortableSpeakerPacketS2C packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUUID(packet.identity);
        buffer.writeUtf(packet.speakerId, 256);
        buffer.writeUtf(packet.fullStateKey, 512);
    }
    public static void handle(OpenPortableSpeakerPacketS2C packet, Supplier<NetworkManager.PacketContext> context) {
        context.get().queue(() -> {
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
    public static void sendToPlayer(ServerPlayer player, OpenPortableSpeakerPacketS2C packet) { PacketRegistries.CHANNEL.sendToPlayer(player, packet); }
    public BlockPos getPos() { return pos; }
    public UUID getIdentity() { return identity; }
    public String getSpeakerId() { return speakerId; }
    public String getFullStateKey() { return fullStateKey; }
}
