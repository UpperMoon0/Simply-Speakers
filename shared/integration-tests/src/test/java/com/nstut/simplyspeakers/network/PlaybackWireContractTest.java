package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.audio.DirectionalAudio;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** One wire contract compiled against each version's actual packet implementations. */
class PlaybackWireContractTest {
    @SuppressWarnings("unchecked")
    private <T> T roundTrip(T packet) throws Exception {
        FriendlyByteBuf buffer;
        Class<?> bufferType;
        try {
            bufferType = Class.forName("net.minecraft.network.RegistryFriendlyByteBuf");
            Class<?> access = Class.forName("net.minecraft.core.RegistryAccess");
            buffer = (FriendlyByteBuf) bufferType.getConstructor(io.netty.buffer.ByteBuf.class, access)
                    .newInstance(Unpooled.buffer(), access.getField("EMPTY").get(null));
        } catch (ClassNotFoundException legacy) {
            bufferType = FriendlyByteBuf.class; buffer = new FriendlyByteBuf(Unpooled.buffer());
        }
        try {
            Class<?> type = packet.getClass();
            T decoded;
            if (bufferType == FriendlyByteBuf.class) {
                if (packet instanceof PlayAudioPacketS2C || packet instanceof SpeakerStateUpdatePacketS2C) {
                    type.getMethod("encode", type, bufferType).invoke(null, packet, buffer);
                } else { type.getMethod("encode", bufferType).invoke(packet, buffer); }
                decoded = (T) type.getConstructor(bufferType).newInstance(buffer);
            } else {
                type.getMethod("encode", bufferType, type).invoke(null, buffer, packet);
                decoded = (T) type.getMethod("decode", bufferType).invoke(null, buffer);
            }
            assertEquals(0, buffer.readableBytes(), "decoder must consume the full packet");
            return decoded;
        } finally { buffer.release(); }
    }

    @Test void policySnapshotRoundTripsWithPlaybackWithoutDroppingViewerRights() throws Exception {
        var state=new com.nstut.simplyspeakers.SpeakerState();
        var owner=java.util.UUID.randomUUID();var friend=java.util.UUID.randomUUID();
        state.setOwnerUuid(owner);state.setAccessMode(com.nstut.simplyspeakers.SpeakerAccess.TRUSTED);state.trustPlayer(friend);
        var source=PlaylistSyncPacketS2C.fromState(BlockPos.ZERO,"minecraft:overworld/net_policy",state,0);
        var view=com.nstut.simplyspeakers.permissions.AccessViewSnapshot.capture(state,friend,false,true,java.util.UUID::toString);
        var field=PlaylistSyncPacketS2C.class.getDeclaredField("accessView");field.setAccessible(true);field.set(source,view);
        var decoded=roundTrip(source);assertEquals(view,decoded.getAccessView());
        assertTrue(decoded.getAccessView().canControl());assertFalse(decoded.getAccessView().canManage());
    }

    @Test void playPreservesDimensionIdentityOccurrenceFacingAndTransportOffset() throws Exception {
        var pos = new BlockPos(-123, 64, 567);
        var packet = new PlayAudioPacketS2C(pos, "station", "https://example.invalid/a.wav", "a.wav",
                12.25f, false, 32, 0.75f, 0.3f)
                .withRemoteIdentity("minecraft:the_nether/net_station", 17)
                .withExtras(new DirectionalAudio.Extras(0.8f, 120, 0.5f, (byte) 5));
        var decoded = roundTrip(packet);
        assertEquals(pos, decoded.getPos()); assertEquals(packet.getAudioId(), decoded.getAudioId());
        assertEquals(12.25f, decoded.getPlaybackPositionSeconds());
        assertEquals(32, decoded.getMaxRange()); assertEquals(0.75f, decoded.getMaxVolume());
        assertEquals(0.3f, decoded.getAudioDropoff()); assertEquals(packet.getExtras(), decoded.getExtras());
        assertEquals("minecraft:the_nether/net_station", decoded.getFullStateKey());
        assertEquals(17, decoded.getPlaybackGeneration());
    }

    @Test void emptyPlaylistIsARealWireSnapshotWithPausedAndPlayingIndex() throws Exception {
        var decoded = roundTrip(new PlaylistSyncPacketS2C(new BlockPos(1, 2, 3),
                "minecraft:overworld/net_station", List.of(), List.of(), -1, true, 2, -1, true));
        assertEquals("minecraft:overworld/net_station", decoded.getFullStateKey());
        assertTrue(decoded.getAudioIds().isEmpty());
    }

    @Test void playerSnapshotCarriesTemporaryQueueAndCurrentPlayback() throws Exception {
        var state = new com.nstut.simplyspeakers.SpeakerState("a", "A.wav", false, false, -1);
        state.getPlaylist().add("a", "A.wav"); state.getPlaylist().add("b", "B.wav");
        state.getPlaylist().playFromStart(); state.getPlaylist().queueLast("x");
        state.startPlaybackAt(100, 15); state.pauseAt(140);
        var packet = PlaylistSyncPacketS2C.fromState(new BlockPos(1,2,3), "minecraft:overworld/net_station", state, 160);
        var decoded = roundTrip(packet);
        assertEquals(packet.getPlayerView(), decoded.getPlayerView());
        assertEquals(List.of("x"), decoded.getPlayerView().queue());
        assertEquals(List.of(1), decoded.getPlayerView().upcoming());
        assertEquals(17, decoded.getPlayerView().positionSeconds());
        assertTrue(decoded.isPaused());
        assertTrue(decoded.getPlayerView().playing());
    }

