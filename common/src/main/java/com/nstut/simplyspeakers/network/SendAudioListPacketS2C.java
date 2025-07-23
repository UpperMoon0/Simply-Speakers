package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.function.Supplier;

public class SendAudioListPacketS2C {
    private final List<AudioFileMetadata> audioList;

    public SendAudioListPacketS2C(List<AudioFileMetadata> audioList) {
        this.audioList = audioList;
    }

    public SendAudioListPacketS2C(FriendlyByteBuf buf) {
        this.audioList = buf.readList(AudioFileMetadata::decode);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(audioList, (b, data) -> data.encode(b));
    }

    public static void handle(SendAudioListPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        context.queue(() -> {
            if (Minecraft.getInstance().screen instanceof SpeakerScreen screen) {
                screen.updateAudioList(pkt.audioList);
            }
        });
    }
}