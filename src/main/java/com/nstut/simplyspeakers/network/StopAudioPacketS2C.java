package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class StopAudioPacketS2C {
    private final BlockPos pos;

    public StopAudioPacketS2C(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(StopAudioPacketS2C msg, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(msg.pos);
    }

    public static StopAudioPacketS2C decode(FriendlyByteBuf buffer) {
        return new StopAudioPacketS2C(buffer.readBlockPos());
    }

    public static void handle(StopAudioPacketS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // Stop the audio tied to the specific speaker block.
                ClientAudioPlayer.stop(msg.pos);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}