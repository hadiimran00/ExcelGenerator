package org.example.ui.utilities;

import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;

public class ExcelValidator {
    public static void logExcelErrors(File excelFile) {
        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0); // assuming first sheet
            int errorColIndex = -1;

            // Find "Error Message" column index
            Row headerRow = sheet.getRow(0);
            for (Cell cell : headerRow) {
                if ("Error Message".equalsIgnoreCase(cell.getStringCellValue().trim())) {
                    errorColIndex = cell.getColumnIndex();
                    break;
                }
            }

            if (errorColIndex == -1) {
                System.out.println("⚠️ No 'Error Message' column found in " + excelFile.getName());
                return;
            }

            // Print all error messages
            System.out.println("🔍 Excel Validation Errors:");
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    Cell errorCell = row.getCell(errorColIndex);
                    if (errorCell != null) {
                        String errorMsg = errorCell.toString().trim();
                        if (!errorMsg.isEmpty()) {
                            System.out.println("   ❌ Row " + (i + 1) + ": " + errorMsg);
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("❌ Failed to read error Excel file: " + e.getMessage());
        }
}}
