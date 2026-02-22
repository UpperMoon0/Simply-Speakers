package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class SpeakerBlockEntityPacketS2C implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpeakerBlockEntityPacketS2C> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "speaker_block_entity"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, SpeakerBlockEntityPacketS2C> STREAM_CODEC = 
        StreamCodec.of(SpeakerBlockEntityPacketS2C::encode, SpeakerBlockEntityPacketS2C::decode);

    private final BlockPos pos;
    private final String audioId;

    public SpeakerBlockEntityPacketS2C(BlockPos pos, String audioId) {
        this.pos = pos;
        this.audioId = audioId;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, SpeakerBlockEntityPacketS2C packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUtf(packet.audioId);
    }

    public static SpeakerBlockEntityPacketS2C decode(RegistryFriendlyByteBuf buffer) {
        return new SpeakerBlockEntityPacketS2C(buffer.readBlockPos(), buffer.readUtf());
    }

    public static void handle(SpeakerBlockEntityPacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (Minecraft.getInstance().level != null) {
                var blockEntity = Minecraft.getInstance().level.getBlockEntity(packet.pos);
                if (blockEntity instanceof SpeakerBlockEntity speaker) {
                    // Create a CompoundTag containing the updated music path
                    CompoundTag tag = new CompoundTag();
                    tag.putString("AudioID", packet.audioId);
                    speaker.loadAdditional(tag, Minecraft.getInstance().level.registryAccess());
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
