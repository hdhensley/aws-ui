package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.EcsService;
import com.overzealouspelican.awsui.service.SettingsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private EcsClusterTableModel clusterTableModel;

    private JLabel selectedClusterLabel;
    private JTextField serviceSearchField;
    private JTable servicesTable;
    private EcsServiceTableModel serviceTableModel;
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

        clusterTableModel = new EcsClusterTableModel();
        clustersTable = new JTable(clusterTableModel);
        clustersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        clustersTable.setFillsViewportHeight(true);
        clustersTable.setRowHeight(30);
        clustersTable.getColumnModel().getColumn(0).setCellRenderer(new EcsLinkCellRenderer());
        clustersTable.getColumnModel().getColumn(2).setCellRenderer(new EcsClusterTasksRenderer());
        clustersTable.getColumnModel().getColumn(4).setCellRenderer(new EcsStatusCellRenderer());
        installClusterTableInteraction();

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

        serviceTableModel = new EcsServiceTableModel();
        servicesTable = new JTable(serviceTableModel);
        servicesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        servicesTable.setFillsViewportHeight(true);
        servicesTable.setRowHeight(30);
        servicesTable.getColumnModel().getColumn(0).setCellRenderer(new EcsLinkCellRenderer());
        servicesTable.getColumnModel().getColumn(1).setCellRenderer(new EcsStatusCellRenderer());
        servicesTable.getColumnModel().getColumn(4).setCellRenderer(new EcsLinkCellRenderer());
        servicesTable.getColumnModel().getColumn(5).setCellRenderer(new EcsStatusCellRenderer());
        servicesTable.getColumnModel().getColumn(6).setCellRenderer(new EcsServiceTasksRenderer());
        servicesTable.getSelectionModel().addListSelectionListener(e -> updateServiceDetails());
        installServiceTableInteraction();

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

    private void installClusterTableInteraction() {
        clustersTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                handleClusterTableClick(event);
            }
        });
    }

    private void handleClusterTableClick(MouseEvent event) {
        int row = clustersTable.rowAtPoint(event.getPoint());
        int column = clustersTable.columnAtPoint(event.getPoint());
        if (row < 0 || column != 0) {
            return;
        }

        clustersTable.setRowSelectionInterval(row, row);
        if (event.getClickCount() >= 2) {
            openSelectedCluster();
        }
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

    private void installServiceTableInteraction() {
        servicesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                handleServiceTableClick(event);
            }
        });
    }

    private void handleServiceTableClick(MouseEvent event) {
        int row = servicesTable.rowAtPoint(event.getPoint());
        int column = servicesTable.columnAtPoint(event.getPoint());
        if (row < 0 || column < 0) {
            return;
        }

        servicesTable.setRowSelectionInterval(row, row);
        EcsService.ServiceRow serviceRow = serviceTableModel.getRow(servicesTable.convertRowIndexToModel(row));

        if (column == 4) {
            copyTaskDefinition(serviceRow);
            return;
        }

        if (column == 0 && event.getClickCount() >= 1) {
            updateServiceDetails();
        }
    }

    private void copyTaskDefinition(EcsService.ServiceRow serviceRow) {
        copyToClipboard(serviceRow.taskDefinition());
        setStatus("Copied task definition: " + serviceRow.taskDefinition());
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

        serviceDetailsArea.setText(EcsServiceDetailsFormatter.format(row));
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

}
