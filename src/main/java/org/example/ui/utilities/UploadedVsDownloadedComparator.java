package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class UploadedVsDownloadedComparator {

    private static final Logger logger = LoggerUtil.getLogger(UploadedVsDownloadedComparator.class);

    public static void compare(File uploadedFile,
                               File downloadedFile,
                               List<String> compareColumns,
                               ValidationResult result) throws Exception {

        logger.info("──────────────────────────────────────────");
        logger.info("🔍 Comparing Uploaded vs Downloaded Excel");
        logger.info("Uploaded file   : {} (lastModified={})",
                uploadedFile.getAbsolutePath(), new Date(uploadedFile.lastModified()));
        logger.info("Downloaded file : {} (lastModified={})",
                downloadedFile.getAbsolutePath(), new Date(downloadedFile.lastModified()));
        logger.info("Compare columns : {}", compareColumns);

        List<String[]> downloadedDetailed = readRowsDetailed(downloadedFile, compareColumns, "DOWNLOADED");
        List<String[]> uploadedDetailed = readRowsDetailed(uploadedFile, compareColumns, "UPLOADED");

        Set<String> downloadedRows = toKeySet(downloadedDetailed);
        Set<String> uploadedRows = toKeySet(uploadedDetailed);

        logger.info("Downloaded row count: {}", downloadedRows.size());
        logger.info("Uploaded row count  : {}", uploadedRows.size());

        int matched = 0;
        int missing = 0;
        String firstMissing = null;

        // Tracks how many missing rows had a mismatch on each column, e.g.
        // {"Retail Price*": 4} — this is what tells you WHICH column is
        // actually breaking the match, aggregated across all failures,
        // instead of just "N records not found".
        Map<String, Integer> mismatchColumnCounts = new LinkedHashMap<>();

        for (String[] uploadedRow : uploadedDetailed) {
            String key = toKey(uploadedRow);
            if (downloadedRows.contains(key)) {
                matched++;
            } else {
                missing++;
                if (firstMissing == null) {
                    firstMissing = key;
                }
                logger.info("❌ Missing row — running closest-match diff...");
                List<String> mismatchedColumns =
                        diffAgainstClosestMatch(uploadedRow, downloadedDetailed, compareColumns);

                for (String column : mismatchedColumns) {
                    mismatchColumnCounts.merge(column, 1, Integer::sum);
                }
            }
        }

        if (missing == 0) {
            result.pass(String.format(
                    "All uploaded records verified. Uploaded=%d Matched=%d",
                    uploadedRows.size(), matched));
        } else {
            String mismatchSummary = summarizeMismatches(mismatchColumnCounts, missing);

            logger.info("📊 Mismatch summary by column: {}", mismatchColumnCounts);

            result.fail(String.format(
                    "%d uploaded record(s) not found. Likely mismatched column(s): %s. Example: %s",
                    missing,
                    mismatchSummary,
                    firstMissing));
        }

        logger.info("──────────────────────────────────────────");
    }

    /**
     * Builds a short, human-readable summary of which column(s) are
     * causing the mismatches, e.g. "Retail Price* (4/4 rows)".
     * If every missing row disagrees on the same single column, that
     * column is almost certainly the root cause.
     */
    private static String summarizeMismatches(Map<String, Integer> mismatchColumnCounts, int totalMissing) {
        if (mismatchColumnCounts.isEmpty()) {
            return "none identified (rows may be entirely absent from downloaded file)";
        }

        StringBuilder sb = new StringBuilder();
        mismatchColumnCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(entry -> {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(entry.getKey())
                            .append(" (").append(entry.getValue()).append("/").append(totalMissing).append(" rows)");
                });
        return sb.toString();
    }

    /**
     * DEBUG HELPER: finds the downloaded row that matches the uploaded row
     * on the most columns, logs a column-by-column diff, and returns the
     * list of column names that differed — used to build the aggregated
     * mismatch summary reported in ValidationResult.
     */
    private static List<String> diffAgainstClosestMatch(String[] uploadedRow,
                                                        List<String[]> downloadedDetailed,
                                                        List<String> compareColumns) {
        String[] bestMatch = null;
        int bestScore = -1;

        for (String[] downloadedRow : downloadedDetailed) {
            int score = 0;
            for (int i = 0; i < uploadedRow.length; i++) {
                if (uploadedRow[i].equals(downloadedRow[i])) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestMatch = downloadedRow;
            }
        }

        List<String> mismatchedColumns = new ArrayList<>();

        if (bestMatch == null) {
            logger.info("   No downloaded rows at all to compare against.");
            return mismatchedColumns;
        }

        logger.info("   Closest downloaded match ({}/{} columns matched):",
                bestScore, uploadedRow.length);

        for (int i = 0; i < compareColumns.size(); i++) {
            String uploadedVal = uploadedRow[i];
            String downloadedVal = bestMatch[i];
            boolean differs = !uploadedVal.equals(downloadedVal);

            String marker = differs ? "❌ DIFFERS" : "✅";
            logger.info("     {} | {} -> uploaded=[{}] downloaded=[{}]",
                    marker, compareColumns.get(i), uploadedVal, downloadedVal);

            if (differs) {
                mismatchedColumns.add(compareColumns.get(i));
            }
        }

        return mismatchedColumns;
    }

    private static Set<String> toKeySet(List<String[]> rows) {
        Set<String> keys = new HashSet<>();
        for (String[] row : rows) {
            keys.add(toKey(row));
        }
        return keys;
    }

    private static String toKey(String[] row) {
        StringBuilder sb = new StringBuilder();
        for (String val : row) {
            sb.append(val).append(" || ");
        }
        return sb.toString();
    }

    private static List<String[]> readRowsDetailed(File file,
                                                   List<String> compareColumns,
                                                   String label) throws Exception {
        List<String[]> rows = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(new FileInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(0);

            Map<String, Integer> columnIndex = new HashMap<>();
            for (Cell cell : header) {
                columnIndex.put(
                        normalizeText(formatter.formatCellValue(cell)),
                        cell.getColumnIndex());
            }

            logger.info("[{}] Header columns found: {}", label, columnIndex.keySet());

            int[] indexes = new int[compareColumns.size()];
            for (int i = 0; i < compareColumns.size(); i++) {
                Integer idx = columnIndex.get(normalizeText(compareColumns.get(i)));
                if (idx == null) {
                    logger.info("[{}] ⚠️ Requested column '{}' NOT FOUND in header row.",
                            label, compareColumns.get(i));
                    throw new RuntimeException("Column not found: " + compareColumns.get(i));
                }
                indexes[i] = idx;
            }

            int rowsRead = 0;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String[] values = new String[compareColumns.size()];
                boolean allBlank = true;

                for (int i = 0; i < indexes.length; i++) {
                    Cell cell = row.getCell(indexes[i]);
                    String value = cellToComparableString(cell, formatter);
                    values[i] = value;
                    if (!value.isBlank()) allBlank = false;

                    if (rowsRead < 3) {
                        String cellType = cell == null ? "NULL" : cell.getCellType().toString();
                        logger.info("[{}] Row {} | Column '{}' | Type={} | NormalizedValue={}",
                                label, r, compareColumns.get(i), cellType, value);
                    }
                }

                if (!allBlank) {
                    rows.add(values);
                }
                rowsRead++;
            }
        }
        return rows;
    }

    private static String cellToComparableString(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";

        if (cell.getCellType() == CellType.NUMERIC
                || (cell.getCellType() == CellType.FORMULA
                && cell.getCachedFormulaResultType() == CellType.NUMERIC)) {

            if (DateUtil.isCellDateFormatted(cell)) {
                return normalizeText(formatter.formatCellValue(cell));
            }
            return normalizeNumber(cell.getNumericCellValue());
        }

        String text = formatter.formatCellValue(cell);
        if (isNumeric(text.trim())) {
            return normalizeNumber(Double.parseDouble(text.trim()));
        }

        return normalizeText(text);
    }

    private static String normalizeText(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    private static boolean isNumeric(String text) {
        if (text == null || text.isBlank()) return false;
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String normalizeNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return new java.math.BigDecimal(String.valueOf(value))
                .stripTrailingZeros()
                .toPlainString();
    }
}