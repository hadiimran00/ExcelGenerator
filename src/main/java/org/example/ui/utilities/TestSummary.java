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
    private static int validationPass = 0;
    private static int validationFail = 0;

    static Properties properties = new Properties();
    static String ReportsFolderName;
    static String sharedPath;
    static String ReportMsg;
    static String DateTime = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_hh-mm-ss_a"));


    static {
        try {
            properties.load(new FileInputStream("application.properties"));
            sharedPath = properties.getProperty("sharedPath", "NA");
            ReportMsg = properties.getProperty("ReportMsg", "");
            ReportsFolderName = properties.getProperty("ReportsFolderName", "");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static String ExcelTestSummary = ReportsFolderName + "/ExcelTestSummary_" + DateTime + ".html";

    private static final List<String> screenResults = new ArrayList<>();

    // --- Recording Methods (Fixed Separators) ---

    public static void recordDownloadSuccess(String screenName, String base64Data) {
        downloadSuccess++;
        screenResults.add("success|<b>" + screenName + "</b> -> Download Success|" + base64Data);
    }

    public static void recordDownloadFailure(String screenName, String errorMsg, String base64Data) {
        downloadFailure++;
        screenResults.add("failure|<b>" + screenName + "</b> -> Download Failed: " + errorMsg + "|" + base64Data);
    }

    public static void recordUploadSuccess(String screenName, String base64Data) {
        uploadSuccess++;

        screenResults.add("success|<b>" + screenName + "</b> -> Upload Success|" + base64Data);
    }

    public static void recordUploadFailure(String screenName, String errorMsg, String base64Data) {
        uploadFailure++;
        screenResults.add("failure|<b>" + screenName + "</b> -> Upload Failed: " + errorMsg + "|" + base64Data);
    }

    public static void clearSummaryFile() {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(ExcelTestSummary, false), StandardCharsets.UTF_8)) {

            writer.write("""
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8f9fa; color: #333; padding: 30px; }
        h1 { color: #2d3748; text-align: center; text-transform: uppercase; letter-spacing: 1px; }
        .card { background: white; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); padding: 20px; margin-bottom: 30px; }
        h2 { color: #38a169; border-bottom: 2px solid #e2e8f0; padding-bottom: 10px; }
        table { width: 100%; border-collapse: collapse; margin-top: 15px; }
        th { background-color: #4a5568; color: white; padding: 12px; text-align: left; }
        td { padding: 12px; border-bottom: 1px solid #edf2f7; vertical-align: middle; }
        .success { color: #2f855a; font-weight: bold; }
        .failure { color: #e53e3e; font-weight: bold; }
        .thumbnail { width: 120px; height: auto; border: 1px solid #cbd5e0; border-radius: 4px; cursor: pointer; transition: 0.3s; }
        .thumbnail:hover { transform: scale(1.1); box-shadow: 0 4px 8px rgba(0,0,0,0.2); }
        .summary-bar { display: flex; justify-content: space-around; background: #edf2f7; padding: 15px; border-radius: 8px; margin-top: 20px; font-weight: bold; }
        code { background: #edf2f7; padding: 2px 5px; border-radius: 4px; color: #805ad5; }
        
        /* Modal Styles */
        #imgModal { display: none; position: fixed; z-index: 1000; padding-top: 50px; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.9); }
        .modal-content { margin: auto; display: block; max-width: 90%; max-height: 85vh; border-radius: 5px; }
        #closeModal { position: absolute; top: 15px; right: 35px; color: #f1f1f1; font-size: 40px; font-weight: bold; cursor: pointer; }
    </style>
    <script>
        function openImg(src) {
            var modal = document.getElementById("imgModal");
            var modalImg = document.getElementById("expandedImg");
            modal.style.display = "block";
            modalImg.src = src;
        }
        function closeImg() {
            document.getElementById("imgModal").style.display = "none";
        }
    </script>
</head>
<body>
    <h1>🚀 Automation Test Report</h1>
    <!-- Modal Structure -->
    <div id="imgModal" onclick="closeImg()">
        <span id="closeModal" onclick="closeImg()">&times;</span>
        <img class="modal-content" id="expandedImg">
    </div>
""");
            writer.write("<p><b>Execution Time:</b> <code>" + DateTime + "</code></p>");
            writer.write("<p><b>Shared Path:</b> <code>" + sharedPath + "</code></p>");
            if (!ReportMsg.isEmpty()) writer.write("<p><b>Notes:</b> " + ReportMsg + "</p>");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void writeTestSummary(String country) {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(ExcelTestSummary, true), StandardCharsets.UTF_8)) {

            writer.write("<div class='card'>\n");
            writer.write("<h2>📍 Country: " + country + "</h2>\n");
            writer.write("<table>\n");
            writer.write("<tr><th style='width: 10%'>Status</th><th style='width: 65%'>Screen & Action</th><th style='width: 25%'>Screenshot</th></tr>\n");

            for (String result : screenResults) {
                String[] parts = result.split("\\|", 3);
                if (parts.length < 2) continue;

                String cssClass = parts[0].trim();
                String message = parts[1].trim();
                String base64Data = (parts.length == 3) ? parts[2].trim() : "";
                String label = cssClass.equalsIgnoreCase("success") ? "PASS" : "FAIL";

                writer.write("<tr>");
                writer.write("<td class='" + cssClass + "'>" + label + "</td>");
                writer.write("<td>" + message + "</td>");

                if (!base64Data.isEmpty()) {
                    writer.write("<td>"
                            + "<img src='data:image/png;base64," + base64Data + "' "
                            + "class='thumbnail' "
                            + "onclick=\"openImg(this.src)\">"
                            + "</td>");
                } else {
                    writer.write("<td style='color: #a0aec0; font-style: italic;'>No Image</td>");
                }
                writer.write("</tr>\n");
            }

            writer.write("</table>\n");

            writer.write("<div class='summary-bar'>"
                    + "<div>Downloads: <span class='success'>✔ " + downloadSuccess + "</span> / <span class='failure'>✖ " + downloadFailure + "</span></div>"
                    + "<div>Uploads: <span class='success'>✔ " + uploadSuccess + "</span> / <span class='failure'>✖ " + uploadFailure + "</span></div>"
                    + "</div>\n");

            writer.write("</div>\n");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void closeSummaryHtml() {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(ExcelTestSummary, true), StandardCharsets.UTF_8)) {
            writer.write("</body></html>");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    public static void recordValidationResult(ValidationResult result) {
//        if (result.passed) {
//            validationPass++;
//            result.passes.forEach(p ->
//                    screenResults.add("val-pass|" + result.screenName + " → " + p));
//        } else {
//            validationFail++;
//            result.failures.forEach(f ->
//                    screenResults.add("val-fail|" + result.screenName + " → " + f));
//        }
//    }

    public static void appendValidation(ValidationResult result) {
        if (screenResults.isEmpty() || result == null) return;

        int lastIdx = screenResults.size() - 1;
        String[] parts = screenResults.get(lastIdx).split("\\|", 3);

        if (parts.length >= 2) {
            String status = parts[0];
            String message = parts[1];
            String base64 = parts.length == 3 ? "|" + parts[2] : "";

            // Append validation status text
            if (result.passed) {
                //     message += "<br><span style='color:#2f855a;'><b>✅ Validation Passed</b></span>";
                message += "<br><span style='color:#2f855a;'><b>✅</b> " + String.join(", ", result.passes) + "</span>";

            } else {
                message += "<br><span style='color:#e53e3e;'><b>❌</b> " + String.join(", ", result.failures) + "</span>";
                // Flip row to red failure block and adjust counter if upload was marked as success
                if ("success".equals(status)) {
                    status = "failure";
                    uploadSuccess--;
                    uploadFailure++;
                }
            }
            screenResults.set(lastIdx, status + "|" + message + base64);
        }
    }

    public static void resetTestCounter() {
        downloadSuccess = 0;
        downloadFailure = 0;
        uploadSuccess = 0;
        uploadFailure = 0;
        validationPass = 0;
        validationFail = 0;
        screenResults.clear();
    }
}