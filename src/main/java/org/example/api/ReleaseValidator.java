package org.example.api;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class ReleaseValidator {

    private static final String PROPERTIES_FILE = "environments.properties";
    private static final int TIMEOUT_SECONDS = 10;
    private static final int THREAD_POOL_SIZE = 10;

    // Fixed list of microservices
    private static final String[] SERVICES = {
            "batchprice-service", "autoroute-service", "changemanagement-service",
            "claimautomation-service", "fileuploader-service", "global-service",
            "integrator-service", "menu-service", "merchandising-service",
            "orderfulfillment-service", "order-service", "payment-service",
            "powerbi-service", "promotion-service", "stock-service",
            "studio-service", "target-service", "validation-service",
            "workflow-service", "financials-service", "transactionalreport-service",
            "sso-service", "kafkaauditlog-service", "scriptexecutor-service",
            "angular-service"
    };

    // Environment definition class
    static class Environment {
        String name;
        String baseUrl;

        Environment(String name, String baseUrl) {
            this.name = name;
            // Normalize base URL - remove trailing slash
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // Version comparison result
    static class VersionResult {
        String service;
        String env1Version;
        String env2Version;
        ComparisonStatus status;

        VersionResult(String service, String env1Version, String env2Version, ComparisonStatus status) {
            this.service = service;
            this.env1Version = env1Version;
            this.env2Version = env2Version;
            this.status = status;
        }
    }

    enum ComparisonStatus {
        EQUAL("✅", "EQUAL"),
        AHEAD("⬆️", "AHEAD"),
        BEHIND("⬇️", "BEHIND"),
        ERROR("⚠️", "ERROR");

        final String icon;
        final String label;

        ComparisonStatus(String icon, String label) {
            this.icon = icon;
            this.label = label;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ReleaseValidator::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Release Version Comparator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 700);
        frame.setLayout(new BorderLayout(10, 10));

        // Top panel with padding
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JLabel env1Label = new JLabel("Source Environment:");
        JComboBox<Environment> env1Dropdown = new JComboBox<>();
        env1Dropdown.setPreferredSize(new Dimension(150, 25));

        JLabel env2Label = new JLabel("Target Environment:");
        JComboBox<Environment> env2Dropdown = new JComboBox<>();
        env2Dropdown.setPreferredSize(new Dimension(150, 25));

        JButton compareButton = new JButton("Compare Versions");
        compareButton.setPreferredSize(new Dimension(150, 25));

        topPanel.add(env1Label);
        topPanel.add(env1Dropdown);
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(env2Label);
        topPanel.add(env2Dropdown);
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(compareButton);

        frame.add(topPanel, BorderLayout.NORTH);

        // Results area
        JTextArea reportArea = new JTextArea();
        reportArea.setEditable(false);
        reportArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        reportArea.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane scrollPane = new JScrollPane(reportArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        frame.add(scrollPane, BorderLayout.CENTER);

        // Load environments from properties file
        List<Environment> environments = loadEnvironmentsFromProperties();

        if (environments.isEmpty()) {
            reportArea.setText("⚠️ Warning: No environments loaded from properties file.\n\n");
            reportArea.append("Expected file: " + PROPERTIES_FILE + "\n");
            reportArea.append("Location: Same folder as this application\n\n");
            reportArea.append("File format (key=value pairs):\n");
            reportArea.append("  R1QA=https://dcodecnr1dev1.unilever.com/\n");
            reportArea.append("  ProdR1=https://dcode.unilever.com/\n");
            reportArea.append("  AstronDEV=https://danonengdev.centegyapps.com/\n");
            reportArea.append("  AstronSIT=https://danonengsit.centegyapps.com/\n\n");
            reportArea.append("Please create the file and restart the application.");
        } else {
            for (Environment env : environments) {
                env1Dropdown.addItem(env);
                env2Dropdown.addItem(env);
            }

            // Set different defaults if possible
            if (environments.size() > 1) {
                env2Dropdown.setSelectedIndex(1);
            }

            reportArea.setText("✅ Loaded " + environments.size() + " environment(s) from " + PROPERTIES_FILE + "\n\n");
            reportArea.append("Loaded environments:\n");
            for (Environment env : environments) {
                reportArea.append("  • " + env.name + " → " + env.baseUrl + "\n");
            }
            reportArea.append("\n");
            reportArea.append("Total services to check: " + SERVICES.length + "\n");
            reportArea.append("\nSelect two environments and click 'Compare Versions' to start.");
        }

        // Compare button action
        compareButton.addActionListener((ActionEvent e) -> {
            Environment env1 = (Environment) env1Dropdown.getSelectedItem();
            Environment env2 = (Environment) env2Dropdown.getSelectedItem();

            if (env1 == null || env2 == null) {
                reportArea.setText("❌ Error: Please select both environments.\n");
                return;
            }

            if (env1.name.equals(env2.name)) {
                reportArea.setText("❌ Error: Please select different environments.\n");
                return;
            }

            reportArea.setText("🚀 Starting version comparison...\n");
            reportArea.append("Environment 1: " + env1.name + "\n");
            reportArea.append("Environment 2: " + env2.name + "\n");
            reportArea.append("Total services: " + SERVICES.length + "\n\n");
            reportArea.append("Fetching versions in parallel...\n");

            compareButton.setEnabled(false);

            // Run comparison in background thread
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    return compareVersions(env1, env2);
                }

                @Override
                protected void done() {
                    try {
                        reportArea.setText(get());
                    } catch (Exception ex) {
                        reportArea.setText("❌ Unexpected error: " + ex.getMessage());
                        ex.printStackTrace();
                    } finally {
                        compareButton.setEnabled(true);
                    }
                }
            }.execute();
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // Main comparison logic
    private static String compareVersions(Environment env1, Environment env2) {
        StringBuilder result = new StringBuilder();
        result.append("🔍 VERSION COMPARISON\n");
        result.append(env1.name).append(" vs ").append(env2.name).append("\n");
        result.append("=".repeat(60)).append("\n\n");

        // Thread-safe collections
        ConcurrentHashMap<String, String> env1Versions = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, String> env2Versions = new ConcurrentHashMap<>();

        // Fetch versions in parallel
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<?>> futures = new ArrayList<>();

        // Fetch env1 versions
        for (String service : SERVICES) {
            futures.add(executor.submit(() -> {
                String version = fetchVersion(env1.baseUrl, service);
                env1Versions.put(service, version);
            }));
        }

        // Fetch env2 versions
        for (String service : SERVICES) {
            futures.add(executor.submit(() -> {
                String version = fetchVersion(env2.baseUrl, service);
                env2Versions.put(service, version);
            }));
        }

        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            try {
                future.get(TIMEOUT_SECONDS + 5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Individual task failures are already handled in fetchVersion
            }
        }

        executor.shutdown();

        // Compare results
        List<VersionResult> results = new ArrayList<>();
        for (String service : SERVICES) {
            String v1 = env1Versions.getOrDefault(service, "ERROR");
            String v2 = env2Versions.getOrDefault(service, "ERROR");
            ComparisonStatus status = compareVersionStrings(v1, v2);
            results.add(new VersionResult(service, v1, v2, status));
        }

        // Sort: errors first, then mismatches, then by service name
        results.sort((a, b) -> {
            if (a.status == ComparisonStatus.ERROR && b.status != ComparisonStatus.ERROR) return 1;
            if (a.status != ComparisonStatus.ERROR && b.status == ComparisonStatus.ERROR) return -1;
            if (a.status != ComparisonStatus.EQUAL && b.status == ComparisonStatus.EQUAL) return 1;
            if (a.status == ComparisonStatus.EQUAL && b.status != ComparisonStatus.EQUAL) return -1;
            return a.service.compareTo(b.service);
        });

        // Count statistics
        int equal = 0, ahead = 0, behind = 0, errors = 0;

        // Display results
        for (VersionResult r : results) {
            result.append(r.status.icon).append(" ").append(r.service);

            switch (r.status) {
                case EQUAL:
                    result.append(" → ").append(r.env1Version).append("\n");
                    equal++;
                    break;
                case AHEAD:
                    result.append(" → ").append(env2.name).append(" is AHEAD\n");
                    result.append("   ").append(env1.name).append(": ").append(r.env1Version).append("\n");
                    result.append("   ").append(env2.name).append(": ").append(r.env2Version).append("\n");
                    ahead++;
                    break;
                case BEHIND:
                    result.append(" → ").append(env2.name).append(" is BEHIND\n");
                    result.append("   ").append(env1.name).append(": ").append(r.env1Version).append("\n");
                    result.append("   ").append(env2.name).append(": ").append(r.env2Version).append("\n");
                    behind++;
                    break;
                case ERROR:
                    result.append("\n");
                    result.append("   ").append(env1.name).append(": ").append(r.env1Version).append("\n");
                    result.append("   ").append(env2.name).append(": ").append(r.env2Version).append("\n");
                    errors++;
                    break;
            }
            result.append("\n");
        }

        // Summary
        result.append("=".repeat(60)).append("\n");
        result.append("SUMMARY\n");
        result.append("=".repeat(60)).append("\n");
        result.append("Total Services: ").append(SERVICES.length).append("\n");
        result.append("✅ Equal: ").append(equal).append("\n");
        result.append("⬆️ Ahead: ").append(ahead).append("\n");
        result.append("⬇️ Behind: ").append(behind).append("\n");
        result.append("⚠️ Errors: ").append(errors).append("\n");
        result.append("Total Mismatches: ").append(ahead + behind + errors).append("\n");
        result.append("Source Environment: ").append(env1.baseUrl).append("\n");
        result.append("Target Environment: ").append(env2.baseUrl).append("\n");
        return result.toString();
    }

    // Fetch version from a single service
    private static String fetchVersion(String baseUrl, String service) {
        try {
            RestAssured.useRelaxedHTTPSValidation();
            RestAssured.baseURI = baseUrl;

            Response response;

            // ✅ Special handling for Angular service
            if ("angular-service".equals(service)) {
                response = RestAssured.given()
                        .relaxedHTTPSValidation()
                        .config(RestAssured.config()
                                .httpClient(io.restassured.config.HttpClientConfig.httpClientConfig()
                                        .setParam("http.connection.timeout", TIMEOUT_SECONDS * 1000)
                                        .setParam("http.socket.timeout", TIMEOUT_SECONDS * 1000)))
                        .when()
                        .get("ngui/main/resources/version.txt");   // for angular service
            } else {
                // ✅ Existing API call (unchanged)
                response = RestAssured.given()
                        .relaxedHTTPSValidation()
                        .header("Accept", "application/json")
                        .config(RestAssured.config()
                                .httpClient(io.restassured.config.HttpClientConfig.httpClientConfig()
                                        .setParam("http.connection.timeout", TIMEOUT_SECONDS * 1000)
                                        .setParam("http.socket.timeout", TIMEOUT_SECONDS * 1000)))
                        .when()
                        .get("/" + service + "/api/v1/releaseversion/getReleaseVersion");
            }

            int statusCode = response.getStatusCode();

            if (statusCode == 200) {
                String body = response.getBody().asString().trim();
                body = body.replaceAll("^\"|\"$", "");
                return body.isEmpty() ? "EMPTY_RESPONSE" : body;
            } else {
                String body = response.getBody().asString().trim();
                body = body.replaceAll("^\"|\"$", "");
                return body.isEmpty()
                        ? "HTTP: " + statusCode + "EMPTY_RESPONSE"
                        : "(HTTP: " + statusCode + ") " + body;
            }

        } catch (Exception e) {
            if (e.getMessage() != null &&
                    (e.getMessage().contains("timeout") || e.getMessage().contains("timed out"))) {
                return "TIMEOUT";
            }
            return "ERROR";
        }

    }

    // Compare two version strings numerically
    private static ComparisonStatus compareVersionStrings(String v1, String v2) {
        // If either is an error, return ERROR
        if (v1.startsWith("ERROR") || v1.startsWith("HTTP") || v1.contains("(HTTP:") ||
                v1.startsWith("TIMEOUT") || v2.startsWith("ERROR") || v2.startsWith("HTTP") ||
                v2.contains("(HTTP:") || v2.startsWith("TIMEOUT") ||
                v1.equals("EMPTY_RESPONSE") || v2.equals("EMPTY_RESPONSE")) {
            return ComparisonStatus.ERROR;
        }

        try {
            int comparison = compareVersions(v1, v2);
            if (comparison == 0) {
                return ComparisonStatus.EQUAL;
            } else if (comparison < 0) {
                return ComparisonStatus.AHEAD; // v2 is higher
            } else {
                return ComparisonStatus.BEHIND; // v2 is lower
            }
        } catch (Exception e) {
            return ComparisonStatus.ERROR;
        }
    }

    // Compare versions numerically (e.g., 21.1.10.0 vs 21.1.9.0)
    // Also handles non-numeric parts (e.g., 1.1.101.hp_1 vs 1.1.101.hp_5)
    private static int compareVersions(String version1, String version2) {
        String[] v1Parts = version1.split("\\.");
        String[] v2Parts = version2.split("\\.");

        int maxLength = Math.max(v1Parts.length, v2Parts.length);

        for (int i = 0; i < maxLength; i++) {
            String v1Part = i < v1Parts.length ? v1Parts[i] : "0";
            String v2Part = i < v2Parts.length ? v2Parts[i] : "0";

            // Try to compare as numbers first
            try {
                int v1Num = Integer.parseInt(v1Part);
                int v2Num = Integer.parseInt(v2Part);

                if (v1Num < v2Num) {
                    return -1;
                } else if (v1Num > v2Num) {
                    return 1;
                }
            } catch (NumberFormatException e) {
                // One or both parts are not pure numbers
                // Handle mixed formats like "0" vs "hp_1" or "hp_1" vs "hp_5"
                int compareResult = compareVersionPart(v1Part, v2Part);
                if (compareResult != 0) {
                    return compareResult;
                }
            }
        }

        return 0; // versions are equal
    }

    // Compare individual version parts (handles both numeric and non-numeric)
    private static int compareVersionPart(String part1, String part2) {
        // Try parsing both as integers
        boolean p1IsNum = isInteger(part1);
        boolean p2IsNum = isInteger(part2);

        if (p1IsNum && p2IsNum) {
            // Both are numbers
            int v1 = Integer.parseInt(part1);
            int v2 = Integer.parseInt(part2);
            return Integer.compare(v1, v2);
        } else if (p1IsNum) {
            // Numeric comes before non-numeric (0 < hp_1)
            return -1;
        } else if (p2IsNum) {
            // Non-numeric comes after numeric (hp_1 > 0)
            return 1;
        } else {
            // Both are non-numeric, compare lexicographically
            // Extract numeric suffix if present (hp_1 vs hp_5)
            return compareAlphanumeric(part1, part2);
        }
    }

    // Check if string is a valid integer
    private static boolean isInteger(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Compare alphanumeric strings (e.g., hp_1 vs hp_5)
    private static int compareAlphanumeric(String s1, String s2) {
        // Try to extract prefix and numeric suffix
        String[] parts1 = splitAlphanumeric(s1);
        String[] parts2 = splitAlphanumeric(s2);

        // Compare prefix first
        int prefixCompare = parts1[0].compareTo(parts2[0]);
        if (prefixCompare != 0) {
            return prefixCompare;
        }

        // Same prefix, compare numeric suffix
        if (!parts1[1].isEmpty() && !parts2[1].isEmpty()) {
            try {
                int num1 = Integer.parseInt(parts1[1]);
                int num2 = Integer.parseInt(parts2[1]);
                return Integer.compare(num1, num2);
            } catch (NumberFormatException e) {
                return parts1[1].compareTo(parts2[1]);
            }
        }

        // Fallback to string comparison
        return s1.compareTo(s2);
    }


    // Split alphanumeric string into prefix and numeric suffix
    // e.g., "hp_5" -> ["hp_", "5"], "abc123" -> ["abc", "123"]
    private static String[] splitAlphanumeric(String s) {
        int i = s.length() - 1;
        while (i >= 0 && Character.isDigit(s.charAt(i))) {
            i--;
        }
        if (i < s.length() - 1) {
            return new String[]{s.substring(0, i + 1), s.substring(i + 1)};
        } else {
            return new String[]{s, ""};
        }
    }

    // Load environments from properties file
    private static List<Environment> loadEnvironmentsFromProperties() {
        List<Environment> environments = new ArrayList<>();
        Properties properties = new Properties();

        // Try multiple locations for the properties file
        File propertiesFile = findPropertiesFile();

        if (propertiesFile == null || !propertiesFile.exists()) {
            System.err.println("⚠️ Properties file not found: " + PROPERTIES_FILE);
            return environments;
        }

        try (FileInputStream fis = new FileInputStream(propertiesFile)) {
            properties.load(fis);
            System.out.println("✅ Loaded properties from: " + propertiesFile.getAbsolutePath());

            // Convert properties to Environment objects
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (value != null && !value.trim().isEmpty()) {
                    environments.add(new Environment(key, value.trim()));
                }
            }

            // Sort environments by name for better UX
            environments.sort(Comparator.comparing(e -> e.name));

        } catch (IOException e) {
            System.err.println("⚠️ Failed to load properties: " + e.getMessage());
            e.printStackTrace();
        }

        return environments;
    }

    // Find properties file in multiple locations
    private static File findPropertiesFile() {
        // 1. Try current directory (where .exe is located)
        File currentDir = new File(PROPERTIES_FILE);
        if (currentDir.exists()) {
            return currentDir;
        }

        // 2. Try the directory where the JAR is running
        try {
            String jarPath = ReleaseValidator.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            File jarDir = new File(jarPath).getParentFile();
            File jarDirFile = new File(jarDir, PROPERTIES_FILE);
            if (jarDirFile.exists()) {
                return jarDirFile;
            }
        } catch (Exception e) {
            // Ignore and try next location
        }

        // 3. Try user's home directory
        File homeDir = new File(System.getProperty("user.home"), PROPERTIES_FILE);
        if (homeDir.exists()) {
            return homeDir;
        }

        // 4. Return null if not found
        return null;
    }
}