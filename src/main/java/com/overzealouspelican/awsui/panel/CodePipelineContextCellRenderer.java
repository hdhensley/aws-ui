package com.overzealouspelican.awsui.panel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

final class CodePipelineContextCellRenderer extends DefaultTableCellRenderer {
    private static final Color COLOR_SUCCEEDED = new Color(76, 153, 76);
    private static final Color COLOR_FAILED = new Color(192, 57, 57);
    private static final Color COLOR_IN_PROGRESS = new Color(52, 110, 200);
    private static final Color COLOR_STOPPING = new Color(200, 140, 40);

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected,
        boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (!isSelected && value instanceof String text) {
            if (text.contains("Succeeded")) {
                setForeground(COLOR_SUCCEEDED);
            } else if (text.contains("Failed")) {
                setForeground(COLOR_FAILED);
            } else if (text.contains("InProgress")) {
                setForeground(COLOR_IN_PROGRESS);
            } else if (text.contains("Stopping") || text.contains("Stopped")) {
                setForeground(COLOR_STOPPING);
            } else {
                setForeground(table.getForeground());
            }
        }
        return this;
    }
}