package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.audio.ZonePreviewMesh;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.*;

/** Hidden-window GPU fixture. Images are shader-fixture captures, not Minecraft screenshots. */
public final class ZonePreviewGlSmoke {
    private static final int WIDTH=960, HEIGHT=540;
    private static final FloatBuffer MATRIX=BufferUtils.createFloatBuffer(16);
    private static Path output;
    public static void main(String[] args) throws Exception {
        output=Path.of(args[0]); Files.createDirectories(output);
        GLFWErrorCallback callback=GLFWErrorCallback.createPrint(System.err).set();
        if(!GLFW.glfwInit()) throw new IllegalStateException("GLFW initialization failed; no graphics acceptance obtained");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(WIDTH,HEIGHT,"Simply Speakers hidden shader fixture",0,0);
        if(window==0) throw new IllegalStateException("Hidden OpenGL context unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            System.out.println("GPU="+GL11.glGetString(GL11.GL_RENDERER)+" VERSION="+GL11.glGetString(GL11.GL_VERSION));
            ZonePreviewRenderer renderer=new ZonePreviewRenderer();
            renderer.upload(ZonePreviewMesh.create(16,1,1,90,.9f,2));
            check(renderer.available(),"Shader compilation failed");
            scene(renderer,"small-outside",16,new Vector3f(25,16,25),new Vector3f(0,0,0),0,0,false);
            scene(renderer,"small-inside",16,new Vector3f(0,3,1),new Vector3f(0,0,-16),0,0,false);
            scene(renderer,"portable-pose-a",16,new Vector3f(25,16,25),new Vector3f(0,0,0),0,0,false);
            scene(renderer,"portable-pose-b",16,new Vector3f(25,16,25),new Vector3f(0,0,0),4,.8,false);
            scene(renderer,"depth-occlusion-fixture",16,new Vector3f(25,16,25),new Vector3f(0,0,0),0,0,true);
            renderer.upload(ZonePreviewMesh.create(512,1,1,90,.9f,2));
            scene(renderer,"large-outside",512,new Vector3f(850,500,850),new Vector3f(0,0,0),0,0,false);
            scene(renderer,"large-inside",512,new Vector3f(20,15,20),new Vector3f(0,0,-512),0,0,false);
            int vbo=field(renderer,"vbo"),vao=field(renderer,"vao"),program=field(renderer,"program");
            check(GL15.glIsBuffer(vbo)&&GL30.glIsVertexArray(vao)&&GL20.glIsProgram(program),"Expected retained GL resources");
            renderer.close(); renderer.close();
            check(!GL15.glIsBuffer(vbo)&&!GL30.glIsVertexArray(vao)&&!GL20.glIsProgram(program),"GPU resources survived close");
            renderer.upload(ZonePreviewMesh.create(16,1,0,90,1,2));
            renderer.reload();
            check(field(renderer,"vbo")==0&&field(renderer,"program")==0,"Reload retained resources");
            System.setProperty("simplyspeakers.previewHudOnly","true");
            renderer.upload(ZonePreviewMesh.create(16,1,0,90,1,2));
            check(!renderer.available()&&field(renderer,"vbo")==0,"Forced fallback allocated a GPU buffer");
            System.clearProperty("simplyspeakers.previewHudOnly");
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL reported an error");
            System.out.println("ZONE_SHADER_FIXTURE_PASS: resources, reload, fallback, state restoration and 7 GPU captures");
            System.out.println("NOT Minecraft integration or particle-baseline performance acceptance.");
        } finally {
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); callback.free();
        }
    }

    private static void scene(ZonePreviewRenderer renderer,String name,int range,Vector3f eye,Vector3f target,
                              double x,double rotation,boolean occluded) throws Exception {
        GL11.glViewport(0,0,WIDTH,HEIGHT); GL11.glClearColor(.035f,.055f,.075f,1);
        GL11.glDepthMask(true); GL11.glClearDepth(1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        if(occluded) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(0,0,WIDTH/2,HEIGHT);
            GL11.glClearColor(.08f,.1f,.12f,1); GL11.glClearDepth(.1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glClearDepth(1);
        }
        new Matrix4f().perspective((float)Math.toRadians(70),(float)WIDTH/HEIGHT,.05f,range*8f)
            .lookAt(eye,target,new Vector3f(0,1,0)).get(0,MATRIX);
        GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND); GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(true); GL11.glDepthFunc(GL11.GL_ALWAYS);
        GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ZERO,GL11.GL_ONE);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_REVERSE_SUBTRACT,GL14.GL_FUNC_SUBTRACT);
        renderer.draw(MATRIX,x,0,0,rotation);
        check(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&!GL11.glIsEnabled(GL11.GL_BLEND)&&GL11.glIsEnabled(GL11.GL_CULL_FACE),"Enable state leaked");
        check(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)&&GL11.glGetInteger(GL11.GL_DEPTH_FUNC)==GL11.GL_ALWAYS,"Depth state leaked");
        check(GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)==GL14.GL_FUNC_REVERSE_SUBTRACT,"Blend equation leaked");
        check(GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)==GL11.GL_ZERO,"Blend factors leaked");
        check(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==0&&GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)==0,"Program or VAO leaked");
        GL11.glFinish();
        ByteBuffer pixels=BufferUtils.createByteBuffer(WIDTH*HEIGHT*4);
        GL11.glReadPixels(0,0,WIDTH,HEIGHT,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);
        int bright=0;
        for(int py=0;py<HEIGHT;py++)for(int px=0;px<WIDTH;px++) {
            int index=((HEIGHT-1-py)*WIDTH+px)*4;
            int red=pixels.get(index)&255,green=pixels.get(index+1)&255,blue=pixels.get(index+2)&255;
            if(green>80) bright++;
            image.setRGB(px,py,(red<<16)|(green<<8)|blue);
        }
        check(bright>25,"No readable shader pixels in "+name);
        ImageIO.write(image,"png",output.resolve(name+".png").toFile());
        System.out.println(name+" pixels="+bright);
    }
    private static int field(Object instance,String name) throws Exception {
        var field=instance.getClass().getDeclaredField(name); field.setAccessible(true); return field.getInt(instance);
    }
    private static void check(boolean valid,String message) { if(!valid)throw new AssertionError(message); }
}
