package org.example.ui;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

public class ExcelGen {

    public static String generateExcel(String templatePath,  Map<String, Map<String, Object>> columnRules) {
        try {
            System.out.println(columnRules);
           // ObjectMapper mapper = new ObjectMapper();

//            // Convert JsonNode → Map
//            Map<String, Map<String, Object>> columnRules =
//                    mapper.convertValue(rules, new TypeReference<>() {
//                    });

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
                            String prefix = (String) ruleConfig.getOrDefault("prefix", "");
                           String uuidPart = UUID.randomUUID().toString().replaceAll("-", "").substring(0, length);
                            cell.setCellValue(prefix + uuidPart.toUpperCase());
                            break;
                        case "PHONE":
                            int totalLength = (int) ruleConfig.getOrDefault("length", 11); // full phone length
                            int suffixLength = totalLength - 2; // subtract '03' prefix
                            String ts = String.valueOf(System.nanoTime());
                            String lastDigits = ts.substring(ts.length() - suffixLength);
                            cell.setCellValue("03" + lastDigits);
                            break;
                        case "EMAIL":
                            cell.setCellValue("test" + System.currentTimeMillis() + "@mail.com");
                            break;
                        case "PLATE":
                            cell.setCellValue("ABC-" + (1000 + random.nextInt(9000)));
                            break;
                        case "NUM":
                            int num = (int) ruleConfig.getOrDefault("length", 5);
                            String millisStr = String.valueOf(System.currentTimeMillis());
                            String trimmed = millisStr.substring(millisStr.length() - num);
                            double number = Double.parseDouble(trimmed); // ✅ Convert to actual number
                            cell.setCellValue(number); // ✅ Sets as numeric cell
                            break;
                        case "DATE":
                            String formattedDate = java.time.LocalDate.now().toString(); // "yyyy-MM-dd"
                            cell.setCellValue(formattedDate); // stored as string
                            break;
                        case "COORDINATES":
                            double cord = 24 + random.nextDouble();
                            cell.setCellValue(cord);
                            break;
                        case "NIC":
                            String nanoTimeStr = String.valueOf(System.nanoTime());

                            // Use the last 13 digits for the required format (5 + 7 + 1 = 13)
                            int requiredLength = 13;
                            int start = Math.max(0, nanoTimeStr.length() - requiredLength);
                            String last13Digits = nanoTimeStr.substring(start);

                            // Ensure we have exactly 13 digits (pad with '0' if nanoTime was short, though unlikely)
                            while (last13Digits.length() < requiredLength) {
                                last13Digits = "0" + last13Digits;
                            }

                            // Apply the format XXXXX-XXXXXXX-X
                            String part1 = last13Digits.substring(0, 5);    // First 5 digits
                            String part2 = last13Digits.substring(5, 12);   // Next 7 digits
                            String part3 = last13Digits.substring(12, 13);  // Last 1 digit

                            cell.setCellValue(part1 + "-" + part2 + "-" + part3);
                            break;

                        default:
                            cell.setCellValue(type); // literal value if needed
                    }
                }
            }

            // Write updated Excel
            FileOutputStream fos = new FileOutputStream(templatePath);
            workbook.write(fos);
            fos.close();
            workbook.close();
            fis.close();
            if (!templatePath.endsWith(".xlsx")) {
                templatePath = templatePath + ".xlsx";
            }

            return templatePath;

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to generate Excel: " + e.getMessage());
            return null;
        }
    }
}
