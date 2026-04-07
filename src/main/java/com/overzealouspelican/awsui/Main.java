package com.overzealouspelican.awsui;

import com.formdev.flatlaf.FlatLightLaf;
import com.overzealouspelican.awsui.frame.MainFrame;
import com.overzealouspelican.awsui.service.SettingsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;

/**
 * Entry point for the empty desktop shell.
 */
public class Main {

    public static void main(String[] args) {
        SettingsService settingsService = new SettingsService();
        settingsService.loadAndApplyTheme();

        try {
            String lookAndFeelName = UIManager.getLookAndFeel().getClass().getName();
            if (lookAndFeelName.contains("Metal") || lookAndFeelName.contains("Nimbus")) {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
        } catch (Exception ex) {
            System.err.println("Failed to initialize FlatLaf fallback");
        }

        UITheme.applyGlobalDefaults();

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(settingsService);
            frame.setVisible(true);
        });
    }
}
