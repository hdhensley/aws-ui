package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CloudWatchLogsService;
import com.overzealouspelican.awsui.service.SettingsService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LogsSavedFilterCoordinatorTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private List<String> errors;
    private List<String> statuses;
    private AtomicReference<String> defaultFilterName;
    private LogsSavedFilterCoordinator coordinator;
    private JList<SettingsService.SavedLogsFilter> savedFiltersList;
    private DefaultListModel<SettingsService.SavedLogsFilter> savedFiltersListModel;
    private JTextField filterNameField;
    private StubSettingsService settingsService;

    @BeforeEach
    void setUp() {
        errors = new ArrayList<>();
        statuses = new ArrayList<>();
        defaultFilterName = new AtomicReference<>(null);

        settingsService = new StubSettingsService();
        filterNameField = new JTextField();
        savedFiltersListModel = new DefaultListModel<>();
        savedFiltersList = new JList<>(savedFiltersListModel);

        coordinator = new LogsSavedFilterCoordinator(
            settingsService,
            new JTextField(),
            new JTextField(),
            new JComboBox<>(),
            new JComboBox<>(),
            new JComboBox<>(),
            new JComboBox<>(),
            new JTextField(),
            filterNameField,
            savedFiltersList,
            savedFiltersListModel,
            "Choose group",
            "All logStream values",
            new CloudWatchLogsService.LogStreamOption("", "All streams", null),
            errors::add,
            statuses::add,
            () -> {},
            onComplete -> { if (onComplete != null) onComplete.run(); },
            (load, onComplete) -> { if (onComplete != null) onComplete.run(); },
            onComplete -> { if (onComplete != null) onComplete.run(); },
            () -> {},
            value -> {},
            defaultFilterName::get,
            defaultFilterName::set
        );
    }

    // --- isSelectedFilterDefault ---

    @Test
    void isSelectedFilterDefault_returnsFalse_whenNoDefaultSet() {
        SettingsService.SavedLogsFilter filter = filter("my-filter");
        assertFalse(coordinator.isSelectedFilterDefault(filter));
    }

    @Test
    void isSelectedFilterDefault_returnsTrue_whenNameMatches() {
        defaultFilterName.set("my-filter");
        SettingsService.SavedLogsFilter filter = filter("my-filter");
        assertTrue(coordinator.isSelectedFilterDefault(filter));
    }

    @Test
    void isSelectedFilterDefault_isCaseInsensitive() {
        defaultFilterName.set("MY-FILTER");
        SettingsService.SavedLogsFilter filter = filter("my-filter");
        assertTrue(coordinator.isSelectedFilterDefault(filter));
    }

    @Test
    void isSelectedFilterDefault_returnsFalse_whenDifferentName() {
        defaultFilterName.set("other-filter");
        SettingsService.SavedLogsFilter filter = filter("my-filter");
        assertFalse(coordinator.isSelectedFilterDefault(filter));
    }

    // --- saveCurrentFilter blank-name guard ---

    @Test
    void saveCurrentFilter_rejectsBlankName() {
        filterNameField.setText("   ");

        coordinator.saveCurrentFilter();

        assertEquals(List.of("Enter a filter name before saving."), errors);
        assertTrue(statuses.isEmpty());
    }

    @Test
    void saveCurrentFilter_rejectsEmptyName() {
        filterNameField.setText("");

        coordinator.saveCurrentFilter();

        assertEquals(List.of("Enter a filter name before saving."), errors);
    }

    @Test
    void saveCurrentFilter_acceptsValidName() {
        filterNameField.setText("prod-errors");

        coordinator.saveCurrentFilter();

        assertTrue(errors.isEmpty());
        assertEquals(1, statuses.size());
        assertTrue(statuses.get(0).contains("prod-errors"));
        assertEquals("prod-errors", settingsService.lastSavedFilter.getName());
    }

    // --- setDefaultSavedFilter ---

    @Test
    void setDefaultSavedFilter_updatesDefaultName() {
        SettingsService.SavedLogsFilter filter = filter("prod-errors");

        coordinator.setDefaultSavedFilter(filter);

        assertEquals("prod-errors", defaultFilterName.get());
        assertEquals("prod-errors", settingsService.defaultFilterName);
        assertTrue(statuses.stream().anyMatch(s -> s.contains("prod-errors")));
    }

    // --- clearDefaultSavedFilter ---

    @Test
    void clearDefaultSavedFilter_clearsDefaultName() {
        defaultFilterName.set("prod-errors");

        coordinator.clearDefaultSavedFilter();

        assertNull(defaultFilterName.get());
        assertTrue(settingsService.defaultFilterCleared);
        assertTrue(statuses.stream().anyMatch(s -> s.toLowerCase().contains("cleared")));
    }

    // --- helpers ---

    private static SettingsService.SavedLogsFilter filter(String name) {
        return new SettingsService.SavedLogsFilter(name, "", "", "", "", "", "", "");
    }

    private static class StubSettingsService extends SettingsService {
        SettingsService.SavedLogsFilter lastSavedFilter;
        String defaultFilterName;
        boolean defaultFilterCleared;

        @Override public void saveLogsFilter(SettingsService.SavedLogsFilter filter) { lastSavedFilter = filter; }
        @Override public List<SettingsService.SavedLogsFilter> getSavedLogsFilters() { return List.of(); }
        @Override public void removeLogsFilter(String name) {}
        @Override public void setDefaultLogsFilterName(String name) { defaultFilterName = name; }
        @Override public void clearDefaultLogsFilterName() { defaultFilterCleared = true; }
        @Override public String getDefaultLogsFilterName() { return defaultFilterName; }
    }
}
