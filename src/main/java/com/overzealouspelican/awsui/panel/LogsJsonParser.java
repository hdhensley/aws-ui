package com.overzealouspelican.awsui.panel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LogsJsonParser {
    private LogsJsonParser() {
    }

    static List<LogsParsedJsonRow> parseJsonRows(String rawLogs) {
        if (rawLogs == null || rawLogs.isBlank()) {
            return List.of();
        }

        String[] lines = rawLogs.split("\\R");
        List<LogsParsedJsonRow> rows = new ArrayList<>();

        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }

            int jsonStart = line.indexOf('{');
            if (jsonStart < 0) {
                continue;
            }

            String prefixTimestamp = line.substring(0, jsonStart).trim();
            String jsonText = line.substring(jsonStart).trim();

            try {
                JsonElement element = JsonParser.parseString(jsonText);
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject object = element.getAsJsonObject();
                Map<String, String> values = new LinkedHashMap<>();
                values.put("timestamp", prefixTimestamp);
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    values.put(entry.getKey(), flattenJsonValue(entry.getValue()));
                }
                rows.add(new LogsParsedJsonRow(values));
            } catch (Exception ignored) {
                // Ignore malformed lines and keep any valid JSON rows we can recover.
            }
        }

        return rows.isEmpty() ? List.of() : rows;
    }

    private static String flattenJsonValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "";
        }
        if (value.isJsonPrimitive()) {
            return value.getAsJsonPrimitive().getAsString();
        }
        return value.toString();
    }
}