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
            "centangularsndui-service"
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

        JLabel env1Label = new JLabel("Lower Environment:");
        JComboBox<Environment> env1Dropdown = new JComboBox<>();
        env1Dropdown.setPreferredSize(new Dimension(150, 25));

        JLabel env2Label = new JLabel("Higher Environment:");
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
            reportArea.append("  R1QA=https://dcodecnr1dev1.unilever.com/ngui/\n");
            reportArea.append("  ProdR1=https://dcode.unilever.com/ngui/\n");
            reportArea.append("  AstronDEV=https://danonengdev.centegyapps.com/ngui\n");
            reportArea.append("  AstronSIT=https://danonengsit.centegyapps.com/ngui\n\n");
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
        result.append("Lower Environment:").append(env1).append("\n");
        result.append("Higher Environment:").append(env2).append("\n");
        return result.toString();
    }

    // Fetch version from a single service
    private static String fetchVersion(String baseUrl, String service) {
        try {
            RestAssured.useRelaxedHTTPSValidation();
            RestAssured.baseURI = baseUrl;

            Response response = RestAssured.given()
                    .relaxedHTTPSValidation()
                    .header("Accept", "application/json")
                    .config(RestAssured.config()
                            .httpClient(io.restassured.config.HttpClientConfig.httpClientConfig()
                                    .setParam("http.connection.timeout", TIMEOUT_SECONDS * 1000)
                                    .setParam("http.socket.timeout", TIMEOUT_SECONDS * 1000)))
                    .when()
                    .get("/" + service + "/api/v1/releaseversion/getReleaseVersion");

            int statusCode = response.getStatusCode();
            if (statusCode == 200) {
                String body = response.getBody().asString().trim();
                // Remove quotes if present
                body = body.replaceAll("^\"|\"$", "");
                return body.isEmpty() ? " EMPTY RESPONSE " : body;
            } else {
                String body = response.getBody().asString().trim();
                // Remove quotes if present
                body = body.replaceAll("^\"|\"$", "");
                return body.isEmpty()
                        ? "HTTP: " + statusCode + " EMPTY RESPONSE "
                        : " (HTTP: " + statusCode + ")" + body;
            }
        } catch (Exception e) {
            // Handle timeout, connection errors, etc.
            if (e.getMessage() != null && (e.getMessage().contains("timeout") ||
                    e.getMessage().contains("timed out"))) {
                return "TIMEOUT";
            }
            return "ERROR";
        }
    }

    // Compare two version strings numerically
    private static ComparisonStatus compareVersionStrings(String v1, String v2) {
        // If either is an error, return ERROR
        if (v1.startsWith("ERROR") || v1.startsWith("HTTP_") || v1.startsWith("TIMEOUT") ||
                v2.startsWith("ERROR") || v2.startsWith("HTTP_") || v2.startsWith("TIMEOUT") ||
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
    private static int compareVersions(String version1, String version2) {
        String[] v1Parts = version1.split("\\.");
        String[] v2Parts = version2.split("\\.");

        int maxLength = Math.max(v1Parts.length, v2Parts.length);

        for (int i = 0; i < maxLength; i++) {
            int v1Part = i < v1Parts.length ? Integer.parseInt(v1Parts[i]) : 0;
            int v2Part = i < v2Parts.length ? Integer.parseInt(v2Parts[i]) : 0;

            if (v1Part < v2Part) {
                return -1; // version1 < version2
            } else if (v1Part > v2Part) {
                return 1; // version1 > version2
            }
        }

        return 0; // versions are equal
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