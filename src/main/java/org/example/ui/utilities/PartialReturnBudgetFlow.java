package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.example.ui.pages.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.example.ui.pages.basePage.requireScenarioValue;
import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;

public class PartialReturnBudgetFlow {

    private static final Logger logger = LoggerUtil.getLogger(PartialReturnBudgetFlow.class);

    public static ValidationResult run(
            WebDriver driver,
            Map<String, String> scenarioData,
            String downloadDir
    ) throws InterruptedException {
        ValidationResult result = new ValidationResult(
                "SDMS-10626 Free SKU incorrectly added to budget on partial return " +
                        "when stock was initially zero and Free SKU was never given"
        );

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        try {
            validateInputs(scenarioData);

            String budgetPromoId = scenarioData.get("budgetPromoId");
            String fullCustomerCode = scenarioData.get("fullCustomerCode");
            String freeProductName = scenarioData.get("freeProductName");
            String keyColumn = scenarioData.get("validateKeyColumn");
            String keyValue = scenarioData.get("validateKeyValue");
            String targetColumn = scenarioData.get("validateTargetColumn");
            String businessEntity = scenarioData.get("businessEntity");
            String docType = scenarioData.get("docType");
            String ginPjp = scenarioData.get("ginPjp");
            String targetReason = scenarioData.get("returnReasonType");
            String budgetTabId = scenarioData.get("BudgetTabID");
            String orderPJP = scenarioData.getOrDefault("PJPNO", "0000000362");

            // Page Instantiations
            SANPage sanPage = new SANPage(driver);
            OrderBookingPage orderBookingPage = new OrderBookingPage(driver);
            OrderDeliveryDatePage deliveryDatePage = new OrderDeliveryDatePage(driver);
            TransactionInquiryPage transactionInquiryPage = new TransactionInquiryPage(driver);
            GoodsIssueNotePage ginPage = new GoodsIssueNotePage(driver);
            FreshSalesReturnPage freshReturnPage = new FreshSalesReturnPage(driver);
            CashmemoStatusPage cashmemoStatusPage = new CashmemoStatusPage(driver);
            SalesReturnPage salesReturnPage = new SalesReturnPage(driver);

            // PRE-REQ: STOCK ADJUSTMENT
            sanPage.navigateToScreen("Stock Adjustment SAN", "DYL_201045");
            sanPage.fillSANHeader(businessEntity, docType);
            if (sanPage.addSANDetailRowAndRemoveStock(freeProductName, scenarioData.getOrDefault("stockType", "01-Sound"), scenarioData.getOrDefault("reasonType", "Sound CAT"), scenarioData.getOrDefault("batch", "1-1"))) {
                sanPage.executeApprovalSteps(docType);
            }

            // STEP 1: INITIAL BUDGET EXPORT
            sanPage.navigateToScreen("Budget Setup", "BUDGET_LAYOUT");
            waitForLoaderToDisappear(driver);
            exportAndValidateBudget(driver, sanPage, wait, downloadDir, budgetPromoId, keyColumn, keyValue, targetColumn, "0", result, budgetTabId);

            // STEP 2: ORDER BOOKING
            orderBookingPage.navigateToScreen("Order Booking", "ORDER_BOOKING");
            String orderNo = createOrder(orderBookingPage, scenarioData, "First");
            Event.robustClick(driver, By.id("newOrder"));
            String orderNo2 = createOrder(orderBookingPage, scenarioData, "Second");
            scenarioData.put("orderNo", orderNo);
            scenarioData.put("orderNo2", orderNo2);

            // STEP 2.5: DELIVERY DATE
            updateDeliveryDate(driver, sanPage, deliveryDatePage, result, orderPJP, orderNo);
            updateDeliveryDate(driver, sanPage, deliveryDatePage, result, orderPJP, orderNo2);

            // STEP 3: TRANSACTION INQUIRY
            executeTransactionInquiry(driver, transactionInquiryPage, scenarioData, orderNo, budgetPromoId, "Sales", result);

            // STEP 4: BUDGET CHECK
            sanPage.navigateToScreen("Budget Setup", "BUDGET_LAYOUT");
            exportAndValidateBudget(driver, sanPage, wait, downloadDir, budgetPromoId, keyColumn, keyValue, targetColumn, "0", result, budgetTabId);

            // STEP 5: RE-INSERT STOCK
            sanPage.navigateToScreen("Stock Adjustment SAN", "DYL_201045");
            sanPage.fillSANHeader(businessEntity, docType);
            sanPage.addSANDetailRow(freeProductName, scenarioData.getOrDefault("stockType", "01-Sound"), scenarioData.getOrDefault("reasonType", "Sound CAT"), scenarioData.getOrDefault("batch", "1-1"), "25", "25");
            sanPage.executeApprovalSteps(docType);

            // STEP 6: GOODS ISSUE NOTE (GIN)
            ginPage.navigateToScreen("Goods Issue Note", "GOOD_ISSUE_NODE");
            waitForLoaderToDisappear(driver);
            ginPage.selectDeliveryPjpAndDate(ginPjp, LocalDate.now().toString());

            ginPage.click(By.id("tab_2"));
            ginPage.click(By.id("gridFilterCheckbox"));
            ginPage.filterAndSelectOrder(orderNo, By.id("checkbox-1"));
            ginPage.filterAndSelectOrder(orderNo2, By.id("row_1_checkbox_1"));
            ginPage.submitGIN(result);

            String ginNo = ginPage.getFirstRowGinNoAndOpen();
            ginPage.approveStep("Approved GIN Step 1", result);
            ginPage.getFirstRowGinNoAndOpen();
            ginPage.approveStep("Approved GIN Step 2", result);

            // STEP 6.5: FRESH SALES RETURN
            freshReturnPage.navigateToScreen("Fresh Sales Return", "FRESH_SALES_RETURN");
            waitForLoaderToDisappear(driver);
            String freshSalesReturnNo = freshReturnPage.createReturnHeader(ginPjp, orderNo, result);
            freshReturnPage.enterReturnDetails("1", "10", targetReason);
            freshReturnPage.validateAndSave(result);

            // STEP 7 & 8: BUDGET CHECK
            sanPage.navigateToScreen("Budget Setup", "BUDGET_LAYOUT");
            exportAndValidateBudget(driver, sanPage, wait, downloadDir, budgetPromoId, keyColumn, keyValue, targetColumn, "0", result, budgetTabId);
            executeTransactionInquiry(driver, transactionInquiryPage, scenarioData, freshSalesReturnNo, budgetPromoId, "Fresh Sales Return", result);

            // DELIVER ORDER
            cashmemoStatusPage.navigateToScreen("Cashmemo Status", "CASHMEMO-STATUS");
            cashmemoStatusPage.deliverOrder(ginPjp, ginNo, orderNo2, result);

            // SALES RETURN
            salesReturnPage.navigateToScreen("Sales Return", "DYL_201801");
            waitForLoaderToDisappear(driver);
            String salesReturnNo = salesReturnPage.createReturnHeader(ginPjp, orderNo2, result);
            salesReturnPage.enterReturnQuantityAndReason("30", scenarioData.get("SalesReturnReasonType"));
            salesReturnPage.validateAndSave(result);
            salesReturnPage.approveStep("Approved SR Step 1", result);
            salesReturnPage.approveStep("Approved SR Step 2", result);

            // PICK SALES RETURN
            salesReturnPage.navigateToScreen("Sales Return", "SALESRETURN-STATUSCHANGE");
            waitForLoaderToDisappear(driver);
            salesReturnPage.pickSalesReturn(ginPjp, ginNo, salesReturnNo, result);

            // STEP 9: FINAL BUDGET CHECK
            sanPage.navigateToScreen("Budget Setup", "BUDGET_LAYOUT");
            exportAndValidateBudget(driver, sanPage, wait, downloadDir, budgetPromoId, keyColumn, keyValue, targetColumn, "0", result, budgetTabId);
            executeTransactionInquiry(driver, transactionInquiryPage, scenarioData, salesReturnNo, budgetPromoId, "Sales Return", result);

            result.pass("Free SKU was not added or reallocated to the budget after Sales Return, as it was never issued to the customer.");

        } catch (Exception e) {
            logger.error("❌ Partial Return Budget Allocation flow failed: {}", e.getMessage(), e);
            result.fail("Flow execution encountered an error: " + e.getMessage());
        }

        return result;
    }

