package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class StopAudioPacketS2C implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StopAudioPacketS2C> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "stop_audio"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, StopAudioPacketS2C> STREAM_CODEC = 
        StreamCodec.of(StopAudioPacketS2C::encode, StopAudioPacketS2C::decode);

    private final BlockPos pos;

    public StopAudioPacketS2C(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, StopAudioPacketS2C packet) {
        buffer.writeBlockPos(packet.pos);
    }

    public static StopAudioPacketS2C decode(RegistryFriendlyByteBuf buffer) {
        return new StopAudioPacketS2C(buffer.readBlockPos());
    }

    public static void handle(StopAudioPacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            // Stop the audio tied to the specific speaker block.
            ClientAudioPlayer.stop(packet.pos);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
