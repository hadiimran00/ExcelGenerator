    package org.example.ui.utilities;

    import java.util.Map;
    import java.util.concurrent.ConcurrentHashMap;

    public class GeneratedDataStore {

        // testId -> columnName -> generatedValue
        private static final Map<String, Map<String, String>> store = new ConcurrentHashMap<>();

        private static volatile String bearerToken;

        /**
         * Captured once per browser session right after login (see AuthTokenCapture)
         * and reused by every API-based flow (OrderBookingApiClient, etc.) instead of
         * each flow re-sniffing it from network traffic.
         */
        public static void storeBearerToken(String token) {
            bearerToken = token;
        }

        public static void clearBearerToken() {
            bearerToken = null;
        }

        public static String getBearerToken() {
            if (bearerToken == null || bearerToken.isBlank()) {
                throw new IllegalStateException(
                        "Bearer token was not captured after login. Ensure AuthTokenCapture.captureAndStore(driver) "
                                + "ran successfully before any API-based flow (Coupon/PartialReturnBudget) executes.");
            }
            return bearerToken;
        }

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