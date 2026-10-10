package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.SpeakerSettings;
import com.nstut.simplyspeakers.portable.PortableEmitterSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises actual portable pose resolution and the real playback cleanup entry points.
 * OpenAL playback itself is independently required by the opt-in live fixture. */
class ClientPortableSpeakersIntegrationTest {
    private final BlockPos token = new BlockPos(31, -2048, 42);
    private final UUID identity = UUID.randomUUID(), holderId = UUID.randomUUID();
    private Minecraft client;
    private ClientLevel level;
    private MockedStatic<Minecraft> minecraft;
    private MockedStatic<?> spatial;

    @BeforeEach void setup() throws Exception {
        client = mock(Minecraft.class);
        level = mock(ClientLevel.class);
        client.level = level;
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        minecraft = mockStatic(Minecraft.class);
        minecraft.when(Minecraft::getInstance).thenReturn(client);
        ClientAudioPlayer.stopAll();
        ClientPortableSpeakers.clear();
        try {
            spatial = mockStatic(Class.forName("com.nstut.simplyspeakers.client.compat.sable.ClientSpeakerSpatialResolver"), call -> {
                if (call.getMethod().getName().equals("resolveRender")) {
                    var position = (net.minecraft.core.Position) call.getArgument(1);
                    return new Vec3(position.x(), position.y(), position.z());
                }
                if (call.getMethod().getName().equals("resolveRenderFacing") && call.getArguments().length == 4)
                    return new double[]{call.getArgument(2), call.getArgument(3)};
                return RETURNS_DEFAULTS.answer(call);
            });
        } catch (ClassNotFoundException vanilla) { /* Only the 1.21.1 adapter integrates Sable. */ }
    }
    @AfterEach void cleanup() {
        ClientAudioPlayer.stopAll();
        ClientPortableSpeakers.clear();
        if (spatial != null) spatial.close();
        minecraft.close();
    }
    private PortableEmitterSnapshot snapshot(double x) {
        return new PortableEmitterSnapshot(identity, holderId, "minecraft:overworld", x, 65, 4, 90);
    }
    @SuppressWarnings("unchecked")
    private PlaybackMembership<BlockPos> membership() throws Exception {
        var field = ClientAudioPlayer.class.getDeclaredField("membership");
        field.setAccessible(true);
        return (PlaybackMembership<BlockPos>) field.get(null);
    }
    private void settleSnapshot() throws Exception {
        var field = ClientPortableSpeakers.class.getDeclaredField("poses"); field.setAccessible(true);
        var pose = ((Map<?, ?>) field.get(null)).get(token);
        var received = pose.getClass().getDeclaredField("received"); received.setAccessible(true);
        received.setLong(pose, System.nanoTime() - 1_000_000_000L);
    }

    @Test void trackedCarrierUsesMovingEntityPoseRatherThanTheReservedToken() {
        Player holder = mock(Player.class);
        when(level.getPlayerByUUID(holderId)).thenReturn(holder);
        when(holder.isAlive()).thenReturn(true);
        when(holder.getPosition(anyFloat())).thenReturn(new Vec3(2.25, 70, -8.5));
        when(holder.position()).thenReturn(new Vec3(2.25, 70, -8.5));
        when(holder.getYRot()).thenReturn(90f);
        assertTrue(ClientPortableSpeakers.begin(token, snapshot(1)));
        assertEquals(new Vec3(2.25, 71, -8.5), ClientPortableSpeakers.resolvePosition(token));
        when(holder.getPosition(anyFloat())).thenReturn(new Vec3(13.75, 70, -8.5));
        assertEquals(new Vec3(13.75, 71, -8.5), ClientPortableSpeakers.resolvePosition(token));
        assertArrayEquals(new double[]{-1, 0}, ClientPortableSpeakers.resolveFacing(token), 0.00001);
        assertEquals(1, ClientPortableSpeakers.poseCount());
        when(holder.isAlive()).thenReturn(false);
        assertNull(ClientPortableSpeakers.resolvePosition(token), "a dead tracked holder must not fall back to an old audible snapshot");
    }

