package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CloudWatchLogsService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogsCommandControllerTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private static final String CHOOSE_GROUP = "Choose group";

    private List<String> statusMessages;
    private List<String> errorMessages;

    // Controller wired to a no-op async workflow (for happy-path tests that reach the workflow).
    private LogsCommandController controller;
    // Controller wired to null workflow — safe only for guard-clause tests that return before calling it.
    private LogsCommandController guardController;

    private JComboBox<String> groupComboBox;
    private JComboBox<CloudWatchLogsService.LogStreamOption> streamComboBox;
    private JTextField groupSearchField;
    private JTextField streamSearchField;
    private JComboBox<LogsTimeframeOption> timeframeComboBox;

    @BeforeEach
    void setUp() {
        statusMessages = new ArrayList<>();
        errorMessages = new ArrayList<>();

        LogsAsyncWorkflow noOpWorkflow = new LogsAsyncWorkflow(
            new NoOpLogsService(),
            new StubAsyncView()
        );

        controller = new LogsCommandController(
            noOpWorkflow,
            CHOOSE_GROUP,
            statusMessages::add,
            errorMessages::add
        );

        guardController = new LogsCommandController(
            null,
            CHOOSE_GROUP,
            statusMessages::add,
            errorMessages::add
        );

        groupComboBox = new JComboBox<>(new String[]{CHOOSE_GROUP});
        streamComboBox = new JComboBox<>();
        groupSearchField = new JTextField();
        streamSearchField = new JTextField();
        timeframeComboBox = new JComboBox<>(new LogsTimeframeOption[]{
            new LogsTimeframeOption("Last 10 minutes", java.time.Duration.ofMinutes(10))
        });
    }

    // --- refreshLogGroups ---

    @Test
    void refreshLogGroups_nullProfile_setsStatusAndReturnsFalse() {
        boolean result = guardController.refreshLogGroups(null, groupComboBox, streamComboBox, groupSearchField, null);

        assertFalse(result);
        assertEquals(List.of("No profile selected"), statusMessages);
    }

    @Test
    void refreshLogGroups_blankProfile_setsStatusAndReturnsFalse() {
        boolean result = guardController.refreshLogGroups("   ", groupComboBox, streamComboBox, groupSearchField, null);

        assertFalse(result);
        assertEquals(List.of("No profile selected"), statusMessages);
    }

    @Test
    void refreshLogGroups_validProfile_returnsTrue() {
        boolean result = controller.refreshLogGroups("my-profile", groupComboBox, streamComboBox, groupSearchField, null);

        assertTrue(result);
        assertTrue(statusMessages.isEmpty());
    }

    // --- refreshLogStreams ---

    @Test
    void refreshLogStreams_nullProfile_setsStatusAndReturnsFalse() {
        boolean result = guardController.refreshLogStreams(null, groupComboBox, streamSearchField, false, () -> {}, null);

        assertFalse(result);
        assertEquals(List.of("No profile selected"), statusMessages);
    }

    @Test
    void refreshLogStreams_chooseGroupSelected_setsStatusAndReturnsFalse() {
        groupComboBox.setSelectedItem(CHOOSE_GROUP);

        boolean result = guardController.refreshLogStreams("my-profile", groupComboBox, streamSearchField, false, () -> {}, null);

        assertFalse(result);
        assertEquals(List.of("Select a log group"), statusMessages);
    }

    @Test
    void refreshLogStreams_nullGroupItem_setsStatusAndReturnsFalse() {
        groupComboBox.setModel(new DefaultComboBoxModel<>());

        boolean result = guardController.refreshLogStreams("my-profile", groupComboBox, streamSearchField, false, () -> {}, null);

        assertFalse(result);
        assertEquals(List.of("Select a log group"), statusMessages);
    }

    @Test
    void refreshLogStreams_validGroupSelected_returnsTrue() {
        groupComboBox.addItem("/aws/lambda/my-fn");
        groupComboBox.setSelectedItem("/aws/lambda/my-fn");

        boolean result = controller.refreshLogStreams("my-profile", groupComboBox, streamSearchField, false, () -> {}, null);

        assertTrue(result);
        assertTrue(statusMessages.isEmpty());
    }

    // --- loadLogs ---

    @Test
    void loadLogs_nullProfile_showsErrorAndReturnsFalse() {
        boolean result = guardController.loadLogs(null, groupComboBox, streamComboBox, timeframeComboBox, null);

        assertFalse(result);
        assertEquals(List.of("No AWS profile selected."), errorMessages);
    }

    @Test
    void loadLogs_blankProfile_showsErrorAndReturnsFalse() {
        boolean result = guardController.loadLogs("", groupComboBox, streamComboBox, timeframeComboBox, null);

        assertFalse(result);
        assertEquals(List.of("No AWS profile selected."), errorMessages);
    }

    @Test
    void loadLogs_chooseGroupSelected_showsErrorAndReturnsFalse() {
        groupComboBox.setSelectedItem(CHOOSE_GROUP);

        boolean result = guardController.loadLogs("my-profile", groupComboBox, streamComboBox, timeframeComboBox, null);

        assertFalse(result);
        assertEquals(List.of("Select a log group."), errorMessages);
    }

    @Test
    void loadLogs_validGroupSelected_returnsTrue() {
        groupComboBox.addItem("/aws/lambda/my-fn");
        groupComboBox.setSelectedItem("/aws/lambda/my-fn");

        boolean result = controller.loadLogs("my-profile", groupComboBox, streamComboBox, timeframeComboBox, null);

        assertTrue(result);
        assertTrue(errorMessages.isEmpty());
    }

    // --- stubs ---

    private static class NoOpLogsService extends CloudWatchLogsService {
        @Override
        public List<String> listLogGroups(String profileName, String query) { return List.of(); }

        @Override
        public List<CloudWatchLogsService.LogStreamOption> listLogStreams(String profileName, String logGroupName, String query) { return List.of(); }

        @Override
        public String getLogEvents(String profileName, String logGroupName, String logStreamName, java.time.Instant startTime, java.time.Instant endTime) { return ""; }
    }

    // Minimal stub to satisfy LogsAsyncWorkflow constructor
    private static class StubAsyncView implements LogsAsyncWorkflow.AsyncView {
        @Override public void setBusyState(boolean busy, String statusText) {}
        @Override public void setStatus(String text) {}
        @Override public String currentStatusText() { return ""; }
        @Override public void showError(String message) {}
        @Override public void onGroupsLoaded(List<String> groups) {}
        @Override public void onStreamsLoaded(List<CloudWatchLogsService.LogStreamOption> streams) {}
        @Override public void beforeLogsLoad() {}
        @Override public void onLogsLoaded(String rawLogs, String timeframeLabel) {}
    }
}
