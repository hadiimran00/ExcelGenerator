package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.example.ui.pages.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.example.ui.pages.basePage.requireScenarioValue;
import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;

/**
 * Coupon2.0: books two orders via the order-service API (see OrderBookingApiClient).
 * Order1 is unallocated and edited (twice - once before GIN, once again after GIN approval);
 * order2 is never edited. Each order gets its own separate GIN: order1's GIN is saved as-is,
 * order2's GIN is edited on tab_3 (actual quantity + loss reason) before saving ("GIN editing").
 * Both orders are then delivered, and order2 alone is taken through Sales Return.
 * Transaction Inquiry is checked at five points: order2 right after booking, order1 after its
 * first edit, order2 after its GIN-with-editing, order1 after its second edit, and order2 after
 * its Sales Return.
 */
public class CouponOrderEditFlow {

    private static final Logger logger = LoggerUtil.getLogger(CouponOrderEditFlow.class);

    public static ValidationResult run(
            WebDriver driver,
            Map<String, String> scenarioData,
            String downloadDir
    ) throws InterruptedException {
        ValidationResult result = new ValidationResult(
                "Coupon2.0 - Order Stock Allocation, Order Editing and discount validation flow"
        );

        try {
            validateInputs(scenarioData);

            String pjpNo = scenarioData.get("PJPNO");
            String orderPJP = scenarioData.getOrDefault("PJPNO", "PKOB0001");
            String ginPjp = scenarioData.get("ginPjp");
            String ginEditQty3 = scenarioData.get("ginEditQty3");
            String ginLossReason = scenarioData.get("ginLossReason");
            // Each checkpoint gets its own expected value since the order/state under test differs
            // at every stage rather than sharing one constant.
            String expectedDiscountAmountOrder2Booking = scenarioData.get("expectedDiscountAmountOrder2Booking");
            String expectedDiscountAmountOrder1AfterEdit = scenarioData.get("expectedDiscountAmountOrder1AfterEdit");
            String expectedDiscountAmountOrder2AfterGin = scenarioData.get("expectedDiscountAmountOrder2AfterGin");
            String expectedDiscountAmountOrder1AfterSecondEdit = scenarioData.get("expectedDiscountAmountOrder1AfterSecondEdit");
            String expectedDiscountAmountOrder2AfterSalesReturn = scenarioData.get("expectedDiscountAmountOrder2AfterSalesReturn");
            String promoId = scenarioData.get("schemeCode");

            // Page Instantiations
            OrderStockAllocationPage stockAllocationPage = new OrderStockAllocationPage(driver);
            OrderEditingPage orderEditingPage = new OrderEditingPage(driver);
            OrderDeliveryDatePage deliveryDatePage = new OrderDeliveryDatePage(driver);
            TransactionInquiryPage transactionInquiryPage = new TransactionInquiryPage(driver);
            GoodsIssueNotePage ginPage = new GoodsIssueNotePage(driver);
            CashmemoStatusPage cashmemoStatusPage = new CashmemoStatusPage(driver);
            SalesReturnPage salesReturnPage = new SalesReturnPage(driver);

            // STEP 1: ORDER BOOKING (via API)
            String orderNo = OrderBookingApiClient.createOrder(driver, scenarioData, "First");
            String orderNo2 = OrderBookingApiClient.createOrder(driver, scenarioData, "Second");
            scenarioData.put("orderNo", orderNo);
            scenarioData.put("orderNo2", orderNo2);

            // STEP 2: TRANSACTION INQUIRY - VALIDATE DISCOUNT (order2, baseline before any edit)
            validateDiscount(driver, transactionInquiryPage, "Order 2 - baseline right after booking (before any edit)",
                    pjpNo, orderNo2, promoId, expectedDiscountAmountOrder2Booking, result);

            // STEP 3: DELIVERY DATE (both orders)
            updateDeliveryDate(driver, deliveryDatePage, result, orderPJP, orderNo);
            updateDeliveryDate(driver, deliveryDatePage, result, orderPJP, orderNo2);

            // STEP 4: UNALLOCATE + EDIT FIRST ORDER (order2 is left untouched)
            // Search text only needs to narrow the list enough for screenId to become clickable
            // (navigateToScreen always clicks by id, never by the matched text) - "stock allocation"
            // matches both PK's "order stock allocation" and PH's plain "Stock Allocation" menu label.
            stockAllocationPage.navigateToScreen("stock allocation", "DYL_201904");
            waitForLoaderToDisappear(driver);
            stockAllocationPage.unallocateOrder(pjpNo, orderNo, result);

            orderEditingPage.navigateToScreen("order editing", "ORDER_EDITING");
            waitForLoaderToDisappear(driver);
            orderEditingPage.editOrder(scenarioData, orderNo, result);

            // STEP 5: TRANSACTION INQUIRY - VALIDATE DISCOUNT (order1, after first edit)
            validateDiscount(driver, transactionInquiryPage, "Order 1 - after first edit (qty=" + scenarioData.get("editQuantityPCS") + ", before GIN)",
                    pjpNo, orderNo, promoId, expectedDiscountAmountOrder1AfterEdit, result);

            // STEP 6: TWO SEPARATE GINS - order1 saved as-is, order2 edited on tab_3 before saving
            String ginNo1 = createAndApproveGin(driver, ginPage, ginPjp, orderNo, null, null, result);
            String ginNo2 = createAndApproveGin(driver, ginPage, ginPjp, orderNo2, ginEditQty3, ginLossReason, result);

            // STEP 7: TRANSACTION INQUIRY - VALIDATE DISCOUNT (order2, after its GIN-with-editing)
            validateDiscount(driver, transactionInquiryPage, "Order 2 - after its GIN (quantity/loss-reason edited on tab_3)",
                    pjpNo, orderNo2, promoId, expectedDiscountAmountOrder2AfterGin, result);

            // STEP 8: EDIT FIRST ORDER AGAIN (after GIN approval) - a separate qty3, since goods
            // are already issued at this point.
            orderEditingPage.navigateToScreen("order editing", "ORDER_EDITING");
            waitForLoaderToDisappear(driver);
            orderEditingPage.editOrder(scenarioData, orderNo, scenarioData.get("editQuantityPCSpostGIN"), result);

            // STEP 9: TRANSACTION INQUIRY - VALIDATE DISCOUNT (order1, after second edit)
            validateDiscount(driver, transactionInquiryPage, "Order 1 - after second edit (qty=" + scenarioData.get("editQuantityPCSpostGIN") + ", after GIN approval)",
                    pjpNo, orderNo, promoId, expectedDiscountAmountOrder1AfterSecondEdit, result);

            // STEP 10: DELIVER BOTH ORDERS (each against its own GIN)
            cashmemoStatusPage.navigateToScreen("Cashmemo Status", "CASHMEMO-STATUS");
            cashmemoStatusPage.deliverOrder(ginPjp, ginNo1, orderNo, result);
            cashmemoStatusPage.deliverOrder(ginPjp, ginNo2, orderNo2, result);

            // STEP 11: SALES RETURN (order2 only)
            salesReturnPage.navigateToScreen("Sales Return", "DYL_201801");
            waitForLoaderToDisappear(driver);
            String salesReturnNo = salesReturnPage.createReturnHeader(ginPjp, orderNo2, result);
            salesReturnPage.enterReturnQuantityAndReason(scenarioData.get("salesReturnQty3"), scenarioData.get("SalesReturnReasonType"));
            salesReturnPage.validateAndSave(result);
            salesReturnPage.approveStep("Approved SR Step 1", result);
            salesReturnPage.approveStep("Approved SR Step 2", result);

            salesReturnPage.navigateToScreen("Sales Return", "SALESRETURN-STATUSCHANGE");
            waitForLoaderToDisappear(driver);
            salesReturnPage.pickSalesReturn(ginPjp, ginNo2, salesReturnNo, result);

            // STEP 12: TRANSACTION INQUIRY - VALIDATE DISCOUNT (order2, after Sales Return)
            // Document type here is "Sales Return", not "Sales", and the document searched is the
            // Sales Return number itself, not the original order number.
            validateDiscount(driver, transactionInquiryPage, "Order 2 - after Sales Return (searched by Sales Return doc, not original order)",
                    "Sales Return", pjpNo, salesReturnNo, promoId, expectedDiscountAmountOrder2AfterSalesReturn, result);

            result.pass("Coupon2.0 order stock allocation, editing and discount validation flow completed successfully.", driver);

        } catch (Exception e) {
            logger.error("❌ Coupon2.0 Order Edit flow failed: {}", e.getMessage(), e);
            result.fail("Flow execution encountered an error: " + e.getMessage());
        }

        return result;
    }

