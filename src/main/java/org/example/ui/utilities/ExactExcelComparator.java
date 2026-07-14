package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;

public class ExactExcelComparator {

    private static final Logger logger =
            LoggerUtil.getLogger(ExactExcelComparator.class);

    public static void compare(File uploaded,
                               File downloaded,
                               ValidationResult result) {

        try (
                Workbook uploadedWb = WorkbookFactory.create(new FileInputStream(uploaded));
                Workbook downloadedWb = WorkbookFactory.create(new FileInputStream(downloaded))
        ) {

            Sheet uploadedSheet = uploadedWb.getSheetAt(0);
            Sheet downloadedSheet = downloadedWb.getSheetAt(0);

            DataFormatter formatter = new DataFormatter();

            int uploadedRows = uploadedSheet.getLastRowNum();
            int downloadedRows = downloadedSheet.getLastRowNum();

            if (uploadedRows != downloadedRows) {
                result.fail(String.format(
                        "Values mismatch. Uploaded=%d Downloaded=%d",
                        uploadedRows,
                        downloadedRows));
                return;
            }

            int matchedRows = 0;

            // Skip header row (row 0)
            for (int r = 1; r <= uploadedRows; r++) {

                Row uploadedRow = uploadedSheet.getRow(r);
                Row downloadedRow = downloadedSheet.getRow(r);

                int uploadedCells = uploadedRow == null ? 0 : uploadedRow.getLastCellNum();
                int downloadedCells = downloadedRow == null ? 0 : downloadedRow.getLastCellNum();

                if (uploadedCells != downloadedCells) {
                    result.fail(String.format(
                            "Values mismatch at row %d. Uploaded=%d Downloaded=%d",
                            r + 1,
                            uploadedCells,
                            downloadedCells));
                    return;
                }

                for (int c = 0; c < uploadedCells; c++) {

                    String uploadedValue = normalize(
                            uploadedRow == null ? null : uploadedRow.getCell(c),
                            formatter);

                    String downloadedValue = normalize(
                            downloadedRow == null ? null : downloadedRow.getCell(c),
                            formatter);

                    if (!uploadedValue.equals(downloadedValue)) {

                        String columnName = "";

                        Row header = uploadedSheet.getRow(0);
                        if (header != null && header.getCell(c) != null) {
                            columnName = formatter.formatCellValue(header.getCell(c));
                        }

                        logger.info(
                                "❌ Mismatch -> Row={} Column={} Uploaded={} Downloaded={}",
                                r + 1,
                                columnName,
                                uploadedValue,
                                downloadedValue);

                        result.fail(String.format(
                                "Mismatch at Row=%d Column='%s' Uploaded='%s' Downloaded='%s'",
                                r + 1,
                                columnName,
                                uploadedValue,
                                downloadedValue));

                        return;
                    }
                }

                matchedRows++;
            }

            int uploadedRecords = uploadedSheet.getLastRowNum();
            int downloadedRecords = downloadedSheet.getLastRowNum();

            logger.info(
                    "✅ Exact compare passed. Uploaded='{}' Downloaded='{}' Records Compared={}",
                    uploaded.getName(),
                    downloaded.getName(),
                    matchedRows);

            result.pass(
                    "Uploaded Records=" + uploadedRecords +
                            ", Downloaded Records=" + downloadedRecords +
                            ", All Values Matched Successfully."
            );

        } catch (Exception e) {
            logger.error("Exact compare failed", e);
            result.fail("Exact compare failed: " + e.getMessage());
        }
    }

    private static String normalize(Cell cell, DataFormatter formatter) {

        if (cell == null) {
            return "";
        }

        String value = formatter.formatCellValue(cell);

        return value
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase();
    }
}