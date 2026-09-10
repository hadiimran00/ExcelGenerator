package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class StockReconciliationExcel {
    private static final Logger logger = LoggerUtil.getLogger(StockReconciliationExcel.class);

    /**
     * Reads the target Excel file, finds the specified SKU and Qty columns,
     * applies the adjustment (+/-) ONLY to the matching targetSku, and overwrites the file.
     */
    public static void updateQuantity(File downloadedFile, String skuColumn, String qtyColumn, double adjustment, String targetSku) {
        logger.info("📄 Reading Excel file for modification: {}", downloadedFile.getName());

        try (FileInputStream fis = new FileInputStream(downloadedFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new RuntimeException("Excel sheet is empty! No header row found.");
            }

            int skuColIdx = -1;
            int qtyColIdx = -1;

            // Dynamically search for column headers
            for (Cell cell : headerRow) {
                String headerVal = cell.getStringCellValue().trim();
                if (headerVal.equalsIgnoreCase(skuColumn)) {
                    skuColIdx = cell.getColumnIndex();
                } else if (headerVal.equalsIgnoreCase(qtyColumn)) {
                    qtyColIdx = cell.getColumnIndex();
                }
            }

            if (skuColIdx == -1 || qtyColIdx == -1) {
                throw new RuntimeException("Could not map columns. SKU Col Index: " + skuColIdx + ", Qty Col Index: " + qtyColIdx);
            }

            boolean skuFound = false;

            // Iterate through rows (skipping header) to update quantities
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell skuCell = row.getCell(skuColIdx);
                Cell qtyCell = row.getCell(qtyColIdx);

                if (skuCell != null && qtyCell != null) {
                    // Read current SKU string safely regardless of cell type (Numeric/String)
                    String currentSku = "";
                    if (skuCell.getCellType() == CellType.STRING) {
                        currentSku = skuCell.getStringCellValue().trim();
                    } else if (skuCell.getCellType() == CellType.NUMERIC) {
                        currentSku = String.valueOf((long) skuCell.getNumericCellValue());
                    }

                    // CHECK IF THIS ROW MATCHES THE TARGET SKU
                    if (currentSku.equals(targetSku.trim())) {
                        skuFound = true;
                        double currentQty = 0;
                        if (qtyCell.getCellType() == CellType.NUMERIC) {
                            currentQty = qtyCell.getNumericCellValue();
                        } else if (qtyCell.getCellType() == CellType.STRING) {
                            currentQty = Double.parseDouble(qtyCell.getStringCellValue().trim());
                        }

                        double newQty = currentQty + adjustment;

                        // Preserve original cell formatting type
                        if (qtyCell.getCellType() == CellType.STRING) {
                            qtyCell.setCellValue(String.valueOf((int) newQty));
                        } else {
                            qtyCell.setCellValue(newQty);
                        }

                        logger.info("✏️ SKU: [{}] | Original Qty: [{}] -> Updated Qty: [{}]",
                                currentSku, currentQty, newQty);

                        // Target found and updated, break out of the loop if you only expect 1 match
                        break;
                    }
                }
            }

            if (!skuFound) {
                logger.warn("⚠️ Target SKU [{}] was not found in the Excel file.", targetSku);
            }

            // Force formula recalculation on save if formulas are used
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateAll();

            // Overwrite original file
            try (FileOutputStream fos = new FileOutputStream(downloadedFile)) {
                workbook.write(fos);
            }
            logger.info("💾 Excel file modified and saved successfully.");

        } catch (Exception e) {
            logger.error("❌ Failed to modify Stock Reconciliation Excel file", e);
            throw new RuntimeException("Excel adjustment execution failed: " + e.getMessage(), e);
        }
    }
}