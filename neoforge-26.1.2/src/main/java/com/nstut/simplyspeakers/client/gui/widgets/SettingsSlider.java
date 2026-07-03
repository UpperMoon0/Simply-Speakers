package com.nstut.simplyspeakers.client.gui.widgets;

import com.nstut.simplyspeakers.client.SliderValueMapper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
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

    /**
     * Minecraft 26.1 moved slider mouse handling behind a private normalized-value
     * setter. Handle it explicitly so the mapped callback is guaranteed to fire.
     */
    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        updateValueFromMouse(event.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        updateValueFromMouse(event.x());
    }

    private void updateValueFromMouse(double mouseX) {
        double normalized = SliderValueMapper.normalizedFromMouse(mouseX, this.getX(), this.width);
        if (Double.compare(normalized, this.value) == 0) {
            return;
        }

        this.value = normalized;
        updateMessage();
        applyValue();
    }

    public interface ValueFormatter {
        Component format(double value);
    }

    public interface OnValueChange {
        void onValueChange(double value);
    }
}
