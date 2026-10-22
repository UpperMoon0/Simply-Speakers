package com.nstut.simplyspeakers.platform;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class ClientFileSelectionTest {
    @Test void workerCompletionCannotTouchUiUntilClientExecutorDrains() throws Exception {
        Queue<Runnable> client=new ConcurrentLinkedQueue<>();
        AtomicReference<Thread> dialogThread=new AtomicReference<>(),callbackThread=new AtomicReference<>();
        AtomicReference<File> selected=new AtomicReference<>();
        Thread caller=Thread.currentThread();
        Thread worker=ClientFileSelection.open(()->{dialogThread.set(Thread.currentThread());return "invalid-extension.txt";},client::add,
                file->{selected.set(file);callbackThread.set(Thread.currentThread());});
        worker.join(5000);assertFalse(worker.isAlive());assertNotSame(caller,dialogThread.get());
        assertNull(selected.get());assertNull(callbackThread.get());assertEquals(1,client.size());
        client.remove().run();assertEquals(new File("invalid-extension.txt"),selected.get());assertSame(caller,callbackThread.get());
    }
    @Test void cancellationSchedulesNoCallback() throws Exception {
        Queue<Runnable> client=new ConcurrentLinkedQueue<>();
        Thread worker=ClientFileSelection.open(()->null,client::add,file->fail("Cancelled selection ran callback"));
        worker.join(5000);assertFalse(worker.isAlive());assertTrue(client.isEmpty());
    }
}
