package com.overzealouspelican.awsui.service;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.util.prefs.Preferences;

/**
 * Stores and applies app-level settings such as theme preference.
 */
public class SettingsService {

    private static final Preferences PREFS = Preferences.userNodeForPackage(SettingsService.class);
    private static final String THEME_KEY = "theme";
    private static final String DEFAULT_THEME = "FlatLaf IntelliJ";

    public static class ThemeOption {
        private final String displayName;
        private final String className;

        public ThemeOption(String displayName, String className) {
            this.displayName = displayName;
            this.className = className;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getClassName() {
            return className;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public ThemeOption[] getAvailableThemes() {
        return new ThemeOption[]{
            new ThemeOption("FlatLaf Light", FlatLightLaf.class.getName()),
            new ThemeOption("FlatLaf Dark", FlatDarkLaf.class.getName()),
            new ThemeOption("FlatLaf IntelliJ", FlatIntelliJLaf.class.getName()),
            new ThemeOption("FlatLaf Darcula", FlatDarculaLaf.class.getName())
        };
    }

    public String getSavedTheme() {
        return PREFS.get(THEME_KEY, DEFAULT_THEME);
    }

    public void saveTheme(String themeName) {
        PREFS.put(THEME_KEY, themeName);
    }

    public void resetTheme() {
        PREFS.remove(THEME_KEY);
    }

    public String getDefaultTheme() {
        return DEFAULT_THEME;
    }

    public void applyTheme(String themeClassName) throws Exception {
        UIManager.setLookAndFeel(themeClassName);
        for (java.awt.Window window : java.awt.Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
        }
    }

    public void loadAndApplyTheme() {
        String savedThemeName = getSavedTheme();
        String themeClassName = getThemeClassName(savedThemeName);

        if (themeClassName == null) {
            themeClassName = FlatIntelliJLaf.class.getName();
        }

        try {
            UIManager.setLookAndFeel(themeClassName);
        } catch (Exception ex) {
            try {
                UIManager.setLookAndFeel(new FlatLightLaf());
            } catch (Exception ignored) {
                // Nothing else to do here.
            }
        }
    }

    private String getThemeClassName(String displayName) {
        for (ThemeOption theme : getAvailableThemes()) {
            if (theme.getDisplayName().equals(displayName)) {
                return theme.getClassName();
            }
        }
        return null;
    }
}
