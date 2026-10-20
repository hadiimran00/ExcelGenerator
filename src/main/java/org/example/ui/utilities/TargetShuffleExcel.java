package org.example.ui.utilities;

import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Target Shuffling Excel: one row per product / category (PROD column), one "PJP Name~PJP Code" column per PJP
 * holding that PJP's % share of the product.
 * BD and PH also have a TARGET_VALUE column and a "Total" row (blank PROD) holding each PJP's target value:
 * PJP target value = sum over products of (share % x TARGET_VALUE / 100).
 * Shares are numeric cells in BD and text cells (e.g. "3.85") in PH; both are read, and writes keep the cell's type.
 */
public class TargetShuffleExcel {

    private static final String TARGET_VALUE = "TARGET_VALUE";

    private final List<String> headers = new ArrayList<>();
    private final Map<String, Map<String, Double>> sharesByProduct = new LinkedHashMap<>();
    private final Map<String, BigDecimal> targetValueByProduct = new LinkedHashMap<>();
    private final Map<String, BigDecimal> totalRowValues = new LinkedHashMap<>();

    private TargetShuffleExcel() {
    }

    public static TargetShuffleExcel read(File file) throws IOException {
        TargetShuffleExcel shuffle = new TargetShuffleExcel();
        DataFormatter formatter = new DataFormatter();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Cell cell : sheet.getRow(0)) {
                shuffle.headers.add(formatter.formatCellValue(cell).trim());
            }
            int prodColumn = shuffle.headers.indexOf("PROD");
            int targetValueColumn = shuffle.headers.indexOf(TARGET_VALUE);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, Double> shares = new LinkedHashMap<>();
                for (int c = 0; c < shuffle.headers.size(); c++) {
                    BigDecimal share = shuffle.headers.get(c).contains("~") ? numberIn(row.getCell(c)) : null;
                    if (share != null) {
                        shares.put(shuffle.headers.get(c), share.doubleValue());
                    }
                }
                String product = formatter.formatCellValue(row.getCell(prodColumn)).trim();
                // BD exports a "Total" row with no product code: it holds each PJP's target value
                if (product.isEmpty()) {
                    shares.forEach((pjp, value) -> shuffle.totalRowValues.put(pjp, BigDecimal.valueOf(value)));
                    continue;
                }
                shuffle.sharesByProduct.put(product, shares);
                BigDecimal targetValue = targetValueColumn >= 0 ? numberIn(row.getCell(targetValueColumn)) : null;
                if (targetValue != null) {
                    shuffle.targetValueByProduct.put(product, targetValue);
                }
            }
        }
        return shuffle;
    }

    public List<String> getProducts() {
        return new ArrayList<>(sharesByProduct.keySet());
    }

    public List<String> getPjpColumns() {
        List<String> pjpColumns = new ArrayList<>();
        for (String header : headers) {
            if (header.contains("~")) pjpColumns.add(header);
        }
        return pjpColumns;
    }

    public double getShare(String product, String pjpColumn) {
        return sharesByProduct.get(product).getOrDefault(pjpColumn, 0.0);
    }

    // Columns are "PJP Name~PJP Code"; match on the code
    public String getPjpColumn(String pjpCode) {
        for (String header : headers) {
            if (header.endsWith("~" + pjpCode)) {
                return header;
            }
        }
        throw new IllegalStateException("PJP [" + pjpCode + "] not found in shuffling file");
    }

    // The PJP with the largest share of the product, other than the excluded one
    public String getHighestSharePjpColumn(String product, String excludedColumn) {
        String highest = null;
        for (Map.Entry<String, Double> share : sharesByProduct.get(product).entrySet()) {
            if (!share.getKey().equals(excludedColumn) && (highest == null || share.getValue() > getShare(product, highest))) {
                highest = share.getKey();
            }
        }
        return highest;
    }

    public BigDecimal getTotalShare(String product) {
        BigDecimal total = BigDecimal.ZERO;
        for (double share : sharesByProduct.get(product).values()) {
            total = total.add(BigDecimal.valueOf(share));
        }
        return total;
    }

    public BigDecimal getTargetValue(String product) {
        return targetValueByProduct.getOrDefault(product, BigDecimal.ZERO);
    }

    /** DT target value: sum of every product's TARGET_VALUE. */
    public BigDecimal getTotalTargetValue() {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : targetValueByProduct.values()) {
            total = total.add(value);
        }
        return total;
    }

    /** Sum of every PJP's Total row value. */
    public BigDecimal getSumOfPjpTotals() {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : totalRowValues.values()) {
            total = total.add(value);
        }
        return total;
    }

    public boolean hasTotalRow() {
        return !totalRowValues.isEmpty() && !targetValueByProduct.isEmpty();
    }

    public BigDecimal getTotalRowValue(String pjpColumn) {
        return totalRowValues.getOrDefault(pjpColumn, BigDecimal.ZERO);
    }

    /** PJP target value = sum over products of (share % x product TARGET_VALUE / 100). */
    public BigDecimal calculatePjpTargetValue(String pjpColumn) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> target : targetValueByProduct.entrySet()) {
            BigDecimal share = BigDecimal.valueOf(getShare(target.getKey(), pjpColumn));
            total = total.add(share.multiply(target.getValue()).divide(BigDecimal.valueOf(100)));
        }
        return total.stripTrailingZeros();
    }

    /** Moves delta % of a product from one PJP column to another, writing the file in place. */
    public static void moveShare(File file, String product, String fromColumn, String toColumn, double delta) throws IOException {
        TargetShuffleExcel shuffle = read(file);
        int fromIndex = shuffle.headers.indexOf(fromColumn);
        int toIndex = shuffle.headers.indexOf(toColumn);
        BigDecimal amount = BigDecimal.valueOf(delta);

        DataFormatter formatter = new DataFormatter();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            int prodColumn = shuffle.headers.indexOf("PROD");

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || !product.equals(formatter.formatCellValue(row.getCell(prodColumn)).trim())) continue;
                Cell fromCell = cellAt(row, fromIndex);
                Cell toCell = cellAt(row, toIndex);
                writeNumber(fromCell, valueOrZero(fromCell).subtract(amount));
                writeNumber(toCell, valueOrZero(toCell).add(amount));
                break;
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }

    /** Rewrites the Total row (blank PROD) so each PJP holds its calculated target value. No-op when the file has none. */
    public static void recalculateTotalRow(File file) throws IOException {
        TargetShuffleExcel shuffle = read(file);
        if (!shuffle.hasTotalRow()) return;

        DataFormatter formatter = new DataFormatter();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            int prodColumn = shuffle.headers.indexOf("PROD");

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || !formatter.formatCellValue(row.getCell(prodColumn)).trim().isEmpty()) continue;
                for (int c = 0; c < shuffle.headers.size(); c++) {
                    if (shuffle.headers.get(c).contains("~")) {
                        writeNumber(cellAt(row, c), shuffle.calculatePjpTargetValue(shuffle.headers.get(c)));
                    }
                }
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }

    // A number in a numeric cell or in a text cell such as "3.85"; null when the cell holds no number
    private static BigDecimal numberIn(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(cell.getNumericCellValue());
        if (cell.getCellType() == CellType.STRING) {
            String text = cell.getStringCellValue().trim();
            try {
                return text.isEmpty() ? null : new BigDecimal(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal valueOrZero(Cell cell) {
        BigDecimal value = numberIn(cell);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Cell cellAt(Row row, int column) {
        return row.getCell(column) == null ? row.createCell(column) : row.getCell(column);
    }

    // Keeps the cell's type: text cells stay text (PH), numeric cells stay numeric (BD)
    private static void writeNumber(Cell cell, BigDecimal value) {
        BigDecimal plain = value.stripTrailingZeros();
        if (cell.getCellType() == CellType.STRING) {
            cell.setCellValue(plain.scale() < 0 ? plain.setScale(0).toPlainString() : plain.toPlainString());
        } else {
            cell.setCellValue(plain.doubleValue());
        }
    }
}
