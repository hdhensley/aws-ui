package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CloudWatchLogsService;
import com.overzealouspelican.awsui.service.SettingsService;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class LogsSavedFilterCoordinator {
    private final SettingsService settingsService;
    private final JTextField groupSearchField;
    private final JTextField streamSearchField;
    private final JComboBox<String> groupComboBox;
    private final JComboBox<CloudWatchLogsService.LogStreamOption> streamComboBox;
    private final JComboBox<LogsTimeframeOption> timeframeComboBox;
    private final JComboBox<String> jsonLogStreamFilterComboBox;
    private final JTextField clientSearchField;
    private final JTextField filterNameField;
    private final JList<SettingsService.SavedLogsFilter> savedFiltersList;
    private final DefaultListModel<SettingsService.SavedLogsFilter> savedFiltersListModel;
    private final String chooseGroupOption;
    private final String allJsonLogStreams;
    private final CloudWatchLogsService.LogStreamOption allStreamsOption;
    private final Consumer<String> showError;
    private final Consumer<String> setStatus;
    private final Runnable applyClientSideSearch;
    private final Consumer<Runnable> refreshLogGroups;
    private final RefreshStreams refreshLogStreams;
    private final Consumer<Runnable> loadLogs;
    private final Runnable repaintSavedFilters;
    private final Consumer<Boolean> setApplyingSavedFilter;
    private final Supplier<String> defaultSavedFilterNameSupplier;
    private final Consumer<String> defaultSavedFilterNameSetter;

    interface RefreshStreams {
        void run(boolean loadLogsAfterRefresh, Runnable onComplete);
    }

    LogsSavedFilterCoordinator(
        SettingsService settingsService,
        JTextField groupSearchField,
        JTextField streamSearchField,
        JComboBox<String> groupComboBox,
        JComboBox<CloudWatchLogsService.LogStreamOption> streamComboBox,
        JComboBox<LogsTimeframeOption> timeframeComboBox,
        JComboBox<String> jsonLogStreamFilterComboBox,
        JTextField clientSearchField,
        JTextField filterNameField,
        JList<SettingsService.SavedLogsFilter> savedFiltersList,
        DefaultListModel<SettingsService.SavedLogsFilter> savedFiltersListModel,
        String chooseGroupOption,
        String allJsonLogStreams,
        CloudWatchLogsService.LogStreamOption allStreamsOption,
        Consumer<String> showError,
        Consumer<String> setStatus,
        Runnable applyClientSideSearch,
        Consumer<Runnable> refreshLogGroups,
        RefreshStreams refreshLogStreams,
        Consumer<Runnable> loadLogs,
        Runnable repaintSavedFilters,
        Consumer<Boolean> setApplyingSavedFilter,
        Supplier<String> defaultSavedFilterNameSupplier,
        Consumer<String> defaultSavedFilterNameSetter
    ) {
        this.settingsService = settingsService;
        this.groupSearchField = groupSearchField;
        this.streamSearchField = streamSearchField;
        this.groupComboBox = groupComboBox;
        this.streamComboBox = streamComboBox;
        this.timeframeComboBox = timeframeComboBox;
        this.jsonLogStreamFilterComboBox = jsonLogStreamFilterComboBox;
        this.clientSearchField = clientSearchField;
        this.filterNameField = filterNameField;
        this.savedFiltersList = savedFiltersList;
        this.savedFiltersListModel = savedFiltersListModel;
        this.chooseGroupOption = chooseGroupOption;
        this.allJsonLogStreams = allJsonLogStreams;
        this.allStreamsOption = allStreamsOption;
        this.showError = showError;
        this.setStatus = setStatus;
        this.applyClientSideSearch = applyClientSideSearch;
        this.refreshLogGroups = refreshLogGroups;
        this.refreshLogStreams = refreshLogStreams;
        this.loadLogs = loadLogs;
        this.repaintSavedFilters = repaintSavedFilters;
        this.setApplyingSavedFilter = setApplyingSavedFilter;
        this.defaultSavedFilterNameSupplier = defaultSavedFilterNameSupplier;
        this.defaultSavedFilterNameSetter = defaultSavedFilterNameSetter;
    }

    void reloadSavedFiltersList(String selectedName) {
        String defaultSavedFilterName = LogsSavedFilterWorkflow.reloadSavedFiltersList(
            settingsService,
            savedFiltersListModel,
            savedFiltersList,
            selectedName
        );
        defaultSavedFilterNameSetter.accept(defaultSavedFilterName);
    }

    void saveCurrentFilter() {
        String name = filterNameField.getText() == null ? "" : filterNameField.getText().trim();
        if (name.isBlank()) {
            showError.accept("Enter a filter name before saving.");
            return;
        }

        String groupSearch = LogsSavedFilterWorkflow.textOrEmpty(groupSearchField);
        String selectedGroup = LogsSavedFilterWorkflow.selectedGroupValue(groupComboBox, chooseGroupOption);
        String streamSearch = LogsSavedFilterWorkflow.textOrEmpty(streamSearchField);
        String selectedStream = LogsSavedFilterWorkflow.selectedStreamValue(streamComboBox);
        String timeframeLabel = LogsSavedFilterWorkflow.selectedTimeframeLabel(timeframeComboBox);
        String jsonLogStream = LogsSavedFilterWorkflow.selectedJsonLogStreamValue(jsonLogStreamFilterComboBox, allJsonLogStreams);
        String clientSearch = LogsSavedFilterWorkflow.textOrEmpty(clientSearchField);

        settingsService.saveLogsFilter(new SettingsService.SavedLogsFilter(
            name,
            groupSearch,
            selectedGroup,
            streamSearch,
            selectedStream,
            timeframeLabel,
            jsonLogStream,
            clientSearch
        ));

        reloadSavedFiltersList(name);
        setStatus.accept("Saved filter: " + name);
    }

    void deleteSelectedSavedFilter(Component parent) {
        SettingsService.SavedLogsFilter selected = savedFiltersList.getSelectedValue();
        if (selected == null) {
            showError.accept("Select a saved filter to delete.");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
            parent,
            "Delete saved filter '" + selected.getName() + "'?",
            "Delete Filter",
            JOptionPane.YES_NO_OPTION
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        settingsService.removeLogsFilter(selected.getName());
        reloadSavedFiltersList(null);
        setStatus.accept("Deleted filter: " + selected.getName());
    }

    void applySelectedSavedFilter(boolean showErrorOnFailure) {
        SettingsService.SavedLogsFilter selected = savedFiltersList.getSelectedValue();
        if (selected == null) {
            if (showErrorOnFailure) {
                showError.accept("Select a saved filter to apply.");
            }
            return;
        }

        applySavedFilter(selected, showErrorOnFailure);
    }

    void applySavedFilter(SettingsService.SavedLogsFilter selected, boolean showErrorOnFailure) {
        if (selected == null) {
            if (showErrorOnFailure) {
                showError.accept("Select a saved filter to apply.");
            }
            return;
        }

        setApplyingSavedFilter.accept(true);
        filterNameField.setText(selected.getName());
        groupSearchField.setText(selected.getGroupSearch());
        streamSearchField.setText(selected.getStreamSearch());
        clientSearchField.setText(selected.getClientSearch());
        LogsSavedFilterWorkflow.selectTimeframeByLabel(timeframeComboBox, selected.getTimeframeLabel());

        refreshLogGroups.accept(() -> {
            LogsSavedFilterWorkflow.selectGroupByValue(groupComboBox, selected.getSelectedGroup());
            refreshLogStreams.run(false, () -> {
                LogsSavedFilterWorkflow.selectStreamByName(streamComboBox, allStreamsOption, selected.getSelectedStream());
                loadLogs.accept(() -> {
                    LogsSavedFilterWorkflow.selectJsonLogStreamByValue(
                        jsonLogStreamFilterComboBox,
                        allJsonLogStreams,
                        selected.getJsonLogStream()
                    );
                    applyClientSideSearch.run();
                    setStatus.accept("Applied filter: " + selected.getName());
                    setApplyingSavedFilter.accept(false);
                });
            });
        });
    }

    void applyDefaultSavedFilterOrRefresh() {
        String defaultName = settingsService.getDefaultLogsFilterName();
        if (defaultName == null || defaultName.isBlank()) {
            refreshLogGroups.accept(null);
            return;
        }

        List<SettingsService.SavedLogsFilter> filters = settingsService.getSavedLogsFilters();
        for (SettingsService.SavedLogsFilter filter : filters) {
            if (defaultName.equalsIgnoreCase(filter.getName())) {
                reloadSavedFiltersList(filter.getName());
                applySavedFilter(filter, false);
                return;
            }
        }

        refreshLogGroups.accept(null);
    }

    void setDefaultSavedFilter(SettingsService.SavedLogsFilter filter) {
        settingsService.setDefaultLogsFilterName(filter.getName());
        defaultSavedFilterNameSetter.accept(filter.getName());
        repaintSavedFilters.run();
        setStatus.accept("Default filter set: " + filter.getName());
    }

    void clearDefaultSavedFilter() {
        settingsService.clearDefaultLogsFilterName();
        defaultSavedFilterNameSetter.accept(null);
        repaintSavedFilters.run();
        setStatus.accept("Default filter cleared");
    }

    boolean isSelectedFilterDefault(SettingsService.SavedLogsFilter filter) {
        String defaultSavedFilterName = defaultSavedFilterNameSupplier.get();
        return defaultSavedFilterName != null && defaultSavedFilterName.equalsIgnoreCase(filter.getName());
    }
}