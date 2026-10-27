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
    @Test void serverMetadataCannotEscapeTheAudioCache() throws Exception {
        try (var mc = mockStatic(Minecraft.class)) {
        mc.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
        Method cache = ClientAudioPlayer.class.getDeclaredMethod("cacheFile", String.class, String.class);
        cache.setAccessible(true);
        for (String id : List.of("../outside", "..\\outside", "/absolute", "C:relative", "a/b", "a\\b", "CON", "nul", "COM1")) {
            InvocationTargetException invalid = assertThrows(InvocationTargetException.class,
                    () -> cache.invoke(null, id, "test.wav"));
            assertInstanceOf(java.io.IOException.class, invalid.getCause());
        }
        InvocationTargetException invalid = assertThrows(InvocationTargetException.class,
                () -> cache.invoke(null, UUID.randomUUID().toString(), "test.wav/../../outside"));
        assertInstanceOf(java.io.IOException.class, invalid.getCause());
        assertNotNull(cache.invoke(null, UUID.randomUUID().toString(), "test.wav"));
        }
    }

    @Test @SuppressWarnings("unchecked") void cancelledFinalChunkCannotPublishItsPartialFile() throws Exception {
        try (var mc = mockStatic(Minecraft.class)) {
        mc.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
        Class<?> type = Class.forName(ClientAudioPlayer.class.getName() + "$DownloadProcess");
        Constructor<?> constructor = type.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        String audio = UUID.randomUUID().toString();
        Object process = constructor.newInstance(audio, "cancelled.wav");
        Map<String,Object> downloads = (Map<String,Object>)field(ClientAudioPlayer.class,"activeDownloads").get(null);
        Method add = type.getDeclaredMethod("addData", int.class, byte[].class); add.setAccessible(true);
        Method complete = type.getDeclaredMethod("complete"); complete.setAccessible(true);
        Method cleanup = type.getDeclaredMethod("cleanup"); cleanup.setAccessible(true);
        downloads.put(audio, process);
        try {
            add.invoke(process, 0, new byte[]{1,2,3});
            File part = (File)field(type,"partFile").get(process);
            field(type,"cancelled").set(process, true);
            InvocationTargetException invalid = assertThrows(InvocationTargetException.class, () -> complete.invoke(process));
            assertInstanceOf(java.io.IOException.class, invalid.getCause());
            assertTrue(part.exists(), "cancelled bytes remain owned by cleanup, never the published cache");
            assertFalse(new File(part.getParentFile(), audio + ".wav").exists());
            assertEquals(true, cleanup.invoke(process));
            assertFalse(part.exists());
        } finally { downloads.remove(audio,process); cleanup.invoke(process); }
        }
    }

    @Test void failedPartialDeletionCanBeRetried() throws Exception {
        try (var mc = mockStatic(Minecraft.class)) {
            mc.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            Class<?> type = Class.forName(ClientAudioPlayer.class.getName() + "$DownloadProcess");
            Constructor<?> constructor = type.getDeclaredConstructor(String.class, String.class); constructor.setAccessible(true);
            Object process = constructor.newInstance(UUID.randomUUID().toString(), "retry.wav");
            File part = (File)field(type,"partFile").get(process);
            Method cleanup = type.getDeclaredMethod("cleanup"); cleanup.setAccessible(true);
            Files.createDirectories(part.toPath());
            var child = part.toPath().resolve("held"); Files.write(child,new byte[]{1});
            try {
                assertEquals(false,cleanup.invoke(process));
                Files.delete(child);
                assertEquals(true,cleanup.invoke(process));
                assertFalse(part.exists());
            } finally { Files.deleteIfExists(child); Files.deleteIfExists(part.toPath()); }
        }
    }

    @Test @SuppressWarnings("unchecked") void missingUploadApprovalOrFinalAcknowledgementExpires() throws Exception {
        try (var mc = mockStatic(Minecraft.class)) {
            mc.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            Class<?> type = Class.forName(ClientAudioPlayer.class.getName() + "$UploadProcess");
            Constructor<?> constructor = type.getDeclaredConstructor(File.class); constructor.setAccessible(true);
            Object process = constructor.newInstance(new File("synthetic-timeout.wav"));
            field(type,"activity").setLong(process, System.nanoTime() - TimeUnit.SECONDS.toNanos(61));
            Map<UUID,Object> uploads = (Map<UUID,Object>)field(ClientAudioPlayer.class,"activeUploads").get(null);
            UUID id = UUID.randomUUID(); uploads.put(id,process);
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (uploads.containsKey(id) && System.nanoTime() < deadline) Thread.sleep(10);
                assertFalse(uploads.containsKey(id), "missing terminal replies must release client upload metadata");
            } finally { uploads.remove(id,process); }
        }
    }

    @Test @SuppressWarnings("unchecked") void staleUploadRepliesCannotCrossConnectionsAndInvalidNegotiationReleasesAdmission() throws Exception {
        Object[] connection = {mock(Minecraft.class.getMethod("getConnection").getReturnType())};
        Minecraft client = mock(Minecraft.class, call -> call.getMethod().getName().equals("getConnection")
                ? connection[0] : RETURNS_DEFAULTS.answer(call));
        // 26.2 reads screen state through the native Gui owner, rather than Minecraft.screen.
        Field guiOwner = field(Minecraft.class, "gui");
        guiOwner.set(client, mock(guiOwner.getType()));
        try (var mc = mockStatic(Minecraft.class)) {
            mc.when(Minecraft::getInstance).thenReturn(client);
            Class<?> type = Class.forName(ClientAudioPlayer.class.getName() + "$UploadProcess");
            Constructor<?> constructor = type.getDeclaredConstructor(File.class); constructor.setAccessible(true);
            Map<UUID,Object> uploads = (Map<UUID,Object>)field(ClientAudioPlayer.class,"activeUploads").get(null);
            UUID approval = UUID.randomUUID(), acknowledgement = UUID.randomUUID(), invalidSize = UUID.randomUUID();
            Object oldApproval = constructor.newInstance(new File("synthetic.wav"));
            uploads.put(approval,oldApproval);
            uploads.put(acknowledgement,constructor.newInstance(new File("synthetic.wav")));
            connection[0] = mock(Minecraft.class.getMethod("getConnection").getReturnType());
            try {
                ClientAudioPlayer.handleUploadResponse(approval,true,4096,null);
                assertFalse(field(type,"started").getBoolean(oldApproval));
                assertFalse(uploads.containsKey(approval));
                assertDoesNotThrow(() -> ClientAudioPlayer.handleUploadAcknowledgement(acknowledgement,true,null,null));
                assertFalse(uploads.containsKey(acknowledgement));
                uploads.put(invalidSize,constructor.newInstance(new File("synthetic.wav")));
                ClientAudioPlayer.handleUploadResponse(invalidSize,true,4095,null);
                assertFalse(uploads.containsKey(invalidSize));
            } finally { uploads.remove(approval); uploads.remove(acknowledgement); uploads.remove(invalidSize); }
        }
    }

}
