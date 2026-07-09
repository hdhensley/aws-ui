package com.overzealouspelican.awsui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Stores and applies app-level settings such as theme preference.
 */
public class SettingsService {

    private static final Preferences PREFS = Preferences.userNodeForPackage(SettingsService.class);
    private static final String THEME_KEY = "theme";
    private static final String DEFAULT_THEME = "FlatLaf IntelliJ";
    private static final String AWS_PROFILE_KEY = "aws_profile";
    private static final String LOGS_SAVED_FILTERS_KEY = "logs_saved_filters";
    private static final String LOGS_DEFAULT_FILTER_KEY = "logs_default_filter";

    public static class SavedLogsFilter {
        private final String name;
        private final String groupSearch;
        private final String selectedGroup;
        private final String streamSearch;
        private final String selectedStream;
        private final String timeframeLabel;
        private final String jsonLogStream;
        private final String clientSearch;

        public SavedLogsFilter(
            String name,
            String groupSearch,
            String selectedGroup,
            String streamSearch,
            String selectedStream,
            String timeframeLabel,
            String jsonLogStream,
            String clientSearch
        ) {
            this.name = name;
            this.groupSearch = groupSearch;
            this.selectedGroup = selectedGroup;
            this.streamSearch = streamSearch;
            this.selectedStream = selectedStream;
            this.timeframeLabel = timeframeLabel;
            this.jsonLogStream = jsonLogStream;
            this.clientSearch = clientSearch;
        }

        public String getName() {
            return name;
        }

        public String getGroupSearch() {
            return groupSearch;
        }

        public String getSelectedGroup() {
            return selectedGroup;
        }

        public String getStreamSearch() {
            return streamSearch;
        }

        public String getSelectedStream() {
            return selectedStream;
        }

        public String getTimeframeLabel() {
            return timeframeLabel;
        }

        public String getJsonLogStream() {
            return jsonLogStream;
        }

        public String getClientSearch() {
            return clientSearch;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class ThemeOption {
        private final String displayName;
        private final String className;

        public ThemeOption(String displayName, String className) {
            this.displayName = displayName;
            this.className = className;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getClassName() {
            return className;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public ThemeOption[] getAvailableThemes() {
        return new ThemeOption[]{
            new ThemeOption("FlatLaf Light", FlatLightLaf.class.getName()),
            new ThemeOption("FlatLaf Dark", FlatDarkLaf.class.getName()),
            new ThemeOption("FlatLaf IntelliJ", FlatIntelliJLaf.class.getName()),
            new ThemeOption("FlatLaf Darcula", FlatDarculaLaf.class.getName())
        };
    }

    public String getSavedTheme() {
        return PREFS.get(THEME_KEY, DEFAULT_THEME);
    }

    public void saveTheme(String themeName) {
        PREFS.put(THEME_KEY, themeName);
    }

    public void resetTheme() {
        PREFS.remove(THEME_KEY);
    }

    public String getDefaultTheme() {
        return DEFAULT_THEME;
    }

    public void saveAwsProfile(String profileName) {
        PREFS.put(AWS_PROFILE_KEY, profileName);
    }

    public String getSavedAwsProfile() {
        return PREFS.get(AWS_PROFILE_KEY, null);
    }

    public List<SavedLogsFilter> getSavedLogsFilters() {
        String raw = PREFS.get(LOGS_SAVED_FILTERS_KEY, "[]");
        List<SavedLogsFilter> filters = new ArrayList<>();

        try {
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonArray()) {
                return filters;
            }

            JsonArray array = parsed.getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject object = element.getAsJsonObject();
                String name = getString(object, "name");
                if (name.isBlank()) {
                    continue;
                }

                filters.add(new SavedLogsFilter(
                    name,
                    getString(object, "groupSearch"),
                    getString(object, "selectedGroup"),
                    getString(object, "streamSearch"),
                    getString(object, "selectedStream"),
                    getString(object, "timeframeLabel"),
                    getString(object, "jsonLogStream"),
                    getString(object, "clientSearch")
                ));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }

        return filters;
    }

    public void saveLogsFilter(SavedLogsFilter filter) {
        List<SavedLogsFilter> filters = new ArrayList<>(getSavedLogsFilters());
        int existingIndex = -1;
        for (int i = 0; i < filters.size(); i++) {
            if (filters.get(i).getName().equalsIgnoreCase(filter.getName())) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex >= 0) {
            filters.set(existingIndex, filter);
        } else {
            filters.add(filter);
        }

        persistSavedLogsFilters(filters);
    }

    public void removeLogsFilter(String filterName) {
        if (filterName == null || filterName.isBlank()) {
            return;
        }

        List<SavedLogsFilter> filters = new ArrayList<>(getSavedLogsFilters());
        filters.removeIf(filter -> filter.getName().equalsIgnoreCase(filterName));
        persistSavedLogsFilters(filters);

        String defaultFilterName = getDefaultLogsFilterName();
        if (defaultFilterName != null && defaultFilterName.equalsIgnoreCase(filterName)) {
            clearDefaultLogsFilterName();
        }
    }

    public String getDefaultLogsFilterName() {
        String value = PREFS.get(LOGS_DEFAULT_FILTER_KEY, "").trim();
        return value.isBlank() ? null : value;
    }

    public void setDefaultLogsFilterName(String filterName) {
        if (filterName == null || filterName.isBlank()) {
            clearDefaultLogsFilterName();
            return;
        }
        PREFS.put(LOGS_DEFAULT_FILTER_KEY, filterName.trim());
    }

    public void clearDefaultLogsFilterName() {
        PREFS.remove(LOGS_DEFAULT_FILTER_KEY);
    }

    public void applyTheme(String themeClassName) throws Exception {
        UIManager.setLookAndFeel(themeClassName);
        for (java.awt.Window window : java.awt.Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
        }
    }

    public void loadAndApplyTheme() {
        String savedThemeName = getSavedTheme();
        String themeClassName = getThemeClassName(savedThemeName);

        if (themeClassName == null) {
            themeClassName = FlatIntelliJLaf.class.getName();
        }

        try {
            UIManager.setLookAndFeel(themeClassName);
        } catch (Exception ex) {
            try {
                UIManager.setLookAndFeel(new FlatLightLaf());
            } catch (Exception ignored) {
                // Nothing else to do here.
            }
        }
    }

    private void persistSavedLogsFilters(List<SavedLogsFilter> filters) {
        JsonArray array = new JsonArray();
        for (SavedLogsFilter filter : filters) {
            JsonObject object = new JsonObject();
            object.addProperty("name", safe(filter.getName()));
            object.addProperty("groupSearch", safe(filter.getGroupSearch()));
            object.addProperty("selectedGroup", safe(filter.getSelectedGroup()));
            object.addProperty("streamSearch", safe(filter.getStreamSearch()));
            object.addProperty("selectedStream", safe(filter.getSelectedStream()));
            object.addProperty("timeframeLabel", safe(filter.getTimeframeLabel()));
            object.addProperty("jsonLogStream", safe(filter.getJsonLogStream()));
            object.addProperty("clientSearch", safe(filter.getClientSearch()));
            array.add(object);
        }
        PREFS.put(LOGS_SAVED_FILTERS_KEY, array.toString());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }

    private String getThemeClassName(String displayName) {
        for (ThemeOption theme : getAvailableThemes()) {
            if (theme.getDisplayName().equals(displayName)) {
                return theme.getClassName();
            }
        }
        return null;
    }
}
