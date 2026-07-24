package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.util.*;

/**
 * Performs a strict 1-to-1 comparison between an uploaded Excel file and a downloaded Excel file.
 *
 * Unlike UploadedVsDownloadedComparator (which tolerates extra records in the downloaded file),
 * ExactExcelComparator requires:
 *  1. The exact same number of data records in both files.
 *  2. Every record in the upload file must exist exactly once in the download file, but
 *     their row indexes (ordering) do not have to match.
 */
public class ExactExcelComparator {

    private static final Logger logger = LoggerUtil.getLogger(ExactExcelComparator.class);

    // Overload for backward compatibility (compares all columns by default)
    public static void compare(File uploadedFile,
                               File downloadedFile,
                               ValidationResult result) {
        compare(uploadedFile, downloadedFile, null, result);
    }

    public static void compare(File uploadedFile,
                               File downloadedFile,
                               List<String> compareColumns,
                               ValidationResult result) {

        logger.info("🔍 Exact Excel Comparison (Order-Independent)");
        DataFormatter formatter = new DataFormatter();

        try (Workbook uploadedWb = WorkbookFactory.create(new FileInputStream(uploadedFile));
             Workbook downloadedWb = WorkbookFactory.create(new FileInputStream(downloadedFile))) {

            Sheet uploadedSheet = uploadedWb.getSheetAt(0);
            Sheet downloadedSheet = downloadedWb.getSheetAt(0);

            // Step 1: Resolve columns to compare (use all columns if none specified)
            List<String> columnsToCompare = resolveColumnsToCompare(uploadedSheet, compareColumns, formatter);
            logger.info("Compare columns : {}", columnsToCompare);

            // Step 2: Map column headers to their corresponding column indexes
            Map<String, Integer> uploadedColumnIndex =
                    mapColumnsToIndexes(uploadedSheet, columnsToCompare, formatter, "UPLOADED");
            Map<String, Integer> downloadedColumnIndex =
                    mapColumnsToIndexes(downloadedSheet, columnsToCompare, formatter, "DOWNLOADED");

            for (String column : columnsToCompare) {
                if (!uploadedColumnIndex.containsKey(column)) {
                    result.fail("Compare column '" + column + "' not found in uploaded file.");
                    return;
                }
                if (!downloadedColumnIndex.containsKey(column)) {
                    result.fail("Compare column '" + column + "' not found in downloaded file.");
                    return;
                }
            }

            // Step 3: Read and normalize rows for compared columns
            List<String[]> uploadedRows = readRows(uploadedSheet, columnsToCompare, uploadedColumnIndex, formatter);
            List<String[]> downloadedRows = readRows(downloadedSheet, columnsToCompare, downloadedColumnIndex, formatter);

            logger.info("Uploaded records read  : {}", uploadedRows.size());
            logger.info("Downloaded records read: {}", downloadedRows.size());

            // Step 4: Enforce exact size matching (Strict requirement)
            if (uploadedRows.size() != downloadedRows.size()) {
                String errorMsg = String.format(
                        "Record count mismatch. Uploaded=%d Downloaded=%d",
                        uploadedRows.size(), downloadedRows.size());
                logger.info("❌ {}", errorMsg);
                result.fail(errorMsg);
                return;
            }

            // Step 5: Extract counts of keys for order-independent validation
            Map<String, Integer> uploadedKeyCounts = toKeyCounts(uploadedRows);
            Map<String, Integer> downloadedKeyCounts = toKeyCounts(downloadedRows);

            // Step 6: Verify exact matching between key multisets
            if (!uploadedKeyCounts.equals(downloadedKeyCounts)) {
                List<String> missingFromDownloaded = findMissing(uploadedRows, downloadedKeyCounts);
                List<String> extraInDownloaded = findMissing(downloadedRows, uploadedKeyCounts);

                Map<String, Integer> mismatchColumnCounts =
                        diagnoseMismatches(uploadedRows, downloadedRows, columnsToCompare, missingFromDownloaded);

                if (!missingFromDownloaded.isEmpty()) {
                    logMismatches("Uploaded record(s) NOT found in downloaded file", missingFromDownloaded);
                }
                if (!extraInDownloaded.isEmpty()) {
                    logMismatches("Unmatched downloaded record(s) (not present in upload)", extraInDownloaded);
                }

                String failMessage = String.format(
                        "Exact comparison failed. %d record(s) mismatched. Likely column(s): %s. First mismatched key: %s",
                        missingFromDownloaded.size(),
                        summarize(mismatchColumnCounts),
                        missingFromDownloaded.isEmpty() ? "N/A" : missingFromDownloaded.get(0));

                logger.info("❌ {}", failMessage);
                result.fail(failMessage);
                return;
            }

            // Step 7: Success execution path
            String passMessage = String.format(
                    "Uploaded Records=%d, Downloaded Records=%d, All Values Matched Successfully.",
                    uploadedRows.size(), downloadedRows.size());
            logger.info("✅ {}", passMessage);
            result.pass(passMessage);

        } catch (Exception e) {
            logger.error("Exact compare failed due to exception", e);
            result.fail("Exact compare failed: " + e.getMessage());
        }
    }

    private static List<String> resolveColumnsToCompare(Sheet uploadedSheet,
                                                        List<String> compareColumns,
                                                        DataFormatter formatter) {
        if (compareColumns != null && !compareColumns.isEmpty()) {
            return compareColumns;
        }
        List<String> allColumns = new ArrayList<>();
        Row header = uploadedSheet.getRow(0);
        if (header != null) {
            for (Cell cell : header) {
                allColumns.add(formatter.formatCellValue(cell).trim());
            }
        }
        return allColumns;
    }

