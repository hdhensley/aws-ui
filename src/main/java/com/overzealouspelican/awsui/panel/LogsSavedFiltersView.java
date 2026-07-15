package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.SettingsService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class LogsSavedFiltersView {
    final JPanel gutter;
    final JLabel titleLabel;
    final JButton toggleButton;
    final JPanel contentPanel;
    final DefaultListModel<SettingsService.SavedLogsFilter> listModel;
    final JList<SettingsService.SavedLogsFilter> list;
    final JButton applyButton;
    final JButton deleteButton;
    final Icon collapsedIcon;

    LogsSavedFiltersView(
        int expandedWidth,
        int collapsedWidth,
        Supplier<String> defaultSavedFilterNameSupplier,
        Runnable onApplySelected,
        Runnable onDeleteSelected,
        Runnable onToggle,
        Consumer<MouseEvent> onContextMenuEvent
    ) {
        gutter = new JPanel(new BorderLayout());
        gutter.setBackground(UITheme.panelBackground());
        gutter.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIManager.getColor("Component.borderColor")));
        gutter.setPreferredSize(new Dimension(expandedWidth, 0));
        gutter.setMinimumSize(new Dimension(collapsedWidth, 0));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM));

        Icon icon = UIManager.getIcon("FileView.directoryIcon");
        if (icon == null) {
            icon = UIManager.getIcon("OptionPane.informationIcon");
        }
        collapsedIcon = icon;

        titleLabel = new JLabel("Saved Filters");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, UITheme.FONT_SIZE_SM));

        toggleButton = new JButton("<");
        toggleButton.setMargin(new Insets(2, 8, 2, 8));
        toggleButton.addActionListener(e -> onToggle.run());

        header.add(titleLabel, BorderLayout.WEST);
        header.add(toggleButton, BorderLayout.EAST);

        contentPanel = new JPanel(new BorderLayout(UITheme.SPACING_SM, UITheme.SPACING_SM));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(0, UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM));

        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new LogsSavedFilterListCellRenderer(defaultSavedFilterNameSupplier));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onApplySelected.run();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                onContextMenuEvent.accept(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                onContextMenuEvent.accept(e);
            }
        });

        JScrollPane listScrollPane = new JScrollPane(list);
        listScrollPane.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        JPanel actions = new JPanel(new GridLayout(1, 2, UITheme.SPACING_SM, 0));
        actions.setOpaque(false);
        applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> onApplySelected.run());
        deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> onDeleteSelected.run());
        actions.add(applyButton);
        actions.add(deleteButton);

        contentPanel.add(listScrollPane, BorderLayout.CENTER);
        contentPanel.add(actions, BorderLayout.SOUTH);

        gutter.add(header, BorderLayout.NORTH);
        gutter.add(contentPanel, BorderLayout.CENTER);
    }
}