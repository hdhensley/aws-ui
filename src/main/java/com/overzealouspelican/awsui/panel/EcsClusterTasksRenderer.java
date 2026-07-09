package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.EcsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

final class EcsClusterTasksRenderer extends JPanel implements TableCellRenderer {
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel label = new JLabel();
    private final JPanel progressWrapper = new JPanel(new GridBagLayout());

    EcsClusterTasksRenderer() {
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
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        EcsClusterTableModel model = (EcsClusterTableModel) table.getModel();
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