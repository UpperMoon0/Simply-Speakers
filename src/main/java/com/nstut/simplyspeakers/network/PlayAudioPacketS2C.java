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
    private final float playbackPositionSeconds; // Added field

    // Updated constructor
    public PlayAudioPacketS2C(BlockPos pos, String audioPath, float playbackPositionSeconds) {
        this.pos = pos;
        this.audioPath = audioPath;
        this.playbackPositionSeconds = playbackPositionSeconds;
    }

    // Updated encode method
    public static void encode(PlayAudioPacketS2C pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.audioPath);
        buf.writeFloat(pkt.playbackPositionSeconds); // Write the float
    }

    // Updated decode method
    public static PlayAudioPacketS2C decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String audioPath = buf.readUtf();
        float playbackPositionSeconds = buf.readFloat(); // Read the float
        return new PlayAudioPacketS2C(pos, audioPath, playbackPositionSeconds); // Pass to constructor
    }

    // Updated handle method
    public static void handle(PlayAudioPacketS2C pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // Play the audio for the specific speaker block, passing the start position.
                // Note: ClientAudioPlayer.play will need to be updated in Phase 2 to accept this third argument.
                ClientAudioPlayer.play(pkt.pos, pkt.audioPath, pkt.playbackPositionSeconds);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
