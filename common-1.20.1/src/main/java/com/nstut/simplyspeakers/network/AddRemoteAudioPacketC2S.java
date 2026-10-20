package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Adds an owned Library bookmark; never starts or queues playback. */
public class AddRemoteAudioPacketC2S {
    private final BlockPos pos;
    private final String url, name;
    public AddRemoteAudioPacketC2S(BlockPos pos,String url,String name) {this.pos=pos;this.url=url;this.name=name;}
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);buf.writeUtf(url,256);buf.writeUtf(name,128);
    }
    public AddRemoteAudioPacketC2S(FriendlyByteBuf buf) {
        this(buf.readBlockPos(),buf.readUtf(256),buf.readUtf(128));
    }
    public static void handle(AddRemoteAudioPacketC2S packet,java.util.function.Supplier<NetworkManager.PacketContext> contexts) {
        NetworkManager.PacketContext context=contexts.get();
        ServerPlayer player=(ServerPlayer)context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canControlSpeaker(player,packet.pos)) return;
            try {
                var manager=SimplySpeakers.getAudioFileManager();
                if(manager==null)return;
                manager.addRemoteAudio(packet.url,packet.name,player.getUUID().toString());
                manager.sendAudioList(player,packet.pos);
            } catch (java.io.IOException | IllegalArgumentException error) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "gui.simplyspeakers.library.add_failed"),true);
            }
        });
    }
}
