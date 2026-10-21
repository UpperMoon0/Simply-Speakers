package com.nstut.simplyspeakers.forge;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
/** Suspend owned AL handles before vanilla destroys them; never patch game sources into the mod. */
public final class AudioTransformer implements IClassTransformer {
 public byte[] transform(String name,String transformed,byte[] bytes){
  if(bytes==null||!"net.minecraft.client.audio.SoundManager".equals(transformed))return bytes;
  ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
  final boolean[] patched={false,false};
  reader.accept(new ClassVisitor(Opcodes.ASM5,writer){
   public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions){
    MethodVisitor delegate=super.visitMethod(access,method,desc,signature,exceptions);
    if(("unloadSoundSystem".equals(method)||"func_148613_b".equals(method))&&"()V".equals(desc)){patched[0]=true;return new MethodVisitor(Opcodes.ASM5,delegate){public void visitCode(){super.visitCode();visitMethodInsn(Opcodes.INVOKESTATIC,"com/nstut/simplyspeakers/client/LegacyClient","contextDestroying","()V",false);}};}
    if((access&Opcodes.ACC_STATIC)!=0 && desc.endsWith(";Z)Z"))return new MethodVisitor(Opcodes.ASM5,delegate){
     public void visitFieldInsn(int opcode,String owner,String field,String signature){super.visitFieldInsn(opcode,owner,field,signature);
      if(opcode==Opcodes.PUTFIELD && ("loaded".equals(field)||"field_148617_f".equals(field)) && "Z".equals(signature)){
       patched[1]=true;visitVarInsn(Opcodes.ILOAD,1);visitMethodInsn(Opcodes.INVOKESTATIC,"com/nstut/simplyspeakers/client/LegacyClient","contextReady","(Z)V",false);
      }
     }
    };
    return delegate;
   }
  },0);
  if(!patched[0]||!patched[1])throw new IllegalStateException("Simply Speakers could not install the sound-context destruction guard");
  return writer.toByteArray();
 }
}
