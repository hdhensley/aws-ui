package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.SettingsService;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

final class LogsSavedFilterListCellRenderer extends DefaultListCellRenderer {
    private final Supplier<String> defaultFilterNameSupplier;

    LogsSavedFilterListCellRenderer(Supplier<String> defaultFilterNameSupplier) {
        this.defaultFilterNameSupplier = defaultFilterNameSupplier;
    }

    @Override
    public Component getListCellRendererComponent(
        JList<?> list,
        Object value,
        int index,
        boolean isSelected,
        boolean cellHasFocus
    ) {
        JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof SettingsService.SavedLogsFilter filter) {
            String defaultFilterName = defaultFilterNameSupplier.get();
            boolean isDefault = defaultFilterName != null
                && defaultFilterName.equalsIgnoreCase(filter.getName());
            label.setText(isDefault ? filter.getName() + " (default)" : filter.getName());
        }
        return label;
    }
}