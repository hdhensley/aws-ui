package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.EcsService;
import com.overzealouspelican.awsui.service.SettingsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * ECS viewer with cluster -> services drill-down and force deployment action.
 */
public class EcsPanel extends JPanel {

    private static final String VIEW_CLUSTERS = "clusters";
    private static final String VIEW_SERVICES = "services";

    private final EcsService ecsService;
    private final SettingsService settingsService;

    private String currentProfile;
    private String selectedClusterArn;
    private String selectedClusterName;

    private CardLayout contentLayout;
    private JPanel contentPanel;
    private JLabel statusLabel;

    private JTextField clusterSearchField;
    private JTable clustersTable;
    private ClusterTableModel clusterTableModel;

    private JLabel selectedClusterLabel;
    private JTextField serviceSearchField;
    private JTable servicesTable;
    private ServiceTableModel serviceTableModel;
    private JTextArea serviceDetailsArea;
    private JButton forceDeployButton;

    public EcsPanel(EcsService ecsService, SettingsService settingsService) {
        this.ecsService = ecsService;
        this.settingsService = settingsService;
        this.currentProfile = settingsService.getSavedAwsProfile();
        initializePanel();
    }

    public void setProfile(String profile) {
        this.currentProfile = profile;
        selectedClusterArn = null;
        selectedClusterName = null;
        clearDisplayedData();
        if (clusterSearchField != null) {
            clusterSearchField.setText("");
        }
        if (serviceSearchField != null) {
            serviceSearchField.setText("");
        }
        if (selectedClusterLabel != null) {
            selectedClusterLabel.setText("Cluster: -");
        }
        setStatus("Switching profile...");
        showClustersView();
        refreshClusters();
    }

    public void refresh() {
        if (selectedClusterArn == null) {
            refreshClusters();
        } else {
            refreshServices();
        }
    }

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBackground(UITheme.panelBackground());

        add(createHeader(), BorderLayout.NORTH);

        contentLayout = new CardLayout();
        contentPanel = new JPanel(contentLayout);
        contentPanel.add(createClustersView(), VIEW_CLUSTERS);
        contentPanel.add(createServicesView(), VIEW_SERVICES);
        add(contentPanel, BorderLayout.CENTER);

