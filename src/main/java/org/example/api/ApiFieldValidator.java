//package org.example.api;
//
//import io.restassured.RestAssured;
//import io.restassured.response.Response;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//import java.io.File;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.util.*;
//
//public class ApiFieldValidator {
//
//    private static final String BASE_URI = "https://dcodecnr1dev1.unilever.com";
//    private static final String ENDPOINT = "/ngui/asset/i18n/en.json";
//    private static final String BASELINE_PATH = "src/main/resources/expectedFields.json";
//    private static final String REPORT_PATH = "target/jsonDiffReport.txt";
//
//    public static void main(String[] args) {
//        System.out.println("🚀 Starting UI Text Validation Test...");
//
//
//        try {
//            ObjectMapper mapper = new ObjectMapper();
//
//            JsonNode expected = mapper.readTree(new File(BASELINE_PATH));
//            System.out.println("✅ Loaded expected UI Text JSON file");
//            RestAssured.useRelaxedHTTPSValidation();
//
//            RestAssured.baseURI = BASE_URI;
//            Response response = RestAssured
//                    .given()
//                    .relaxedHTTPSValidation()
//                    .header("Accept", "application/json")
//                    .when()
//                    .get(ENDPOINT)
//                    .then()
//                    .statusCode(200)
//                    .extract()
//                    .response();
//
//            JsonNode actual = mapper.readTree(response.asString());
//            System.out.println("✅ Fetched latest UI text content from the API");
//
//            List<String> missing = new ArrayList<>();
//            List<String> extra = new ArrayList<>();
//            List<String> mismatched = new ArrayList<>();
//
//            compareJson("", expected, actual, missing, extra, mismatched);
//
//            writeReport(missing, extra, mismatched);
//
//
//            try (FileWriter fw = new FileWriter(REPORT_PATH, true)) { // 'true' appends
//                if (missing.isEmpty() && extra.isEmpty() && mismatched.isEmpty()) {
//                    System.out.println("✅ All UI texts match expected values!");
//                    fw.write("✅ All UI texts match expected values!\n");
//                } else {
//                    System.out.println("❌ Differences found! Check " + REPORT_PATH);
//                    fw.write("\n❌ Differences found! See above details.\n");
//                    System.exit(1);
//                }
//            } catch (Exception e) {
//                System.out.println("❌ Failed to run Test! " + e.getMessage());
//                try (FileWriter fw = new FileWriter(REPORT_PATH, true)) {
//                    fw.write("❌ Failed to update report: " + e.getMessage() + "\n");
//                } catch (IOException ioException) {
//                    System.out.println("⚠️ Also failed to write error to report: " + ioException.getMessage());
//                }
//                throw new RuntimeException("Failed to update report", e);
//            }
//
//
//
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.exit(1);
//        }
//    }
//
//    private static void compareJson(String path,
//                                    JsonNode expected,
//                                    JsonNode actual,
//                                    List<String> missing,
//                                    List<String> extra,
//                                    List<String> mismatched) {
//
//        // 1. Handle missing keys and mismatched values
//        if (expected.isObject()) {
//            Iterator<String> fieldNames = expected.fieldNames();
//            while (fieldNames.hasNext()) {
//                String field = fieldNames.next();
//                String currentPath = path.isEmpty() ? field : path + "." + field;
//
//                if (!actual.has(field)) {
//                    missing.add(currentPath);
//                } else {
//                    compareJson(currentPath, expected.get(field), actual.get(field), missing, extra, mismatched);
//                }
//            }
//
//            // 2. Find extra fields (present in actual but not expected)
//            Iterator<String> actualFields = actual.fieldNames();
//            while (actualFields.hasNext()) {
//                String field = actualFields.next();
//                if (!expected.has(field)) {
//                    String currentPath = path.isEmpty() ? field : path + "." + field;
//                    extra.add(currentPath);
//                }
//            }
//        }
//        else if (expected.isArray() && actual.isArray()) {
//            int min = Math.min(expected.size(), actual.size());
//            for (int i = 0; i < min; i++) {
//                compareJson(path + "[" + i + "]", expected.get(i), actual.get(i), missing, extra, mismatched);
//            }
//            if (expected.size() > actual.size())
//                missing.add(path + " has " + (expected.size() - actual.size()) + " missing elements");
//            if (actual.size() > expected.size())
//                extra.add(path + " has " + (actual.size() - expected.size()) + " extra elements");
//        }
//        else if (!expected.equals(actual)) {
//            mismatched.add(path + " → expected: " + expected + ", actual: " + actual);
//        }
//    }
//
//    private static void writeReport(List<String> missing, List<String> extra, List<String> mismatched) {
//        try (FileWriter fw = new FileWriter(REPORT_PATH)) {
//            fw.write("=== UI Text Validation Report ===\n\n");
//
//            fw.write("Missing Keys (" + missing.size() + "):\n");
//            for (String s : missing) fw.write(" - " + s + "\n");
//            fw.write("\n");
//
//            fw.write("New Keys (" + extra.size() + "):\n");
//            for (String s : extra) fw.write(" + " + s + "\n");
//            fw.write("\n");
//
//            fw.write("Value Mismatches (" + mismatched.size() + "):\n");
//            for (String s : mismatched) fw.write(" * " + s + "\n");
//
//            fw.write("\n===============================\n");
//            System.out.println("\n📄 Report generated at: " + REPORT_PATH);
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to write report file", e);
//        }
//    }
//}


