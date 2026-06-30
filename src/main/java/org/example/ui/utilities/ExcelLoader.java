package org.example.ui.utilities;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.util.*;

public class ExcelLoader {

    public static List<Map<String, Object>> loadScreens(String configPath) throws Exception {
        List<Map<String, Object>> screens = new ArrayList<>();
        FileInputStream fis = new FileInputStream(configPath);
        Workbook workbook = new XSSFWorkbook(fis);

        // --- Screens Sheet ---
        Sheet screensSheet = workbook.getSheet("Screens");
        List<String> headers = getHeaders(screensSheet.getRow(0));

        for (int i = 1; i <= screensSheet.getLastRowNum(); i++) {
            Row row = screensSheet.getRow(i);
            if (row == null) continue;

            Cell idCell = row.getCell(0); // testID column (assumed first)
            if (idCell == null || idCell.toString().trim().isEmpty()) continue;

            String testID = idCell.toString().trim();
            Map<String, Object> screen = new LinkedHashMap<>();

            for (int j = 0; j < headers.size(); j++) {
                Cell cell = row.getCell(j);
                String value = (cell == null) ? "" : cell.toString().trim();
                screen.put(headers.get(j), value);
            }

            // Ensure testID key always exists
            screen.put("testID", testID);
            screens.add(screen);
        }

        // --- Params Sheet ---
        Sheet paramsSheet = workbook.getSheet("Params");
        for (int i = 1; i <= paramsSheet.getLastRowNum(); i++) {
            Row row = paramsSheet.getRow(i);
            if (row == null) continue;

            String testID = row.getCell(0).getStringCellValue();
            String key = row.getCell(2).getStringCellValue();
            String value = row.getCell(3).getStringCellValue();

            screens.stream()
                    .filter(s -> s.get("testID").equals(testID))
                    .forEach(s -> {
                        Map<String, String> params =
                                (Map<String, String>) s.getOrDefault("params", new LinkedHashMap<>());
                        params.put(key, value);
                        s.put("params", params);
                    });
        }


        // --- Rules Sheet ---
        Sheet rulesSheet = workbook.getSheet("Rules");
        for (int i = 1; i <= rulesSheet.getLastRowNum(); i++) {
            Row row = rulesSheet.getRow(i);
            if (row == null) continue;

            String testID = row.getCell(0).getStringCellValue();
            String columnName = row.getCell(2).getStringCellValue();
            String type = row.getCell(3).getStringCellValue();
            int length;
            Cell lengthCell = row.getCell(4);
            if (lengthCell != null && lengthCell.getCellType() == CellType.NUMERIC) {
                length = (int) lengthCell.getNumericCellValue();
            } else {
                length = 0;
            }

            String prefix = row.getCell(5) != null ? row.getCell(5).getStringCellValue() : "";

            screens.stream()
                    .filter(s -> s.get("testID").equals(testID))
                    .forEach(s -> {
                        Map<String, Map<String, Object>> rules =
                                (Map<String, Map<String, Object>>) s.getOrDefault("rules", new LinkedHashMap<>());
                        Map<String, Object> ruleDetails = new LinkedHashMap<>();
                        ruleDetails.put("type", type);
                        ruleDetails.put("length", length);
                        ruleDetails.put("prefix", prefix);
                        rules.put(columnName, ruleDetails);
                        s.put("rules", rules);
                    });
        }

        // --- Validations Sheet ---
        Sheet validationsSheet = workbook.getSheet("Validations");

        if (validationsSheet != null) {

            List<String> validationHeaders =
                    getHeaders(validationsSheet.getRow(0));

            for (int i = 1; i <= validationsSheet.getLastRowNum(); i++) {

                Row row = validationsSheet.getRow(i);
                if (row == null) continue;

                String testID = row.getCell(0).getStringCellValue().trim();

                Map<String, Object> validationMap =
                        new LinkedHashMap<>();

                for (int j = 0; j < validationHeaders.size(); j++) {

                    Cell cell = row.getCell(j);

                    String value =
                            (cell == null) ? "" : cell.toString().trim();

                    validationMap.put(validationHeaders.get(j), value);
                }

                screens.stream()
                        .filter(s -> s.get("testID").equals(testID))
                        .forEach(s -> {
                            Map<String, Map<String, Object>> validations =
                                    (Map<String, Map<String, Object>>) s.getOrDefault(
                                            "validations",
                                            new LinkedHashMap<>()
                                    );

                            validations.put(testID, validationMap);

                            s.put("validations", validations);
                        });
            }
        }
        // --- ScenarioData Sheet ---
        Sheet scenarioSheet = workbook.getSheet("ScenarioData");

        if (scenarioSheet != null) {

            for (int i = 1; i <= scenarioSheet.getLastRowNum(); i++) {

                Row row = scenarioSheet.getRow(i);
                if (row == null) continue;

                String testID = row.getCell(0).toString().trim();
                String key = row.getCell(1).toString().trim();
                String value = row.getCell(2).toString().trim();

                screens.stream()
                        .filter(s -> testID.equals(s.get("testID")))
                        .forEach(s -> {

                            Map<String, String> scenarioData =
                                    (Map<String, String>) s.getOrDefault(
                                            "scenarioData",
                                            new LinkedHashMap<>());

                            scenarioData.put(key, value);

                            s.put("scenarioData", scenarioData);
                        });
            }
        }

        workbook.close();
        fis.close();
        return screens;
    }


    private static List<String> getHeaders(Row headerRow) {
        List<String> headers = new ArrayList<>();
        if (headerRow == null) return headers;
        for (Cell cell : headerRow) {
            headers.add(cell.getStringCellValue().trim());
        }
        return headers;
    }
}
