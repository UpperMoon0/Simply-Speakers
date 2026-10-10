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
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Server -> client playlist snapshot driving the playlist editor. */
public class PlaylistSyncPacketS2C implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlaylistSyncPacketS2C> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "playlist_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaylistSyncPacketS2C> STREAM_CODEC =
        StreamCodec.of(PlaylistSyncPacketS2C::encode, PlaylistSyncPacketS2C::decode);

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
        var policy=com.nstut.simplyspeakers.permissions.AccessViewSnapshot.capture(state,player.getUUID(),player.level().getServer().getPlayerList().isOp(player.nameAndId()),
            com.nstut.simplyspeakers.Config.isRemoteStreamingAllowed(),id -> {
                var online=player.level().getServer().getPlayerList().getPlayer(id);
                return online==null?id.toString():online.getName().getString();
            });
        for(var part:packet.splitForPlayer(player.getUUID())) {part.accessView=policy;NetworkManager.sendToPlayer(player,part);}
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

    public static void encode(RegistryFriendlyByteBuf buffer, PlaylistSyncPacketS2C packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUtf(packet.fullStateKey, 512);
        buffer.writeBoolean(packet.catalogOnly);buffer.writeBoolean(packet.hasLibrary);
        buffer.writeByteArray(packet.library.encode());
        buffer.writeVarInt(packet.audioIds.size());for(int i=0;i<packet.audioIds.size();i++){buffer.writeUtf(packet.audioIds.get(i),256);buffer.writeUtf(i<packet.filenames.size()?packet.filenames.get(i):"",256);}
        buffer.writeVarInt(Math.max(-1, packet.currentIndex));
        buffer.writeBoolean(packet.shuffle);
        buffer.writeVarInt(Math.max(0, packet.repeatOrdinal));
        buffer.writeVarInt(Math.max(-1, packet.playingIndex));
        buffer.writeBoolean(packet.paused);
        buffer.writeByteArray(packet.playerView.encode());
        buffer.writeByteArray(packet.accessView.encode());
    }

    public static PlaylistSyncPacketS2C decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        String fullStateKey = buffer.readUtf(512);
        boolean catalogOnly=buffer.readBoolean(),hasLibrary=buffer.readBoolean();
        var library=com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot.decode(buffer.readByteArray(com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot.MAX_BYTES));
        int count=buffer.readVarInt();if(count<0 || count>Playlist.MAX_ENTRIES)throw new IllegalArgumentException("Track count");
        var ids=new ArrayList<String>();var names=new ArrayList<String>();
        for(int i=0;i<count;i++){ids.add(buffer.readUtf(256));names.add(buffer.readUtf(256));}
        PlaylistSyncPacketS2C packet = new PlaylistSyncPacketS2C(pos, fullStateKey, ids, names, buffer.readVarInt(),
                buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
        packet.library=library;packet.catalogOnly=catalogOnly;packet.hasLibrary=hasLibrary;
        packet.playerView = com.nstut.simplyspeakers.playlist.PlayerViewSnapshot.decode(
                buffer.readByteArray(com.nstut.simplyspeakers.playlist.PlayerViewSnapshot.MAX_BYTES));
        packet.accessView=com.nstut.simplyspeakers.permissions.AccessViewSnapshot.decode(buffer.readByteArray(com.nstut.simplyspeakers.permissions.AccessViewSnapshot.MAX_BYTES));
        return packet;
    }

    public static void handle(PlaylistSyncPacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            var client=Minecraft.getInstance();
            if(!packet.catalogOnly && client.level!=null) {
                String prefix=client.level.dimension().identifier().toString()+"/";
                String stateKey=packet.fullStateKey.startsWith(prefix)?packet.fullStateKey.substring(prefix.length()):"";
                if(stateKey.isEmpty() && !com.nstut.simplyspeakers.client.ClientPortableSpeakers.isPortableToken(packet.pos) && client.level.getBlockEntity(packet.pos) instanceof com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity speaker) stateKey=speaker.getStateKey();
                if(!stateKey.isEmpty()) {
                    var state=com.nstut.simplyspeakers.client.ClientSpeakerRegistry.getOrCreateState(stateKey);
                    state.getPlaylist().setRepeatMode(com.nstut.simplyspeakers.playlist.RepeatMode.fromIndex(packet.repeatOrdinal));
                    state.getPlaylist().setShuffle(packet.shuffle);
                }
            }
            if (Minecraft.getInstance().screen instanceof SpeakerScreen screen
                    && matchesScreen(packet, screen)) {
                screen.updateAccessModel(packet.accessView);
                if(packet.catalogOnly)screen.updatePlaylistCatalog(packet.library);else screen.updatePlaylistModel(packet);
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
