package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ToggleLoopPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleLoopPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "toggle_loop"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleLoopPacketC2S> STREAM_CODEC = 
        StreamCodec.of(ToggleLoopPacketC2S::encode, ToggleLoopPacketC2S::decode);

    private final BlockPos pos;
    private final boolean isLooping;

    public ToggleLoopPacketC2S(BlockPos pos, boolean isLooping) {
        this.pos = pos;
        this.isLooping = isLooping;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, ToggleLoopPacketC2S packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeBoolean(packet.isLooping);
    }

    public static ToggleLoopPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new ToggleLoopPacketC2S(buffer.readBlockPos(), buffer.readBoolean());
    }

    public static void handle(ToggleLoopPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (player != null) {
                ServerLevel level = player.serverLevel();
                if (level.isLoaded(packet.pos)) {
                    BlockEntity blockEntity = level.getBlockEntity(packet.pos);
                    if (blockEntity instanceof SpeakerBlockEntity speakerEntity) {
                        speakerEntity.setLooping(packet.isLooping);
                    }
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
