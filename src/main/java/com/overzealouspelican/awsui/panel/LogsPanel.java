package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CloudWatchLogsService;
import com.overzealouspelican.awsui.service.SettingsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CloudWatch logs viewer with searchable group/stream selectors and timeframe filter.
 */
public class LogsPanel extends JPanel {

    private static final Dimension STANDARD_FILTER_INPUT_SIZE = new Dimension(240, 30);
    private static final Dimension STANDARD_FILTER_BUTTON_SIZE = new Dimension(130, 30);
    private static final int SAVED_FILTERS_WIDTH_EXPANDED = 280;
    private static final int SAVED_FILTERS_WIDTH_COLLAPSED = 36;

    private static final CloudWatchLogsService.LogStreamOption ALL_STREAMS_OPTION =
        new CloudWatchLogsService.LogStreamOption("", "All streams", null);
    private static final String ALL_JSON_LOG_STREAMS = "All logStream values";
    private static final String CHOOSE_GROUP_OPTION = "Choose group";

    private final CloudWatchLogsService logsService;
    private final SettingsService settingsService;
    private final LogsAsyncWorkflow asyncWorkflow;

    private String currentProfile;

    private JTextField groupSearchField;
    private JTextField streamSearchField;
    private JComboBox<String> groupComboBox;
    private JComboBox<CloudWatchLogsService.LogStreamOption> streamComboBox;
    private JComboBox<TimeframeOption> timeframeComboBox;
    private JButton refreshGroupsButton;
    private JButton refreshStreamsButton;
    private JButton loadLogsButton;
    private JComboBox<String> jsonLogStreamFilterComboBox;
    private JTextField clientSearchField;
    private JButton applyClientSearchButton;
    private JButton clearClientSearchButton;
    private JTextField filterNameField;
    private JButton saveFilterButton;
    private JButton applySavedFilterButton;
    private JButton deleteSavedFilterButton;
    private JButton toggleSavedFiltersButton;
    private JLabel savedFiltersTitleLabel;
    private Icon savedFiltersCollapsedIcon;
    private JList<SettingsService.SavedLogsFilter> savedFiltersList;
    private DefaultListModel<SettingsService.SavedLogsFilter> savedFiltersListModel;
    private JPanel savedFiltersContentPanel;
    private JPanel savedFiltersGutter;
    private boolean savedFiltersCollapsed;
    private boolean applyingSavedFilter;
    private String defaultSavedFilterName;
    private JTextArea logsTextArea;
    private JTable logsTable;
    private LogsJsonTableModel jsonLogsTableModel;
    private TableRowSorter<LogsJsonTableModel> jsonLogsRowSorter;
    private CardLayout displayLayout;
    private JPanel displayPanel;
    private JLabel statusLabel;
    private String lastRawLogs = "";
    private List<LogsParsedJsonRow> lastParsedJsonRows = List.of();

    private static final String DISPLAY_TEXT = "text";
    private static final String DISPLAY_TABLE = "table";

    public LogsPanel(CloudWatchLogsService logsService, SettingsService settingsService) {
        this.logsService = logsService;
        this.settingsService = settingsService;
        this.asyncWorkflow = new LogsAsyncWorkflow(logsService, new AsyncViewImpl());
        this.currentProfile = settingsService.getSavedAwsProfile();
        initializePanel();
    }

    public void setProfile(String profile) {
        this.currentProfile = profile;
        resetFiltersForProfileChange();
        if (statusLabel != null) {
            statusLabel.setText("Switching profile...");
        }
        refreshLogGroups();
    }

    public void refresh() {
        applyDefaultSavedFilterOrRefresh();
    }

