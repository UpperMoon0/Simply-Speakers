package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;

public class PacketRegistries {
    private static boolean registered = false;
    
    public static void registerC2S() {
        // Client to Server packets - register receivers on server side
        // Note: registerReceiver also registers the codec for the payload type
        NetworkManager.registerReceiver(NetworkManager.c2s(), LoadAudioCallPacketC2S.TYPE, LoadAudioCallPacketC2S.STREAM_CODEC, LoadAudioCallPacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), AudioPathPacketC2S.TYPE, AudioPathPacketC2S.STREAM_CODEC, AudioPathPacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), ToggleLoopPacketC2S.TYPE, ToggleLoopPacketC2S.STREAM_CODEC, ToggleLoopPacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), RequestUploadAudioPacketC2S.TYPE, RequestUploadAudioPacketC2S.STREAM_CODEC, RequestUploadAudioPacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), UploadAudioDataPacketC2S.TYPE, UploadAudioDataPacketC2S.STREAM_CODEC, UploadAudioDataPacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), RequestAudioListPacketC2S.TYPE, RequestAudioListPacketC2S.STREAM_CODEC, RequestAudioListPacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), SelectAudioPacketC2S.TYPE, SelectAudioPacketC2S.STREAM_CODEC, SelectAudioPacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), RequestAudioFilePacketC2S.TYPE, RequestAudioFilePacketC2S.STREAM_CODEC, RequestAudioFilePacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), StopPlaybackPacketC2S.TYPE, StopPlaybackPacketC2S.STREAM_CODEC, StopPlaybackPacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), SetSpeakerIdPacketC2S.TYPE, SetSpeakerIdPacketC2S.STREAM_CODEC, SetSpeakerIdPacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), UpdateMaxVolumePacketC2S.TYPE, UpdateMaxVolumePacketC2S.STREAM_CODEC, UpdateMaxVolumePacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), UpdateMaxRangePacketC2S.TYPE, UpdateMaxRangePacketC2S.STREAM_CODEC, UpdateMaxRangePacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), UpdateAudioDropoffPacketC2S.TYPE, UpdateAudioDropoffPacketC2S.STREAM_CODEC, UpdateAudioDropoffPacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), UpdateProxyMaxVolumePacketC2S.TYPE, UpdateProxyMaxVolumePacketC2S.STREAM_CODEC, UpdateProxyMaxVolumePacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), UpdateProxyMaxRangePacketC2S.TYPE, UpdateProxyMaxRangePacketC2S.STREAM_CODEC, UpdateProxyMaxRangePacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), UpdateProxyAudioDropoffPacketC2S.TYPE, UpdateProxyAudioDropoffPacketC2S.STREAM_CODEC, UpdateProxyAudioDropoffPacketC2S::handle);
    }

    public static void registerS2C() {
        // Register S2C receivers on client side
        // Note: registerReceiver also registers the payload type internally, so we don't need to call registerS2CPayloadType separately
        NetworkManager.registerReceiver(NetworkManager.s2c(), StopAudioPacketS2C.TYPE, StopAudioPacketS2C.STREAM_CODEC, StopAudioPacketS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), PlayAudioPacketS2C.TYPE, PlayAudioPacketS2C.STREAM_CODEC, PlayAudioPacketS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), SpeakerBlockEntityPacketS2C.TYPE, SpeakerBlockEntityPacketS2C.STREAM_CODEC, SpeakerBlockEntityPacketS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), RespondUploadAudioPacketS2C.TYPE, RespondUploadAudioPacketS2C.STREAM_CODEC, RespondUploadAudioPacketS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), AcknowledgeUploadPacketS2C.TYPE, AcknowledgeUploadPacketS2C.STREAM_CODEC, AcknowledgeUploadPacketS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), SendAudioListPacketS2C.TYPE, SendAudioListPacketS2C.STREAM_CODEC, SendAudioListPacketS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), SendAudioFilePacketS2C.TYPE, SendAudioFilePacketS2C.STREAM_CODEC, SendAudioFilePacketS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), SpeakerStateUpdatePacketS2C.TYPE, SpeakerStateUpdatePacketS2C.STREAM_CODEC, SpeakerStateUpdatePacketS2C::handle);
    }
    
    public static void init() {
        if (registered) {
            SimplySpeakers.LOGGER.info("Packet registries already initialized, skipping");
            return;
        }
        registered = true;
        
        SimplySpeakers.LOGGER.info("Initializing packet registries...");
        // Always register both directions - Architectury handles the side-specific logic
        registerC2S();
        registerS2C();
        SimplySpeakers.LOGGER.info("Packet registries initialized");
    }
}
