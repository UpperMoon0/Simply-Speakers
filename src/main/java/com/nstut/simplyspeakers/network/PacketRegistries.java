package com.nstut.simplyspeakers.network;

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
                AudioPathPacketC2S.class,
                AudioPathPacketC2S::encode,
                AudioPathPacketC2S::decode,
                AudioPathPacketC2S::handle
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
        CHANNEL.registerMessage(
                4,
                StopAudioCallPacketC2S.class,
                StopAudioCallPacketC2S::encode,
                StopAudioCallPacketC2S::decode,
                StopAudioCallPacketC2S::handle
        );
        CHANNEL.registerMessage(
                5,
                PlayAudioCallPacketC2S.class,
                PlayAudioCallPacketC2S::encode,
                PlayAudioCallPacketC2S::decode,
                PlayAudioCallPacketC2S::handle
        );
    }

    public static void sendToClients(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}