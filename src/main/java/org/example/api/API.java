package org.example.api;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.*;
import java.util.stream.Collectors;

public class API {
    public static void main(String[] args) {
        Result result = compareFieldNames();
        if (result.success()) {
            System.out.println("Success: All expected DYP values present, no extras.");
        } else {
            System.out.println("Failure: Field differences found.");
            System.out.println("Missing: " + result.missing());
            System.out.println("Unexpected: " + result.unexpected());
        }
    }

    public static Result compareFieldNames() {
        RestAssured.baseURI = "https://dcodecnr1dev1.unilever.com";
        String sessionId = "ZGE5OTllM2EtYTM0MS00NmNmLWJhYjgtNGUzY2VjNzMyODAx";

        Response response = RestAssured
                .given()
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8")
                .header("Referer", "https://dcodecnr1dev1.unilever.com/ngui/multiple-organization?code=...")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)...")
                .cookie("SESSIONID", sessionId)
                .when()
                .get("/ngui/asset/i18n/en.json")
                .then()
                .statusCode(200)
                .extract()
                .response();

        Map<String, Object> jsonResponse = response.jsonPath().getMap("");

        // Extract and normalize actual DYP values
        Set<String> actualSet = jsonResponse.entrySet()
                .stream()
                .filter(e -> e.getKey().startsWith("DYP"))
                .map(e -> normalize(e.getValue().toString()))
                .collect(Collectors.toSet());

        // Load and normalize expected values from Excel
        String excelPath = "Resources/fieldNamesPK.xlsx";
        List<String> expectedValuesRaw = ExpectedValuesLoader.loadExpected(excelPath);

        Set<String> expectedSet = expectedValuesRaw.stream()
                .map(API::normalize)
                .collect(Collectors.toSet());

        // Compute differences
        Set<String> missing = new HashSet<>(expectedSet);
        missing.removeAll(actualSet);

        Set<String> unexpected = new HashSet<>(actualSet);
        unexpected.removeAll(expectedSet);

        // Log unexpected fields
        if (!unexpected.isEmpty()) {
            System.out.println("Unexpected fields found in API response:");
            unexpected.forEach(System.out::println);
        }

        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            return new Result(false, missing, unexpected);
        } else {
            return new Result(true, Collections.emptySet(), Collections.emptySet());
        }
    }

    private static String normalize(String input) {
        if (input == null) return "";
        return input.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    public record Result(boolean success, Set<String> missing, Set<String> unexpected) {
    }
}
