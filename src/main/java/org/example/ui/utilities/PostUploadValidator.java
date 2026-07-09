package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.*;

public class PostUploadValidator {

    private static final Logger logger = LoggerUtil.getLogger(PostUploadValidator.class);

    /**
     * Called after upload completes.
     * Reads validateMode from screen config and runs the appropriate checks.
     * validateMode options:
     * DOWNLOAD_COMPARE  — download again, compare generated values vs downloaded Excel
     * UI_SEARCH         — navigate to grid screen, search for generated value
     * BOTH              — do both
     * (blank)           — skip validation entirely
     */

    public static ValidationResult run(WebDriver driver,
                                       Map<String, Object> validationsSheet,
                                       String testId,
                                       String uploadedFilePath,
                                       String downloadDir, String validateMode, Map<String, String> scenarioData) {
       System.out.println(validationsSheet);
        if ( validationsSheet == null || validateMode.isBlank() || validationsSheet.isEmpty()) {
            logger.info("⏩ No validation configuration found. Skipping validation.");
            return null;
        }
//        if (validateMode.isBlank()) {
//            logger.info("No validation configured for: {}", screenName);
//            return null; // nothing to do
//        }
        String screenName   = str(validationsSheet, "screenName");
        String columnId = str(validationsSheet, "columnId");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

        ValidationResult result = new ValidationResult(screenName);
        if (validationsSheet.isEmpty()) {
            logger.info("⏩ No validation configuration found. Skipping validation.");
            return null;
        }
        if (validateMode.isBlank()) {
            logger.info("No validation configured for: {}", screenName);
            return null; // nothing to do
        }

        logger.info("──────────────────────────────────────────");
        logger.info("🔎 Post-upload validation [{}] mode={}", screenName, validateMode);

        List<String> validateCols = splitCsv(str(validationsSheet, "validateCols"));
        validateMode = validateMode == null
                ? ""
                : validateMode.trim().toUpperCase();
        // ── VALIDATION MODE SWITCH ────────────────────────────────────────────
        switch (validateMode) {
            case "DOWNLOAD_COMPARE": {
                try {
                    // Trigger download (reuse existing params)
                    @SuppressWarnings("unchecked")
                    Map<String, String> params = (Map<String, String>) validationsSheet.getOrDefault("params", Map.of());
                    FileManager.downloadExcel(driver, screenName + "_redownload", params);

                    File downloaded = latestFile(downloadDir);
                    DownloadComparator.compareGeneratedVsDownloaded(
                            downloaded, testId, validateCols, result);
                } catch (Exception e) {
                    result.fail("Download-compare step failed: " + e.getMessage());
                    logger.info("Download-compare error", e);
                }
                break;
            }

            case "UI_SEARCH": {
                runUISearchValidation(driver, validationsSheet, testId, result);
                break;
            }
            case "LMT": {
                validateCashmemo.validateCashmemoForLMT(driver, wait, result);
                break;
            }
            case "PROD_PRICE": { //For price master excel
                ProdPriceExcelValidation.validate(
                        driver,
                        testId,validationsSheet,
                        scenarioData,
                        result
                );
                break;
            }
                case "PROD_ENRICH": { // for product enrichment
                    ProdEnrichValidation.validate(
                            driver,
                            testId, validationsSheet,
                            scenarioData,
                            result
                    );
                    break;
                }
                    case "DIST_PROFILE":{ // for DIST EXCEL
                        DistProfileValidation.validate(
                                driver,
                                testId,validationsSheet,
                                scenarioData,
                                result
                        );
                        break;

                }
                case "VALID_EXCEL":{ // for Validation EXCEL
                ValidationExcelValidation.validate(
                        driver,
                        testId,validationsSheet,
                        scenarioData,
                        result
                );
                break;


            }
                case "COMPARE": { // for Validation EXCEL
                    try {

                        File downloaded = latestFile(downloadDir);
                        if (downloaded == null) {
                            result.fail("Downloaded file not found.");
                            break;
                        }
                        String compareColumns = scenarioData.get("CompareColumns");
                        List<String> columns = Arrays.stream(compareColumns.split(","))
                                .map(String::trim)
                                .toList();
                        UploadedVsDownloadedComparator.compare(
                                new File(uploadedFilePath),
                                downloaded,
                                columns,
                                result
                        );

                    } catch (Exception e) {
                        result.fail(e.getMessage());
                    }

                    break;

            }
//            case "PJP_EXCEL":
//
//                createDSR(driver, scenarioData);
//
//                createSellingCategory(driver, scenarioData);
//
//                FileManager.downloadExcel(driver, "Header", params);
//                FileManager.uploadFile(driver, headerExcel);
//                PostUploadValidator.run(...);
//
//                FileManager.downloadExcel(driver, "Detail", params);
//                FileManager.uploadFile(driver, detailExcel);
//                PostUploadValidator.run(...);
//
//                break;
            case "SELLCAT" :{
                SellCatBulkUploadValidation.validate(
                        driver,
                        testId,validationsSheet,
                        scenarioData,
                        result
                );
                break;
            }

            default:
                logger.info("Invalid validation mode: {}", validateMode);
                result.fail("Invalid validation mode");
                break;
        }

        result.logSummary(logger);
    //    TestSummary.recordValidationResult(result);

        return result;
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    // 3. Updated method signature to accept menuSearchText instead of screenName
    private static void navigateToMenuScreen(WebDriver driver,
                                             String menuSearchText,
                                             String menuItemId) {
        try {
            // Open menu search box
            var wait = new WebDriverWait(
                    driver, Duration.ofSeconds(10));

            // Click hamburger if menu is collapsed
            try {
                wait.until(ExpectedConditions
                        .elementToBeClickable(By.id("menurollin"))).click();
            } catch (NoSuchElementException e) {
                driver.findElement(
                        By.cssSelector("input[placeholder='Search Here']"));
            }

            // Type menu search text in menu search box
            var searchBox = wait.until(ExpectedConditions
                    .elementToBeClickable(By
                            .cssSelector("input[placeholder='Search Here']")));
            searchBox.clear();

            // 4. Send the new search text parameter
            searchBox.sendKeys(menuSearchText);
            Thread.sleep(400);

            // Click the menu item
            wait.until(ExpectedConditions
                    .elementToBeClickable(By.id(menuItemId))).click();

            LoaderWait.waitForLoaderToDisappear(driver);

        } catch (Exception e) {
            logger.info("Could not navigate to menu item: {}", menuItemId);
        }
    }

    private static File latestFile(String dir) {
        File folder = new File(dir);
        File[] files = folder.listFiles((d, n) ->
                n.toLowerCase().endsWith(".xlsx") || n.toLowerCase().endsWith(".csv"));
        if (files == null || files.length == 0) return null;

        return Arrays.stream(files)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
    }

    public static String str(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? "" : val.toString().trim();
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.asList(raw.split(","));
    }

    private static void runUISearchValidation(
            WebDriver driver,
            Map<String, Object> validationsSheet,
            String testId,
            ValidationResult result) {

        String menuItemId = str(validationsSheet, "validateScreenId");
        String validateGeneratedCol = str(validationsSheet, "validateGeneratedCol");
        String validateGridCol = str(validationsSheet, "validateGridCol");
        String menuSearch = str(validationsSheet, "menuSearch");
        String screenName = str(validationsSheet, "screenName");
        String columnId = str(validationsSheet ,"columnId");
        String searchValue =
                GeneratedDataStore.get(testId, validateGeneratedCol);

        logger.info("Generated value for [{}][{}] = {}", testId, validateGeneratedCol, searchValue);

        if (menuItemId.isBlank()) {
            result.fail("validateScreenId is blank — cannot navigate for UI search");
            return;
        }

        if (validateGeneratedCol.isBlank()) {
            result.fail("validateGeneratedCol is blank");
            return;
        }

        if (searchValue == null || searchValue.isBlank()) {
            result.fail("No generated value found for column: " + validateGeneratedCol);
            return;
        }

        String keywordToSearch =
                menuSearch.isBlank() ? screenName : menuSearch;

        navigateToMenuScreen(
                driver,
                keywordToSearch,
                menuItemId
        );

        UIValidator.navigateSearchAndVerify(
                driver,
                validateGridCol,
                searchValue,
                result,
                columnId
        );

    }
}