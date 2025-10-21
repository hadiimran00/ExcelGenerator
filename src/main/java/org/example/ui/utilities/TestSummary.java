package org.example.ui.utilities;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class TestSummary {
    private static int downloadSuccess = 0;
    private static int downloadFailure = 0;
    private static int uploadSuccess = 0;
    private static int uploadFailure = 0;

    static Properties properties = new Properties();
    static String sharedPath;
    static String DateTime = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a"));

    static {
        try {
            properties.load(new FileInputStream("application.properties"));
            sharedPath = properties.getProperty("sharedPath", "");
            System.out.println("Shared Path: " + sharedPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final List<String> screenResults = new ArrayList<>();

    public static void recordDownloadSuccess(String screenName) {
        downloadSuccess++;
        screenResults.add("success|" + screenName + " -> Download Success");
    }

    public static void recordDownloadFailure(String screenName, String errorMsg) {
        downloadFailure++;
        screenResults.add("failure|" + screenName + " -> Download Failed: " + errorMsg);
    }

    public static void recordUploadSuccess(String screenName) {
        uploadSuccess++;
        screenResults.add("success|" + screenName + " -> Upload Success");
    }

    public static void recordUploadFailure(String screenName, String errorMsg) {
        uploadFailure++;
        screenResults.add("failure|" + screenName + " -> Upload Failed: " + errorMsg);
    }

    public static void clearSummaryFile() {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream("summary.html", false), StandardCharsets.UTF_8)) {

            writer.write("""
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #f4f6f8;
            color: #222222;
            margin: 0;
            padding: 20px;
        }
        h1 {
            font-size: 22px;
            color: #2c7a7b;
            text-align: center;
            margin-bottom: 30px;
        }
        h3, h4 {
            font-size: 14px;
            margin: 6px 0;
        }
        .card {
            background-color: #ffffff;
            border: 1px solid #dddddd;
            padding: 16px;
            margin-bottom: 20px;
        }
        h2 {
            font-size: 16px;
            color: #276749;
            border-left: 4px solid #38a169;
            padding-left: 10px;
            margin-bottom: 10px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 14px;
        }
        th {
            background-color: #38a169;
            color: #ffffff;
            padding: 8px;
            text-align: left;
        }
        td {
            padding: 8px;
            border-bottom: 1px solid #e2e8f0;
        }
        .success {
            color: #2f855a;
            font-weight: bold;
        }
        .failure {
            color: #c53030;
            font-weight: bold;
        }
        .summary {
            background-color: #e6fffa;
            border: 1px solid #b2f5ea;
            padding: 10px;
            font-size: 14px;
            margin-top: 20px;
            text-align: center;
        }
        code {
            background-color: #f1f1f1;
            padding: 2px 4px;
            border-radius: 4px;
            font-family: monospace;
        }
    </style>
</head>
<body>
    <h1>Automation Summary Report</h1>
""");

            writer.write("<h3>Test Execution Date & Time: <code>" + DateTime + "</code></h3>\n");
            writer.write("<h3>Screenshots & Downloaded Excels available on Shared Path: <code>" + sharedPath + "</code></h3>\n");

            writer.write("""
    <h4>Note: Folders are cleaned after every run.</h4>
    <h4>Test detail logs are attached.</h4>
    <h4>This is an automated email from Jenkins.</h4>
""");


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeTestSummary(String country) {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream("summary.html", true), StandardCharsets.UTF_8)) {

            writer.write("<div class='card'>\n");
            writer.write("<h2>Country: " + country + "</h2>\n");
            writer.write("<table>\n");
            writer.write("<tr><th>Status</th><th>Screen</th></tr>\n");

            for (String result : screenResults) {
                String[] parts = result.split("\\|", 2);
                if (parts.length != 2) continue;

                String cssClass = parts[0].trim(); // "success" or "failure"
                String text = parts[1].trim();
                String label = cssClass.equals("success") ? "SUCCESS" : "FAILURE";

                writer.write("<tr>");
                writer.write("<td class='" + cssClass + "'>" + label + "</td>");
                writer.write("<td>" + text + "</td>");
                writer.write("</tr>\n");
            }

            writer.write("</table>\n");

            writer.write("<div class='summary'>"
                    + "<span class='success'>Download Success: " + downloadSuccess + "</span>"
                    + "<span class='failure'>  Download Failure: " + downloadFailure + "</span>"
                    + "<span style='margin: 0 20px;'>|</span>"
                    + "<span class='success'>Upload Success: " + uploadSuccess + "</span>"
                    + "<span class='failure'>  Upload Failure: " + uploadFailure + "</span>"
                    + "</div>\n");



            writer.write("</div>\n");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void closeSummaryHtml() {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream("summary.html", true), StandardCharsets.UTF_8)) {
            writer.write("</body></html>");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void resetTestCounter() {
        downloadSuccess = 0;
        downloadFailure = 0;
        uploadSuccess = 0;
        uploadFailure = 0;
        screenResults.clear();
    }
}
