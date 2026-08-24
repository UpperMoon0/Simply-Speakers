package com.nstut.simplyspeakers.client.ui;

import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.Panel;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.layout.Alignment;
import com.nstut.openui.minecraft.UiScreen;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import com.nstut.openui.theme.ColorScheme;
import com.nstut.openui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shared base for Simply Speakers OpenUI screens.
 *
 * <p>Owns the persisted theme signal and a consistent header theme toggle. Migrated
 * screens must extend this rather than vanilla {@code Screen}.</p>
 */
public abstract class SimplySpeakersUiScreen extends UiScreen {
    private static final int SHELL_HORIZONTAL_OVERHEAD = 24;
    protected final Signal<UiThemeMode> themeMode = Signals.of(SimplySpeakersUiPreferences.getThemeMode());

    protected SimplySpeakersUiScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        applyTheme();
    }

    /**
     * Compact header theme toggle. The label describes the action ("switch to light"),
     * so while in dark mode it reads "☀ Light" and vice versa.
     */
    protected ButtonWidget buildThemeToggle() {
        return Ui.button(
                        () -> Component.literal(themeMode.get() == UiThemeMode.DARK ? "☀ Light" : "☾ Dark"),
                        this::toggleTheme)
                .ghost().small();
    }

    protected void toggleTheme() {
        UiThemeMode next = themeMode.get().next();
        themeMode.set(next);
        SimplySpeakersUiPreferences.setThemeMode(next);
        applyTheme();
    }

    protected final void applyTheme() {
        if (uiRuntime() != null) {
            uiRuntime().theme(themeMode.get().toOpenUiTheme());
        }
    }

    protected final Theme currentTheme() {
        return uiRuntime() != null ? uiRuntime().theme() : themeMode.get().toOpenUiTheme();
    }

    protected final ColorScheme colors() {
        return currentTheme().colors();
    }

    protected UIComponent buildWindow(UIComponent content, int contentMaxWidth) {
        Panel shell = new Panel().elevated().padding(12);
        shell.child(content);
        int shellWidth = Math.min(contentMaxWidth + SHELL_HORIZONTAL_OVERHEAD, Math.max(76, width - 16));
        int shellHeight = Math.max(80, height - 16);
        shell.width(shellWidth);
        shell.height(shellHeight);
        shell.maxWidth(shellWidth);
        shell.maxHeight(shellHeight);
        return Ui.stack(
                buildScrim(),
                Ui.padding(8, Ui.stack(shell).align(Alignment.CENTER, Alignment.CENTER))
        );
    }

    protected UIComponent buildScrim() {
        return new UIComponent() {
            @Override public int preferredWidth(Font font) { return 0; }
            @Override public int preferredHeight(Font font) { return 0; }

            @Override
            public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
                g.fill(x, y, x + width, y + height, colors().backdrop());
            }
        };
    }
}
