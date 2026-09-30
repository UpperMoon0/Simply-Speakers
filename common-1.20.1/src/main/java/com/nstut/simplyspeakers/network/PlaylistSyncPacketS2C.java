package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import com.nstut.simplyspeakers.playlist.Playlist;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PlaylistSyncPacketS2C {

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

    public PlaylistSyncPacketS2C(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.fullStateKey = buf.readUtf(256);
        int count = buf.readVarInt();
        this.audioIds = new ArrayList<>();
        this.filenames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(256);
            String name = buf.readUtf(256);
            if (this.audioIds.size() < Playlist.MAX_ENTRIES) {
                this.audioIds.add(id);
                this.filenames.add(name);
            }
        }
        this.currentIndex = buf.readVarInt();
        this.shuffle = buf.readBoolean();
        this.repeatOrdinal = buf.readVarInt();
        this.playingIndex = buf.readVarInt();
        this.paused = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeUtf(this.fullStateKey, 256);
        int entryCount = Math.min(this.audioIds.size(), Playlist.MAX_ENTRIES);
        buf.writeVarInt(entryCount);
        for (int i = 0; i < entryCount; i++) {
            buf.writeUtf(this.audioIds.get(i), 256);
            buf.writeUtf(i < this.filenames.size() ? this.filenames.get(i) : "", 256);
        }
        buf.writeVarInt(Math.max(-1, this.currentIndex));
        buf.writeBoolean(this.shuffle);
        buf.writeVarInt(Math.max(0, this.repeatOrdinal));
        buf.writeVarInt(Math.max(-1, this.playingIndex));
        buf.writeBoolean(this.paused);
    }

    public static void handle(PlaylistSyncPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        context.queue(() -> {
            if (Minecraft.getInstance().screen instanceof SpeakerScreen screen
                    && matchesScreen(pkt, screen)) {
                screen.updatePlaylistModel(pkt);
            }
        });
    }

    /**
     * A playlist sync reaches the open GUI when it targets the GUI's physical position or
     * the GUI speaker's authoritative full state key. The key match keeps linked-speaker
     * GUIs (and both mains of one network) updated by centralized broadcasts.
     */
    private static boolean matchesScreen(PlaylistSyncPacketS2C pkt, SpeakerScreen screen) {
        if (screen.getBlockEntityPos().equals(pkt.pos)) return true;
        return !pkt.fullStateKey.isEmpty() && pkt.fullStateKey.equals(screen.getFullStateKey());
    }

    public BlockPos getPos() { return pos; }
    public String getFullStateKey() { return fullStateKey; }
    public List<String> getAudioIds() { return audioIds; }
    public List<String> getFilenames() { return filenames; }
    public int getCurrentIndex() { return currentIndex; }
    public boolean isShuffle() { return shuffle; }
    public int getRepeatOrdinal() { return repeatOrdinal; }
    public int getPlayingIndex() { return playingIndex; }
    public boolean isPaused() { return paused; }
}
