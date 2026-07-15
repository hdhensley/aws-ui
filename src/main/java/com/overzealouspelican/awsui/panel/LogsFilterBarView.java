package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import java.awt.*;

final class LogsFilterBarView {
    final JPanel panel;
    final JTextField groupSearchField;
    final JButton refreshGroupsButton;
    final JComboBox<String> groupComboBox;
    final JTextField streamSearchField;
    final JButton refreshStreamsButton;
    final JComboBox<com.overzealouspelican.awsui.service.CloudWatchLogsService.LogStreamOption> streamComboBox;
    final JComboBox<LogsTimeframeOption> timeframeComboBox;
    final JButton loadLogsButton;
    final JComboBox<String> jsonLogStreamFilterComboBox;
    final JTextField clientSearchField;
    final JButton applyClientSearchButton;
    final JButton clearClientSearchButton;
    final JTextField filterNameField;
    final JButton saveFilterButton;

    LogsFilterBarView(Dimension inputSize, Dimension buttonSize, String allJsonLogStreams) {
        panel = new JPanel(new GridBagLayout());
        panel.setBackground(UITheme.panelBackground());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, UITheme.SPACING_SM, UITheme.SPACING_SM);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("Log Group Search:"), gbc);

        groupSearchField = new JTextField();
        applyInputSize(groupSearchField, inputSize);
        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(groupSearchField, gbc);

        refreshGroupsButton = new JButton("Find Groups");
        applyButtonSize(refreshGroupsButton, buttonSize);
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(refreshGroupsButton, gbc);

        groupComboBox = new JComboBox<>();
        applyInputSize(groupComboBox, inputSize);
        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(groupComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Log Stream Search:"), gbc);

        streamSearchField = new JTextField();
        applyInputSize(streamSearchField, inputSize);
        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(streamSearchField, gbc);

        refreshStreamsButton = new JButton("Find Streams");
        applyButtonSize(refreshStreamsButton, buttonSize);
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(refreshStreamsButton, gbc);

        streamComboBox = new JComboBox<>();
        applyInputSize(streamComboBox, inputSize);
        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(streamComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.insets = new Insets(0, 0, 0, UITheme.SPACING_SM);
        panel.add(new JLabel("Timeframe:"), gbc);

        timeframeComboBox = new JComboBox<>(new LogsTimeframeOption[]{
            new LogsTimeframeOption("Last 1 minute", java.time.Duration.ofMinutes(1)),
            new LogsTimeframeOption("Last 5 minutes", java.time.Duration.ofMinutes(5)),
            new LogsTimeframeOption("Last 10 minutes", java.time.Duration.ofMinutes(10)),
            new LogsTimeframeOption("Last 30 minutes", java.time.Duration.ofMinutes(30)),
            new LogsTimeframeOption("Last 60 minutes", java.time.Duration.ofMinutes(60)),
            new LogsTimeframeOption("Last 6 hours", java.time.Duration.ofHours(6)),
            new LogsTimeframeOption("Last 24 hours", java.time.Duration.ofHours(24))
        });
        applyInputSize(timeframeComboBox, inputSize);
        timeframeComboBox.setSelectedIndex(2);
        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(timeframeComboBox, gbc);

        loadLogsButton = new JButton("Load Logs");
        UITheme.stylePrimaryButton(loadLogsButton);
        applyButtonSize(loadLogsButton, buttonSize);
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(loadLogsButton, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(Box.createHorizontalStrut(inputSize.width), gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.insets = new Insets(UITheme.SPACING_SM, 0, 0, UITheme.SPACING_SM);
        panel.add(new JLabel("JSON logStream:"), gbc);

        jsonLogStreamFilterComboBox = new JComboBox<>(new String[]{allJsonLogStreams});
        jsonLogStreamFilterComboBox.setEnabled(false);
        applyInputSize(jsonLogStreamFilterComboBox, inputSize);
        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(jsonLogStreamFilterComboBox, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(Box.createHorizontalStrut(buttonSize.width), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(Box.createHorizontalStrut(inputSize.width), gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        gbc.insets = new Insets(UITheme.SPACING_SM, 0, 0, UITheme.SPACING_SM);
        panel.add(new JLabel("Client Search:"), gbc);

        clientSearchField = new JTextField();
        applyInputSize(clientSearchField, inputSize);
        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(clientSearchField, gbc);

        JPanel searchButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UITheme.SPACING_SM, 0));
        searchButtonsPanel.setOpaque(false);
        applyClientSearchButton = new JButton("Apply Search");
        clearClientSearchButton = new JButton("Clear Search");
        searchButtonsPanel.add(applyClientSearchButton);
        searchButtonsPanel.add(clearClientSearchButton);

        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(searchButtonsPanel, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(Box.createHorizontalStrut(inputSize.width), gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        gbc.insets = new Insets(UITheme.SPACING_SM, 0, 0, UITheme.SPACING_SM);
        panel.add(new JLabel("Filter Name:"), gbc);

        filterNameField = new JTextField();
        applyInputSize(filterNameField, inputSize);
        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(filterNameField, gbc);

        saveFilterButton = new JButton("Save Filter");
        applyButtonSize(saveFilterButton, buttonSize);
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(saveFilterButton, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(Box.createHorizontalStrut(inputSize.width), gbc);
    }

    private static void applyInputSize(JComponent component, Dimension size) {
        component.setPreferredSize(size);
        component.setMinimumSize(size);
    }

    private static void applyButtonSize(AbstractButton button, Dimension size) {
        button.setPreferredSize(size);
        button.setMinimumSize(size);
    }
}