package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.example.ui.pages.OrderBookingPage;
import org.example.ui.pages.OrderDeliveryDatePage;
import org.example.ui.pages.SANPage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.StaleElementReferenceException;

import java.io.File;
import java.time.Duration;
import java.util.Map;

import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;

public class PartialReturnBudgetFlow {
    private static final Logger logger = LoggerUtil.getLogger(PartialReturnBudgetFlow.class);

    public static ValidationResult run(
            WebDriver driver,
            Map<String, String> scenarioData,
            String downloadDir
    ) {
        ValidationResult result = new ValidationResult("Partial Return Budget Allocation Flow");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        // Instantiate Page Objects once for efficiency
        SANPage sanPage = new SANPage(driver);
        OrderBookingPage orderBookingPage = new OrderBookingPage(driver);
        OrderDeliveryDatePage deliveryDatePage = new OrderDeliveryDatePage(driver);

        try {
            // Extract parameters from scenarioData (with fallbacks)
            String budgetPromoId = scenarioData.getOrDefault("budgetPromoId", "JC06-0000952");
            String fullCustomerCode = scenarioData.getOrDefault("fullCustomerCode", "C0000023667-Shahjalal Super Store");
            String fullProductName = scenarioData.getOrDefault("fullProductName", "68640058");
            String freeProductName = scenarioData.getOrDefault("freeProductName", "62732112");
            String returnQty = scenarioData.getOrDefault("returnQty", "15");
            String keyColumn = scenarioData.getOrDefault("validateKeyColumn", "CHNLHIER_CODE");
            String keyValue = scenarioData.getOrDefault("validateKeyValue", "C01047");
            String targetColumn = scenarioData.getOrDefault("validateTargetColumn", "CHNLHIER_QTY_UTILIZED");

            String businessEntity = scenarioData.getOrDefault("businessEntity", "C0000000038-Sound Stock W/H");
            String docType = scenarioData.getOrDefault("docType", "Stock Adjustment Admin");
            String ginPjp = scenarioData.getOrDefault("ginPjp", "0000000362");

            // ==========================================
            // PRE REQ : REMOVING STOCK (STOCK ADJUSTMENT SAN)
            // ==========================================

            logger.info("📌 PRE REQ : Navigating and Creating Stock Adjustment SAN");
            sanPage.navigateToScreen("Stock Adjustment SAN", "DYL_201045");

            sanPage.fillSANHeader(businessEntity, docType);

            // Dynamically populates dropdowns, reads stock ATP ("5356-0-20"), enters negative stock, and returns boolean
            boolean isSanCreated = sanPage.addSANDetailRowAndRemoveStock(
                    freeProductName,
                    scenarioData.getOrDefault("stockType", "01-Sound"),
                    scenarioData.getOrDefault("reasonType", "Sound CAT"),
                    scenarioData.getOrDefault("batch", "1-1")
            );
            if (isSanCreated) {
                sanPage.executeApprovalSteps(docType);
                logger.info("✅ SAN workflow approved.");
            } else {
                logger.info("⏩ Stock was zero. Skipped approval steps since no SAN was generated.");
            }

            logger.info("✅ Stock Removal SAN Completed Successfully.");

            // ==========================================
            // STEP 1: INITIAL BUDGET EXPORT & VALIDATION
            // ==========================================
            logger.info("📌 STEP 1: Exporting Initial Budget Setup for Catalog: {}", budgetPromoId);
            sanPage.navigateToScreen("Budget Setup", "BUDGET_LAYOUT");
            waitForLoaderToDisappear(driver);
            exportAndValidateBudget(driver, sanPage, wait, downloadDir, budgetPromoId, keyColumn, keyValue, targetColumn, "0", result);

            // ==========================================
            // STEP 2: ORDER BOOKING
            // ==========================================
            logger.info("📌 STEP 2: Creating Order for Customer: {}", fullCustomerCode);
            orderBookingPage.navigateToScreen("Order Booking", "ORDER_BOOKING");

            String orderNo = orderBookingPage.orderBooking(scenarioData);
            if (orderNo == null || orderNo.isBlank()) {
                throw new RuntimeException("Order Booking failed: Order number was not generated.");
            }

            logger.info("✅ Order Booking completed. Generated Order No: {}", orderNo);
            scenarioData.put("orderNo", orderNo);
            scenarioData.put("orderNumber", orderNo);

            // ==========================================
            // STEP 2.5: DELIVERY DATE CHANGE
            // ==========================================
            logger.info("📌 STEP 2.5: Updating Delivery Date for Order: {}", orderNo);
            sanPage.navigateToScreen("Delivery Date Change", "DYL_201080");
            waitForLoaderToDisappear(driver);

            String orderPJP = scenarioData.getOrDefault("PJPNO", "0000000362");
            deliveryDatePage.selectDropdown(By.id("DDL__EPJPPJPNODAILY"), orderPJP);
            deliveryDatePage.searchOrder("rowfilter_DOCNO", orderNo);
            deliveryDatePage.selectOrder(orderNo);
            deliveryDatePage.click(By.id("DYP.G......ProcessButton"));
            deliveryDatePage.acceptAlertIfPresent(driver);
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            waitForLoaderToDisappear(driver);
            logger.info("✅ Delivery Date Updated Successfully for Order No: {}", orderNo);

            // ==========================================
            // STEP 3: TRANSACTION INQUIRY
            // ==========================================
            logger.info("📌 STEP 3: Navigating and executing BG - Transaction Inquiry");
            executeTransactionInquiry(driver, wait, sanPage, scenarioData, orderNo, budgetPromoId, result);

            // ==========================================
            // STEP 4: BUDGET CHECK AFTER ORDER BOOKING
            // ==========================================
            logger.info("📌 STEP 4: Exporting Budget Setup Post Order Booking for Catalog: {}", budgetPromoId);
            sanPage.navigateToScreen("Budget Setup", "BUDGET_LAYOUT");
            exportAndValidateBudget(driver, sanPage, wait, downloadDir, budgetPromoId, keyColumn, keyValue, targetColumn, "0", result);

            // ==========================================
            // RE-INSERT STOCK IN NEXT SAN
            // ==========================================
            logger.info("📌 Navigating and Creating Next SAN to Re-insert Stock");
            sanPage.navigateToScreen("Stock Adjustment SAN", "DYL_201045");

            sanPage.fillSANHeader(businessEntity, docType);

            String restoreQty1 = "25";
            String restoreQty3 = "25";

            // Execute Next SAN (Re-insertion)
            sanPage.addSANDetailRow(
                    freeProductName,
                    scenarioData.getOrDefault("stockType", "01-Sound"),
                    scenarioData.getOrDefault("reasonType", "Sound CAT"),
                    scenarioData.getOrDefault("batch", "1-1"),
                    restoreQty1,
                    restoreQty3
            );

            sanPage.executeApprovalSteps(docType);
            logger.info("✅ Re-insertion SAN Completed Successfully.");

            // ==========================================
            // STEP 6: GOODS ISSUE NOTE (GIN) CREATION & APPROVAL
            // ==========================================
            logger.info("📌 STEP 6: Navigating and Executing Goods Issue Note (GIN)");
            sanPage.navigateToScreen("Goods Issue Note", "GOOD_ISSUE_NODE");
            waitForLoaderToDisappear(driver);

            // 1. Select Delivery Man / PJP & Delivery Date
            try {
                sanPage.selectDropdown(By.id("deliveryManPjp"), ginPjp);
            } catch (Exception e) {
                sanPage.selectDropdown(By.id("deliveryManPjp"), ginPjp);
            }
            String todaysDate = java.time.LocalDate.now().toString();
            sanPage.selectDropdown(By.id("deliveryDate"), todaysDate);

            // 2. Load Cash Memo Selection
            Event.robustClick(driver, By.id("tab_2"));
            waitForLoaderToDisappear(driver);

            Event.robustClick(driver, By.id("gridFilterCheckbox"));

            WebElement cmDocFilter = wait.until(ExpectedConditions.elementToBeClickable(By.id("rowfilter_cmDocumentNo")));
            cmDocFilter.clear();
            cmDocFilter.sendKeys(orderNo);
            waitForLoaderToDisappear(driver);

            Event.robustClick(driver, By.id("checkbox-1"));
            Event.robustClick(driver, By.id("tab_3"));
            waitForLoaderToDisappear(driver);

            // 3. Edit Quantity & Details
            Event.robustClick(driver, By.id("row_1_actual_pc"));
            waitForLoaderToDisappear(driver);

            By actualQty = By.id("actualQty3_0");

            try {
                WebElement spinButton = wait.until(
                        ExpectedConditions.elementToBeClickable(actualQty)
                );

                spinButton.clear();
                spinButton.sendKeys("10");

            } catch (StaleElementReferenceException e) {
                logger.warn("⚠️ actualQty3_0 became stale. Re-finding element and retrying...");

                WebElement spinButton = wait.until(
                        ExpectedConditions.elementToBeClickable(actualQty)
                );

                spinButton.clear();
                spinButton.sendKeys("10");
                logger.warn("⚠️ actualQty3_0 found");
            }

            // DevExtreme Grid Cell: Loss Reason Dropdown Handling
            WebElement gridCell = wait.until(ExpectedConditions.elementToBeClickable(By.id("row_1_loss_reason")));
            gridCell.click();

            WebElement lossReasonInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("lossReason_0")));
            lossReasonInput.click();

