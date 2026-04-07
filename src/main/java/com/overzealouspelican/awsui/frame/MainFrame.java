package com.overzealouspelican.awsui.frame;

import com.overzealouspelican.awsui.panel.SettingsEditorPanel;
import com.overzealouspelican.awsui.service.SettingsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import java.awt.*;

/**
 * Minimal desktop shell with an intentionally empty content area.
 */
public class MainFrame extends JFrame {

    private static final String VIEW_HOME = "home";
    private static final String VIEW_SETTINGS = "settings";

    private final SettingsService settingsService;
    private final CardLayout contentLayout;
    private final JPanel contentContainer;

    public MainFrame(SettingsService settingsService) {
        super("AWS UI");
        this.settingsService = settingsService;
        this.contentLayout = new CardLayout();
        this.contentContainer = new JPanel(contentLayout);
        initialize();
    }

    private void initialize() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        setJMenuBar(createMenuBar());

        add(createToolbar(), BorderLayout.NORTH);

        contentContainer.add(createEmptyCanvas(), VIEW_HOME);
        contentContainer.add(new SettingsEditorPanel(settingsService), VIEW_SETTINGS);
        add(contentContainer, BorderLayout.CENTER);
        showHomeView();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem closeItem = new JMenuItem("Close");
        closeItem.addActionListener(e -> dispose());
        fileMenu.add(closeItem);

        JMenu viewMenu = new JMenu("View");
        JMenuItem homeItem = new JMenuItem("Home");
        JMenuItem settingsItem = new JMenuItem("Settings");

        homeItem.addActionListener(e -> showHomeView());
        settingsItem.addActionListener(e -> showSettingsView());

        viewMenu.add(homeItem);
        viewMenu.add(settingsItem);

        menuBar.add(fileMenu);
        menuBar.add(viewMenu);

        return menuBar;
    }

    private JComponent createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UITheme.SPACING_SM, 6));
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, UITheme.SPACING_SM, 4, UITheme.SPACING_SM));
        toolbar.setBackground(UIManager.getColor("Panel.background"));
        toolbar.setPreferredSize(new Dimension(0, UITheme.TOOLBAR_HEIGHT));

        JLabel appLabel = new JLabel("AWS UI Shell");
        appLabel.setFont(appLabel.getFont().deriveFont(Font.BOLD, UITheme.FONT_SIZE_TITLE));

        toolbar.add(appLabel);

        return toolbar;
    }

    private JComponent createEmptyCanvas() {
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(UITheme.contentPadding());

        JPanel emptyArea = new JPanel();
        emptyArea.setOpaque(false);

        contentPanel.add(emptyArea, BorderLayout.CENTER);
        return contentPanel;
    }

    private void showHomeView() {
        contentLayout.show(contentContainer, VIEW_HOME);
    }

    private void showSettingsView() {
        contentLayout.show(contentContainer, VIEW_SETTINGS);
    }
}