package org.example.api;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import java.awt.*;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.*;

public class ApiFieldValidator {

    private static final String REPORT_PATH = "target/jsonDiffReport.txt";
    private static final String EXCEL_PATH = "src/main/resources/UITestConfig.xlsx"; // Excel with env list

    // Environment definition class
    static class Environment {
        String name;
        String type; // "API" or "BASELINE"
        String pathOrUrl; // File path or API URL

        Environment(String name, String type, String pathOrUrl) {
            this.name = name;
            this.type = type;
            this.pathOrUrl = pathOrUrl;
        }

        @Override
        public String toString() {
            return name + " [" + type + "]";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ApiFieldValidator::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("UI Text Validator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        JPanel panel = new JPanel(new FlowLayout());
        JLabel expLabel = new JLabel("Expected Result:");
        JComboBox<Environment> expDropdown = new JComboBox<>();
        JLabel actualLabel = new JLabel("Actual Result:");
        JComboBox<Environment> actualDropdown = new JComboBox<>();
        JButton compareButton = new JButton("Compare");

        panel.add(expLabel);
        panel.add(expDropdown);
        panel.add(actualLabel);
        panel.add(actualDropdown);
        panel.add(compareButton);
        frame.add(panel, BorderLayout.NORTH);

        JTextArea reportArea = new JTextArea();
        reportArea.setEditable(false);
        reportArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(reportArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Load environments from Excel
        java.util.List<Environment> environments = loadEnvsFromExcel(EXCEL_PATH);

        // Handle case when Excel is empty or fails to load
        if (environments.isEmpty()) {
            reportArea.setText("⚠️ Warning: No environments loaded from Excel.\n");
            reportArea.append("Expected Excel file at: " + EXCEL_PATH + "\n");
            reportArea.append("Excel should have columns:\n");
            reportArea.append("  Column A = ENV (environment name)\n");
            reportArea.append("  Column B = TYPE (API or BASELINE)\n");
            reportArea.append("  Column C = PATH_OR_URL (file path or API base URL)\n");
        } else {
            for (Environment env : environments) {
                expDropdown.addItem(env);
                actualDropdown.addItem(env);
            }
            reportArea.setText("✅ Loaded " + environments.size() + " environment(s) from Excel.\n");
            reportArea.append("Select environments and click 'Compare' to start.\n\n");
            reportArea.append("Loaded environments:\n");
            for (Environment env : environments) {
                reportArea.append("  • " + env.name + " [" + env.type + "]\n");
            }
        }

        // Compare button action
        compareButton.addActionListener((ActionEvent e) -> {
            Environment expEnv = (Environment) expDropdown.getSelectedItem();
            Environment actualEnv = (Environment) actualDropdown.getSelectedItem();

            // Validate selections
            if (expEnv == null || actualEnv == null) {
                reportArea.setText("❌ Error: Please select both environments.\n");
                return;
            }

            reportArea.setText("🚀 Starting comparison...\n");
            reportArea.append("Expected Environment: " + expEnv.name + " [" + expEnv.type + "]\n");
            reportArea.append("  Location: " + expEnv.pathOrUrl + "\n");
            reportArea.append("Actual Environment: " + actualEnv.name + " [" + actualEnv.type + "]\n");
            reportArea.append("  Location: " + actualEnv.pathOrUrl + "\n\n");

            // Disable button during comparison
            compareButton.setEnabled(false);

            // Run comparison in background thread to avoid freezing UI
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    StringBuilder result = new StringBuilder();

                    try {
                        ObjectMapper mapper = new ObjectMapper();

                        // Fetch exp JSON
                        result.append("📥 Fetching data from ").append(expEnv.name).append("...\n");
                        JsonNode expJson = fetchJson(expEnv, mapper);
                        result.append("✅ Expected data loaded successfully\n");

                        // Fetch actual JSON
                        result.append("📥 Fetching data from ").append(actualEnv.name).append("...\n");
                        JsonNode actualJson = fetchJson(actualEnv, mapper);
                        result.append("✅ Actual data loaded successfully\n\n");

                        result.append("🔍 Comparing JSON structures...\n");
                        java.util.List<String> missing = new ArrayList<>();
                        java.util.List<String> extra = new ArrayList<>();
                        java.util.List<String> mismatched = new ArrayList<>();

                        compareJson("", expJson, actualJson, missing, extra, mismatched);

                        writeReport(missing, extra, mismatched);
                        result.append("📄 Full report written to: ").append(REPORT_PATH).append("\n\n");

                        if (missing.isEmpty() && extra.isEmpty() && mismatched.isEmpty()) {
                            result.append("✅ SUCCESS: All UI texts match between environments!\n");
                        } else {
                            result.append("❌ DIFFERENCES FOUND:\n");
                            result.append("  • Missing Keys: ").append(missing.size()).append("\n");
                            result.append("  • Extra Keys: ").append(extra.size()).append("\n");
                            result.append("  • Value Mismatches: ").append(mismatched.size()).append("\n\n");

                            // Show first few examples
                            if (!missing.isEmpty()) {
                                result.append("Missing Keys (first 5):\n");
                                for (int i = 0; i < Math.min(5, missing.size()); i++) {
                                    result.append("  - ").append(missing.get(i)).append("\n");
                                }
                                if (missing.size() > 5) {
                                    result.append("  ... and ").append(missing.size() - 5).append(" more\n");
                                }
                                result.append("\n");
                            }

                            if (!extra.isEmpty()) {
                                result.append("Extra Keys (first 5):\n");
                                for (int i = 0; i < Math.min(5, extra.size()); i++) {
                                    result.append("  + ").append(extra.get(i)).append("\n");
                                }
                                if (extra.size() > 5) {
                                    result.append("  ... and ").append(extra.size() - 5).append(" more\n");
                                }
                                result.append("\n");
                            }

                            if (!mismatched.isEmpty()) {
                                result.append("Value Mismatches (first 5):\n");
                                for (int i = 0; i < Math.min(5, mismatched.size()); i++) {
                                    result.append("  * ").append(mismatched.get(i)).append("\n");
                                }
                                if (mismatched.size() > 5) {
                                    result.append("  ... and ").append(mismatched.size() - 5).append(" more\n");
                                }
                            }
                        }

                    } catch (Exception ex) {
                        result.append("\n❌ ERROR: ").append(ex.getMessage()).append("\n");
                        result.append("\nStack trace:\n");
                        StringWriter sw = new StringWriter();
                        ex.printStackTrace(new PrintWriter(sw));
                        result.append(sw.toString());
                    }

                    return result.toString();
                }

                @Override
                protected void done() {
                    try {
                        reportArea.setText(get());
                    } catch (Exception ex) {
                        reportArea.setText("❌ Unexpected error: " + ex.getMessage());
                    } finally {
                        compareButton.setEnabled(true);
                    }
                }
            }.execute();
        });

        frame.setLocationRelativeTo(null); // Center on screen
        frame.setVisible(true);
    }

