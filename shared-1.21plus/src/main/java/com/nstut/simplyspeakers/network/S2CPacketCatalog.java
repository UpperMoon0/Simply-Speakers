package com.nstut.simplyspeakers.network;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The single source of truth for modern (1.21+) server-to-client packets.
 *
 * <p>Dedicated servers register payload types so they can encode packets,
 * while clients register receivers as well. Keeping both paths backed by this
 * catalog prevents a new S2C packet from working on clients but crashing a
 * dedicated server with a missing codec.</p>
 */
public final class S2CPacketCatalog {
    private S2CPacketCatalog() {
    }

    public static void registerPayloadTypes() {
        registerAll(new Registrar() {
            @Override
            public <T extends CustomPacketPayload> void register(
                    CustomPacketPayload.Type<T> type,
                    StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                    NetworkManager.NetworkReceiver<T> receiver) {
                NetworkManager.registerS2CPayloadType(type, codec);
            }
        });
    }

    public static void registerReceivers() {
        registerAll(new Registrar() {
            @Override
            public <T extends CustomPacketPayload> void register(
                    CustomPacketPayload.Type<T> type,
                    StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                    NetworkManager.NetworkReceiver<T> receiver) {
                NetworkManager.registerReceiver(NetworkManager.s2c(), type, codec, receiver);
            }
        });
    }

    static void registerAll(Registrar registrar) {
        registrar.register(StopAudioPacketS2C.TYPE, StopAudioPacketS2C.STREAM_CODEC, StopAudioPacketS2C::handle);
        registrar.register(PlayAudioPacketS2C.TYPE, PlayAudioPacketS2C.STREAM_CODEC, PlayAudioPacketS2C::handle);
        registrar.register(SpeakerBlockEntityPacketS2C.TYPE, SpeakerBlockEntityPacketS2C.STREAM_CODEC, SpeakerBlockEntityPacketS2C::handle);
        registrar.register(RespondUploadAudioPacketS2C.TYPE, RespondUploadAudioPacketS2C.STREAM_CODEC, RespondUploadAudioPacketS2C::handle);
        registrar.register(AcknowledgeUploadPacketS2C.TYPE, AcknowledgeUploadPacketS2C.STREAM_CODEC, AcknowledgeUploadPacketS2C::handle);
        registrar.register(SendAudioListPacketS2C.TYPE, SendAudioListPacketS2C.STREAM_CODEC, SendAudioListPacketS2C::handle);
        registrar.register(SendAudioFilePacketS2C.TYPE, SendAudioFilePacketS2C.STREAM_CODEC, SendAudioFilePacketS2C::handle);
        registrar.register(SpeakerStateUpdatePacketS2C.TYPE, SpeakerStateUpdatePacketS2C.STREAM_CODEC, SpeakerStateUpdatePacketS2C::handle);
    }

    interface Registrar {
        <T extends CustomPacketPayload> void register(
                CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                NetworkManager.NetworkReceiver<T> receiver);
    }
}
