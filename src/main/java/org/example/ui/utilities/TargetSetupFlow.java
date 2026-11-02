package org.example.ui.utilities;

import io.restassured.response.Response;
import org.apache.logging.log4j.Logger;
import org.example.ui.pages.TargetAchievementPage;
import org.example.ui.pages.TargetSetupPage;
import org.example.ui.pages.TransactionInquiryPage;
import org.example.ui.pages.basePage;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.example.ui.pages.basePage.requireScenarioValue;
import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;

/**
 * Target end-to-end flow: historical data -> new/edit target -> bulk upload -> split -> shuffling
 * -> achievement (base) -> order lifecycle -> achievement again.
 * Check: achievement change = order gross - fresh return gross - sales return gross.
 */
public class TargetSetupFlow {

    private static final Logger logger = LoggerUtil.getLogger(TargetSetupFlow.class);
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final long POLL_INTERVAL_MS = 15000;

    public static ValidationResult run(
            WebDriver driver,
            String screenName,
            Map<String, String> scenarioData,
            String historicalDataFile,
            Map<String, Map<String, Object>> rules,
            String testID,
            List<Map<String, Object>> screens,
            String resourcesFolder,
            String downloadDir
    ) {
        ValidationResult result = new ValidationResult(
                "Target Setup - Historical Data, New and Edit Target, Bulk Upload, Split, Shuffling and Achievement"
        );

        try {
            validateInputs(scenarioData);

            // Test Data
            String distributor = scenarioData.get("distributor");
            String periodType = scenarioData.getOrDefault("targetPeriodType", "Monthly");
            String measurement = scenarioData.getOrDefault("targetMeasurement", "Gross Amount");
            String active = scenarioData.getOrDefault("targetActive", "Yes");
            String productHierarchy = scenarioData.get("targetProductHierarchy");
            String historicalSalesPeriod = scenarioData.get("targetHistoricalSalesPeriod");
            String offsetPeriod = scenarioData.get("targetOffsetPeriod");
            String shufflingCapping = scenarioData.get("targetShufflingCapping");
            String daysLimit = scenarioData.get("targetDaysLimit");
            String compareColumns = scenarioData.getOrDefault("targetCompareColumns", "DIST_CD,CAL_YEAR,CAL_MTH,PRDCAT_CD,TARGET_SALES");
            // R1 (PK/BD) uses the process-log endpoints; PH uses the older targetMasterDetail endpoints
            String splitEndpoint = scenarioData.getOrDefault("targetSplitEndpoint", TargetServiceApiClient.TARGET_SPLIT_ENDPOINT);
            // A blank body sends no request body (PH split)
            String splitBody = scenarioData.getOrDefault("targetSplitBody", "{}");
            int splitInitialWaitSeconds = Integer.parseInt(scenarioData.getOrDefault("splitInitialWaitSeconds", "30"));
            int splitTimeoutSeconds = Integer.parseInt(scenarioData.getOrDefault("splitWaitSeconds", "300"));
            String shuffleFromPjp = scenarioData.get("shuffleFromPjp");
            String orderPjp = scenarioData.get("PJPNO");
            String shuffleToPjp = scenarioData.getOrDefault("shuffleToPjp", orderPjp);
            double shuffleDelta = Double.parseDouble(scenarioData.getOrDefault("shuffleDelta", "2"));
            String achievementEndpoint = scenarioData.getOrDefault("targetAchievementEndpoint", TargetServiceApiClient.TARGET_ACHIEVEMENT_ENDPOINT);
            String achievementBody = scenarioData.getOrDefault("targetAchievementBody", "{}");
            int achievementWaitSeconds = Integer.parseInt(scenarioData.getOrDefault("achievementWaitSeconds", "60"));
            int achievementTimeoutSeconds = Integer.parseInt(scenarioData.getOrDefault("achievementTimeoutSeconds", "300"));
            String ginPjp = scenarioData.get("ginPjp");
            String freshReturnQty3 = scenarioData.get("freshReturnQty3");
            String freshReturnReason = scenarioData.get("returnReasonType");
            String salesReturnQty3 = scenarioData.get("salesReturnQty3");
            String salesReturnReason = scenarioData.get("SalesReturnReasonType");
            String grossColumn = scenarioData.getOrDefault("grossAmountColumnId", "row_1_gross_amount");
            // PH achievement includes markup (SDMS-11926): set markupColumnId to add each document's markup to its gross
            String markupColumn = scenarioData.get("markupColumnId");
            // Sales Return Without Reference runs only when srWithoutRefQty3 is set (PH)
            String srWithoutRefQty3 = scenarioData.get("srWithoutRefQty3");
            String srWithoutRefStockType = scenarioData.getOrDefault("srWithoutRefStockType", "Sound");
            String srWithoutRefReason = scenarioData.get("srWithoutRefReason");
            String srWithoutRefDocumentType = scenarioData.getOrDefault("srWithoutRefDocumentType", "Sales Return");

            LocalDate today = LocalDate.now();
            String year = String.valueOf(today.getYear());
            String monthName = today.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase();
            // Historical data is for the previous month (its Rules use a -1 month offset)
            String historicalYear = String.valueOf(today.minusMonths(1).getYear());

            String catalogId = scenarioData.getOrDefault("targetCatalogPrefix", "TGT")
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
            scenarioData.put("targetCatalogId", catalogId);

            // Expected lister State / Definition at each step (Entry -> Active -> Split)
            String stateAfterCreate = scenarioData.getOrDefault("targetStateAfterCreate", "Entry");
            String stateAfterUpload = scenarioData.getOrDefault("targetStateAfterUpload", "Active");
            String splitDefinition = scenarioData.getOrDefault("targetSplitDefinition", "1 splitted out of 1");
            String targetScreenId = (String) basePage.findScreenByTestId(screens, testID, resourcesFolder).get("screenId");

            Map<String, Object> bulkScreen = basePage.findScreenByTestId(screens, scenarioData.get("targetBulkTestId"), resourcesFolder);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> bulkRules = (Map<String, Map<String, Object>>) bulkScreen.get("rules");

            // Page Instantiations
            TargetSetupPage targetSetupPage = new TargetSetupPage(driver);
            TargetAchievementPage achievementPage = new TargetAchievementPage(driver);
            TransactionInquiryPage transactionInquiryPage = new TransactionInquiryPage(driver);
            OrderSteps orderSteps = new OrderSteps(driver);

            // STEP 1: HISTORICAL DATA EXPORT
            targetSetupPage.openHistoricalData();
            targetSetupPage.selectYearAndDistributor(historicalYear, distributor);
            checkExported(targetSetupPage.exportToExcel(downloadDir), "Historical Data", driver, result);

            // STEP 2: HISTORICAL DATA UPLOAD (Rules set CAL_YEAR / CAL_MTH to the previous month)
            String historicalFile = ExcelGen.generateExcel(historicalDataFile, rules, testID);
            uploadAndCheck(targetSetupPage, historicalFile, "Historical Data upload", downloadDir, driver, result);
            targetSetupPage.goBack();

            // STEP 3: NEW TARGET
            targetSetupPage.openNewTarget();
            targetSetupPage.fillTargetHeader(catalogId, year, periodType, monthName, measurement, active);
            targetSetupPage.fillExtraHeaderFields(productHierarchy, historicalSalesPeriod, offsetPeriod, shufflingCapping, daysLimit);
            String saveToast = targetSetupPage.saveTarget(result);
            result.pass("Target [" + catalogId + "] saved for " + monthName + " " + year
                    + " (" + periodType + ", " + measurement + ", " + active + "): " + saveToast);
            targetSetupPage.goBackIfOnForm();

            // STEP 3b: saving another target with the same code must be rejected (SDMS-9780)
            targetSetupPage.openNewTarget();
            targetSetupPage.fillTargetHeader(catalogId, year, periodType, monthName, measurement, active);
            targetSetupPage.fillExtraHeaderFields(productHierarchy, historicalSalesPeriod, offsetPeriod, shufflingCapping, daysLimit);
            String duplicateToast = targetSetupPage.saveTargetExpectingError();
            if (duplicateToast.toLowerCase().contains("catalog id already exists")) {
                result.pass("Duplicate target code [" + catalogId + "] rejected: " + duplicateToast);
            } else {
                result.fail("Duplicate target code [" + catalogId + "]: expected 'Catalog id already exists.' but got '"
                        + duplicateToast + "'", driver);
            }
            targetSetupPage.goBackIfOnForm();

            // STEP 4: EDIT TARGET (open and save without changes)
            targetSetupPage.selectTarget(catalogId);
            checkTargetState(targetSetupPage, catalogId, stateAfterCreate, "after New Target", driver, result);
            targetSetupPage.openEditTarget(catalogId);
            // The saved target keeps the month it was created for, not the next month (SDMS-2863)
            String savedYear = targetSetupPage.getSavedYear();
            String savedPeriod = targetSetupPage.getSavedPeriodName();
            if (year.equals(savedYear) && savedPeriod.toUpperCase().contains(monthName)) {
                result.pass("Target [" + catalogId + "] saved period: " + savedYear + " " + savedPeriod);
            } else {
                result.fail("Target [" + catalogId + "] saved period is " + savedYear + " " + savedPeriod
                        + ", expected " + year + " " + monthName, driver);
            }
            String editToast = targetSetupPage.saveTarget(result);
            result.pass("Target [" + catalogId + "] edited and saved: " + editToast);
            targetSetupPage.goBack();

            // STEP 5: TARGET BULK UPLOAD (own config row: template + Rules for the current month), then re-export to confirm
            targetSetupPage.selectTarget(catalogId);
            targetSetupPage.openTargetUploadBulk();
            File bulkTemplate = checkExported(targetSetupPage.exportToExcel(downloadDir), "Target Bulk template", driver, result);
            // A Monthly target's template must not carry a CAL_WEEK column (SDMS-10243)
            if (bulkTemplate != null && "Monthly".equalsIgnoreCase(periodType)) {
                checkNoColumn(bulkTemplate, "CAL_WEEK", "Target Bulk template (Monthly)", driver, result);
            }
            String targetFile = ExcelGen.generateExcel((String) bulkScreen.get("templatePath"), bulkRules, (String) bulkScreen.get("testID"));
            uploadAndCheck(targetSetupPage, targetFile, "Target Bulk upload", downloadDir, driver, result);
            File exportedTarget = checkExported(targetSetupPage.exportToExcel(downloadDir), "Target Bulk after upload", driver, result);
            if (exportedTarget != null) {
                UploadedVsDownloadedComparator.compare(new File(targetFile), exportedTarget, splitColumns(compareColumns), result);
            }
            targetSetupPage.goBack();
            targetSetupPage.selectTarget(catalogId);
            checkTargetState(targetSetupPage, catalogId, stateAfterUpload, "after Target Bulk upload", driver, result);

            // STEP 6: TARGET SPLIT - process-log endpoint, then wait for State = Split
            triggerProcess(driver, splitEndpoint, splitBody, "Target Split", result);
            Thread.sleep(splitInitialWaitSeconds * 1000L);
            // The filter row is already open from the state check above
            waitForTargetState(targetSetupPage, catalogId, "Split", splitTimeoutSeconds, result);
            checkText("Target [" + catalogId + "] Definition after split", splitDefinition,
                    targetSetupPage.getFirstRowDefinition(), driver, result);

            // STEP 7: TARGET SHUFFLING - move shuffleDelta % to the order PJP on every product, upload, re-export
            targetSetupPage.openTargetShuffling();
            // A user assigned one distributor must see only that distributor (SDMS-10892)
            List<String> offered = targetSetupPage.getDistributorOptions();
            if (offered.size() == 1 && offered.get(0).contains(distributor)) {
                result.pass("Target Shuffling distributor list shows only the assigned distributor: " + offered);
            } else {
                result.fail("Target Shuffling distributor list should show only " + distributor + " but shows " + offered, driver);
            }
            targetSetupPage.selectDistributor(distributor);
            File shuffleFile = checkExported(targetSetupPage.exportToExcel(downloadDir), "Target Shuffling", driver, result);
            if (shuffleFile != null) {
                // Exported file as the app built it: Total row = sum of (share % / 100 x category TARGET_VALUE)
                checkShuffleTargetValues(shuffleFile, shuffleToPjp, "before shuffling", result);
                // Re-uploading the exported file unchanged must succeed (SDMS-11887 / SDMS-10349)
                uploadAndCheck(targetSetupPage, shuffleFile.getAbsolutePath(), "Target Shuffling upload (unchanged file)", downloadDir, driver, result);
                TargetShuffleExcel beforeShuffle = TargetShuffleExcel.read(shuffleFile);
                Map<String, BigDecimal> expectedValueChanges = new LinkedHashMap<>();
                List<String> shuffledColumns = shuffleTarget(shuffleFile, shuffleFromPjp, shuffleToPjp, shuffleDelta,
                        expectedValueChanges, result);
                TargetShuffleExcel.recalculateTotalRow(shuffleFile);
                uploadAndCheck(targetSetupPage, shuffleFile.getAbsolutePath(), "Target Shuffling upload", downloadDir, driver, result);

                File exportedShuffle = checkExported(targetSetupPage.exportToExcel(downloadDir), "Target Shuffling after upload", driver, result);
                if (exportedShuffle != null) {
                    UploadedVsDownloadedComparator.compare(shuffleFile, exportedShuffle, shuffledColumns, result);
                    checkShuffleTotals(exportedShuffle, result);
                    checkShuffleTargetValues(exportedShuffle, shuffleToPjp, "after shuffling", result);
                    checkShuffleMovement(beforeShuffle, exportedShuffle, expectedValueChanges, result);
                }
            }
            // Shuffling must not change the target's state
            targetSetupPage.navigateToScreen(screenName, targetScreenId);
            targetSetupPage.selectTarget(catalogId);
            checkTargetState(targetSetupPage, catalogId, "Split", "after Target Shuffling", driver, result);

            // STEP 8: ACHIEVEMENT BEFORE THE ORDER (base value)
            triggerProcess(driver, achievementEndpoint, achievementBody, "Target Achievement before order", result);
            Thread.sleep(achievementWaitSeconds * 1000L);
            BigDecimal achievementBefore = readAchievement(achievementPage, distributor, orderPjp, catalogId);
            result.pass("Target Achievement before order: PJP " + orderPjp + ", target " + catalogId + " = " + achievementBefore.toPlainString());

            // STEP 9: ORDER FLOW - book, deliver, fresh return, sales return
            String orderNo = orderSteps.bookOrder(scenarioData);
            orderSteps.updateDeliveryDate(orderPjp, orderNo, result);
            String ginNo = orderSteps.createAndApproveGin(ginPjp, orderNo, null, null, result);
            String freshReturnNo = orderSteps.freshReturn(ginPjp, orderNo, null, freshReturnQty3, freshReturnReason, result);
            orderSteps.deliver(ginPjp, ginNo, orderNo, scenarioData, result);
            String salesReturnNo = orderSteps.salesReturn(ginPjp, ginNo, orderNo, salesReturnQty3, salesReturnReason, result);
            String srWithoutRefNo = null;
            if (srWithoutRefQty3 != null && !srWithoutRefQty3.isBlank()) {
                // Must also be deducted from the PJP achievement (SDMS-12359)
                srWithoutRefNo = orderSteps.salesReturnWithoutReference(scenarioData, ginPjp, ginNo, srWithoutRefQty3,
                        srWithoutRefStockType, srWithoutRefReason, result);
            }
            result.pass("Order flow done: order " + orderNo + ", GIN " + ginNo + ", fresh return " + freshReturnNo
                    + ", sales return " + salesReturnNo + (srWithoutRefNo != null ? ", sales return w/o reference " + srWithoutRefNo : ""));

            // Expected change = order gross - fresh return gross - sales return gross [- sales return w/o reference gross]
            BigDecimal orderGross = readGrossAmount(driver, transactionInquiryPage, "Sales", orderPjp, orderNo, grossColumn, markupColumn);
            BigDecimal freshReturnGross = readGrossAmount(driver, transactionInquiryPage, "Fresh Sales Return", orderPjp, freshReturnNo, grossColumn, markupColumn);
            BigDecimal salesReturnGross = readGrossAmount(driver, transactionInquiryPage, "Sales Return", orderPjp, salesReturnNo, grossColumn, markupColumn);
            BigDecimal srWithoutRefGross = srWithoutRefNo == null ? BigDecimal.ZERO
                    : readGrossAmount(driver, transactionInquiryPage, srWithoutRefDocumentType, orderPjp, srWithoutRefNo, grossColumn, markupColumn);
            BigDecimal expectedChange = orderGross.subtract(freshReturnGross).subtract(salesReturnGross).subtract(srWithoutRefGross);

            // STEP 10: ACHIEVEMENT AFTER THE ORDER - change must equal the net gross amount
            triggerProcess(driver, achievementEndpoint, achievementBody, "Target Achievement after order", result);
            BigDecimal achievementAfter = waitForAchievementChange(achievementPage, distributor, orderPjp, catalogId, achievementBefore, achievementTimeoutSeconds);
            BigDecimal actualChange = achievementAfter.subtract(achievementBefore);
            checkAmount("Achievement change for PJP " + orderPjp + " (" + achievementBefore.toPlainString() + " -> "
                            + achievementAfter.toPlainString() + ") vs order " + (markupColumn != null ? "gross + markup " : "gross ")
                            + orderGross.toPlainString()
                            + " - fresh return " + freshReturnGross.toPlainString() + " - sales return " + salesReturnGross.toPlainString()
                            + (srWithoutRefNo != null ? " - sales return w/o reference " + srWithoutRefGross.toPlainString() : ""),
                    expectedChange, actualChange, driver, result);

        } catch (Exception e) {
            logger.error("❌ Target Setup flow failed: {}", e.getMessage(), e);
            result.fail("Flow execution encountered an error: " + e.getMessage(), driver);
        }

        return result;
    }

