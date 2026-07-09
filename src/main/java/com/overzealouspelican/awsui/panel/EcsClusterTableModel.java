package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.EcsService;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class EcsClusterTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Cluster", "Services", "Tasks", "Container instances", "Status"};
    private List<EcsService.ClusterRow> rows = new ArrayList<>();

    void setRows(List<EcsService.ClusterRow> newRows) {
        rows = new ArrayList<>(newRows);
        fireTableDataChanged();
    }

    EcsService.ClusterRow getRow(int modelRow) {
        return rows.get(modelRow);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        EcsService.ClusterRow row = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> row.name();
            case 1 -> row.activeServices();
            case 2 -> row.pendingTasks() + " Pending | " + row.runningTasks() + " Running";
            case 3 -> row.containerInstances();
            case 4 -> row.status();
            default -> "";
        };
    }
}