package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CloudWatchLogsService;
import com.overzealouspelican.awsui.service.SettingsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseEvent;
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
    private LogsSavedFilterCoordinator savedFilterCoordinator;
    private LogsCommandController commandController;

    private String currentProfile;

    private JTextField groupSearchField;
    private JTextField streamSearchField;
    private JComboBox<String> groupComboBox;
    private JComboBox<CloudWatchLogsService.LogStreamOption> streamComboBox;
    private JComboBox<LogsTimeframeOption> timeframeComboBox;
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
        savedFilterCoordinator.applyDefaultSavedFilterOrRefresh();
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
        initializeCoordinators();

        refreshLogGroups();
    }

    private void initializeCoordinators() {
        savedFilterCoordinator = new LogsSavedFilterCoordinator(
            settingsService,
            groupSearchField,
            streamSearchField,
            groupComboBox,
            streamComboBox,
            timeframeComboBox,
            jsonLogStreamFilterComboBox,
            clientSearchField,
            filterNameField,
            savedFiltersList,
            savedFiltersListModel,
            CHOOSE_GROUP_OPTION,
            ALL_JSON_LOG_STREAMS,
            ALL_STREAMS_OPTION,
            this::showError,
            this::setStatus,
            this::applyClientSideSearch,
            this::refreshLogGroups,
            this::refreshLogStreams,
            this::loadLogs,
            () -> savedFiltersList.repaint(),
            value -> applyingSavedFilter = value,
            () -> defaultSavedFilterName,
            value -> defaultSavedFilterName = value
        );

        commandController = new LogsCommandController(
            asyncWorkflow,
            CHOOSE_GROUP_OPTION,
            this::setStatus,
            this::showError
        );
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
        LogsSavedFiltersView view = new LogsSavedFiltersView(
            SAVED_FILTERS_WIDTH_EXPANDED,
            SAVED_FILTERS_WIDTH_COLLAPSED,
            () -> defaultSavedFilterName,
            this::applySelectedSavedFilter,
            this::deleteSelectedSavedFilter,
            this::toggleSavedFiltersCollapsed,
            this::showSavedFilterContextMenuIfNeeded
        );

        savedFiltersGutter = view.gutter;
        savedFiltersCollapsedIcon = view.collapsedIcon;
        savedFiltersTitleLabel = view.titleLabel;
        toggleSavedFiltersButton = view.toggleButton;
        savedFiltersContentPanel = view.contentPanel;
        savedFiltersListModel = view.listModel;
        savedFiltersList = view.list;
        applySavedFilterButton = view.applyButton;
        deleteSavedFilterButton = view.deleteButton;

        savedFilterCoordinator.reloadSavedFiltersList(null);
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
        LogsFilterBarView view = new LogsFilterBarView(
            STANDARD_FILTER_INPUT_SIZE,
            STANDARD_FILTER_BUTTON_SIZE,
            ALL_JSON_LOG_STREAMS
        );
        groupSearchField = view.groupSearchField;
        refreshGroupsButton = view.refreshGroupsButton;
        groupComboBox = view.groupComboBox;
        streamSearchField = view.streamSearchField;
        refreshStreamsButton = view.refreshStreamsButton;
        streamComboBox = view.streamComboBox;
        timeframeComboBox = view.timeframeComboBox;
        loadLogsButton = view.loadLogsButton;
        jsonLogStreamFilterComboBox = view.jsonLogStreamFilterComboBox;
        clientSearchField = view.clientSearchField;
        applyClientSearchButton = view.applyClientSearchButton;
        clearClientSearchButton = view.clearClientSearchButton;
        filterNameField = view.filterNameField;
        saveFilterButton = view.saveFilterButton;

        wireActions();
        return view.panel;
    }

    private JComponent createLogsDisplay() {
        LogsDisplayView view = new LogsDisplayView(DISPLAY_TEXT, DISPLAY_TABLE);
        displayLayout = view.layout;
        displayPanel = view.panel;
        logsTextArea = view.logsTextArea;
        logsTable = view.logsTable;
        jsonLogsTableModel = view.tableModel;
        jsonLogsRowSorter = view.rowSorter;
        return view.panel;
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

    private void saveCurrentFilter() {
        savedFilterCoordinator.saveCurrentFilter();
    }

    private void deleteSelectedSavedFilter() {
        savedFilterCoordinator.deleteSelectedSavedFilter(this);
    }

    private void applySelectedSavedFilter() {
        savedFilterCoordinator.applySelectedSavedFilter(true);
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
        clearDefaultItem.setEnabled(savedFilterCoordinator.isSelectedFilterDefault(selected));
        menu.add(clearDefaultItem);

        menu.show(savedFiltersList, event.getX(), event.getY());
    }

    private void setDefaultSavedFilter(SettingsService.SavedLogsFilter filter) {
        savedFilterCoordinator.setDefaultSavedFilter(filter);
    }

    private void clearDefaultSavedFilter() {
        savedFilterCoordinator.clearDefaultSavedFilter();
    }

    private void refreshLogGroups() {
        refreshLogGroups(null);
    }

    private void refreshLogGroups(Runnable onComplete) {
        commandController.refreshLogGroups(currentProfile, groupComboBox, streamComboBox, groupSearchField, onComplete);
    }

    private void refreshLogStreams() {
        refreshLogStreams(false);
    }

    private void refreshLogStreams(boolean loadLogsAfterRefresh) {
        refreshLogStreams(loadLogsAfterRefresh, null);
    }

    private void refreshLogStreams(boolean loadLogsAfterRefresh, Runnable onComplete) {
        commandController.refreshLogStreams(
            currentProfile,
            groupComboBox,
            streamSearchField,
            loadLogsAfterRefresh,
            this::loadLogs,
            onComplete
        );
    }

    private void loadLogs() {
        loadLogs(null);
    }

    private void loadLogs(Runnable onComplete) {
        commandController.loadLogs(currentProfile, groupComboBox, streamComboBox, timeframeComboBox, onComplete);
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

    private void setStatus(String text) {
        statusLabel.setText(text);
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
