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
        static String sharedPath;
        static String DateTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));


        static {
            try {
                properties.load(new FileInputStream("application.properties"));
                successBase64 = properties.getProperty("successbase64", "").replaceAll("\\s+", "");
                failureBase64 = properties.getProperty("failurebase64", "").replaceAll("\\s+", "");
                countryBase64 = properties.getProperty("countrybase64", "").replaceAll("\\s+", "");
                arrowBase64 = properties.getProperty("arrowbase64", "").replaceAll("\\s+", "");
                sharedPath=properties.getProperty("sharedPath", "");

                System.out.println("Shared Path: " + sharedPath);
            } catch (IOException e) {
                e.printStackTrace();
                successBase64 = "";
                failureBase64 = "";
            }
        }


        private static final List<String> screenResults = new ArrayList<>();


        public static void recordDownloadSuccess(String screenName) {
            downloadSuccess++;
            screenResults.add("success|" + screenName + " [[arrow]] Download Success");
        }

        public static void recordDownloadFailure(String screenName, String errorMsg) {
            downloadFailure++;
            screenResults.add("failure|" + screenName + " [[arrow]] Download Failed: " + errorMsg);
        }

        public static void recordUploadSuccess(String screenName) {
            uploadSuccess++;
            screenResults.add("success|" + screenName + " [[arrow]] Upload Success");
        }

        public static void recordUploadFailure(String screenName, String errorMsg) {
            uploadFailure++;
            screenResults.add("failure|" + screenName + " [[arrow]] Upload Failed: " + errorMsg);
        }

        private static boolean headerWritten = false;

        public static void clearSummaryFile() {
            try (OutputStreamWriter writer =
                         new OutputStreamWriter(new FileOutputStream("summary.html", false), StandardCharsets.UTF_8)) {

                writer.write(
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                        <meta charset="UTF-8">
                        <style>
                            body {
                                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                                background: #f4f6f8;
                                color: #222;
                                padding: 30px;
                                margin: 0;
                            }
                            h1 {
                                color: #2c7a7b;
                                font-weight: 700;
                                font-size: 2.5em;
                                margin-bottom: 30px;
                                text-align: center;
                                text-shadow: 0 1px 2px rgba(0,0,0,0.1);
                            }
                            h3 {
                                background: #e6fffa;
                                padding: 12px 20px;
                                border-radius: 8px;
                                font-size: 1.2em;
                                color: #234e52;
                                font-weight: 600;
                                margin-bottom: 20px;
                                box-shadow: 0 2px 5px rgba(0, 0, 0, 0.05);
                            }
                            h4 {
                                font-size: 1em;
                                color: #4a5568;
                                font-weight: 500;
                                margin: 8px 0;
                            }
                            .card {
                                background: #ffffff;
                                border-radius: 12px;
                                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
                                padding: 25px 30px;
                                margin-bottom: 25px;
                                transition: box-shadow 0.3s ease;
                            }
                            .card:hover {
                                box-shadow: 0 8px 20px rgba(0, 0, 0, 0.10);
                            }
                            h2 {
                                color: #276749;
                                border-left: 6px solid #38a169;
                                padding-left: 15px;
                                font-weight: 700;
                                font-size: 1.5em;
                                display: flex;
                                align-items: center;
                                gap: 12px;
                                margin-bottom: 20px;
                            }
                            table {
                                border-collapse: separate;
                                border-spacing: 0;
                                width: 100%;
                                font-size: 14px;
                                border-radius: 12px;
                                overflow: hidden;
                                box-shadow: 0 2px 5px rgba(0,0,0,0.05);
                            }
                            th, td {
                                padding: 12px 15px;
                                text-align: left;
                                border-bottom: 1px solid #e2e8f0;
                                vertical-align: middle;
                            }
                            th {
                                background-color: #38a169;
                                color: #fff;
                                font-weight: 600;
                                font-size: 15px;
                            }
                            tr:last-child td {
                                border-bottom: none;
                            }
                            tr:hover {
                                background-color: #f0fff4;
                            }
                            .success {
                                color: #2f855a;
                                font-weight: 700;
                                display: flex;
                                align-items: center;
                                gap: 6px;
                            }
                            .failure {
                                color: #c53030;
                                font-weight: 700;
                                display: flex;
                                align-items: center;
                                gap: 6px;
                            }
                            .summary {
                                margin-top: 18px;
                                background: #e6fffa;
                                padding: 12px 20px;
                                border-radius: 10px;
                                font-size: 15px;
                                color: #234e52;
                                display: flex;
                                justify-content: center;
                                align-items: center;
                                gap: 12px;
                                font-weight: 600;
                            }
                            .summary span {
                                display: flex;
                                align-items: center;
                                gap: 6px;
                            }
                            img {
                                vertical-align: middle;
                            }
                        </style>
                        </head><body>
                        <h1>Automation Summary Report</h1>
                        """
                                + "<h3>Test Execution Date & Time: <code>" + DateTime + "</code></h3>"
                                + "<h3>Screenshots & Downloaded Excels are available on shared path: <code>" + sharedPath + "</code></h3>"
                                + """
    <h4> Note: Folders are cleaned after every run.</h4>
    <h4>Test detail logs are attached.</h4>
    <h4>This is an automated email from Jenkins.</h4>
    """
                );


            } catch (IOException e) {
                e.printStackTrace();
            }
            headerWritten = true;
        }

        public static void writeTestSummary(String country) {
            String arrowImgTag = "<img src=\"data:image/png;base64," + arrowBase64 + "\" alt=\"→\" style=\"width:16px; height:16px; vertical-align:middle; margin: 0 6px;\">";
            try (OutputStreamWriter writer =
                         new OutputStreamWriter(new FileOutputStream("summary.html", true), StandardCharsets.UTF_8)) {

                writer.write("<div class='card'>\n");
                writer.write("<h2><img src=\"data:image/png;base64," + countryBase64 + "\" alt=\"Country Icon\" style=\"width:28px; height:28px; border-radius:50%;\"> " + country + "</h2>\n");


                writer.write("<table><tr><th>Status</th><th>Screen</th></tr>\n");

                for (String result : screenResults) {
                    // Expected format: "success|Screen Name → Upload Success"
                    String[] parts = result.split("\\|", 2);
                    if (parts.length != 2) continue; // skip bad lines

                    String cssClass = parts[0].trim();  // "success" or "failure"
                       String text = parts[1].trim();// the actual message

                    // Replace [[arrow]] placeholder here:
                    text = text.replace("[[arrow]]", arrowImgTag);

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
                        + "<span class='success'>Downloads: " + downloadSuccess + " <img src='data:image/png;base64," + successBase64 + "' alt='Success' style='width:18px; height:18px;'></span>"
                        + "<span class='failure'>/ " + downloadFailure + " <img src='data:image/png;base64," + failureBase64 + "' alt='Failure' style='width:18px; height:18px;'></span>"
                        + "<img src='data:image/png;base64," + arrowBase64 + "' alt='Arrow' style='width:20px; height:20px; margin: 0 10px;'>"
                        + "<span class='success'>Uploads: " + uploadSuccess + " <img src='data:image/png;base64," + successBase64 + "' alt='Success' style='width:18px; height:18px;'></span>"
                        + "<span class='failure'>/ " + uploadFailure + " <img src='data:image/png;base64," + failureBase64 + "' alt='Failure' style='width:18px; height:18px;'></span>"
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
