package com.nstut.simplyspeakers.audio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChunkedFileTransfer {
    private ChunkedFileTransfer() {
    }

    @FunctionalInterface
    public interface ChunkConsumer {
        void accept(byte[] chunk, boolean isLast) throws IOException;
    }

    public static ExecutorService newDaemonFixedThreadPool(int threads, String threadName) {
        return Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    public static void writeChunks(Path filePath, Iterable<byte[]> chunks) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(filePath)) {
            for (byte[] chunk : chunks) {
                outputStream.write(chunk);
            }
        }
    }

    public static void streamFile(Path filePath, int chunkSize, ChunkConsumer consumer) throws IOException {
        long remaining = Files.size(filePath);

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[chunkSize];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                remaining -= read;
                consumer.accept(chunk, remaining <= 0);
            }

            if (remaining == 0 && Files.size(filePath) == 0) {
                consumer.accept(new byte[0], true);
            }
        }
    }
}