    // Fetch JSON from either file or API based on environment type
    private static JsonNode fetchJson(Environment env, ObjectMapper mapper) throws Exception {
        if ("BASELINE".equalsIgnoreCase(env.type)) {
            // Read from file
            File file = new File(env.pathOrUrl);
            if (!file.exists()) {
                throw new FileNotFoundException("Baseline file not found: " + env.pathOrUrl);
            }
            return mapper.readTree(file);
        } else if ("API".equalsIgnoreCase(env.type)) {
            // Fetch from API
            // Extract base URL and endpoint
            String fullUrl = env.pathOrUrl;

            // Parse the URL to separate base URL and endpoint
            String baseUrl;
            String endpoint;

            if (fullUrl.contains("/ngui/")) {
                int endpointStart = fullUrl.indexOf("/ngui/");
                baseUrl = fullUrl.substring(0, endpointStart);
                endpoint = fullUrl.substring(endpointStart);
            } else {
                // Fallback: assume the whole thing is base URL
                baseUrl = fullUrl;
                endpoint = "/ngui/asset/i18n/en.json"; // default
            }

            RestAssured.useRelaxedHTTPSValidation();
            RestAssured.baseURI = baseUrl;

            Response response = RestAssured.given()
                    .relaxedHTTPSValidation()
                    .header("Accept", "application/json")
                    .when()
                    .get(endpoint)
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();

            return mapper.readTree(response.asString());
        } else {
            throw new IllegalArgumentException("Unknown environment type: " + env.type + ". Must be 'API' or 'BASELINE'");
        }
    }

