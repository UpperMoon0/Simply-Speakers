package com.nstut.simplyspeakers.client;

import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual native receive path: wrong generation, background writes and invalid indices. */
class AudioDownloadTransferTest {
    private Field field(Class<?> type,String name) throws Exception { Field f=type.getDeclaredField(name);f.setAccessible(true);return f; }
    @Test @SuppressWarnings("unchecked") void receiptFollowsWorkerWriteAndStaleRequestsCannotCreateFiles() throws Exception {
        BlockingQueue<Runnable> callbacks=new LinkedBlockingQueue<>();
        List<Thread> dispatchThreads=new CopyOnWriteArrayList<>();
        Minecraft client=mock(Minecraft.class, call->{
            if(call.getMethod().getName().equals("execute") && call.getArgument(0) instanceof Runnable task) {
                dispatchThreads.add(Thread.currentThread());callbacks.add(task);return null;
            }
            return RETURNS_DEFAULTS.answer(call);
        });
        try(var mc=mockStatic(Minecraft.class)) {
            mc.when(Minecraft::getInstance).thenReturn(client);
            Class<?> type=Class.forName(ClientAudioPlayer.class.getName()+"$DownloadProcess");
            Constructor<?> constructor=type.getDeclaredConstructor(String.class,String.class);constructor.setAccessible(true);
            String audio=UUID.randomUUID().toString();Object process=constructor.newInstance(audio,"test.wav");
            UUID request=(UUID)field(type,"transferId").get(process);
            File part=(File)field(type,"partFile").get(process);
            Map<String,Object> downloads=(Map<String,Object>)field(ClientAudioPlayer.class,"activeDownloads").get(null);
            downloads.put(audio,process);
            try {
                ClientAudioPlayer.handleAudioFileChunk(audio,UUID.randomUUID(),0,new byte[]{1},false);
                assertFalse(part.exists());assertTrue(callbacks.isEmpty());
                byte[] chunk=new byte[4096];Arrays.fill(chunk,(byte)27);
                ClientAudioPlayer.handleAudioFileChunk(audio,request,0,chunk,false);
                assertNotNull(callbacks.poll(5,TimeUnit.SECONDS),"receipt must follow a real worker write");
                assertArrayEquals(chunk,Files.readAllBytes(part.toPath()));
                assertNotSame(Thread.currentThread(),dispatchThreads.get(0));
                ClientAudioPlayer.handleAudioFileChunk(audio,request,2,new byte[]{1},false);
                assertNotNull(callbacks.poll(5,TimeUnit.SECONDS));
                assertFalse(part.exists(),"invalid index must close and delete partial data");
            } finally {
                downloads.remove(audio,process);
                Method cleanup=type.getDeclaredMethod("cleanup");cleanup.setAccessible(true);cleanup.invoke(process);
            }
        }
    }
}
