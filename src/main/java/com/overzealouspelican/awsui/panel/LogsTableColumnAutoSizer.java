package com.overzealouspelican.awsui.panel;

import javax.swing.*;

final class LogsTableColumnAutoSizer {
    private LogsTableColumnAutoSizer() {
    }

    static void resizeColumns(
        JTable table,
        int maxRowsToScan,
        int minWidth,
        int maxWidth,
        int charWidthEstimate
    ) {
        for (int column = 0; column < table.getColumnCount(); column++) {
            int width = minWidth;
            for (int row = 0; row < Math.min(table.getRowCount(), maxRowsToScan); row++) {
                Object value = table.getValueAt(row, column);
                if (value != null) {
                    width = Math.max(width, Math.min(maxWidth, value.toString().length() * charWidthEstimate));
                }
            }
            table.getColumnModel().getColumn(column).setPreferredWidth(width);
        }
    }
}