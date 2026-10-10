package com.nstut.simplyspeakers.network;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies real payload bytecode without any client classes visible to its loader.
 * Common 1.20.1/1.21.1 also initialize payloads and their codec fields here. NeoForge
 * 26 instruments class initialization with a loader-private guardian, so its Gradle
 * task requests load/resolve/signature verification without initialization. That
 * still exercises the original ClientLevel verifier hazard; the actual dedicated
 * runtime harness independently checks NeoForge payload/codec initialization.
 */
class PortablePacketDedicatedSideLoadingTest {
    private static final String PREFIX = "com.nstut.simplyspeakers.network.";
    private static final Set<String> PAYLOADS = Set.of(
            PREFIX + "OpenPortableSpeakerPacketS2C", PREFIX + "PortableSpeakerPositionPacketS2C",
            PREFIX + "PortableEmitterCodec");

    private static final class DedicatedSideLoader extends ClassLoader {
        final List<String> clientLoads = new ArrayList<>();
        private final List<Path> mainClasses;
        DedicatedSideLoader(List<Path> mainClasses) {
            super(PortablePacketDedicatedSideLoadingTest.class.getClassLoader());
            this.mainClasses = mainClasses;
        }
        private byte[] originalBytecode(String name) throws ClassNotFoundException {
            String relativePath = name.replace('.', '/') + ".class";
            for (Path directory : mainClasses) {
                Path file = directory.resolve(relativePath);
                if (!Files.isRegularFile(file)) continue;
                try { return Files.readAllBytes(file); }
                catch (IOException error) { throw new ClassNotFoundException("Cannot read original payload " + file, error); }
            }
            // Do not fall back to getResourceAsStream: NeoForge transforms those
            // bytes and injects a guardian accessible only to its own classloader.
            throw new ClassNotFoundException("Original compiled payload missing: " + name + " in " + mainClasses);
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.startsWith("net.minecraft.client.") || name.startsWith("com.nstut.simplyspeakers.client.")) {
                    clientLoads.add(name);
                    throw new ClassNotFoundException("Client class unavailable on dedicated server: " + name);
                }
                if (!PAYLOADS.contains(name)) return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    byte[] bytes = originalBytecode(name);
                    loaded = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }

    @Test void absentCompiledOutputCannotFallBackToTransformedResources(@org.junit.jupiter.api.io.TempDir Path directory) {
        var loader = new DedicatedSideLoader(List.of(directory));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName(PREFIX + "OpenPortableSpeakerPacketS2C", true, loader));
    }

    @Test void portablePayloadsVerifyWithoutClientClassesAndInitializeWhereSupported() throws Exception {
        List<Path> mainClasses = Arrays.stream(System.getProperty("simplyspeakers.testMainClasses", "")
                        .split(Pattern.quote(File.pathSeparator)))
                .filter(path -> !path.isBlank()).map(Path::of).toList();
        assertFalse(mainClasses.isEmpty(), "Gradle must supply the original compiled main classes directories");
        var loader = new DedicatedSideLoader(mainClasses);
        boolean verificationOnly = Boolean.getBoolean("simplyspeakers.testPayloadVerificationOnly");
        for (String name : List.of("OpenPortableSpeakerPacketS2C", "PortableSpeakerPositionPacketS2C")) {
            Class<?> payload = assertDoesNotThrow(() -> loader.loadClass(PREFIX + name, true));
            if (!verificationOnly)
                assertSame(payload, assertDoesNotThrow(() -> Class.forName(PREFIX + name, true, loader)));
            assertSame(loader, payload.getClassLoader());
            assertNotNull(payload.getDeclaredMethods());
            assertNotNull(payload.getDeclaredConstructors());
            // Reading a static field initializes the class. Never do that in the
            // NeoForge rejecting loader: its injected guardian requires the real
            // game loader. The dedicated runtime harness checks those real codecs.
            if (!verificationOnly) {
                for (var field : payload.getDeclaredFields()) {
                    if (field.getName().equals("TYPE") || field.getName().equals("STREAM_CODEC"))
                        assertNotNull(field.get(null));
                }
            }
        }
        assertTrue(loader.clientLoads.isEmpty(), "Payload loading touched client classes: " + loader.clientLoads);
    }
}