    @Test void namedCatalogAndPlayingSourceSurviveTheActualVersionWireCodec() throws Exception {
        var state=new com.nstut.simplyspeakers.SpeakerState();state.getPlaylist().add("a","A.wav");String id=state.createSavedPlaylist("Evening");state.findSavedPlaylist(id).getPlaylist().add("b","B.wav");state.activateSavedPlaylist(id);
        var decoded=roundTrip(PlaylistSyncPacketS2C.fromState(BlockPos.ZERO,"network",state,0));
        assertEquals(id,decoded.getLibrary().activeId());assertEquals(2,decoded.getLibrary().entries().size());assertEquals(List.of("b"),decoded.getAudioIds());assertEquals(List.of("a"),decoded.getLibrary().find("default").audioIds());
    }
    @Test void playlistMutationsCarryAnExplicitTargetThroughTheActualVersionWireCodec() throws Exception {
        var decoded=roundTrip(new PlaylistControlPacketC2S(BlockPos.ZERO,PlaylistControlPacketC2S.OP_RENAME_PLAYLIST,-1,false,"","New name","target-list"));
        assertEquals("target-list",decoded.getPlaylistId());
    }

    @Test void recipientCatalogNeverLeaksAnotherPlayersListAndEmptyCatalogKeepsSpeakerPlayback()throws Exception {
        var registry=com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.class;
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.resetForWorld();
        try {
            var owner=java.util.UUID.randomUUID();var other=java.util.UUID.randomUUID();
            var catalog=com.nstut.simplyspeakers.playlist.PlayerPlaylistStore.library(owner);String id=catalog.createSavedPlaylist("Private clips");
            catalog.findSavedPlaylist(id).getPlaylist().add("a","A.wav");
            var state=new com.nstut.simplyspeakers.SpeakerState();state.usePlayerPlaylist(owner,id,catalog.findSavedPlaylist(id).getPlaylist());state.getPlaylist().queueLast("queued");
            com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:overworld/personal",state);
            var packet=PlaylistSyncPacketS2C.fromState(BlockPos.ZERO,"minecraft:overworld/personal",state,0);
            var personal=roundTrip(packet.forPlayer(owner));assertEquals("Private clips",personal.getLibrary().find(id).name());
            var visitor=roundTrip(packet.forPlayer(other));assertTrue(visitor.getLibrary().entries().isEmpty());assertEquals("",visitor.getLibrary().activeId());
            assertEquals(List.of("a"),visitor.getAudioIds());assertEquals(List.of("queued"),visitor.getPlayerView().queue());
            assertEquals(1,packet.forPlayer(owner).getLibrary().entries().size(),"Personalizing must not mutate the shared snapshot");
        } finally { com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.resetForWorld(); }
    }

    @Test void maximumPersonalLibraryAndIndependentRuntimeQueueUseSeparateBoundedPayloads()throws Exception {
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.resetForWorld();
        try {
            var owner=java.util.UUID.randomUUID();var catalog=com.nstut.simplyspeakers.playlist.PlayerPlaylistStore.library(owner);String text="\u4e00".repeat(256);
            for(int n=0;n<2;n++){String id=catalog.createSavedPlaylist("List "+n);for(int i=0;i<256;i++)catalog.findSavedPlaylist(id).getPlaylist().add(text,text);}
            var state=new com.nstut.simplyspeakers.SpeakerState();for(int i=0;i<256;i++){state.getPlaylist().add(text,text);state.getPlaylist().queueLast(text);}
            com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:overworld/large",state);
            var split=PlaylistSyncPacketS2C.fromState(BlockPos.ZERO,"minecraft:overworld/large",state,0).splitForPlayer(owner);
            assertTrue(split.get(0).isCatalogOnly());assertEquals(2,roundTrip(split.get(0)).getLibrary().entries().size());
            var runtime=roundTrip(split.get(1));assertFalse(runtime.hasLibrary());assertEquals(256,runtime.getAudioIds().size());assertEquals(256,runtime.getPlayerView().queue().size());
            assertTrue(split.get(0).getLibrary().encode().length+2000<1_048_576);
            assertTrue(256*2*256*3+runtime.getPlayerView().encode().length+2000<1_048_576);
        }finally{com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.resetForWorld();}
    }
    @Test void authoritativeSettingsSurviveTheStatePacketWire() throws Exception {
        var server=new com.nstut.simplyspeakers.SpeakerState();server.setNetworkName("Hall");
        server.setMaxVolume(.7f);server.setControllerVolume(0f);server.setMaxRange(32);server.setDirectionality(.8f);
        server.setConeAngleDegrees(120);server.setRearAttenuation(.2f);server.setAudioDropoff(.4f);
        var packet=new SpeakerStateUpdatePacketS2C(BlockPos.ZERO,"station","update","","",-1,false,"minecraft:overworld/net_station").withSettings(server);
        var decoded=roundTrip(packet);var client=new com.nstut.simplyspeakers.SpeakerState();decoded.getSettings().apply(client);
        assertEquals("Hall",client.getNetworkName());assertEquals(.7f,client.getConfiguredMaxVolume());
        assertEquals(0f,client.getMaxVolume());assertEquals(32,client.getMaxRange());assertEquals(.8f,client.getDirectionality());
        assertEquals(120,client.getConeAngleDegrees());assertEquals(.2f,client.getRearAttenuation());assertEquals(.4f,client.getAudioDropoff());
    }
}