    private static Map<String, Integer> mapColumnsToIndexes(Sheet sheet,
                                                            List<String> columnsToCompare,
                                                            DataFormatter formatter,
                                                            String label) {
        Map<String, Integer> result = new HashMap<>();
        Row header = sheet.getRow(0);
        if (header == null) {
            return result;
        }

        Map<String, Integer> headerLookup = new HashMap<>();
        for (Cell cell : header) {
            headerLookup.put(normalizeText(formatter.formatCellValue(cell)), cell.getColumnIndex());
        }

        for (String column : columnsToCompare) {
            Integer index = headerLookup.get(normalizeText(column));
            if (index != null) {
                result.put(column, index);
            }
        }
        return result;
    }

    private static List<String[]> readRows(Sheet sheet,
                                           List<String> columnsToCompare,
                                           Map<String, Integer> columnIndex,
                                           DataFormatter formatter) {
        List<String[]> rows = new ArrayList<>();

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String[] values = new String[columnsToCompare.size()];
            boolean allBlank = true;

            for (int i = 0; i < columnsToCompare.size(); i++) {
                Integer index = columnIndex.get(columnsToCompare.get(i));
                Cell cell = (index == null) ? null : row.getCell(index);
                values[i] = normalizeCell(cell, formatter);
                if (!values[i].isBlank()) allBlank = false;
            }

            if (!allBlank) {
                rows.add(values);
            }
        }
        return rows;
    }

    private static Map<String, Integer> toKeyCounts(List<String[]> rows) {
        Map<String, Integer> keyCounts = new LinkedHashMap<>();
        for (String[] row : rows) {
            keyCounts.merge(toKey(row), 1, Integer::sum);
        }
        return keyCounts;
    }

    private static String toKey(String[] row) {
        StringBuilder key = new StringBuilder();
        for (String value : row) {
            key.append(value).append(" || ");
        }
        return key.toString();
    }

    private static List<String> findMissing(List<String[]> sourceRows, Map<String, Integer> targetKeyCounts) {
        List<String> missing = new ArrayList<>();
        Map<String, Integer> remainingTargetCounts = new HashMap<>(targetKeyCounts);

        for (String[] sourceRow : sourceRows) {
            String key = toKey(sourceRow);
            int remaining = remainingTargetCounts.getOrDefault(key, 0);

            if (remaining > 0) {
                remainingTargetCounts.put(key, remaining - 1);
            } else {
                missing.add(key);
            }
        }
        return missing;
    }

    private static Map<String, Integer> diagnoseMismatches(List<String[]> uploadedRows,
                                                           List<String[]> downloadedRows,
                                                           List<String> columnsToCompare,
                                                           List<String> missingKeys) {
        Map<String, Integer> mismatchColumnCounts = new LinkedHashMap<>();
        if (downloadedRows.isEmpty()) return mismatchColumnCounts;

        Set<String> missingKeySet = new HashSet<>(missingKeys);

        for (String[] uploadedRow : uploadedRows) {
            if (!missingKeySet.contains(toKey(uploadedRow))) continue;

            String[] closest = findClosestRow(uploadedRow, downloadedRows);
            for (int i = 0; i < columnsToCompare.size(); i++) {
                if (!uploadedRow[i].equals(closest[i])) {
                    mismatchColumnCounts.merge(columnsToCompare.get(i), 1, Integer::sum);
                }
            }
        }
        return mismatchColumnCounts;
    }

    private static String[] findClosestRow(String[] target, List<String[]> candidates) {
        String[] best = candidates.get(0);
        int bestScore = -1;

        for (String[] candidate : candidates) {
            int score = 0;
            for (int i = 0; i < target.length; i++) {
                if (target[i].equals(candidate[i])) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static String summarize(Map<String, Integer> mismatchColumnCounts) {
        if (mismatchColumnCounts.isEmpty()) return "none identified";

        StringBuilder sb = new StringBuilder();
        mismatchColumnCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(entry -> {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(entry.getKey()).append(" (").append(entry.getValue()).append(")");
                });
        return sb.toString();
    }

    private static void logMismatches(String label, List<String> keys) {
        if (keys.isEmpty()) return;
        logger.info("❌ {} ({} record(s)). Showing up to 5:", label, keys.size());
        keys.stream().limit(5).forEach(key -> logger.info("   Key: [{}]", key));
    }

    private static String normalizeCell(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";

        boolean isNumeric = cell.getCellType() == CellType.NUMERIC
                || (cell.getCellType() == CellType.FORMULA
                && cell.getCachedFormulaResultType() == CellType.NUMERIC);

        if (isNumeric && !DateUtil.isCellDateFormatted(cell)) {
            return normalizeNumber(cell.getNumericCellValue());
        }

        String text = formatter.formatCellValue(cell);
        String trimmed = text.trim();

        if (isNumericText(trimmed)) {
            return normalizeNumber(Double.parseDouble(trimmed));
        }

        return normalizeText(text);
    }

    private static String normalizeText(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    private static boolean isNumericText(String text) {
        if (text == null || text.isBlank()) return false;
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String normalizeNumber(double value) {
        if (Double.isNaN(value)) {
            return "NAN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "INFINITY" : "-INFINITY";
        }
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return new BigDecimal(String.valueOf(value)).stripTrailingZeros().toPlainString();
    }
}