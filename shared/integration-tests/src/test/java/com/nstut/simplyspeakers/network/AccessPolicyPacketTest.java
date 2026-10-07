package com.nstut.simplyspeakers.network;
import com.nstut.simplyspeakers.*;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Supplier;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AccessPolicyPacketTest {
    private final UUID owner=UUID.randomUUID(),other=UUID.randomUUID();
    private SpeakerState state;private SpeakerBlockEntity speaker;private ServerPlayer player;private NetworkManager.PacketContext context;
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @BeforeEach void setup()throws Exception {
        state=new SpeakerState();state.setOwnerUuid(owner);state.setAccessMode(SpeakerAccess.PUBLIC);
        speaker=mock(SpeakerBlockEntity.class);when(speaker.getSpeakerState()).thenReturn(state);
        var level=mock(ServerLevel.class);var server=mock(MinecraftServer.class);when(level.getServer()).thenReturn(server);when(server.getPlayerList()).thenReturn(mock(PlayerList.class));when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(speaker);
        player=mock(ServerPlayer.class);when(player.getUUID()).thenReturn(owner);when(player.level()).thenReturn(level);
        try{var method=ServerPlayer.class.getMethod("serverLevel");when(method.invoke(player)).thenReturn(level);}catch(NoSuchMethodException ignored){}
        context=mock(NetworkManager.PacketContext.class);when(context.getPlayer()).thenReturn(player);doAnswer(inv->{((Runnable)inv.getArgument(0)).run();return null;}).when(context).queue(any(Runnable.class));
        doAnswer(inv->{state.setAccessMode(inv.getArgument(0));return null;}).when(speaker).setAccessMode(any());
        doAnswer(inv->{state.claimOwnershipIfAbsent(inv.getArgument(0));return null;}).when(speaker).claimOwnership(any());
        doAnswer(inv->{if((boolean)inv.getArgument(1))state.trustPlayer(inv.getArgument(0));else state.distrustPlayer(inv.getArgument(0));return null;}).when(speaker).modifyTrust(any(),anyBoolean());
    }
    private void receive(Object packet)throws Exception {
        try(var physical=mockStatic(SpeakerPacketSecurity.class,CALLS_REAL_METHODS)){
            physical.when(()->SpeakerPacketSecurity.canModify(player,BlockPos.ZERO)).thenReturn(true);
            try{packet.getClass().getMethod("handle",packet.getClass(),NetworkManager.PacketContext.class).invoke(null,packet,context);}
            catch(NoSuchMethodException e){packet.getClass().getMethod("handle",packet.getClass(),Supplier.class).invoke(null,packet,(Supplier<NetworkManager.PacketContext>)()->context);}
        }
    }
    @Test void publicPlaybackDoesNotGrantAccessManagement()throws Exception {when(player.getUUID()).thenReturn(other);receive(SpeakerPolicyPacketC2S.accessMode(BlockPos.ZERO,SpeakerAccess.OWNER_ONLY));verify(speaker,never()).setAccessMode(any());assertEquals(SpeakerAccess.PUBLIC,state.getAccessMode());}
    @Test void ownerCanChangeAccessAndReceivesAuthoritativeReply()throws Exception {receive(SpeakerPolicyPacketC2S.accessMode(BlockPos.ZERO,SpeakerAccess.OWNER_ONLY));assertEquals(SpeakerAccess.OWNER_ONLY,state.getAccessMode());verify(speaker).sendPlaylistSync(player);}
    @Test void trustAdditionAndRemovalUsePersistedUuid()throws Exception {receive(SpeakerPolicyPacketC2S.trust(BlockPos.ZERO,other,true));assertTrue(state.isTrusted(other));receive(SpeakerPolicyPacketC2S.trust(BlockPos.ZERO,other,false));assertFalse(state.isTrusted(other));}
    @Test void invalidTrustDoesNotClaimAnUnownedNetwork()throws Exception {state.setOwnerUuid(null);receive(new SpeakerPolicyPacketC2S(BlockPos.ZERO,SpeakerPolicyPacketC2S.OP_TRUST_CHANGE,0,true,"bad player"));assertNull(state.getOwnerUuid());verify(speaker,never()).claimOwnership(any());}
    @Test void forgedOwnershipTransferRequiresManagementRights()throws Exception {when(player.getUUID()).thenReturn(other);receive(new SpeakerPolicyPacketC2S(BlockPos.ZERO,SpeakerPolicyPacketC2S.OP_TRANSFER_OWNER,0,false,other.toString()));verify(speaker,never()).claimOwnership(any());assertEquals(owner,state.getOwnerUuid());}
    @Test void disabledStreamsCannotEnterQueueThroughTheGuiPacket()throws Exception {
        boolean allowed=Config.allowRemoteStreams;Config.allowRemoteStreams=false;
        try(var operations=mockStatic(com.nstut.simplyspeakers.speakers.PlayerPlaylistControlService.class)){
            receive(new PlaylistControlPacketC2S(BlockPos.ZERO,PlaylistControlPacketC2S.OP_QUEUE_LAST,-1,false,"https://example.invalid/a.wav",""));
            operations.verifyNoInteractions();
        }finally{Config.allowRemoteStreams=allowed;}
    }
    @Test void privateUrlsCannotEnterPlaylistEvenWhenStreamsAreEnabled()throws Exception {
        boolean allowed=Config.allowRemoteStreams;Config.allowRemoteStreams=true;
        try(var operations=mockStatic(com.nstut.simplyspeakers.speakers.PlayerPlaylistControlService.class)){
            receive(new PlaylistControlPacketC2S(BlockPos.ZERO,PlaylistControlPacketC2S.OP_ADD,-1,false,"http://127.0.0.1/a.wav",""));
            operations.verifyNoInteractions();
        }finally{Config.allowRemoteStreams=allowed;}
    }

}
