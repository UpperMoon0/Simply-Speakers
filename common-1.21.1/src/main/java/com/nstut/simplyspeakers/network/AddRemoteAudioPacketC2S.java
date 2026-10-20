package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Adds an owned Library bookmark; never starts or queues playback. */
public class AddRemoteAudioPacketC2S implements CustomPacketPayload {
    public static final Type<AddRemoteAudioPacketC2S> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID,"add_remote_audio"));
    public static final StreamCodec<RegistryFriendlyByteBuf,AddRemoteAudioPacketC2S> STREAM_CODEC =
            StreamCodec.of(AddRemoteAudioPacketC2S::encode,AddRemoteAudioPacketC2S::decode);
    private final BlockPos pos;
    private final String url, name;
    public AddRemoteAudioPacketC2S(BlockPos pos,String url,String name) {this.pos=pos;this.url=url;this.name=name;}
    public static void encode(RegistryFriendlyByteBuf buf,AddRemoteAudioPacketC2S packet) {
        buf.writeBlockPos(packet.pos);buf.writeUtf(packet.url,256);buf.writeUtf(packet.name,128);
    }
    public static AddRemoteAudioPacketC2S decode(RegistryFriendlyByteBuf buf) {
        return new AddRemoteAudioPacketC2S(buf.readBlockPos(),buf.readUtf(256),buf.readUtf(128));
    }
    public static void handle(AddRemoteAudioPacketC2S packet,NetworkManager.PacketContext context) {
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
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
}
