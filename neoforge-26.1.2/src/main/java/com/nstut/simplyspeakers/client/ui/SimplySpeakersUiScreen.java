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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Shared base for Simply Speakers OpenUI screens.
 *
 * <p>Owns the persisted theme signal, a consistent header theme toggle, and the
 * modal window chrome (dimmed backdrop plus centered surface shell) that every
 * migrated screen hosts its content in.</p>
 */
public abstract class SimplySpeakersUiScreen extends UiScreen {
    /** Extra horizontal room the shell adds around content (padding + border). */
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

    /**
     * Root composition for migrated screens: a dimmed backdrop with the content
     * hosted in a centered window shell that never exceeds the viewport.
     *
     * @param content          the panel column (already constrained via {@code maxWidth})
     * @param contentMaxWidth  the content's intended maximum width
     */
    protected UIComponent buildWindow(UIComponent content, int contentMaxWidth) {
        // No color overrides here: Panel resolves surface/border from the live
        // runtime theme on every frame, so toggling light/dark restyles the
        // shell immediately (frozen ints captured at build time kept the
        // previous theme's colors after a toggle, like Economy's per-frame
        // renderBaseShell avoids).
        Panel shell = new Panel()
                .elevated()
                .padding(12);
        shell.child(content);
        // Pin BOTH dimensions explicitly: centered stacks size children by
        // their intrinsic preferredWidth/preferredHeight chain, which ignores
        // requested sizes deeper in the tree (e.g. the audio list reports a
        // 100px preferred width) and made the window resize per tab.
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

    /** Full-viewport dimming layer behind the shell, kept theme-reactive. */
    protected UIComponent buildScrim() {
        return new UIComponent() {
            @Override
            public int preferredWidth(Font font) {
                return 0;
            }

            @Override
            public int preferredHeight(Font font) {
                return 0;
            }

            @Override
            public void render(GuiGraphicsExtractor g, Font font, int mx, int my, float pt) {
                g.fill(x, y, x + width, y + height, colors().backdrop());
            }
        };
    }
}
