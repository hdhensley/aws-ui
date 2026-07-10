package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CloudWatchLogsService;

import javax.swing.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

final class LogsAsyncWorkflow {
    interface AsyncView {
        void setBusyState(boolean busy, String statusText);

        void setStatus(String text);

        String currentStatusText();

        void showError(String message);

        void onGroupsLoaded(List<String> groups);

        void onStreamsLoaded(List<CloudWatchLogsService.LogStreamOption> streams);

        void beforeLogsLoad();

        void onLogsLoaded(String rawLogs, String timeframeLabel);
    }

    private final CloudWatchLogsService logsService;
    private final AsyncView view;

    LogsAsyncWorkflow(CloudWatchLogsService logsService, AsyncView view) {
        this.logsService = logsService;
        this.view = view;
    }

    void refreshLogGroups(String profileName, String query, Runnable onComplete) {
        view.setBusyState(true, "Loading log groups...");

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return logsService.listLogGroups(profileName, query);
            }

            @Override
            protected void done() {
                try {
                    List<String> groups = get();
                    view.onGroupsLoaded(groups);
                    view.setStatus("Groups: " + groups.size());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    view.setStatus("Interrupted while loading groups");
                } catch (ExecutionException ex) {
                    view.setStatus("Failed to load groups");
                    view.showError(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                } finally {
                    view.setBusyState(false, view.currentStatusText());
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        }.execute();
    }

    void refreshLogStreams(
        String profileName,
        String group,
        String query,
        boolean loadLogsAfterRefresh,
        Runnable loadLogsAction,
        Runnable onComplete
    ) {
        view.setBusyState(true, "Loading streams...");

        new SwingWorker<List<CloudWatchLogsService.LogStreamOption>, Void>() {
            @Override
            protected List<CloudWatchLogsService.LogStreamOption> doInBackground() {
                return logsService.listLogStreams(profileName, group, query);
            }

            @Override
            protected void done() {
                boolean shouldAutoLoadLogs = false;
                try {
                    List<CloudWatchLogsService.LogStreamOption> streams = get();
                    view.onStreamsLoaded(streams);
                    view.setStatus("Streams: " + streams.size() + " (most recent first)");
                    shouldAutoLoadLogs = true;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    view.setStatus("Interrupted while loading streams");
                } catch (ExecutionException ex) {
                    view.setStatus("Failed to load streams");
                    view.showError(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                } finally {
                    view.setBusyState(false, view.currentStatusText());
                    if (loadLogsAfterRefresh && shouldAutoLoadLogs && loadLogsAction != null) {
                        loadLogsAction.run();
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        }.execute();
    }

    void loadLogs(
        String profileName,
        String group,
        String streamName,
        Duration duration,
        String timeframeLabel,
        Runnable onComplete
    ) {
        Instant end = Instant.now();
        Instant start = end.minus(duration);

        view.setBusyState(true, "Loading logs...");
        view.beforeLogsLoad();

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return logsService.getLogEvents(profileName, group, streamName, start, end);
            }

            @Override
            protected void done() {
                try {
                    view.onLogsLoaded(get(), timeframeLabel);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    view.setStatus("Interrupted while loading logs");
                } catch (ExecutionException ex) {
                    view.setStatus("Failed to load logs");
                    view.showError(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                } finally {
                    view.setBusyState(false, view.currentStatusText());
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        }.execute();
    }
}