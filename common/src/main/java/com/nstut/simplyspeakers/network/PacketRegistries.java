//
package com.nstut.simplyspeakers.network;

import dev.architectury.networking.NetworkChannel;
import net.minecraft.resources.ResourceLocation;
import com.nstut.simplyspeakers.SimplySpeakers;

public class PacketRegistries {
    public static final NetworkChannel CHANNEL = NetworkChannel.create(
            new ResourceLocation(SimplySpeakers.MOD_ID, "main")
    );

    public static void registerC2S() {
        // Client to Server packets
        CHANNEL.register(LoadAudioCallPacketC2S.class,
                LoadAudioCallPacketC2S::encode,
                LoadAudioCallPacketC2S::new, // Assuming a constructor that takes a FriendlyByteBuf
                LoadAudioCallPacketC2S::handle
        );
        CHANNEL.register(AudioPathPacketC2S.class,
                AudioPathPacketC2S::encode,
                AudioPathPacketC2S::new, // Assuming a constructor that takes a FriendlyByteBuf
                AudioPathPacketC2S::handle
        );
    }

    public static void registerS2C() {
        // Server to Client packets
        CHANNEL.register(StopAudioPacketS2C.class,
                StopAudioPacketS2C::encode,
                StopAudioPacketS2C::new, // Assuming a constructor that takes a FriendlyByteBuf
                StopAudioPacketS2C::handle
        );
        CHANNEL.register(PlayAudioPacketS2C.class,
                PlayAudioPacketS2C::encode,
                PlayAudioPacketS2C::new, // Assuming a constructor that takes a FriendlyByteBuf
                PlayAudioPacketS2C::handle
        );
        CHANNEL.register(SpeakerBlockEntityPacketS2C.class,
                SpeakerBlockEntityPacketS2C::encode,
                SpeakerBlockEntityPacketS2C::new, // Assuming a constructor that takes a FriendlyByteBuf
                SpeakerBlockEntityPacketS2C::handle
        );
    }
    
    public static void init() {
        registerC2S();
        registerS2C();
    }

    // Sending packets:
    // To server: NetworkManager.sendToServer(CHANNEL, new MyPacket(...));
    // To client(s): NetworkManager.sendToPlayers(listOfPlayers, CHANNEL, new MyPacket(...));
    // Or: NetworkManager.sendToPlayer(player, CHANNEL, new MyPacket(...));
    // Or: NetworkManager.sendToClients(serverLevel, CHANNEL, new MyPacket(...));
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
