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
    void blockedReadCannotWriteOrDeleteBeforeWorkerExits(String action, @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var tasks=new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
        var reading=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var closed=new java.util.concurrent.CountDownLatch(1);
        var writes=new java.util.concurrent.atomic.AtomicInteger();
        var deleted=new AtomicBoolean();
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Minecraft client=mock(Minecraft.class,call -> {
            if((call.getMethod().getName().equals("tell") || call.getMethod().getName().equals("execute"))
                    && call.getArguments().length==1 && call.getArgument(0) instanceof Runnable task) {
                tasks.add(task);return null;
            }
            return RETURNS_DEFAULTS.answer(call);
        });
        var format=new javax.sound.sampled.AudioFormat(8000,16,1,true,false);
        var pcm=new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(new byte[4]),format,2) {
            @Override public int read(byte[] data,int offset,int length) {
                reading.countDown();
                // A network/decoder read may ignore interruption and even a close request.
                boolean interrupted=false;
                while(release.getCount()>0)try { release.await(); } catch(InterruptedException e){interrupted=true;}
                if(interrupted)Thread.currentThread().interrupt();
                return 4;
            }
            @Override public void close() { closed.countDown(); }
        };
        Class<?> type=Class.forName("com.nstut.simplyspeakers.client.ClientAudioPlayer$StreamingAudioResource");
        var constructor=type.getDeclaredConstructor(String.class,int.class,int[].class,Thread.class,boolean.class);
        constructor.setAccessible(true);
        Object old=constructor.newInstance("blocked-test",17,new int[]{1,2,3},null,false);
        Object replacement=constructor.newInstance("blocked-test",18,new int[]{4,5,6},null,false);
        var resources=ClientAudioPlayer.class.getDeclaredField("networkResources");resources.setAccessible(true);
        @SuppressWarnings("unchecked") var map=(java.util.Map<String,Object>)resources.get(null);
        var threadField=type.getDeclaredField("streamingThread");threadField.setAccessible(true);
        var stop=type.getDeclaredMethod("stopAndCleanup");stop.setAccessible(true);
        var worker=ClientAudioPlayer.class.getDeclaredMethod(action.startsWith("url")?"streamUrlAudioData":"streamAudioData",type,String.class,float.class);worker.setAccessible(true);
        var opener=ClientAudioPlayer.class.getDeclaredMethod("openUrlStream",type,String.class);opener.setAccessible(true);
        var file=java.nio.file.Files.createFile(directory.resolve("blocked.wav")).toFile();
        Thread thread=new Thread(() -> {
            try(var audio=mockStatic(ClientAudioPlayer.class);
                var decoder=mockStatic(com.nstut.simplyspeakers.audio.IncrementalAudioDecoders.class);
                var al=mockStatic(org.lwjgl.openal.AL10.class,call -> {
                    if(call.getMethod().getName().equals("alBufferData") || call.getMethod().getName().equals("alSourceQueueBuffers")
                            || call.getMethod().getName().equals("alSourcePlay"))writes.incrementAndGet();
                    return RETURNS_DEFAULTS.answer(call);
                })) {
                String input=action.startsWith("url")?"https://example.invalid/blocked.wav":file.getPath();
                audio.when(() -> opener.invoke(null,old,input)).thenReturn(pcm);
                decoder.when(() -> com.nstut.simplyspeakers.audio.IncrementalAudioDecoders.openPcmStream(file)).thenReturn(pcm);
                audio.when(() -> worker.invoke(null,old,input,0f)).thenCallRealMethod();
                worker.invoke(null,old,input,0f);
            } catch(Throwable e){failure.set(e);}
        },"blocked-decoder-test");
        threadField.set(old,thread);map.put("blocked-test",old);
        try(var minecraft=mockStatic(Minecraft.class);
            var al=mockStatic(org.lwjgl.openal.AL10.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            al.when(() -> org.lwjgl.openal.AL10.alIsSource(17)).thenReturn(true);
            al.when(() -> org.lwjgl.openal.AL10.alDeleteSources(17)).thenAnswer(call -> {deleted.set(true);return null;});
            thread.start();
            assertTrue(reading.await(5,java.util.concurrent.TimeUnit.SECONDS),"production worker must reach the blocked read");
            if(action.endsWith("stop"))map.remove("blocked-test",old);else map.put("blocked-test",replacement);
            stop.invoke(old);
            assertTrue(closed.await(5,java.util.concurrent.TimeUnit.SECONDS),"cancellation must close the active input");
            // Longer than the old unsafe join timeout: nothing may delete resources yet.
            Thread.sleep(650);
            assertTrue(thread.isAlive());assertTrue(tasks.isEmpty());assertFalse(deleted.get());
            release.countDown();thread.join(5000);
            assertFalse(thread.isAlive());assertNull(failure.get());
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while(tasks.isEmpty() && System.nanoTime()<deadline)Thread.sleep(10);
            assertEquals(1,tasks.size());tasks.remove().run();
            assertTrue(deleted.get());assertEquals(0,writes.get(),"cancelled read must never submit OpenAL work");
            assertSame(action.endsWith("stop")?null:replacement,map.get("blocked-test"));
        } finally {release.countDown();thread.join(5000);map.remove("blocked-test");}
    }

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
