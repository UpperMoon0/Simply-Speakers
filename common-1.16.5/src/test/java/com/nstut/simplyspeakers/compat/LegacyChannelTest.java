package com.nstut.simplyspeakers.compat;
import io.netty.buffer.Unpooled;
import me.shedaniel.architectury.networking.NetworkManager;
import me.shedaniel.architectury.utils.Env;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class LegacyChannelTest {
    static class ControlC2S {}
    static class ClientOnlyS2C {}
    private static NetworkManager.PacketContext context(final Env side) {
        return new NetworkManager.PacketContext() {
            public Player getPlayer() {throw new AssertionError("No player access before direction validation");}
            public void queue(Runnable task) {task.run();}
            public Env getEnvironment() {return side;}
        };
    }
    @Test void serverNeverInvokesAClientOnlyPacketHandler() {
        assertTrue(LegacyChannel.accepts(ControlC2S.class,Env.SERVER));
        assertFalse(LegacyChannel.accepts(ClientOnlyS2C.class,Env.SERVER));
    }
    @Test void clientNeverInvokesAServerOnlyPacketHandler() {
        assertTrue(LegacyChannel.accepts(ClientOnlyS2C.class,Env.CLIENT));
        assertFalse(LegacyChannel.accepts(ControlC2S.class,Env.CLIENT));
        assertFalse(LegacyChannel.accepts(Object.class,Env.CLIENT));
    }
    @Test void malformedServerBoundS2cNeverDecodesOrInitializesClientCode() {
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeByte(0x80); // Deliberately unterminated VarInt.
            NetworkManager.NetworkReceiver receiver=LegacyChannel.receiver(ClientOnlyS2C.class,b->{
                throw new AssertionError("Client-only decoder must not initialize on a dedicated server");
            },(packet,ctx)->{throw new AssertionError("Client-only handler ran on server");});
            receiver.receive(buffer,context(Env.SERVER));
            assertEquals(0,buffer.readerIndex(),"Wrong-side bytes must remain unread");
        } finally {buffer.release();}
    }
    @Test void actualReceiverDispatchesOnlyCorrectDirectionAndPropagatesBoundedDecodeFailure() {
        AtomicInteger decoded=new AtomicInteger(),handled=new AtomicInteger();
        NetworkManager.NetworkReceiver receiver=LegacyChannel.receiver(ControlC2S.class,b->{
            decoded.incrementAndGet();assertEquals(42,b.readVarInt());return new ControlC2S();
        },(packet,ctx)->ctx.get().queue(handled::incrementAndGet));
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(42);
            receiver.receive(buffer,context(Env.CLIENT));assertEquals(0,decoded.get());assertEquals(0,handled.get());
            receiver.receive(buffer,context(Env.SERVER));assertEquals(1,decoded.get());assertEquals(1,handled.get());
            assertThrows(IndexOutOfBoundsException.class,()->receiver.receive(buffer,context(Env.SERVER)));
            assertEquals(1,handled.get(),"Malformed correct-side data must not reach handler");
        } finally {buffer.release();}
    }
    @Test void preservesArchitecturyUuidDerivedChannelId() {
        Class<?> type=com.nstut.simplyspeakers.network.StopAudioPacketS2C.class;
        String expected=java.util.UUID.nameUUIDFromBytes(type.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", "");
        assertEquals("simplyspeakers:main/"+expected,LegacyChannel.packetId(new ResourceLocation("simplyspeakers","main"),type).toString());
    }
}
