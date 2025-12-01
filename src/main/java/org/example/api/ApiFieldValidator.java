package org.example.api;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class ApiFieldValidator {

    private static final String BASE_URI = "https://dcodecnr2dev3.unilever.com";
    private static final String ENDPOINT = "/ngui/asset/i18n/en.json";
    private static final String BASELINE_PATH = "src/main/resources/expectedFields.json";
    private static final String REPORT_PATH = "target/jsonDiffReport.txt";

    public static void main(String[] args) {
        System.out.println("🚀 Starting UI Text Validation Test...");


        try {
            ObjectMapper mapper = new ObjectMapper();

            JsonNode expected = mapper.readTree(new File(BASELINE_PATH));
            System.out.println("✅ Loaded expected UI Text JSON file");
            RestAssured.useRelaxedHTTPSValidation();

            RestAssured.baseURI = BASE_URI;
            Response response = RestAssured
                    .given()
                    .relaxedHTTPSValidation()
                    .header("Accept", "application/json")
                    .when()
                    .get(ENDPOINT)
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();

            JsonNode actual = mapper.readTree(response.asString());
            System.out.println("✅ Fetched latest UI text content from the API");

            List<String> missing = new ArrayList<>();
            List<String> extra = new ArrayList<>();
            List<String> mismatched = new ArrayList<>();

            compareJson("", expected, actual, missing, extra, mismatched);

            writeReport(missing, extra, mismatched);


            try (FileWriter fw = new FileWriter(REPORT_PATH, true)) { // 'true' appends
                if (missing.isEmpty() && extra.isEmpty() && mismatched.isEmpty()) {
                    System.out.println("✅ All UI texts match expected values!");
                    fw.write("✅ All UI texts match expected values!\n");
                } else {
                    System.out.println("❌ Differences found! Check " + REPORT_PATH);
                    fw.write("\n❌ Differences found! See above details.\n");
                    System.exit(1);
                }
            } catch (Exception e) {
                System.out.println("❌ Failed to run Test! " + e.getMessage());
                try (FileWriter fw = new FileWriter(REPORT_PATH, true)) {
                    fw.write("❌ Failed to update report: " + e.getMessage() + "\n");
                } catch (IOException ioException) {
                    System.out.println("⚠️ Also failed to write error to report: " + ioException.getMessage());
                }
                throw new RuntimeException("Failed to update report", e);
            }




        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void compareJson(String path,
                                    JsonNode expected,
                                    JsonNode actual,
                                    List<String> missing,
                                    List<String> extra,
                                    List<String> mismatched) {

        // 1. Handle missing keys and mismatched values
        if (expected.isObject()) {
            Iterator<String> fieldNames = expected.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                String currentPath = path.isEmpty() ? field : path + "." + field;

                if (!actual.has(field)) {
                    missing.add(currentPath);
                } else {
                    compareJson(currentPath, expected.get(field), actual.get(field), missing, extra, mismatched);
                }
            }

            // 2. Find extra fields (present in actual but not expected)
            Iterator<String> actualFields = actual.fieldNames();
            while (actualFields.hasNext()) {
                String field = actualFields.next();
                if (!expected.has(field)) {
                    String currentPath = path.isEmpty() ? field : path + "." + field;
                    extra.add(currentPath);
                }
            }
        }
        else if (expected.isArray() && actual.isArray()) {
            int min = Math.min(expected.size(), actual.size());
            for (int i = 0; i < min; i++) {
                compareJson(path + "[" + i + "]", expected.get(i), actual.get(i), missing, extra, mismatched);
            }
            if (expected.size() > actual.size())
                missing.add(path + " has " + (expected.size() - actual.size()) + " missing elements");
            if (actual.size() > expected.size())
                extra.add(path + " has " + (actual.size() - expected.size()) + " extra elements");
        }
        else if (!expected.equals(actual)) {
            mismatched.add(path + " → expected: " + expected + ", actual: " + actual);
        }
    }

    private static void writeReport(List<String> missing, List<String> extra, List<String> mismatched) {
        try (FileWriter fw = new FileWriter(REPORT_PATH)) {
            fw.write("=== UI Text Validation Report ===\n\n");

            fw.write("Missing Keys (" + missing.size() + "):\n");
            for (String s : missing) fw.write(" - " + s + "\n");
            fw.write("\n");

            fw.write("New Keys (" + extra.size() + "):\n");
            for (String s : extra) fw.write(" + " + s + "\n");
            fw.write("\n");

            fw.write("Value Mismatches (" + mismatched.size() + "):\n");
            for (String s : mismatched) fw.write(" * " + s + "\n");

            fw.write("\n===============================\n");
            System.out.println("\n📄 Report generated at: " + REPORT_PATH);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write report file", e);
        }
    }
}
