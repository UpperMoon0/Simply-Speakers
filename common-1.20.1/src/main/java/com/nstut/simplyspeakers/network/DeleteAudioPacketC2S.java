package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Supplier;

public class DeleteAudioPacketC2S {
    private final String audioId;

    public DeleteAudioPacketC2S(String audioId) {
        this.audioId = audioId;
    }

    public DeleteAudioPacketC2S(FriendlyByteBuf buf) {
        this.audioId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(audioId);
    }

    public static void handle(DeleteAudioPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            SimplySpeakers.getAudioFileManager().deleteAudioFile(pkt.audioId, player.getUUID().toString());
            List<AudioFileMetadata> audioList = SimplySpeakers.getAudioFileManager()
                    .getAudioListForPlayer(player.getUUID().toString());
            PacketRegistries.CHANNEL.sendToPlayer(player, new SendAudioListPacketS2C(audioList));
        });
    }
}
