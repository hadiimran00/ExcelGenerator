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
        static String successBase64;
        static String failureBase64;
        static String countryBase64;
        static String arrowBase64;


        static {
            try {
                properties.load(new FileInputStream("application.properties"));
                successBase64 = properties.getProperty("successbase64", "").replaceAll("\\s+", "");
                failureBase64 = properties.getProperty("failurebase64", "").replaceAll("\\s+", "");
                countryBase64 = properties.getProperty("countrybase64", "").replaceAll("\\s+", "");
                arrowBase64 = properties.getProperty(" arrowbase64", "").replaceAll("\\s+", "");
            } catch (IOException e) {
                e.printStackTrace();
                successBase64 = "";
                failureBase64 = "";
            }
        }


        private static final List<String> screenResults = new ArrayList<>();

        public static void recordDownloadSuccess(String screenName) {
            downloadSuccess++;
            screenResults.add("success|" + screenName + " – Download Success");
        }

        public static void recordDownloadFailure(String screenName, String errorMsg) {
            downloadFailure++;
            screenResults.add("failure|" + screenName + " – Download Failed: " + errorMsg);
        }

        public static void recordUploadSuccess(String screenName) {
            uploadSuccess++;
            screenResults.add("success|" + screenName + " – Upload Success");
        }

        public static void recordUploadFailure(String screenName, String errorMsg) {
            uploadFailure++;
            screenResults.add("failure|" + screenName + " – Upload Failed: " + errorMsg);
        }

        private static boolean headerWritten = false;

        public static void clearSummaryFile() {
            try (OutputStreamWriter writer =
                         new OutputStreamWriter(new FileOutputStream("summary.html", false), StandardCharsets.UTF_8)) {

                writer.write("""
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; background:#f8f9fa; color:#333; padding:20px; }
                        .card { background:#fff; border:1px solid #ddd; border-radius:8px; padding:15px 20px; margin-bottom:20px; }
                        h2 { color:#2e7d32; border-left:5px solid #4CAF50; padding-left:10px; }
                        table { border-collapse:collapse; width:100%; font-size:13px; margin-top:10px; }
                        th, td { border:1px solid #ddd; padding:6px 10px; text-align:left; }
                        th { background:#4CAF50; color:#fff; }
                        .success { color:#28a745; font-weight:bold; }
                        .failure { color:#dc3545; font-weight:bold; }
                        .summary { margin-top:10px; padding:6px; background:#eef6ee; border-radius:5px; display:inline-block; }
                    </style>
                    </head><body>
                    <h1 style='color:#2e7d32;'>Automation Summary Report</h1>
                    """);
            } catch (IOException e) {
                e.printStackTrace();
            }
            headerWritten = true;
        }

        public static void writeTestSummary(String country) {
            try (OutputStreamWriter writer =
                         new OutputStreamWriter(new FileOutputStream("summary.html", true), StandardCharsets.UTF_8)) {

                writer.write("<div class='card'>\n");
                writer.write("<h2><img src=\"data:image/png;base64," + countryBase64 + "\" alt=\"Country Icon\" style=\"width:24px; height:24px; vertical-align:middle;\"> " + country + "</h2>\n");


                writer.write("<table><tr><th>Status</th><th>Screen</th></tr>\n");

                for (String result : screenResults) {
                    // Expected format: "success|Screen Name → Upload Success"
                    String[] parts = result.split("\\|", 2);
                    if (parts.length != 2) continue; // skip bad lines

                    String cssClass = parts[0].trim();  // "success" or "failure"
                    String text = parts[1].trim();      // the actual message
                    String icon = cssClass.equals("success")
                            ? "<img src=\"data:image/png;base64," + successBase64 + "\" alt=\"Success\" style=\"width:16px;height:16px;\" />"
                            : "<img src=\"data:image/png;base64," + failureBase64 + "\" alt=\"Failed\" style=\"width:16px;height:16px;\" />";



                    writer.write("<tr>");
                    writer.write("<td class='" + cssClass + "'>" + icon + "</td>");
                    writer.write("<td>" + text + "</td>");
                    writer.write("</tr>\n");
                }

                writer.write("</table>\n");

                writer.write("<div class='summary'>"
                        + "Downloads: <span class='success'>" + downloadSuccess + "</span> "
                        + "<img src='data:image/png;base64," + successBase64 + "' alt='Success' style='width:16px; height:16px; vertical-align:middle;'> / "
                        + "<span class='failure'>" + downloadFailure + "</span> "
                        + "<img src='data:image/png;base64," + failureBase64 + "' alt='Failure' style='width:16px; height:16px; vertical-align:middle;'> "
                        + "<img src='data:image/png;base64," + arrowBase64 + "' alt='Arrow' style='width:16px; height:16px; vertical-align:middle; margin: 0 8px;'> "
                        + "Uploads: <span class='success'>" + uploadSuccess + "</span> "
                        + "<img src='data:image/png;base64," + successBase64 + "' alt='Success' style='width:16px; height:16px; vertical-align:middle;'> / "
                        + "<span class='failure'>" + uploadFailure + "</span> "
                        + "<img src='data:image/png;base64," + failureBase64 + "' alt='Failure' style='width:16px; height:16px; vertical-align:middle;'>"
                        + "</div>\n");



            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        public static void closeSummaryHtml() {
            try (OutputStreamWriter writer =
                         new OutputStreamWriter(new FileOutputStream("summary.html", true), StandardCharsets.UTF_8)) {
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
