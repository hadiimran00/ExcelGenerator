package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;

public class ExcelValidator {
    private static final Logger logger = LoggerUtil.getLogger(ExcelValidator.class);
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
                logger.warn("⚠️ No 'Error Message' column found in {}", excelFile.getName());
                return;
            }

            // Print all error messages
            logger.info("🔍 Excel Validation Errors:");
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    Cell errorCell = row.getCell(errorColIndex);
                    if (errorCell != null) {
                        String errorMsg = errorCell.toString().trim();
                        if (!errorMsg.isEmpty()) {
                            logger.info("   ❌ Row {}: {}", i + 1, errorMsg);
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("❌ Failed to read error Excel file: {}", e.getMessage(), e);
        }
}}
