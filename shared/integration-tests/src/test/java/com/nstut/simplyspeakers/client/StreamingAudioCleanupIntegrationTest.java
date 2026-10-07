package com.nstut.simplyspeakers.client;

import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamingAudioCleanupIntegrationTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"url-stop","url-restart","url-seek","file-stop","file-restart","file-seek"})
    void delayedWorkerCannotAcquireOrCleanReplacementResource(String action) throws Exception {
        var tasks=new ArrayList<Runnable>();
        Minecraft client=mock(Minecraft.class,call -> {
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
            Object old=constructor.newInstance("race-test",17,new int[]{1,2,3},null,false);
            Object replacement=constructor.newInstance("race-test",18,new int[]{4,5,6},null,false);
            var resources=ClientAudioPlayer.class.getDeclaredField("networkResources");resources.setAccessible(true);
            @SuppressWarnings("unchecked") var map=(java.util.Map<String,Object>)resources.get(null);
            var flag=type.getDeclaredField("stopFlag");flag.setAccessible(true);
            try {
                if(!action.endsWith("stop"))map.put("race-test",replacement);
                // Invoke the production decoder entry point only after transport replaced/stopped it.
                var worker=ClientAudioPlayer.class.getDeclaredMethod(action.startsWith("url")?"streamUrlAudioData":"streamAudioData",
                        type,String.class,float.class);worker.setAccessible(true);
                worker.invoke(null,old,action.startsWith("url")?"http://127.0.0.1/blocked.wav":"missing-file.wav",0f);
                assertSame(action.endsWith("stop")?null:replacement,map.get("race-test"));
                assertFalse(((AtomicBoolean)flag.get(replacement)).get());
                assertTrue(((AtomicBoolean)flag.get(old)).get());
                assertEquals(1,tasks.size(),"only the old resource may schedule cleanup");
            } finally { map.remove("race-test"); }
        }
    }

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
