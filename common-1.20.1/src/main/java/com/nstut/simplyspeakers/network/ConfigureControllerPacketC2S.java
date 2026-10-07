package com.nstut.simplyspeakers.network;
import com.nstut.simplyspeakers.blocks.entities.RedstoneControllerBlockEntity;
import com.nstut.simplyspeakers.control.ControllerAction;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;

public class ConfigureControllerPacketC2S  {

    private final BlockPos pos, proxyPos;
    private final String network, action, audioId;
    private final boolean proxy, restart;
    private final float ceiling;
    public ConfigureControllerPacketC2S(BlockPos pos, String network, boolean proxy, BlockPos proxyPos,
                                       String action, String audioId, boolean restart, float ceiling) {
        this.pos=pos; this.network=network; this.proxy=proxy; this.proxyPos=proxyPos;
        this.action=action; this.audioId=audioId; this.restart=restart; this.ceiling=ceiling;
    }
public ConfigureControllerPacketC2S(FriendlyByteBuf buffer) { this(buffer.readBlockPos(), buffer.readUtf(64), buffer.readBoolean(), buffer.readBlockPos(), buffer.readUtf(32), buffer.readUtf(128), buffer.readBoolean(), buffer.readFloat()); }
    public void encode(FriendlyByteBuf buffer) {
        ConfigureControllerPacketC2S packet = this;
        buffer.writeBlockPos(packet.pos); buffer.writeUtf(packet.network,64);
        buffer.writeBoolean(packet.proxy); buffer.writeBlockPos(packet.proxyPos);
        buffer.writeUtf(packet.action,32); buffer.writeUtf(packet.audioId,128);
        buffer.writeBoolean(packet.restart); buffer.writeFloat(packet.ceiling);
    }
    public static void handle(ConfigureControllerPacketC2S packet, java.util.function.Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
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

}
