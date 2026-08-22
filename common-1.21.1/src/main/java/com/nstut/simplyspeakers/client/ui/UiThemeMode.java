package com.nstut.simplyspeakers.client.ui;

import com.nstut.openui.theme.Theme;

/**
 * Persisted UI theme selection. An enum (not a boolean) so future
 * {@code system}/{@code high_contrast} options can be added without a file-format migration.
 */
public enum UiThemeMode {
    DARK,
    LIGHT;

    public Theme toOpenUiTheme() {
        return this == LIGHT ? Theme.light() : Theme.dark();
    }

    public UiThemeMode next() {
        return this == DARK ? LIGHT : DARK;
    }

    public String configValue() {
        return this == LIGHT ? "light" : "dark";
    }

    public static UiThemeMode fromConfigValue(String value) {
        if ("light".equalsIgnoreCase(value)) return LIGHT;
        return DARK;
    }
}
