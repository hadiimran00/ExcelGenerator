package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.example.ui.pages.OrderBookingPage;
import org.example.ui.pages.OrderDeliveryDatePage;
import org.example.ui.pages.SANPage;
import org.example.ui.pages.TransactionInquiryPage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.Map;

import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;

public class PartialReturnBudgetFlow {

    // Initialize logger for tracking execution details
    private static final Logger logger = LoggerUtil.getLogger(PartialReturnBudgetFlow.class);

    /**
     * Executes the main workflow for validating Partial Return Budget Allocation.
     */
    public static ValidationResult run(
            WebDriver driver,
            Map<String, String> scenarioData,
            String downloadDir
    ) {
        // Initialize ValidationResult object with test case summary details
        ValidationResult result = new ValidationResult(
                "SDMS-10626 Free SKU incorrectly added to budget on partial return " +
                        "when stock was initially zero and Free SKU was never given"
        );

        // Explicit wait instance for Selenium actions
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        // Instantiate Page Objects once for efficient UI navigation
        SANPage sanPage = new SANPage(driver);
        OrderBookingPage orderBookingPage = new OrderBookingPage(driver);
        OrderDeliveryDatePage deliveryDatePage = new OrderDeliveryDatePage(driver);
        TransactionInquiryPage transactionInquiryPage = new TransactionInquiryPage(driver);

        try {
            // Extract required test parameters from scenarioData (with robust defaults)
            String budgetPromoId = scenarioData.getOrDefault("budgetPromoId", "JC06-0000952");
            String fullCustomerCode = scenarioData.getOrDefault("fullCustomerCode", "C0000023667-Shahjalal Super Store");
            String fullProductName = scenarioData.getOrDefault("fullProductName", "68640058");
            String freeProductName = scenarioData.getOrDefault("freeProductName", "62732112");
            String keyColumn = scenarioData.getOrDefault("validateKeyColumn", "CHNLHIER_CODE");
            String keyValue = scenarioData.getOrDefault("validateKeyValue", "C01047");
            String targetColumn = scenarioData.getOrDefault("validateTargetColumn", "CHNLHIER_QTY_UTILIZED");

            String businessEntity = scenarioData.getOrDefault("businessEntity", "C0000000038-Sound Stock W/H");
            String docType = scenarioData.getOrDefault("docType", "Stock Adjustment Admin");
            String ginPjp = scenarioData.getOrDefault("ginPjp", "0000000362");

            // =========================================================================
            // PRE-REQUISITE : REMOVING INITIAL STOCK (STOCK ADJUSTMENT SAN)
            // =========================================================================
            logger.info("📌 PRE REQ : Navigating and Creating Stock Adjustment SAN");
            sanPage.navigateToScreen("Stock Adjustment SAN", "DYL_201045");

            // Populate SAN Header fields
            sanPage.fillSANHeader(businessEntity, docType);

            // Add detail row to clear existing stock for free product
            boolean isSanCreated = sanPage.addSANDetailRowAndRemoveStock(
                    freeProductName,
                    scenarioData.getOrDefault("stockType", "01-Sound"),
                    scenarioData.getOrDefault("reasonType", "Sound CAT"),
                    scenarioData.getOrDefault("batch", "1-1")
            );

            // Handle SAN approval workflow if SAN was created
            if (isSanCreated) {
                sanPage.executeApprovalSteps(docType);
                logger.info("✅ SAN workflow approved.");
            } else {
                logger.info("⏩ Stock was zero. Skipped approval steps since no SAN was generated.");
            }

            logger.info("✅ Stock Removal SAN Completed Successfully.");

            // =========================================================================
            // STEP 1: INITIAL BUDGET EXPORT & VALIDATION
            // =========================================================================
            logger.info("📌 STEP 1: Exporting Initial Budget Setup for Catalog: {}", budgetPromoId);
            sanPage.navigateToScreen("Budget Setup", "BUDGET_LAYOUT");
            waitForLoaderToDisappear(driver);

            // Export Excel report and check initial utilization is 0
            exportAndValidateBudget(driver, sanPage, wait, downloadDir, budgetPromoId, keyColumn, keyValue, targetColumn, "0", result);

            // =========================================================================
            // STEP 2: ORDER BOOKING
            // =========================================================================
            logger.info("📌 STEP 2: Creating Order for Customer: {}", fullCustomerCode);
            orderBookingPage.navigateToScreen("Order Booking", "ORDER_BOOKING");

            // Execute order booking action
            String orderNo = orderBookingPage.orderBooking(scenarioData);
            if (orderNo == null || orderNo.isBlank()) {
                throw new RuntimeException("Order Booking failed: Order number was not generated.");
            }
            Event.robustClick(driver, By.id("newOrder"));

            String orderNo2 = orderBookingPage.orderBooking(scenarioData);
            if (orderNo2 == null || orderNo2.isBlank()) {
                throw new RuntimeException("Order Booking failed: Second order number was not generated.");
            }

            logger.info("✅ Order Bookings completed. Order Nos: {} and {}", orderNo, orderNo2);
            scenarioData.put("orderNo", orderNo);
            scenarioData.put("orderNo2", orderNo2);

            // =========================================================================
            // STEP 2.5: DELIVERY DATE CHANGE
            // =========================================================================
            String orderPJP = scenarioData.getOrDefault("PJPNO", "0000000362");

            logger.info("📌 STEP 2.5a: Updating Delivery Date for Order: {}", orderNo);
            sanPage.navigateToScreen("Delivery Date Change", "DYL_201080");
            waitForLoaderToDisappear(driver);
            deliveryDatePage.processDeliveryDateForOrder(orderPJP, orderNo);
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            waitForLoaderToDisappear(driver);
            logger.info("✅ Delivery Date Updated Successfully for Order No: {}", orderNo);

            logger.info("📌 STEP 2.5b: Updating Delivery Date for Order: {}", orderNo2);
            sanPage.navigateToScreen("Delivery Date Change", "DYL_201080");
            waitForLoaderToDisappear(driver);
            deliveryDatePage.processDeliveryDateForOrder(orderPJP, orderNo2);
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            waitForLoaderToDisappear(driver);
            logger.info("✅ Delivery Date Updated Successfully for Order No: {}", orderNo2);

            // =========================================================================
            // STEP 3: TRANSACTION INQUIRY (INITIAL POST-ORDER CHECK)
            // =========================================================================
            logger.info("📌 STEP 3: Navigating and executing BG - Transaction Inquiry");
            executeTransactionInquiry(driver, transactionInquiryPage, scenarioData, orderNo, budgetPromoId, "Sales", result);

            // =========================================================================
            // STEP 4: BUDGET CHECK AFTER ORDER BOOKING
            // =========================================================================
            logger.info("📌 STEP 4: Exporting Budget Setup Post Order Booking for Catalog: {}", budgetPromoId);
            sanPage.navigateToScreen("Budget Setup", "BUDGET_LAYOUT");
            exportAndValidateBudget(driver, sanPage, wait, downloadDir, budgetPromoId, keyColumn, keyValue, targetColumn, "0", result);

            // =========================================================================
            // STEP 5: RE-INSERT STOCK VIA SAN
            // =========================================================================
            logger.info("📌 STEP 5: Navigating and Creating Next SAN to Re-insert Stock");
            sanPage.navigateToScreen("Stock Adjustment SAN", "DYL_201045");

            sanPage.fillSANHeader(businessEntity, docType);

            String restoreQty1 = "25";
            String restoreQty3 = "25";

            // Add stock quantity details
            sanPage.addSANDetailRow(
                    freeProductName,
                    scenarioData.getOrDefault("stockType", "01-Sound"),
                    scenarioData.getOrDefault("reasonType", "Sound CAT"),
                    scenarioData.getOrDefault("batch", "1-1"),
                    restoreQty1,
                    restoreQty3
            );

            // Execute approval for restored stock
            sanPage.executeApprovalSteps(docType);
            logger.info("✅ Re-insertion SAN Completed Successfully.");

            // =========================================================================
            // STEP 6: GOODS ISSUE NOTE (GIN) CREATION & APPROVAL
            // =========================================================================
            logger.info("📌 STEP 6: Navigating and Executing Goods Issue Note (GIN)");
            sanPage.navigateToScreen("Goods Issue Note", "GOOD_ISSUE_NODE");
            waitForLoaderToDisappear(driver);

            // Select Delivery PJP and Date
            try {
                sanPage.selectDropdown(By.id("deliveryManPjp"), ginPjp);
            } catch (Exception e) {
                sanPage.selectDropdown(By.id("deliveryManPjp"), ginPjp);
            }
            String todaysDate = java.time.LocalDate.now().toString();
            sanPage.selectDropdown(By.id("deliveryDate"), todaysDate);

            // Filter document and select order
            Event.robustClick(driver, By.id("tab_2"));
            waitForLoaderToDisappear(driver);

            Event.robustClick(driver, By.id("gridFilterCheckbox"));

            WebElement cmDocFilter = wait.until(ExpectedConditions.elementToBeClickable(By.id("rowfilter_cmDocumentNo")));
            cmDocFilter.clear();
            cmDocFilter.sendKeys(orderNo);
            waitForLoaderToDisappear(driver);
            Event.robustClick(driver, By.id("row_1_checkbox_1"));

            cmDocFilter = wait.until(ExpectedConditions.elementToBeClickable(By.id("rowfilter_cmDocumentNo")));
            cmDocFilter.clear();
            cmDocFilter.sendKeys(orderNo2);
            waitForLoaderToDisappear(driver);

            Event.robustClick(driver, By.id("row_1_checkbox_1"));
            Event.robustClick(driver, By.id("tab_3"));
            waitForLoaderToDisappear(driver);

            Event.robustClick(driver, By.id("saveallBtn"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            waitForLoaderToDisappear(driver);

            // Process First Level GIN Approval
            Event.robustClick(driver, By.id("tab_1"));
            waitForLoaderToDisappear(driver);

            By ginNoLocator = By.id("row_1_gin_no.");
            String ginNo = driver.findElement(ginNoLocator).getText().trim();
            Event.robustClick(driver, ginNoLocator);
            waitForLoaderToDisappear(driver);

            Event.robustClick(driver, By.id("forward"));
            WebElement commentsField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("comments")));
            commentsField.clear();
            commentsField.sendKeys("Approved GIN Step 1");
            Event.robustClick(driver, By.id("forwardPopUpSaveBtn"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));

            // Process Second Level GIN Approval
            Event.robustClick(driver, By.id("row_1_gin_no."));
            waitForLoaderToDisappear(driver);

            Event.robustClick(driver, By.id("forward"));
            WebElement commentsField2 = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("comments")));
            commentsField2.clear();
            commentsField2.sendKeys("Approved GIN Step 2");
            Event.robustClick(driver, By.id("forwardPopUpSaveBtn"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            logger.info("✅ GIN Creation and Approvals Completed Successfully.");

            // =========================================================================
            // STEP 6.5: FRESH SALES RETURN
            // =========================================================================
            logger.info("📌 STEP 6.5: Creating Fresh Sales Return for Order: {}", orderNo);
            sanPage.navigateToScreen("Fresh Sales Return", "FRESH_SALES_RETURN");
            waitForLoaderToDisappear(driver);

            // Search order and initiate sales return
            try {
                sanPage.selectDropdown(By.id("pjpNo"), ginPjp);
            } catch (Exception e) {
                sanPage.selectDropdown(By.id("pjpNo"), ginPjp);
            }

            Event.robustClick(driver, By.id("gridFilterCheckbox"));
            waitForLoaderToDisappear(driver);

            WebElement docFilter = wait.until(ExpectedConditions.elementToBeClickable(By.id("rowfilter_tcmm_docno")));
            docFilter.clear();
            docFilter.sendKeys(orderNo);
            waitForLoaderToDisappear(driver);

            Event.robustClick(driver, By.id("row_1_document_no"));
            waitForLoaderToDisappear(driver);

            Event.robustClick(driver, By.id("saveBtn"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            waitForLoaderToDisappear(driver);

            String FreshSalesReturnNo = driver.findElement(By.id("documentNo")).getDomProperty("value");

            logger.info("Fresh Sales Return Created successfully: {}", FreshSalesReturnNo);

            Event.robustClick(driver, By.id("tab_9"));
            waitForLoaderToDisappear(driver);
            Event.robustClick(driver, By.id("rowEditBtn_Edit_0"));

            // Enter return quantities
            WebElement qty1Input = wait.until(ExpectedConditions.elementToBeClickable(By.id("quantity1_0")));
            qty1Input.clear();
            qty1Input.sendKeys("1");

            WebElement qty3Input = wait.until(ExpectedConditions.elementToBeClickable(By.id("quantity3_0")));
            qty3Input.clear();
            qty3Input.sendKeys("10");

            // Select Return Reason
            WebElement reasonDropdown = wait.until(ExpectedConditions.elementToBeClickable(By.id("reasonType")));
            reasonDropdown.click();

            String targetReason = scenarioData.getOrDefault("returnReasonType", "Expired");
            String reasonItemXpath = String.format("//div[contains(@class,'dx-item-content') and contains(text(),'%s')]", targetReason);
            WebElement targetReasonItem = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(reasonItemXpath)));
            targetReasonItem.click();

            // Save and validate return transaction
            Event.robustClick(driver, By.id("rowEditBtn_Save_0"));
            waitForLoaderToDisappear(driver);
            Event.robustClick(driver, By.id("validationBtn"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            waitForLoaderToDisappear(driver);
            Event.robustClick(driver, By.id("saveBtn"));
            String noti = ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            waitForLoaderToDisappear(driver);

            logger.info("Notification received after saving: {}", noti);
            if (noti != null && noti.toLowerCase().contains("success")) {
                result.pass("Partial Fresh return created successfully.");
                logger.info("✅ Fresh Sales Return details updated successfully.");
            } else {
                result.fail("Failed to create Partial Fresh return. Notification received: " + noti);
                logger.error("❌ Partial Fresh return creation failed. Notification: {}", noti);
            }

            // =========================================================================
            // STEP 7 & 8: BUDGET CHECK & TRANSACTION INQUIRY
            // =========================================================================
            logger.info("📌 STEP 7: Final Budget Setup Verification");
            sanPage.navigateToScreen("Budget Setup", "BUDGET_LAYOUT");
            exportAndValidateBudget(driver, sanPage, wait, downloadDir, budgetPromoId, keyColumn, keyValue, targetColumn, "0", result);

            logger.info("📌 STEP 8: Final BG - Transaction Inquiry Verification");
            executeTransactionInquiry(driver, transactionInquiryPage, scenarioData, FreshSalesReturnNo, budgetPromoId, "Fresh Sales Return", result);

            waitForLoaderToDisappear(driver);

            // Log final passed status message
            result.pass(
                    "Free SKU was not added or reallocated to the budget after Fresh Return, as it was never issued to the customer."
            );

            // =========================================================================
            // DELIVER ORDER
            // =========================================================================
            sanPage.navigateToScreen("Cashmemo Status", "CASHMEMO-STATUS");
            sanPage.selectDropdown(By.id("dsrType"), ginPjp);
            sanPage.selectDropdown(By.id("ginno"), ginNo);
            Event.robustClick(driver, By.id("gridFilterCheckbox"));
            cmDocFilter = wait.until(ExpectedConditions.elementToBeClickable(By.id("rowfilter_documentNo")));
            cmDocFilter.clear();
            cmDocFilter.sendKeys(orderNo2);
            Event.robustClick(driver, By.id("row_1_checkbox_1"));
            Event.robustClick(driver, By.id("saveallBtn"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));

            // =========================================================================
            // SALES RETURN
            // =========================================================================
            sanPage.navigateToScreen("Sales Return", "DYL_201801");
            sanPage.selectDropdown(By.id("pjpNo"), ginPjp);
            Event.robustClick(driver, By.id("gridFilterCheckbox"));
            cmDocFilter = wait.until(ExpectedConditions.elementToBeClickable(By.id("rowfilter_tcmm_docno")));
            cmDocFilter.clear();
            cmDocFilter.sendKeys(orderNo2);
            waitForLoaderToDisappear(driver);
            Thread.sleep(500);
            Event.robustClick(driver, By.id("row_1_document_no"));
            Event.robustClick(driver, By.id("saveBtn"));
            String SalesReturnNo = driver.findElement(By.id("documentNo")).getDomProperty("value");

            waitForLoaderToDisappear(driver);
            Event.robustClick(driver, By.id("tab_9"));
            waitForLoaderToDisappear(driver);
            Event.robustClick(driver, By.id("rowEditBtn_Edit_0"));

            WebElement salesReturnQTY = wait.until(ExpectedConditions.elementToBeClickable(By.id("quantity3_0")));
            salesReturnQTY.clear();
            salesReturnQTY.sendKeys("30");

            // Select Return Reason
            reasonDropdown = wait.until(ExpectedConditions.elementToBeClickable(By.id("reasonType")));
            reasonDropdown.click();

            targetReason = scenarioData.getOrDefault("SalesReturnReasonType", "No Cash");
            reasonItemXpath = String.format("//div[contains(@class,'dx-item-content') and contains(text(),'%s')]", targetReason);
            targetReasonItem = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(reasonItemXpath)));
            targetReasonItem.click();

            // Save and validate return transaction
            Event.robustClick(driver, By.id("rowEditBtn_Save_0"));
            waitForLoaderToDisappear(driver);
            Event.robustClick(driver, By.id("validationBtn"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            waitForLoaderToDisappear(driver);
            Event.robustClick(driver, By.id("saveBtn"));
            noti = ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            waitForLoaderToDisappear(driver);

            logger.info("Notification received after saving: {}", noti);
            if (noti != null && noti.toLowerCase().contains("success")) {
                result.pass("Sales return created successfully.");
                logger.info("✅ Sales Return details updated successfully.");
            } else {
                result.fail("Failed to create Sales return. Notification received: " + noti);
                logger.error("❌ Sales return creation failed. Notification: {}", noti);
            }

            // Approve SR Level 1
            Event.robustClick(driver, By.id("forward"));
            commentsField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("comments")));
            commentsField.clear();
            commentsField.sendKeys("Approved SR Step 1");
            Event.robustClick(driver, By.id("forwardPopUpSaveBtn"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));

            waitForLoaderToDisappear(driver);

            // Approve SR Level 2
            Event.robustClick(driver, By.id("forward"));
            commentsField2 = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("comments")));
            commentsField2.clear();
            commentsField2.sendKeys("Approved SR Step 2");

