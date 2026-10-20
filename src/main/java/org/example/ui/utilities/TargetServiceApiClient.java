package org.example.ui.utilities;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

/**
 * Calls target-service process-log endpoints (split / achievement) with the logged-in session,
 * the same way OrderBookingApiClient calls order-service.
 */
public class TargetServiceApiClient {

    private static final Logger logger = LoggerUtil.getLogger(TargetServiceApiClient.class);
    public static final String TARGET_SPLIT_ENDPOINT = "/target-service/api/v1/process-log/targetSplit-process-with-log";
    public static final String TARGET_ACHIEVEMENT_ENDPOINT = "/target-service/api/v1/process-log/targetAchievement-process-with-log";

    public static Response post(WebDriver driver, String endpoint, String body, String label) {
        String baseUrl = ApiSession.getBaseUrl(driver);

        RequestSpecification request = RestAssured.given()
                .baseUri(baseUrl)
                .relaxedHTTPSValidation()
                .header("Accept", "application/json, text/plain, */*")
                .header("App-Option-Id", "TARGET_LAYOUT")
                .header("Authorization", GeneratedDataStore.getBearerToken())
                .header("Content-Type", "application/json")
                .header("Origin", baseUrl)
                .header("Cookie", "SESSIONID=" + ApiSession.getSessionCookieValue(driver));
        // Some endpoints (e.g. PH autoSplitPJPTarget) take no body
        if (body != null && !body.isBlank()) {
            request.body(body);
        }

        Response response = request
                .when()
                .post(endpoint)
                .then()
                .extract()
                .response();

        logger.info("[{}] POST {} -> HTTP {} | {}", label, endpoint, response.statusCode(), response.asString());
        return response;
    }
}
