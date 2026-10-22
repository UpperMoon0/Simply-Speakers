package com.nstut.simplyspeakers.testing;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
/** Shared executable dedicated-server boundary contract for both legacy loaders. */
public final class PacketClassIsolation {
    private PacketClassIsolation() {}
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    public static void verify(String outputs,Object server) throws Exception {
        List<Path> roots=new ArrayList<>();
        for(String value:outputs.split(java.util.regex.Pattern.quote(File.pathSeparator)))
            if(!value.isEmpty())roots.add(Paths.get(value));
        check(!roots.isEmpty(),"Compiled main outputs required");
        List<String> blocked=new ArrayList<>();
        ClassLoader loader=new ClassLoader(PacketClassIsolation.class.getClassLoader()) {
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
        int count=0;
        for(Path root:roots) {
            Path folder=root.resolve("com/nstut/simplyspeakers/network");if(!Files.isDirectory(folder))continue;
            try(java.nio.file.DirectoryStream<Path> files=Files.newDirectoryStream(folder,"*PacketS2C.class")) {
                for(Path path:files) {
                    String name="com.nstut.simplyspeakers.network."+path.getFileName().toString().replace(".class","");
                    Class<?> type=Class.forName(name,true,loader);
                    check(loader==type.getClassLoader(),"Packet was not isolated");type.getDeclaredMethods();
                    type.getDeclaredConstructors();type.getDeclaredFields();
                    type.getMethod("handle",type,Supplier.class).invoke(null,null,(Supplier<Object>)()->server);count++;
                }
            }
        }
        check(count>=11,"Must exercise all current native S2C packet classes");
        check(blocked.isEmpty(),"Packet initialization touched client code: "+blocked);
    }
}
