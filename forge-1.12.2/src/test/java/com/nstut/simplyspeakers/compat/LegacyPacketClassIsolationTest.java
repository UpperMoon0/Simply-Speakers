package com.nstut.simplyspeakers.compat;
import org.junit.Test;
import net.minecraft.entity.player.EntityPlayer;
import com.nstut.simplyspeakers.testing.PacketClassIsolation;
public class LegacyPacketClassIsolationTest {
    @Test public void everyPacketCodecAndWrongSideHandlerIsDedicatedSafe() throws Exception {
        LegacyNetwork.PacketContext server=new LegacyNetwork.PacketContext() {
            public EntityPlayer getPlayer(){throw new AssertionError("Wrong-side handler read a player");}
            public void queue(Runnable work){throw new AssertionError("Wrong-side handler queued client work");}
        };
        PacketClassIsolation.verify(System.getProperty("simplyspeakers.testMainClasses",""),server);
    }
}
