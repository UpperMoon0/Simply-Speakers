package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager; // Changed import


import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
// Removed: import net.minecraftforge.api.distmarker.Dist;
// Removed: import net.minecraftforge.fml.DistExecutor;
// Removed: import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayAudioPacketS2C {
    private final BlockPos pos;
    private final String audioPath;
    private final float playbackPositionSeconds;

    public PlayAudioPacketS2C(BlockPos pos, String audioPath, float playbackPositionSeconds) {
        this.pos = pos;
        this.audioPath = audioPath;
        this.playbackPositionSeconds = playbackPositionSeconds;
    }

    public PlayAudioPacketS2C(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.audioPath = buf.readUtf();
        this.playbackPositionSeconds = buf.readFloat();
    }

    public static void encode(PlayAudioPacketS2C pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.audioPath);
        buf.writeFloat(pkt.playbackPositionSeconds);
    }

    // Updated handle method for Architectury
    public static void handle(PlayAudioPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        // Ensure this code runs only on the client side
        if (Minecraft.getInstance().level.isClientSide) {
            context.queue(() -> {
                // Play the audio for the specific speaker block, passing the start position.
                ClientAudioPlayer.play(pkt.pos, pkt.audioPath, pkt.playbackPositionSeconds);
            });
        }
        // For S2C packets, Architectury handles setPacketHandled implicitly when queueing on the client.
    }
}
