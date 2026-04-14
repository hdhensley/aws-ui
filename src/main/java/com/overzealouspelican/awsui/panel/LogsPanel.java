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
        refreshLogGroups();
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

        panel.add(createFilterBar(), BorderLayout.NORTH);
        panel.add(createLogsDisplay(), BorderLayout.CENTER);

        return panel;
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

        groupComboBox.addActionListener(e -> {
            Object selected = groupComboBox.getSelectedItem();
            if (selected != null && !CHOOSE_GROUP_OPTION.equals(selected)) {
                refreshLogStreams(true);
            }
        });
    }

    private void refreshLogGroups() {
        if (currentProfile == null || currentProfile.isBlank()) {
            statusLabel.setText("No profile selected");
            groupComboBox.setModel(new DefaultComboBoxModel<>(new String[]{CHOOSE_GROUP_OPTION}));
            streamComboBox.setModel(new DefaultComboBoxModel<>());
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
                }
            }
        }.execute();
    }

    private void refreshLogStreams() {
        refreshLogStreams(false);
    }

    private void refreshLogStreams(boolean loadLogsAfterRefresh) {
        if (currentProfile == null || currentProfile.isBlank()) {
            statusLabel.setText("No profile selected");
            return;
        }

        String group = (String) groupComboBox.getSelectedItem();
        if (group == null || group.isBlank() || CHOOSE_GROUP_OPTION.equals(group)) {
            statusLabel.setText("Select a log group");
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
                }
            }
        }.execute();
    }

    private void loadLogs() {
        if (currentProfile == null || currentProfile.isBlank()) {
            showError("No AWS profile selected.");
            return;
        }

        String group = (String) groupComboBox.getSelectedItem();
        CloudWatchLogsService.LogStreamOption stream =
            (CloudWatchLogsService.LogStreamOption) streamComboBox.getSelectedItem();
        if (group == null || group.isBlank() || CHOOSE_GROUP_OPTION.equals(group)) {
            showError("Select a log group.");
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

    private record ParsedJsonRow(Map<String, String> values) {
    }

    private record TimeframeOption(String label, Duration duration) {
        @Override
        public String toString() {
            return label;
        }
    }
}
