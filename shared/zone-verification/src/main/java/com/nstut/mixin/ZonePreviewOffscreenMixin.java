package com.nstut.mixin;

@org.spongepowered.asm.mixin.Mixin(com.mojang.blaze3d.platform.Window.class)
public abstract class ZonePreviewOffscreenMixin {
    @org.spongepowered.asm.mixin.injection.Redirect(method="<init>",at=@org.spongepowered.asm.mixin.injection.At(
        value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"),remap=false)
    private long simplyspeakers$hiddenFixture(int width,int height,CharSequence title,long monitor,long share) {
        if(Boolean.getBoolean("simplyspeakers.zoneShaderAcceptance")) org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_VISIBLE,org.lwjgl.glfw.GLFW.GLFW_FALSE);
        return org.lwjgl.glfw.GLFW.glfwCreateWindow(width,height,title,monitor,share);
    }
}