    private static void validateInputs(Map<String, String> data) {
        String[] keys = {"distributor", "targetBulkTestId", "PJPNO", "ginPjp", "productCode", "QuantityPCS",
                "freshReturnQty3", "returnReasonType", "salesReturnQty3", "SalesReturnReasonType"};
        for (String key : keys) {
            requireScenarioValue(data, key, data.get(key));
        }
    }

    // ---------- checks ----------

    // Upload inside the flow's own result (one report row); a rejected upload lists the error Excel's row messages
    private static void uploadAndCheck(TargetSetupPage page, String filePath, String label, String downloadDir,
                                       WebDriver driver, ValidationResult result) throws InterruptedException {
        long uploadedAt = System.currentTimeMillis();
        String toast = page.uploadExcel(filePath);
        if (toast.toLowerCase().contains("success")) {
            result.pass(label + ": " + toast);
            return;
        }
        String message = label + " failed: " + (toast.isBlank() ? "no message appeared" : toast);
        File errorFile = page.waitForErrorFile(downloadDir, uploadedAt);
        if (errorFile != null) {
            message += " | Excel row errors: " + ExcelValidator.logExcelErrors(errorFile);
        }
        result.fail(message, driver);
    }

    private static void checkNoColumn(File file, String column, String label, WebDriver driver, ValidationResult result)
            throws IOException {
        List<String> headers = new ArrayList<>();
        try (java.io.FileInputStream in = new java.io.FileInputStream(file);
             org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(in)) {
            org.apache.poi.ss.usermodel.DataFormatter formatter = new org.apache.poi.ss.usermodel.DataFormatter();
            for (org.apache.poi.ss.usermodel.Cell cell : workbook.getSheetAt(0).getRow(0)) {
                headers.add(formatter.formatCellValue(cell).trim());
            }
        }
        if (headers.stream().anyMatch(h -> h.equalsIgnoreCase(column))) {
            result.fail(label + " has a " + column + " column: " + headers, driver);
        } else {
            result.pass(label + " has no " + column + " column: " + headers);
        }
    }

