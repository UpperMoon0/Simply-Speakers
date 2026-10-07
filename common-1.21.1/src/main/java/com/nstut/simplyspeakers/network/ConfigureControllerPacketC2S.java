package com.nstut.simplyspeakers.network;
import com.nstut.simplyspeakers.blocks.entities.RedstoneControllerBlockEntity;
import com.nstut.simplyspeakers.control.ControllerAction;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class ConfigureControllerPacketC2S implements CustomPacketPayload {
public static final Type<ConfigureControllerPacketC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("simplyspeakers","configure_controller"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ConfigureControllerPacketC2S> STREAM_CODEC = StreamCodec.of(ConfigureControllerPacketC2S::encode,ConfigureControllerPacketC2S::decode);
    private final BlockPos pos, proxyPos;
    private final String network, action, audioId;
    private final boolean proxy, restart;
    private final float ceiling;
    public ConfigureControllerPacketC2S(BlockPos pos, String network, boolean proxy, BlockPos proxyPos,
                                       String action, String audioId, boolean restart, float ceiling) {
        this.pos=pos; this.network=network; this.proxy=proxy; this.proxyPos=proxyPos;
        this.action=action; this.audioId=audioId; this.restart=restart; this.ceiling=ceiling;
    }
public static ConfigureControllerPacketC2S decode(RegistryFriendlyByteBuf buffer) { return new ConfigureControllerPacketC2S(buffer.readBlockPos(), buffer.readUtf(64), buffer.readBoolean(), buffer.readBlockPos(), buffer.readUtf(32), buffer.readUtf(128), buffer.readBoolean(), buffer.readFloat()); }
    public static void encode(RegistryFriendlyByteBuf buffer, ConfigureControllerPacketC2S packet) {
        buffer.writeBlockPos(packet.pos); buffer.writeUtf(packet.network,64);
        buffer.writeBoolean(packet.proxy); buffer.writeBlockPos(packet.proxyPos);
        buffer.writeUtf(packet.action,32); buffer.writeUtf(packet.audioId,128);
        buffer.writeBoolean(packet.restart); buffer.writeFloat(packet.ceiling);
    }
    public static void handle(ConfigureControllerPacketC2S packet, NetworkManager.PacketContext context) {

        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        context.queue(() -> {
            if (!player.level().hasChunkAt(packet.pos)) return;
            if (player.level().getBlockEntity(packet.pos) instanceof RedstoneControllerBlockEntity controller) {
                boolean valid = java.util.Arrays.stream(ControllerAction.values()).anyMatch(a -> a.id().equals(packet.action));
                if (valid) controller.configure(player, packet.network, packet.proxy, packet.proxyPos,
                    ControllerAction.byId(packet.action), packet.audioId, packet.restart, packet.ceiling);
            }
        });
    }
@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
