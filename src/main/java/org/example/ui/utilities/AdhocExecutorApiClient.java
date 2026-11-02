package org.example.ui.utilities;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.Map;

/**
 * Runs an Adhoc Executor job group the way the Adhoc Executor screen's Execute does, reusing the logged-in browser
 * session's SESSIONID cookie and the bearer token captured at login. The executing user (e.g. BD "demo") is sent like
 * the screen's popup does, so the test user does not need the right to execute the group.
 */
public class AdhocExecutorApiClient {

    private static final Logger logger = LoggerUtil.getLogger(AdhocExecutorApiClient.class);
    private static final String COMBOS_ENDPOINT = "/global-service/api/v1/adhocExecutor/getAdhocDetailForCombos";
    private static final String EXECUTE_ENDPOINT = "/global-service/api/v1/adhocExecutor/execute";

    /**
     * Executes the group source, given by the names shown on the screen (e.g. "S&DNG",
     * "Incentive auto reward generation-7021"); returns the job's message.
     */
    public static String execute(WebDriver driver, String organization, String group, String groupSource,
                                 String executeUser, String executePassword) {
        String baseUrl = ApiSession.getBaseUrl(driver);
        String cookie = "SESSIONID=" + ApiSession.getSessionCookieValue(driver);

        // The screen sends codes, not names: look them up the way its dropdowns are filled
        JsonPath combos = RestAssured.given()
                .baseUri(baseUrl)
                .relaxedHTTPSValidation()
                .header("Accept", "application/json, text/plain, */*")
                .header("Authorization", GeneratedDataStore.getBearerToken())
                .header("Cookie", cookie)
                .when()
                .get(COMBOS_ENDPOINT)
                .then()
                .extract()
                .jsonPath();
        Map<String, Object> org = findByDesc(combos.getList("organizations"), organization, "Organization");
        Map<String, Object> grp = findByDesc(listOf(org, "groups"), group, "Group in " + organization);
        Map<String, Object> source = findByDesc(listOf(grp, "groupDetails"), groupSource, "Group source in " + group);

        Map<String, Object> body = Map.of(
                "organizationCode", org.get("id"),
                "groupCode", grp.get("id"),
                "sourceCode", source.get("id"),
                "sourceQueryParams", Map.of(),
                "identification", "adhoc",
                "event", "Adhoc_Execution");
        Response response = RestAssured.given()
                .baseUri(baseUrl)
                .relaxedHTTPSValidation()
                .header("Accept", "application/json, text/plain, */*")
                .header("Authorization", GeneratedDataStore.getBearerToken())
                .header("Content-Type", "application/json")
                .header("Cookie", cookie)
                .queryParam("user", executeUser)
                .queryParam("password", executePassword)
                .body(body)
                .when()
                .post(EXECUTE_ENDPOINT)
                .then()
                .extract()
                .response();

        // The screen treats the body's status as the outcome: 200 done, 206 needs extra parameters, 500 failed
        String status = response.statusCode() / 100 == 2 ? String.valueOf(response.jsonPath().getString("status")) : null;
        String description = response.statusCode() / 100 == 2 ? response.jsonPath().getString("description") : null;
        if (!"200".equals(status)) {
            throw new RuntimeException("Adhoc job " + groupSource + " failed with status " + response.statusCode()
                    + (status == null ? "" : " / " + status) + ": " + response.asString());
        }
        logger.info("✅ Adhoc job {}: {}", groupSource, description);
        return description;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> parent, String key) {
        return (List<Map<String, Object>>) parent.get(key);
    }

    private static Map<String, Object> findByDesc(List<Map<String, Object>> items, String desc, String what) {
        if (items != null) {
            for (Map<String, Object> item : items) {
                if (desc.trim().equalsIgnoreCase(String.valueOf(item.get("desc")).trim())) {
                    return item;
                }
            }
        }
        throw new IllegalArgumentException(what + " [" + desc + "] not found on the Adhoc Executor");
    }
}
