package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Supplier;
import java.util.logging.Logger;

public class LoadAudioCallPacketC2S {

    private static final Logger LOGGER = Logger.getLogger(LoadAudioCallPacketC2S.class.getName());

    private final BlockPos pos;
    private final String musicPath;

    public LoadAudioCallPacketC2S(BlockPos pos, String musicPath) {
        this.pos = pos;
        this.musicPath = musicPath;
    }

    public LoadAudioCallPacketC2S(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.musicPath = buffer.readUtf();
    }

    public static void encode(LoadAudioCallPacketC2S packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUtf(packet.musicPath);
    }

    public static void handle(LoadAudioCallPacketC2S packet, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player != null) {
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    BlockEntity blockEntity = serverLevel.getBlockEntity(packet.pos);
                    if (blockEntity instanceof SpeakerBlockEntity speakerEntity) {
                        try {
                            speakerEntity.setAudioPath(packet.musicPath);
                        } catch (Exception e) {
                            LOGGER.severe("Error processing LoadAudioCallPacketC2S for speaker at " + packet.pos + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }
}