    // Load environment definitions from Excel file
    private static java.util.List<Environment> loadEnvsFromExcel(String path) {
        java.util.List<Environment> environments = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(path);
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean isFirstRow = true;

            for (Row row : sheet) {
                // Skip header row
                if (isFirstRow) {
                    isFirstRow = false;
                    Cell firstCell = row.getCell(0);
                    if (firstCell != null) {
                        String value = getCellValueAsString(firstCell).toLowerCase();
                        if (value.contains("env") || value.contains("name") || value.contains("environment")) {
                            continue; // Skip header
                        }
                    }
                }

                Cell nameCell = row.getCell(0);
                Cell typeCell = row.getCell(1);
                Cell pathOrUrlCell = row.getCell(2);

                if (nameCell != null && typeCell != null && pathOrUrlCell != null) {
                    String name = getCellValueAsString(nameCell);
                    String type = getCellValueAsString(typeCell);
                    String pathOrUrl = getCellValueAsString(pathOrUrlCell);

                    if (!name.isEmpty() && !type.isEmpty() && !pathOrUrl.isEmpty()) {
                        environments.add(new Environment(name, type, pathOrUrl));
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("⚠️ Excel file not found at: " + path);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to load Excel: " + e.getMessage());
            e.printStackTrace();
        }
        return environments;
    }

    // Helper to safely get cell value as string
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue()).trim();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    // Existing compareJson logic - UNCHANGED
    private static void compareJson(String path, JsonNode expected, JsonNode actual,
                                    java.util.List<String> missing, java.util.List<String> extra, java.util.List<String> mismatched) {

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
            Iterator<String> actualFields = actual.fieldNames();
            while (actualFields.hasNext()) {
                String field = actualFields.next();
                if (!expected.has(field)) {
                    String currentPath = path.isEmpty() ? field : path + "." + field;
                    extra.add(currentPath);
                }
            }
        } else if (expected.isArray() && actual.isArray()) {
            int min = Math.min(expected.size(), actual.size());
            for (int i = 0; i < min; i++) {
                compareJson(path + "[" + i + "]", expected.get(i), actual.get(i), missing, extra, mismatched);
            }
            if (expected.size() > actual.size())
                missing.add(path + " has " + (expected.size() - actual.size()) + " missing elements");
            if (actual.size() > expected.size())
                extra.add(path + " has " + (actual.size() - expected.size()) + " extra elements");
        } else if (!expected.equals(actual)) {
            mismatched.add(path + " → expected: " + expected + ", actual: " + actual);
        }
    }

    // Existing writeReport logic - UNCHANGED
    private static void writeReport(java.util.List<String> missing, java.util.List<String> extra, java.util.List<String> mismatched) {
        try (FileWriter fw = new FileWriter(REPORT_PATH)) {
            fw.write("=== UI Text Validation Report ===\n\n");
            fw.write("Missing Keys (" + missing.size() + "):\n");
            for (String s : missing) fw.write(" - " + s + "\n");
            fw.write("\nNew Keys (" + extra.size() + "):\n");
            for (String s : extra) fw.write(" + " + s + "\n");
            fw.write("\nValue Mismatches (" + mismatched.size() + "):\n");
            for (String s : mismatched) fw.write(" * " + s + "\n");
            fw.write("\n===============================\n");
            System.out.println("📄 Report generated at: " + REPORT_PATH);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write report file", e);
        }
    }
}