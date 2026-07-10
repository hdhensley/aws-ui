package com.overzealouspelican.awsui.panel;

import java.util.Locale;

final class LogsTextFilter {
    private LogsTextFilter() {
    }

    static String filterTextLogs(String rawLogs, String query) {
        if (rawLogs == null) {
            return "";
        }
        if (query == null || query.isBlank()) {
            return rawLogs;
        }

        String[] lines = rawLogs.split("\\R");
        String needle = query.toLowerCase(Locale.ROOT);
        StringBuilder filtered = new StringBuilder();
        int matches = 0;

        for (String line : lines) {
            if (line.toLowerCase(Locale.ROOT).contains(needle)) {
                filtered.append(line).append(System.lineSeparator());
                matches++;
            }
        }

        if (matches == 0) {
            return "No client-side matches for: " + query;
        }

        return filtered.toString();
    }
}