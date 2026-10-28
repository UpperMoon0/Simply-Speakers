package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.audio.ZonePreviewMesh;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Native 26.x GPU pipeline. Never accesses OpenGL or assumes a graphics backend. */
public final class ZonePreviewRenderer {
    private static final RenderPipeline VISIBLE = pipeline("visible", CompareOp.LESS_THAN_OR_EQUAL);
    private static final RenderPipeline HIDDEN = pipeline("hidden", CompareOp.GREATER_THAN);
    private static final int VERTEX_BYTES = 28;
    private final ByteBuffer upload = ByteBuffer.allocateDirect(ZonePreviewMesh.MAX_SEGMENTS * 6 * VERTEX_BYTES).order(ByteOrder.nativeOrder());
    private final ByteBuffer uniforms = ByteBuffer.allocateDirect(112).order(ByteOrder.nativeOrder());
    private GpuBuffer vertices, visibleUniforms, hiddenUniforms;
    private int count;
    private boolean failed;
    private final boolean compatibilityFallback = hasShaderIntegration();

    private static RenderPipeline pipeline(String name, CompareOp depth) {
        return RenderPipeline.builder().withLocation("simplyspeakers:pipeline/zone_preview_" + name)
            .withVertexShader("simplyspeakers:core/zone_preview").withFragmentShader("simplyspeakers:core/zone_preview")
            .withUniform("ZonePreview", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES)
            .withCull(false).withDepthStencilState(new DepthStencilState(depth, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
    }

    public boolean available() { return !failed && !compatibilityFallback && !Boolean.getBoolean("simplyspeakers.previewHudOnly"); }

    private static boolean hasShaderIntegration() {
        for (String name : new String[]{"net.irisshaders.iris.Iris", "net.coderbot.iris.Iris",
                "net.optifine.shaders.Shaders", "shadersmod.client.Shaders"}) {
            try { Class.forName(name, false, ZonePreviewRenderer.class.getClassLoader()); return true; }
            catch (ClassNotFoundException absent) { /* Try the next integration. */ }
            catch (LinkageError incompatible) { return true; }
        }
        return false;
    }

    public void upload(ZonePreviewMesh mesh) {
        close();
        if (!available()) return;
        try {
            var device=RenderSystem.getDevice();
            if (!device.precompilePipeline(VISIBLE).isValid() || !device.precompilePipeline(HIDDEN).isValid())
                throw new IllegalStateException("Zone preview pipeline did not compile");
            float[] data=mesh.copyVertices();
            upload.clear();
            for (int offset=0; offset<data.length; offset+=ZonePreviewMesh.FLOATS_PER_VERTEX) {
                float end=data[offset+9];
                for(int axis=0;axis<3;axis++) upload.putFloat(data[offset+axis]*(1-end)+data[offset+3+axis]*end);
                upload.putFloat(data[offset+10]).putFloat(0);
                for(int color=0;color<3;color++) upload.put((byte)Math.round(data[offset+6+color]*255));
                upload.put((byte)255);
                double dx=data[offset+3]-data[offset],dy=data[offset+4]-data[offset+1],dz=data[offset+5]-data[offset+2];
                double length=Math.max(.000001,Math.sqrt(dx*dx+dy*dy+dz*dz));
                upload.put((byte)Math.round(dx/length*127)).put((byte)Math.round(dy/length*127)).put((byte)Math.round(dz/length*127)).put((byte)0);
            }
            upload.flip();
            vertices=device.createBuffer(() -> "Simply Speakers zone ribbons",GpuBuffer.USAGE_VERTEX,upload);
            visibleUniforms=device.createBuffer(() -> "Simply Speakers visible zone",GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_COPY_DST,112);
            hiddenUniforms=device.createBuffer(() -> "Simply Speakers hidden zone",GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_COPY_DST,112);
            count=mesh.vertexCount();
        } catch (RuntimeException error) { fail(error); }
    }

    public void draw(FloatBuffer matrix, double x, double y, double z, double rotation) {
        if(vertices==null || !available()) return;
        var target=Minecraft.getInstance().getMainRenderTarget();
        if(target.getDepthTextureView()==null || target.getColorTextureView()==null) return;
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        uniforms.clear();
        for(int i=0;i<16;i++) uniforms.putFloat(matrix.get(i));
        uniforms.putFloat((float)x).putFloat((float)y).putFloat((float)z).putFloat(0);
        uniforms.putFloat((float)Math.cos(rotation)).putFloat((float)Math.sin(rotation));
        uniforms.putFloat(target.width).putFloat(target.height);
        uniforms.putFloat(0).putFloat(0).putFloat(0).putFloat(0).flip();
        encoder.writeToBuffer(visibleUniforms.slice(),uniforms);
        uniforms.putFloat(96,1);
        encoder.writeToBuffer(hiddenUniforms.slice(),uniforms);
        try(var pass=encoder.createRenderPass(() -> "Simply Speakers zone preview",target.getColorTextureView(),OptionalInt.empty(),
                target.getDepthTextureView(),OptionalDouble.empty())) {
            pass.setVertexBuffer(0,vertices);
            pass.setPipeline(HIDDEN); pass.setUniform("ZonePreview",hiddenUniforms); pass.draw(0,count);
            pass.setPipeline(VISIBLE); pass.setUniform("ZonePreview",visibleUniforms); pass.draw(0,count);
        }
    }

    public void close() {
        if(vertices!=null) vertices.close();
        if(visibleUniforms!=null) visibleUniforms.close();
        if(hiddenUniforms!=null) hiddenUniforms.close();
        vertices=null; visibleUniforms=null; hiddenUniforms=null; count=0;
    }
    public void fail(RuntimeException error) {
        failed=true; close();
        System.err.println("[SimplySpeakers] Zone preview disabled; HUD remains available: "+error.getMessage());
    }
    public void reload() { close(); failed=false; }
}
