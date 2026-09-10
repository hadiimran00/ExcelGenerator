package org.example.ui.pages;

import org.apache.logging.log4j.Logger;
import org.example.ui.utilities.Event;
import org.example.ui.utilities.LoggerUtil;
import org.example.ui.utilities.ToastHandles;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

public class SANPage extends basePage {
    private static final Logger logger = LoggerUtil.getLogger(SANPage.class);
    private final WebDriverWait wait;

    // --- LOCATORS ---
    // Navigation & Search
    private final By menuButton = By.cssSelector(".mdi.mdi-menu.mdi-18px");
    private final By menuSearchBox = By.cssSelector("input[placeholder='Search Here']");
    private final By stockAdjustmentSanOption = By.xpath("//*[contains(@class,'dx-item-content') and normalize-space()='Stock Adjustment SAN']");

    // Clear Buttons
    private final By clearButton0 = By.id("clear_button_0");
    private final By clearButton1 = By.id("clear_button_1");

    // Header Controls & Inputs
    private final By headerSaveBtn = By.id("saveBtn");
    private final By sanDetailTab = By.id("tab_2");
    private final By sanHeaderTab = By.id("tab_1");
    private final By headerCheckbox0 = By.id("gridFilterCheckbox");
    private final By genericCheckboxIcon = By.cssSelector(".dx-checkbox-icon");
    private final By amountInput = By.xpath("//input[@type='text' or @type='number']");
    private final By FromWarehouse = By.id("DDL__epp1_busent_code_phs_lvl1_b");
    private final By docTypeDropdown = By.id("documentType");
    private final By selectDropdownField = By.xpath("//*[normalize-space()='Select']");

    // Detail Grid Controls
    private final By addRowBtn = By.id("cashMemoSelectionGrid0");
    private final By prodCodeInput = By.id("prodCode_0");
    private final By stockTypeInput = By.id("stockType_0");
    private final By reasonTypeInput = By.id("reasonType_0");
    private final By batchInput = By.id("batch_0");
    private final By quantityInput = By.id("quantity1_0");
    private final By rowSaveBtn = By.id("rowEditBtn_Save_0");
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By docTypeFilterInput = By.id("rowfilter_pdot_desc");
    private final By dateFilterInput = By.id("rowfilter_DT__TSTMDOCDATE");

    // Workflow Actions
    private final By forwardBtn = By.id("forward");
    private final By confirmYesBtn = By.id("yes");
    private final By workflowCommentInput = By.id("comments");
    private final By workflowSaveBtn = By.id("forwardPopUpSaveBtn");

