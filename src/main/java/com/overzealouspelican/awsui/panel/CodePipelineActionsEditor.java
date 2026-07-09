package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CodePipelineService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.function.Consumer;

final class CodePipelineActionsEditor extends AbstractCellEditor implements TableCellEditor {
    private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, UITheme.SPACING_SM, 4));
    private final JButton viewButton = new JButton("View");
    private final JButton startButton = new JButton("▶  Start");
    private final JButton stopButton = new JButton("⏹  Stop");
    private final Consumer<CodePipelineService.PipelineRow> onView;
    private final Consumer<CodePipelineService.PipelineRow> onStart;
    private final Consumer<CodePipelineService.PipelineRow> onStop;
    private CodePipelineService.PipelineRow currentRow;

    CodePipelineActionsEditor(
        Consumer<CodePipelineService.PipelineRow> onView,
        Consumer<CodePipelineService.PipelineRow> onStart,
        Consumer<CodePipelineService.PipelineRow> onStop) {
        this.onView = onView;
        this.onStart = onStart;
        this.onStop = onStop;

        viewButton.setFont(viewButton.getFont().deriveFont(UITheme.FONT_SIZE_SM));
        startButton.setFont(startButton.getFont().deriveFont(UITheme.FONT_SIZE_SM));
        stopButton.setFont(stopButton.getFont().deriveFont(UITheme.FONT_SIZE_SM));

        viewButton.addActionListener(e -> {
            fireEditingStopped();
            onView.accept(currentRow);
        });
        startButton.addActionListener(e -> {
            fireEditingStopped();
            onStart.accept(currentRow);
        });
        stopButton.addActionListener(e -> {
            fireEditingStopped();
            onStop.accept(currentRow);
        });

        panel.add(viewButton);
        panel.add(startButton);
        panel.add(stopButton);
    }

    @Override
    public Component getTableCellEditorComponent(
        JTable table, Object value, boolean isSelected, int row, int column) {
        if (value instanceof CodePipelineService.PipelineRow pipelineRow) {
            currentRow = pipelineRow;
            stopButton.setEnabled(pipelineRow.inProgressExecutionId() != null);
        }
        panel.setBackground(table.getSelectionBackground());
        return panel;
    }

    @Override
    public Object getCellEditorValue() {
        return currentRow;
    }
}