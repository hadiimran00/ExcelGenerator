package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.util.*;

/**
 * Compares an uploaded Excel file against a downloaded Excel file.
 *
 * How it works:
 *  - Each row is turned into a single "key" string, built from the values
 *    of the columns we care about (either all columns, or just the ones
 *    passed in via compareColumns).
 *  - We then check: does every uploaded row's key also exist somewhere in
 *    the downloaded file? Row order does not matter.
 *
 * Note: the downloaded file is expected to contain MORE records than the
 * uploaded file (a download typically returns the full dataset, not just
 * what was just uploaded). So we only fail if an uploaded row is MISSING
 * from the downloaded file — extra records in the downloaded file are
 * normal and not a failure.
 *
 * A value mismatch in any compared column shows up as a missing key,
 * since the key changes when any value changes. To make debugging
 * easier, when a row is missing we also find its closest match in the
 * downloaded file and log which column(s) differ.
 */
public class UploadedVsDownloadedComparator {

    private static final Logger logger = LoggerUtil.getLogger(UploadedVsDownloadedComparator.class);

    public static void compare(File uploadedFile,
                               File downloadedFile,
                               List<String> compareColumns,
                               ValidationResult result) throws Exception {

        logger.info("──────────────────────────────────────────");
        logger.info("🔍 Comparing Uploaded vs Downloaded Excel (order-independent)");
        logger.info("Uploaded file   : {} (lastModified={})",
                uploadedFile.getAbsolutePath(), new Date(uploadedFile.lastModified()));
        logger.info("Downloaded file : {} (lastModified={})",
                downloadedFile.getAbsolutePath(), new Date(downloadedFile.lastModified()));

        DataFormatter formatter = new DataFormatter();

        try (Workbook uploadedWb = WorkbookFactory.create(new FileInputStream(uploadedFile));
             Workbook downloadedWb = WorkbookFactory.create(new FileInputStream(downloadedFile))) {

            Sheet uploadedSheet = uploadedWb.getSheetAt(0);
            Sheet downloadedSheet = downloadedWb.getSheetAt(0);

            // Step 1: figure out which columns to compare.
            // If compareColumns is empty, use every column in the
            // uploaded file's header row.
            List<String> columnsToCompare = resolveColumnsToCompare(uploadedSheet, compareColumns, formatter);
            logger.info("Compare columns : {}", columnsToCompare);

            // Step 2: map each column name to its index, in both files.
            // Fails immediately if a requested column is missing.
            Map<String, Integer> uploadedColumnIndex =
                    mapColumnsToIndexes(uploadedSheet, columnsToCompare, formatter, "UPLOADED");
            Map<String, Integer> downloadedColumnIndex =
                    mapColumnsToIndexes(downloadedSheet, columnsToCompare, formatter, "DOWNLOADED");

            for (String column : columnsToCompare) {
                if (!uploadedColumnIndex.containsKey(column)) {
                    fail(result, "Compare column '" + column + "' not found in uploaded file.");
                    return;
                }
                if (!downloadedColumnIndex.containsKey(column)) {
                    fail(result, "Compare column '" + column + "' not found in downloaded file.");
                    return;
                }
            }

            // Step 3: read every data row from both files as an array of
            // normalized values (one per compared column).
            List<String[]> uploadedRows =
                    readRows(uploadedSheet, columnsToCompare, uploadedColumnIndex, formatter);
            List<String[]> downloadedRows =
                    readRows(downloadedSheet, columnsToCompare, downloadedColumnIndex, formatter);

            logger.info("Uploaded record count  : {}", uploadedRows.size());
            logger.info("Downloaded record count: {}", downloadedRows.size());

            // Step 4: build key -> occurrence-count maps (a "multiset"),
            // so duplicate rows in the uploaded file are handled
            // correctly, not just presence.
            Map<String, Integer> downloadedKeyCounts = toKeyCounts(downloadedRows);

            // Step 5: check that every uploaded row exists in the
            // downloaded file. Extra rows in the downloaded file (which
            // is normal — it contains the full dataset) are ignored.
            List<String> missingFromDownloaded = findMissing(uploadedRows, downloadedKeyCounts);

            if (missingFromDownloaded.isEmpty()) {
                String message = String.format(
                        "All uploaded records verified. Uploaded=%d Downloaded=%d",
                        uploadedRows.size(), downloadedRows.size());
                logger.info("✅ {}", message);
                result.pass(message);
                return;
            }

            // Step 6: for debugging, find the closest match for each
            // missing uploaded row and report which column(s) differ.
            Map<String, Integer> mismatchColumnCounts =
                    diagnoseMismatches(uploadedRows, downloadedRows, columnsToCompare, missingFromDownloaded);

            logMismatches("Uploaded record(s) NOT found in downloaded file", missingFromDownloaded);

            if (!mismatchColumnCounts.isEmpty()) {
                logger.info("📊 Likely mismatched column(s): {}", mismatchColumnCounts);
            }

            String failMessage = String.format(
                    "%d uploaded record(s) not found in downloaded file. Likely column(s): %s. Example key: %s ",
                    missingFromDownloaded.size(),
                    summarize(mismatchColumnCounts),
                    missingFromDownloaded.get(0));

            fail(result, failMessage);

        } finally {
            logger.info("──────────────────────────────────────────");
        }
    }

