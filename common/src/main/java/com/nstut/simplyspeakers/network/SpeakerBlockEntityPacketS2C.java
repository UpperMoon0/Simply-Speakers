package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

public class SpeakerBlockEntityPacketS2C {
    private final BlockPos pos;
    private final String audioId;

    public SpeakerBlockEntityPacketS2C(BlockPos pos, String audioId) {
        this.pos = pos;
        this.audioId = audioId;
    }

    public SpeakerBlockEntityPacketS2C(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.audioId = buf.readUtf();
    }

    public static void encode(SpeakerBlockEntityPacketS2C message, FriendlyByteBuf buf) {
        buf.writeBlockPos(message.pos);
        buf.writeUtf(message.audioId);
    }

    // Updated handle method for Architectury
    public static void handle(SpeakerBlockEntityPacketS2C message, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext ctx = ctxSupplier.get();
        ctx.queue(() -> {
            if (Minecraft.getInstance().level != null) {
                var blockEntity = Minecraft.getInstance().level.getBlockEntity(message.pos);
                if (blockEntity instanceof SpeakerBlockEntity speaker) {
                    // Create a CompoundTag containing the updated music path
                    CompoundTag tag = new CompoundTag();
                    tag.putString("AudioID", message.audioId);
                    speaker.load(tag); // Use load to update the client-side entity state from the NBT tag
                }
            }
        });
        // For S2C packets, Architectury handles setPacketHandled implicitly when queueing on the client.
    }
}