package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import com.nstut.simplyspeakers.playlist.Playlist;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server -> client playlist snapshot driving the playlist editor. */
public class PlaylistSyncPacketS2C implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlaylistSyncPacketS2C> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "playlist_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaylistSyncPacketS2C> STREAM_CODEC =
        StreamCodec.of(PlaylistSyncPacketS2C::encode, PlaylistSyncPacketS2C::decode);

    private final BlockPos pos;
    /** Dimension-qualified registry key identifying the shared state; may be empty. */
    private final String fullStateKey;
    private final List<String> audioIds;
    private final List<String> filenames;
    private final int currentIndex;
    private final boolean shuffle;
    private final int repeatOrdinal;
    private final int playingIndex;
    private final boolean paused;

    public PlaylistSyncPacketS2C(BlockPos pos, String fullStateKey, List<String> audioIds, List<String> filenames,
                                 int currentIndex, boolean shuffle, int repeatOrdinal,
                                 int playingIndex, boolean paused) {
        this.pos = pos;
        this.fullStateKey = fullStateKey != null ? fullStateKey : "";
        this.audioIds = audioIds != null ? audioIds : List.of();
        this.filenames = filenames != null ? filenames : List.of();
        this.currentIndex = currentIndex;
        this.shuffle = shuffle;
        this.repeatOrdinal = repeatOrdinal;
        this.playingIndex = playingIndex;
        this.paused = paused;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, PlaylistSyncPacketS2C packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUtf(packet.fullStateKey, 256);
        int entryCount = Math.min(packet.audioIds.size(), Playlist.MAX_ENTRIES);
        buffer.writeVarInt(entryCount);
        for (int i = 0; i < entryCount; i++) {
            buffer.writeUtf(packet.audioIds.get(i), 256);
            buffer.writeUtf(i < packet.filenames.size() ? packet.filenames.get(i) : "", 256);
        }
        buffer.writeVarInt(Math.max(-1, packet.currentIndex));
        buffer.writeBoolean(packet.shuffle);
        buffer.writeVarInt(Math.max(0, packet.repeatOrdinal));
        buffer.writeVarInt(Math.max(-1, packet.playingIndex));
        buffer.writeBoolean(packet.paused);
    }

    public static PlaylistSyncPacketS2C decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        String fullStateKey = buffer.readUtf(256);
        int count = buffer.readVarInt();
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = buffer.readUtf(256);
            String name = buffer.readUtf(256);
            if (ids.size() < Playlist.MAX_ENTRIES) {
                ids.add(id);
                names.add(name);
            }
        }
        return new PlaylistSyncPacketS2C(pos, fullStateKey, ids, names, buffer.readVarInt(),
                buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(PlaylistSyncPacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (Minecraft.getInstance().screen instanceof SpeakerScreen screen
                    && matchesScreen(packet, screen)) {
                screen.updatePlaylistModel(packet);
            }
        });
    }

    /**
     * A playlist sync reaches the open GUI when it targets the GUI's physical position or
     * the GUI speaker's authoritative full state key. The key match keeps linked-speaker
     * GUIs (and both mains of one network) updated by centralized broadcasts.
     */
    private static boolean matchesScreen(PlaylistSyncPacketS2C packet, SpeakerScreen screen) {
        if (screen.getBlockEntityPos().equals(packet.pos)) return true;
        return !packet.fullStateKey.isEmpty() && packet.fullStateKey.equals(screen.getFullStateKey());
    }

    public BlockPos getPos() {
        return pos;
    }

    public String getFullStateKey() {
        return fullStateKey;
    }

    public List<String> getAudioIds() {
        return audioIds;
    }

    public List<String> getFilenames() {
        return filenames;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public int getRepeatOrdinal() {
        return repeatOrdinal;
    }

    public int getPlayingIndex() {
        return playingIndex;
    }

    public boolean isPaused() {
        return paused;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
