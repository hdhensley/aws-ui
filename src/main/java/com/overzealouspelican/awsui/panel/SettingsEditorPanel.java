package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.SettingsService;
import com.overzealouspelican.awsui.service.SettingsService.ThemeOption;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import java.awt.*;

/**
 * Settings editor inspired by the PCAC layout, focused on theme controls.
 */
public class SettingsEditorPanel extends JPanel {

    private static final String CATEGORY_APPEARANCE = "Appearance";
    private static final String CATEGORY_GENERAL = "General";
    private static final String CATEGORY_ADVANCED = "Advanced";

    private final SettingsService settingsService;
    private final CardLayout detailsCardLayout;
    private final JPanel detailsPanel;

    private JComboBox<ThemeOption> themeComboBox;
    private JButton applyButton;
    private JButton resetButton;
    private JList<String> categoryList;

    public SettingsEditorPanel(SettingsService settingsService) {
        this.settingsService = settingsService;
        this.detailsCardLayout = new CardLayout();
        this.detailsPanel = new JPanel(detailsCardLayout);
        initializePanel();
        loadSettings();
    }

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBackground(UITheme.panelBackground());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setBorder(null);
        splitPane.setDividerSize(4);
        splitPane.setResizeWeight(0);
        splitPane.setLeftComponent(createCategorySidebar());
        splitPane.setRightComponent(createDetailsContainer());
        splitPane.setDividerLocation(200);

        add(splitPane, BorderLayout.CENTER);

        categoryList.setSelectedIndex(0);
        detailsCardLayout.show(detailsPanel, CATEGORY_APPEARANCE);
    }

    private JComponent createCategorySidebar() {
        DefaultListModel<String> categories = new DefaultListModel<>();
        categories.addElement(CATEGORY_APPEARANCE);
        categories.addElement(CATEGORY_GENERAL);
        categories.addElement(CATEGORY_ADVANCED);

        categoryList = new JList<>(categories);
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setFont(categoryList.getFont().deriveFont(Font.PLAIN, UITheme.FONT_SIZE_MD));
        categoryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = categoryList.getSelectedValue();
                if (selected != null) {
                    detailsCardLayout.show(detailsPanel, selected);
                }
            }
        });

        JScrollPane sidebar = new JScrollPane(categoryList);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIManager.getColor("Component.borderColor")));
        sidebar.setPreferredSize(new Dimension(200, 0));
        return sidebar;
    }

    private JComponent createDetailsContainer() {
        detailsPanel.setBorder(UITheme.contentPadding());
        detailsPanel.setBackground(UITheme.panelBackground());
        detailsPanel.add(createAppearancePanel(), CATEGORY_APPEARANCE);
        detailsPanel.add(createPlaceholderPanel(CATEGORY_GENERAL), CATEGORY_GENERAL);
        detailsPanel.add(createPlaceholderPanel(CATEGORY_ADVANCED), CATEGORY_ADVANCED);

        JScrollPane scrollPane = new JScrollPane(detailsPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private JPanel createAppearancePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(UITheme.panelBackground());

        panel.add(createSectionLabel(CATEGORY_APPEARANCE));
        panel.add(Box.createVerticalStrut(UITheme.SPACING_SM));
        panel.add(createThemePanel());
        panel.add(Box.createVerticalStrut(UITheme.SPACING_LG));
        panel.add(createButtonPanel());
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private JPanel createPlaceholderPanel(String categoryName) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(UITheme.panelBackground());

        JLabel heading = createSectionLabel(categoryName);
        JLabel description = new JLabel("No settings are available here yet.");
        description.setForeground(UIManager.getColor("Label.disabledForeground"));

        panel.add(heading);
        panel.add(Box.createVerticalStrut(UITheme.SPACING_SM));
        panel.add(description);
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private JLabel createSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, UITheme.FONT_SIZE_MD));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel createThemePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        panel.setBackground(UITheme.panelBackground());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("Theme:");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        themeComboBox = new JComboBox<>(settingsService.getAvailableThemes());
        themeComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, UITheme.BUTTON_HEIGHT));
        themeComboBox.setPreferredSize(new Dimension(0, UITheme.BUTTON_HEIGHT));
        themeComboBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(label);
        panel.add(Box.createVerticalStrut(UITheme.SPACING_XS));
        panel.add(themeComboBox);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, UITheme.SPACING_SM, 0));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, UITheme.BUTTON_HEIGHT + 4));
        panel.setBackground(UITheme.panelBackground());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        resetButton = new JButton("Reset");
        resetButton.addActionListener(e -> resetToDefaults());

        applyButton = new JButton("Apply");
        UITheme.stylePrimaryButton(applyButton);
        applyButton.addActionListener(e -> saveSettings());

        panel.add(resetButton);
        panel.add(applyButton);

        return panel;
    }

    private void loadSettings() {
        String savedTheme = settingsService.getSavedTheme();
        for (int i = 0; i < themeComboBox.getItemCount(); i++) {
            if (themeComboBox.getItemAt(i).getDisplayName().equals(savedTheme)) {
                themeComboBox.setSelectedIndex(i);
                break;
            }
        }
    }

    private void saveSettings() {
        ThemeOption selectedTheme = (ThemeOption) themeComboBox.getSelectedItem();
        if (selectedTheme == null) {
            return;
        }

        try {
            settingsService.saveTheme(selectedTheme.getDisplayName());
            settingsService.applyTheme(selectedTheme.getClassName());
            UITheme.applyGlobalDefaults();

            JOptionPane.showMessageDialog(
                this,
                "Theme applied successfully.",
                "Settings",
                JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to apply theme: " + ex.getMessage(),
                "Theme Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void resetToDefaults() {
        int choice = JOptionPane.showConfirmDialog(
            this,
            "Reset theme to defaults?",
            "Reset Settings",
            JOptionPane.YES_NO_OPTION
        );

        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        settingsService.resetTheme();
        String defaultTheme = settingsService.getDefaultTheme();

        for (int i = 0; i < themeComboBox.getItemCount(); i++) {
            if (themeComboBox.getItemAt(i).getDisplayName().equals(defaultTheme)) {
                themeComboBox.setSelectedIndex(i);
                break;
            }
        }

        saveSettings();
    }
}
