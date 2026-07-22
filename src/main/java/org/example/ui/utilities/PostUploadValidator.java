package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
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
                                       String downloadDir, String validateMode, Map<String, String> scenarioData) throws Exception {
       System.out.println(validationsSheet);
        // COMPARE mode doesn't require Validation sheet
        if (!"COMPARE".equals(validateMode)) {

            if (validationsSheet == null || validationsSheet.isEmpty()) {
                logger.info("⏩ No validation configuration found. Skipping validation.");
                return null;
            }
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
                validateCashmemo.validateCashmemoForLMT(driver, wait,result,testId);
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
            case "LOCUS":{ // for DIST EXCEL

                File downloaded = latestFile(downloadDir);
                LocusDownloadExcelValidation.validate(
                        driver,
                        testId,validationsSheet,
                        scenarioData,downloaded,
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
                break;}
                case "PROD_DIST_PRICE":{ // for PRICE MASTER APPROVAL EXCEL
                        ProdPriceApprovalExcelValidation.validate(
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
            case "SELLCAT" :{
                SellCatBulkUploadValidation.validate(
                        driver,
                        testId,validationsSheet,
                        scenarioData,
                        result
                );
                break;
            }
            case "EXACT_COMPARE": {
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
                ExactExcelComparator.compare(
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
    private static void navigateToMenuScreen(WebDriver driver, String menuSearchText, String menuItemId) {
        logger.debug("[MenuNav] Starting navigation -> Search: '{}' | MenuItemId: '{}'", menuSearchText, menuItemId);

        // 1. CRITICAL: Clear any post-upload spinners/backdrops before touching the menu
        LoaderWait.waitForLoaderToDisappear(driver);

        var wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        var shortWait = new WebDriverWait(driver, Duration.ofSeconds(3));

        By formContainerLocator = By.id("form");
        By searchBoxLocator = By.cssSelector("#form input.filterinput");
        By menuItemLocator = By.id(menuItemId);
        By hamburgerLocator = By.id("menurollin");

        try {
            // ------------------------------------------------------------------
            // STEP 1: Hamburger / Visibility Check with JS Overlay Fallback
            // ------------------------------------------------------------------
            boolean isSearchVisible = false;
            try {
                WebElement searchInput = driver.findElement(searchBoxLocator);
                isSearchVisible = searchInput.isDisplayed();
                logger.debug("[MenuNav] Search input display state: {}", isSearchVisible);
            } catch (Exception ignored) {
                logger.debug("[MenuNav] Search input not present in DOM yet.");
            }

            if (!isSearchVisible) {
                logger.debug("[MenuNav] Menu collapsed. Attempting to expand via hamburger [{}]...", hamburgerLocator);

                try {
                    // Try standard click via short 3s wait
                    shortWait.until(ExpectedConditions.elementToBeClickable(hamburgerLocator));
                    Event.robustClick(driver, hamburgerLocator);
                } catch (TimeoutException te) {
                    logger.warn("[MenuNav] Hamburger not clickable via UI wait (overlay active?). Forcing JS click on [{}]", hamburgerLocator);
                    WebElement hamburger = driver.findElement(hamburgerLocator);
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", hamburger);
                }

                // Wait for search box container to expand
                wait.until(ExpectedConditions.visibilityOfElementLocated(searchBoxLocator));
                logger.debug("[MenuNav] Search box is now visible.");
            }

            // ------------------------------------------------------------------
            // STEP 2: Focus & Type Search Text inside #form
            // ------------------------------------------------------------------
            WebElement searchBox = wait.until(ExpectedConditions.elementToBeClickable(searchBoxLocator));

            try {
                searchBox.click();
            } catch (Exception e) {
                logger.debug("[MenuNav] Direct click failed; clicking #form container to focus input.");
                driver.findElement(formContainerLocator).click();
            }

            logger.debug("[MenuNav] Clearing input and typing search text: '{}'...", menuSearchText);
            searchBox.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
            searchBox.sendKeys(menuSearchText);

            String typedValue = searchBox.getAttribute("value");
            logger.debug("[MenuNav] DOM input value verified: '{}'", typedValue);

            // ------------------------------------------------------------------
            // STEP 3: Click Filtered Target Menu Item (Handles Angular Re-renders)
            // ------------------------------------------------------------------
            logger.debug("[MenuNav] Waiting for target menu item [{}]...", menuItemId);

            boolean itemClicked = false;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    WebElement menuItem = wait.until(ExpectedConditions.elementToBeClickable(menuItemLocator));

                    logger.debug("[MenuNav] Attempt {}/3 - Found menu item [{}]. Displayed: {}, Enabled: {}",
                            attempt, menuItemId, menuItem.isDisplayed(), menuItem.isEnabled());

                    Event.robustClick(driver, menuItemLocator);
                    itemClicked = true;
                    logger.debug("[MenuNav] Successfully clicked menu item [{}].", menuItemId);
                    break;
                } catch (StaleElementReferenceException e) {
                    logger.warn("[MenuNav] Attempt {}/3 - Element went stale during Angular filter animation. Retrying...", attempt);
                }
            }

            if (!itemClicked) {
                throw new IllegalStateException("Failed to click menu item [" + menuItemId + "] after 3 retries.");
            }

            // ------------------------------------------------------------------
            // STEP 4: Post-Navigation Loader Sync
            // ------------------------------------------------------------------
            logger.debug("[MenuNav] Waiting for target page loader...");
            LoaderWait.waitForLoaderToDisappear(driver);
            logger.debug("[MenuNav] Navigation completed successfully for [{}]", menuItemId);

        } catch (Exception e) {
            logger.error("[MenuNav-FAILURE] Navigation failed for MenuItemId: '{}' | SearchText: '{}'", menuItemId, menuSearchText);
            try {
                logger.error("[MenuNav-FAILURE] Page URL: {}", driver.getCurrentUrl());

                boolean hamburgerExists = !driver.findElements(hamburgerLocator).isEmpty();
                boolean formContainerExists = !driver.findElements(formContainerLocator).isEmpty();
                boolean searchExists = !driver.findElements(searchBoxLocator).isEmpty();
                boolean menuItemExists = !driver.findElements(menuItemLocator).isEmpty();

                logger.error("[MenuNav-FAILURE] Element Status -> Hamburger Exists: {} | #form Container Exists: {} | Search Input Exists: {} | Target Item Exists: {}",
                        hamburgerExists, formContainerExists, searchExists, menuItemExists);
            } catch (Exception diagError) {
                logger.error("[MenuNav-FAILURE] Could not log diagnostics: {}", diagError.getMessage());
            }

            throw new RuntimeException("Menu navigation failed for item: " + menuItemId, e);
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