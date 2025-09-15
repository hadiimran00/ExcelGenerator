package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

public class ExcelFlowDriver {

    public static void main(String[] args) {
        try {
            // Load JSON config
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new File("config.json"));

            for (JsonNode screen : root.get("screens")) {
                String screenName = screen.get("screenName").asText();
                String mode = screen.get("mode").asText();
                String rules = screen.get("rules").asText(); // temp placeholder if you want to split rules per screen

                System.out.println("\n=== Processing Screen: " + screenName + " ===");
                System.out.println("Mode: " + mode);

                switch (mode) {
                    case "UPLOAD_ONLY":
                        uploadFile(screenName + ".xlsx");
                        break;

                    case "UPLOAD_WITH_CHANGES":
                        String updatedFile = ExcelGen.generateExcel(
                                screenName + ".xlsx",
                                screenName + "_updated.xlsx",
                                rules
                        );
                        uploadFile(updatedFile);
                        break;

                    case "DOWNLOAD_EDIT_UPLOAD":
                        String downloaded = downloadExcel(screenName);
                        String updated2 = ExcelGen.generateExcel(
                                downloaded,
                                screenName + "_updated.xlsx",
                                rules
                        );
                        uploadFile(updated2);
                        break;

                    default:
                        System.out.println("Unknown mode: " + mode);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // === Placeholder methods ===
    private static void uploadFile(String filePath) {
        System.out.println("Uploading file: " + filePath);
    }

    private static String downloadExcel(String screenName) {
        System.out.println("Downloading Excel for: " + screenName);
        return screenName + "_downloaded.xlsx";
    }
}
