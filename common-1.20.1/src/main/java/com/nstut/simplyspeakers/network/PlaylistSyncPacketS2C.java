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

    private com.nstut.simplyspeakers.permissions.AccessViewSnapshot accessView = com.nstut.simplyspeakers.permissions.AccessViewSnapshot.EMPTY;
    public com.nstut.simplyspeakers.permissions.AccessViewSnapshot getAccessView(){return accessView;}
    private boolean catalogOnly,hasLibrary=true;
    public boolean hasLibrary() { return hasLibrary; }
    public boolean isCatalogOnly() { return catalogOnly; }
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
    private com.nstut.simplyspeakers.playlist.PlayerViewSnapshot playerView =
            com.nstut.simplyspeakers.playlist.PlayerViewSnapshot.EMPTY;

    private com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot library;
    /** Split personal catalogs from runtime queues so both remain below Minecraft's payload limit. */
    public java.util.List<PlaylistSyncPacketS2C> splitForPlayer(java.util.UUID player) {
        var personal=forPlayer(player);
        var catalog=new PlaylistSyncPacketS2C(pos,fullStateKey,java.util.List.of(),java.util.List.of(),-1,false,0,-1,false);
        catalog.catalogOnly=true;catalog.library=personal.library;
        personal.library=new com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot("",java.util.List.of());personal.hasLibrary=false;
        return java.util.List.of(catalog,personal);
    }
    public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player,PlaylistSyncPacketS2C packet) {
        var state=com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.getSpeakerStateByFullKey(packet.fullStateKey);
        var policy=com.nstut.simplyspeakers.permissions.AccessViewSnapshot.capture(state,player.getUUID(),player.hasPermissions(2),
            com.nstut.simplyspeakers.Config.isRemoteStreamingAllowed(),id -> {
                var online=player.level().getServer().getPlayerList().getPlayer(id);
                return online==null?id.toString():online.getName().getString();
            });
        for(var part:packet.splitForPlayer(player.getUUID())) {part.accessView=policy;PacketRegistries.CHANNEL.sendToPlayer(player,part);}
    }
    public PlaylistSyncPacketS2C forPlayer(java.util.UUID player) {
        var state=com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if(state!=null) { com.nstut.simplyspeakers.playlist.PlayerPlaylistStore.migrate(fullStateKey,state);com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.markDirty(); }
        var copy=new PlaylistSyncPacketS2C(pos,fullStateKey,audioIds,filenames,currentIndex,shuffle,repeatOrdinal,playingIndex,paused);
        copy.playerView=playerView;
        copy.library=state==null?new com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot("",java.util.List.of()):com.nstut.simplyspeakers.playlist.PlayerPlaylistStore.snapshot(player,state);
        return copy;
    }
    public com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot getLibrary() { return library; }
    public com.nstut.simplyspeakers.playlist.PlayerViewSnapshot getPlayerView() { return playerView; }

    public static PlaylistSyncPacketS2C fromState(BlockPos pos, String key,
            com.nstut.simplyspeakers.SpeakerState state, long tick) {
        return fromState(pos, key, state, tick, null);
    }

    public static PlaylistSyncPacketS2C fromState(BlockPos pos, String key,
            com.nstut.simplyspeakers.SpeakerState state, long tick,
            com.nstut.simplyspeakers.audio.AudioFileManager manager) {
        Playlist playlist = state.getPlaylist();
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (var track : playlist.getTracks()) {
            ids.add(track.getAudioId()); names.add(track.getFilename());
        }
        var metadata = manager == null ? null : manager.getManifest().get(state.getAudioId());
        float duration = metadata == null ? 0 : metadata.getDurationSeconds();
        PlaylistSyncPacketS2C snapshot = new PlaylistSyncPacketS2C(pos, key, ids, names,
                playlist.getCurrentIndex(), playlist.isShuffle(), playlist.getRepeatMode().ordinal(),
                state.isPlaying() && state.isPlaylistSourceActive() && !playlist.isQueuedTrackActive() ? playlist.getCurrentIndex() : -1,
                state.isPaused());
        snapshot.playerView = com.nstut.simplyspeakers.playlist.PlayerViewSnapshot.capture(state, tick, duration);
        snapshot.library=com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot.capture(state);
        return snapshot;
    }

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
        this.library=com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot.single(this.audioIds,this.filenames);
    }

    public PlaylistSyncPacketS2C(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.fullStateKey = buf.readUtf(256);
        this.catalogOnly=buf.readBoolean();this.hasLibrary=buf.readBoolean();
        this.library=com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot.decode(buf.readByteArray(com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot.MAX_BYTES));
        int count=buf.readVarInt();if(count<0 || count>Playlist.MAX_ENTRIES)throw new IllegalArgumentException("Track count");
        this.audioIds=new ArrayList<>();this.filenames=new ArrayList<>();
        for(int i=0;i<count;i++){audioIds.add(buf.readUtf(256));filenames.add(buf.readUtf(256));}
        this.currentIndex = buf.readVarInt();
        this.shuffle = buf.readBoolean();
        this.repeatOrdinal = buf.readVarInt();
        this.playingIndex = buf.readVarInt();
        this.paused = buf.readBoolean();
        this.playerView = com.nstut.simplyspeakers.playlist.PlayerViewSnapshot.decode(
                buf.readByteArray(com.nstut.simplyspeakers.playlist.PlayerViewSnapshot.MAX_BYTES));
        this.accessView=com.nstut.simplyspeakers.permissions.AccessViewSnapshot.decode(buf.readByteArray(com.nstut.simplyspeakers.permissions.AccessViewSnapshot.MAX_BYTES));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeUtf(this.fullStateKey, 256);
        buf.writeBoolean(catalogOnly);buf.writeBoolean(hasLibrary);
        buf.writeByteArray(library.encode());
        buf.writeVarInt(audioIds.size());for(int i=0;i<audioIds.size();i++){buf.writeUtf(audioIds.get(i),256);buf.writeUtf(i<filenames.size()?filenames.get(i):"",256);}
        buf.writeVarInt(Math.max(-1, this.currentIndex));
        buf.writeBoolean(this.shuffle);
        buf.writeVarInt(Math.max(0, this.repeatOrdinal));
        buf.writeVarInt(Math.max(-1, this.playingIndex));
        buf.writeBoolean(this.paused);
        buf.writeByteArray(playerView.encode());
        buf.writeByteArray(accessView.encode());
    }

    public static void handle(PlaylistSyncPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        context.queue(() -> {
            var client=Minecraft.getInstance();
            if(!pkt.catalogOnly && client.level!=null) {
                String stateKey=pkt.fullStateKey.contains("/")?pkt.fullStateKey.substring(pkt.fullStateKey.indexOf('/')+1):"";
                if(stateKey.isEmpty() && client.level.getBlockEntity(pkt.pos) instanceof com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity speaker) stateKey=speaker.getStateKey();
                if(!stateKey.isEmpty()) {
                    var state=com.nstut.simplyspeakers.client.ClientSpeakerRegistry.getOrCreateState(stateKey);
                    state.getPlaylist().setRepeatMode(com.nstut.simplyspeakers.playlist.RepeatMode.fromIndex(pkt.repeatOrdinal));
                    state.getPlaylist().setShuffle(pkt.shuffle);
                }
            }
            if (Minecraft.getInstance().screen instanceof SpeakerScreen screen
                    && matchesScreen(pkt, screen)) {
                screen.updateAccessModel(pkt.accessView);
                if(pkt.catalogOnly)screen.updatePlaylistCatalog(pkt.library);else screen.updatePlaylistModel(pkt);
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