    public SANPage(WebDriver driver) {
        super(driver);
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public void openStockAdjustmentSAN() {
        logger.info("📂 Navigating to Stock Adjustment SAN menu...");
        Event.robustClick(driver, menuButton);

        WebElement searchField = wait.until(ExpectedConditions.elementToBeClickable(menuSearchBox));
        searchField.clear();
        searchField.sendKeys("san");

        Event.robustClick(driver, stockAdjustmentSanOption);
    }

    /**
     * Populates the SAN header using selectDropdown() from basePage
     */
    public void fillSANHeader(String businessEntity, String docType) throws InterruptedException {
        logger.info("🏢 Populating SAN Header -> Business Entity: [{}], Doc Type: [{}]", businessEntity, docType);

        safeClearButton(clearButton1);
        selectDropdown(FromWarehouse, businessEntity);

        safeClearButton(clearButton0);
        try {
            selectDropdown(docTypeDropdown, docType);
        }catch(Exception e) {
            selectDropdown(docTypeDropdown, docType);
        }


        try {
            Event.robustClick(driver, headerSaveBtn);
        } catch (Exception e) {
            logger.info("ℹ️ Header save handled automatically or button unavailable.");
        }
    }

    /**
     * Adds line item details using selectDropdown() from basePage
     */
    public void addSANDetailRow(
            String product,
            String stockType,
            String reasonType,
            String batch,
            String qty1,
            String qty3) throws InterruptedException {

        logger.info("========== START ADD SAN DETAIL ROW ==========");
        logger.info("Product   : [{}]", product);
        logger.info("StockType : [{}]", stockType);
        logger.info("Reason    : [{}]", reasonType);
        logger.info("Batch     : [{}]", batch);
        logger.info("Qty 1     : [{}]", qty1);
        logger.info("Qty 3     : [{}]", qty3);

        try {
            logger.info("[1] Clicking SAN Detail tab...");
            Thread.sleep(1000);
            Event.robustClick(driver, sanDetailTab);

            logger.info("[2] Clicking Add Row button...");
            try {
                Event.robustClick(driver, addRowBtn);
            } catch (Exception e) {
                Event.robustClick(driver, sanDetailTab);
                Thread.sleep(1000);
                Event.robustClick(driver, addRowBtn);
            }

            logger.info("[3] Selecting Product: [{}]", product);
            try {
                selectDropdown(prodCodeInput, product);
            } catch (Exception e) {
                Thread.sleep(1000);
                selectDropdown(prodCodeInput, product);
            }

            logger.info("[4] Selecting Stock Type: [{}]", stockType);
            selectDropdown(stockTypeInput, stockType);

            logger.info("[5] Selecting Reason Type: [{}]", reasonType);
            selectDropdown(reasonTypeInput, reasonType);

            logger.info("[6] Selecting Batch: [{}]", batch);
            selectDropdown(batchInput, batch);

            By stockLocator = By.id("row_1_current_stock_(atp)");
            WebElement stockElement = wait.until(ExpectedConditions.visibilityOfElementLocated(stockLocator));
            String rawStockText = stockElement.getText().trim();
            logger.info("📌 Captured current stock ATP: [{}]", rawStockText);

            String[] parts = rawStockText.split("-");
            if (parts.length < 3) {
                throw new RuntimeException("Unexpected stock format received: " + rawStockText);
            }

            logger.info("[7] Entering quantity1_0: [{}]", qty1);
            enterQuantityWithRetry(By.id("quantity1_0"), qty1);

            logger.info("[8] Entering quantity3_0: [{}]", qty3);
            enterQuantityWithRetry(By.id("quantity3_0"), qty3);

            logger.info("[9] Clicking row Save button...");
            Event.robustClick(driver, rowSaveBtn);
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));

            logger.info("========== SAN DETAIL ROW ADDED SUCCESSFULLY ==========");
        } catch (Exception e) {
            logger.error("========== FAILED TO ADD SAN DETAIL ROW ==========", e);
            logger.error(
                    "Failure details -> Product=[{}], StockType=[{}], Reason=[{}], Batch=[{}], Qty1=[{}], Qty3=[{}]",
                    product,
                    stockType,
                    reasonType,
                    batch,
                    qty1,
                    qty3
            );
            throw e;
        }
    }

    // Stale-safe helper function for quantity inputs
    private void enterQuantityWithRetry(By locator, String value) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                WebElement field = wait.until(ExpectedConditions.elementToBeClickable(locator));
                field.click();
                field.clear();
                field.sendKeys(value);
                break;
            } catch (StaleElementReferenceException e) {
                if (attempt == maxAttempts) throw e;
                wait.until(ExpectedConditions.presenceOfElementLocated(locator));
            }
        }
    }

    /**
     * Executes workflow approval steps using selectDropdown() from basePage
     */
    public void executeApprovalSteps(String docType) throws InterruptedException {

        Event.robustClick(driver, sanHeaderTab);

        try {
            Event.robustClick(driver, headerCheckbox0);
        } catch (Exception e) {
            Event.robustClick(driver, genericCheckboxIcon);
        }

        // Document Type filter
        By docTypeFilter = By.id("rowfilter_pdot_desc");

        WebElement docTypeField = wait.until(
                ExpectedConditions.elementToBeClickable(docTypeFilter)
        );

        docTypeField = driver.findElement(docTypeFilter);
        docTypeField.click();
        docTypeField.clear();
        docTypeField.sendKeys(docType);

        // Document Date filter
        By docDateFilter = By.id("rowfilter_DT__TSTMDOCDATE");

        WebElement docDateField = wait.until(
                ExpectedConditions.elementToBeClickable(docDateFilter)
        );
        String targetDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        docDateField = driver.findElement(docDateFilter);
        docDateField.click();
        docDateField.clear();
        docDateField.sendKeys(targetDate);
        waitForLoader();
        Thread.sleep(1000);
        Event.robustClick(driver, By.id("row_1_document_no"));
        waitForLoader();
        Thread.sleep(1000);

        // Multi-stage approval
        forwardAndComment("Test 1");
        ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
        Event.robustClick(driver, By.id("row_1_document_no"));
        waitForLoader();
        Thread.sleep(500);
        forwardAndComment("Test 2");
        ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
        Event.robustClick(driver, By.id("row_1_document_no"));
        waitForLoader();
        Thread.sleep(500);
        forwardAndComment("Test 3");
        ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
    }

    // =========================================================
    // GRID SEARCH & HELPER UTILITIES
    // =========================================================

    public void searchDocument(String doc) {
        logger.info("🔍 Searching SAN document type: {}", doc);
        Event.robustClick(driver, gridFilterCheckbox);
        WebElement searchBox = wait.until(ExpectedConditions.elementToBeClickable(docTypeFilterInput));
        searchBox.clear();
        searchBox.sendKeys(doc);
    }

    public String getLatestDocumentNumber() {
        logger.info("📥 Fetching latest Document ID from grid...");
        By firstRowDocCell = By.xpath("//tr[contains(@class,'dx-data-row')][1]/td[1]");
        WebElement cell = wait.until(ExpectedConditions.visibilityOfElementLocated(firstRowDocCell));
        String docNum = cell.getText().trim();
        logger.info("🎯 Captured Document ID: {}", docNum);
        return docNum;
    }

    public void openDocument(String docNumber) {
        logger.info("📂 Opening SAN document: {}", docNumber);
        By rowLocator = By.xpath("//tr[contains(@class,'dx-data-row')]//td[contains(text(),'" + docNumber + "')]");
        Event.robustClick(driver, rowLocator);
    }

    private void forwardAndComment(String commentText) {
        logger.info("➡️ Dynamic forward step with comment: '{}'", commentText);
        Event.robustClick(driver, forwardBtn);
        enterWorkflowComment(commentText);
        saveWorkflow();
    }

    private void enterWorkflowComment(String commentText) {
        logger.info("💬 Entering workflow comment: '{}'", commentText);
        WebElement commentField = wait.until(ExpectedConditions.elementToBeClickable(workflowCommentInput));
        commentField.clear();
        commentField.sendKeys(commentText);
    }

    private void saveWorkflow() {
        logger.info("💾 Saving workflow step...");
        Event.robustClick(driver, workflowSaveBtn);
    }

    private void safeClearButton(By clearButtonLocator) {
        try {
            Event.robustClick(driver, clearButtonLocator);
        } catch (Exception e) {
            logger.debug("ℹ️ Clear button not present for locator: {}", clearButtonLocator);
        }
    }

    /**
     * Adds line item details, extracts current stock, and removes it if present.
     * @return true if stock was present and row was saved; false if stock was zero.
     */
    public boolean addSANDetailRowAndRemoveStock(
            String product,
            String stockType,
            String reasonType,
            String batch) throws InterruptedException {

        logger.info("========== START ADD SAN DETAIL ROW ==========");
        logger.info("Product   : [{}]", product);
        logger.info("StockType : [{}]", stockType);
        logger.info("Reason    : [{}]", reasonType);
        logger.info("Batch     : [{}]", batch);

        try {
            logger.info("[1] Clicking SAN Detail tab...");
            Thread.sleep(1000);
            Event.robustClick(driver, sanDetailTab);

            int maxRetries = 3;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    logger.info("[2] Clicking Add Row button...");
                    Event.robustClick(driver, addRowBtn);

                    logger.info("[3] Selecting Product: [{}]", product);
                    waitForLoader();
                    selectDropdown(prodCodeInput, product);

                    break; // Success

                } catch (Exception e) {
                    logger.warn("Attempt {} failed: {}", attempt, e.getMessage());

                    if (attempt == maxRetries) {
                        throw e;
                    }

                    Event.robustClick(driver, sanDetailTab);
                    Thread.sleep(1000);
                }
            }

            logger.info("[4] Selecting Stock Type: [{}]", stockType);
            selectDropdown(stockTypeInput, stockType);

            logger.info("[5] Selecting Reason Type: [{}]", reasonType);
            selectDropdown(reasonTypeInput, reasonType);

            logger.info("[6] Selecting Batch: [{}]", batch);
            selectDropdown(batchInput, batch);

            // Dynamic Stock Extraction
            By stockLocator = By.id("row_1_current_stock_(atp)");
            WebElement stockElement = wait.until(ExpectedConditions.visibilityOfElementLocated(stockLocator));
            String rawStockText = stockElement.getText().trim();
            logger.info("📌 Captured current stock ATP: [{}]", rawStockText);

            String[] parts = rawStockText.split("-");
            if (parts.length < 3) {
                throw new RuntimeException("Unexpected stock format received: " + rawStockText);
            }

            String positiveQty1 = parts[0].trim();
            String positiveQty3 = parts[2].trim();

            double val1 = Double.parseDouble(positiveQty1.isEmpty() ? "0" : positiveQty1);
            double val3 = Double.parseDouble(positiveQty3.isEmpty() ? "0" : positiveQty3);

            // Zero stock check: Exit flow directly if stock is 0
            if (val1 == 0 && val3 == 0) {
                logger.info("⚠️ Current stock is ZERO [{}]. Exiting SAN creation without saving...", rawStockText);
                return false;
            }

            String removeQty1 = "-" + positiveQty1;
            String removeQty3 = "-" + positiveQty3;
