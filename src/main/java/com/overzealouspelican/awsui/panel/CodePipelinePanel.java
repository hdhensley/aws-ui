package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CodePipelineService;
import com.overzealouspelican.awsui.service.SettingsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private JLabel errorLabel;
    private JLabel statusLabel;
    private CardLayout stateLayout;
    private JPanel stateContainer;

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
        setBackground(UIManager.getColor("Panel.background"));

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
        header.setBackground(UIManager.getColor("Panel.background"));
        header.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")),
            javax.swing.BorderFactory.createEmptyBorder(UITheme.SPACING_SM, UITheme.SPACING_LG, UITheme.SPACING_SM, UITheme.SPACING_LG)
        ));

        JLabel title = new JLabel("CodePipelines");
        title.setFont(title.getFont().deriveFont(Font.BOLD, UITheme.FONT_SIZE_TITLE));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UITheme.SPACING_SM, 0));
        rightPanel.setOpaque(false);

        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, UITheme.FONT_SIZE_SM));
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadPipelines());

        rightPanel.add(statusLabel);
        rightPanel.add(refreshButton);

        header.add(title, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel createLoadingPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIManager.getColor("Panel.background"));
        JLabel label = new JLabel("Loading pipelines…");
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(label);
        return panel;
    }

    private JPanel createErrorPanel() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(UIManager.getColor("Panel.background"));

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

    private JScrollPane createTablePanel() {
        tableModel = new PipelineTableModel();
        JTable table = new JTable(tableModel);

        TableRowSorter<PipelineTableModel> rowSorter = new TableRowSorter<>(tableModel);
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
        table.setRowSorter(rowSorter);

        table.setRowHeight(40);
        table.setShowHorizontalLines(false);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.getColumnModel().getColumn(COL_NAME).setPreferredWidth(280);
        table.getColumnModel().getColumn(COL_HISTORY).setPreferredWidth(130);
        table.getColumnModel().getColumn(COL_HISTORY).setMaxWidth(150);
        table.getColumnModel().getColumn(COL_CONTEXT).setPreferredWidth(200);
        table.getColumnModel().getColumn(COL_LAST_DEPLOYED).setPreferredWidth(190);
        table.getColumnModel().getColumn(COL_LAST_DEPLOYED).setMaxWidth(220);
        table.getColumnModel().getColumn(COL_ACTIONS).setPreferredWidth(170);
        table.getColumnModel().getColumn(COL_ACTIONS).setMaxWidth(190);

        table.getColumnModel().getColumn(COL_HISTORY).setCellRenderer(new HistoryCellRenderer());
        table.getColumnModel().getColumn(COL_CONTEXT).setCellRenderer(new ContextCellRenderer());
        table.getColumnModel().getColumn(COL_ACTIONS).setCellRenderer(new ActionsRenderer());
        table.getColumnModel().getColumn(COL_ACTIONS).setCellEditor(new ActionsEditor());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(null);
        return scrollPane;
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
        private final JButton startBtn = new JButton("▶  Start");
        private final JButton stopBtn  = new JButton("⏹  Stop");

        ActionsRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, UITheme.SPACING_SM, 4));
            setOpaque(true);
            startBtn.setFont(startBtn.getFont().deriveFont(UITheme.FONT_SIZE_SM));
            stopBtn.setFont(stopBtn.getFont().deriveFont(UITheme.FONT_SIZE_SM));
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
        private final JButton startBtn = new JButton("▶  Start");
        private final JButton stopBtn  = new JButton("⏹  Stop");
        private CodePipelineService.PipelineRow currentRow;

        ActionsEditor() {
            startBtn.setFont(startBtn.getFont().deriveFont(UITheme.FONT_SIZE_SM));
            stopBtn.setFont(stopBtn.getFont().deriveFont(UITheme.FONT_SIZE_SM));

            startBtn.addActionListener(e -> {
                fireEditingStopped();
                handleStart(currentRow);
            });
            stopBtn.addActionListener(e -> {
                fireEditingStopped();
                handleStop(currentRow);
            });

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
