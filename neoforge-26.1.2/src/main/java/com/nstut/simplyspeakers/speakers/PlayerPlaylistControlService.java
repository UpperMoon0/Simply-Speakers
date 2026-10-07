package com.nstut.simplyspeakers.speakers;

import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.playlist.*;
import com.nstut.simplyspeakers.network.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Only the requesting player's catalog is editable; the speaker owns the runtime queue. */
public final class PlayerPlaylistControlService {
    private PlayerPlaylistControlService() {}
    public static boolean control(ServerPlayer player,String key,byte op,int index,boolean flag,String audio,String filename,String id) {
        var level=(net.minecraft.server.level.ServerLevel)player.level();var server=level.getServer();var state=ServerSpeakerRegistry.getSpeakerStateByFullKey(key);
        if(state==null)return false;
        PlayerPlaylistStore.migrate(key,state);
        var owner=player.getUUID();var catalog=PlayerPlaylistStore.library(owner);
        boolean runtime=op==6 || (op>=7 && op<=13) || op==15;
        if(runtime) {
            boolean applied=ServerSpeakerControlService.playlistControl(server,level,key,op,index,flag,audio,filename,"");
            if(applied && (op==7 || op==8) && owner.equals(state.getPlaylistOwnerUuid())) {
                var source=catalog.findSavedPlaylist(state.getActivePlaylistId());
                if(source!=null) { source.getPlaylist().setShuffle(state.getPlaylist().isShuffle(),state.getPlaylist().getShuffleSeed());source.getPlaylist().setRepeatMode(state.getPlaylist().getRepeatMode());PlayerPlaylistStore.changed(); }
            }
            return applied;
        }
        var saved=catalog.findSavedPlaylist(id);
        if(op!=17 && saved==null)return false;
        Playlist playlist=saved==null?null:saved.getPlaylist();
        var affected=new LinkedHashSet<String>();affected.add(key);
        for(var entry:ServerSpeakerRegistry.getAllSpeakerStates().entrySet())if(entry.getValue().usesPlayerPlaylist(owner,id))affected.add(entry.getKey());
        boolean changed=true;
        switch(op) {
            case 17 -> changed=catalog.createSavedPlaylist(filename)!=null;
            case 18 -> changed=catalog.renameSavedPlaylist(id,filename);
            case 20 -> changed=catalog.duplicateSavedPlaylist(id,filename)!=null;
            case 19 -> {
                changed=catalog.deleteSavedPlaylist(id);
                if(changed) for(var entry:ServerSpeakerRegistry.getAllSpeakerStates().entrySet()) if(entry.getValue().usesPlayerPlaylist(owner,id)) {
                    var linked=entry.getValue();
                    // Stop each loaded matching network in its own dimension.
                    var location=entry.getKey().substring(0,entry.getKey().indexOf('/'));
                    if(linked.isPlaylistSourceActive() && !linked.getPlaylist().isQueuedTrackActive()) {
                        for(var world:server.getAllLevels()) if(ServerSpeakerRegistry.getDimension(world).equals(location)) ServerSpeakerControlService.stop(server,world,entry.getKey());
                        linked.setAudioId("");linked.setAudioFilename("");
                    }
                    linked.detachPlayerPlaylist();
                }
            }
            case 0 -> { if(playlist.size()>=Playlist.MAX_ENTRIES || catalog.savedTrackCount()>=SpeakerState.MAX_SAVED_TRACKS)return false;playlist.add(audio,filename); }
            case 1 -> playlist.removeByAudioId(audio);
            case 16 -> { if(index<0 || index>=playlist.size())return false;playlist.removeAt(index); }
            case 3 -> { if(index<=0 || index>=playlist.size())return false;playlist.moveUp(index); }
            case 4 -> { if(index<0 || index>=playlist.size()-1)return false;playlist.moveDown(index); }
            case 5 -> playlist.clear();
            case 2,14 -> {
                if(playlist.isEmpty() || (op==2 && (index<0 || index>=playlist.size())))return false;
                for(var track:playlist.getTracks()) if(SpeakerPacketSecurity.resolveAuthorizedTrack(player,track.getAudioId())==null)return false;
                state.usePlayerPlaylist(owner,id,playlist);
                return ServerSpeakerControlService.playlistControl(server,level,key,op,index,flag,audio,filename,"");
            }
            default -> { return false; }
        }
        if(!changed)return false;
        if(playlist!=null && op!=19 && op!=18) for(var linked:ServerSpeakerRegistry.getAllSpeakerStates().values())
            if(linked.usesPlayerPlaylist(owner,id)) linked.getPlaylist().setTracks(SpeakerState.copyTracks(playlist));
        PlayerPlaylistStore.changed();ServerSpeakerRegistry.markDirty();
        // The snapshot is personalized at the network boundary for every recipient.
        for(String affectedKey:affected) {
            var linked=ServerSpeakerRegistry.getSpeakerStateByFullKey(affectedKey);if(linked==null)continue;
            String dimension=affectedKey.substring(0,affectedKey.indexOf('/'));
            for(var world:server.getAllLevels())if(ServerSpeakerRegistry.getDimension(world).equals(dimension))
                ServerPlaybackEnvironment.sendPlaylist(world,PlaylistSyncPacketS2C.fromState(net.minecraft.core.BlockPos.ZERO,affectedKey,linked,world.getGameTime(),com.nstut.simplyspeakers.SimplySpeakers.getAudioFileManager()));
        }
        return true;
    }
}
