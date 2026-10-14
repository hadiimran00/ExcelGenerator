package org.example.ui.utilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.example.ui.pages.basePage.requireScenarioValue;

/**
 * Books Cash Memo orders directly against the order-service REST API
 * (initiateSaveOrderLTSaga) instead of driving the Order Booking screen,
 * reusing the currently logged-in browser session's SESSIONID cookie and the
 * bearer token AuthTokenCapture captured once right after login.
 */
public class OrderBookingApiClient {

    private static final Logger logger = LoggerUtil.getLogger(OrderBookingApiClient.class);
    private static final String ENDPOINT = "/order-service/api/v1/cashmemo/initiateSaveOrderLTSaga";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String createOrder(WebDriver driver, Map<String, String> scenarioData, String label) {
        String outlet = scenarioData.get("OUTLET");
        String sellCat = scenarioData.get("SELLCAT");
        String section = scenarioData.get("SECTION");
        String daily = scenarioData.get("daily");
        String schemeCode = scenarioData.get("schemeCode");
        String loyaltyPromoId = scenarioData.get("loyaltyPromoId");
        String productCode = scenarioData.get("productCode");
        String quantityPcs = scenarioData.get("QuantityPCS");

        requireScenarioValue(scenarioData, "OUTLET", outlet);
        requireScenarioValue(scenarioData, "SELLCAT", sellCat);
        requireScenarioValue(scenarioData, "SECTION", section);
        requireScenarioValue(scenarioData, "daily", daily);
        requireScenarioValue(scenarioData, "schemeCode", schemeCode);
        requireScenarioValue(scenarioData, "loyaltyPromoId", loyaltyPromoId);
        requireScenarioValue(scenarioData, "productCode", productCode);
        requireScenarioValue(scenarioData, "QuantityPCS", quantityPcs);

        String bearerToken = GeneratedDataStore.getBearerToken();
        String sessionId = getSessionCookieValue(driver);
        String baseUrl = getBaseUrl(driver);
        // Optional per-country overrides: the booking API wants entity codes where the UI dropdowns
        // (which share the OUTLET/SELLCAT/SECTION keys) may need descriptive text, and the loyalty
        // scheme code can differ from the loyaltyPromoId searched for on the screens. Defaults keep
        // the original PK payload.
        String apiOutlet = optional(scenarioData, "apiOutlet", outlet);
        String apiSellCat = optional(scenarioData, "apiSellCat", sellCat);
        String apiSection = optional(scenarioData, "apiSection", section);
        String loyaltySchemeCode = optional(scenarioData, "LOYALTYB2BPROMO", loyaltyPromoId);
        Map<String, Object> detailOverrides = new LinkedHashMap<>();
        detailOverrides.put("principalCode", optional(scenarioData, "detailPrincipalCode", "1"));
        detailOverrides.put("barCode", optional(scenarioData, "barCode", "6281006704732"));
        detailOverrides.put("rate", new BigDecimal(optional(scenarioData, "rate", "128.3475")));
        detailOverrides.put("retailRate", new BigDecimal(optional(scenarioData, "retailRate", "128.3475")));
        detailOverrides.put("taxRate", new BigDecimal(optional(scenarioData, "taxRate", "0")));
        String payload = buildPayload(apiOutlet, apiSellCat, apiSection, daily, schemeCode, loyaltySchemeCode,
                productCode, quantityPcs, detailOverrides);

        logger.info("🔑 [{}] Using token (masked): {}...{} | SESSIONID (masked): {}...{} | all cookies: {} | base URL: {}",
                label,
                bearerToken.substring(0, Math.min(20, bearerToken.length())),
                bearerToken.substring(Math.max(0, bearerToken.length() - 10)),
                sessionId.substring(0, Math.min(6, sessionId.length())),
                sessionId.substring(Math.max(0, sessionId.length() - 6)),
                driver.manage().getCookies().stream().map(c -> c.getName() + "@" + c.getPath()).toList(),
                baseUrl);

        Response response = RestAssured.given()
                .baseUri(baseUrl)
                .relaxedHTTPSValidation()
                .header("Accept", "application/json, text/plain, */*")
                .header("App-Option-Id", "ORDER_BOOKING")
                .header("Authorization", bearerToken)
                .header("Content-Type", "application/json")
                .header("action-type", "B")
                .header("fetch", "OrderBooking-Save")
                .header("Origin", baseUrl)
                .header("Referer", baseUrl + "/ngui/order-booking/order-fillter")
                .header("Cookie", "SESSIONID=" + sessionId)
                .body(payload)
                .when()
                .post(ENDPOINT)
                .then()
                .extract()
                .response();

        if (response.statusCode() != 200) {
            logger.error("❌ [{}] Order Booking API response headers: {}", label, response.headers());
            throw new RuntimeException(label + " Order Booking API call failed with status "
                    + response.statusCode() + ": " + response.asString());
        }

        String orderNo = response.jsonPath().getString("documentNo");
        if (orderNo == null || orderNo.isBlank()) {
            throw new RuntimeException(label + " Order Booking API call did not return a documentNo. Response: "
                    + response.asString());
        }

        logger.info("✅ [{}] Order booked via API: {}", label, orderNo);
        return orderNo;
    }

