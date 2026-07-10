package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CloudWatchLogsService;
import com.overzealouspelican.awsui.service.SettingsService;

import javax.swing.*;
import java.util.List;

final class LogsSavedFilterWorkflow {
    private LogsSavedFilterWorkflow() {
    }

    static String reloadSavedFiltersList(
        SettingsService settingsService,
        DefaultListModel<SettingsService.SavedLogsFilter> listModel,
        JList<SettingsService.SavedLogsFilter> list,
        String selectedName
    ) {
        String defaultSavedFilterName = settingsService.getDefaultLogsFilterName();
        listModel.clear();
        List<SettingsService.SavedLogsFilter> filters = settingsService.getSavedLogsFilters();
        int selectedIndex = -1;

        for (int i = 0; i < filters.size(); i++) {
            SettingsService.SavedLogsFilter filter = filters.get(i);
            listModel.addElement(filter);
            if (selectedName != null && selectedName.equalsIgnoreCase(filter.getName())) {
                selectedIndex = i;
            }
        }

        if (selectedIndex >= 0) {
            list.setSelectedIndex(selectedIndex);
        } else if (!listModel.isEmpty()) {
            list.setSelectedIndex(0);
        }

        list.repaint();
        return defaultSavedFilterName;
    }

    static String textOrEmpty(JTextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    static String selectedGroupValue(JComboBox<String> groupComboBox, String chooseGroupOption) {
        Object selectedGroup = groupComboBox.getSelectedItem();
        if (!(selectedGroup instanceof String value)) {
            return "";
        }
        return chooseGroupOption.equals(value) ? "" : value;
    }

    static String selectedStreamValue(JComboBox<CloudWatchLogsService.LogStreamOption> streamComboBox) {
        Object selectedStream = streamComboBox.getSelectedItem();
        if (!(selectedStream instanceof CloudWatchLogsService.LogStreamOption value)) {
            return "";
        }
        return value.name() == null ? "" : value.name();
    }

    static String selectedTimeframeLabel(JComboBox<?> timeframeComboBox) {
        Object selectedTimeframe = timeframeComboBox.getSelectedItem();
        return selectedTimeframe == null ? "" : selectedTimeframe.toString();
    }

    static String selectedJsonLogStreamValue(JComboBox<String> jsonLogStreamComboBox, String allJsonLogStreamsValue) {
        Object selectedJsonStream = jsonLogStreamComboBox.getSelectedItem();
        if (!(selectedJsonStream instanceof String value) || allJsonLogStreamsValue.equals(value)) {
            return "";
        }
        return value;
    }

    static void selectTimeframeByLabel(JComboBox<?> timeframeComboBox, String label) {
        if (label == null || label.isBlank()) {
            return;
        }

        for (int i = 0; i < timeframeComboBox.getItemCount(); i++) {
            Object option = timeframeComboBox.getItemAt(i);
            if (label.equals(String.valueOf(option))) {
                timeframeComboBox.setSelectedIndex(i);
                break;
            }
        }
    }

    static void selectGroupByValue(JComboBox<String> groupComboBox, String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return;
        }

        ComboBoxModel<String> model = groupComboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            String value = model.getElementAt(i);
            if (groupName.equals(value)) {
                groupComboBox.setSelectedItem(value);
                return;
            }
        }
    }

    static void selectStreamByName(
        JComboBox<CloudWatchLogsService.LogStreamOption> streamComboBox,
        CloudWatchLogsService.LogStreamOption allStreamsOption,
        String streamName
    ) {
        if (streamName == null || streamName.isBlank()) {
            streamComboBox.setSelectedItem(allStreamsOption);
            return;
        }

        ComboBoxModel<CloudWatchLogsService.LogStreamOption> model = streamComboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            CloudWatchLogsService.LogStreamOption option = model.getElementAt(i);
            if (streamName.equals(option.name())) {
                streamComboBox.setSelectedItem(option);
                return;
            }
        }
        streamComboBox.setSelectedItem(allStreamsOption);
    }

    static void selectJsonLogStreamByValue(
        JComboBox<String> jsonLogStreamComboBox,
        String allJsonLogStreamsValue,
        String value
    ) {
        if (value == null || value.isBlank()) {
            jsonLogStreamComboBox.setSelectedItem(allJsonLogStreamsValue);
            return;
        }

        ComboBoxModel<String> model = jsonLogStreamComboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            String option = model.getElementAt(i);
            if (value.equals(option)) {
                jsonLogStreamComboBox.setSelectedItem(option);
                return;
            }
        }
        jsonLogStreamComboBox.setSelectedItem(allJsonLogStreamsValue);
    }
}