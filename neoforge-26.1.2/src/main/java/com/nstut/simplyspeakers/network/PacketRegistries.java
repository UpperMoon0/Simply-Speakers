package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;

public class PacketRegistries {
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
        NetworkManager.registerReceiver(NetworkManager.c2s(), DeleteAudioPacketC2S.TYPE, DeleteAudioPacketC2S.STREAM_CODEC, DeleteAudioPacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), UpdateMaxVolumePacketC2S.TYPE, UpdateMaxVolumePacketC2S.STREAM_CODEC, UpdateMaxVolumePacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), UpdateMaxRangePacketC2S.TYPE, UpdateMaxRangePacketC2S.STREAM_CODEC, UpdateMaxRangePacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), UpdateAudioDropoffPacketC2S.TYPE, UpdateAudioDropoffPacketC2S.STREAM_CODEC, UpdateAudioDropoffPacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), UpdateProxyMaxVolumePacketC2S.TYPE, UpdateProxyMaxVolumePacketC2S.STREAM_CODEC, UpdateProxyMaxVolumePacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), UpdateProxyMaxRangePacketC2S.TYPE, UpdateProxyMaxRangePacketC2S.STREAM_CODEC, UpdateProxyMaxRangePacketC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), UpdateProxyAudioDropoffPacketC2S.TYPE, UpdateProxyAudioDropoffPacketC2S.STREAM_CODEC, UpdateProxyAudioDropoffPacketC2S::handle);
    }

    public static void registerS2C() {
        S2CPacketCatalog.registerReceivers();
    }
    
    public static void init() {
        SimplySpeakers.LOGGER.info("Initializing packet registries...");
        registerC2S();
        if (Platform.getEnvironment() == Env.SERVER) {
            S2CPacketCatalog.registerPayloadTypes();
        }
        SimplySpeakers.LOGGER.info("Packet registries initialized");
    }
}

