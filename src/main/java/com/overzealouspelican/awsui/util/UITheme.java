package com.overzealouspelican.awsui.util;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Centralized theme constants and utility styling helpers.
 */
public final class UITheme {

    private UITheme() {
    }

    public static final int SPACING_XS = 4;
    public static final int SPACING_SM = 8;
    public static final int SPACING_MD = 12;
    public static final int SPACING_LG = 16;

    public static final int ARC = 8;
    public static final int ARC_LARGE = 12;

    public static final float FONT_SIZE_SM = 11f;
    public static final float FONT_SIZE_MD = 12f;
    public static final float FONT_SIZE_TITLE = 16f;

    public static final int BUTTON_HEIGHT = 32;
    public static final int TOOLBAR_HEIGHT = 44;

    public static final Color ACCENT = new Color(47, 128, 237);
    public static final Color ACCENT_FOREGROUND = Color.WHITE;

    private static final Color FALLBACK_DARK_PANEL = new Color(43, 46, 52);

    /**
     * Apply FlatLaf defaults for rounded corners and a modern baseline style.
     */
    public static void applyGlobalDefaults() {
        Color basePanel = panelBackground();
        Color surface = surfaceBackground();

        if (isDarkTheme()) {
            // Deepen baseline surfaces so dark themes are visually darker than neutral gray.
            UIManager.put("Panel.background", darken(basePanel, 0.15f));
            UIManager.put("Viewport.background", darken(basePanel, 0.12f));
            UIManager.put("Table.background", darken(basePanel, 0.1f));
            UIManager.put("TextArea.background", darken(basePanel, 0.1f));
            UIManager.put("TabbedPane.background", darken(basePanel, 0.12f));
        }

        UIManager.put("Button.arc", ARC);
        UIManager.put("Component.arc", ARC);
        UIManager.put("TextComponent.arc", ARC);
        UIManager.put("CheckBox.arc", ARC);
        UIManager.put("ProgressBar.arc", ARC);
        UIManager.put("ScrollBar.thumbArc", ARC);
        UIManager.put("ScrollBar.trackArc", ARC);

        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.focusColor", ACCENT);

        UIManager.put("Button.margin", new Insets(4, 14, 4, 14));

        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.showButtons", false);

        UIManager.put("SplitPane.dividerSize", 4);
        UIManager.put("SplitPaneDivider.gripDotCount", 0);

        UIManager.put("TabbedPane.selectedBackground", surface);
        UIManager.put("TabbedPane.tabHeight", 36);
        UIManager.put("TabbedPane.tabSelectionArc", ARC);
        UIManager.put("TabbedPane.cardTabSelectionHeight", 3);
        UIManager.put("TabbedPane.tabInsets", new Insets(4, 16, 4, 16));

        UIManager.put("MenuBar.borderColor", UIManager.getColor("Component.borderColor"));
        UIManager.put("PopupMenu.arc", ARC);
        UIManager.put("List.selectionArc", ARC);
        UIManager.put("Table.selectionArc", ARC);
        UIManager.put("ToolTip.background", surface);
        UIManager.put("ToolTip.arc", ARC);
        UIManager.put("ComboBox.padding", new Insets(4, 8, 4, 8));
    }

    public static Color panelBackground() {
        Color panel = UIManager.getColor("Panel.background");
        if (panel != null) {
            return panel;
        }
        return isDarkTheme() ? FALLBACK_DARK_PANEL : UIManager.getColor("control");
    }

    public static Color surfaceBackground() {
        Color panel = panelBackground();
        return isDarkTheme() ? darken(panel, 0.1f) : panel;
    }

    public static Color hoverBackground(Color base) {
        if (base == null) {
            base = panelBackground();
        }
        return isDarkTheme() ? base.brighter() : base.darker();
    }

    private static boolean isDarkTheme() {
        Object dark = UIManager.get("laf.dark");
        return dark instanceof Boolean && (Boolean) dark;
    }

    private static Color darken(Color color, float factor) {
        factor = Math.max(0f, Math.min(1f, factor));
        int r = Math.max(0, Math.round(color.getRed() * (1f - factor)));
        int g = Math.max(0, Math.round(color.getGreen() * (1f - factor)));
        int b = Math.max(0, Math.round(color.getBlue() * (1f - factor)));
        return new Color(r, g, b, color.getAlpha());
    }

    public static Border contentPadding() {
        return BorderFactory.createEmptyBorder(SPACING_LG, SPACING_LG, SPACING_LG, SPACING_LG);
    }

    public static void stylePrimaryButton(JButton button) {
        button.setBackground(ACCENT);
        button.setForeground(ACCENT_FOREGROUND);
        button.setFocusPainted(false);
        button.putClientProperty("JButton.buttonType", "roundRect");
    }
}