    private void resetFiltersForProfileChange() {
        if (groupSearchField != null) {
            groupSearchField.setText("");
        }
        if (streamSearchField != null) {
            streamSearchField.setText("");
        }
        if (groupComboBox != null) {
            groupComboBox.setModel(new DefaultComboBoxModel<>());
        }
        if (streamComboBox != null) {
            streamComboBox.setModel(new DefaultComboBoxModel<>(
                new CloudWatchLogsService.LogStreamOption[]{ALL_STREAMS_OPTION}
            ));
        }
        if (jsonLogStreamFilterComboBox != null) {
            jsonLogStreamFilterComboBox.setModel(new DefaultComboBoxModel<>(new String[]{ALL_JSON_LOG_STREAMS}));
            jsonLogStreamFilterComboBox.setSelectedIndex(0);
            jsonLogStreamFilterComboBox.setEnabled(false);
        }
        if (timeframeComboBox != null) {
            timeframeComboBox.setSelectedIndex(2);
        }
        if (logsTextArea != null) {
            logsTextArea.setText("");
        }
        if (jsonLogsTableModel != null) {
            jsonLogsTableModel.clear();
        }
        if (clientSearchField != null) {
            clientSearchField.setText("");
        }
        if (filterNameField != null) {
            filterNameField.setText("");
        }
        applyingSavedFilter = false;
        lastRawLogs = "";
        lastParsedJsonRows = List.of();
        if (displayLayout != null && displayPanel != null) {
            displayLayout.show(displayPanel, DISPLAY_TEXT);
        }
        if (statusLabel != null) {
            statusLabel.setText("Switching profile...");
        }
    }

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBackground(UITheme.panelBackground());

        add(createHeader(), BorderLayout.NORTH);
        add(createMainContent(), BorderLayout.CENTER);

