package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.EcsService;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class EcsServiceTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Service", "Status", "Scheduling", "Launch", "Task definition", "Deployments", "Tasks", "Created"};
    private List<EcsService.ServiceRow> rows = new ArrayList<>();

    void setRows(List<EcsService.ServiceRow> newRows) {
        rows = new ArrayList<>(newRows);
        fireTableDataChanged();
    }

    EcsService.ServiceRow getRow(int modelRow) {
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
        EcsService.ServiceRow row = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> row.name();
            case 1 -> row.status();
            case 2 -> row.schedulingStrategy();
            case 3 -> row.launchType();
            case 4 -> row.taskDefinition();
            case 5 -> row.deploymentStatus();
            case 6 -> row.runningCount() + "/" + row.desiredCount() + " Running";
            case 7 -> row.createdAt();
            default -> "";
        };
    }
}