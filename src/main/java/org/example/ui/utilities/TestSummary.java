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
    // Each row's passed/failed tests, kept beside the row so a later validation adds to the same totals
    private static final List<ValidationResult> rowTests = new ArrayList<>();
    // Where a row's test list is rendered once all its tests are known
    private static final String TESTS_PLACEHOLDER = "{{tests}}";

    // --- Recording Methods (Fixed Separators) ---

    public static void recordDownloadSuccess(String screenName, String base64Data) {
        recordDownloadSuccess(screenName, base64Data, List.of());
    }

    // checks: what this step verified (download toast, file found in directory), listed on the row
    public static void recordDownloadSuccess(String screenName, String base64Data, List<String> checks) {
        downloadSuccess++;
        addRow("success", "<b>" + screenName + "</b> -> Download Success", checks, List.of(), base64Data);
    }

    public static void recordDownloadFailure(String screenName, String errorMsg, String base64Data) {
        recordDownloadFailure(screenName, errorMsg, base64Data, List.of(), List.of());
    }

    public static void recordDownloadFailure(String screenName, String errorMsg, String base64Data,
                                             List<String> passedChecks, List<String> failedChecks) {
        downloadFailure++;
        addRow("failure", "<b>" + screenName + "</b> -> Download Failed: " + errorMsg, passedChecks, failedChecks, base64Data);
    }

    public static void recordUploadSuccess(String screenName, String base64Data) {
        recordUploadSuccess(screenName, base64Data, List.of());
    }

    // checks: what this step verified (upload toast), listed on the row
    public static void recordUploadSuccess(String screenName, String base64Data, List<String> checks) {
        uploadSuccess++;
        addRow("success", "<b>" + screenName + "</b> -> Upload Success", checks, List.of(), base64Data);
    }

    public static void recordUploadFailure(String screenName, String errorMsg, String base64Data) {
        recordUploadFailure(screenName, errorMsg, base64Data, List.of());
    }

    public static void recordUploadFailure(String screenName, String errorMsg, String base64Data, List<String> failedChecks) {
        uploadFailure++;
        addRow("failure", "<b>" + screenName + "</b> -> Upload Failed: " + errorMsg, List.of(), failedChecks, base64Data);
    }

    // A new report row; its tests are rendered when the report is written
    private static void addRow(String status, String header, List<String> passed, List<String> failed, String base64Data) {
        ValidationResult tests = new ValidationResult("");
        tests.passes.addAll(passed);
        tests.failures.addAll(failed);
        rowTests.add(tests);
        screenResults.add(status + "|" + header + TESTS_PLACEHOLDER + "|" + base64Data);
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
        details.checks { margin-top: 6px; }
        details.checks summary { cursor: pointer; font-weight: bold; user-select: none; }
        details.checks summary:hover { text-decoration: underline; }

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

            for (int i = 0; i < screenResults.size(); i++) {
                String[] parts = screenResults.get(i).split("\\|", 3);
                if (parts.length < 2) continue;

                String cssClass = parts[0].trim();
                String message = parts[1].trim().replace(TESTS_PLACEHOLDER, formatCheckList(rowTests.get(i)));
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

            int testsPassed = rowTests.stream().mapToInt(t -> t.passes.size()).sum();
            int testsFailed = rowTests.stream().mapToInt(t -> t.failures.size()).sum();
            writer.write("<div class='summary-bar'>"
                    + "<div>Total Test Case(s): " + (testsPassed + testsFailed) + " (<span class='success'>✔ " + testsPassed
                    + " passed</span> / <span class='failure'>✖ " + testsFailed + " failed</span>)</div>"
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
        if (result == null) return;

        // Standalone entry when there is no earlier row, or the earlier row belongs to another screen:
        // a flow (e.g. Locus Upload) that fails before recording its own upload/download must not land
        // on the previous screen's row (it showed as a Selling Category Bulk Upload failure).
        if (screenResults.isEmpty() || !lastRowIsScreen(result.screenName)) {
            String cssClass = result.passed ? "success" : "failure";
            String label = "<b>" + result.screenName + "</b>";
            // A multi-checkpoint flow (e.g. CouponOrderEditFlow validating two promos at five
            // checkpoints) accumulates passes AND failures independently - show both, not just
            // whichever the overall pass/fail flag happens to pick.
            addRow(cssClass, label + " -> ", result.passes, result.failures, result.screenshotBase64);
            return;
        }

        // UNCHANGED: Existing logic for appending to previous upload/download entries
        int lastIdx = screenResults.size() - 1;
        String[] parts = screenResults.get(lastIdx).split("\\|", 3);

        if (parts.length >= 2) {
            String status = parts[0];
            String message = parts[1];
            String existingBase64 = parts.length == 3 ? parts[2] : "";
            String base64 = !result.screenshotBase64.isEmpty() ? result.screenshotBase64 : existingBase64;

            // Add to the row's own tests, so the row shows one total instead of a second list
            rowTests.get(lastIdx).passes.addAll(result.passes);
            rowTests.get(lastIdx).failures.addAll(result.failures);
            if (!result.failures.isEmpty()) {
                if ("success".equals(status)) {
                    status = "failure";
                    uploadSuccess--;
                    uploadFailure++;
                }
            }
            screenResults.set(lastIdx, status + "|" + message + "|" + base64);
        }
    }

    // Rows start with "<b>Screen name</b>": a validation belongs to the last row only when it is for that screen.
    // Prefix match, so "<screen>_redownload" rows (DOWNLOAD_COMPARE) still count as the same screen.
    private static boolean lastRowIsScreen(String screenName) {
        String[] parts = screenResults.get(screenResults.size() - 1).split("\\|", 3);
        if (parts.length < 2 || screenName == null || screenName.isBlank()) return false;
        String message = parts[1];
        int start = message.indexOf("<b>");
        int end = message.indexOf("</b>");
        if (start < 0 || end <= start) return false;
        String rowScreen = message.substring(start + 3, end).trim().toLowerCase();
        String name = screenName.trim().toLowerCase();
        return name.startsWith(rowScreen) || rowScreen.startsWith(name);
    }

    // Passed and failed tests as click-to-open lists: passes start collapsed so a long flow stays one
    // short row, failures start open so they are seen without clicking.
    private static String formatCheckList(ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        if (!result.failures.isEmpty()) {
            sb.append("<details class='checks' open><summary style='color:#e53e3e;'>❌ ")
                    .append(result.failures.size()).append(" Test Case(s) failed")
                    .append("</summary><span style='color:#e53e3e;'>").append(formatAsBulletList(result.failures))
                    .append("</span></details>");
        }
        if (!result.passes.isEmpty()) {
            sb.append("<details class='checks'><summary style='color:#2f855a;'>✅ ")
                    .append(result.passes.size()).append(" Test Case(s) passed")
                    .append(" (click to view)</summary><span style='color:#2f855a;'>").append(formatAsBulletList(result.passes))
                    .append("</span></details>");
        }
        return sb.toString();
    }

    // Appends per-row errors read from the app's generated error Excel onto the most recently
    // recorded screen entry, so the report shows *why* an upload failed, not just that it did.
    public static void appendErrorDetails(List<String> errorRows) {
        if (errorRows == null || errorRows.isEmpty() || screenResults.isEmpty()) return;

        int lastIdx = screenResults.size() - 1;
        String[] parts = screenResults.get(lastIdx).split("\\|", 3);
        if (parts.length < 2) return;

        String status = parts[0];
        String message = parts[1];
        String base64 = parts.length == 3 ? parts[2] : "";

        message += "<br><span style='color:#e53e3e;'><b>📄 Excel Row Errors:</b>"
                + formatAsBulletList(errorRows) + "</span>";

        screenResults.set(lastIdx, status + "|" + message + "|" + base64);
    }

    public static void resetTestCounter() {
        downloadSuccess = 0;
        downloadFailure = 0;
        uploadSuccess = 0;
        uploadFailure = 0;
        validationPass = 0;
        validationFail = 0;
        screenResults.clear();
        rowTests.clear();
    }
    private static String escapeSeparator(String text) {
        if (text == null) return "";
        return text.replace("|", "&#124;");
    }

    // Renders validation details as an HTML bullet list instead of one long comma-joined line.
    // Repeats are kept intentionally: the same validation can legitimately pass/fail at multiple steps.
    private static String formatAsBulletList(List<String> items) {
        if (items == null || items.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("<ul style='margin:4px 0; padding-left:18px;'>");
        for (String item : items) {
            sb.append("<li>").append(escapeSeparator(item)).append("</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

}