package org.example.ui.utilities;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDate;
import java.util.*;

public class ExcelGen {

    private static final Logger logger = LoggerFactory.getLogger(ExcelGen.class);

    public static String generateExcel(String templatePath,
                                       Map<String, Map<String, Object>> columnRules,
                                       String testId) {
        try {
            logger.info("Column Rules: {}", columnRules);

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

                    String generated = null;
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
                           generated = prefix + uuidPart.toUpperCase();
                            cell.setCellValue(generated);
                            GeneratedDataStore.store(testId, columnName, generated);
                            break;
                        case "PHONE":
                            int totalLength = (int) ruleConfig.getOrDefault("length", 11); // full phone length
                            int suffixLength = totalLength - 2; // subtract '03' prefix
                            String ts = String.valueOf(System.nanoTime());
                            String lastDigits = ts.substring(ts.length() - suffixLength);
                            generated = "03" + lastDigits;
                            cell.setCellValue(generated);
                            GeneratedDataStore.store(testId, columnName, generated);
                            break;
                        case "EMAIL":
                            generated = "test" + System.currentTimeMillis() + "@mail.com";
                            cell.setCellValue(generated);
                            GeneratedDataStore.store(testId, columnName, generated);
                            break;
                        case "PLATE":
                            generated = "ABC-" + (1000 + random.nextInt(9000));
                            cell.setCellValue(generated);
                            GeneratedDataStore.store(testId, columnName, generated);
                            break;
                        case "NUM":
                            int num = (int) ruleConfig.getOrDefault("length", 5);
                            StringBuilder randomNum = new StringBuilder();
                            Random rand = new Random();
                            // first digit: 1–9 (avoid leading zero)
                            randomNum.append(rand.nextInt(9) + 1);
                            // remaining digits: 0–9
                            for (int j = 1; j < num; j++) {
                                randomNum.append(rand.nextInt(10));
                            }
                            // if length <= 15, safe to store as number, else store as text
                            if (num <= 15) {
                                generated = String.valueOf(Double.parseDouble(randomNum.toString()));
                                cell.setCellValue(generated);
                                GeneratedDataStore.store(testId, columnName, generated);
                            } else {
                                generated=randomNum.toString();
                                cell.setCellValue(generated);
                                GeneratedDataStore.store(testId, columnName, generated);// store as text for long numbers
                            }
                            break;

                        case "DATE":
                            int num2 = (int) ruleConfig.getOrDefault("length", 0);
                            LocalDate date = LocalDate.now().plusDays(num2);
                            String formattedDate = date.toString(); // yyyy-MM-dd
                            generated = formattedDate;
                            cell.setCellValue(generated);
                            GeneratedDataStore.store(testId, columnName, generated);
                            break;
                        case "COORDINATES":
                            double cord = 24 + random.nextDouble();
                            cell.setCellValue(cord);
                            generated = String.valueOf(cord);
                            GeneratedDataStore.store(testId, columnName,generated);
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

                            generated = part1 + "-" + part2 + "-" + part3;
                            cell.setCellValue(generated);
                            GeneratedDataStore.store(testId, columnName, generated);

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
            logger.error("Failed to generate Excel: {}", e.getMessage(), e);
            return null;
        }
    }
}
