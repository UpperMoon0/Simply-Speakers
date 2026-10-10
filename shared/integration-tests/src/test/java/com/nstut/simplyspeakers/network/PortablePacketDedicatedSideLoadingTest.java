package com.nstut.simplyspeakers.network;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies real payload bytecode without any client classes visible to its loader. */
class PortablePacketDedicatedSideLoadingTest {
    private static final String PREFIX = "com.nstut.simplyspeakers.network.";
    private static final Set<String> PAYLOADS = Set.of(
            PREFIX + "OpenPortableSpeakerPacketS2C", PREFIX + "PortableSpeakerPositionPacketS2C",
            PREFIX + "PortableEmitterCodec");

    private static final class DedicatedSideLoader extends ClassLoader {
        final List<String> clientLoads = new ArrayList<>();
        DedicatedSideLoader() { super(PortablePacketDedicatedSideLoadingTest.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.startsWith("net.minecraft.client.") || name.startsWith("com.nstut.simplyspeakers.client.")) {
                    clientLoads.add(name);
                    throw new ClassNotFoundException("Client class unavailable on dedicated server: " + name);
                }
                if (!PAYLOADS.contains(name)) return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(name);
                        byte[] bytes = input.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException error) { throw new ClassNotFoundException(name, error); }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }

    @Test void portablePayloadsAndModernCodecsInitializeWithoutClientClasses() throws Exception {
        var loader = new DedicatedSideLoader();
        for (String name : List.of("OpenPortableSpeakerPacketS2C", "PortableSpeakerPositionPacketS2C")) {
            Class<?> payload = assertDoesNotThrow(() -> Class.forName(PREFIX + name, true, loader));
            assertSame(loader, payload.getClassLoader());
            assertNotNull(payload.getDeclaredMethods());
            assertNotNull(payload.getDeclaredConstructors());
            // Modern payloads initialize their real method-reference codecs. Legacy
            // payloads have constructors/encode methods instead of these fields.
            for (var field : payload.getDeclaredFields()) {
                if (field.getName().equals("TYPE") || field.getName().equals("STREAM_CODEC"))
                    assertNotNull(field.get(null));
            }
        }
        assertTrue(loader.clientLoads.isEmpty(), "Payload loading touched client classes: " + loader.clientLoads);
    }
}
