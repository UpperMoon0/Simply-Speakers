package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class SendAudioListPacketS2C implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SendAudioListPacketS2C> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "send_audio_list"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, SendAudioListPacketS2C> STREAM_CODEC = 
        StreamCodec.of(SendAudioListPacketS2C::encode, SendAudioListPacketS2C::decode);

    private final List<AudioFileMetadata> audioList;

    public SendAudioListPacketS2C(List<AudioFileMetadata> audioList) {
        this.audioList = audioList;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, SendAudioListPacketS2C packet) {
        buffer.writeCollection(packet.audioList, (b, data) -> data.encode(b));
    }

    public static SendAudioListPacketS2C decode(RegistryFriendlyByteBuf buffer) {
        return new SendAudioListPacketS2C(buffer.readList(AudioFileMetadata::decode));
    }

    public static void handle(SendAudioListPacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (Minecraft.getInstance().screen instanceof SpeakerScreen screen) {
                screen.updateAudioList(packet.audioList);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
