package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.portable.PortableEmitterSnapshot;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import java.util.function.Supplier;

/** Movement-only snapshot. Never creates a decoder or adds playback membership. */
public final class PortableSpeakerPositionPacketS2C {
    private final BlockPos pos;
    private final PortableEmitterSnapshot snapshot;

    public PortableSpeakerPositionPacketS2C(BlockPos pos, PortableEmitterSnapshot snapshot) {
        this.pos = pos.immutable();
        this.snapshot = snapshot;
    }
    public PortableSpeakerPositionPacketS2C(FriendlyByteBuf buffer) { this(buffer.readBlockPos(), PortableEmitterCodec.read(buffer)); }
    public static void encode(PortableSpeakerPositionPacketS2C packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        PortableEmitterCodec.write(buffer, packet.snapshot);
    }
    public static void handle(PortableSpeakerPositionPacketS2C packet, Supplier<NetworkManager.PacketContext> context) {
        context.get().queue(() -> com.nstut.simplyspeakers.client.ClientPortableSpeakerPackets.position(packet));
    }
    public static void sendToPlayer(ServerPlayer player, PortableSpeakerPositionPacketS2C packet) { PacketRegistries.CHANNEL.sendToPlayer(player, packet); }
    public BlockPos getPos() { return pos; }
    public PortableEmitterSnapshot getSnapshot() { return snapshot; }
}
