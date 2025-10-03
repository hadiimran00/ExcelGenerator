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

            writer.println("============================================");
            writer.println();
        } catch (IOException e) {
            System.out.println("❌ Failed to write summary: " + e.getMessage());
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
