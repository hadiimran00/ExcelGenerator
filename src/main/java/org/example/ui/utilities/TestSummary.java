package org.example.ui.utilities;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Utility class to track and report test results for upload and download operations.
 */
public class TestSummary {

    private static int upTestPassed = 0;
    private static int upTestFailed = 0;
    private static int downTestPassed = 0;
    private static int downTestFailed = 0;

    // === Increment Methods ===
    public static void recordUploadSuccess() {
        upTestPassed++;
    }

    public static void recordUploadFailure() {
        upTestFailed++;
    }

    public static void recordDownloadSuccess() {
        downTestPassed++;
    }

    public static void recordDownloadFailure() {
        downTestFailed++;
    }

    // === Reporting ===
    public static void writeTestSummary(String country) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("summary.txt", true))) {
            writer.println("==================================================");
            writer.println("            TEST FINISHED FOR COUNTRY: " + country);
            writer.println("==================================================");

            writer.printf("%-25s : %d%n", " Upload Tests Total", upTestPassed + upTestFailed);
            writer.printf("%-25s : %d%n", " Upload Tests Passed", upTestPassed);
            writer.printf("%-25s : %d%n", " Upload Tests Failed", upTestFailed);

            writer.println("--------------------------------------------");

            writer.printf("%-25s : %d%n", " Download Tests Total", downTestPassed + downTestFailed);
            writer.printf("%-25s : %d%n", " Download Tests Passed", downTestPassed);
            writer.printf("%-25s : %d%n", " Download Tests Failed", downTestFailed);

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

    // === Reset Counters ===
    public static void resetTestCounter() {
        upTestPassed = 0;
        upTestFailed = 0;
        downTestPassed = 0;
        downTestFailed = 0;
    }
}