//            String removeQty1 = positiveQty1;
//         String removeQty3 =  positiveQty3;


            logger.info("[7] Entering quantity1_0: [{}]", removeQty1);
            enterQuantityWithRetry(By.id("quantity1_0"), removeQty1);

            logger.info("[8] Entering quantity3_0: [{}]", removeQty3);
            enterQuantityWithRetry(By.id("quantity3_0"), removeQty3);

            logger.info("[9] Clicking row Save button...");
            Event.robustClick(driver, rowSaveBtn);
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));

            logger.info("========== SAN DETAIL ROW ADDED SUCCESSFULLY ==========");
            return true;

        } catch (Exception e) {
            logger.error("========== FAILED TO ADD SAN DETAIL ROW ==========", e);
            throw e;
        }
    }

    /**
     * Approves the document by executing the workflow approval steps.
     * @param docType The document type to filter and approve.
     */
    public void approveDocument(String docType) throws InterruptedException {
        logger.info("APPROVAL Workflow initiated for Doc Type: [{}]", docType);
        executeApprovalSteps(docType);
    }

    /**
     * Filters the grid by today's date using the date filter input.
     */
    public void filterByTodayDate() {
        logger.info("📅 Filtering grid by today's date...");
        String targetDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        WebElement docDateField = wait.until(
                ExpectedConditions.elementToBeClickable(dateFilterInput)
        );
        docDateField.click();
        docDateField.clear();
        docDateField.sendKeys(targetDate);
        waitForLoader();
    }
}