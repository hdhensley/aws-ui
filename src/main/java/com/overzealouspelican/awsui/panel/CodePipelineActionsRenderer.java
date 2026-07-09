package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CodePipelineService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

final class CodePipelineActionsRenderer extends JPanel implements TableCellRenderer {
    private final JButton viewButton = new JButton("View");
    private final JButton startButton = new JButton("▶  Start");
    private final JButton stopButton = new JButton("⏹  Stop");

    CodePipelineActionsRenderer() {
        setLayout(new FlowLayout(FlowLayout.CENTER, UITheme.SPACING_SM, 4));
        setOpaque(true);
        viewButton.setFont(viewButton.getFont().deriveFont(UITheme.FONT_SIZE_SM));
        startButton.setFont(startButton.getFont().deriveFont(UITheme.FONT_SIZE_SM));
        stopButton.setFont(stopButton.getFont().deriveFont(UITheme.FONT_SIZE_SM));
        add(viewButton);
        add(startButton);
        add(stopButton);
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected,
        boolean hasFocus, int row, int column) {
        if (value instanceof CodePipelineService.PipelineRow pipelineRow) {
            stopButton.setEnabled(pipelineRow.inProgressExecutionId() != null);
        }
        setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        return this;
    }
}