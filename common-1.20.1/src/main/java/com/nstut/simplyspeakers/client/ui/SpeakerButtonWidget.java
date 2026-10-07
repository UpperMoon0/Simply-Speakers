package com.nstut.simplyspeakers.client.ui;

import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.animation.Easing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;

/** Theme-aware button fills keep focus rings separate and primary labels readable on hover. */
public class SpeakerButtonWidget extends ButtonWidget {
    private boolean ghostFill, primaryFill;
    private java.util.function.BooleanSupplier enabledSupplier;
    public SpeakerButtonWidget(Component label) { super(label); }
    public SpeakerButtonWidget(Supplier<Component> label) { super(label); }
    public static SpeakerButtonWidget button(Component label, Runnable action) { return new SpeakerButtonWidget(label).onPress(action); }
    public static SpeakerButtonWidget button(Supplier<Component> label, Runnable action) { return new SpeakerButtonWidget(label).onPress(action); }
    @Override public SpeakerButtonWidget ghost() { ghostFill=true;primaryFill=false;super.ghost();return this; }
    @Override public SpeakerButtonWidget primary() { ghostFill=false;primaryFill=true;super.primary();return this; }
    @Override public SpeakerButtonWidget danger() { ghostFill=false;primaryFill=false;super.danger();return this; }
    @Override public SpeakerButtonWidget onPress(Runnable action) {super.onPress(action);return this;}
    @Override public SpeakerButtonWidget small() {super.small();return this;}
    public SpeakerButtonWidget enabledWhen(java.util.function.BooleanSupplier condition) {
        enabledSupplier=condition;enabled(condition.getAsBoolean());return this;
    }
    @Override public void render(GuiGraphics g, Font font,int mx,int my,float pt) {
        if(enabledSupplier!=null)enabled(enabledSupplier.getAsBoolean());
        if(ghostFill) setColors(theme().colors().surfaceRaised(),theme().colors().surfaceVariant());
        if(primaryFill) {
            int base=theme().colors().primaryDim(), hover=theme().colors().primary();
            double progress=Easing.EASE_OUT.apply(getHoverProgress()), luminance=0;
            for(int channel=0;channel<3;channel++) {
                int shift=channel*8;
                double value=(((base>>shift)&255)*(1-progress)+((hover>>shift)&255)*progress)/255.0;
                value=value<=0.04045 ? value/12.92 : Math.pow((value+0.055)/1.055,2.4);
                luminance+=value*(channel==0?0.0722:channel==1?0.7152:0.2126);
            }
            textColor(luminance>0.179 ? 0xff111111 : 0xffffffff);
        }
        super.render(g,font,mx,my,pt);
    }
}