    private static void validateInputs(Map<String, String> data) {
        String[] keys = {
                "OUTLET", "SELLCAT", "SECTION", "daily", "schemeCode", "productCode", "QuantityPCS",
                "PJPNO", "editQuantityPCS", "editQuantityPCSpostGIN", "ginEditQty3", "ginLossReason",
                "expectedDiscountAmountOrder2Booking", "expectedDiscountAmountOrder1AfterEdit",
                "expectedDiscountAmountOrder2AfterGin", "expectedDiscountAmountOrder1AfterSecondEdit",
                "expectedDiscountAmountOrder2AfterSalesReturn", "ginPjp",
                "SalesReturnReasonType", "salesReturnQty3"
        };
        for (String key : keys) {
            requireScenarioValue(data, key, data.get(key));
        }
    }

    /**
     * Creates and fully approves a GIN for a single order. When ginLossReason is non-null,
     * "GIN editing" is applied on tab_3 (actual quantity + loss reason) before saving;
     * otherwise the GIN is saved as-is.
     */
    private static String createAndApproveGin(
            WebDriver driver,
            GoodsIssueNotePage ginPage,
            String ginPjp,
            String orderNo,
            String ginEditQty3,
            String ginLossReason,
            ValidationResult result
    ) {
        ginPage.navigateToScreen("Goods Issue Note", "GOOD_ISSUE_NODE");
        waitForLoaderToDisappear(driver);
        ginPage.selectDeliveryPjpAndDate(ginPjp, LocalDate.now().toString());
        waitForLoaderToDisappear(driver);

        ginPage.click(By.id("tab_2"));
        waitForLoaderToDisappear(driver);
        ginPage.click(By.id("gridFilterCheckbox"));
        waitForLoaderToDisappear(driver);
        ginPage.filterAndSelectOrder(orderNo, By.id("checkbox-1"));

        if (ginLossReason != null) {
            ginPage.submitGinWithEditing(ginEditQty3, ginLossReason, result);
        } else {
            ginPage.submitGIN(result);
        }

        String ginNo = ginPage.getFirstRowGinNoAndOpen();
        ginPage.approveStep("Approved GIN Step 1", result);
        ginPage.getFirstRowGinNoAndOpen();
        ginPage.approveStep("Approved GIN Step 2", result);
        return ginNo;
    }

