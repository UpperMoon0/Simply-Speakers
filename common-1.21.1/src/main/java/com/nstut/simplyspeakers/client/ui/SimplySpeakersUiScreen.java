package com.nstut.simplyspeakers.client.ui;

import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.minecraft.UiScreen;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import net.minecraft.network.chat.Component;

/**
 * Shared base for Simply Speakers OpenUI screens.
 *
 * <p>Owns the persisted theme signal and a consistent header theme toggle. Migrated
 * screens must extend this rather than vanilla {@code Screen}.</p>
 */
public abstract class SimplySpeakersUiScreen extends UiScreen {
    protected final Signal<UiThemeMode> themeMode = Signals.of(SimplySpeakersUiPreferences.getThemeMode());

    protected SimplySpeakersUiScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        uiRuntime().theme(themeMode.get().toOpenUiTheme());
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
        if (uiRuntime() != null) {
            uiRuntime().theme(next.toOpenUiTheme());
        }
    }
}