    private static File checkExported(File file, String label, WebDriver driver, ValidationResult result) {
        if (file == null) {
            result.fail(label + " export did not download a file", driver);
        } else {
            result.pass(label + " exported: " + file.getName());
        }
        return file;
    }

    private static void checkAmount(String label, BigDecimal expected, BigDecimal actual, WebDriver driver, ValidationResult result) {
        String message = label + ": expected " + expected.toPlainString() + ", actual " + actual.toPlainString();
        if (expected.subtract(actual).abs().compareTo(AMOUNT_TOLERANCE) <= 0) {
            result.pass(message, driver);
        } else {
            result.fail(message, driver);
        }
    }

    // Each product row with a split target must split 100% across its PJPs
    private static void checkShuffleTotals(File shuffleFile, ValidationResult result) throws IOException {
        TargetShuffleExcel shuffle = TargetShuffleExcel.read(shuffleFile);
        for (String product : shuffle.getProducts()) {
            BigDecimal total = shuffle.getTotalShare(product);
            if (total.signum() == 0) continue;   // no split target for this product
            String message = "Target Shuffling: product " + product + " PJP shares total " + total.stripTrailingZeros().toPlainString() + "%";
            if (total.subtract(BigDecimal.valueOf(100)).abs().compareTo(AMOUNT_TOLERANCE) <= 0) {
                result.pass(message);
            } else {
                result.fail(message + ", expected 100%");
            }
        }
    }

