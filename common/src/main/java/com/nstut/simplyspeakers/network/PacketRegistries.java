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
                LoadAudioCallPacketC2S::new,
                LoadAudioCallPacketC2S::handle
        );
        CHANNEL.register(AudioPathPacketC2S.class,
                AudioPathPacketC2S::encode,
                AudioPathPacketC2S::new,
                AudioPathPacketC2S::handle
        );
        CHANNEL.register(ToggleLoopPacketC2S.class,
                ToggleLoopPacketC2S::encode,
                ToggleLoopPacketC2S::new,
                ToggleLoopPacketC2S::handle
        );
        CHANNEL.register(RequestUploadAudioPacketC2S.class,
                RequestUploadAudioPacketC2S::encode,
                RequestUploadAudioPacketC2S::new,
                RequestUploadAudioPacketC2S::handle
        );
        CHANNEL.register(UploadAudioDataPacketC2S.class,
                UploadAudioDataPacketC2S::encode,
                UploadAudioDataPacketC2S::new,
                UploadAudioDataPacketC2S::handle
        );
        CHANNEL.register(RequestAudioListPacketC2S.class,
                RequestAudioListPacketC2S::encode,
                RequestAudioListPacketC2S::new,
                RequestAudioListPacketC2S::handle
        );
        CHANNEL.register(SelectAudioPacketC2S.class,
                SelectAudioPacketC2S::encode,
                SelectAudioPacketC2S::new,
                SelectAudioPacketC2S::handle
        );
        CHANNEL.register(RequestAudioFilePacketC2S.class,
                RequestAudioFilePacketC2S::encode,
                RequestAudioFilePacketC2S::new,
                RequestAudioFilePacketC2S::handle
        );
        CHANNEL.register(StopPlaybackPacketC2S.class,
                StopPlaybackPacketC2S::encode,
                StopPlaybackPacketC2S::new,
                StopPlaybackPacketC2S::handle
        );
        CHANNEL.register(SetSpeakerIdPacketC2S.class,
                SetSpeakerIdPacketC2S::encode,
                SetSpeakerIdPacketC2S::new,
                SetSpeakerIdPacketC2S::handle
        );
    }

    public static void registerS2C() {
        // Server to Client packets
        CHANNEL.register(StopAudioPacketS2C.class,
                StopAudioPacketS2C::encode,
                StopAudioPacketS2C::new,
                StopAudioPacketS2C::handle
        );
        CHANNEL.register(PlayAudioPacketS2C.class,
                PlayAudioPacketS2C::encode,
                PlayAudioPacketS2C::new,
                PlayAudioPacketS2C::handle
        );
        CHANNEL.register(SpeakerBlockEntityPacketS2C.class,
                SpeakerBlockEntityPacketS2C::encode,
                SpeakerBlockEntityPacketS2C::new,
                SpeakerBlockEntityPacketS2C::handle
        );
        CHANNEL.register(RespondUploadAudioPacketS2C.class,
                RespondUploadAudioPacketS2C::encode,
                RespondUploadAudioPacketS2C::new,
                RespondUploadAudioPacketS2C::handle
        );
        CHANNEL.register(AcknowledgeUploadPacketS2C.class,
                AcknowledgeUploadPacketS2C::encode,
                AcknowledgeUploadPacketS2C::new,
                AcknowledgeUploadPacketS2C::handle
        );
        CHANNEL.register(SendAudioListPacketS2C.class,
                SendAudioListPacketS2C::encode,
                SendAudioListPacketS2C::new,
                SendAudioListPacketS2C::handle
        );
        CHANNEL.register(SendAudioFilePacketS2C.class,
                SendAudioFilePacketS2C::encode,
                SendAudioFilePacketS2C::new,
                SendAudioFilePacketS2C::handle
        );
        CHANNEL.register(SpeakerStateUpdatePacketS2C.class,
                SpeakerStateUpdatePacketS2C::encode,
                SpeakerStateUpdatePacketS2C::new,
                SpeakerStateUpdatePacketS2C::handle
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
}