package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CodePipelineService;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class CodePipelineTableModel extends AbstractTableModel {
    private static final String[] COLUMN_NAMES = {"Name", "Last 5 Runs", "Context", "Last Deployed", "Actions"};

    private List<CodePipelineService.PipelineRow> rows = new ArrayList<>();

    void setRows(List<CodePipelineService.PipelineRow> newRows) {
        this.rows = new ArrayList<>(newRows);
        fireTableDataChanged();
    }

    int getRowCountValue() {
        return rows.size();
    }

    CodePipelineService.PipelineRow getRowAt(int rowIndex) {
        return rows.get(rowIndex);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        CodePipelineService.PipelineRow row = rows.get(rowIndex);
        return switch (columnIndex) {
            case CodePipelinePanel.COL_NAME -> row.name();
            case CodePipelinePanel.COL_HISTORY -> row.recentStatuses();
            case CodePipelinePanel.COL_CONTEXT -> row.region() + "  ·  " + row.status();
            case CodePipelinePanel.COL_LAST_DEPLOYED -> row.lastDeployedAt();
            case CodePipelinePanel.COL_ACTIONS -> row;
            default -> null;
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == CodePipelinePanel.COL_ACTIONS;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case CodePipelinePanel.COL_HISTORY -> List.class;
            case CodePipelinePanel.COL_ACTIONS -> CodePipelineService.PipelineRow.class;
            default -> String.class;
        };
    }
}