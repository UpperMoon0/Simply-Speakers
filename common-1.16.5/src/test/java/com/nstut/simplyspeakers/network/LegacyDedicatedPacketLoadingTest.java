package com.nstut.simplyspeakers.network;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import me.shedaniel.architectury.networking.NetworkManager;
import me.shedaniel.architectury.utils.Env;
import java.util.function.Supplier;
/** Exercise every production S2C packet's initialization with client code unavailable. */
class LegacyDedicatedPacketLoadingTest {
    @Test void allServerRegisteredPacketClassesInitializeWithoutClientClasses() throws Exception {
        List<Path> roots=new ArrayList<>();
        for(String value:System.getProperty("simplyspeakers.testMainClasses","").split(java.util.regex.Pattern.quote(File.pathSeparator)))
            if(!value.isEmpty())roots.add(Paths.get(value));
        assertFalse(roots.isEmpty());
        List<String> blocked=new ArrayList<>();
        ClassLoader loader=new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                synchronized(getClassLoadingLock(name)) {
                    if(name.startsWith("net.minecraft.client.") || name.startsWith("com.nstut.simplyspeakers.client.")) {
                        blocked.add(name);throw new ClassNotFoundException("Client-only code is absent: "+name);
                    }
                    if(!name.startsWith("com.nstut.simplyspeakers.network."))return super.loadClass(name,resolve);
                    Class<?> result=findLoadedClass(name);
                    if(result==null)for(Path root:roots) {
                        Path file=root.resolve(name.replace('.','/')+".class");
                        if(!Files.isRegularFile(file))continue;
                        try {byte[] data=Files.readAllBytes(file);result=defineClass(name,data,0,data.length);break;}
                        catch(java.io.IOException error) {throw new ClassNotFoundException(name,error);}
                    }
                    if(result==null)throw new ClassNotFoundException(name);
                    if(resolve)resolveClass(result);return result;
                }
            }
        };
        NetworkManager.PacketContext server=mock(NetworkManager.PacketContext.class);
        when(server.getEnvironment()).thenReturn(Env.SERVER);
        int count=0;
        for(Path root:roots) {
            Path folder=root.resolve("com/nstut/simplyspeakers/network");if(!Files.isDirectory(folder))continue;
            try(java.nio.file.DirectoryStream<Path> files=Files.newDirectoryStream(folder,"*PacketS2C.class")) {
                for(Path path:files) {
                    String name="com.nstut.simplyspeakers.network."+path.getFileName().toString().replace(".class","");
                    Class<?> type=Class.forName(name,true,loader);
                    assertSame(loader,type.getClassLoader());assertNotNull(type.getDeclaredMethods());
                    assertNotNull(type.getDeclaredConstructors());assertNotNull(type.getDeclaredFields());
                    type.getMethod("handle",type,Supplier.class).invoke(null,null,(Supplier<NetworkManager.PacketContext>)()->server);count++;
                }
            }
        }
        assertTrue(count>=11,"Must exercise all current native S2C packet classes");
        assertTrue(blocked.isEmpty(),"Packet initialization touched client code: "+blocked);
    }
}
