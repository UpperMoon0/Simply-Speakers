package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.math.AudioMath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.architectury.networking.NetworkManager;

import java.util.function.Supplier;

public class UpdateMaxVolumePacketC2S {

    private final BlockPos pos;
    private final float maxVolume;

    public UpdateMaxVolumePacketC2S(BlockPos pos, float maxVolume) {
        this.pos = pos;
        this.maxVolume = maxVolume;
    }

    public UpdateMaxVolumePacketC2S(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.maxVolume = AudioMath.sanitizeFloat(buf.readFloat(), 1.0f);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeFloat(AudioMath.sanitizeFloat(this.maxVolume, 1.0f));
    }

    public static void handle(UpdateMaxVolumePacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canModify(player, pkt.pos)) {
                return;
            }

            ServerLevel level = player.serverLevel();
            BlockEntity blockEntity = level.getBlockEntity(pkt.pos);
            if (blockEntity instanceof SpeakerBlockEntity speakerEntity) {
                speakerEntity.setMaxVolume(pkt.maxVolume);
            }
        });
    }
}