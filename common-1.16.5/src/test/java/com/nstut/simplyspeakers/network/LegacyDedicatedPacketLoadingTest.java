package com.nstut.simplyspeakers.network;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import me.shedaniel.architectury.networking.NetworkManager;
import me.shedaniel.architectury.utils.Env;
import com.nstut.simplyspeakers.testing.PacketClassIsolation;
class LegacyDedicatedPacketLoadingTest {
    @Test void allServerRegisteredPacketClassesInitializeWithoutClientClasses() throws Exception {
        NetworkManager.PacketContext server=mock(NetworkManager.PacketContext.class);
        when(server.getEnvironment()).thenReturn(Env.SERVER);
        PacketClassIsolation.verify(System.getProperty("simplyspeakers.testMainClasses",""),server);
    }
}