    private static void updateDeliveryDate(WebDriver driver, OrderDeliveryDatePage deliveryDatePage, ValidationResult result, String orderPJP, String orderNo) {
        deliveryDatePage.navigateToScreen("Delivery Date Change", "DYL_201080");
        waitForLoaderToDisappear(driver);
        deliveryDatePage.processDeliveryDateForOrder(orderPJP, orderNo);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoaderToDisappear(driver);
    }

    private static void validateDiscount(
            WebDriver driver,
            TransactionInquiryPage transactionInquiryPage,
            String checkpointLabel,
            String pjpNo,
            String orderNo,
            String promoId,
            String expectedDiscountAmount,
            ValidationResult result
    ) {
        validateDiscount(driver, transactionInquiryPage, checkpointLabel, "Sales", pjpNo, orderNo, promoId, expectedDiscountAmount, result);
    }

    private static void validateDiscount(
            WebDriver driver,
            TransactionInquiryPage transactionInquiryPage,
            String checkpointLabel,
            String documentType,
            String pjpNo,
            String orderNo,
            String promoId,
            String expectedDiscountAmount,
            ValidationResult result
    ) {
        transactionInquiryPage.navigateToScreen("BG - Transaction Inquiry", "DYL_BG1016");
        waitForLoaderToDisappear(driver);

        transactionInquiryPage.selectDocumentType(documentType);
        transactionInquiryPage.selectPjp(pjpNo);
        transactionInquiryPage.searchAndSelectDocument(orderNo);
        transactionInquiryPage.searchPromotion(promoId);

        // A visible row on the Total Offering tab is NOT sufficient evidence the promo was applied -
        // an unrelated auto-applied promotion can occupy row 1 even when our scheme was never
        // attached to the order. The promotion id itself must match what we booked with.
        if (!transactionInquiryPage.isPromotionApplied(promoId, Duration.ofSeconds(10))) {
            result.fail("[" + checkpointLabel + "] Promo [" + promoId + "] not applied to document [" + orderNo
                    + "] - the Total Offering tab does not show this scheme id after filtering. This is the known"
                    + " application defect: the coupon scheme sent at booking never actually attaches to the order.", driver);
            logger.info("Promo not applied [{}]: document {} promo {}", checkpointLabel, orderNo, promoId);
            return;
        }

        String actualDiscount = transactionInquiryPage.getFirstRowDiscount();

        if (!expectedDiscountAmount.equals(actualDiscount)) {
            result.fail("[" + checkpointLabel + "] Discount amount mismatch for document [" + orderNo
                    + "]. Expected [" + expectedDiscountAmount + "] (per the coupon's slab formula) but found ["
                    + actualDiscount + "] on screen.", driver);
            logger.info("Discount amount mismatch [{}]: expected {} found {} for document {}",
                    checkpointLabel, expectedDiscountAmount, actualDiscount, orderNo);
        } else {
            result.pass("[" + checkpointLabel + "] Discount amount correct for document [" + orderNo
                    + "]: [" + expectedDiscountAmount + "] as expected.", driver);
            logger.info("✅ Discount amount correct [{}]: {} for document {}", checkpointLabel, expectedDiscountAmount, orderNo);
        }
    }
}
