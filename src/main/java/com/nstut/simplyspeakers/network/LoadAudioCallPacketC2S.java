package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

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

    public static void encode(LoadAudioCallPacketC2S packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUtf(packet.musicPath);
    }

    public static LoadAudioCallPacketC2S decode(FriendlyByteBuf buffer) {
        return new LoadAudioCallPacketC2S(buffer.readBlockPos(), buffer.readUtf(32767));
    }

    public static void handle(LoadAudioCallPacketC2S packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                net.minecraft.server.level.ServerLevel level = player.serverLevel(); // Use serverLevel()
                if (level != null) { // Check level validity
                    BlockEntity blockEntity = level.getBlockEntity(packet.pos);
                    if (blockEntity instanceof SpeakerBlockEntity speakerEntity) { // Use pattern matching
                        try {
                            speakerEntity.setAudioPath(packet.musicPath);
                        } catch (Exception e) {
                            // Log the exception properly
                            LOGGER.severe("Error setting audio path for speaker at " + packet.pos + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
