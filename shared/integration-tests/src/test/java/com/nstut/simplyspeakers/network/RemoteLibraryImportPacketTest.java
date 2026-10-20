package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.speakers.PlayerPlaylistControlService;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.function.Supplier;
import static org.mockito.Mockito.*;

class RemoteLibraryImportPacketTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}

    private void receive(boolean canControl) throws Exception {
        var player=mock(ServerPlayer.class);var owner=UUID.randomUUID();when(player.getUUID()).thenReturn(owner);
        var manager=mock(AudioFileManager.class);var context=mock(NetworkManager.PacketContext.class);
        when(context.getPlayer()).thenReturn(player);
        doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(context).queue(any(Runnable.class));
        var packet=new AddRemoteAudioPacketC2S(BlockPos.ZERO,"https://example.invalid/a.wav","Rain");
        try(var security=mockStatic(SpeakerPacketSecurity.class);var mod=mockStatic(SimplySpeakers.class);
            var playback=mockStatic(PlayerPlaylistControlService.class)) {
            security.when(()->SpeakerPacketSecurity.canControlSpeaker(player,BlockPos.ZERO)).thenReturn(canControl);
            mod.when(SimplySpeakers::getAudioFileManager).thenReturn(manager);
            try {packet.getClass().getMethod("handle",packet.getClass(),NetworkManager.PacketContext.class).invoke(null,packet,context);}
            catch(NoSuchMethodException legacy) {packet.getClass().getMethod("handle",packet.getClass(),Supplier.class).invoke(null,packet,(Supplier<NetworkManager.PacketContext>)()->context);}
            if(canControl) {
                verify(manager).addRemoteAudio("https://example.invalid/a.wav","Rain",owner.toString());
                verify(manager).sendAudioList(player,BlockPos.ZERO);
            } else verifyNoInteractions(manager);
            playback.verifyNoInteractions();
        }
    }
    @Test void importRequiresCurrentSpeakerControl() throws Exception {receive(false);}
    @Test void permittedImportRefreshesLibraryWithoutPlayingOrQueueing() throws Exception {receive(true);}
}
