package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.example.ui.pages.OrderDeliveryDatePage;
import org.example.ui.pages.executeNodeJob;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List; // FIXED: Added missing import for List
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

            // ---------- STEP 6: Execute Background Node Job ----------
            logger.info("Step 6: Executing Node Executor Job in isolated background browser...");
            executeNodeJob nodeJob = new executeNodeJob(driver);
            nodeJob.run(scenarioData, result);
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

        String statusColumnId = scenarioData.get("StatusColumnId");
        String ginColumnId = scenarioData.get("GinColumnId");
        String expectedStatus = "Planning completed";

        logger.info("Applying Transaction Inquiry filters for order: {}", orderNumber);
        Event.robustClick(driver, By.id("gridFilterCheckbox"));

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement searchBox = wait.until(
                ExpectedConditions.elementToBeClickable(By.id("rowfilter_TXT__TCMMDOCNO")));

        searchBox.clear();
        searchBox.sendKeys(orderNumber);
        searchBox.sendKeys(Keys.ENTER);

        // DevExtreme typing debounce buffer
        Thread.sleep(800);
        waitForLoaderToDisappear(driver);

        List<WebElement> visibleRows = driver.findElements(By.xpath("//tr[contains(@class,'dx-data-row')]"));
        if (visibleRows.isEmpty()) {
            logger.warn("No records returned in grid for order: {}", orderNumber);
            List<WebElement> emptyMessage = driver.findElements(By.className("dx-datagrid-nodata"));
            if (!emptyMessage.isEmpty()) {
                logger.warn("Grid system message: \"{}\"", emptyMessage.get(0).getText());
            }
            result.fail("Order [" + orderNumber + "] not found in Transaction Inquiry.");
            return;
        }

        String statusCol = (statusColumnId != null && !statusColumnId.isBlank()) ? statusColumnId : "row_1_execution_status";
        String ginCol = (ginColumnId != null && !ginColumnId.isBlank()) ? ginColumnId : "row_1_gin_no";

        String actualStatus = readFirstRowCell(driver, statusCol);
        String actualGin = readFirstRowCell(driver, ginCol);

        logger.info("Transaction Inquiry Result -> Status=[{}], GIN=[{}]", actualStatus, actualGin);

        if (actualStatus.isBlank()) {
            result.fail("Failed to extract Status for order [" + orderNumber + "].");
            return;
        }

        if (!actualStatus.equalsIgnoreCase(expectedStatus)) {
            result.fail("Status mismatch. Expected=" + expectedStatus + " Actual=" + actualStatus);
        } else {
            result.pass("Status matched: " + actualStatus);
        }

        if (actualGin.isBlank()) {
            result.fail("GIN Number was not generated.");
        } else {
            result.pass("Auto GIN Created, GIN No: " + actualGin);
        }
    }

    private static String readFirstRowCell(WebDriver driver, String columnId) {
        String rowXpath = "//tr[contains(@class,'dx-data-row')][1]";
        String cellXpath = rowXpath + "//td[@id='" + columnId + "']";

        List<WebElement> cells = driver.findElements(By.xpath(cellXpath));
        String text = "";

        // 1. Try to read textContent instantly if the element is already in the DOM
        if (!cells.isEmpty()) {
            text = cells.get(0).getAttribute("textContent").trim();
        }

        // 2. Scroll only if the cell is completely unrendered (virtualized) or truly empty
        if (text.isEmpty()) {
            logger.info("Cell '{}' not in DOM or empty. Scrolling grid to render...", columnId);
            List<WebElement> rows = driver.findElements(By.xpath(rowXpath));
            if (!rows.isEmpty()) {
                scrollGridToFarRight(driver, rows.get(0));

                // Re-locate cell to avoid StaleElementReferenceException after scroll repaint
                cells = driver.findElements(By.xpath(cellXpath));
                if (!cells.isEmpty()) {
                    text = cells.get(0).getAttribute("textContent").trim();
                    logger.info("Resolved cell '{}' post-scroll: [{}]", columnId, text);
                } else {
                    logger.warn("Cell '{}' could not be found even after scrolling.", columnId);
                }
            } else {
                logger.warn("No active data rows found; skipping scroll sequence.");
            }
        } else {
            logger.info("Resolved cell '{}' instantly using textContent: [{}]", columnId, text);
        }

        return text;
    }

    private static void scrollGridToFarRight(WebDriver driver, WebElement rowElement) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                    "var container = arguments[0].closest('.dx-scrollable-container');" +
                            "if (container) { container.scrollLeft = container.scrollWidth; }"
            );
            Thread.sleep(400); // Wait briefly for DevExtreme to repaint virtual columns
        } catch (Exception e) {
            logger.error("Failed to execute horizontal grid scroll", e);
        }
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