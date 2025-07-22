package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SendAudioListPacketS2C {
    private final List<String> audioList;

    public SendAudioListPacketS2C(List<String> audioList) {
        this.audioList = audioList;
    }

    public SendAudioListPacketS2C(FriendlyByteBuf buf) {
        this.audioList = buf.readList(FriendlyByteBuf::readUtf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(audioList, FriendlyByteBuf::writeUtf);
    }

    public static void handle(SendAudioListPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        context.queue(() -> {
            if (Minecraft.getInstance().screen instanceof SpeakerScreen screen) {
                List<AudioFileMetadata> files = pkt.audioList.stream()
                        .map(id -> SimplySpeakers.getAudioFileManager().getManifest().get(id))
                        .collect(Collectors.toList());
                screen.getAudioListWidget().setAudioList(files);
            }
        });
    }
}