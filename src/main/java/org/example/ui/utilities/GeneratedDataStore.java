package org.example.ui.utilities;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GeneratedDataStore {

    // testId -> columnName -> generatedValue
    private static final Map<String, Map<String, String>> store = new ConcurrentHashMap<>();

    public static void store(String testId, String columnName, String value) {
        store.computeIfAbsent(testId, k -> new ConcurrentHashMap<>())
                .put(columnName, value);
    }

    public static String get(String testId, String columnName) {
        Map<String, String> row = store.get(testId);
        return (row != null) ? row.getOrDefault(columnName, "") : "";
    }

    public static Map<String, String> getAll(String testId) {
        return store.getOrDefault(testId, Map.of());
    }

    public static void clear(String testId) {
        store.remove(testId);
    }
}