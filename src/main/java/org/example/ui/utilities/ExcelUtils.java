package org.example.ui.utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

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
}