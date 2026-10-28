package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.audio.ZonePreviewMesh;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** OpenGL adapter for the 1.20/1.21 render paths. All methods run on the render thread. */
public final class ZonePreviewRenderer {
    private int program, vao, vbo, count;
    private int matrixLocation, originLocation, rotationLocation, viewportLocation, occludedLocation;
    private boolean failed;
    private final IntBuffer viewport = BufferUtils.createIntBuffer(4);

    public boolean available() { return !failed && !Boolean.getBoolean("simplyspeakers.previewHudOnly"); }

    public void upload(ZonePreviewMesh mesh) {
        close();
        if (!available()) return;
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            program = GL20.glCreateProgram();
            attach(GL20.GL_VERTEX_SHADER, "vert");
            attach(GL20.GL_FRAGMENT_SHADER, "frag");
            GL20.glBindAttribLocation(program, 0, "Start");
            GL20.glBindAttribLocation(program, 1, "End");
            GL20.glBindAttribLocation(program, 2, "Color");
            GL20.glBindAttribLocation(program, 3, "Corner");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IOException(GL20.glGetProgramInfoLog(program, 4096));
            matrixLocation = uniform("ViewProjection"); originLocation = uniform("Origin");
            rotationLocation = uniform("Rotation"); viewportLocation = uniform("Viewport");
            occludedLocation = uniform("Occluded");
            vao = GL30.glGenVertexArrays(); vbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            float[] vertices = mesh.copyVertices();
            FloatBuffer data = BufferUtils.createFloatBuffer(vertices.length);
            data.put(vertices).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
            int stride = ZonePreviewMesh.FLOATS_PER_VERTEX * 4;
            attribute(0, 3, stride, 0); attribute(1, 3, stride, 12);
            attribute(2, 3, stride, 24); attribute(3, 2, stride, 36);
            count = mesh.vertexCount();
        } catch (IOException | RuntimeException ex) {
            failed = true;
            System.err.println("[SimplySpeakers] Zone shader unavailable; using HUD preview: " + ex.getMessage());
            close();
        } finally {
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
        }
    }

    private void attach(int type, String suffix) throws IOException {
        int shader = GL20.glCreateShader(type);
        try (InputStream input = ZonePreviewRenderer.class.getResourceAsStream(
                "/assets/simplyspeakers/shaders/zone_preview." + suffix)) {
            if (input == null) throw new IOException("Missing zone shader " + suffix);
            GL20.glShaderSource(shader, new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
                throw new IOException(GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glAttachShader(program, shader);
        } finally {
            // The program retains attached shaders through linking; deletion releases them with the program.
            GL20.glDeleteShader(shader);
        }
    }

    private int uniform(String name) throws IOException {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0) throw new IOException("Missing zone uniform " + name);
        return location;
    }

    private static void attribute(int index, int size, int stride, long offset) {
        GL20.glEnableVertexAttribArray(index);
        GL20.glVertexAttribPointer(index, size, GL11.GL_FLOAT, false, stride, offset);
    }

    /** All positions are camera relative doubles until the final uniform conversion. */
    public void draw(FloatBuffer matrix, double x, double y, double z, double rotation) {
        if (program == 0 || count == 0 || !available()) return;
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int func = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int eqRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), eqAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        try {
            viewport.clear(); GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            if (viewport.get(2) <= 0 || viewport.get(3) <= 0) return;
            GL20.glUseProgram(program); GL30.glBindVertexArray(vao);
            GL20.glUniformMatrix4fv(matrixLocation, false, matrix);
            GL20.glUniform3f(originLocation, (float)x, (float)y, (float)z);
            GL20.glUniform2f(rotationLocation, (float)Math.cos(rotation), (float)Math.sin(rotation));
            GL20.glUniform2f(viewportLocation, viewport.get(2), viewport.get(3));
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthFunc(GL11.GL_GREATER); GL20.glUniform1f(occludedLocation, 1);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
            GL11.glDepthFunc(GL11.GL_LEQUAL); GL20.glUniform1f(occludedLocation, 0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
        } finally {
            GL20.glUseProgram(oldProgram); GL30.glBindVertexArray(oldVao);
            enabled(GL11.GL_DEPTH_TEST, depth); enabled(GL11.GL_BLEND, blend); enabled(GL11.GL_CULL_FACE, cull);
            GL11.glDepthMask(mask); GL11.glDepthFunc(func);
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            GL20.glBlendEquationSeparate(eqRgb, eqAlpha);
        }
    }

    private static void enabled(int capability, boolean enabled) {
        if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability);
    }

    public void close() {
        if (vbo != 0) GL15.glDeleteBuffers(vbo);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (program != 0) GL20.glDeleteProgram(program);
        vbo = 0; vao = 0; program = 0; count = 0;
    }

    /** Called on resource reload, permitting one new compile attempt for the next preview. */
    public void reload() { close(); failed = false; }
}
