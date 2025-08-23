package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

public class ExcelGen {

    public static String generateExcel(String templatePath, String outputPath, String rulesJsonPath) {
        try {
            // Read JSON rules
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Map<String, Object>> columnRules = mapper.readValue(new File(rulesJsonPath), Map.class);

            // Open Excel template
            FileInputStream fis = new FileInputStream(templatePath);
            Workbook workbook = new XSSFWorkbook(fis);
            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new RuntimeException("Header row is missing in Excel");

            Random random = new Random();

            // Iterate over data rows
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue; // skip empty rows

                for (Cell headerCell : headerRow) {
                    String columnName = headerCell.getStringCellValue();

                    if (!columnRules.containsKey(columnName)) continue; // only modify unique columns

                    Map<String, Object> ruleConfig = columnRules.get(columnName);
                    String type = (String) ruleConfig.get("type");

                    // Ensure the cell exists
                    int colIndex = headerCell.getColumnIndex();
                    Cell cell = row.getCell(colIndex);
                    if (cell == null) {
                        cell = row.createCell(colIndex);
                    }

                    // Set value based on type
                    switch (type) {
                        case "UUID":
                            int length = (int) ruleConfig.getOrDefault("length", 8);
                            String prefix = (String) ruleConfig.getOrDefault("prefix", "AUTO");
                            String uuidPart = UUID.randomUUID().toString().replaceAll("-", "").substring(0, length);
                            cell.setCellValue(prefix + uuidPart.toUpperCase());
                            System.out.println(prefix + uuidPart.toUpperCase());
                            break;
                        case "PHONE":
                            int totalLength = (int) ruleConfig.getOrDefault("length", 11); // full phone length
                            int suffixLength = totalLength - 2; // subtract '03' prefix
                            String ts = String.valueOf(System.currentTimeMillis());
                            String lastDigits = ts.substring(ts.length() - suffixLength);
                            cell.setCellValue("03" + lastDigits);
                            break;
                        case "EMAIL":
                            cell.setCellValue("test" + System.currentTimeMillis() + "@mail.com");
                            break;
                        case "PLATE":
                            cell.setCellValue("ABC-" + (1000 + random.nextInt(9000)));
                            break;
                        case "COORDINATES":
                            double lat = 24 + random.nextDouble();
                            double lon = 67 + random.nextDouble();
                            cell.setCellValue(lat + "," + lon);
                            break;
                        default:
                            cell.setCellValue(type); // literal value if needed
                    }
                }
            }

            // Write updated Excel
            FileOutputStream fos = new FileOutputStream(outputPath);
            workbook.write(fos);
            fos.close();
            workbook.close();
            fis.close();
            if (!outputPath.endsWith(".xlsx")) {
                outputPath = outputPath + ".xlsx";
            }

            return outputPath;

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to generate Excel: " + e.getMessage());
            return null;
        }
    }
}
