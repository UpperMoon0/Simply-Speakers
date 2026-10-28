package com.nstut.simplyspeakers.client;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.GameRenderer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the injection against this target's actual Minecraft bytecode, without a graphics context. */
class ZonePreviewProjectionTest {
    @Test void captureUsesTheProjectionUploadedByVanillaRatherThanALocalOrdinal() throws Exception {
        // Active Mixin correctly forbids direct loading of a registered mixin class.
        // Read its annotation and five-instruction identity capture directly instead.
        List<String> methods=new ArrayList<>(),instructions=new ArrayList<>();
        String[] targetHolder={null};
        AtomicInteger index=new AtomicInteger(-1),captureMethods=new AtomicInteger();
        try(InputStream input=getClass().getResourceAsStream("/com/nstut/mixin/ZonePreviewLevelMixin.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    if(!name.equals("simplyspeakers$captureProjection")) return null;
                    captureMethods.incrementAndGet();
                    assertEquals("(Lorg/joml/Matrix4f;)Lorg/joml/Matrix4f;",descriptor);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible) {
                            if(!descriptor.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArg;")) return null;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String name,Object value) {
                                    if(name.equals("index")) index.set((Integer)value);
                                }
                                @Override public AnnotationVisitor visitArray(String name) {
                                    if(!name.equals("method")) return null;
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String name,Object value) { methods.add((String)value); }
                                    };
                                }
                                @Override public AnnotationVisitor visitAnnotation(String name,String descriptor) {
                                    if(!name.equals("at")) return null;
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String name,Object value) {
                                            if(name.equals("target")) targetHolder[0]=(String)value;
                                        }
                                    };
                                }
                            };
                        }
                        @Override public void visitVarInsn(int opcode,int variable) { instructions.add(opcode+":"+variable); }
                        @Override public void visitFieldInsn(int opcode,String owner,String name,String descriptor) { instructions.add(opcode+":"+owner+"."+name+":"+descriptor); }
                        @Override public void visitInsn(int opcode) { instructions.add(Integer.toString(opcode)); }
                        @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean isInterface) { instructions.add("call:"+owner+"."+name); }
                        @Override public void visitJumpInsn(int opcode,Label label) { instructions.add("jump:"+opcode); }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        assertEquals(1,captureMethods.get());
        assertEquals(List.of("renderLevel"),methods);
        assertEquals(0,index.get());
        // this.projection = argument; return argument; no arithmetic or matrix calls.
        assertEquals(List.of("25:0","25:1","181:com/nstut/mixin/ZonePreviewLevelMixin.simplyspeakers$projection:Lorg/joml/Matrix4f;","25:1","176"),instructions);
        String target=targetHolder[0];
        assertEquals("Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;",target);
        AtomicInteger calls=new AtomicInteger();
        try(InputStream input=GameRenderer.class.getResourceAsStream("GameRenderer.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    if(!name.equals("renderLevel")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean isInterface) {
                            if(target.equals("L"+owner+";"+name+descriptor)) calls.incrementAndGet();
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        assertEquals(1,calls.get(),"Capture must select the single world projection upload in this Minecraft version");

    }
}
