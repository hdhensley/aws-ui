package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.EcsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

final class EcsServiceTasksRenderer extends JPanel implements TableCellRenderer {
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel label = new JLabel();
    private final JPanel progressWrapper = new JPanel(new GridBagLayout());

    EcsServiceTasksRenderer() {
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
        EcsServiceTableModel model = (EcsServiceTableModel) table.getModel();
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