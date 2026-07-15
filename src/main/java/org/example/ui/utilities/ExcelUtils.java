package org.example.ui.utilities;

import org.apache.poi.ss.usermodel.*;

import java.io.*;

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
}