package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class SpeakerBlockEntityPacketS2C {
    private final BlockPos pos;
    private final String musicPath;

    public SpeakerBlockEntityPacketS2C(BlockPos pos, String musicPath) {
        this.pos = pos;
        this.musicPath = musicPath;
    }

    public static void encode(SpeakerBlockEntityPacketS2C message, FriendlyByteBuf buf) {
        buf.writeBlockPos(message.pos);
        buf.writeUtf(message.musicPath);
    }

    public static SpeakerBlockEntityPacketS2C decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String musicPath = buf.readUtf(32767);
        return new SpeakerBlockEntityPacketS2C(pos, musicPath);
    }

    public static void handle(SpeakerBlockEntityPacketS2C message, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (net.minecraft.client.Minecraft.getInstance().level != null) {
                var blockEntity = net.minecraft.client.Minecraft.getInstance().level.getBlockEntity(message.pos);
                if (blockEntity instanceof SpeakerBlockEntity speaker) {
                    // Create a CompoundTag containing the updated music path
                    CompoundTag tag = new CompoundTag();
                    tag.putString("AudioPath", message.musicPath);
                    // Use the built‑in update method to update the client block entity
                    speaker.handleUpdateTag(tag);
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}