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
    private final String musicPath; // Assuming this is still needed, or a CompoundTag for more complex data

    public SpeakerBlockEntityPacketS2C(BlockPos pos, String musicPath) {
        this.pos = pos;
        this.musicPath = musicPath;
    }

    public SpeakerBlockEntityPacketS2C(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.musicPath = buf.readUtf();
    }

    public static void encode(SpeakerBlockEntityPacketS2C message, FriendlyByteBuf buf) {
        buf.writeBlockPos(message.pos);
        buf.writeUtf(message.musicPath);
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
                    tag.putString("AudioPath", message.musicPath);
                    // Use the built-in update method to update the client block entity
                    // or a custom method if SpeakerBlockEntity has one for this purpose.
                    // speaker.handleUpdateTag(tag); // This is a common vanilla method
                    // If you have a specific method like setAudioPathClient, use that:
                    speaker.setAudioPath(message.musicPath); // Assuming direct update or a client-specific method
                    // Or if it needs more complex data from the packet:
                    // speaker.updateClientData(message.someOtherData);
                }
            }
        });
        // For S2C packets, Architectury handles setPacketHandled implicitly when queueing on the client.
    }
}