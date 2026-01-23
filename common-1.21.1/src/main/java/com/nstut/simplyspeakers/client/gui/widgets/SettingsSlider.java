package com.nstut.simplyspeakers.client.gui.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class SettingsSlider extends AbstractSliderButton {
    private final double minValue;
    private final double maxValue;
    private final ValueFormatter formatter;
    private final OnValueChange onValueChange;

    public SettingsSlider(int x, int y, int width, int height, Component message, double value, double minValue, double maxValue, ValueFormatter formatter, OnValueChange onValueChange) {
        super(x, y, width, height, message, 0.0);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.formatter = formatter;
        this.onValueChange = onValueChange;
        this.setValue(value);
    }

    @Override
    protected void updateMessage() {
        this.setMessage(formatter.format(getValue()));
    }

    @Override
    protected void applyValue() {
        onValueChange.onValueChange(getValue());
    }

    public double getValue() {
        return Mth.lerp(this.value, this.minValue, this.maxValue);
    }

    public void setValue(double value) {
        this.value = Mth.clamp((value - this.minValue) / (this.maxValue - this.minValue), 0.0, 1.0);
        updateMessage();
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
    }

    public interface ValueFormatter {
        Component format(double value);
    }

    public interface OnValueChange {
        void onValueChange(double value);
    }
}