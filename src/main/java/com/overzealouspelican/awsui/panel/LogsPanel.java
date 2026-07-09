package com.overzealouspelican.awsui.panel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.overzealouspelican.awsui.service.CloudWatchLogsService;
import com.overzealouspelican.awsui.service.SettingsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ExecutionException;

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
    private JsonLogsTableModel jsonLogsTableModel;
    private TableRowSorter<JsonLogsTableModel> jsonLogsRowSorter;
    private CardLayout displayLayout;
    private JPanel displayPanel;
    private JLabel statusLabel;
    private String lastRawLogs = "";
    private List<ParsedJsonRow> lastParsedJsonRows = List.of();

    private static final String DISPLAY_TEXT = "text";
    private static final String DISPLAY_TABLE = "table";

    public LogsPanel(CloudWatchLogsService logsService, SettingsService settingsService) {
        this.logsService = logsService;
        this.settingsService = settingsService;
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
        savedFiltersList.setCellRenderer(new SavedFilterListCellRenderer());
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

        jsonLogsTableModel = new JsonLogsTableModel();
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
        installCopyActionForTable();
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
        defaultSavedFilterName = settingsService.getDefaultLogsFilterName();
        savedFiltersListModel.clear();
        List<SettingsService.SavedLogsFilter> filters = settingsService.getSavedLogsFilters();
        int selectedIndex = -1;

        for (int i = 0; i < filters.size(); i++) {
            SettingsService.SavedLogsFilter filter = filters.get(i);
            savedFiltersListModel.addElement(filter);
            if (selectedName != null && selectedName.equalsIgnoreCase(filter.getName())) {
                selectedIndex = i;
            }
        }

        if (selectedIndex >= 0) {
            savedFiltersList.setSelectedIndex(selectedIndex);
        } else if (!savedFiltersListModel.isEmpty()) {
            savedFiltersList.setSelectedIndex(0);
        }

        savedFiltersList.repaint();
    }

    private void saveCurrentFilter() {
        String name = filterNameField.getText() == null ? "" : filterNameField.getText().trim();
        if (name.isBlank()) {
            showError("Enter a filter name before saving.");
            return;
        }

        String groupSearch = textOrEmpty(groupSearchField);
        String selectedGroup = selectedGroupValue();
        String streamSearch = textOrEmpty(streamSearchField);
        String selectedStream = selectedStreamValue();
        String timeframeLabel = selectedTimeframeLabel();
        String jsonLogStream = selectedJsonLogStreamValue();
        String clientSearch = textOrEmpty(clientSearchField);

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
        selectTimeframeByLabel(selected.getTimeframeLabel());

        refreshLogGroups(() -> {
            selectGroupByValue(selected.getSelectedGroup());
            refreshLogStreams(false, () -> {
                selectStreamByName(selected.getSelectedStream());
                loadLogs(() -> {
                    selectJsonLogStreamByValue(selected.getJsonLogStream());
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

    private String selectedGroupValue() {
        Object selectedGroup = groupComboBox.getSelectedItem();
        if (!(selectedGroup instanceof String value)) {
            return "";
        }
        return CHOOSE_GROUP_OPTION.equals(value) ? "" : value;
    }

    private String selectedStreamValue() {
        Object selectedStream = streamComboBox.getSelectedItem();
        if (!(selectedStream instanceof CloudWatchLogsService.LogStreamOption value)) {
            return "";
        }
        return value.name() == null ? "" : value.name();
    }

    private String selectedTimeframeLabel() {
        Object selectedTimeframe = timeframeComboBox.getSelectedItem();
        return selectedTimeframe == null ? "" : selectedTimeframe.toString();
    }

    private String selectedJsonLogStreamValue() {
        Object selectedJsonStream = jsonLogStreamFilterComboBox.getSelectedItem();
        if (!(selectedJsonStream instanceof String value) || ALL_JSON_LOG_STREAMS.equals(value)) {
            return "";
        }
        return value;
    }

    private String textOrEmpty(JTextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private void selectTimeframeByLabel(String label) {
        if (label == null || label.isBlank()) {
            return;
        }

        for (int i = 0; i < timeframeComboBox.getItemCount(); i++) {
            TimeframeOption option = timeframeComboBox.getItemAt(i);
            if (label.equals(option.toString())) {
                timeframeComboBox.setSelectedIndex(i);
                break;
            }
        }
    }

    private void selectGroupByValue(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return;
        }

        ComboBoxModel<String> model = groupComboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            String value = model.getElementAt(i);
            if (groupName.equals(value)) {
                groupComboBox.setSelectedItem(value);
                return;
            }
        }
    }

    private void selectStreamByName(String streamName) {
        if (streamName == null || streamName.isBlank()) {
            streamComboBox.setSelectedItem(ALL_STREAMS_OPTION);
            return;
        }

        ComboBoxModel<CloudWatchLogsService.LogStreamOption> model = streamComboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            CloudWatchLogsService.LogStreamOption option = model.getElementAt(i);
            if (streamName.equals(option.name())) {
                streamComboBox.setSelectedItem(option);
                return;
            }
        }
        streamComboBox.setSelectedItem(ALL_STREAMS_OPTION);
    }

    private void selectJsonLogStreamByValue(String value) {
        if (value == null || value.isBlank()) {
            jsonLogStreamFilterComboBox.setSelectedItem(ALL_JSON_LOG_STREAMS);
            return;
        }

        ComboBoxModel<String> model = jsonLogStreamFilterComboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            String option = model.getElementAt(i);
            if (value.equals(option)) {
                jsonLogStreamFilterComboBox.setSelectedItem(option);
                return;
            }
        }
        jsonLogStreamFilterComboBox.setSelectedItem(ALL_JSON_LOG_STREAMS);
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

        setBusyState(true, "Loading log groups...");
        String query = groupSearchField == null ? "" : groupSearchField.getText();

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return logsService.listLogGroups(currentProfile, query);
            }

            @Override
            protected void done() {
                try {
                    List<String> groups = get();
                    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                    model.addElement(CHOOSE_GROUP_OPTION);
                    for (String group : groups) {
                        model.addElement(group);
                    }
                    groupComboBox.setModel(model);
                    groupComboBox.setSelectedIndex(0);
                    streamComboBox.setModel(new DefaultComboBoxModel<>(new CloudWatchLogsService.LogStreamOption[]{ALL_STREAMS_OPTION}));
                    statusLabel.setText("Groups: " + groups.size());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Interrupted while loading groups");
                } catch (ExecutionException ex) {
                    statusLabel.setText("Failed to load groups");
                    showError(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                } finally {
                    setBusyState(false, statusLabel.getText());
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        }.execute();
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

        setBusyState(true, "Loading streams...");
        String query = streamSearchField.getText();

        new SwingWorker<List<CloudWatchLogsService.LogStreamOption>, Void>() {
            @Override
            protected List<CloudWatchLogsService.LogStreamOption> doInBackground() {
                return logsService.listLogStreams(currentProfile, group, query);
            }

            @Override
            protected void done() {
                boolean shouldAutoLoadLogs = false;
                try {
                    List<CloudWatchLogsService.LogStreamOption> streams = get();
                    DefaultComboBoxModel<CloudWatchLogsService.LogStreamOption> model = new DefaultComboBoxModel<>();
                    model.addElement(ALL_STREAMS_OPTION);
                    for (CloudWatchLogsService.LogStreamOption stream : streams) {
                        model.addElement(stream);
                    }
                    streamComboBox.setModel(model);
                    statusLabel.setText("Streams: " + streams.size() + " (most recent first)");
                    shouldAutoLoadLogs = true;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Interrupted while loading streams");
                } catch (ExecutionException ex) {
                    statusLabel.setText("Failed to load streams");
                    showError(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                } finally {
                    setBusyState(false, statusLabel.getText());
                    if (loadLogsAfterRefresh && shouldAutoLoadLogs) {
                        loadLogs();
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        }.execute();
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
        Instant end = Instant.now();
        Instant start = end.minus(duration);

        setBusyState(true, "Loading logs...");
        logsTextArea.setText("");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                String streamName = stream == null ? null : stream.name();
                return logsService.getLogEvents(currentProfile, group, streamName, start, end);
            }

            @Override
            protected void done() {
                try {
                    renderLogs(get());
                    statusLabel.setText("Loaded logs for " + timeframe);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Interrupted while loading logs");
                } catch (ExecutionException ex) {
                    statusLabel.setText("Failed to load logs");
                    showError(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                } finally {
                    setBusyState(false, statusLabel.getText());
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        }.execute();
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
        List<ParsedJsonRow> parsedRows = parseJsonRows(rawLogs);
        lastParsedJsonRows = parsedRows;
        if (!parsedRows.isEmpty()) {
            jsonLogsTableModel.setRows(parsedRows);
            updateJsonLogStreamFilterOptions(parsedRows);
            applyJsonLogStreamFilter();
            resizeJsonColumns();
            displayLayout.show(displayPanel, DISPLAY_TABLE);
            return;
        }

        jsonLogsTableModel.clear();
        jsonLogStreamFilterComboBox.setModel(new DefaultComboBoxModel<>(new String[]{ALL_JSON_LOG_STREAMS}));
        jsonLogStreamFilterComboBox.setEnabled(false);
        jsonLogsRowSorter.setRowFilter(null);
        logsTextArea.setText(filterTextLogs(lastRawLogs, clientSearchField.getText()));
        logsTextArea.setCaretPosition(0);
        displayLayout.show(displayPanel, DISPLAY_TEXT);
    }

    private void updateJsonLogStreamFilterOptions(List<ParsedJsonRow> parsedRows) {
        Set<String> values = new LinkedHashSet<>();
        for (ParsedJsonRow row : parsedRows) {
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
            public boolean include(Entry<? extends JsonLogsTableModel, ? extends Integer> entry) {
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

        logsTextArea.setText(filterTextLogs(lastRawLogs, clientSearchField.getText()));
        logsTextArea.setCaretPosition(0);
    }

    private String filterTextLogs(String rawLogs, String query) {
        if (rawLogs == null) {
            return "";
        }
        if (query == null || query.isBlank()) {
            return rawLogs;
        }

        String[] lines = rawLogs.split("\\R");
        String needle = query.toLowerCase(Locale.ROOT);
        StringBuilder filtered = new StringBuilder();
        int matches = 0;

        for (String line : lines) {
            if (line.toLowerCase(Locale.ROOT).contains(needle)) {
                filtered.append(line).append(System.lineSeparator());
                matches++;
            }
        }

        if (matches == 0) {
            return "No client-side matches for: " + query;
        }

        return filtered.toString();
    }

    private void installCopyActionForTable() {
        KeyStroke copy = KeyStroke.getKeyStroke(
            KeyEvent.VK_C,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
        );
        logsTable.getInputMap(JComponent.WHEN_FOCUSED).put(copy, "copyCells");
        logsTable.getActionMap().put("copyCells", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                copySelectedTableCells();
            }
        });
    }

    private void copySelectedTableCells() {
        int[] selectedRows = logsTable.getSelectedRows();
        int[] selectedCols = logsTable.getSelectedColumns();
        if (selectedRows.length == 0 || selectedCols.length == 0) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int r = 0; r < selectedRows.length; r++) {
            for (int c = 0; c < selectedCols.length; c++) {
                Object value = logsTable.getValueAt(selectedRows[r], selectedCols[c]);
                if (value != null) {
                    builder.append(value);
                }
                if (c < selectedCols.length - 1) {
                    builder.append('\t');
                }
            }
            if (r < selectedRows.length - 1) {
                builder.append(System.lineSeparator());
            }
        }

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
            new StringSelection(builder.toString()),
            null
        );
    }

    private List<ParsedJsonRow> parseJsonRows(String rawLogs) {
        if (rawLogs == null || rawLogs.isBlank()) {
            return List.of();
        }

        String[] lines = rawLogs.split("\\R");
        List<ParsedJsonRow> rows = new ArrayList<>();
        int nonEmptyLines = 0;

        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }

            nonEmptyLines++;

            int jsonStart = line.indexOf('{');
            if (jsonStart < 0) {
                continue;
            }

            String prefixTimestamp = line.substring(0, jsonStart).trim();
            String jsonText = line.substring(jsonStart).trim();

            try {
                JsonElement element = JsonParser.parseString(jsonText);
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject object = element.getAsJsonObject();
                Map<String, String> values = new LinkedHashMap<>();
                values.put("timestamp", prefixTimestamp);
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    values.put(entry.getKey(), flattenJsonValue(entry.getValue()));
                }
                rows.add(new ParsedJsonRow(values));
            } catch (Exception ex) {
                // Ignore malformed lines and keep any valid JSON rows we can recover.
            }
        }

        if (rows.isEmpty()) {
            return List.of();
        }

        // If at least one line parsed as JSON, prefer the table view over raw text.
        return rows;
    }

    private String flattenJsonValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "";
        }
        if (value.isJsonPrimitive()) {
            return value.getAsJsonPrimitive().getAsString();
        }
        return value.toString();
    }

    private void resizeJsonColumns() {
        for (int column = 0; column < logsTable.getColumnCount(); column++) {
            int width = 120;
            for (int row = 0; row < Math.min(logsTable.getRowCount(), 100); row++) {
                Object value = logsTable.getValueAt(row, column);
                if (value != null) {
                    width = Math.max(width, Math.min(500, value.toString().length() * 7));
                }
            }
            logsTable.getColumnModel().getColumn(column).setPreferredWidth(width);
        }
    }

    private static class JsonLogsTableModel extends AbstractTableModel {
        private List<String> columns = List.of();
        private List<ParsedJsonRow> rows = List.of();

        void setRows(List<ParsedJsonRow> newRows) {
            rows = newRows;
            Set<String> discoveredColumns = new LinkedHashSet<>();
            discoveredColumns.add("timestamp");
            discoveredColumns.add("@timestamp");
            discoveredColumns.add("log.level");
            discoveredColumns.add("message");
            discoveredColumns.add("log.logger");
            discoveredColumns.add("process.thread.name");
            discoveredColumns.add("logStream");
            for (ParsedJsonRow row : newRows) {
                discoveredColumns.addAll(row.values().keySet());
            }
            columns = new ArrayList<>();
            for (String column : discoveredColumns) {
                boolean exists = newRows.stream().anyMatch(row -> row.values().containsKey(column));
                if (exists || "timestamp".equals(column)) {
                    columns.add(column);
                }
            }
            fireTableStructureChanged();
        }

        void clear() {
            columns = List.of();
            rows = List.of();
            fireTableStructureChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.size();
        }

        @Override
        public String getColumnName(int column) {
            return columns.get(column);
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return rows.get(rowIndex).values().getOrDefault(columns.get(columnIndex), "");
        }

        int getColumnIndex(String columnName) {
            return columns.indexOf(columnName);
        }
    }

    private class SavedFilterListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SettingsService.SavedLogsFilter filter) {
                boolean isDefault = defaultSavedFilterName != null
                    && defaultSavedFilterName.equalsIgnoreCase(filter.getName());
                label.setText(isDefault ? filter.getName() + " (default)" : filter.getName());
            }
            return label;
        }
    }

    private record ParsedJsonRow(Map<String, String> values) {
    }

    private record TimeframeOption(String label, Duration duration) {
        @Override
        public String toString() {
            return label;
        }
    }
}