            String targetValue = scenarioData.getOrDefault("lossReason", "Stock Out");
            String itemXpath = String.format("//div[contains(@class,'dx-item-content') and contains(text(),'%s')]", targetValue);
            WebElement targetItem = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(itemXpath)));
            targetItem.click();

            Event.robustClick(driver, By.id("saveallBtn"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            waitForLoaderToDisappear(driver);

            // 4. Workflow Forward Approval 1 & 2
            Event.robustClick(driver, By.id("tab_1"));
            waitForLoaderToDisappear(driver);

            Event.robustClick(driver, By.id("row_1_gin_no."));
            waitForLoaderToDisappear(driver);

            Event.robustClick(driver, By.id("forward"));
            WebElement commentsField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("comments")));
            commentsField.clear();
            commentsField.sendKeys("Approved GIN Step 1");
            Event.robustClick(driver, By.id("forwardPopUpSaveBtn"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));

            Event.robustClick(driver, By.id("row_1_gin_no."));
            waitForLoaderToDisappear(driver);

            Event.robustClick(driver, By.id("forward"));
            WebElement commentsField2 = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("comments")));
            commentsField2.clear();
            commentsField2.sendKeys("Approved GIN Step 2");
            Event.robustClick(driver, By.id("forwardPopUpSaveBtn"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            logger.info("✅ GIN Creation and Approvals Completed Successfully.");

            // ==========================================
            // STEP 6.5: FRESH SALES RETURN
            // ==========================================
            logger.info("📌 STEP 6.5: Creating Fresh Sales Return for Order: {}", orderNo);
            sanPage.navigateToScreen("Fresh Sales Return", "FRESH_SALES_RETURN");
            waitForLoaderToDisappear(driver);

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

            Event.robustClick(driver, By.id("tab_11"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            waitForLoaderToDisappear(driver);
            Event.robustClick(driver, By.id("tab_9"));
            waitForLoaderToDisappear(driver);
            Event.robustClick(driver, By.id("rowEditBtn_Edit_0"));

            WebElement qty1Input = wait.until(ExpectedConditions.elementToBeClickable(By.id("quantity1_0")));
            qty1Input.clear();
            qty1Input.sendKeys("1");

            WebElement qty3Input = wait.until(ExpectedConditions.elementToBeClickable(By.id("quantity3_0")));
            qty3Input.clear();
            qty3Input.sendKeys("10");

            // DevExtreme Dropdown Handling for Fresh Sales Return Reason (same as GIN loss_reason)
            WebElement reasonDropdown = wait.until(ExpectedConditions.elementToBeClickable(By.id("reasonType")));
            reasonDropdown.click();

            String targetReason = scenarioData.getOrDefault("returnReasonType", "Expired");
            String reasonItemXpath = String.format("//div[contains(@class,'dx-item-content') and contains(text(),'%s')]", targetReason);
            WebElement targetReasonItem = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(reasonItemXpath)));
            targetReasonItem.click();

            Event.robustClick(driver, By.id("rowEditBtn_Save_0"));
            waitForLoaderToDisappear(driver);
            Event.robustClick(driver, By.id("validationBtn"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            waitForLoaderToDisappear(driver);
            Event.robustClick(driver, By.id("saveBtn"));
            ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
            waitForLoaderToDisappear(driver);
            logger.info("✅ Fresh Sales Return details updated successfully.");

            // ==========================================
            // STEP 7: FINAL BUDGET CHECK & TRANSACTION INQUIRY
            // ==========================================
            logger.info("📌 STEP 7: Final Budget Setup Verification");
            sanPage.navigateToScreen("Budget Setup", "BUDGET_LAYOUT");
            exportAndValidateBudget(driver, sanPage, wait, downloadDir, budgetPromoId, keyColumn, keyValue, targetColumn, returnQty, result);

            logger.info("📌 STEP 8: Final BG - Transaction Inquiry Verification");
            executeTransactionInquiry(driver, wait, sanPage, scenarioData, "BG26000000627", budgetPromoId, result);

        } catch (Exception e) {
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
        Event.robustClick(driver, By.id("checkbox-11"));

        WebElement catalogFilter = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[contains(@id,'targetCatalogId') or contains(@class,'rowfilter')]")));
        catalogFilter.clear();
        catalogFilter.sendKeys(targetCatalogId);
        waitForLoaderToDisappear(driver);

        Event.robustClick(driver, By.xpath("(//td[contains(@id,'row_1_code')])[1]"));
        waitForLoaderToDisappear(driver);
        Event.robustClick(driver, By.xpath("//span[text()='Bulk Promo Allocation']"));
        waitForLoaderToDisappear(driver);
        Event.robustClick(driver, By.id("tab_group_2"));
        waitForLoaderToDisappear(driver);
        Event.robustClick(driver, By.xpath("//button[contains(text(),'Export to Excel')]"));
        waitForLoaderToDisappear(driver);

        File downloadedFile = new File(FileManager.getLatestDownloadedFile(downloadDir));

        String actualQtyUtilized = ExcelValidator.getCellValueByRowKey(
                downloadedFile,
                keyColumn,
                keyValue,
                targetColumn
        );

        if (!expectedValue.equals(actualQtyUtilized)) {
            result.fail(
                    "Expected " + targetColumn + " to be " + expectedValue
                            + " for code [" + keyValue + "] but found [" + actualQtyUtilized + "]"
            );
            logger.info(
                    "Excel Validation Failed : {} is {} for {}",
                    targetColumn,
                    expectedValue,
                    keyValue
            );
        } else {
            result.pass(
                    "Excel Validation Passed: " + targetColumn + " is " + expectedValue
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
     * Helper method encapsulating Transaction Inquiry interactions.
     */
    private static void executeTransactionInquiry(
            WebDriver driver,
            TransactionInquiryPage transactionInquiryPage,
            Map<String, String> scenarioData,
            String orderNo,
            String budgetPromoId,
            ValidationResult result
    ) {
        String pjpNo = scenarioData.get("PJPNO");

        transactionInquiryPage.navigateToScreen("BG - Transaction Inquiry", "DYL_BG1016");
        waitForLoaderToDisappear(driver);

        logger.info("📌 Selecting Document Type: Sales");
        sanPage.selectDropdown(By.id("DDL__PDOTDOCMTYPEPARENT"), "Sales");

        logger.info("📌 Selecting PJP: {}", PJPNO);
        sanPage.selectDropdown(By.id("DDL__EPJPPJPNODAILY"), PJPNO);

        logger.info("📌 Entering document number: {}", orderNo);
        Event.robustClick(driver, By.id("gridFilterCheckbox"));
        WebElement documentNoFilter = wait.until(ExpectedConditions.elementToBeClickable(By.id("rowfilter_TXT__TCMMDOCNO")));
        documentNoFilter.clear();
        documentNoFilter.sendKeys(orderNo);
        waitForLoaderToDisappear(driver);

        logger.info("📌 Selecting outlet");
        Event.robustClick(driver, By.id("row_1_document_no"));
        waitForLoaderToDisappear(driver);

        logger.info("📌 Entering promo code: {}", budgetPromoId);
        Event.robustClick(driver, By.id("tab_4"));
        Event.robustClick(driver, By.id("checkbox-1"));
        WebElement promoTextbox = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("rowfilter_TXT__psch_schme_id")));
        promoTextbox.clear();
        promoTextbox.sendKeys(budgetPromoId);
        waitForLoaderToDisappear(driver);

        logger.info("📌 Verifying promotion search result");

        WebElement promotionCell = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.id("row_1_promotion_id")
                )
        );

        String actualPromotion = promotionCell.getText().trim();
        logger.info("📌 Search result promotion: {}", actualPromotion);

        if (!budgetPromoId.equals(actualPromotion)) {
            result.fail(
                    String.format(
                            "Expected promotion [%s] but found [%s]",
                            budgetPromoId,
                            actualPromotion
                    )
            );
        } else {
            logger.info("✅ Promotion found successfully: {}", actualPromotion);
        }

        WebElement allocatedQuantityCell = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.id("row_1_allocated_quantity")
                )
        );

        String actualAllocatedQuantity = allocatedQuantityCell.getText().trim();
        logger.info("📌 Allocated quantity for [{}]: {}", actualPromotion, actualAllocatedQuantity);

        if (!"0".equals(actualAllocatedQuantity)) {
            result.fail(
                    String.format(
                            "Allocated quantity validation failed for promotion [%s]. Expected [0] but found [%s]",
                            budgetPromoId,
                            actualAllocatedQuantity
                    )
            );
        } else {
            logger.info("✅ Allocated quantity is 0 for promotion: {}", budgetPromoId);
        }

        logger.info("✅ Transaction Inquiry steps completed");
    }
}