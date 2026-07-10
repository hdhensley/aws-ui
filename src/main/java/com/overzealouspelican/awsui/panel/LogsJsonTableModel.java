package com.overzealouspelican.awsui.panel;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class LogsJsonTableModel extends AbstractTableModel {
    private List<String> columns = List.of();
    private List<LogsParsedJsonRow> rows = List.of();

    void setRows(List<LogsParsedJsonRow> newRows) {
        rows = newRows;
        Set<String> discoveredColumns = new LinkedHashSet<>();
        discoveredColumns.add("timestamp");
        discoveredColumns.add("@timestamp");
        discoveredColumns.add("log.level");
        discoveredColumns.add("message");
        discoveredColumns.add("log.logger");
        discoveredColumns.add("process.thread.name");
        discoveredColumns.add("logStream");
        for (LogsParsedJsonRow row : newRows) {
            discoveredColumns.addAll(row.values().keySet());
        }
        columns = new ArrayList<>();
        for (String column : discoveredColumns) {
            boolean exists = newRows.stream().anyMatch(row -> row.values().containsKey(column));
            if (exists || "timestamp".equals(column)) {
                columns.add(column);
            }
        }
        fireTableStructureChanged();
    }

    void clear() {
        columns = List.of();
        rows = List.of();
        fireTableStructureChanged();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return rows.get(rowIndex).values().getOrDefault(columns.get(columnIndex), "");
    }

    int getColumnIndex(String columnName) {
        return columns.indexOf(columnName);
    }
}