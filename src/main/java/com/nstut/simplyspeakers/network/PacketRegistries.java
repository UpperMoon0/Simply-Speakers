package com.nstut.simplyspeakers.network;

import net.minecraft.core.BlockPos;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.resources.ResourceLocation;

public class PacketRegistries {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("simplyspeakers", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        CHANNEL.registerMessage(
                0,
                MusicPathPacketC2S.class,
                MusicPathPacketC2S::encode,
                MusicPathPacketC2S::decode,
                MusicPathPacketC2S::handle
        );
        CHANNEL.registerMessage(
                1,
                StopAudioPacketS2C.class,
                StopAudioPacketS2C::encode,
                StopAudioPacketS2C::decode,
                StopAudioPacketS2C::handle
        );
        CHANNEL.registerMessage(
                2,
                PlayAudioPacketS2C.class,
                PlayAudioPacketS2C::encode,
                PlayAudioPacketS2C::decode,
                PlayAudioPacketS2C::handle
        );
        CHANNEL.registerMessage(
                3,
                SpeakerBlockEntityPacketS2C.class,
                SpeakerBlockEntityPacketS2C::encode,
                SpeakerBlockEntityPacketS2C::decode,
                SpeakerBlockEntityPacketS2C::handle
        );
    }

    public static void sendMusicPathToServer(BlockPos pos, String musicPath) {
        CHANNEL.sendToServer(new MusicPathPacketC2S(pos, musicPath));
    }

    public static void sendToClients(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
}