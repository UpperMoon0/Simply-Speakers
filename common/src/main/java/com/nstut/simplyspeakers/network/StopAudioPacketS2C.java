package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

public class StopAudioPacketS2C {
    private final BlockPos pos;

    public StopAudioPacketS2C(BlockPos pos) {
        this.pos = pos;
    }

    // Constructor for decoding
    public StopAudioPacketS2C(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public static void encode(StopAudioPacketS2C pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
    }

    // Updated handle method for Architectury
    public static void handle(StopAudioPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        // Always queue on the client, Architectury ensures this is only called on the correct side
        context.queue(() -> {
            // Stop the audio tied to the specific speaker block.
            ClientAudioPlayer.stop(pkt.pos);
        });
        // For S2C packets, Architectury handles setPacketHandled implicitly when queueing on the client.
    }
}