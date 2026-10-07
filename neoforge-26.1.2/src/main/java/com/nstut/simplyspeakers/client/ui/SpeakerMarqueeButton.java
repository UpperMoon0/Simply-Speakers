package com.nstut.simplyspeakers.client.ui;

import com.nstut.openui.api.TextWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.function.Supplier;

/** Preserves button interaction while OpenUI scrolls overflowing names. */
public class SpeakerMarqueeButton extends SpeakerButtonWidget {
    private final TextWidget title;
    private final int rightInset;
    private Component[] reservedLabels;
    /** Reserve the longest choice, so changing a mode cannot outgrow its button. */
    public SpeakerMarqueeButton reserveLabels(Component... labels) {
        reservedLabels = labels.clone();
        return this;
    }
    @Override public int preferredWidth(Font font) {
        if (reservedLabels == null) return super.preferredWidth(font);
        int widest = 0;
        for (Component label : reservedLabels) widest = Math.max(widest, font.width(label));
        return widest + 6 + rightInset;
    }
    public SpeakerMarqueeButton(Supplier<Component> label, int rightInset) {
        super(Component.empty());
        this.title = new TextWidget(label).nowrap().marquee();
        this.rightInset = rightInset;
    }
    @Override public void render(GuiGraphicsExtractor graphics, Font font, int mx, int my, float delta) {
        super.render(graphics, font, mx, my, delta);
        title.theme(theme());
        int color = theme().colors().onSurface();
        if (!isEnabled()) color = (color & 0x00ffffff) | 0x66000000;
        title.color(color);
        title.layout(getX()+6, getY()+(getHeight()-font.lineHeight)/2,
                Math.max(0,getWidth()-6-rightInset), font.lineHeight);
        title.render(graphics,font,mx,my,delta);
    }
}
