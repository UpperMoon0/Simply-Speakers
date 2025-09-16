package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.function.Supplier;

public class SyncProxySpeakerPacketS2C {
    private final BlockPos pos;
    private final String speakerId;
    private final String action; // "play" or "stop"
    private final String audioId;
    private final String audioFilename;
    private final float playbackPositionSeconds;
    private final boolean isLooping;

    public SyncProxySpeakerPacketS2C(BlockPos pos, String speakerId, String action, String audioId, String audioFilename, float playbackPositionSeconds, boolean isLooping) {
        this.pos = pos;
        this.speakerId = speakerId;
        this.action = action;
        this.audioId = audioId;
        this.audioFilename = audioFilename;
        this.playbackPositionSeconds = playbackPositionSeconds;
        this.isLooping = isLooping;
    }

    public SyncProxySpeakerPacketS2C(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.speakerId = buf.readUtf();
        this.action = buf.readUtf();
        this.audioId = buf.readUtf();
        this.audioFilename = buf.readUtf();
        this.playbackPositionSeconds = buf.readFloat();
        this.isLooping = buf.readBoolean();
    }

    public static void encode(SyncProxySpeakerPacketS2C pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.speakerId);
        buf.writeUtf(pkt.action);
        buf.writeUtf(pkt.audioId);
        buf.writeUtf(pkt.audioFilename);
        buf.writeFloat(pkt.playbackPositionSeconds);
        buf.writeBoolean(pkt.isLooping);
    }

    public static void handle(SyncProxySpeakerPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        // Ensure this code runs only on the client side
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.isClientSide) {
            context.queue(() -> {
                SimplySpeakers.LOGGER.info("CLIENT: Received SyncProxySpeakerPacketS2C for pos: {}, speakerId: {}, action: {}", pkt.pos, pkt.speakerId, pkt.action);
                
                // Instead of checking the block entity at pkt.pos, we need to find all proxy speakers
                // with matching speakerId in the loaded chunks
                // We'll check a reasonable area around the player for proxy speakers
                net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    // Check blocks in a reasonable radius around the player
                    int radius = 32; // chunks
                    net.minecraft.core.BlockPos playerPos = player.blockPosition();
                    net.minecraft.world.level.ChunkPos centerChunk = new net.minecraft.world.level.ChunkPos(playerPos);
                    
                    for (int cx = centerChunk.x - radius; cx <= centerChunk.x + radius; cx++) {
                        for (int cz = centerChunk.z - radius; cz <= centerChunk.z + radius; cz++) {
                            net.minecraft.world.level.chunk.LevelChunk chunk = Minecraft.getInstance().level.getChunkSource().getChunkNow(cx, cz);
                            if (chunk != null) {
                                // Iterate through block entities in this chunk
                                for (BlockEntity be : chunk.getBlockEntities().values()) {
                                    if (be instanceof ProxySpeakerBlockEntity proxySpeaker &&
                                        pkt.speakerId.equals(proxySpeaker.getSpeakerId())) {
                                        if ("play".equals(pkt.action)) {
                                            // Play the audio for the specific proxy speaker block
                                            AudioFileMetadata metadata = new AudioFileMetadata(pkt.audioId, pkt.audioFilename);
                                            ClientAudioPlayer.play(proxySpeaker.getBlockPos(), metadata, pkt.playbackPositionSeconds, pkt.isLooping);
                                        } else if ("stop".equals(pkt.action)) {
                                            // Stop the audio tied to the specific proxy speaker block
                                            ClientAudioPlayer.stop(proxySpeaker.getBlockPos());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }
    }
}