    // BD: each PJP's Total row value must equal sum of (share % x product TARGET_VALUE / 100)
    private static void checkShuffleTargetValues(File shuffleFile, String orderPjp, String label, ValidationResult result) throws IOException {
        TargetShuffleExcel shuffle = TargetShuffleExcel.read(shuffleFile);
        if (!shuffle.hasTotalRow()) return;

        List<String> mismatches = new ArrayList<>();
        for (String pjpColumn : shuffle.getPjpColumns()) {
            BigDecimal expected = shuffle.calculatePjpTargetValue(pjpColumn);
            BigDecimal actual = shuffle.getTotalRowValue(pjpColumn);
            if (expected.subtract(actual).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
                mismatches.add(pjpColumn + " total " + actual.toPlainString() + ", calculated " + expected.toPlainString());
            }
        }
        if (mismatches.isEmpty()) {
            result.pass("Target Shuffling (" + label + "): Total row matches share % x target value for all " + shuffle.getPjpColumns().size()
                    + " PJPs; order PJP " + orderPjp + " target value = "
                    + shuffle.calculatePjpTargetValue(shuffle.getPjpColumn(orderPjp)).toPlainString());
        } else {
            result.fail("Target Shuffling (" + label + "): Total row does not match share % x target value for " + mismatches);
        }
    }

