package com.nstut.simplyspeakers.forge;
import java.io.*;
import org.junit.Test;
import org.objectweb.asm.*;
import static org.junit.Assert.*;
public class AudioTransformerTest {
    private static final String NAME = "net.minecraft.client.audio.SoundManager";
    private static byte[] fixture(boolean srg, boolean readiness) {
        String owner = NAME.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, srg ? "field_148617_f" : "loaded", "Z", null, null).visitEnd();
        MethodVisitor unload = writer.visitMethod(Opcodes.ACC_PUBLIC, srg ? "func_148613_b" : "unloadSoundSystem", "()V", null, null);
        unload.visitCode(); unload.visitInsn(Opcodes.RETURN); unload.visitMaxs(0, 1); unload.visitEnd();
        if (readiness) {
            MethodVisitor setter = writer.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "access$002", "(L" + owner + ";Z)Z", null, null);
            setter.visitCode(); setter.visitVarInsn(Opcodes.ALOAD, 0); setter.visitVarInsn(Opcodes.ILOAD, 1);
            setter.visitInsn(Opcodes.DUP_X1); setter.visitFieldInsn(Opcodes.PUTFIELD, owner, srg ? "field_148617_f" : "loaded", "Z");
            setter.visitInsn(Opcodes.IRETURN); setter.visitMaxs(3, 2); setter.visitEnd();
        }
        writer.visitEnd(); return writer.toByteArray();
    }
    private static void assertGuards(byte[] transformed) {
        final int[] hooks = {0,0};
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM5) {
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    boolean loadedWritten;
                    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        if (opcode == Opcodes.PUTFIELD && (name.equals("loaded") || name.equals("field_148617_f"))) loadedWritten = true;
                    }
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        if (!owner.equals("com/nstut/simplyspeakers/client/LegacyClient")) return;
                        if (name.equals("contextDestroying")) hooks[0]++;
                        if (name.equals("contextReady")) { assertTrue("Readiness hook must follow successful loaded write", loadedWritten); hooks[1]++; }
                    }
                };
            }
        }, 0);
        assertArrayEquals(new int[]{1,1}, hooks);
    }
    @Test public void patchesMappedAndSrgNames() {
        for (boolean srg : new boolean[]{false,true}) assertGuards(new AudioTransformer().transform(NAME,NAME,fixture(srg,true)));
    }
    @Test public void rejectsUnsupportedSoundManagerInsteadOfUnsafePlayback() {
        try { new AudioTransformer().transform(NAME,NAME,fixture(false,false)); fail(); }
        catch (IllegalStateException expected) { }
    }
    @Test public void unrelatedClassesAndNullAreUntouched() {
        byte[] bytes = {1,2,3};
        assertSame(bytes, new AudioTransformer().transform("other","other",bytes));
        assertNull(new AudioTransformer().transform(NAME,NAME,null));
    }
    @Test public void patchesGenuineMinecraftSoundManagerFromCompileClasspath() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(NAME.replace('.', '/') + ".class")) {
            assertNotNull("Native Minecraft compile artifact must be present", input);
            ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer,0,count);
            assertGuards(new AudioTransformer().transform(NAME,NAME,output.toByteArray()));
        }
    }
}