    @Test void untrackedFarCarrierUsesAuthoritativePositionsAndTeleportsSnap() throws Exception {
        assertTrue(ClientPortableSpeakers.begin(token, snapshot(1)));
        assertEquals(new Vec3(1, 65, 4), ClientPortableSpeakers.resolvePosition(token));
        ClientPortableSpeakers.update(token, snapshot(5));
        settleSnapshot();
        assertEquals(new Vec3(5, 65, 4), ClientPortableSpeakers.resolvePosition(token));
        ClientPortableSpeakers.update(token, snapshot(200));
        assertEquals(new Vec3(200, 65, 4), ClientPortableSpeakers.resolvePosition(token));
    }

    @Test void foreignDimensionAndOrdinaryBlockPositionsCannotCreatePortableEmitters() throws Exception {
        assertFalse(ClientPortableSpeakers.begin(BlockPos.ZERO, snapshot(1)));
        var foreign = new PortableEmitterSnapshot(identity, holderId, "minecraft:the_nether", 1, 65, 4, 0);
        assertFalse(ClientPortableSpeakers.begin(token, foreign));
        assertEquals(0, ClientPortableSpeakers.poseCount());
        assertTrue(ClientPortableSpeakers.begin(token, snapshot(1)));
        membership().track(token, "portable_dimension", new SpeakerSettings(1, 16, 1));
        when(level.dimension()).thenReturn(Level.NETHER);
        assertNull(ClientPortableSpeakers.resolvePosition(token));
        assertNull(ClientPortableSpeakers.resolveFacing(token));
        ClientPortableSpeakers.tick();
        assertFalse(membership().isTracking(token)); assertFalse(ClientPortableSpeakers.contains(token));
    }

    @Test void staleIdentityOrHolderUpdatesCannotMoveOrResurrectAnEmitter() {
        assertTrue(ClientPortableSpeakers.begin(token, snapshot(1)));
        ClientPortableSpeakers.update(token, new PortableEmitterSnapshot(UUID.randomUUID(), holderId,
                "minecraft:overworld", 500, 65, 4, 0));
        ClientPortableSpeakers.update(token, new PortableEmitterSnapshot(identity, UUID.randomUUID(),
                "minecraft:overworld", 600, 65, 4, 0));
        assertEquals(new Vec3(1, 65, 4), ClientPortableSpeakers.resolvePosition(token));
        ClientAudioPlayer.stop(token);
        ClientPortableSpeakers.update(token, snapshot(700));
        assertFalse(ClientPortableSpeakers.contains(token));
        assertNull(ClientPortableSpeakers.resolvePosition(token));
    }

    @Test void replacementCarrierInvalidatesTheOldPlaybackMembership() throws Exception {
        assertTrue(ClientPortableSpeakers.begin(token, snapshot(1)));
        membership().track(token, "portable_test", new SpeakerSettings(1, 16, 1));
        var transferred = new PortableEmitterSnapshot(identity, UUID.randomUUID(), "minecraft:overworld", 40, 65, 4, 0);
        assertTrue(ClientPortableSpeakers.begin(token, transferred));
        assertFalse(membership().isTracking(token));
        assertEquals(new Vec3(40, 65, 4), ClientPortableSpeakers.resolvePosition(token));
    }

    @Test void networkStopCleansOnlyItsPortablePosesAndGlobalStopCleansAll() throws Exception {
        var other = token.offset(1, 0, 0);
        assertTrue(ClientPortableSpeakers.begin(token, snapshot(1)));
        assertTrue(ClientPortableSpeakers.begin(other, new PortableEmitterSnapshot(UUID.randomUUID(), holderId,
                "minecraft:overworld", 2, 65, 4, 0)));
        membership().track(token, "portable_test", new SpeakerSettings(1, 16, 1));
        membership().track(other, "portable_other", new SpeakerSettings(1, 16, 1));
        ClientAudioPlayer.stopNetwork("portable_test");
        assertFalse(ClientPortableSpeakers.contains(token));
        assertTrue(ClientPortableSpeakers.contains(other));
        ClientAudioPlayer.stopAll();
        assertEquals(0, ClientPortableSpeakers.poseCount());
        assertEquals(0, membership().size());
    }
}