    // BD: the DT target is only redistributed - sum of PJP totals = DT TARGET_VALUE (Level-1 = Level-2)
    // and each PJP's target value moved by exactly the shuffled amount; every other PJP is unchanged
    private static void checkShuffleMovement(TargetShuffleExcel before, File exportedShuffle,
                                             Map<String, BigDecimal> expectedValueChanges, ValidationResult result) throws IOException {
        TargetShuffleExcel after = TargetShuffleExcel.read(exportedShuffle);
        if (!before.hasTotalRow() || !after.hasTotalRow()) return;

        BigDecimal dtTarget = after.getTotalTargetValue();
        BigDecimal pjpTotal = after.getSumOfPjpTotals();
        String totalMessage = "Target Shuffling: sum of PJP target values " + pjpTotal.stripTrailingZeros().toPlainString()
                + " vs DT target " + dtTarget.stripTrailingZeros().toPlainString();
        if (pjpTotal.subtract(dtTarget).abs().compareTo(AMOUNT_TOLERANCE) <= 0
                && dtTarget.compareTo(before.getTotalTargetValue()) == 0) {
            result.pass(totalMessage + " (unchanged by shuffling)");
        } else {
            result.fail(totalMessage + ", DT target before shuffling " + before.getTotalTargetValue().stripTrailingZeros().toPlainString());
        }

        List<String> mismatches = new ArrayList<>();
        for (String pjpColumn : after.getPjpColumns()) {
            BigDecimal expected = expectedValueChanges.getOrDefault(pjpColumn, BigDecimal.ZERO);
            BigDecimal actual = after.getTotalRowValue(pjpColumn).subtract(before.calculatePjpTargetValue(pjpColumn));
            if (expected.subtract(actual).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
                mismatches.add(pjpColumn + " changed " + actual.stripTrailingZeros().toPlainString()
                        + ", expected " + expected.stripTrailingZeros().toPlainString());
            }
        }
        if (mismatches.isEmpty()) {
            List<String> moved = new ArrayList<>();
            expectedValueChanges.forEach((pjp, change) -> moved.add(pjp + " " + (change.signum() > 0 ? "+" : "")
                    + change.stripTrailingZeros().toPlainString()));
            result.pass("Target Shuffling: PJP target values moved exactly as shuffled " + moved + ", all other PJPs unchanged");
        } else {
            result.fail("Target Shuffling: PJP target values did not move as shuffled: " + mismatches);
        }
    }

