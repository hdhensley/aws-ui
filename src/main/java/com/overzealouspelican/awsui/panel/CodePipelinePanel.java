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

    private final CodePipelineService service;
    private final SettingsService settingsService;

    private String currentProfile;
    private volatile boolean isLoading = false;

    private PipelineTableModel tableModel;
    private TableRowSorter<PipelineTableModel> rowSorter;
    private JTable pipelineTable;
    private JTextArea pipelineDetailsArea;
    private JTextField pipelineSearchField;
    private JLabel errorLabel;
    private JLabel statusLabel;
    private CardLayout stateLayout;
    private JPanel stateContainer;
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
        stateContainer.add(createTablePanel(), STATE_TABLE);
        add(stateContainer, BorderLayout.CENTER);

        stateLayout.show(stateContainer, STATE_LOADING);
    }

    private JComponent createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.surfaceBackground());
        header.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")),
            javax.swing.BorderFactory.createEmptyBorder(UITheme.SPACING_SM, UITheme.SPACING_LG, UITheme.SPACING_SM, UITheme.SPACING_LG)
        ));

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);

        JLabel title = new JLabel("CodePipelines");
        title.setFont(title.getFont().deriveFont(Font.BOLD, UITheme.FONT_SIZE_TITLE));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UITheme.SPACING_SM, 0));
        searchPanel.setOpaque(false);
        JLabel searchLabel = new JLabel("Search:");
        pipelineSearchField = new JTextField(24);
        JButton clearSearchButton = new JButton("Clear");
        pipelineSearchField.addActionListener(e -> applyPipelineNameFilter());
        clearSearchButton.addActionListener(e -> {
            pipelineSearchField.setText("");
            applyPipelineNameFilter();
        });
        searchPanel.add(searchLabel);
        searchPanel.add(pipelineSearchField);
        searchPanel.add(clearSearchButton);

        leftPanel.add(title);
        leftPanel.add(Box.createVerticalStrut(UITheme.SPACING_SM));
        leftPanel.add(searchPanel);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UITheme.SPACING_SM, 0));
        rightPanel.setOpaque(false);

        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, UITheme.FONT_SIZE_SM));
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadPipelines());

        rightPanel.add(statusLabel);
        rightPanel.add(refreshButton);

        header.add(leftPanel, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);
        return header;
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

        pipelineDetailsArea = new JTextArea();
        pipelineDetailsArea.setEditable(false);
        pipelineDetailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        pipelineDetailsArea.setRows(8);

        JPanel detailsPanel = new JPanel(new BorderLayout());
        detailsPanel.setBackground(UITheme.surfaceBackground());
        detailsPanel.setBorder(BorderFactory.createTitledBorder("Pipeline Details"));
        detailsPanel.add(new JScrollPane(pipelineDetailsArea), BorderLayout.CENTER);

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
        if (currentProfile == null || currentProfile.isBlank()) {
            errorLabel.setText("No AWS profile selected. Choose one on the Home page.");
            stateLayout.show(stateContainer, STATE_ERROR);
            statusLabel.setText("");
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
                    if (pipelineDetailsArea != null) {
                        pipelineDetailsArea.setText("");
                    }
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
        if (pipelineDetailsArea == null || pipelineTable == null) {
            return;
        }

        int selectedRow = pipelineTable.getSelectedRow();
        if (selectedRow < 0) {
            detailsRequestVersion++;
            pipelineDetailsArea.setText("");
            return;
        }

        int modelRow = pipelineTable.convertRowIndexToModel(selectedRow);
        CodePipelineService.PipelineRow row = tableModel.rows.get(modelRow);

        int requestVersion = ++detailsRequestVersion;
        pipelineDetailsArea.setText(buildPipelineDetailsText(row, "Loading..."));
        loadPipelinePhases(row, requestVersion);
    }

    private void loadPipelinePhases(CodePipelineService.PipelineRow row, int requestVersion) {
        String profile = currentProfile;
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return service.getPipelinePhases(profile, row.name());
            }

            @Override
            protected void done() {
                if (requestVersion != detailsRequestVersion) {
                    return;
                }

                try {
                    List<String> phases = get();
                    String phasesText = phases.isEmpty()
                        ? "-"
                        : String.join(System.lineSeparator() + "  ", phases);
                    pipelineDetailsArea.setText(buildPipelineDetailsText(row, phasesText));
                    pipelineDetailsArea.setCaretPosition(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    pipelineDetailsArea.setText(buildPipelineDetailsText(row, "Load interrupted"));
                } catch (ExecutionException e) {
                    pipelineDetailsArea.setText(buildPipelineDetailsText(row, "Failed to load phases"));
                }
            }
        }.execute();
    }

    private String buildPipelineDetailsText(CodePipelineService.PipelineRow row, String phasesText) {
        String recentRuns = row.recentStatuses().isEmpty()
            ? "-"
            : String.join(", ", row.recentStatuses());

        return "Name: " + row.name() + System.lineSeparator()
            + "Region: " + row.region() + System.lineSeparator()
            + "Current Status: " + row.status() + System.lineSeparator()
            + "Last Deployed: " + row.lastDeployedAt() + System.lineSeparator()
            + "Current Execution ID: " + (row.inProgressExecutionId() == null ? "-" : row.inProgressExecutionId()) + System.lineSeparator()
            + "Recent Runs (newest first): " + recentRuns + System.lineSeparator()
            + "Phases:" + System.lineSeparator()
            + "  " + phasesText;
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