    private static void validateInputs(Map<String, String> data) {
        String[] keys = {"budgetPromoId", "fullCustomerCode", "productCode", "freeProductName", "validateKeyColumn", "validateKeyValue", "validateTargetColumn", "businessEntity", "docType", "ginPjp", "BudgetTabID", "returnReasonType"};
        for (String key : keys) {
            requireScenarioValue(data, key, data.get(key));
        }
    }

    private static String createOrder(OrderBookingPage page, Map<String, String> data, String label) throws InterruptedException {
        String orderNo = page.orderBooking(data);
        if (orderNo == null || orderNo.isBlank()) {
            throw new RuntimeException(label + " Order Booking failed: Order number was not generated.");
        }

        return orderNo;
    }

    private static void updateDeliveryDate(WebDriver driver, SANPage sanPage, OrderDeliveryDatePage deliveryDatePage, ValidationResult result, String orderPJP, String orderNo) {
        sanPage.navigateToScreen("Delivery Date Change", "DYL_201080");
        waitForLoaderToDisappear(driver);
        deliveryDatePage.processDeliveryDateForOrder(orderPJP, orderNo);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoaderToDisappear(driver);
    }

    /**
     * Helper method to export Budget Setup data and validate cell values via Excel.
     */
    private static void exportAndValidateBudget(
            WebDriver driver,
            SANPage sanPage,
            WebDriverWait wait,
            String downloadDir,
            String targetCatalogId,
            String keyColumn,
            String keyValue,
            String targetColumn,
            String expectedValue,
            ValidationResult result,
            String budgetTabID) {
        waitForLoaderToDisappear(driver);

        // Click grid filter checkbox
        try {
            Event.robustClick(driver, By.id("checkbox-11"));
        } catch (Exception e) {
            Event.robustClick(driver, By.id("checkbox-11"));
        }

        // Apply target catalog ID filter
        WebElement catalogFilter = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[contains(@id,'targetCatalogId') or contains(@class,'rowfilter')]")));
        catalogFilter.clear();
        catalogFilter.sendKeys(targetCatalogId);
        waitForLoaderToDisappear(driver);

        // Open Bulk Promo Allocation and export file
        Event.robustClick(driver, By.xpath(
                "//*[@id='row_1_budget_code' or @id='row_1_code']"
        ));
        waitForLoaderToDisappear(driver);
        Event.robustClick(driver, By.xpath("//span[text()='Bulk Promo Allocation']"));
        waitForLoaderToDisappear(driver);
        Event.robustClick(driver, By.id(budgetTabID));
        waitForLoaderToDisappear(driver);
        Event.robustClick(driver, By.xpath("//button[contains(text(),'Export to Excel')]"));
        waitForLoaderToDisappear(driver);

        // Locate downloaded Excel file
        File downloadedFile = new File(FileManager.getLatestDownloadedFile(downloadDir));

        // Parse target cell value from Excel file
        String actualQtyUtilized = ExcelValidator.getCellValueByRowKey(
                downloadedFile,
                keyColumn,
                keyValue,
                targetColumn
        );

        // Assert cell value matches expected criteria
        if (!expectedValue.equals(actualQtyUtilized)) {
            result.fail(
                    " Excel Budget Validation Failed, Expected " + targetColumn + " to be " + expectedValue
                            + " for code [" + keyValue + "] but found [" + actualQtyUtilized + "]"
            );
            logger.info(
                    " Excel Budget Validation Failed : {} is {} for {}",
                    targetColumn,
                    expectedValue,
                    keyValue
            );
        } else {
            result.pass(
                    " Excel Budget Validation Passed: " + targetColumn + " is " + expectedValue
                            + " for " + keyValue
            );

            logger.info(
                    "Excel Validation Passed: {} is {} for {}",
                    targetColumn,
                    expectedValue,
                    keyValue
            );
        }
    }

