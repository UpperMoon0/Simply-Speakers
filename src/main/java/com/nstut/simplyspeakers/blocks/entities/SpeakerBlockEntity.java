// Language: java
package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.Config; // Added import
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.network.PlayAudioPacketS2C;
import com.nstut.simplyspeakers.network.SpeakerBlockEntityPacketS2C;
import com.nstut.simplyspeakers.network.StopAudioPacketS2C;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkDirection; // Added import
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Getter
public class SpeakerBlockEntity extends BlockEntity {

    private static final Logger LOGGER = Logger.getLogger(SpeakerBlockEntity.class.getName());

    private String audioPath = "";
    private boolean isPlaying = false;
    private final Set<UUID> listeningPlayers = new HashSet<>(); // Track players currently hearing the sound

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistries.SPEAKER.get(), pos, state);
    }

    public void setAudioPath(String audioPath) {
        // Server side only
        if (level != null && !level.isClientSide) {
            this.audioPath = audioPath;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

            // Send update packet to all clients (needed for GUI sync)
            SpeakerBlockEntityPacketS2C packet = new SpeakerBlockEntityPacketS2C(getBlockPos(), audioPath);
            PacketRegistries.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }

    // Need this for BlockEntity ticking
    public static void serverTick(Level level, BlockPos pos, BlockState state, SpeakerBlockEntity blockEntity) {
        blockEntity.tick();
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            stopAudio(); // Ensure stop logic runs
        }
        super.setRemoved();
    }

    public void playAudio() {
        if (level == null || level.isClientSide || isPlaying) {
            return;
        }
        if (audioPath == null || audioPath.isEmpty()) {
            LOGGER.warning("Audio path is empty, cannot play.");
            return;
        }

        isPlaying = true;
        listeningPlayers.clear(); // Reset listeners when starting fresh
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); // Update block state visually if needed

        // Initial play command to nearby players
        updateNearbyPlayers();
    }

    public void stopAudio() {
        if (level == null || level.isClientSide || !isPlaying) {
            return;
        }

        isPlaying = false;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); // Update block state visually if needed

        StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(getBlockPos());

        // --- Modification Start: Send stop packet more broadly ---
        // Send to all players tracking the chunk the speaker is in.
        // This ensures clients that might have moved just out of range still get the stop command.
        PacketRegistries.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(getBlockPos())), stopPacket);
        // --- Modification End ---

        listeningPlayers.clear(); // Clear the server-side tracking list
    }

    private void tick() {
        if (level == null || level.isClientSide || !isPlaying) {
            return;
        }

        // Validate block state
        if (!level.getBlockState(worldPosition).is(BlockRegistries.SPEAKER.get())) {
            stopAudio(); // Stop if the block is no longer a speaker
            return;
        }

        // Update player lists every tick (or less frequently if performance is an issue)
        updateNearbyPlayers();
    }

    private void updateNearbyPlayers() {
        if (level == null || level.isClientSide || !isPlaying) return; // Only run on server when playing

        ServerLevel serverLevel = (ServerLevel) level;
        double range = Config.SPEAKER_RANGE.get();
        double rangeSq = range * range; // Use squared distance for efficiency
        AABB area = new AABB(worldPosition).inflate(range);

        List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(ServerPlayer.class, area, player ->
                player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= rangeSq
        );
        Set<UUID> nearbyPlayerIds = nearbyPlayers.stream().map(Player::getUUID).collect(Collectors.toSet());

        // --- Logic for stopping players who moved out of range ---
        Set<UUID> playersToStop = new HashSet<>(listeningPlayers); // Start with all currently listening players
        playersToStop.removeAll(nearbyPlayerIds); // Remove those who are still nearby

        if (!playersToStop.isEmpty()) {
            StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(getBlockPos());
            for (UUID playerId : playersToStop) {
                Player player = serverLevel.getPlayerByUUID(playerId); // Get Player object
                if (player instanceof ServerPlayer serverPlayer) { // Check if it's a ServerPlayer
                    // Send packet directly via player connection using the correctly typed variable
                    Packet<?> vanillaPacket = PacketRegistries.CHANNEL.toVanillaPacket(stopPacket, NetworkDirection.PLAY_TO_CLIENT);
                    serverPlayer.connection.send(vanillaPacket); // Use serverPlayer here
                    LOGGER.fine("Sent stop packet to player " + serverPlayer.getName().getString() + " who moved out of range.");
                } else if (player != null) {
                    // Log if we found a player but it wasn't a ServerPlayer (shouldn't happen on server)
                    LOGGER.warning("Found player with UUID " + playerId + " for stopping, but it was not a ServerPlayer instance.");
                }
                // Always remove from listeningPlayers if they were in the playersToStop set
                listeningPlayers.remove(playerId);
            }
        }
        // --- End stop logic ---

        // Players who moved into range (or are already in range and need initial sync)
        Set<UUID> playersToStart = new HashSet<>(nearbyPlayerIds);
        playersToStart.removeAll(listeningPlayers); // Keep only those who are nearby but weren't listening

        if (!playersToStart.isEmpty()) {
            PlayAudioPacketS2C playPacket = new PlayAudioPacketS2C(getBlockPos(), audioPath);
            for (UUID playerId : playersToStart) {
                Player player = serverLevel.getPlayerByUUID(playerId); // Get Player object
                if (player instanceof ServerPlayer serverPlayer) { // Check if it's a ServerPlayer
                    // Send packet directly via player connection using the correctly typed variable
                    Packet<?> vanillaPacket = PacketRegistries.CHANNEL.toVanillaPacket(playPacket, NetworkDirection.PLAY_TO_CLIENT);
                    serverPlayer.connection.send(vanillaPacket); // Use serverPlayer here
                    LOGGER.fine("Sent play packet to player " + serverPlayer.getName().getString() + " who moved into range.");
                    listeningPlayers.add(playerId); // Add to active listeners only if packet sent successfully
                } else if (player != null) {
                     // Log if we found a player but it wasn't a ServerPlayer (shouldn't happen on server)
                     LOGGER.warning("Found player with UUID " + playerId + " but it was not a ServerPlayer instance.");
                }
            }
        }
    }


    // --- NBT & Update Packet Handling ---

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        audioPath = tag.getString("AudioPath");
        isPlaying = tag.getBoolean("IsPlaying");
        // listeningPlayers is runtime only, not saved/loaded
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("AudioPath", audioPath);
        tag.putBoolean("IsPlaying", isPlaying);
    }

    // For initial chunk data sync
    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag); // Re-use save logic
        return tag;
    }

    // For block updates (level.sendBlockUpdated)
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag); // Re-use load logic
        // If the client receives an update indicating the sound should be playing,
        // but it isn't, it might need to request it or handle it based on game logic.
        // However, the tick updates should handle players entering range.
    }

}
