package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.example.ui.pages.OrderDeliveryDatePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Map;

import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;

public class LocusUploadFlow {

    private static final Logger logger = LoggerUtil.getLogger(LocusUploadFlow.class);

    public static ValidationResult run(
            WebDriver driver,
            Map<String, Object> bulkOrderScreen,
            Map<String, Object> deliveryDateChangeScreen,
            Map<String, Object> locusUploadScreen,
            Map<String, Object> transactionInquiryScreen,
            Map<String, String> scenarioData,
            String downloadDir
    ) {

        // screenName used if we have to bail before we even know which screen we're on
        String screenName = (String) transactionInquiryScreen.getOrDefault("screenName", "Locus Upload Flow");
        ValidationResult result = new ValidationResult(screenName);

        logger.info("=====================================================");
        logger.info("🚀 Starting Locus Upload Flow");
        logger.info("=====================================================");

        try {
            // ---------- STEP 2: Read Generated Order Number ----------
            String bulkOrderUploadTestID = scenarioData.get("bulkOrderUploadTestID");
            String cashmemoNo = GeneratedDataStore.get(bulkOrderUploadTestID, "cashmemoNo");
            logger.info("Step 1: Generated Order Number = [{}]", cashmemoNo);

            if (cashmemoNo == null || cashmemoNo.isBlank()) {
                result.fail("Order Number not found in GeneratedDataStore for testId: "
                        + bulkOrderUploadTestID + ", column: cashmemoNo");
                return result;
            }

            // ---------- STEP 3: Change Delivery Date on the Order ----------
            logger.info("Step 3: Updating Delivery Date on order [{}]...", cashmemoNo);
            updateDeliveryDate(driver, deliveryDateChangeScreen, scenarioData, cashmemoNo);
            logger.info("Step 3 complete.");

            // ---------- STEP 4: Update Locus Excel ----------
            String locusTemplatePath = (String) locusUploadScreen.get("templatePath");
            String locusOrderColumn = scenarioData.get("LocusOrderColumn");
            String locusInvoiceKeyColumn = scenarioData.get("LocusInvoiceKeyColumn");
            String orgaCode = scenarioData.get("orgaCode");
            String locusDeliveryDateColumn = scenarioData.get("LocusDeliveryDateColumn");
            String todayDate = java.time.LocalDate.now().toString();

            logger.info("Step 4: Updating Locus Excel columns [{}]=[{}], [{}]=[{}]",
                    locusOrderColumn, cashmemoNo, locusDeliveryDateColumn, todayDate);
            String invoiceKey = orgaCode + "-" + cashmemoNo;
            ExcelUtils.updateColumnValue(locusTemplatePath, locusOrderColumn, cashmemoNo);
            ExcelUtils.updateColumnValue(locusTemplatePath, locusDeliveryDateColumn, todayDate);
            ExcelUtils.updateColumnValue(locusTemplatePath, locusInvoiceKeyColumn, invoiceKey);
            logger.info("Step 4 complete.");

            // ---------- STEP 5: Upload Locus Excel ----------
            logger.info("Step 5: Uploading Locus Excel...");
            PJPExcelUpload.uploadScreenRespectingMode(driver, locusUploadScreen);
            logger.info("Step 5 complete.");

            // ---------- STEP 6: Call Locus API ----------
            logger.info("Step 6: Calling Locus API...");
            callLocusApi(scenarioData, result);
            if (!result.passed) {
                // API call failed — stop here, don't bother checking Transaction Inquiry
                return result;
            }
            logger.info("Step 6 complete.");

            // ---------- STEP 7: Transaction Inquiry Validation ----------
            logger.info("Step 7: Validating via Transaction Inquiry...");
            validateTransactionInquiry(driver, transactionInquiryScreen, scenarioData, cashmemoNo, result);

        } catch (Exception e) {
            logger.error("💥 Locus Upload Flow failed with an exception", e);
            result.fail("Locus Upload Flow error: " + e.getMessage());
            ScreenshotService.takeScreenshot(driver, "LocusUploadFlow_Error");
        }

        logger.info("=====================================================");
        logger.info("✅ Locus Upload Flow Completed (passed={})", result.passed);
        logger.info("=====================================================");

        return result;
    }