    /**
     * Helper method encapsulating Transaction Inquiry interactions using TransactionInquiryPage.
     */
    private static void executeTransactionInquiry(
            WebDriver driver,
            TransactionInquiryPage transactionInquiryPage,
            Map<String, String> scenarioData,
            String orderNo,
            String budgetPromoId,
            String docType,
            ValidationResult result
    ) {
        String pjpNo = scenarioData.get("PJPNO");

        // Navigate to Transaction Inquiry
        transactionInquiryPage.navigateToScreen("BG - Transaction Inquiry", "DYL_BG1016");
        waitForLoaderToDisappear(driver);

        // Apply filters
        transactionInquiryPage.selectDocumentType(docType);
        transactionInquiryPage.selectPjp(pjpNo);
        transactionInquiryPage.searchAndSelectDocument(orderNo);
        transactionInquiryPage.searchPromotion(budgetPromoId);

        // Verify promotion ID match
        logger.info("📌 Verifying promotion search result");
        String actualPromotion = transactionInquiryPage.getFirstRowPromotionId();
        logger.info("📌 Search result promotion: {}", actualPromotion);

        if (!budgetPromoId.equals(actualPromotion)) {
            result.fail(
                    " Expected promotion [" + budgetPromoId + "] but found [" + actualPromotion + "]"
            );
        } else {
            result.pass(
                    " Free SKU Promotion [" + actualPromotion + "] appearing in Cashmemo Offering"
            );
            logger.info("✅ Promotion found successfully: {}", actualPromotion);
        }

        // Verify allocated quantity is zero
        String actualAllocatedQuantity = transactionInquiryPage.getFirstRowAllocatedQuantity();
        logger.info("📌 Allocated quantity for [{}]: {}", actualPromotion, actualAllocatedQuantity);

        if (!"0".equals(actualAllocatedQuantity)) {
            result.fail(
                    " Allocated quantity validation failed for promotion [" + budgetPromoId
                            + "]. Expected [0] but found [" + actualAllocatedQuantity + "]"
            );
        } else {
            result.pass(
                    " Allocated quantity validation passed for promotion [" + budgetPromoId
                            + "]. Expected [0] found [" + actualAllocatedQuantity + "]"
            );
            logger.info("✅ Allocated quantity is 0 for promotion: {}", budgetPromoId);
        }

        logger.info("✅ Transaction Inquiry steps completed");
    }
}