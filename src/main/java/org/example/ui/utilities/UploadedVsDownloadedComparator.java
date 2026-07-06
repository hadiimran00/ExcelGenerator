package org.example.ui.utilities;

import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class UploadedVsDownloadedComparator {

    public static void compare(File uploadedFile,
                               File downloadedFile,
                               List<String> compareColumns,
                               ValidationResult result) throws Exception {



        Set<String> downloadedRows =
                readRows(downloadedFile, compareColumns);


        Set<String> uploadedRows =
                readRows(uploadedFile, compareColumns);
        int matched = 0;
        int missing = 0;
        String firstMissing = null;

        for (String row : uploadedRows) {

            if (downloadedRows.contains(row)) {
                matched++;
            } else {
                missing++;

                if (firstMissing == null) {
                    firstMissing = row;
                }
            }
        }

        if (missing == 0) {
            result.pass(String.format(
                    "All uploaded records verified. Uploaded=%d Matched=%d",
                    uploadedRows.size(),
                    matched));
        } else {
            result.fail(String.format(
                    "%d uploaded record(s) not found. Example: %s",
                    missing,
                    firstMissing));
        }
    }

    private static Set<String> readRows(File file,
                                        List<String> compareColumns) throws Exception {

        Set<String> rows = new HashSet<>();

        try (Workbook workbook =
                     WorkbookFactory.create(new FileInputStream(file))) {

            Sheet sheet = workbook.getSheetAt(0);

            DataFormatter formatter = new DataFormatter();

            Row header = sheet.getRow(0);

            Map<String,Integer> columnIndex = new HashMap<>();

            for (Cell cell : header) {
                columnIndex.put(
                        formatter.formatCellValue(cell).trim(),
                        cell.getColumnIndex());
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {

                Row row = sheet.getRow(r);

                if (row == null)
                    continue;

                StringBuilder key = new StringBuilder();

                for (String column : compareColumns) {

                    Integer index = columnIndex.get(column);

                    if (index == null) {
                        System.out.println(index+column);
                        throw new RuntimeException(
                                "Column not found: " + column);

                    }


                    Cell cell = row.getCell(index);

                    key.append(
                                    cell == null
                                            ? ""
                                            : formatter.formatCellValue(cell).trim())
                            .append(" || ");
                }

                String record = key.toString();

                if (!record.replace("|","").isBlank()) {
                    rows.add(record);
                }
            }
        }

        return rows;
    }
}