package com.nstut.simplyspeakers.client;

import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamingAudioCleanupIntegrationTest {
    @Test void transportStopAndDecoderCompletionScheduleOnlyOneCleanup() throws Exception {
        var tasks=new ArrayList<Runnable>();
        Minecraft client=mock(Minecraft.class,call -> {
            // Minecraft renamed tell to execute in 26.1.2.
            if((call.getMethod().getName().equals("tell") || call.getMethod().getName().equals("execute"))
                    && call.getArguments().length==1 && call.getArgument(0) instanceof Runnable task) {
                tasks.add(task);return null;
            }
            return RETURNS_DEFAULTS.answer(call);
        });
        try(var minecraft=mockStatic(Minecraft.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            Class<?> type=Class.forName("com.nstut.simplyspeakers.client.ClientAudioPlayer$StreamingAudioResource");
            var constructor=type.getDeclaredConstructor(String.class,int.class,int[].class,Thread.class,boolean.class);
            constructor.setAccessible(true);
            Object resource=constructor.newInstance("test",17,new int[]{1,2,3},null,false);
            var stop=type.getDeclaredMethod("stopAndCleanup");stop.setAccessible(true);
            stop.invoke(resource);stop.invoke(resource);stop.invoke(resource);
            assertEquals(1,tasks.size(),"repeated cleanup must not delete an OpenAL source ID reused by new playback");
            var flag=type.getDeclaredField("stopFlag");flag.setAccessible(true);
            assertTrue(((AtomicBoolean)flag.get(resource)).get());
        }
    }
}
