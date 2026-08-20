package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ToggleLoopPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleLoopPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "toggle_loop"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleLoopPacketC2S> STREAM_CODEC = 
        StreamCodec.of(ToggleLoopPacketC2S::encode, ToggleLoopPacketC2S::decode);

    private final BlockPos blockPos;
    private final boolean looping;

    public ToggleLoopPacketC2S(BlockPos blockPos, boolean looping) {
        this.blockPos = blockPos;
        this.looping = looping;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, ToggleLoopPacketC2S packet) {
        buffer.writeBlockPos(packet.blockPos);
        buffer.writeBoolean(packet.looping);
    }

    public static ToggleLoopPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new ToggleLoopPacketC2S(buffer.readBlockPos(), buffer.readBoolean());
    }

    public static void handle(ToggleLoopPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canModify(player, packet.blockPos)) {
                return;
            }

            Level level = player.level();
            BlockEntity blockEntity = level.getBlockEntity(packet.blockPos);
            if (blockEntity instanceof SpeakerBlockEntity speakerEntity) {
                speakerEntity.setLooping(packet.looping);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}



