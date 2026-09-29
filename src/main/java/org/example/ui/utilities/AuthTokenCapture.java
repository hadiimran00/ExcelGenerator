package org.example.ui.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

import java.util.List;
import java.util.Map;

/**
 * Captures the bearer access token right after login and stores it in
 * GeneratedDataStore for every API-based flow (OrderBookingApiClient, etc.) to reuse.
 * The app never exposes this token via a cookie or localStorage - the SPA fires its own
 * POST .../oauth2/token exchange after login and holds the token in memory only - so it
 * is read here directly from that response body via Chrome's CDP Network domain
 * (goog:loggingPrefs performance log for the request, executeCdpCommand for the body).
 */
public class AuthTokenCapture {

    private static final Logger logger = LoggerUtil.getLogger(AuthTokenCapture.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOKEN_URL_MARKER = "/oauth2/token";
    private static final int MAX_ATTEMPTS = 5;
    private static final long POLL_INTERVAL_MS = 500;

    public static void captureAndStore(WebDriver driver) {
        GeneratedDataStore.clearBearerToken();

        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            logger.warn("⚠️ Driver is not Chromium-based; cannot capture bearer token via CDP.");
            return;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String token = findAccessToken(driver, chromiumDriver);
            if (token != null) {
                GeneratedDataStore.storeBearerToken(token);
                logger.info("🔑 Captured bearer token after login (masked): {}...",
                        token.substring(0, Math.min(20, token.length())));
                return;
            }
            sleep(POLL_INTERVAL_MS);
        }

        logger.warn("⚠️ Could not capture bearer token after login within {} attempts. "
                + "API-based flows (Coupon/PartialReturnBudget) will fail if used for this user.", MAX_ATTEMPTS);
    }

    private static String findAccessToken(WebDriver driver, ChromiumDriver chromiumDriver) {
        List<LogEntry> entries = driver.manage().logs().get(LogType.PERFORMANCE).getAll();
        for (LogEntry entry : entries) {
            try {
                JsonNode message = MAPPER.readTree(entry.getMessage()).path("message");
                if (!"Network.responseReceived".equals(message.path("method").asText())) {
                    continue;
                }
                JsonNode params = message.path("params");
                String url = params.path("response").path("url").asText();
                if (!url.contains(TOKEN_URL_MARKER)) {
                    continue;
                }

                String requestId = params.path("requestId").asText();
                Map<String, Object> result = chromiumDriver.executeCdpCommand(
                        "Network.getResponseBody", Map.of("requestId", requestId));
                Object body = result.get("body");
                if (body == null) {
                    continue;
                }

                JsonNode tokenJson = MAPPER.readTree(body.toString());
                String accessToken = tokenJson.path("access_token").asText(null);
                if (accessToken == null || accessToken.isBlank()) {
                    continue;
                }
                String tokenType = tokenJson.path("token_type").asText("Bearer");
                return tokenType + " " + accessToken;
            } catch (Exception ignored) {
                // Not the token response, or its body isn't retrievable (yet) - keep scanning.
            }
        }
        return null;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