        showClustersView();
        refreshClusters();
    }

    private JComponent createHeader() {
        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, UITheme.FONT_SIZE_SM));
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel trailingPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UITheme.SPACING_SM, 0));
        trailingPanel.add(statusLabel);
        return UITheme.createPageHeader("ECS", trailingPanel);
    }

    private JComponent createClustersView() {
        JPanel panel = new JPanel(new BorderLayout(UITheme.SPACING_SM, UITheme.SPACING_SM));
        panel.setBorder(UITheme.contentPadding());
        panel.setBackground(UITheme.panelBackground());

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, UITheme.SPACING_SM, 0));
        filterBar.setOpaque(false);
        filterBar.add(new JLabel("Search clusters:"));
        clusterSearchField = new JTextField(28);
        filterBar.add(clusterSearchField);
        JButton searchButton = new JButton("Find");
        JButton openButton = new JButton("Open Cluster");
        filterBar.add(searchButton);
        filterBar.add(openButton);

        clusterTableModel = new ClusterTableModel();
        clustersTable = new JTable(clusterTableModel);
        clustersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        clustersTable.setFillsViewportHeight(true);
        clustersTable.setRowHeight(30);
        clustersTable.getColumnModel().getColumn(0).setCellRenderer(new LinkCellRenderer());
        clustersTable.getColumnModel().getColumn(2).setCellRenderer(new ClusterTasksRenderer());
        clustersTable.getColumnModel().getColumn(4).setCellRenderer(new StatusCellRenderer());
        clustersTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = clustersTable.rowAtPoint(e.getPoint());
                int col = clustersTable.columnAtPoint(e.getPoint());
                if (row >= 0 && col == 0 && e.getClickCount() >= 1) {
                    clustersTable.setRowSelectionInterval(row, row);
                    if (e.getClickCount() >= 2) {
                        openSelectedCluster();
                    }
                }
            }
        });

        searchButton.addActionListener(e -> refreshClusters());
        clusterSearchField.addActionListener(e -> refreshClusters());
        openButton.addActionListener(e -> openSelectedCluster());

        panel.add(filterBar, BorderLayout.NORTH);
        panel.add(new JScrollPane(clustersTable), BorderLayout.CENTER);
        return panel;
    }

    private JComponent createServicesView() {
        JPanel panel = new JPanel(new BorderLayout(UITheme.SPACING_SM, UITheme.SPACING_SM));
        panel.setBorder(UITheme.contentPadding());
        panel.setBackground(UITheme.panelBackground());

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, UITheme.SPACING_SM, 0));
        topBar.setOpaque(false);

        JButton backButton = new JButton("Back to Clusters");
        backButton.addActionListener(e -> showClustersView());

        selectedClusterLabel = new JLabel("Cluster: -");
        selectedClusterLabel.setFont(selectedClusterLabel.getFont().deriveFont(Font.BOLD, UITheme.FONT_SIZE_MD));

        topBar.add(backButton);
        topBar.add(Box.createHorizontalStrut(UITheme.SPACING_SM));
        topBar.add(selectedClusterLabel);

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, UITheme.SPACING_SM, 0));
        filterBar.setOpaque(false);
        filterBar.add(new JLabel("Search services:"));
        serviceSearchField = new JTextField(26);
        filterBar.add(serviceSearchField);
        JButton searchButton = new JButton("Find");
        searchButton.addActionListener(e -> refreshServices());
        serviceSearchField.addActionListener(e -> refreshServices());
        filterBar.add(searchButton);

        serviceTableModel = new ServiceTableModel();
        servicesTable = new JTable(serviceTableModel);
        servicesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        servicesTable.setFillsViewportHeight(true);
        servicesTable.setRowHeight(30);
        servicesTable.getColumnModel().getColumn(0).setCellRenderer(new LinkCellRenderer());
        servicesTable.getColumnModel().getColumn(1).setCellRenderer(new StatusCellRenderer());
        servicesTable.getColumnModel().getColumn(4).setCellRenderer(new LinkCellRenderer());
        servicesTable.getColumnModel().getColumn(5).setCellRenderer(new DeploymentStatusRenderer());
        servicesTable.getColumnModel().getColumn(6).setCellRenderer(new ServiceTasksRenderer());
        servicesTable.getSelectionModel().addListSelectionListener(e -> updateServiceDetails());
        servicesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = servicesTable.rowAtPoint(e.getPoint());
                int col = servicesTable.columnAtPoint(e.getPoint());
                if (row < 0 || col < 0) {
                    return;
                }

                servicesTable.setRowSelectionInterval(row, row);
                int modelRow = servicesTable.convertRowIndexToModel(row);
                EcsService.ServiceRow serviceRow = serviceTableModel.getRow(modelRow);

                if (col == 4) {
                    copyToClipboard(serviceRow.taskDefinition());
                    setStatus("Copied task definition: " + serviceRow.taskDefinition());
                } else if (col == 0 && e.getClickCount() >= 1) {
                    updateServiceDetails();
                }
            }
        });

        serviceDetailsArea = new JTextArea();
        serviceDetailsArea.setEditable(false);
        serviceDetailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        serviceDetailsArea.setRows(8);

        forceDeployButton = new JButton("Force New Deployment");
        UITheme.stylePrimaryButton(forceDeployButton);
        forceDeployButton.setEnabled(false);
        forceDeployButton.addActionListener(e -> triggerForceDeployment());

        JPanel bottomPanel = new JPanel(new BorderLayout(UITheme.SPACING_SM, UITheme.SPACING_SM));
        bottomPanel.setOpaque(false);
        bottomPanel.add(new JScrollPane(serviceDetailsArea), BorderLayout.CENTER);
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UITheme.SPACING_SM, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(forceDeployButton);
        bottomPanel.add(actionPanel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(servicesTable),
            bottomPanel);
        split.setResizeWeight(0.68);
        split.setDividerSize(8);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        topBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(topBar);
        north.add(Box.createVerticalStrut(UITheme.SPACING_SM));
        north.add(filterBar);

        panel.add(north, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private void refreshClusters() {
        if (currentProfile == null || currentProfile.isBlank()) {
            statusLabel.setText("No profile selected");
            clusterTableModel.setRows(List.of());
            if (serviceTableModel != null) {
                serviceTableModel.setRows(List.of());
            }
            return;
        }

        clearDisplayedData();
        setStatus("Loading clusters...");
        String query = clusterSearchField == null ? "" : clusterSearchField.getText();

        new SwingWorker<List<EcsService.ClusterRow>, Void>() {
            @Override
            protected List<EcsService.ClusterRow> doInBackground() {
                return ecsService.listClusters(currentProfile, query);
            }

            @Override
            protected void done() {
                try {
                    List<EcsService.ClusterRow> rows = get();
                    clusterTableModel.setRows(rows);
                    setStatus("Clusters: " + rows.size());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setStatus("Interrupted while loading clusters");
                } catch (ExecutionException ex) {
                    setStatus("Failed to load clusters");
                    showError(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                }
            }
        }.execute();
    }

    private void openSelectedCluster() {
        int selectedRow = clustersTable.getSelectedRow();
        if (selectedRow < 0) {
            showError("Select a cluster first.");
            return;
        }

        EcsService.ClusterRow row = clusterTableModel.getRow(clustersTable.convertRowIndexToModel(selectedRow));
        selectedClusterArn = row.arn();
        selectedClusterName = row.name();
        selectedClusterLabel.setText("Cluster: " + selectedClusterName);
        showServicesView();
        refreshServices();
    }

    private void refreshServices() {
        if (currentProfile == null || currentProfile.isBlank()) {
            setStatus("No profile selected");
            return;
        }
        if (selectedClusterArn == null || selectedClusterArn.isBlank()) {
            setStatus("Select a cluster first");
            return;
        }

        if (serviceTableModel != null) {
            serviceTableModel.setRows(List.of());
        }
        if (serviceDetailsArea != null) {
            serviceDetailsArea.setText("");
        }
        if (forceDeployButton != null) {
            forceDeployButton.setEnabled(false);
        }
        setStatus("Loading services...");
        String query = serviceSearchField == null ? "" : serviceSearchField.getText();

        new SwingWorker<List<EcsService.ServiceRow>, Void>() {
            @Override
            protected List<EcsService.ServiceRow> doInBackground() {
                return ecsService.listServices(currentProfile, selectedClusterArn, query);
            }

            @Override
            protected void done() {
                try {
                    List<EcsService.ServiceRow> rows = get();
                    serviceTableModel.setRows(rows);
                    serviceDetailsArea.setText("");
                    forceDeployButton.setEnabled(false);
                    setStatus("Services: " + rows.size());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setStatus("Interrupted while loading services");
                } catch (ExecutionException ex) {
                    setStatus("Failed to load services");
                    showError(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                }
            }
        }.execute();
    }

    private void updateServiceDetails() {
        int selectedRow = servicesTable.getSelectedRow();
        if (selectedRow < 0) {
            serviceDetailsArea.setText("");
            forceDeployButton.setEnabled(false);
            return;
        }

        EcsService.ServiceRow row = serviceTableModel.getRow(servicesTable.convertRowIndexToModel(selectedRow));
        forceDeployButton.setEnabled(true);

        String details = "Service: " + row.name() + System.lineSeparator()
            + "ARN: " + row.arn() + System.lineSeparator()
            + "Status: " + row.status() + System.lineSeparator()
            + "Launch Type: " + row.launchType() + System.lineSeparator()
            + "Scheduling: " + row.schedulingStrategy() + System.lineSeparator()
            + "Task Definition: " + row.taskDefinition() + System.lineSeparator()
            + "Deployments: " + row.deploymentStatus() + System.lineSeparator()
            + "Last Deployment: " + row.lastDeploymentAt() + System.lineSeparator()
            + "Tasks: desired=" + row.desiredCount() + ", running=" + row.runningCount() + ", pending=" + row.pendingCount() + System.lineSeparator()
            + "Created At: " + row.createdAt();
        serviceDetailsArea.setText(details);
        serviceDetailsArea.setCaretPosition(0);
    }

    private void triggerForceDeployment() {
        int selectedRow = servicesTable.getSelectedRow();
        if (selectedRow < 0) {
            showError("Select a service first.");
            return;
        }

        EcsService.ServiceRow row = serviceTableModel.getRow(servicesTable.convertRowIndexToModel(selectedRow));
        int choice = JOptionPane.showConfirmDialog(
            this,
            "Force new deployment for service '" + row.name() + "'?",
            "Confirm Deployment",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        setStatus("Triggering deployment...");
        forceDeployButton.setEnabled(false);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                ecsService.forceNewDeployment(currentProfile, selectedClusterArn, row.name());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    setStatus("Deployment triggered for " + row.name());
                    refreshServices();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setStatus("Interrupted while deploying");
                } catch (ExecutionException ex) {
                    setStatus("Failed to trigger deployment");
                    showError(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                    forceDeployButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void showClustersView() {
        contentLayout.show(contentPanel, VIEW_CLUSTERS);
    }

    private void showServicesView() {
        contentLayout.show(contentPanel, VIEW_SERVICES);
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void clearDisplayedData() {
        if (clusterTableModel != null) {
            clusterTableModel.setRows(List.of());
        }
        if (serviceTableModel != null) {
            serviceTableModel.setRows(List.of());
        }
        if (serviceDetailsArea != null) {
            serviceDetailsArea.setText("");
        }
        if (forceDeployButton != null) {
            forceDeployButton.setEnabled(false);
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "ECS", JOptionPane.ERROR_MESSAGE);
    }

    private void copyToClipboard(String value) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
    }

    private static class LinkCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setText(value == null ? "" : String.valueOf(value));
            setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return this;
        }
    }

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final Color GREEN = new Color(22, 135, 84);
        private static final Color ORANGE = new Color(198, 120, 35);

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String text = String.valueOf(value);
            if (!isSelected) {
                if ("ACTIVE".equalsIgnoreCase(text) || "COMPLETED".equalsIgnoreCase(text)) {
                    setForeground(GREEN);
                } else if (text.toUpperCase().contains("PROGRESS") || text.toUpperCase().contains("PRIMARY")) {
                    setForeground(ORANGE);
                } else {
                    setForeground(table.getForeground());
                }
            }
            return this;
        }
    }

    private static class DeploymentStatusRenderer extends StatusCellRenderer {
    }

    private static class ClusterTasksRenderer extends JPanel implements TableCellRenderer {
        private final JProgressBar progressBar = new JProgressBar();
        private final JLabel label = new JLabel();
        private final JPanel progressWrapper = new JPanel(new GridBagLayout());

        ClusterTasksRenderer() {
            setLayout(new BorderLayout(UITheme.SPACING_SM, 0));
            setOpaque(true);
            progressBar.setStringPainted(false);
            progressBar.setPreferredSize(new Dimension(140, 8));
            progressBar.setMinimumSize(new Dimension(80, 8));
            progressWrapper.setOpaque(false);
            progressWrapper.add(progressBar);
            add(progressWrapper, BorderLayout.CENTER);
            add(label, BorderLayout.EAST);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            ClusterTableModel model = (ClusterTableModel) table.getModel();
            EcsService.ClusterRow cluster = model.getRow(table.convertRowIndexToModel(row));
            int total = cluster.runningTasks() + cluster.pendingTasks();
            progressBar.setMaximum(Math.max(total, 1));
            progressBar.setValue(cluster.runningTasks());
            progressBar.setForeground(new Color(34, 148, 83));
            label.setText(cluster.pendingTasks() + " Pending | " + cluster.runningTasks() + " Running");
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            progressWrapper.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            label.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            return this;
        }
    }

    private static class ServiceTasksRenderer extends JPanel implements TableCellRenderer {
        private final JProgressBar progressBar = new JProgressBar();
        private final JLabel label = new JLabel();
        private final JPanel progressWrapper = new JPanel(new GridBagLayout());

        ServiceTasksRenderer() {
            setLayout(new BorderLayout(UITheme.SPACING_SM, 0));
            setOpaque(true);
            progressBar.setStringPainted(false);
            progressBar.setPreferredSize(new Dimension(140, 8));
            progressBar.setMinimumSize(new Dimension(80, 8));
            progressWrapper.setOpaque(false);
            progressWrapper.add(progressBar);
            add(progressWrapper, BorderLayout.CENTER);
            add(label, BorderLayout.EAST);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            ServiceTableModel model = (ServiceTableModel) table.getModel();
            EcsService.ServiceRow service = model.getRow(table.convertRowIndexToModel(row));
            int desired = Math.max(service.desiredCount(), 1);
            progressBar.setMaximum(desired);
            progressBar.setValue(Math.min(service.runningCount(), desired));
            progressBar.setForeground(new Color(34, 148, 83));
            label.setText(service.runningCount() + "/" + service.desiredCount() + " Tasks running");
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            progressWrapper.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            label.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            return this;
        }
    }

    private static class ClusterTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
            "Cluster", "Services", "Tasks", "Container instances", "Status"
        };
        private List<EcsService.ClusterRow> rows = new ArrayList<>();

        void setRows(List<EcsService.ClusterRow> newRows) {
            rows = new ArrayList<>(newRows);
            fireTableDataChanged();
        }

        EcsService.ClusterRow getRow(int modelRow) {
            return rows.get(modelRow);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            EcsService.ClusterRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.name();
                case 1 -> row.activeServices();
                case 2 -> row.pendingTasks() + " Pending | " + row.runningTasks() + " Running";
                case 3 -> row.containerInstances();
                case 4 -> row.status();
                default -> "";
            };
        }
    }

    private static class ServiceTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
            "Service", "Status", "Scheduling", "Launch", "Task definition",
            "Deployments", "Tasks", "Created"
        };
        private List<EcsService.ServiceRow> rows = new ArrayList<>();

        void setRows(List<EcsService.ServiceRow> newRows) {
            rows = new ArrayList<>(newRows);
            fireTableDataChanged();
        }

        EcsService.ServiceRow getRow(int modelRow) {
            return rows.get(modelRow);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            EcsService.ServiceRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.name();
                case 1 -> row.status();
                case 2 -> row.schedulingStrategy();
                case 3 -> row.launchType();
                case 4 -> row.taskDefinition();
                case 5 -> row.deploymentStatus();
                case 6 -> row.runningCount() + "/" + row.desiredCount() + " Running";
                case 7 -> row.createdAt();
                default -> "";
            };
        }
    }
}
