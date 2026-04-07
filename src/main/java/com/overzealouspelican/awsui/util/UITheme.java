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

    /**
     * Apply FlatLaf defaults for rounded corners and a modern baseline style.
     */
    public static void applyGlobalDefaults() {
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

        UIManager.put("TabbedPane.selectedBackground", UIManager.getColor("Panel.background"));
        UIManager.put("TabbedPane.tabHeight", 36);
        UIManager.put("TabbedPane.tabSelectionArc", ARC);
        UIManager.put("TabbedPane.cardTabSelectionHeight", 3);
        UIManager.put("TabbedPane.tabInsets", new Insets(4, 16, 4, 16));

        UIManager.put("MenuBar.borderColor", UIManager.getColor("Component.borderColor"));
        UIManager.put("PopupMenu.arc", ARC);
        UIManager.put("List.selectionArc", ARC);
        UIManager.put("Table.selectionArc", ARC);
        UIManager.put("ToolTip.background", UIManager.getColor("Panel.background"));
        UIManager.put("ToolTip.arc", ARC);
        UIManager.put("ComboBox.padding", new Insets(4, 8, 4, 8));
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
