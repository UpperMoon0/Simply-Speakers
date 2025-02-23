package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
import java.util.logging.Logger;

public class AudioPathPacketC2S {

    private static final Logger LOGGER = Logger.getLogger(AudioPathPacketC2S.class.getName());

    private final BlockPos pos;
    private final String musicPath;

    public AudioPathPacketC2S(BlockPos pos, String musicPath) {
        this.pos = pos;
        this.musicPath = musicPath;
    }

    public static void encode(AudioPathPacketC2S packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUtf(packet.musicPath);
    }

    public static AudioPathPacketC2S decode(FriendlyByteBuf buffer) {
        return new AudioPathPacketC2S(buffer.readBlockPos(), buffer.readUtf(32767));
    }

    public static void handle(AudioPathPacketC2S packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                try (var level = player.level()) { // only if player.level() returns an AutoCloseable resource
                    BlockEntity blockEntity = level.getBlockEntity(packet.pos);
                    if (blockEntity instanceof SpeakerBlockEntity) {
                        ((SpeakerBlockEntity) blockEntity).setAudioPath(packet.musicPath);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        context.setPacketHandled(true);
    }
}