package org.example.ui.utilities;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Row edits on the Excel files the incentive screens download (reward, approval, target, reward generation).
 * Columns are found by header text; codes stay text so leading zeros survive.
 */
public class IncentiveExcel {

    private static final DataFormatter FORMATTER = new DataFormatter();

    public static Map<String, Integer> headerColumns(Sheet sheet, Iterable<String> headers) {
        Map<String, Integer> found = new HashMap<>();
        for (Cell cell : sheet.getRow(0)) {
            found.put(FORMATTER.formatCellValue(cell).trim(), cell.getColumnIndex());
        }
        Map<String, Integer> columns = new HashMap<>();
        for (String header : headers) {
            Integer index = found.get(header);
            if (index == null) {
                throw new IllegalStateException("Column '" + header + "' not found; headers are " + found.keySet());
            }
            columns.put(header, index);
        }
        return columns;
    }

    /** First data row whose cells equal every {header: value} in {@code match}, or -1. */
    public static int findRow(Sheet sheet, Map<String, String> match) {
        Map<String, Integer> columns = headerColumns(sheet, match.keySet());
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && match.entrySet().stream()
                    .allMatch(m -> m.getValue().equals(cellText(row, columns.get(m.getKey()))))) {
                return r;
            }
        }
        return -1;
    }

    /** Removes every other data row and moves {@code keepRow} to row 1 (-1 removes all data rows). */
    public static void keepOnlyRow(Sheet sheet, int keepRow) {
        for (int r = sheet.getLastRowNum(); r >= 1; r--) {
            Row row = sheet.getRow(r);
            if (row != null && r != keepRow) {
                sheet.removeRow(row);
            }
        }
        if (keepRow > 1) {
            sheet.shiftRows(keepRow, keepRow, 1 - keepRow);
        }
    }

    public static String cellText(Row row, int col) {
        return FORMATTER.formatCellValue(row.getCell(col)).trim();
    }

    // The stored number, not the displayed text (a "#,##0" format would round 307.32 to 307)
    public static BigDecimal cellNumber(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String digits = cellText(row, col).replaceAll("[^0-9.\\-]", "");
        return digits.isEmpty() || "-".equals(digits) || ".".equals(digits) ? BigDecimal.ZERO : new BigDecimal(digits);
    }

    // Numbers are written as numbers; everything else as text
    public static void setCell(Row row, int col, Object value) {
        // App downloads use inline strings; POI writes a new value beside the old one instead of replacing it,
        // so the cell is recreated (same style) rather than updated in place
        Cell old = row.getCell(col);
        CellStyle style = old == null ? null : old.getCellStyle();
        if (old != null) {
            row.removeCell(old);
        }
        Cell cell = row.createCell(col);
        if (style != null) {
            cell.setCellStyle(style);
        }
        if (value instanceof BigDecimal number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    public static void save(Workbook workbook, File target) throws IOException {
        try (FileOutputStream out = new FileOutputStream(target)) {
            workbook.write(out);
        }
    }
}
