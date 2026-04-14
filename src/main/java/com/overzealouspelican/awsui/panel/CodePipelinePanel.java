package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CodePipelineService;
import com.overzealouspelican.awsui.service.SettingsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import javax.swing.RowFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    private static final int COL_NAME = 0;
    private static final int COL_HISTORY = 1;
    private static final int COL_CONTEXT = 2;
    private static final int COL_LAST_DEPLOYED = 3;
    private static final int COL_ACTIONS = 4;

    private static final String STATE_LOADING = "loading";
    private static final String STATE_ERROR = "error";
    private static final String STATE_TABLE = "table";
    private static final int VISUAL_REFRESH_INTERVAL_MS = 15_000;
    private static final int VISUAL_REFRESH_PROGRESS_TICK_MS = 250;

    private final CodePipelineService service;
    private final SettingsService settingsService;

    private String currentProfile;
    private volatile boolean isLoading = false;

    private PipelineTableModel tableModel;
    private TableRowSorter<PipelineTableModel> rowSorter;
    private JTable pipelineTable;
    private JTextArea pipelineSummaryArea;
    private PipelinePhaseGraphPanel pipelinePhaseGraphPanel;
    private JTextField pipelineSearchField;
    private JLabel errorLabel;
    private JLabel statusLabel;
    private JLabel visualRefreshStatusLabel;
    private JProgressBar visualRefreshProgressBar;
    private JButton refreshVisualButton;
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
        tableModel = new PipelineTableModel();
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

        pipelineTable.getColumnModel().getColumn(COL_HISTORY).setCellRenderer(new HistoryCellRenderer());
        pipelineTable.getColumnModel().getColumn(COL_CONTEXT).setCellRenderer(new ContextCellRenderer());
        pipelineTable.getColumnModel().getColumn(COL_ACTIONS).setCellRenderer(new ActionsRenderer());
        pipelineTable.getColumnModel().getColumn(COL_ACTIONS).setCellEditor(new ActionsEditor());

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

        pipelinePhaseGraphPanel = new PipelinePhaseGraphPanel();
        JScrollPane phaseGraphScrollPane = new JScrollPane(pipelinePhaseGraphPanel);
        phaseGraphScrollPane.setBorder(BorderFactory.createEmptyBorder());
        phaseGraphScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        phaseGraphScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        JLabel detailsTitleLabel = new JLabel("Pipeline Details");
        detailsTitleLabel.setFont(detailsTitleLabel.getFont().deriveFont(Font.BOLD, UITheme.FONT_SIZE_MD));

        visualRefreshStatusLabel = new JLabel("Select a pipeline to enable visual refresh.");
        visualRefreshStatusLabel.setFont(visualRefreshStatusLabel.getFont().deriveFont(Font.PLAIN, UITheme.FONT_SIZE_SM));
        visualRefreshStatusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

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
        detailsActionsPanel.add(visualRefreshStatusLabel);
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

        JPanel detailsPanel = new JPanel(new BorderLayout());
        detailsPanel.setBackground(UITheme.surfaceBackground());
        detailsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
            BorderFactory.createEmptyBorder(UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM)
        ));
        detailsPanel.add(stackedDetailsContent, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScrollPane, detailsPanel);
        splitPane.setResizeWeight(0.72);
        splitPane.setDividerSize(8);

        return splitPane;
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
            public boolean include(Entry<? extends PipelineTableModel, ? extends Integer> entry) {
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
            setVisualRefreshStatus("Select a pipeline to enable visual refresh.");
            if (refreshVisualButton != null) {
                refreshVisualButton.setEnabled(false);
            }
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
                    setVisualRefreshStatus("Select a pipeline to enable visual refresh.");
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
            setVisualRefreshStatus("Select a pipeline to enable visual refresh.");
            return;
        }

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
            setVisualRefreshStatus("Select a pipeline to enable visual refresh.");
            return;
        }

        int requestVersion = ++detailsRequestVersion;
        stopVisualRefreshProgressTimer();
        pipelineSummaryArea.setText(buildPipelineSummaryText(row));
        if (showLoadingState) {
            pipelinePhaseGraphPanel.showLoading();
        }
        setVisualRefreshStatus("Refreshing selected pipeline...");
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
        if (modelRow < 0 || modelRow >= tableModel.rows.size()) {
            return null;
        }
        return tableModel.rows.get(modelRow);
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
                    setVisualRefreshStatus("Last refreshed just now.");
                    restartVisualRefreshTimers();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    pipelinePhaseGraphPanel.showMessage("Load interrupted");
                    setVisualRefreshStatus("Visual refresh interrupted.");
                    setVisualRefreshProgressState(false, 0, "Refresh interrupted");
                } catch (ExecutionException e) {
                    pipelinePhaseGraphPanel.showMessage("Failed to load phases");
                    setVisualRefreshStatus("Failed to refresh selected pipeline.");
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

    private void setVisualRefreshStatus(String message) {
        if (visualRefreshStatusLabel != null) {
            visualRefreshStatusLabel.setText(message);
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

    private static class PipelinePhaseGraphPanel extends JPanel {
        private static final int MAX_VISIBLE_ACTIONS = 5;

        PipelinePhaseGraphPanel() {
            setLayout(new BorderLayout());
            setBackground(UITheme.surfaceBackground());
            showEmpty();
        }

        void showEmpty() {
            showMessage("Select a pipeline to view phase states.");
        }

        void showLoading() {
            showMessage("Loading phase states...");
        }

        void showMessage(String message) {
            removeAll();
            JLabel label = new JLabel(message);
            label.setForeground(UIManager.getColor("Label.disabledForeground"));

            JPanel messagePanel = new JPanel(new GridBagLayout());
            messagePanel.setOpaque(false);
            messagePanel.add(label);

            add(messagePanel, BorderLayout.CENTER);
            revalidate();
            repaint();
        }

        void setPhases(List<CodePipelineService.PhaseState> phases) {
            if (phases == null || phases.isEmpty()) {
                showMessage("No phases available.");
                return;
            }

            removeAll();

            JPanel rail = new JPanel();
            rail.setOpaque(false);
            rail.setLayout(new BoxLayout(rail, BoxLayout.X_AXIS));
            rail.setBorder(BorderFactory.createEmptyBorder(UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM));

            for (int i = 0; i < phases.size(); i++) {
                CodePipelineService.PhaseState phase = phases.get(i);
                rail.add(createPhaseCard(phase));
                if (i < phases.size() - 1) {
                    rail.add(Box.createHorizontalStrut(UITheme.SPACING_SM));
                    rail.add(createConnectorLabel());
                    rail.add(Box.createHorizontalStrut(UITheme.SPACING_SM));
                }
            }

            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setOpaque(false);
            wrapper.add(rail, BorderLayout.WEST);

            add(wrapper, BorderLayout.NORTH);
            revalidate();
            repaint();
        }

        private JComponent createConnectorLabel() {
            JLabel label = new JLabel("→");
            label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
            label.setForeground(UIManager.getColor("Label.disabledForeground"));

            JPanel panel = new JPanel(new GridBagLayout());
            panel.setOpaque(false);
            panel.add(label);
            return panel;
        }

        private JComponent createPhaseCard(CodePipelineService.PhaseState phase) {
            JPanel card = new JPanel(new BorderLayout(UITheme.SPACING_SM, UITheme.SPACING_SM));
            card.setOpaque(true);
            card.setBackground(UITheme.surfaceBackground());
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(198, 205, 214)),
                BorderFactory.createEmptyBorder(UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM)
            ));
            card.setPreferredSize(new Dimension(220, 190));
            card.setMinimumSize(new Dimension(220, 190));

            JLabel title = new JLabel(phase.name());
            title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));

            JLabel statusBadge = createStatusBadge(phase.status());

            JPanel header = new JPanel(new BorderLayout(UITheme.SPACING_SM, 0));
            header.setOpaque(false);
            header.add(title, BorderLayout.WEST);
            header.add(statusBadge, BorderLayout.EAST);

            JPanel actionsPanel = new JPanel();
            actionsPanel.setOpaque(false);
            actionsPanel.setLayout(new BoxLayout(actionsPanel, BoxLayout.Y_AXIS));

            List<CodePipelineService.PhaseActionState> actions = phase.actions();
            if (actions == null || actions.isEmpty()) {
                JLabel empty = new JLabel("No actions");
                empty.setForeground(UIManager.getColor("Label.disabledForeground"));
                actionsPanel.add(empty);
            } else {
                int visible = Math.min(actions.size(), MAX_VISIBLE_ACTIONS);
                for (int i = 0; i < visible; i++) {
                    actionsPanel.add(createActionRow(actions.get(i)));
                    if (i < visible - 1) {
                        actionsPanel.add(Box.createVerticalStrut(4));
                    }
                }
                if (actions.size() > visible) {
                    actionsPanel.add(Box.createVerticalStrut(4));
                    JLabel more = new JLabel("+" + (actions.size() - visible) + " more");
                    more.setForeground(UIManager.getColor("Label.disabledForeground"));
                    actionsPanel.add(more);
                }
            }

            card.add(header, BorderLayout.NORTH);
            card.add(actionsPanel, BorderLayout.CENTER);
            return card;
        }

        private JComponent createActionRow(CodePipelineService.PhaseActionState action) {
            JPanel row = new JPanel(new BorderLayout(UITheme.SPACING_SM, 0));
            row.setOpaque(false);

            JLabel bullet = new JLabel("●");
            bullet.setForeground(colorForStatus(action.status()));
            bullet.setFont(bullet.getFont().deriveFont(10f));

            JLabel name = new JLabel(truncate(action.name(), 24));
            name.setToolTipText(action.name());

            JLabel status = new JLabel(shortStatus(action.status()));
            status.setForeground(colorForStatus(action.status()));

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            left.setOpaque(false);
            left.add(bullet);
            left.add(Box.createHorizontalStrut(6));
            left.add(name);

            row.add(left, BorderLayout.WEST);
            row.add(status, BorderLayout.EAST);
            return row;
        }

        private JLabel createStatusBadge(String status) {
            JLabel label = new JLabel(" " + shortStatus(status) + " ");
            label.setOpaque(true);
            label.setForeground(Color.WHITE);
            label.setBackground(colorForStatus(status));
            label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
            return label;
        }

        private String shortStatus(String status) {
            if (status == null || status.isBlank() || "-".equals(status)) {
                return "N/A";
            }
            if (status.contains("InProgress")) {
                return "Running";
            }
            if (status.contains("Succeeded")) {
                return "OK";
            }
            if (status.contains("Failed")) {
                return "Failed";
            }
            if (status.contains("Stopping")) {
                return "Stopping";
            }
            if (status.contains("Stopped")) {
                return "Stopped";
            }
            if (status.contains("Cancelled")) {
                return "Cancelled";
            }
            return status;
        }

        private Color colorForStatus(String status) {
            if (status == null || status.isBlank() || "-".equals(status)) {
                return new Color(120, 120, 120);
            }
            if (status.contains("Succeeded")) {
                return new Color(52, 153, 92);
            }
            if (status.contains("Failed")) {
                return new Color(192, 57, 57);
            }
            if (status.contains("InProgress")) {
                return new Color(52, 110, 200);
            }
            if (status.contains("Stopping") || status.contains("Stopped")) {
                return new Color(193, 132, 39);
            }
            if (status.contains("Cancelled")) {
                return new Color(110, 110, 110);
            }
            return new Color(86, 101, 115);
        }

        private String truncate(String text, int max) {
            if (text == null || text.length() <= max) {
                return text;
            }
            return text.substring(0, Math.max(0, max - 1)) + "…";
        }
    }

    // -------------------------------------------------------------------------
    // Pipeline actions
    // -------------------------------------------------------------------------

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

    private void handleView(CodePipelineService.PipelineRow row) {
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

    // -------------------------------------------------------------------------
    // Table model
    // -------------------------------------------------------------------------

    private class PipelineTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {"Name", "Last 5 Runs", "Context", "Last Deployed", "Actions"};
        private List<CodePipelineService.PipelineRow> rows = new ArrayList<>();

        void setRows(List<CodePipelineService.PipelineRow> newRows) {
            this.rows = new ArrayList<>(newRows);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMN_NAMES.length; }
        @Override public String getColumnName(int col) { return COLUMN_NAMES[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            CodePipelineService.PipelineRow r = rows.get(row);
            return switch (col) {
                case COL_NAME -> r.name();
                case COL_HISTORY -> r.recentStatuses();
                case COL_CONTEXT -> r.region() + "  ·  " + r.status();
                case COL_LAST_DEPLOYED -> r.lastDeployedAt();
                case COL_ACTIONS -> r;
                default -> null;
            };
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == COL_ACTIONS;
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return switch (col) {
                case COL_HISTORY -> List.class;
                case COL_ACTIONS -> CodePipelineService.PipelineRow.class;
                default -> String.class;
            };
        }
    }

    // -------------------------------------------------------------------------
    // Renderers + Editor for the History, Context, and Actions columns
    // -------------------------------------------------------------------------

    private static class HistoryCellRenderer extends JPanel implements TableCellRenderer {
        private static final int SLOTS = 5;
        private static final String SYMBOL_SUCCESS     = "✓";
        private static final String SYMBOL_FAILURE     = "✗";
        private static final String SYMBOL_IN_PROGRESS = "●";
        private static final String SYMBOL_NONE        = "·";
        private static final Color COLOR_SUCCESS     = new Color(76, 153, 76);
        private static final Color COLOR_FAILURE     = new Color(192, 57, 57);
        private static final Color COLOR_IN_PROGRESS = new Color(52, 110, 200);

        private final JLabel[] slots = new JLabel[SLOTS];

        HistoryCellRenderer() {
            setLayout(new GridLayout(1, SLOTS, 4, 0));
            setOpaque(true);
            for (int i = 0; i < SLOTS; i++) {
                slots[i] = new JLabel();
                slots[i].setFont(slots[i].getFont().deriveFont(Font.BOLD, 14f));
                slots[i].setHorizontalAlignment(SwingConstants.CENTER);
                slots[i].setVerticalAlignment(SwingConstants.CENTER);
                add(slots[i]);
            }
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int col) {
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, UIManager.getColor("Component.borderColor")));

            @SuppressWarnings("unchecked")
            List<String> statuses = (value instanceof List<?> l)
                ? (List<String>) l : List.of();

            for (int i = 0; i < SLOTS; i++) {
                if (i < statuses.size()) {
                    String s = statuses.get(i);
                    if (s.contains("Succeeded")) {
                        slots[i].setText(SYMBOL_SUCCESS);
                        slots[i].setForeground(isSelected ? table.getSelectionForeground() : COLOR_SUCCESS);
                    } else if (s.contains("Failed")) {
                        slots[i].setText(SYMBOL_FAILURE);
                        slots[i].setForeground(isSelected ? table.getSelectionForeground() : COLOR_FAILURE);
                    } else if (s.contains("InProgress") || s.contains("Stopping")) {
                        slots[i].setText(SYMBOL_IN_PROGRESS);
                        slots[i].setForeground(isSelected ? table.getSelectionForeground() : COLOR_IN_PROGRESS);
                    } else {
                        slots[i].setText(SYMBOL_NONE);
                        slots[i].setForeground(UIManager.getColor("Label.disabledForeground"));
                    }
                } else {
                    slots[i].setText(SYMBOL_NONE);
                    slots[i].setForeground(UIManager.getColor("Label.disabledForeground"));
                }
            }
            return this;
        }
    }

    private static class ContextCellRenderer extends DefaultTableCellRenderer {
        private static final Color COLOR_SUCCEEDED   = new Color(76, 153, 76);
        private static final Color COLOR_FAILED      = new Color(192, 57, 57);
        private static final Color COLOR_IN_PROGRESS = new Color(52, 110, 200);
        private static final Color COLOR_STOPPING    = new Color(200, 140, 40);

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (!isSelected && value instanceof String text) {
                if (text.contains("Succeeded"))  setForeground(COLOR_SUCCEEDED);
                else if (text.contains("Failed"))     setForeground(COLOR_FAILED);
                else if (text.contains("InProgress")) setForeground(COLOR_IN_PROGRESS);
                else if (text.contains("Stopping") || text.contains("Stopped")) setForeground(COLOR_STOPPING);
                else setForeground(table.getForeground());
            }
            return this;
        }
    }

    private class ActionsRenderer extends JPanel implements TableCellRenderer {
        private final JButton viewBtn  = new JButton("View");
        private final JButton startBtn = new JButton("▶  Start");
        private final JButton stopBtn  = new JButton("⏹  Stop");

        ActionsRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, UITheme.SPACING_SM, 4));
            setOpaque(true);
            viewBtn.setFont(viewBtn.getFont().deriveFont(UITheme.FONT_SIZE_SM));
            startBtn.setFont(startBtn.getFont().deriveFont(UITheme.FONT_SIZE_SM));
            stopBtn.setFont(stopBtn.getFont().deriveFont(UITheme.FONT_SIZE_SM));
            add(viewBtn);
            add(startBtn);
            add(stopBtn);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int col) {
            if (value instanceof CodePipelineService.PipelineRow r) {
                stopBtn.setEnabled(r.inProgressExecutionId() != null);
            }
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return this;
        }
    }

    private class ActionsEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, UITheme.SPACING_SM, 4));
        private final JButton viewBtn  = new JButton("View");
        private final JButton startBtn = new JButton("▶  Start");
        private final JButton stopBtn  = new JButton("⏹  Stop");
        private CodePipelineService.PipelineRow currentRow;

        ActionsEditor() {
            viewBtn.setFont(viewBtn.getFont().deriveFont(UITheme.FONT_SIZE_SM));
            startBtn.setFont(startBtn.getFont().deriveFont(UITheme.FONT_SIZE_SM));
            stopBtn.setFont(stopBtn.getFont().deriveFont(UITheme.FONT_SIZE_SM));

            viewBtn.addActionListener(e -> {
                fireEditingStopped();
                handleView(currentRow);
            });
            startBtn.addActionListener(e -> {
                fireEditingStopped();
                handleStart(currentRow);
            });
            stopBtn.addActionListener(e -> {
                fireEditingStopped();
                handleStop(currentRow);
            });

            panel.add(viewBtn);
            panel.add(startBtn);
            panel.add(stopBtn);
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable table, Object value, boolean isSelected, int row, int col) {
            if (value instanceof CodePipelineService.PipelineRow r) {
                currentRow = r;
                stopBtn.setEnabled(r.inProgressExecutionId() != null);
            }
            panel.setBackground(table.getSelectionBackground());
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return currentRow;
        }
    }
}