    // -------------------------------------------------------------------
    // If the caller didn't specify columns, use every column found in
    // the uploaded file's header row.
    // -------------------------------------------------------------------
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

    // -------------------------------------------------------------------
    // Builds "normalized column name" -> column index by reading the
    // sheet's header row.
    // -------------------------------------------------------------------
    private static Map<String, Integer> mapColumnsToIndexes(Sheet sheet,
                                                            List<String> columnsToCompare,
                                                            DataFormatter formatter,
                                                            String label) {
        Map<String, Integer> result = new HashMap<>();
        Row header = sheet.getRow(0);

        if (header == null) {
            logger.info("[{}] Header row is missing.", label);
            return result;
        }

        Map<String, Integer> headerLookup = new HashMap<>();
        for (Cell cell : header) {
            headerLookup.put(normalizeText(formatter.formatCellValue(cell)), cell.getColumnIndex());
        }

        logger.info("[{}] Header columns found: {}", label, headerLookup.keySet());

        for (String column : columnsToCompare) {
            Integer index = headerLookup.get(normalizeText(column));
            if (index != null) {
                result.put(column, index);
            }
        }

        return result;
    }

    // -------------------------------------------------------------------
    // Reads every data row into a normalized String[] aligned with
    // columnsToCompare's order. Rows that are blank across every
    // compared column are skipped.
    // -------------------------------------------------------------------
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

    // -------------------------------------------------------------------
    // Returns every uploaded row's key that does NOT appear (enough
    // times) in the downloaded file. Extra downloaded records are not
    // considered — we only care that uploaded rows made it through.
    // -------------------------------------------------------------------
    private static List<String> findMissing(List<String[]> uploadedRows, Map<String, Integer> downloadedKeyCounts) {
        List<String> missing = new ArrayList<>();
        Map<String, Integer> remainingDownloadedCounts = new HashMap<>(downloadedKeyCounts);

        for (String[] uploadedRow : uploadedRows) {
            String key = toKey(uploadedRow);
            int remaining = remainingDownloadedCounts.getOrDefault(key, 0);

            if (remaining > 0) {
                remainingDownloadedCounts.put(key, remaining - 1);
            } else {
                missing.add(key);
            }
        }

        return missing;
    }

    // -------------------------------------------------------------------
    // DEBUG HELPER: for each missing uploaded row, finds the downloaded
    // row that matches on the most columns, and tallies which column(s)
    // differ most often. This helps quickly spot "it's always column X
    // that's wrong" instead of just seeing rows disappear.
    // -------------------------------------------------------------------
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

    private static void fail(ValidationResult result, String message) {
        logger.info("❌ {}", message);
        result.fail(message);
    }

    // -------------------------------------------------------------------
    // Cell normalization — makes comparison resistant to formatting
    // differences that don't represent a real data difference:
    //  - Numbers: "674" and "674.00000" both become "674"
    //  - Text: trimmed, internal whitespace collapsed, uppercased
    // -------------------------------------------------------------------
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