    private static String buildPayload(
            String outlet, String sellCat, String section, String daily,
            String schemeCode, String loyaltyPromoId, String productCode, String quantityPcs,
            Map<String, Object> detailOverrides) {
        try {
            Map<String, Object> scheme = new LinkedHashMap<>();
            scheme.put("schemeCode", schemeCode);
            scheme.put("schemeValue", "10");
            scheme.put("schemeIdentifier", "COUPON");

            // LOYALTYB2B: a second, separately-searchable scheme attached alongside the coupon -
            // a flat PKR 66 discount, not scaled by quantity like the coupon's slab formula.
            Map<String, Object> loyaltyScheme = new LinkedHashMap<>();
            loyaltyScheme.put("schemeCode", loyaltyPromoId);
            loyaltyScheme.put("schemeValue", "66");
            loyaltyScheme.put("schemeIdentifier", "COUPON");

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("principalCode", detailOverrides.get("principalCode"));
            detail.put("serialNo", "1");
            detail.put("productCode", productCode);
            detail.put("barCode", detailOverrides.get("barCode"));
            detail.put("batch", "1-1");
            detail.put("quantity1", 0);
            detail.put("quantity2", 0);
            detail.put("quantity3", Integer.parseInt(quantityPcs.trim()));
            detail.put("rate", detailOverrides.get("rate"));
            detail.put("retailRate", detailOverrides.get("retailRate"));
            detail.put("taxRate", detailOverrides.get("taxRate"));
            detail.put("reason", "");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("principalCode", 1);
            body.put("documentType", "CM-01");
            body.put("documentDate", Instant.now().toString());
            body.put("entityLevel1TypeC", "OUTL");
            body.put("entityLevel1CodeC", outlet);
            body.put("entityLevel1LogTypeK", "SCTG");
            body.put("entityLevel1LogCodeK", sellCat);
            body.put("entityLevel1LogTypeL", "SECT");
            body.put("entityLevel1LogCodeL", section);
            body.put("daily", daily);
            body.put("cashmemoType", "01");
            body.put("documentNo", "");
            body.put("entryMode", "A");
            body.put("comments", "");
            body.put("refNo", "");
            body.put("externalScheme", List.of(scheme, loyaltyScheme));
            body.put("cashMemoDetails", List.of(detail));

            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Order Booking API payload", e);
        }
    }

    private static String optional(Map<String, String> data, String key, String fallback) {
        String value = data.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String getSessionCookieValue(WebDriver driver) {
        Cookie cookie = driver.manage().getCookieNamed("SESSIONID");
        if (cookie == null) {
            throw new RuntimeException("SESSIONID cookie not found in browser session.");
        }
        return cookie.getValue();
    }

    private static String getBaseUrl(WebDriver driver) {
        URI uri = URI.create(driver.getCurrentUrl());
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
