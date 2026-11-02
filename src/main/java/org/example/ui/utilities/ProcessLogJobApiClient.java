package org.example.ui.utilities;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

/**
 * Runs a distributor's incentive jobs the way Process Log Viewer's manual run does, reusing the logged-in browser
 * session's SESSIONID cookie and the bearer token captured at login.
 */
public class ProcessLogJobApiClient {

    private static final Logger logger = LoggerUtil.getLogger(ProcessLogJobApiClient.class);
    private static final String ACHIEVEMENT_ENDPOINT = "/target-service/api/v1/process-log/incentive-process-with-log-manual";
    private static final String CLAIM_ENDPOINT = "/claimautomation-service/api/v1/process-log/generate-incentive-claim-with-log-manual";

    /** Incentive achievement job; returns the response body. */
    public static String runIncentiveAchievement(WebDriver driver, String distributor) {
        return run(driver, ACHIEVEMENT_ENDPOINT, distributor, "Incentive achievement job");
    }

    /** Incentive claim job; returns the response body. */
    public static String generateIncentiveClaim(WebDriver driver, String distributor) {
        return run(driver, CLAIM_ENDPOINT, distributor, "Incentive claim job");
    }

    // Throws unless the call returns 2xx (the achievement job answers 202: started in the background)
    private static String run(WebDriver driver, String endpoint, String distributor, String label) {
        String baseUrl = ApiSession.getBaseUrl(driver);
        Response response = RestAssured.given()
                .baseUri(baseUrl)
                .relaxedHTTPSValidation()
                .header("Accept", "application/json, text/plain, */*")
                .header("App-Option-Id", "PROCESS_LOG_VIEWER")
                .header("Authorization", GeneratedDataStore.getBearerToken())
                .header("Content-Type", "text/plain")
                .header("Referer", baseUrl + "/ngui/logs/process-log-viewer")
                .header("Cookie", "SESSIONID=" + ApiSession.getSessionCookieValue(driver))
                .queryParam("entityCode", distributor)
                .body("")
                .when()
                .post(endpoint)
                .then()
                .extract()
                .response();

        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException(label + " for " + distributor + " failed with status "
                    + response.statusCode() + ": " + response.asString());
        }
        logger.info("✅ {} for {}: {}", label, distributor, response.asString());
        return response.asString();
    }
}