            // =========================================================================
            // PICK SALES RETURN
            // =========================================================================
            sanPage.navigateToScreen("Sales Return", "SALESRETURN-STATUSCHANGE");
            sanPage.selectDropdown(By.id("pjpNo"), ginPjp);
            Event.robustClick(driver, By.id("gridFilterCheckbox"));
            sanPage.selectDropdown(By.id("ginNumber"), ginNo);

            Event.robustClick(driver, By.id("gridFilterCheckbox"));
            cmDocFilter = wait.until(ExpectedConditions.elementToBeClickable(By.id("rowfilter_tcmm_docno")));
            cmDocFilter.clear();
            cmDocFilter.sendKeys(orderNo2);
            Event.robustClick(driver, By.id("row_1_document_no"));
            Event.robustClick(driver, By.id("tab_2"));
            Event.robustClick(driver, By.id("validation"));
            Event.robustClick(driver, By.id("savesalePick"));
            noti = ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));

            logger.info("Notification received after saving: {}", noti);
            if (noti != null && noti.toLowerCase().contains("success")) {
                logger.info("✅ Sales Return details saved and picked successfully.");
            } else {
                result.fail("Failed to pick Sales return. Notification received: " + noti);
                logger.error("❌ Sales return picking failed. Notification: {}", noti);
            }


            // =========================================================================
            // STEP 9: BUDGET CHECK & TRANSACTION INQUIRY
            // =========================================================================
            logger.info("📌 STEP 9: Final Budget Setup Verification");
            sanPage.navigateToScreen("Budget Setup", "BUDGET_LAYOUT");
            exportAndValidateBudget(driver, sanPage, wait, downloadDir, budgetPromoId, keyColumn, keyValue, targetColumn, "0", result);

            logger.info("📌 STEP 9: Final BG - Transaction Inquiry Verification");
            executeTransactionInquiry(driver, transactionInquiryPage, scenarioData, SalesReturnNo, budgetPromoId, "Sales Return", result);

            waitForLoaderToDisappear(driver);

            // Log final passed status message
            result.pass(
                    "Free SKU was not added or reallocated to the budget after Sales Return, as it was never issued to the customer."
            );


        } catch (Exception e) {
            // Log flow execution error and fail result
            logger.error("❌ Partial Return Budget Allocation flow failed: {}", e.getMessage(), e);
            result.fail("Flow execution encountered an error: " + e.getMessage());
        }

        return result;
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
            ValidationResult result
    ) {
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
        Event.robustClick(driver, By.xpath("(//td[contains(@id,'row_1_code')])[1]"));
        waitForLoaderToDisappear(driver);
        Event.robustClick(driver, By.xpath("//span[text()='Bulk Promo Allocation']"));
        waitForLoaderToDisappear(driver);
        Event.robustClick(driver, By.id("tab_group_2"));
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
            String budgetPromoId, String docType,
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