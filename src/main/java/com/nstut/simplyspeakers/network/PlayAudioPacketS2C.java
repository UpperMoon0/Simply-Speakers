package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PlayAudioPacketS2C {
    private final BlockPos pos;
    private final String audioPath;

    public PlayAudioPacketS2C(BlockPos pos, String audioPath) {
        this.pos = pos;
        this.audioPath = audioPath;
    }

    public static void encode(PlayAudioPacketS2C pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.audioPath);
    }

    public static PlayAudioPacketS2C decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String audioPath = buf.readUtf();
        return new PlayAudioPacketS2C(pos, audioPath);
    }

    public static void handle(PlayAudioPacketS2C pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // Play the audio for the specific speaker block.
                ClientAudioPlayer.play(pkt.pos, pkt.audioPath);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}