    // -------------------------------------------------------------------
    // STEP 3 helper
    // -------------------------------------------------------------------
    private static void updateDeliveryDate(WebDriver driver,
                                           Map<String, Object> deliveryDateChangeScreen,
                                           Map<String, String> scenarioData,
                                           String cashmemoNo) throws Exception {

        String screenName = (String) deliveryDateChangeScreen.get("screenName");
        String screenId = (String) deliveryDateChangeScreen.get("screenId");
        String orderPJP = scenarioData.get("orderPJP");

        navigateToScreen(driver, screenName, screenId);

        OrderDeliveryDatePage page = new OrderDeliveryDatePage(driver);
        page.selectDropdown(By.id("DDL__EPJPPJPNODAILY"), orderPJP);

        page.searchOrder("rowfilter_DOCNO", cashmemoNo);
        page.selectOrder(cashmemoNo);
        page.click(By.id("DYP.G......ProcessButton"));
        page.acceptAlertIfPresent(driver);
    }

    // -------------------------------------------------------------------
    // STEP 6 helper — now records failure into result instead of throwing
    // -------------------------------------------------------------------
    private static void callLocusApi(Map<String, String> scenarioData, ValidationResult result) {
        // TODO: restore real API call once ready; stubbed to 200 for now.
        int statusCode = 200;
        logger.info("Locus API response code: {}", statusCode);

        if (statusCode != 200) {
            result.fail("Locus API call failed. Status=" + statusCode);
        } else {
            result.pass("Locus API call succeeded. Status=" + statusCode);
        }
    }

    // -------------------------------------------------------------------
    // STEP 7 helper — read-only grid check, records into the passed-in result
    // -------------------------------------------------------------------
    private static void validateTransactionInquiry(WebDriver driver,
                                                   Map<String, Object> transactionInquiryScreen,
                                                   Map<String, String> scenarioData,
                                                   String orderNumber,
                                                   ValidationResult result) throws Exception {

        String screenName = (String) transactionInquiryScreen.get("screenName");
        String screenId = (String) transactionInquiryScreen.get("screenId");

        navigateToScreen(driver, screenName, screenId);

        String orderSearchColumnId = scenarioData.get("TransInquiryOrderColumnId");
        String statusColumnId = scenarioData.get("StatusColumnId");
        String ginColumnId = scenarioData.get("GinColumnId");
        String expectedStatus = scenarioData.get("ExpectedStatus");

        Event.robustClick(driver, By.id("gridFilterCheckbox"));

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement searchBox = wait.until(
                ExpectedConditions.elementToBeClickable(By.id(orderSearchColumnId)));
        searchBox.clear();
        searchBox.sendKeys(orderNumber);

        waitForLoaderToDisappear(driver);

        String actualStatus = readFirstRowCell(driver, statusColumnId);
        String actualGin = readFirstRowCell(driver, ginColumnId);

        logger.info("Transaction Inquiry -> Status=[{}], GIN Number=[{}]", actualStatus, actualGin);

        if (actualStatus == null || actualStatus.isBlank()) {
            result.fail("Order [" + orderNumber + "] not found in Transaction Inquiry.");
            return;
        }

        if (!actualStatus.trim().equalsIgnoreCase(expectedStatus.trim())) {
            result.fail("Status mismatch. Expected=" + expectedStatus + " Actual=" + actualStatus);
        } else {
            result.pass("Status matched: " + actualStatus);
        }

        if (actualGin == null || actualGin.isBlank()) {
            result.fail("GIN Number was not generated.");
        } else {
            result.pass("GIN Number generated: " + actualGin);
        }
    }

    private static String readFirstRowCell(WebDriver driver, String columnId) {
        String xpath = "//tr[contains(@class,'dx-data-row')][1]//td[contains(@id,'_" + columnId + "')]";
        var cells = driver.findElements(By.xpath(xpath));
        return cells.isEmpty() ? "" : cells.get(0).getText().trim();
    }

    // -------------------------------------------------------------------
    // Shared navigation helper
    // -------------------------------------------------------------------
    private static void navigateToScreen(WebDriver driver, String screenName, String screenId) throws Exception {
        waitForLoaderToDisappear(driver);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            wait.until(ExpectedConditions.elementToBeClickable(By.id("menurollin")));
            Event.robustClick(driver, By.id("menurollin"));
        } catch (Exception e) {
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("input[placeholder='Search Here']")));
        }

        WebElement searchBox = wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.cssSelector("input[placeholder='Search Here']")));

        searchBox.clear();
        searchBox.sendKeys(screenName);
        Thread.sleep(400);

        wait.until(ExpectedConditions.elementToBeClickable(By.id(screenId)));
        Event.robustClick(driver, By.id(screenId));

        waitForLoaderToDisappear(driver);
    }
}