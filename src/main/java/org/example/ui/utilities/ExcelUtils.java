package org.example.ui.utilities;

import org.apache.poi.ss.usermodel.*;

import java.io.*;

//import static org.example.ui.utilities.PostUploadValidator.logger;

public class ExcelUtils {

    public static boolean csvContainsValue(
            File csvFile,
            String columnName,
            String expectedValue) throws Exception {

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {

            // Read header
            String headerLine = reader.readLine();

            if (headerLine == null) {
                throw new RuntimeException("CSV is empty.");
            }

            String[] headers = headerLine.split(",", -1);

            int columnIndex = -1;

            for (int i = 0; i < headers.length; i++) {
                if (headers[i].trim().equalsIgnoreCase(columnName.trim())) {
                    columnIndex = i;
                    break;
                }
            }

            if (columnIndex == -1) {
                throw new RuntimeException("File / Column not found: " + columnName + " File Name : " + csvFile.getName());
            }

            String line;

            while ((line = reader.readLine()) != null) {

                String[] values = line.split(",", -1);

                if (columnIndex >= values.length) {
                    continue;
                }

                if (values[columnIndex].trim().equalsIgnoreCase(expectedValue.trim())) {
                    return true;
                }
            }

            return false;
        }
    }
    static void updateColumnValue(String filePath, String columnName, String newValue) throws Exception {
        FileInputStream fis = new FileInputStream(filePath);
        Workbook workbook = WorkbookFactory.create(fis);
        fis.close();

        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(0);

        if (headerRow == null) {
            workbook.close();
            throw new RuntimeException("Header row missing in Excel: " + filePath);
        }

        int columnIndex = -1;
        for (Cell cell : headerRow) {
            if (columnName.equalsIgnoreCase(cell.getStringCellValue().trim())) {
                columnIndex = cell.getColumnIndex();
                break;
            }
        }

        if (columnIndex == -1) {
            workbook.close();
            throw new RuntimeException("Column '" + columnName + "' not found in Excel: " + filePath);
        }

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Cell cell = row.getCell(columnIndex);
            if (cell == null) {
                cell = row.createCell(columnIndex);
            }
            cell.setCellValue(newValue);
        }

        FileOutputStream fos = new FileOutputStream(filePath);
        workbook.write(fos);
        fos.close();
        workbook.close();
    }

    /**
     * Reads an Excel file, finds the target row based on a matching SKU value,
     * and extracts the double quantity value from the designated column header.
     *
     * @param downloadedFile The Excel file to parse.
     * @param skuColumn      The header name of the SKU column (e.g., "SKU Code").
     * @param qtyColumn      The header name of the Quantity column (e.g., "Physical Qty").
     * @param targetSku      The target SKU string value you are searching for.
     * @return The double value of the quantity column for that SKU.
     */
    public static double getCellValueByHeader(File downloadedFile, String skuColumn, String qtyColumn, String targetSku) {
        DataFormatter formatter = new DataFormatter();

        try (FileInputStream fis = new FileInputStream(downloadedFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            // Access the first data sheet
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new RuntimeException("Excel parsing failed: The sheet header row (row 0) is empty.");
            }

            int skuColIndex = -1;
            int qtyColIndex = -1;

            // Step 1: Map header names to column indexes
            for (Cell cell : headerRow) {
                String headerValue = formatter.formatCellValue(cell).trim();
                if (headerValue.equalsIgnoreCase(skuColumn)) {
                    skuColIndex = cell.getColumnIndex();
                } else if (headerValue.equalsIgnoreCase(qtyColumn)) {
                    qtyColIndex = cell.getColumnIndex();
                }
            }

            // Verify both target columns were located safely
            if (skuColIndex == -1 || qtyColIndex == -1) {
                throw new RuntimeException(String.format(
                        "Excel Header Error -> Missing target columns. Found '%s': %b | Found '%s': %b",
                        skuColumn, (skuColIndex != -1), qtyColumn, (qtyColIndex != -1)
                ));
            }

            // Step 2: Iterate through data rows to find matching SKU row
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row currentRow = sheet.getRow(rowIndex);
                if (currentRow == null) {
                    continue;
                }

                Cell skuCell = currentRow.getCell(skuColIndex);
                String currentSkuValue = formatter.formatCellValue(skuCell).trim();

                // Check if current row matches the SKU target
                if (currentSkuValue.equalsIgnoreCase(targetSku)) {
                    Cell qtyCell = currentRow.getCell(qtyColIndex);
                    if (qtyCell == null) {
                       // logger.info("⚠️ Targeted quantity cell is empty at row index {}", rowIndex);
                        return 0.0;
                    }

                    // Extract and cast value cleanly depending on cell data type
                    if (qtyCell.getCellType() == CellType.NUMERIC) {
                        return qtyCell.getNumericCellValue();
                    } else if (qtyCell.getCellType() == CellType.FORMULA) {
                        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                        CellValue cellValue = evaluator.evaluate(qtyCell);
                        return cellValue.getNumberValue();
                    } else {
                        // Safe fallback parse for String typed numbers
                        String rawQty = formatter.formatCellValue(qtyCell).trim();
                        return rawQty.isEmpty() ? 0.0 : Double.parseDouble(rawQty);
                    }
                }
            }

            throw new RuntimeException("Data Mapping Error: The targeted SKU '" + targetSku + "' was not found inside the active sheet rows.");

        } catch (IOException e) {
            throw new RuntimeException("Failed to safely read stock workbook stream data: " + e.getMessage(), e);
        }
    }
}