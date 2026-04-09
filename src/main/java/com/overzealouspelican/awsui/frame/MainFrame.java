package com.overzealouspelican.awsui.frame;

import com.overzealouspelican.awsui.panel.CodePipelinePanel;
import com.overzealouspelican.awsui.panel.EcsPanel;
import com.overzealouspelican.awsui.panel.LogsPanel;
import com.overzealouspelican.awsui.panel.SettingsEditorPanel;
import com.overzealouspelican.awsui.service.AwsProfileService;
import com.overzealouspelican.awsui.service.CloudWatchLogsService;
import com.overzealouspelican.awsui.service.CodePipelineService;
import com.overzealouspelican.awsui.service.EcsService;
import com.overzealouspelican.awsui.service.SettingsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Minimal desktop shell with an intentionally empty content area.
 */
public class MainFrame extends JFrame {

    private static final String VIEW_HOME = "home";
    private static final String VIEW_SETTINGS = "settings";
    private static final String VIEW_PIPELINES = "pipelines";
    private static final String VIEW_LOGS = "logs";
    private static final String VIEW_ECS = "ecs";

    private final SettingsService settingsService;
    private final AwsProfileService awsProfileService;
    private final CardLayout contentLayout;
    private final JPanel contentContainer;
    private CodePipelinePanel pipelinePanel;
    private LogsPanel logsPanel;
    private EcsPanel ecsPanel;

    public MainFrame(SettingsService settingsService) {
        super("AWS UI");
        this.settingsService = settingsService;
        this.awsProfileService = new AwsProfileService();
        this.contentLayout = new CardLayout();
        this.contentContainer = new JPanel(contentLayout);
        initialize();
    }

    private void initialize() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLayout(new BorderLayout());
        setJMenuBar(createMenuBar());

        add(createToolbar(), BorderLayout.NORTH);

        pipelinePanel = new CodePipelinePanel(new CodePipelineService(), settingsService);
        ecsPanel = new EcsPanel(new EcsService(), settingsService);
        logsPanel = new LogsPanel(new CloudWatchLogsService(), settingsService);
        contentContainer.add(createEmptyCanvas(), VIEW_HOME);
        contentContainer.add(new SettingsEditorPanel(settingsService), VIEW_SETTINGS);
        contentContainer.add(pipelinePanel, VIEW_PIPELINES);
        contentContainer.add(ecsPanel, VIEW_ECS);
        contentContainer.add(logsPanel, VIEW_LOGS);
        add(contentContainer, BorderLayout.CENTER);
        showHomeView();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        int menuShortcutKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        JMenu fileMenu = new JMenu("File");
        JMenuItem settingsItem = new JMenuItem("Settings");
        JMenuItem closeItem = new JMenuItem("Close");

        settingsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, menuShortcutKeyMask));
        settingsItem.addActionListener(e -> showSettingsView());

        closeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, menuShortcutKeyMask));
        closeItem.addActionListener(e -> dispose());

        fileMenu.add(settingsItem);
        fileMenu.addSeparator();
        fileMenu.add(closeItem);

        JMenu viewMenu = new JMenu("View");
        JMenuItem homeItem = new JMenuItem("Home");
        JMenuItem pipelinesItem = new JMenuItem("Pipelines");
        JMenuItem ecsItem = new JMenuItem("ECS");
        JMenuItem logsItem = new JMenuItem("Logs");

        homeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, menuShortcutKeyMask));
        pipelinesItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_2, menuShortcutKeyMask));
        ecsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_3, menuShortcutKeyMask));
        logsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_4, menuShortcutKeyMask));

        homeItem.addActionListener(e -> showHomeView());
        pipelinesItem.addActionListener(e -> showPipelinesView());
        ecsItem.addActionListener(e -> showEcsView());
        logsItem.addActionListener(e -> showLogsView());

        viewMenu.add(homeItem);
        viewMenu.add(pipelinesItem);
        viewMenu.add(ecsItem);
        viewMenu.add(logsItem);

        menuBar.add(fileMenu);
        menuBar.add(viewMenu);

        return menuBar;
    }

    private JComponent createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UITheme.SPACING_SM, 6));
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, UITheme.SPACING_SM, 4, UITheme.SPACING_SM));
        toolbar.setBackground(UITheme.surfaceBackground());
        toolbar.setPreferredSize(new Dimension(0, UITheme.TOOLBAR_HEIGHT));

        JLabel appLabel = new JLabel("AWS UI Shell");
        appLabel.setFont(appLabel.getFont().deriveFont(Font.BOLD, UITheme.FONT_SIZE_TITLE));
        appLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        appLabel.setToolTipText("Go to Home");
        appLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showHomeView();
            }
        });

        JLabel profileLabel = new JLabel("AWS Profile:");
        profileLabel.setFont(profileLabel.getFont().deriveFont(Font.PLAIN, UITheme.FONT_SIZE_SM));

        JComboBox<String> profileComboBox = createAwsProfileComboBox();
        profileComboBox.setPreferredSize(new Dimension(220, UITheme.BUTTON_HEIGHT));

        toolbar.add(appLabel);
        toolbar.add(Box.createHorizontalStrut(UITheme.SPACING_LG));
        toolbar.add(profileLabel);
        toolbar.add(profileComboBox);

        return toolbar;
    }

    private JComponent createEmptyCanvas() {
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(UITheme.contentPadding());
        contentPanel.setBackground(UITheme.panelBackground());

        JPanel cardsPanel = new JPanel(new GridLayout(2, 2, UITheme.SPACING_LG, UITheme.SPACING_LG));
        cardsPanel.setOpaque(false);
        cardsPanel.add(createNavigationCard(
            "Pipelines",
            "Monitor and manage AWS CodePipelines",
            this::showPipelinesView
        ));
        cardsPanel.add(createNavigationCard(
            "ECS",
            "View clusters, services, and force deployments",
            this::showEcsView
        ));
        cardsPanel.add(createNavigationCard(
            "Logs",
            "Search CloudWatch log groups and streams",
            this::showLogsView
        ));
        cardsPanel.add(createNavigationCard(
            "Settings",
            "Manage app appearance and preferences",
            this::showSettingsView
        ));

        JPanel centered = new JPanel(new GridBagLayout());
        centered.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(UITheme.SPACING_LG, UITheme.SPACING_LG, UITheme.SPACING_LG, UITheme.SPACING_LG);
        centered.add(cardsPanel, gbc);

        contentPanel.add(centered, BorderLayout.CENTER);
        return contentPanel;
    }

    private JComponent createNavigationCard(String title, String description, Runnable onClick) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(UITheme.surfaceBackground());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
            BorderFactory.createEmptyBorder(UITheme.SPACING_LG, UITheme.SPACING_LG, UITheme.SPACING_LG, UITheme.SPACING_LG)
        ));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, UITheme.FONT_SIZE_TITLE));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(Font.PLAIN, UITheme.FONT_SIZE_SM));
        descriptionLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel actionLabel = new JLabel("Open ->");
        actionLabel.setFont(actionLabel.getFont().deriveFont(Font.BOLD, UITheme.FONT_SIZE_SM));
        actionLabel.setForeground(UITheme.ACCENT);
        actionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(UITheme.SPACING_SM));
        card.add(descriptionLabel);
        card.add(Box.createVerticalGlue());
        card.add(actionLabel);

        Color normalColor = card.getBackground();
        Color hoverColor = UITheme.hoverBackground(normalColor);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onClick.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(hoverColor);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(normalColor);
            }
        });

        return card;
    }

    private JComboBox<String> createAwsProfileComboBox() {
        List<String> profiles = awsProfileService.getProfiles();
        if (profiles.isEmpty()) {
            JComboBox<String> emptyCombo = new JComboBox<>(new String[]{"No profiles found"});
            emptyCombo.setEnabled(false);
            emptyCombo.setToolTipText("No AWS profiles were found in ~/.aws/credentials");
            return emptyCombo;
        }

        JComboBox<String> combo = new JComboBox<>(profiles.toArray(new String[0]));

        String savedProfile = settingsService.getSavedAwsProfile();
        if (savedProfile != null && profiles.contains(savedProfile)) {
            combo.setSelectedItem(savedProfile);
        }

        combo.addActionListener(e -> {
            String selected = (String) combo.getSelectedItem();
            if (selected != null) {
                settingsService.saveAwsProfile(selected);
                pipelinePanel.setProfile(selected);
                ecsPanel.setProfile(selected);
                logsPanel.setProfile(selected);
            }
        });

        return combo;
    }

    private void showHomeView() {
        contentLayout.show(contentContainer, VIEW_HOME);
    }

    private void showPipelinesView() {
        contentLayout.show(contentContainer, VIEW_PIPELINES);
        pipelinePanel.refresh();
    }

    private void showEcsView() {
        contentLayout.show(contentContainer, VIEW_ECS);
        ecsPanel.refresh();
    }

    private void showLogsView() {
        contentLayout.show(contentContainer, VIEW_LOGS);
        logsPanel.refresh();
    }

    private void showSettingsView() {
        contentLayout.show(contentContainer, VIEW_SETTINGS);
    }
}
