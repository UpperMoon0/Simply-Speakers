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

/** Movement-only snapshot. Never creates a decoder or adds playback membership. */
public final class PortableSpeakerPositionPacketS2C implements CustomPacketPayload {
    public static final Type<PortableSpeakerPositionPacketS2C> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "portable_speaker_position"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PortableSpeakerPositionPacketS2C> STREAM_CODEC = StreamCodec.of(PortableSpeakerPositionPacketS2C::encode, PortableSpeakerPositionPacketS2C::decode);
    private final BlockPos pos;
    private final PortableEmitterSnapshot snapshot;

    public PortableSpeakerPositionPacketS2C(BlockPos pos, PortableEmitterSnapshot snapshot) {
        this.pos = pos.immutable();
        this.snapshot = snapshot;
    }
    public static void encode(RegistryFriendlyByteBuf buffer, PortableSpeakerPositionPacketS2C packet) {
        buffer.writeBlockPos(packet.pos);
        PortableEmitterCodec.write(buffer, packet.snapshot);
    }
    public static PortableSpeakerPositionPacketS2C decode(RegistryFriendlyByteBuf buffer) {
        return new PortableSpeakerPositionPacketS2C(buffer.readBlockPos(), PortableEmitterCodec.read(buffer));
    }
    public static void handle(PortableSpeakerPositionPacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            ClientPortableSpeakers.update(packet.pos, packet.snapshot);
        });
    }
    public static void sendToPlayer(ServerPlayer player, PortableSpeakerPositionPacketS2C packet) { NetworkManager.sendToPlayer(player, packet); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public BlockPos getPos() { return pos; }
    public PortableEmitterSnapshot getSnapshot() { return snapshot; }
}
