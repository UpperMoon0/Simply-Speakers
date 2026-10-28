package com.nstut.mixin;

@org.spongepowered.asm.mixin.Mixin(com.mojang.blaze3d.platform.Window.class)
public abstract class ZonePreviewOffscreenMixin {
    // Do not redirect window creation: Sodium owns that call for driver workarounds.
    @org.spongepowered.asm.mixin.injection.Redirect(method="<init>",at=@org.spongepowered.asm.mixin.injection.At(
        value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V"),remap=false)
    private void simplyspeakers$hiddenFixture() {
        org.lwjgl.glfw.GLFW.glfwDefaultWindowHints();
        if(Boolean.getBoolean("simplyspeakers.zoneShaderAcceptance")) org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_VISIBLE,org.lwjgl.glfw.GLFW.GLFW_FALSE);
    }
}
