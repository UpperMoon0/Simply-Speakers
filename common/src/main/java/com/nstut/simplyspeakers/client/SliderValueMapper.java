package com.nstut.simplyspeakers.client;

/** Version-neutral mapping for Minecraft's eight-pixel slider handle track. */
public final class SliderValueMapper {
    private SliderValueMapper() {
    }

    public static double normalizedFromMouse(double mouseX, int widgetX, int widgetWidth) {
        double trackWidth = widgetWidth - 8.0;
        if (trackWidth <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (mouseX - (widgetX + 4.0)) / trackWidth));
    }
}