    // ---------- steps ----------

    private static void triggerProcess(WebDriver driver, String endpoint, String body, String label, ValidationResult result) {
        Response response = TargetServiceApiClient.post(driver, endpoint, body, label);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(label + " call failed: HTTP " + response.statusCode() + " " + response.asString());
        }
        result.pass(label + " triggered: HTTP " + response.statusCode() + " " + response.asString());
    }

    // State on the lister for the selected target; changes straight away, so no polling
    private static void checkTargetState(TargetSetupPage page, String catalogId, String expectedState, String step,
                                         WebDriver driver, ValidationResult result) {
        checkText("Target [" + catalogId + "] State " + step, expectedState, page.getFirstRowState(), driver, result);
    }

    private static void checkText(String label, String expected, String actual, WebDriver driver, ValidationResult result) {
        if (expected.equalsIgnoreCase(actual == null ? "" : actual.trim())) {
            result.pass(label + ": " + actual);
        } else {
            result.fail(label + ": expected [" + expected + "] but was [" + actual + "]", driver);
        }
    }

    // Split runs in the background: refresh the target on the lister until its State reaches the expected value
    private static void waitForTargetState(TargetSetupPage page, String catalogId, String expectedState,
                                           int timeoutSeconds, ValidationResult result) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        String state = "";
        while (System.currentTimeMillis() < deadline) {
            page.refreshTarget(catalogId);
            state = page.getFirstRowState();
            logger.info("Target [{}] state: {}", catalogId, state);
            if (expectedState.equalsIgnoreCase(state)) {
                result.pass("Target [" + catalogId + "] state is " + state + ", Definition: " + page.getFirstRowDefinition());
                return;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        throw new IllegalStateException("Target [" + catalogId + "] did not reach state " + expectedState
                + " within " + timeoutSeconds + "s (last state: " + state + ")");
    }

    /** Moves delta % to the "to" PJP on every product, from the configured PJP or the product's highest share. */
    private static List<String> shuffleTarget(File shuffleFile, String fromPjp, String toPjp, double delta,
                                              Map<String, BigDecimal> expectedValueChanges, ValidationResult result) throws IOException {
        TargetShuffleExcel shuffle = TargetShuffleExcel.read(shuffleFile);
        String toColumn = shuffle.getPjpColumn(toPjp);
        List<String> compareColumns = new ArrayList<>(List.of("PROD", toColumn));

        for (String product : shuffle.getProducts()) {
            // A product with no split target (all shares 0) has nothing to shuffle
            if (shuffle.getTotalShare(product).signum() == 0) {
                result.pass("Target Shuffling: product " + product + " has no split target, skipped");
                continue;
            }
            String fromColumn = fromPjp == null || fromPjp.isBlank()
                    ? shuffle.getHighestSharePjpColumn(product, toColumn)
                    : shuffle.getPjpColumn(fromPjp);
            double fromShare = shuffle.getShare(product, fromColumn);
            if (fromShare < delta) {
                throw new IllegalStateException("PJP [" + fromColumn + "] has only " + fromShare
                        + "% for product " + product + ", cannot move " + delta + "%");
            }

            TargetShuffleExcel.moveShare(shuffleFile, product, fromColumn, toColumn, delta);
            // Each moved % is worth delta x product TARGET_VALUE / 100 of target value (BD)
            BigDecimal movedValue = BigDecimal.valueOf(delta).multiply(shuffle.getTargetValue(product)).divide(BigDecimal.valueOf(100));
            expectedValueChanges.merge(fromColumn, movedValue.negate(), BigDecimal::add);
            expectedValueChanges.merge(toColumn, movedValue, BigDecimal::add);
            if (!compareColumns.contains(fromColumn)) compareColumns.add(fromColumn);
            result.pass("Target Shuffling: product " + product + " moved " + delta + "% from [" + fromColumn + "] ("
                    + fromShare + "%) to [" + toColumn + "] (" + shuffle.getShare(product, toColumn) + "%)");
        }
        return compareColumns;
    }

    // First row for the PJP and target (PH lists the same value twice); no row yet = 0
    private static BigDecimal readAchievement(TargetAchievementPage page, String distributor, String pjp, String catalogId) {
        page.open();
        page.searchAchievement(distributor, pjp, catalogId);
        return parseAmount(page.getFirstRowAchievementValue());
    }

    // Achievement runs in the background: re-read until the value moves off the base value
    private static BigDecimal waitForAchievementChange(TargetAchievementPage page, String distributor, String pjp, String catalogId,
                                                       BigDecimal before, int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        BigDecimal value = before;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS);
            value = readAchievement(page, distributor, pjp, catalogId);
            logger.info("Target Achievement PJP {} target {}: {}", pjp, catalogId, value);
            if (value.compareTo(before) != 0) {
                return value;
            }
        }
        return value;
    }

    // Gross amount of the document, plus its markup when markupColumn is set (PH).
    // Return documents can show amounts as negative, so they are read as absolute values
    private static BigDecimal readGrossAmount(WebDriver driver, TransactionInquiryPage page, String documentType,
                                              String pjp, String documentNo, String grossColumn, String markupColumn) {
        page.navigateToScreen("Transaction Inquiry", "DYL_102014");

        waitForLoaderToDisappear(driver);
        page.selectDocumentType(documentType);
        page.selectPjp(pjp);
        BigDecimal amount = parseAmount(page.getDocumentColumnValue(documentNo, grossColumn)).abs();
        if (markupColumn != null && !markupColumn.isBlank()) {
            BigDecimal markup = parseAmount(page.getFilteredRowColumnValue(markupColumn)).abs();
            logger.info("{} {}: gross {} + markup {}", documentType, documentNo, amount, markup);
            amount = amount.add(markup);
        }
        return amount;
    }

    private static BigDecimal parseAmount(String text) {
        String digits = text == null ? "" : text.replaceAll("[^0-9.\\-]", "");
        return digits.isEmpty() || "-".equals(digits) || ".".equals(digits) ? BigDecimal.ZERO : new BigDecimal(digits);
    }

    private static List<String> splitColumns(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