        refreshLogGroups();
    }

    private JComponent createHeader() {
        statusLabel = new JLabel("");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, UITheme.FONT_SIZE_SM));
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel trailingPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UITheme.SPACING_SM, 0));
        trailingPanel.add(statusLabel);
        return UITheme.createPageHeader("Logs", trailingPanel);
    }

    private JComponent createMainContent() {
        JPanel panel = new JPanel(new BorderLayout(UITheme.SPACING_SM, UITheme.SPACING_SM));
        panel.setBorder(UITheme.contentPadding());
        panel.setBackground(UITheme.panelBackground());

        JPanel rightPanel = new JPanel(new BorderLayout(UITheme.SPACING_SM, UITheme.SPACING_SM));
        rightPanel.setOpaque(false);
        rightPanel.add(createFilterBar(), BorderLayout.NORTH);
        rightPanel.add(createLogsDisplay(), BorderLayout.CENTER);

        panel.add(createSavedFiltersGutter(), BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.CENTER);

        return panel;
    }

    private JComponent createSavedFiltersGutter() {
        savedFiltersGutter = new JPanel(new BorderLayout());
        savedFiltersGutter.setBackground(UITheme.panelBackground());
        savedFiltersGutter.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIManager.getColor("Component.borderColor")));
        savedFiltersGutter.setPreferredSize(new Dimension(SAVED_FILTERS_WIDTH_EXPANDED, 0));
        savedFiltersGutter.setMinimumSize(new Dimension(SAVED_FILTERS_WIDTH_COLLAPSED, 0));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM));

        savedFiltersCollapsedIcon = UIManager.getIcon("FileView.directoryIcon");
        if (savedFiltersCollapsedIcon == null) {
            savedFiltersCollapsedIcon = UIManager.getIcon("OptionPane.informationIcon");
        }

        savedFiltersTitleLabel = new JLabel("Saved Filters");
        savedFiltersTitleLabel.setFont(savedFiltersTitleLabel.getFont().deriveFont(Font.BOLD, UITheme.FONT_SIZE_SM));

        toggleSavedFiltersButton = new JButton("<");
        toggleSavedFiltersButton.setMargin(new Insets(2, 8, 2, 8));
        toggleSavedFiltersButton.addActionListener(e -> toggleSavedFiltersCollapsed());

        header.add(savedFiltersTitleLabel, BorderLayout.WEST);
        header.add(toggleSavedFiltersButton, BorderLayout.EAST);

        savedFiltersContentPanel = new JPanel(new BorderLayout(UITheme.SPACING_SM, UITheme.SPACING_SM));
        savedFiltersContentPanel.setOpaque(false);
        savedFiltersContentPanel.setBorder(BorderFactory.createEmptyBorder(0, UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM));

        savedFiltersListModel = new DefaultListModel<>();
        savedFiltersList = new JList<>(savedFiltersListModel);
        savedFiltersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        savedFiltersList.setCellRenderer(new LogsSavedFilterListCellRenderer(() -> defaultSavedFilterName));
        savedFiltersList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    applySelectedSavedFilter();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                showSavedFilterContextMenuIfNeeded(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showSavedFilterContextMenuIfNeeded(e);
            }
        });

        JScrollPane listScrollPane = new JScrollPane(savedFiltersList);
        listScrollPane.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        JPanel actions = new JPanel(new GridLayout(1, 2, UITheme.SPACING_SM, 0));
        actions.setOpaque(false);
        applySavedFilterButton = new JButton("Apply");
        applySavedFilterButton.addActionListener(e -> applySelectedSavedFilter());
        deleteSavedFilterButton = new JButton("Delete");
        deleteSavedFilterButton.addActionListener(e -> deleteSelectedSavedFilter());
        actions.add(applySavedFilterButton);
        actions.add(deleteSavedFilterButton);

        savedFiltersContentPanel.add(listScrollPane, BorderLayout.CENTER);
        savedFiltersContentPanel.add(actions, BorderLayout.SOUTH);

        savedFiltersGutter.add(header, BorderLayout.NORTH);
        savedFiltersGutter.add(savedFiltersContentPanel, BorderLayout.CENTER);

        reloadSavedFiltersList(null);
        return savedFiltersGutter;
    }

    private void toggleSavedFiltersCollapsed() {
        savedFiltersCollapsed = !savedFiltersCollapsed;
        savedFiltersContentPanel.setVisible(!savedFiltersCollapsed);
        if (savedFiltersCollapsed) {
            savedFiltersTitleLabel.setText("");
            savedFiltersTitleLabel.setIcon(savedFiltersCollapsedIcon);
        } else {
            savedFiltersTitleLabel.setText("Saved Filters");
            savedFiltersTitleLabel.setIcon(null);
        }
        savedFiltersGutter.setPreferredSize(new Dimension(
            savedFiltersCollapsed ? SAVED_FILTERS_WIDTH_COLLAPSED : SAVED_FILTERS_WIDTH_EXPANDED,
            0
        ));
        toggleSavedFiltersButton.setText(savedFiltersCollapsed ? ">" : "<");
        savedFiltersGutter.revalidate();
        savedFiltersGutter.repaint();
    }

    private JComponent createFilterBar() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UITheme.panelBackground());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, UITheme.SPACING_SM, UITheme.SPACING_SM);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Log group search + select
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("Log Group Search:"), gbc);

        groupSearchField = new JTextField();
        applyStandardFilterInputSize(groupSearchField);
        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(groupSearchField, gbc);

        refreshGroupsButton = new JButton("Find Groups");
        applyStandardButtonSize(refreshGroupsButton);
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(refreshGroupsButton, gbc);

        groupComboBox = new JComboBox<>();
        applyStandardFilterInputSize(groupComboBox);
        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(groupComboBox, gbc);

        // Log stream search + select
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Log Stream Search:"), gbc);

        streamSearchField = new JTextField();
        applyStandardFilterInputSize(streamSearchField);
        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(streamSearchField, gbc);

        refreshStreamsButton = new JButton("Find Streams");
        applyStandardButtonSize(refreshStreamsButton);
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(refreshStreamsButton, gbc);

        streamComboBox = new JComboBox<>();
        applyStandardFilterInputSize(streamComboBox);
        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(streamComboBox, gbc);

        // Timeframe + load logs
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.insets = new Insets(0, 0, 0, UITheme.SPACING_SM);
        panel.add(new JLabel("Timeframe:"), gbc);

        timeframeComboBox = new JComboBox<>(new TimeframeOption[]{
            new TimeframeOption("Last 1 minute", Duration.ofMinutes(1)),
            new TimeframeOption("Last 5 minutes", Duration.ofMinutes(5)),
            new TimeframeOption("Last 10 minutes", Duration.ofMinutes(10)),
            new TimeframeOption("Last 30 minutes", Duration.ofMinutes(30)),
            new TimeframeOption("Last 60 minutes", Duration.ofMinutes(60)),
            new TimeframeOption("Last 6 hours", Duration.ofHours(6)),
            new TimeframeOption("Last 24 hours", Duration.ofHours(24))
        });
        applyStandardFilterInputSize(timeframeComboBox);
        timeframeComboBox.setSelectedIndex(2);
        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(timeframeComboBox, gbc);

        loadLogsButton = new JButton("Load Logs");
        UITheme.stylePrimaryButton(loadLogsButton);
        applyStandardButtonSize(loadLogsButton);
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(loadLogsButton, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(Box.createHorizontalStrut(STANDARD_FILTER_INPUT_SIZE.width), gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.insets = new Insets(UITheme.SPACING_SM, 0, 0, UITheme.SPACING_SM);
        panel.add(new JLabel("JSON logStream:"), gbc);

        jsonLogStreamFilterComboBox = new JComboBox<>(new String[]{ALL_JSON_LOG_STREAMS});
        jsonLogStreamFilterComboBox.setEnabled(false);
        applyStandardFilterInputSize(jsonLogStreamFilterComboBox);
        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(jsonLogStreamFilterComboBox, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(Box.createHorizontalStrut(STANDARD_FILTER_BUTTON_SIZE.width), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(Box.createHorizontalStrut(STANDARD_FILTER_INPUT_SIZE.width), gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        gbc.insets = new Insets(UITheme.SPACING_SM, 0, 0, UITheme.SPACING_SM);
        panel.add(new JLabel("Client Search:"), gbc);

        clientSearchField = new JTextField();
        applyStandardFilterInputSize(clientSearchField);
        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(clientSearchField, gbc);

        JPanel searchButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UITheme.SPACING_SM, 0));
        searchButtonsPanel.setOpaque(false);
        applyClientSearchButton = new JButton("Apply Search");
        clearClientSearchButton = new JButton("Clear Search");
        searchButtonsPanel.add(applyClientSearchButton);
        searchButtonsPanel.add(clearClientSearchButton);

        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(searchButtonsPanel, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(Box.createHorizontalStrut(STANDARD_FILTER_INPUT_SIZE.width), gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        gbc.insets = new Insets(UITheme.SPACING_SM, 0, 0, UITheme.SPACING_SM);
        panel.add(new JLabel("Filter Name:"), gbc);

        filterNameField = new JTextField();
        applyStandardFilterInputSize(filterNameField);
        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(filterNameField, gbc);

        saveFilterButton = new JButton("Save Filter");
        applyStandardButtonSize(saveFilterButton);
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(saveFilterButton, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(Box.createHorizontalStrut(STANDARD_FILTER_INPUT_SIZE.width), gbc);

        wireActions();
        return panel;
    }

    private void applyStandardFilterInputSize(JComponent component) {
        component.setPreferredSize(STANDARD_FILTER_INPUT_SIZE);
        component.setMinimumSize(STANDARD_FILTER_INPUT_SIZE);
    }

    private void applyStandardButtonSize(AbstractButton button) {
        button.setPreferredSize(STANDARD_FILTER_BUTTON_SIZE);
        button.setMinimumSize(STANDARD_FILTER_BUTTON_SIZE);
    }

    private JComponent createLogsDisplay() {
        displayLayout = new CardLayout();
        displayPanel = new JPanel(displayLayout);

        logsTextArea = new JTextArea();
        logsTextArea.setEditable(false);
        logsTextArea.setLineWrap(false);
        logsTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane textScrollPane = new JScrollPane(logsTextArea);
        textScrollPane.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        jsonLogsTableModel = new LogsJsonTableModel();
        logsTable = new JTable(jsonLogsTableModel);
        jsonLogsRowSorter = new TableRowSorter<>(jsonLogsTableModel);
        logsTable.setRowSorter(jsonLogsRowSorter);
        logsTable.setCellSelectionEnabled(true);
        logsTable.setRowSelectionAllowed(true);
        logsTable.setColumnSelectionAllowed(true);
        logsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        logsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        logsTable.setFillsViewportHeight(true);
        logsTable.setRowHeight(28);
        LogsTableClipboardSupport.installCopyAction(logsTable);
        JScrollPane tableScrollPane = new JScrollPane(logsTable);
        tableScrollPane.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        displayPanel.add(textScrollPane, DISPLAY_TEXT);
        displayPanel.add(tableScrollPane, DISPLAY_TABLE);
        displayLayout.show(displayPanel, DISPLAY_TEXT);
        return displayPanel;
    }

    private void wireActions() {
        refreshGroupsButton.addActionListener(e -> refreshLogGroups());
        groupSearchField.addActionListener(e -> {
            if (refreshGroupsButton.isEnabled()) {
                refreshGroupsButton.doClick();
            }
        });
        refreshStreamsButton.addActionListener(e -> refreshLogStreams(false));
        streamSearchField.addActionListener(e -> {
            if (refreshStreamsButton.isEnabled()) {
                refreshStreamsButton.doClick();
            }
        });
        loadLogsButton.addActionListener(e -> loadLogs());
        jsonLogStreamFilterComboBox.addActionListener(e -> applyJsonLogStreamFilter());
        applyClientSearchButton.addActionListener(e -> applyClientSideSearch());
        clearClientSearchButton.addActionListener(e -> {
            clientSearchField.setText("");
            applyClientSideSearch();
        });
        clientSearchField.addActionListener(e -> applyClientSideSearch());
        saveFilterButton.addActionListener(e -> saveCurrentFilter());
        filterNameField.addActionListener(e -> saveCurrentFilter());

        groupComboBox.addActionListener(e -> {
            if (applyingSavedFilter) {
                return;
            }
            Object selected = groupComboBox.getSelectedItem();
            if (selected != null && !CHOOSE_GROUP_OPTION.equals(selected)) {
                refreshLogStreams(true);
            }
        });
    }

    private void reloadSavedFiltersList(String selectedName) {
        defaultSavedFilterName = LogsSavedFilterWorkflow.reloadSavedFiltersList(
            settingsService,
            savedFiltersListModel,
            savedFiltersList,
            selectedName
        );
    }

    private void saveCurrentFilter() {
        String name = filterNameField.getText() == null ? "" : filterNameField.getText().trim();
        if (name.isBlank()) {
            showError("Enter a filter name before saving.");
            return;
        }

        String groupSearch = LogsSavedFilterWorkflow.textOrEmpty(groupSearchField);
        String selectedGroup = LogsSavedFilterWorkflow.selectedGroupValue(groupComboBox, CHOOSE_GROUP_OPTION);
        String streamSearch = LogsSavedFilterWorkflow.textOrEmpty(streamSearchField);
        String selectedStream = LogsSavedFilterWorkflow.selectedStreamValue(streamComboBox);
        String timeframeLabel = LogsSavedFilterWorkflow.selectedTimeframeLabel(timeframeComboBox);
        String jsonLogStream = LogsSavedFilterWorkflow.selectedJsonLogStreamValue(jsonLogStreamFilterComboBox, ALL_JSON_LOG_STREAMS);
        String clientSearch = LogsSavedFilterWorkflow.textOrEmpty(clientSearchField);

        settingsService.saveLogsFilter(new SettingsService.SavedLogsFilter(
            name,
            groupSearch,
            selectedGroup,
            streamSearch,
            selectedStream,
            timeframeLabel,
            jsonLogStream,
            clientSearch
        ));

        reloadSavedFiltersList(name);
        statusLabel.setText("Saved filter: " + name);
    }

    private void deleteSelectedSavedFilter() {
        SettingsService.SavedLogsFilter selected = savedFiltersList.getSelectedValue();
        if (selected == null) {
            showError("Select a saved filter to delete.");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
            this,
            "Delete saved filter '" + selected.getName() + "'?",
            "Delete Filter",
            JOptionPane.YES_NO_OPTION
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        settingsService.removeLogsFilter(selected.getName());
        reloadSavedFiltersList(null);
        statusLabel.setText("Deleted filter: " + selected.getName());
    }

    private void applySelectedSavedFilter() {
        SettingsService.SavedLogsFilter selected = savedFiltersList.getSelectedValue();
        if (selected == null) {
            showError("Select a saved filter to apply.");
            return;
        }

        applySavedFilter(selected, true);
    }

    private void applySavedFilter(SettingsService.SavedLogsFilter selected, boolean showErrorOnFailure) {
        if (selected == null) {
            if (showErrorOnFailure) {
                showError("Select a saved filter to apply.");
            }
            return;
        }

        applyingSavedFilter = true;
        filterNameField.setText(selected.getName());
        groupSearchField.setText(selected.getGroupSearch());
        streamSearchField.setText(selected.getStreamSearch());
        clientSearchField.setText(selected.getClientSearch());
        LogsSavedFilterWorkflow.selectTimeframeByLabel(timeframeComboBox, selected.getTimeframeLabel());

        refreshLogGroups(() -> {
            LogsSavedFilterWorkflow.selectGroupByValue(groupComboBox, selected.getSelectedGroup());
            refreshLogStreams(false, () -> {
                LogsSavedFilterWorkflow.selectStreamByName(streamComboBox, ALL_STREAMS_OPTION, selected.getSelectedStream());
                loadLogs(() -> {
                    LogsSavedFilterWorkflow.selectJsonLogStreamByValue(
                        jsonLogStreamFilterComboBox,
                        ALL_JSON_LOG_STREAMS,
                        selected.getJsonLogStream()
                    );
                    applyClientSideSearch();
                    statusLabel.setText("Applied filter: " + selected.getName());
                    applyingSavedFilter = false;
                });
            });
        });
    }

    private void applyDefaultSavedFilterOrRefresh() {
        String defaultName = settingsService.getDefaultLogsFilterName();
        if (defaultName == null || defaultName.isBlank()) {
            refreshLogGroups();
            return;
        }

        List<SettingsService.SavedLogsFilter> filters = settingsService.getSavedLogsFilters();
        for (SettingsService.SavedLogsFilter filter : filters) {
            if (defaultName.equalsIgnoreCase(filter.getName())) {
                reloadSavedFiltersList(filter.getName());
                applySavedFilter(filter, false);
                return;
            }
        }

        refreshLogGroups();
    }

    private void showSavedFilterContextMenuIfNeeded(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }

        int index = savedFiltersList.locationToIndex(event.getPoint());
        if (index < 0) {
            return;
        }

        savedFiltersList.setSelectedIndex(index);
        SettingsService.SavedLogsFilter selected = savedFiltersList.getSelectedValue();
        if (selected == null) {
            return;
        }

        JPopupMenu menu = new JPopupMenu();

        JMenuItem setDefaultItem = new JMenuItem("Set as Default");
        setDefaultItem.addActionListener(e -> setDefaultSavedFilter(selected));
        menu.add(setDefaultItem);

        JMenuItem clearDefaultItem = new JMenuItem("Clear Default");
        clearDefaultItem.addActionListener(e -> clearDefaultSavedFilter());
        clearDefaultItem.setEnabled(defaultSavedFilterName != null && defaultSavedFilterName.equalsIgnoreCase(selected.getName()));
        menu.add(clearDefaultItem);

        menu.show(savedFiltersList, event.getX(), event.getY());
    }

    private void setDefaultSavedFilter(SettingsService.SavedLogsFilter filter) {
        settingsService.setDefaultLogsFilterName(filter.getName());
        defaultSavedFilterName = filter.getName();
        savedFiltersList.repaint();
        statusLabel.setText("Default filter set: " + filter.getName());
    }

    private void clearDefaultSavedFilter() {
        settingsService.clearDefaultLogsFilterName();
        defaultSavedFilterName = null;
        savedFiltersList.repaint();
        statusLabel.setText("Default filter cleared");
    }

    private void refreshLogGroups() {
        refreshLogGroups(null);
    }

    private void refreshLogGroups(Runnable onComplete) {
        if (currentProfile == null || currentProfile.isBlank()) {
            statusLabel.setText("No profile selected");
            groupComboBox.setModel(new DefaultComboBoxModel<>(new String[]{CHOOSE_GROUP_OPTION}));
            streamComboBox.setModel(new DefaultComboBoxModel<>());
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        String query = groupSearchField == null ? "" : groupSearchField.getText();
        asyncWorkflow.refreshLogGroups(currentProfile, query, onComplete);
    }

    private void refreshLogStreams() {
        refreshLogStreams(false);
    }

    private void refreshLogStreams(boolean loadLogsAfterRefresh) {
        refreshLogStreams(loadLogsAfterRefresh, null);
    }

    private void refreshLogStreams(boolean loadLogsAfterRefresh, Runnable onComplete) {
        if (currentProfile == null || currentProfile.isBlank()) {
            statusLabel.setText("No profile selected");
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        String group = (String) groupComboBox.getSelectedItem();
        if (group == null || group.isBlank() || CHOOSE_GROUP_OPTION.equals(group)) {
            statusLabel.setText("Select a log group");
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        String query = streamSearchField.getText();
        asyncWorkflow.refreshLogStreams(currentProfile, group, query, loadLogsAfterRefresh, this::loadLogs, onComplete);
    }

    private void loadLogs() {
        loadLogs(null);
    }

    private void loadLogs(Runnable onComplete) {
        if (currentProfile == null || currentProfile.isBlank()) {
            showError("No AWS profile selected.");
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        String group = (String) groupComboBox.getSelectedItem();
        CloudWatchLogsService.LogStreamOption stream =
            (CloudWatchLogsService.LogStreamOption) streamComboBox.getSelectedItem();
        if (group == null || group.isBlank() || CHOOSE_GROUP_OPTION.equals(group)) {
            showError("Select a log group.");
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        TimeframeOption timeframe = (TimeframeOption) timeframeComboBox.getSelectedItem();
        Duration duration = timeframe == null ? Duration.ofMinutes(10) : timeframe.duration();
        String streamName = stream == null ? null : stream.name();
        asyncWorkflow.loadLogs(currentProfile, group, streamName, duration, timeframe == null ? "" : timeframe.toString(), onComplete);
    }

    private void setBusyState(boolean busy, String statusText) {
        refreshGroupsButton.setEnabled(!busy);
        refreshStreamsButton.setEnabled(!busy);
        loadLogsButton.setEnabled(!busy);
        groupSearchField.setEnabled(!busy);
        streamSearchField.setEnabled(!busy);
        groupComboBox.setEnabled(!busy);
        streamComboBox.setEnabled(!busy);
        timeframeComboBox.setEnabled(!busy);
        if (filterNameField != null) {
            filterNameField.setEnabled(!busy);
        }
        if (saveFilterButton != null) {
            saveFilterButton.setEnabled(!busy);
        }
        if (applySavedFilterButton != null) {
            applySavedFilterButton.setEnabled(!busy);
        }
        if (deleteSavedFilterButton != null) {
            deleteSavedFilterButton.setEnabled(!busy);
        }
        statusLabel.setText(statusText);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Logs", JOptionPane.ERROR_MESSAGE);
    }

    private void renderLogs(String rawLogs) {
        lastRawLogs = rawLogs == null ? "" : rawLogs;
        List<LogsParsedJsonRow> parsedRows = LogsJsonParser.parseJsonRows(rawLogs);
        lastParsedJsonRows = parsedRows;
        if (!parsedRows.isEmpty()) {
            jsonLogsTableModel.setRows(parsedRows);
            updateJsonLogStreamFilterOptions(parsedRows);
            applyJsonLogStreamFilter();
            LogsTableColumnAutoSizer.resizeColumns(logsTable, 100, 120, 500, 7);
            displayLayout.show(displayPanel, DISPLAY_TABLE);
            return;
        }

        jsonLogsTableModel.clear();
        jsonLogStreamFilterComboBox.setModel(new DefaultComboBoxModel<>(new String[]{ALL_JSON_LOG_STREAMS}));
        jsonLogStreamFilterComboBox.setEnabled(false);
        jsonLogsRowSorter.setRowFilter(null);
        logsTextArea.setText(LogsTextFilter.filterTextLogs(lastRawLogs, clientSearchField.getText()));
        logsTextArea.setCaretPosition(0);
        displayLayout.show(displayPanel, DISPLAY_TEXT);
    }

    private void updateJsonLogStreamFilterOptions(List<LogsParsedJsonRow> parsedRows) {
        Set<String> values = new LinkedHashSet<>();
        for (LogsParsedJsonRow row : parsedRows) {
            String value = row.values().get("logStream");
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(ALL_JSON_LOG_STREAMS);
        for (String value : values) {
            model.addElement(value);
        }
        jsonLogStreamFilterComboBox.setModel(model);
        jsonLogStreamFilterComboBox.setSelectedIndex(0);
        jsonLogStreamFilterComboBox.setEnabled(values.size() > 0);
    }

    private void applyJsonLogStreamFilter() {
        if (jsonLogsRowSorter == null || jsonLogsTableModel == null) {
            return;
        }

        int logStreamColumn = jsonLogsTableModel.getColumnIndex("logStream");
        if (logStreamColumn < 0) {
            jsonLogsRowSorter.setRowFilter(null);
            return;
        }

        Object selected = jsonLogStreamFilterComboBox.getSelectedItem();
        String selectedValue = selected instanceof String s ? s : ALL_JSON_LOG_STREAMS;
        String search = clientSearchField == null ? "" : clientSearchField.getText().trim().toLowerCase(Locale.ROOT);

        if (ALL_JSON_LOG_STREAMS.equals(selectedValue) && search.isBlank()) {
            jsonLogsRowSorter.setRowFilter(null);
            return;
        }

        jsonLogsRowSorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends LogsJsonTableModel, ? extends Integer> entry) {
                boolean streamMatches = ALL_JSON_LOG_STREAMS.equals(selectedValue)
                    || selectedValue.equals(String.valueOf(entry.getValue(logStreamColumn)));
                if (!streamMatches) {
                    return false;
                }

                if (search.isBlank()) {
                    return true;
                }

                for (int i = 0; i < entry.getValueCount(); i++) {
                    Object value = entry.getValue(i);
                    if (value != null && value.toString().toLowerCase(Locale.ROOT).contains(search)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void applyClientSideSearch() {
        if (!lastParsedJsonRows.isEmpty()) {
            applyJsonLogStreamFilter();
            return;
        }

        logsTextArea.setText(LogsTextFilter.filterTextLogs(lastRawLogs, clientSearchField.getText()));
        logsTextArea.setCaretPosition(0);
    }

    private record TimeframeOption(String label, Duration duration) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final class AsyncViewImpl implements LogsAsyncWorkflow.AsyncView {
        @Override
        public void setBusyState(boolean busy, String statusText) {
            LogsPanel.this.setBusyState(busy, statusText);
        }

        @Override
        public void setStatus(String text) {
            statusLabel.setText(text);
        }

        @Override
        public String currentStatusText() {
            return statusLabel.getText();
        }

        @Override
        public void showError(String message) {
            LogsPanel.this.showError(message);
        }

        @Override
        public void onGroupsLoaded(List<String> groups) {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            model.addElement(CHOOSE_GROUP_OPTION);
            for (String group : groups) {
                model.addElement(group);
            }
            groupComboBox.setModel(model);
            groupComboBox.setSelectedIndex(0);
            streamComboBox.setModel(new DefaultComboBoxModel<>(new CloudWatchLogsService.LogStreamOption[]{ALL_STREAMS_OPTION}));
        }

        @Override
        public void onStreamsLoaded(List<CloudWatchLogsService.LogStreamOption> streams) {
            DefaultComboBoxModel<CloudWatchLogsService.LogStreamOption> model = new DefaultComboBoxModel<>();
            model.addElement(ALL_STREAMS_OPTION);
            for (CloudWatchLogsService.LogStreamOption stream : streams) {
                model.addElement(stream);
            }
            streamComboBox.setModel(model);
        }

        @Override
        public void beforeLogsLoad() {
            logsTextArea.setText("");
        }

        @Override
        public void onLogsLoaded(String rawLogs, String timeframeLabel) {
            renderLogs(rawLogs);
            statusLabel.setText("Loaded logs for " + timeframeLabel);
        }
    }
}
