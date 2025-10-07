package org.example.api;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

public class ExpectedValuesLoader {
    public static List<String> loadExpected(String excelPath) {
        List<String> list = new ArrayList<>();
        File f = new File(excelPath);
        if (!f.exists()) {
            System.err.println("ExpectedValuesLoader: file not found -> " + excelPath);
            return list;
        }
        try (InputStream inp = new FileInputStream(f);
             Workbook wb = new XSSFWorkbook(inp)) {
            Sheet sheet = wb.getSheetAt(0);
            System.out.println("Sheet name: " + sheet.getSheetName() + ", lastRow: " + sheet.getLastRowNum());
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Cell cell = row.getCell(0);
                if (cell == null) continue;
                String val = getCellAsString(cell);
                if (val != null && !val.trim().isEmpty()) {
                    list.add(val.trim());
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading expected values from Excel: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    private static String getCellAsString(Cell cell) {
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception ignored) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default: return "";
        }
    }
}
