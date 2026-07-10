package com.overzealouspelican.awsui.panel;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

final class LogsTableClipboardSupport {
    private LogsTableClipboardSupport() {
    }

    static void installCopyAction(JTable table) {
        KeyStroke copy = KeyStroke.getKeyStroke(
            KeyEvent.VK_C,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
        );
        table.getInputMap(JComponent.WHEN_FOCUSED).put(copy, "copyCells");
        table.getActionMap().put("copyCells", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                copySelectedTableCells(table);
            }
        });
    }

    private static void copySelectedTableCells(JTable table) {
        int[] selectedRows = table.getSelectedRows();
        int[] selectedCols = table.getSelectedColumns();
        if (selectedRows.length == 0 || selectedCols.length == 0) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int row = 0; row < selectedRows.length; row++) {
            for (int column = 0; column < selectedCols.length; column++) {
                Object value = table.getValueAt(selectedRows[row], selectedCols[column]);
                if (value != null) {
                    builder.append(value);
                }
                if (column < selectedCols.length - 1) {
                    builder.append('\t');
                }
            }
            if (row < selectedRows.length - 1) {
                builder.append(System.lineSeparator());
            }
        }

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
            new StringSelection(builder.toString()),
            null
        );
    }
}