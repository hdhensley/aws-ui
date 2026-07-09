package com.overzealouspelican.awsui.panel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

final class EcsLinkCellRenderer extends DefaultTableCellRenderer {
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