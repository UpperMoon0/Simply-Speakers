package com.nstut.simplyspeakers.compat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native registration and wire handlers must verify with no client classes. */
public class LegacyDedicatedSideTest {
    @Test public void forgeWireAndSideProxyRemainDedicatedServerSafe() throws Exception {
        final List<String> clientLoads = new ArrayList<>();
        final String[] roots = System.getProperty("simplyspeakers.testMainClasses", "").split(java.util.regex.Pattern.quote(File.pathSeparator));
        assertTrue("Compiled main output required", roots.length > 0 && !roots[0].isEmpty());
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    if (name.startsWith("net.minecraft.client.") || name.startsWith("com.nstut.simplyspeakers.client.")) {
                        clientLoads.add(name); throw new ClassNotFoundException("Dedicated server excludes " + name);
                    }
                    if (!name.startsWith("com.nstut.simplyspeakers.compat.") && !name.equals("com.nstut.simplyspeakers.forge.CommonProxy"))
                        return super.loadClass(name, resolve);
                    Class<?> result = findLoadedClass(name);
                    if (result == null) {
                        for (String root : roots) {
                            Path path = Paths.get(root, name.replace('.', '/') + ".class");
                            if (!Files.isRegularFile(path)) continue;
                            try { byte[] bytes = Files.readAllBytes(path); result = defineClass(name,bytes,0,bytes.length); break; }
                            catch (java.io.IOException error) { throw new ClassNotFoundException(name,error); }
                        }
                        if (result == null) throw new ClassNotFoundException("Compiled owned class missing: " + name);
                    }
                    if (resolve) resolveClass(result);
                    return result;
                }
            }
        };
        for (String name : new String[]{"compat.LegacyChannel", "compat.LegacyChannel$Wire", "compat.LegacyChannel$ServerHandler", "compat.LegacyChannel$ClientHandler", "compat.LegacyNetwork", "forge.CommonProxy"}) {
            Class<?> type = Class.forName("com.nstut.simplyspeakers." + name,true,loader);
            assertSame(loader,type.getClassLoader());
            assertNotNull(type.getDeclaredConstructors()); assertNotNull(type.getDeclaredMethods());
        }
        assertTrue("Native adapters touched client classes: " + clientLoads, clientLoads.isEmpty());
    }
}
