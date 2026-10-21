package com.nstut.simplyspeakers.testing;

import java.lang.reflect.Field;
import net.minecraft.command.CommandBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Bootstrap;
import net.minecraft.server.MinecraftServer;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LegacyAcknowledgementPermissionTest {
    private String previous;
    private MinecraftServer server;
    private EntityPlayerMP participant;
    private CommandBase command;
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }
    @Before public void prepare() throws Exception {
        previous = System.getProperty(LegacyRuntimeAcceptance.PROPERTY);
        System.setProperty(LegacyRuntimeAcceptance.PROPERTY, "true");
        server = mock(MinecraftServer.class); participant = mock(EntityPlayerMP.class);
        field("current", server); field("participant", participant);
        command = LegacyRuntimeAcceptance.acknowledgementCommand();
    }
    @After public void clear() throws Exception {
        field("current", null); field("participant", null);
        if (previous == null) System.clearProperty(LegacyRuntimeAcceptance.PROPERTY);
        else System.setProperty(LegacyRuntimeAcceptance.PROPERTY, previous);
    }
    @Test public void exactParticipantMayAcknowledgeWithoutOperatorRights() {
        assertFalse(participant.canUseCommand(0, command.getName()));
        assertTrue(command.checkPermission(server, participant));
    }
    @Test public void DisabledWrongServerAndOtherSendersCannotAcknowledge() throws Exception {
        assertFalse(command.checkPermission(mock(MinecraftServer.class), participant));
        assertFalse(command.checkPermission(server, mock(EntityPlayerMP.class)));
        assertFalse(command.checkPermission(server, null));
        System.clearProperty(LegacyRuntimeAcceptance.PROPERTY);
        assertFalse(command.checkPermission(server, participant));
        System.setProperty(LegacyRuntimeAcceptance.PROPERTY, "true");
        field("participant", null); assertFalse(command.checkPermission(server, null));
        field("current", null); assertFalse(command.checkPermission(null, null));
    }
    @Test public void DirectExecutionStillRejectsWrongSenderOrMalformedArguments() throws Exception {
        for (boolean wrongSender : new boolean[]{true, false}) {
            try {
                command.execute(server, wrongSender ? mock(EntityPlayerMP.class) : participant,
                        wrongSender ? new String[]{"started"} : new String[0]);
                fail("Invalid acknowledgement must not reach phase transition");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("unexpected acceptance sender or arguments"));
            }
        }
    }
    private static void field(String name, Object value) throws Exception {
        Field field = LegacyRuntimeAcceptance.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
}
