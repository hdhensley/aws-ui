package com.overzealouspelican.awsui.panel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

class EcsStatusCellRenderer extends DefaultTableCellRenderer {
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