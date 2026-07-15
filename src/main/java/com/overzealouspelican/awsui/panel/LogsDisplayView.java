package com.overzealouspelican.awsui.panel;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;

final class LogsDisplayView {
    final CardLayout layout;
    final JPanel panel;
    final JTextArea logsTextArea;
    final JTable logsTable;
    final LogsJsonTableModel tableModel;
    final TableRowSorter<LogsJsonTableModel> rowSorter;

    LogsDisplayView(String displayTextKey, String displayTableKey) {
        layout = new CardLayout();
        panel = new JPanel(layout);

        logsTextArea = new JTextArea();
        logsTextArea.setEditable(false);
        logsTextArea.setLineWrap(false);
        logsTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane textScrollPane = new JScrollPane(logsTextArea);
        textScrollPane.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        tableModel = new LogsJsonTableModel();
        logsTable = new JTable(tableModel);
        rowSorter = new TableRowSorter<>(tableModel);
        logsTable.setRowSorter(rowSorter);
        logsTable.setCellSelectionEnabled(true);
        logsTable.setRowSelectionAllowed(true);
        logsTable.setColumnSelectionAllowed(true);
        logsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        logsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        logsTable.setFillsViewportHeight(true);
        logsTable.setRowHeight(28);
        LogsTableClipboardSupport.installCopyAction(logsTable);
        JScrollPane tableScrollPane = new JScrollPane(logsTable);
        tableScrollPane.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        panel.add(textScrollPane, displayTextKey);
        panel.add(tableScrollPane, displayTableKey);
        layout.show(panel, displayTextKey);
    }
}