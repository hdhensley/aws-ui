package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CloudWatchLogsService;

import javax.swing.*;
import java.time.Duration;
import java.util.function.Consumer;

final class LogsCommandController {
    private final LogsAsyncWorkflow asyncWorkflow;
    private final String chooseGroupOption;
    private final Consumer<String> statusSetter;
    private final Consumer<String> showError;

    LogsCommandController(
        LogsAsyncWorkflow asyncWorkflow,
        String chooseGroupOption,
        Consumer<String> statusSetter,
        Consumer<String> showError
    ) {
        this.asyncWorkflow = asyncWorkflow;
        this.chooseGroupOption = chooseGroupOption;
        this.statusSetter = statusSetter;
        this.showError = showError;
    }

    boolean refreshLogGroups(
        String currentProfile,
        JComboBox<String> groupComboBox,
        JComboBox<CloudWatchLogsService.LogStreamOption> streamComboBox,
        JTextField groupSearchField,
        Runnable onComplete
    ) {
        if (currentProfile == null || currentProfile.isBlank()) {
            statusSetter.accept("No profile selected");
            groupComboBox.setModel(new DefaultComboBoxModel<>(new String[]{chooseGroupOption}));
            streamComboBox.setModel(new DefaultComboBoxModel<>());
            if (onComplete != null) {
                onComplete.run();
            }
            return false;
        }

        String query = groupSearchField == null ? "" : groupSearchField.getText();
        asyncWorkflow.refreshLogGroups(currentProfile, query, onComplete);
        return true;
    }

    boolean refreshLogStreams(
        String currentProfile,
        JComboBox<String> groupComboBox,
        JTextField streamSearchField,
        boolean loadLogsAfterRefresh,
        Runnable loadLogsAction,
        Runnable onComplete
    ) {
        if (currentProfile == null || currentProfile.isBlank()) {
            statusSetter.accept("No profile selected");
            if (onComplete != null) {
                onComplete.run();
            }
            return false;
        }

        String group = (String) groupComboBox.getSelectedItem();
        if (group == null || group.isBlank() || chooseGroupOption.equals(group)) {
            statusSetter.accept("Select a log group");
            if (onComplete != null) {
                onComplete.run();
            }
            return false;
        }

        String query = streamSearchField.getText();
        asyncWorkflow.refreshLogStreams(currentProfile, group, query, loadLogsAfterRefresh, loadLogsAction, onComplete);
        return true;
    }

    boolean loadLogs(
        String currentProfile,
        JComboBox<String> groupComboBox,
        JComboBox<CloudWatchLogsService.LogStreamOption> streamComboBox,
        JComboBox<LogsTimeframeOption> timeframeComboBox,
        Runnable onComplete
    ) {
        if (currentProfile == null || currentProfile.isBlank()) {
            showError.accept("No AWS profile selected.");
            if (onComplete != null) {
                onComplete.run();
            }
            return false;
        }

        String group = (String) groupComboBox.getSelectedItem();
        CloudWatchLogsService.LogStreamOption stream =
            (CloudWatchLogsService.LogStreamOption) streamComboBox.getSelectedItem();
        if (group == null || group.isBlank() || chooseGroupOption.equals(group)) {
            showError.accept("Select a log group.");
            if (onComplete != null) {
                onComplete.run();
            }
            return false;
        }

        LogsTimeframeOption timeframe = (LogsTimeframeOption) timeframeComboBox.getSelectedItem();
        Duration duration = timeframe == null ? Duration.ofMinutes(10) : timeframe.duration();
        String streamName = stream == null ? null : stream.name();
        asyncWorkflow.loadLogs(currentProfile, group, streamName, duration, timeframe == null ? "" : timeframe.toString(), onComplete);
        return true;
    }
}