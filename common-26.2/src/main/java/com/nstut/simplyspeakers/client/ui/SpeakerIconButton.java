package com.nstut.simplyspeakers.client.ui;
import com.nstut.openui.api.ButtonWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;

/** Consistent 64x64 texture icons fitted inside compact, accessible buttons. */
public final class SpeakerIconButton extends ButtonWidget {
    public enum Icon { PLUS, PLAY, PAUSE, STOP, PREVIOUS, NEXT, BACK, FORWARD, RESTART, REPEAT, SHUFFLE, MORE, JUMP, VOLUME, SUN, MOON, TRASH }
    private Supplier<String> textureName;
    private final java.util.Map<String, com.nstut.openui.controls.IconWidget> textures = new java.util.HashMap<>();
    /** Mode textures retain their 64x64 source while fitting this 24-unit button. */
    public SpeakerIconButton texture(Supplier<String> value) { textureName=value;return this; }
    private boolean primaryIcon;
    private boolean pointerPress;
    private Supplier<Boolean> enabledCondition;
    public SpeakerIconButton enabledWhen(Supplier<Boolean> value) {enabledCondition=value;return this;}
    @Override public boolean mouseClicked(double mx,double my,int button) { boolean handled=super.mouseClicked(mx,my,button);pointerPress=handled;if(handled)clearFocus();return handled; }
    @Override public boolean mouseReleased(double mx,double my,int button) { boolean handled=super.mouseReleased(mx,my,button);if(pointerPress && button==0){clearFocus();pointerPress=false;}return handled; }
    @Override public ButtonWidget primary() { primaryIcon=true; return super.primary(); }
    private final Supplier<Icon> icon;
    private final Supplier<Component> hint;
    public SpeakerIconButton(Icon icon, Component hint, Runnable action) { this(() -> icon, () -> hint, action); }
    public SpeakerIconButton(Supplier<Icon> icon, Supplier<Component> hint, Runnable action) {
        super(Component.empty()); this.icon=icon; this.hint=hint;
        onPress(action); ghost().small(); width(24); height(24); tooltip(hint.get());
    }
    @Override public int preferredWidth(Font font) { return 24; }
    @Override public int preferredHeight(Font font) { return 24; }
    @Override public void render(GuiGraphicsExtractor g, Font font, int mx, int my, float pt) {
        if(enabledCondition!=null)enabled(enabledCondition.get());
        // An opaque ghost fill keeps the focus outline from tinting the entire icon target.
        if(!primaryIcon) setColors(theme().colors().surfaceRaised(),theme().colors().surfaceVariant());
        tooltip(hint.get()); super.render(g,font,mx,my,pt);
        String name = textureName != null ? textureName.get() : switch (icon.get()) {
            case REPEAT -> "loop_all";
            case SHUFFLE -> "order_random";
            default -> icon.get().name().toLowerCase(java.util.Locale.ROOT);
        };
        var texture = textures.computeIfAbsent(name, value ->
                com.nstut.openui.controls.IconWidget.texture("simplyspeakers", "textures/gui/playlist/"+value+".png",64,64).size(24));
        texture.layoutTree(font,x,y,width,height);
        texture.render(g,font,mx,my,pt);
        if (!isEnabled()) g.fill(x,y,x+width,y+height,(theme().colors().surfaceRaised() & 0x00ffffff) | 0x99000000);
    }
}
