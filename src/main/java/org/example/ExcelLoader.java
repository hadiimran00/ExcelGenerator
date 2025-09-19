package org.example;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.util.*;

public class ExcelLoader {

    public static List<Map<String, Object>> loadScreens(String configPath) throws Exception {
        List<Map<String, Object>> screens = new ArrayList<>();
        FileInputStream fis = new FileInputStream(configPath);
        Workbook workbook = new XSSFWorkbook(fis);

        // --- Screens Sheet (generic header-based reader) ---
        Sheet screensSheet = workbook.getSheet("Screens");
        List<String> headers = getHeaders(screensSheet.getRow(0));

        for (int i = 1; i <= screensSheet.getLastRowNum(); i++) {
            Row row = screensSheet.getRow(i);
            if (row == null) continue; // skip empty row object

            // Check if first column (screenName) is blank → skip
            Cell screenNameCell = row.getCell(0);
            if (screenNameCell == null || screenNameCell.toString().trim().isEmpty()) {
                continue;
            }

            Map<String, Object> screen = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                Cell cell = row.getCell(j);
                String value = (cell == null) ? "" : cell.toString().trim();
                screen.put(headers.get(j), value);
            }

            screens.add(screen);
        }

        // --- Params Sheet ---
        Sheet paramsSheet = workbook.getSheet("Params");
        for (int i = 1; i <= paramsSheet.getLastRowNum(); i++) {
            Row row = paramsSheet.getRow(i);
            if (row == null) continue;

            String screenName = row.getCell(0).getStringCellValue();
            String key = row.getCell(1).getStringCellValue();
            String value = row.getCell(2).getStringCellValue();

            screens.stream()
                    .filter(s -> s.get("screenName").equals(screenName))
                    .forEach(s -> {
                        Map<String, String> params = (Map<String, String>) s.getOrDefault("params", new LinkedHashMap<>());
                        params.put(key, value);
                        s.put("params", params);
                    });
        }

        // --- Rules Sheet ---
        Sheet rulesSheet = workbook.getSheet("Rules");
        for (int i = 1; i <= rulesSheet.getLastRowNum(); i++) {
            Row row = rulesSheet.getRow(i);
            if (row == null) continue;

            String screenName = row.getCell(0).getStringCellValue();
            String columnName = row.getCell(1).getStringCellValue();
            String type = row.getCell(2).getStringCellValue();
            int length;
            Cell lengthCell = row.getCell(3);
            if (lengthCell != null && lengthCell.getCellType() == CellType.NUMERIC) {
                length = (int) lengthCell.getNumericCellValue();
            } else {
                length = 0;
            }

            String prefix = row.getCell(4) != null ? row.getCell(4).getStringCellValue() : "";

            screens.stream()
                    .filter(s -> s.get("screenName").equals(screenName))
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

        workbook.close();
        fis.close();
        return screens;
    }

    // --- Utility to read headers from first row ---
    private static List<String> getHeaders(Row headerRow) {
        List<String> headers = new ArrayList<>();
        if (headerRow == null) return headers;
        for (Cell cell : headerRow) {
            headers.add(cell.getStringCellValue().trim());
        }
        return headers;
    }
}
