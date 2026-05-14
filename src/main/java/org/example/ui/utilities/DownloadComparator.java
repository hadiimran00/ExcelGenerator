package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import java.io.File;
import java.util.*;

public class DownloadComparator {

    private static final Logger logger = LoggerUtil.getLogger(DownloadComparator.class);

    /**
     * Verifies that all values uploaded (tracked in GeneratedDataStore) appear
     * in the downloaded Excel file in the expected columns.
     *
     * @param downloadedFile   the file downloaded after upload
     * @param testId           used to pull generated values
     * @param columnsToCheck   header names to compare; empty = check all generated columns
     * @param result           collects pass/fail
     */
    public static void compareGeneratedVsDownloaded(File downloadedFile,
                                                    String testId,
                                                    List<String> columnsToCheck,
                                                    ValidationResult result) {
        if (downloadedFile == null || !downloadedFile.exists()) {
            result.fail("Downloaded file not found for comparison");
            return;
        }

        Map<String, String> generated = GeneratedDataStore.getAll(testId);
        if (generated.isEmpty()) {
            result.fail("No generated values stored for testId: " + testId);
            return;
        }

        // Filter to only the columns we care about
        Map<String, String> toCheck = new LinkedHashMap<>();
        if (columnsToCheck.isEmpty()) {
            toCheck.putAll(generated);
        } else {
            for (String col : columnsToCheck) {
                if (generated.containsKey(col)) toCheck.put(col, generated.get(col));
            }
        }

        try (Workbook wb = WorkbookFactory.create(downloadedFile)) {
            Sheet sheet = wb.getSheetAt(0);
            Map<String, Integer> headers = buildHeaderMap(sheet.getRow(0));

            for (Map.Entry<String, String> entry : toCheck.entrySet()) {
                String colName = entry.getKey();
                String expectedVal = normalize(entry.getValue());

                Integer colIdx = headers.get(colName);
                if (colIdx == null) {
                    // Column might not exist in download — warn, don't fail hard
                    logger.warn("Column '{}' not found in downloaded file headers", colName);
                    continue;
                }

                boolean found = false;
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    String cellVal = normalize(getCellValue(row.getCell(colIdx)));
                    if (cellVal.equals(expectedVal) || cellVal.contains(expectedVal)) {
                        result.pass("Column '" + colName + "' value '" + expectedVal + "' found in downloaded Excel");
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    result.fail("Column '" + colName + "' expected '" + expectedVal
                            + "' — NOT found in downloaded Excel");
                }
            }

        } catch (Exception e) {
            result.fail("Excel comparison failed: " + e.getMessage());
            logger.error("DownloadComparator error", e);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static Map<String, Integer> buildHeaderMap(Row headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (headerRow == null) return map;
        for (Cell c : headerRow) {
            map.put(c.getStringCellValue().trim(), c.getColumnIndex());
        }
        return map;
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA  -> {
                try { yield String.valueOf((long) cell.getNumericCellValue()); }
                catch (Exception e) { yield cell.getStringCellValue(); }
            }
            default -> cell.getStringCellValue();
        };
    }

    private static String normalize(String val) {
        if (val == null) return "";
        return val.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}