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
                if ("ErrorMessage".equalsIgnoreCase(cell.getStringCellValue().trim())) {
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
    }

    // --- REUSABLE LOOKUP & EXTRACTION METHODS ---

    /**
     * Looks up a specific row by matching keyColumn = keyValue, then retrieves the value
     * from targetColumnName in that same row.
     */
    public static String getCellValueByRowKey(File excelFile, String keyColumnName, String keyValue, String targetColumnName) {
        logger.info("📄 Reading [{}] where [{}] = [{}] in file: {}", targetColumnName, keyColumnName, keyValue, excelFile.getName());

        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new RuntimeException("Excel file is empty. Header row not found.");
            }

            int keyColIdx = findColumnIndex(headerRow, keyColumnName);
            int targetColIdx = findColumnIndex(headerRow, targetColumnName);

            if (keyColIdx == -1 || targetColIdx == -1) {
                throw new IllegalArgumentException(String.format("Column mapping failed. Key Col [%s]: %d, Target Col [%s]: %d",
                        keyColumnName, keyColIdx, targetColumnName, targetColIdx));
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell keyCell = row.getCell(keyColIdx);
                String currentKeyValue = getCellStringValue(keyCell);

                if (keyValue.trim().equalsIgnoreCase(currentKeyValue)) {
                    Cell targetCell = row.getCell(targetColIdx);
                    String result = getCellStringValue(targetCell);
                    logger.info(
                            "🎯 Matched Row {} | Found Value: [{}]",
                            i + 1,
                            result
                    );
                    return result;
                }
            }

            throw new RuntimeException(String.format("Row with [%s = %s] was not found in file [%s]", keyColumnName, keyValue, excelFile.getName()));

        } catch (Exception e) {
            logger.error("❌ Failed to extract cell value: {}", e.getMessage(), e);
            throw new RuntimeException("Excel value lookup failed: " + e.getMessage(), e);
        }
    }

    /**
     * Helper to find a column index by header name.
     */
    private static int findColumnIndex(Row headerRow, String headerName) {
        if (headerRow == null) return -1;
        for (Cell cell : headerRow) {
            if (cell != null && headerName.equalsIgnoreCase(getCellStringValue(cell))) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    /**
     * Helper to safely extract string value from any cell type.
     */
    private static String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType();

        if (type == CellType.STRING) {
            return cell.getStringCellValue().trim();
        } else if (type == CellType.NUMERIC) {
            double numVal = cell.getNumericCellValue();
            // Format whole numbers clean without decimals (e.g., 0 instead of 0.0)
            if (numVal == (long) numVal) {
                return String.valueOf((long) numVal);
            }
            return String.valueOf(numVal);
        } else if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue()).trim();
        } else if (type == CellType.FORMULA) {
            return cell.getCellFormula().trim();
        }
        return "";
    }
}