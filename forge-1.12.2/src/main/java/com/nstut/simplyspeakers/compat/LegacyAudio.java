package com.nstut.simplyspeakers.compat;
/** LWJGL2 uses native IntBuffers rather than LWJGL3's primitive-array overloads. */
public final class LegacyAudio {
 private LegacyAudio() {}
 public static void genBuffers(int[] ids){java.nio.IntBuffer values=org.lwjgl.BufferUtils.createIntBuffer(ids.length);org.lwjgl.openal.AL10.alGenBuffers(values);values.get(ids);}
 public static void deleteBuffers(int[] ids){java.nio.IntBuffer values=org.lwjgl.BufferUtils.createIntBuffer(ids.length);values.put(ids).flip();org.lwjgl.openal.AL10.alDeleteBuffers(values);}
 public static void unqueueBuffers(int source,int[] ids){java.nio.IntBuffer values=org.lwjgl.BufferUtils.createIntBuffer(ids.length);org.lwjgl.openal.AL10.alSourceUnqueueBuffers(source,values);values.get(ids);}
}
