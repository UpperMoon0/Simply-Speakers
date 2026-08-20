package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
import com.nstut.simplyspeakers.math.AudioMath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.architectury.networking.NetworkManager;

import java.util.function.Supplier;

public class UpdateProxyAudioDropoffPacketC2S {

    private final BlockPos pos;
    private final float audioDropoff;

    public UpdateProxyAudioDropoffPacketC2S(BlockPos pos, float audioDropoff) {
        this.pos = pos;
        this.audioDropoff = audioDropoff;
    }

    public UpdateProxyAudioDropoffPacketC2S(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.audioDropoff = AudioMath.sanitizeFloat(buf.readFloat(), 1.0f);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeFloat(AudioMath.sanitizeFloat(this.audioDropoff, 1.0f));
    }

    public static void handle(UpdateProxyAudioDropoffPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canModify(player, pkt.pos)) {
                return;
            }

            ServerLevel level = player.serverLevel();
            BlockEntity blockEntity = level.getBlockEntity(pkt.pos);
            if (blockEntity instanceof ProxySpeakerBlockEntity proxySpeakerEntity) {
                proxySpeakerEntity.setAudioDropoff(pkt.audioDropoff);
            }
        });
    }
}