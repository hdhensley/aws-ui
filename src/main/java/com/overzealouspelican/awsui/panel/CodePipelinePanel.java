package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CodePipelineService;
import com.overzealouspelican.awsui.service.SettingsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import javax.swing.RowFilter;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * IntelliJ-inspired CodePipeline monitoring view.
 * Loads pipeline data in the background using the currently selected AWS profile.
 * Call {@link #setProfile(String)} to switch profiles and refresh automatically.
 */
public class CodePipelinePanel extends JPanel {

    static final int COL_NAME = 0;
    static final int COL_HISTORY = 1;
    static final int COL_CONTEXT = 2;
    static final int COL_LAST_DEPLOYED = 3;
    static final int COL_ACTIONS = 4;

    private static final String STATE_LOADING = "loading";
    private static final String STATE_ERROR = "error";
    private static final String STATE_TABLE = "table";
    private static final int VISUAL_REFRESH_INTERVAL_MS = 15_000;
    private static final int VISUAL_REFRESH_PROGRESS_TICK_MS = 250;

    private final CodePipelineService service;
    private final SettingsService settingsService;

    private String currentProfile;
    private volatile boolean isLoading = false;

    private CodePipelineTableModel tableModel;
    private TableRowSorter<CodePipelineTableModel> rowSorter;
    private JTable pipelineTable;
    private JTextArea pipelineSummaryArea;
    private CodePipelinePhaseGraphPanel pipelinePhaseGraphPanel;
    private JTextField pipelineSearchField;
    private JLabel errorLabel;
    private JLabel statusLabel;
    private JProgressBar visualRefreshProgressBar;
    private JButton refreshVisualButton;
    private JSplitPane pipelineSplitPane;
    private JPanel pipelineDetailsPanel;
    private CardLayout stateLayout;
    private JPanel stateContainer;
    private Timer visualRefreshTimer;
    private Timer visualRefreshProgressTimer;
    private long nextVisualRefreshAtMillis = -1L;
    private boolean visualRefreshPaused = false;
    private long visualRefreshPausedAtMillis = 0L;
    private int detailsRequestVersion = 0;

    public CodePipelinePanel(CodePipelineService service, SettingsService settingsService) {
        this.service = service;
        this.settingsService = settingsService;
        this.currentProfile = settingsService.getSavedAwsProfile();
        initializePanel();
    }

    /** Called by MainFrame whenever the profile dropdown changes. */
    public void setProfile(String profile) {
        this.currentProfile = profile;
        loadPipelines();
    }

    /** Called by MainFrame when navigating to this view. */
    public void refresh() {
        loadPipelines();
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBackground(UITheme.panelBackground());

        add(createHeader(), BorderLayout.NORTH);

        stateLayout = new CardLayout();
        stateContainer = new JPanel(stateLayout);
        stateContainer.add(createLoadingPanel(), STATE_LOADING);
        stateContainer.add(createErrorPanel(), STATE_ERROR);
        stateContainer.add(createMainContent(), STATE_TABLE);
        add(stateContainer, BorderLayout.CENTER);

        initializeVisualRefreshTimers();

        stateLayout.show(stateContainer, STATE_LOADING);
    }

    private void initializeVisualRefreshTimers() {
        visualRefreshTimer = new Timer(VISUAL_REFRESH_INTERVAL_MS, e -> refreshSelectedPipelineDetails(false));
        visualRefreshTimer.setRepeats(true);
        visualRefreshTimer.setInitialDelay(VISUAL_REFRESH_INTERVAL_MS);

        visualRefreshProgressTimer = new Timer(VISUAL_REFRESH_PROGRESS_TICK_MS, e -> updateVisualRefreshProgress());
        visualRefreshProgressTimer.setRepeats(true);
        visualRefreshProgressTimer.setInitialDelay(0);
    }

    private JComponent createHeader() {
        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, UITheme.FONT_SIZE_SM));
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel trailingPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UITheme.SPACING_SM, 0));
        trailingPanel.add(statusLabel);
        return UITheme.createPageHeader("CodePipelines", trailingPanel);
    }

    private JComponent createMainContent() {
        JPanel panel = new JPanel(new BorderLayout(UITheme.SPACING_SM, UITheme.SPACING_SM));
        panel.setBorder(UITheme.contentPadding());
        panel.setBackground(UITheme.panelBackground());

        panel.add(createFilterBar(), BorderLayout.NORTH);
        panel.add(createTablePanel(), BorderLayout.CENTER);
        return panel;
    }

    private JComponent createFilterBar() {
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, UITheme.SPACING_SM, 0));
        filterBar.setOpaque(false);

        filterBar.add(new JLabel("Search pipelines:"));
        pipelineSearchField = new JTextField(24);
        pipelineSearchField.addActionListener(e -> applyPipelineNameFilter());

        JButton clearSearchButton = new JButton("Clear");
        clearSearchButton.addActionListener(e -> {
            pipelineSearchField.setText("");
            applyPipelineNameFilter();
        });

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadPipelines());

        filterBar.add(pipelineSearchField);
        filterBar.add(clearSearchButton);
        filterBar.add(refreshButton);
        return filterBar;
    }

    private JPanel createLoadingPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UITheme.panelBackground());
        JLabel label = new JLabel("Loading pipelines…");
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(label);
        return panel;
    }

    private JPanel createErrorPanel() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(UITheme.panelBackground());

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);

        errorLabel = new JLabel();
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton retryButton = new JButton("Retry");
        retryButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        retryButton.addActionListener(e -> loadPipelines());

        inner.add(errorLabel);
        inner.add(Box.createVerticalStrut(UITheme.SPACING_MD));
        inner.add(retryButton);

        wrapper.add(inner);
        return wrapper;
    }

    private JComponent createTablePanel() {
        tableModel = new CodePipelineTableModel();
        pipelineTable = new JTable(tableModel);

        rowSorter = new TableRowSorter<>(tableModel);
        rowSorter.setComparator(COL_NAME, String.CASE_INSENSITIVE_ORDER);
        rowSorter.setComparator(COL_LAST_DEPLOYED, Comparator
            .nullsLast((a, b) -> {
                String left = String.valueOf(a);
                String right = String.valueOf(b);
                if ("-".equals(left) && "-".equals(right)) {
                    return 0;
                }
                if ("-".equals(left)) {
                    return 1;
                }
                if ("-".equals(right)) {
                    return -1;
                }
                return left.compareTo(right);
            }));
        rowSorter.setSortable(COL_HISTORY, false);
        rowSorter.setSortable(COL_CONTEXT, false);
        rowSorter.setSortable(COL_ACTIONS, false);
        pipelineTable.setRowSorter(rowSorter);

        pipelineTable.setRowHeight(40);
        pipelineTable.setShowHorizontalLines(false);
        pipelineTable.setShowVerticalLines(false);
        pipelineTable.setFillsViewportHeight(true);
        pipelineTable.getTableHeader().setReorderingAllowed(false);
        pipelineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        pipelineTable.getColumnModel().getColumn(COL_NAME).setPreferredWidth(280);
        pipelineTable.getColumnModel().getColumn(COL_HISTORY).setPreferredWidth(130);
        pipelineTable.getColumnModel().getColumn(COL_HISTORY).setMaxWidth(150);
        pipelineTable.getColumnModel().getColumn(COL_CONTEXT).setPreferredWidth(200);
        pipelineTable.getColumnModel().getColumn(COL_LAST_DEPLOYED).setPreferredWidth(190);
        pipelineTable.getColumnModel().getColumn(COL_LAST_DEPLOYED).setMaxWidth(220);
        pipelineTable.getColumnModel().getColumn(COL_ACTIONS).setPreferredWidth(250);
        pipelineTable.getColumnModel().getColumn(COL_ACTIONS).setMaxWidth(280);

        pipelineTable.getColumnModel().getColumn(COL_HISTORY).setCellRenderer(new CodePipelineHistoryCellRenderer());
        pipelineTable.getColumnModel().getColumn(COL_CONTEXT).setCellRenderer(new CodePipelineContextCellRenderer());
        pipelineTable.getColumnModel().getColumn(COL_ACTIONS).setCellRenderer(new CodePipelineActionsRenderer());
        pipelineTable.getColumnModel().getColumn(COL_ACTIONS).setCellEditor(
            new CodePipelineActionsEditor(this::handleView, this::handleStart, this::handleStop)
        );

        pipelineTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updatePipelineDetails();
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(pipelineTable);
        tableScrollPane.setBorder(null);

        pipelineSummaryArea = new JTextArea();
        pipelineSummaryArea.setEditable(false);
        pipelineSummaryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        pipelineSummaryArea.setRows(5);
        pipelineSummaryArea.setBorder(BorderFactory.createEmptyBorder(UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM));

        pipelinePhaseGraphPanel = new CodePipelinePhaseGraphPanel();
        JScrollPane phaseGraphScrollPane = new JScrollPane(pipelinePhaseGraphPanel);
        phaseGraphScrollPane.setBorder(BorderFactory.createEmptyBorder());
        phaseGraphScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        phaseGraphScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        JLabel detailsTitleLabel = new JLabel("Pipeline Details");
        detailsTitleLabel.setFont(detailsTitleLabel.getFont().deriveFont(Font.BOLD, UITheme.FONT_SIZE_MD));

        visualRefreshProgressBar = new JProgressBar(0, VISUAL_REFRESH_INTERVAL_MS);
        visualRefreshProgressBar.setStringPainted(true);
        visualRefreshProgressBar.setPreferredSize(new Dimension(180, UITheme.BUTTON_HEIGHT - 4));
        visualRefreshProgressBar.setValue(0);
        visualRefreshProgressBar.setString("Auto-refresh disabled");
        visualRefreshProgressBar.setEnabled(false);
        visualRefreshProgressBar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        visualRefreshProgressBar.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (nextVisualRefreshAtMillis > 0L) {
                    toggleVisualRefreshPause();
                }
            }
        });

        refreshVisualButton = new JButton("Refresh Visual");
        refreshVisualButton.setEnabled(false);
        refreshVisualButton.addActionListener(e -> refreshSelectedPipelineDetails(true));

        JPanel detailsActionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UITheme.SPACING_SM, 0));
        detailsActionsPanel.setOpaque(false);
        detailsActionsPanel.add(visualRefreshProgressBar);
        detailsActionsPanel.add(refreshVisualButton);

        JPanel detailsHeader = new JPanel(new BorderLayout(UITheme.SPACING_SM, 0));
        detailsHeader.setOpaque(false);
        detailsHeader.add(detailsTitleLabel, BorderLayout.WEST);
        detailsHeader.add(detailsActionsPanel, BorderLayout.EAST);

        JPanel stackedDetailsContent = new JPanel(new BorderLayout(UITheme.SPACING_SM, UITheme.SPACING_SM));
        stackedDetailsContent.setOpaque(false);
        stackedDetailsContent.add(detailsHeader, BorderLayout.NORTH);

        JPanel summaryAndGraphPanel = new JPanel(new BorderLayout(UITheme.SPACING_SM, UITheme.SPACING_SM));
        summaryAndGraphPanel.setOpaque(false);
        summaryAndGraphPanel.add(new JScrollPane(pipelineSummaryArea), BorderLayout.NORTH);
        summaryAndGraphPanel.add(phaseGraphScrollPane, BorderLayout.CENTER);

        stackedDetailsContent.add(summaryAndGraphPanel, BorderLayout.CENTER);

        pipelineDetailsPanel = new JPanel(new BorderLayout());
        pipelineDetailsPanel.setBackground(UITheme.surfaceBackground());
        pipelineDetailsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
            BorderFactory.createEmptyBorder(UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM)
        ));
        pipelineDetailsPanel.add(stackedDetailsContent, BorderLayout.CENTER);

        pipelineSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScrollPane, pipelineDetailsPanel);
        pipelineSplitPane.setResizeWeight(0.72);
        pipelineSplitPane.setDividerSize(8);

        setPipelineDetailsVisible(false);

        return pipelineSplitPane;
    }

    private void applyPipelineNameFilter() {
        if (rowSorter == null) {
            return;
        }

        String query = pipelineSearchField == null ? "" : pipelineSearchField.getText().trim();
        if (query.isBlank()) {
            rowSorter.setRowFilter(null);
            return;
        }

        String needle = query.toLowerCase(Locale.ROOT);
        rowSorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends CodePipelineTableModel, ? extends Integer> entry) {
                Object value = entry.getValue(COL_NAME);
                return value != null && value.toString().toLowerCase(Locale.ROOT).contains(needle);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Data loading
    // -------------------------------------------------------------------------

    private void loadPipelines() {
        if (isLoading) {
            return;
        }
        stopVisualRefreshTimers();
        if (currentProfile == null || currentProfile.isBlank()) {
            errorLabel.setText("No AWS profile selected. Choose one on the Home page.");
            stateLayout.show(stateContainer, STATE_ERROR);
            statusLabel.setText("");
            if (refreshVisualButton != null) {
                refreshVisualButton.setEnabled(false);
            }
            setPipelineDetailsVisible(false);
            return;
        }

        isLoading = true;
        statusLabel.setText("Loading…");
        stateLayout.show(stateContainer, STATE_LOADING);

        new SwingWorker<List<CodePipelineService.PipelineRow>, Void>() {
            @Override
            protected List<CodePipelineService.PipelineRow> doInBackground() {
                return service.listPipelines(currentProfile);
            }

            @Override
            protected void done() {
                isLoading = false;
                try {
                    List<CodePipelineService.PipelineRow> rows = get();
                    tableModel.setRows(rows);
                    if (pipelineSummaryArea != null) {
                        pipelineSummaryArea.setText("");
                    }
                    if (pipelinePhaseGraphPanel != null) {
                        pipelinePhaseGraphPanel.showEmpty();
                    }
                    if (refreshVisualButton != null) {
                        refreshVisualButton.setEnabled(false);
                    }
                    setPipelineDetailsVisible(false);
                    detailsRequestVersion++;
                    stateLayout.show(stateContainer, STATE_TABLE);
                    String count = rows.size() + " pipeline" + (rows.size() == 1 ? "" : "s");
                    statusLabel.setText("Profile: " + currentProfile + "  ·  " + count);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("Load interrupted.");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    showError("<html>Failed to load pipelines:<br>" + cause.getMessage() + "</html>");
                }
            }
        }.execute();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        stateLayout.show(stateContainer, STATE_ERROR);
        statusLabel.setText("Error");
    }

    private void updatePipelineDetails() {
        if (pipelineSummaryArea == null || pipelinePhaseGraphPanel == null || pipelineTable == null) {
            return;
        }

        int selectedRow = pipelineTable.getSelectedRow();
        if (selectedRow < 0) {
            detailsRequestVersion++;
            stopVisualRefreshTimers();
            pipelineSummaryArea.setText("");
            pipelinePhaseGraphPanel.showEmpty();
            if (refreshVisualButton != null) {
                refreshVisualButton.setEnabled(false);
            }
            setPipelineDetailsVisible(false);
            return;
        }

        setPipelineDetailsVisible(true);
        if (refreshVisualButton != null) {
            refreshVisualButton.setEnabled(true);
        }
        refreshSelectedPipelineDetails(true);
        restartVisualRefreshTimers();
    }

    private void refreshSelectedPipelineDetails(boolean showLoadingState) {
        CodePipelineService.PipelineRow row = getSelectedPipelineRow();
        if (row == null || currentProfile == null || currentProfile.isBlank()) {
            stopVisualRefreshTimers();
            if (refreshVisualButton != null) {
                refreshVisualButton.setEnabled(false);
            }
            setPipelineDetailsVisible(false);
            return;
        }

        int requestVersion = ++detailsRequestVersion;
        stopVisualRefreshProgressTimer();
        pipelineSummaryArea.setText(buildPipelineSummaryText(row));
        if (showLoadingState) {
            pipelinePhaseGraphPanel.showLoading();
        }
        setVisualRefreshProgressState(false, 0, "Refreshing...");
        loadPipelineSnapshot(row.name(), requestVersion);
    }

    private CodePipelineService.PipelineRow getSelectedPipelineRow() {
        if (pipelineTable == null) {
            return null;
        }

        int selectedRow = pipelineTable.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }

        int modelRow = pipelineTable.convertRowIndexToModel(selectedRow);
        if (modelRow < 0 || modelRow >= tableModel.getRowCount()) {
            return null;
        }
        return tableModel.getRowAt(modelRow);
    }

    private void loadPipelineSnapshot(String pipelineName, int requestVersion) {
        String profile = currentProfile;
        new SwingWorker<CodePipelineService.PipelineSnapshot, Void>() {
            @Override
            protected CodePipelineService.PipelineSnapshot doInBackground() {
                return service.getPipelineSnapshot(profile, pipelineName);
            }

            @Override
            protected void done() {
                if (requestVersion != detailsRequestVersion) {
                    return;
                }

                try {
                    CodePipelineService.PipelineSnapshot snapshot = get();
                    pipelineSummaryArea.setText(buildPipelineSummaryText(snapshot.row()));
                    pipelineSummaryArea.setCaretPosition(0);
                    pipelinePhaseGraphPanel.setPhases(snapshot.phases());
                    if (CodePipelineRefreshPolicy.shouldAutoRefresh(snapshot.phases())) {
                        restartVisualRefreshTimers();
                    } else {
                        stopVisualRefreshTimers();
                        setVisualRefreshProgressState(false, 0, "Auto-refresh idle (all phases OK)");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    pipelinePhaseGraphPanel.showMessage("Load interrupted");
                    setVisualRefreshProgressState(false, 0, "Refresh interrupted");
                } catch (ExecutionException e) {
                    pipelinePhaseGraphPanel.showMessage("Failed to load phases");
                    setVisualRefreshProgressState(false, 0, "Refresh failed");
                }
            }
        }.execute();
    }

    private void restartVisualRefreshTimers() {
        nextVisualRefreshAtMillis = System.currentTimeMillis() + VISUAL_REFRESH_INTERVAL_MS;
        if (visualRefreshTimer != null) {
            visualRefreshTimer.restart();
        }
        if (visualRefreshProgressTimer != null) {
            visualRefreshProgressTimer.restart();
        }
        updateVisualRefreshProgress();
    }

    private void stopVisualRefreshTimers() {
        if (visualRefreshTimer != null) {
            visualRefreshTimer.stop();
        }
        stopVisualRefreshProgressTimer();
        nextVisualRefreshAtMillis = -1L;
        visualRefreshPaused = false;
        setVisualRefreshProgressState(false, 0, "Auto-refresh disabled");
    }

    private void stopVisualRefreshProgressTimer() {
        if (visualRefreshProgressTimer != null) {
            visualRefreshProgressTimer.stop();
        }
    }

    private void setPipelineDetailsVisible(boolean visible) {
        if (pipelineSplitPane == null || pipelineDetailsPanel == null) {
            return;
        }

        pipelineDetailsPanel.setVisible(visible);
        pipelineSplitPane.setDividerSize(visible ? 8 : 0);
        if (visible) {
            SwingUtilities.invokeLater(() -> pipelineSplitPane.setDividerLocation(0.72));
        } else {
            pipelineSplitPane.setDividerLocation(1.0d);
        }
    }

    private void updateVisualRefreshProgress() {
        if (visualRefreshProgressBar == null || nextVisualRefreshAtMillis < 0L) {
            return;
        }

        long remainingMillis;
        if (visualRefreshPaused) {
            remainingMillis = Math.max(0L, nextVisualRefreshAtMillis - visualRefreshPausedAtMillis);
        } else {
            remainingMillis = Math.max(0L, nextVisualRefreshAtMillis - System.currentTimeMillis());
        }

        int elapsedMillis = (int) Math.min(VISUAL_REFRESH_INTERVAL_MS, VISUAL_REFRESH_INTERVAL_MS - remainingMillis);
        long remainingSeconds = (remainingMillis + 999) / 1000;  // Round up to full seconds
        String pauseIndicator = visualRefreshPaused ? " (paused)" : "";
        String progressText = String.format(Locale.ROOT, "Next refresh in %ds%s", remainingSeconds, pauseIndicator);

        setVisualRefreshProgressState(true, elapsedMillis, progressText);

        if (!visualRefreshPaused && remainingMillis == 0L) {
            stopVisualRefreshProgressTimer();
        }
    }

    private void setVisualRefreshProgressState(boolean enabled, int value, String text) {
        if (visualRefreshProgressBar == null) {
            return;
        }

        visualRefreshProgressBar.setEnabled(enabled);
        visualRefreshProgressBar.setValue(Math.max(0, Math.min(VISUAL_REFRESH_INTERVAL_MS, value)));
        visualRefreshProgressBar.setString(text);
    }

    private void toggleVisualRefreshPause() {
        visualRefreshPaused = !visualRefreshPaused;
        if (visualRefreshPaused) {
            visualRefreshPausedAtMillis = System.currentTimeMillis();
            if (visualRefreshTimer != null) {
                visualRefreshTimer.stop();
            }
        } else {
            // Resume: adjust the next refresh time by the pause duration
            long pauseDurationMillis = System.currentTimeMillis() - visualRefreshPausedAtMillis;
            nextVisualRefreshAtMillis += pauseDurationMillis;
            if (visualRefreshTimer != null) {
                visualRefreshTimer.restart();
            }
        }
        updateVisualRefreshProgress();
    }

    private String buildPipelineSummaryText(CodePipelineService.PipelineRow row) {
        String recentRuns = row.recentStatuses().isEmpty()
            ? "-"
            : String.join(", ", row.recentStatuses());

        return "Name: " + row.name() + System.lineSeparator()
            + "Region: " + row.region() + System.lineSeparator()
            + "Current Status: " + row.status() + System.lineSeparator()
            + "Last Deployed: " + row.lastDeployedAt() + System.lineSeparator()
            + "Current Execution ID: " + (row.inProgressExecutionId() == null ? "-" : row.inProgressExecutionId()) + System.lineSeparator()
            + "Recent Runs (newest first): " + recentRuns;
    }

    private void handleStart(CodePipelineService.PipelineRow row) {
        String profile = currentProfile;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.startPipeline(profile, row.name());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadPipelines();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(CodePipelinePanel.this,
                        "Failed to start pipeline: " + cause.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void handleView(CodePipelineService.PipelineRow row) {
        if (row == null) {
            return;
        }

        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            JOptionPane.showMessageDialog(this,
                "Opening the AWS Console is not supported on this system.",
                "Unsupported Action", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Desktop.getDesktop().browse(buildPipelineConsoleUri(row));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Failed to open pipeline in AWS Console: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private URI buildPipelineConsoleUri(CodePipelineService.PipelineRow row) {
        String encodedPipelineName = URLEncoder.encode(row.name(), StandardCharsets.UTF_8)
            .replace("+", "%20");
        String url = "https://" + row.region()
            + ".console.aws.amazon.com/codesuite/codepipeline/pipelines/"
            + encodedPipelineName
            + "/view?region=" + row.region();
        return URI.create(url);
    }

    private void handleStop(CodePipelineService.PipelineRow row) {
        if (row.inProgressExecutionId() == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
            "Stop execution of '" + row.name() + "'?",
            "Confirm Stop", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        String profile = currentProfile;
        String executionId = row.inProgressExecutionId();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.stopPipeline(profile, row.name(), executionId);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadPipelines();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(CodePipelinePanel.this,
                        "Failed to stop pipeline: " + cause.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }
}
