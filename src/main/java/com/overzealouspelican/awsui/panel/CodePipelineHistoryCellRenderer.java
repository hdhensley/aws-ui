package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;

final class CodePipelineHistoryCellRenderer extends JPanel implements TableCellRenderer {
    private static final int SLOTS = 5;
    private static final String SYMBOL_SUCCESS = "✓";
    private static final String SYMBOL_FAILURE = "✗";
    private static final String SYMBOL_IN_PROGRESS = "●";
    private static final String SYMBOL_NONE = "·";
    private static final Color COLOR_SUCCESS = new Color(76, 153, 76);
    private static final Color COLOR_FAILURE = new Color(192, 57, 57);
    private static final Color COLOR_IN_PROGRESS = new Color(52, 110, 200);

    private final JLabel[] slots = new JLabel[SLOTS];

    CodePipelineHistoryCellRenderer() {
        setLayout(new GridLayout(1, SLOTS, 4, 0));
        setOpaque(true);
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = new JLabel();
            slots[i].setFont(slots[i].getFont().deriveFont(Font.BOLD, 14f));
            slots[i].setHorizontalAlignment(SwingConstants.CENTER);
            slots[i].setVerticalAlignment(SwingConstants.CENTER);
            add(slots[i]);
        }
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected,
        boolean hasFocus, int row, int column) {
        setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, UIManager.getColor("Component.borderColor")));

        @SuppressWarnings("unchecked")
        List<String> statuses = (value instanceof List<?> list) ? (List<String>) list : List.of();

        for (int i = 0; i < SLOTS; i++) {
            if (i < statuses.size()) {
                String status = statuses.get(i);
                if (status.contains("Succeeded")) {
                    slots[i].setText(SYMBOL_SUCCESS);
                    slots[i].setForeground(isSelected ? table.getSelectionForeground() : COLOR_SUCCESS);
                } else if (status.contains("Failed")) {
                    slots[i].setText(SYMBOL_FAILURE);
                    slots[i].setForeground(isSelected ? table.getSelectionForeground() : COLOR_FAILURE);
                } else if (status.contains("InProgress") || status.contains("Stopping")) {
                    slots[i].setText(SYMBOL_IN_PROGRESS);
                    slots[i].setForeground(isSelected ? table.getSelectionForeground() : COLOR_IN_PROGRESS);
                } else {
                    slots[i].setText(SYMBOL_NONE);
                    slots[i].setForeground(UIManager.getColor("Label.disabledForeground"));
                }
            } else {
                slots[i].setText(SYMBOL_NONE);
                slots[i].setForeground(UIManager.getColor("Label.disabledForeground"));
            }
        }
        return this;
    }
}