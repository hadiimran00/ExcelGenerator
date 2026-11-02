package org.example.ui.utilities;

import org.apache.logging.log4j.Logger;
import org.example.ui.pages.*;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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

            // LOYALTYB2B: a second scheme attached alongside the coupon on both orders - validated
            // at the same five checkpoints, with its own expected discount amounts.
            String loyaltyPromoId = scenarioData.get("loyaltyPromoId");
            // LOYALTYB2B is a flat amount, so one expected value covers every checkpoint before the
            // return, and a second covers the Sales Return document.
            String expectedDiscountAmountLoyalty = scenarioData.get("expectedDiscountAmountLoyalty");
            String expectedDiscountAmountLoyaltyAfterSalesReturn = scenarioData.get("expectedDiscountAmountLoyaltyAfterSalesReturn");

            // Current Promotion utilized budget per promo at each checkpoint. Optional: a promo/checkpoint
            // without a key is skipped (budget is cumulative across orders, so these are set by hand).
            // The coupon scales with each order's quantity, so unlike the flat loyalty promo it has
            // its own expected budget change at every checkpoint.
            String budgetCouponOrder2Booking = scenarioData.get("expectedUtilizedBudgetCouponOrder2Booking");
            String budgetCouponOrder1AfterEdit = scenarioData.get("expectedUtilizedBudgetCouponOrder1AfterEdit");
            String budgetCouponOrder2AfterGin = scenarioData.get("expectedUtilizedBudgetCouponOrder2AfterGin");
            String budgetCouponOrder1AfterSecondEdit = scenarioData.get("expectedUtilizedBudgetCouponOrder1AfterSecondEdit");
            String budgetCouponAfterSalesReturn = scenarioData.get("expectedUtilizedBudgetCouponAfterSalesReturn");
            String budgetLoyalty = scenarioData.get("expectedUtilizedBudgetLoyalty");
            String budgetLoyaltyAfterSalesReturn = scenarioData.get("expectedUtilizedBudgetLoyaltyAfterSalesReturn");

            // Page Instantiations
            OrderStockAllocationPage stockAllocationPage = new OrderStockAllocationPage(driver);
            OrderEditingPage orderEditingPage = new OrderEditingPage(driver);
            OrderDeliveryDatePage deliveryDatePage = new OrderDeliveryDatePage(driver);
            TransactionInquiryPage transactionInquiryPage = new TransactionInquiryPage(driver);
            GoodsIssueNotePage ginPage = new GoodsIssueNotePage(driver);
            CashmemoStatusPage cashmemoStatusPage = new CashmemoStatusPage(driver);
            SalesReturnPage salesReturnPage = new SalesReturnPage(driver);
            PromotionPage promotionPage = new PromotionPage(driver);

            // Utilized budget is shared and cumulative, so read it before booking and check each
            // checkpoint as a change from this baseline. A promo is only read when it has configured
            // expectedUtilizedBudget* values.
            Map<String, BigDecimal> budgetBaselines = readBudgetBaselines(driver, promotionPage,
                    promoId, anyConfigured(budgetCouponOrder2Booking, budgetCouponOrder1AfterEdit, budgetCouponOrder2AfterGin,
                            budgetCouponOrder1AfterSecondEdit, budgetCouponAfterSalesReturn),
                    loyaltyPromoId, anyConfigured(budgetLoyalty, budgetLoyaltyAfterSalesReturn));

            // STEP 1: ORDER BOOKING (via API)
            String orderNo = OrderBookingApiClient.createOrder(driver, scenarioData, "First");
            String orderNo2 = OrderBookingApiClient.createOrder(driver, scenarioData, "Second");
            String orderNo3 = OrderBookingApiClient.createOrder(driver, scenarioData, "Third");
            // The fourth order is only booked when a below-slab quantity is configured.
            String belowSlabQty = scenarioData.get("belowSlabQuantityPCS");
            String orderNo4 = isBlank(belowSlabQty) ? null : OrderBookingApiClient.createOrder(driver, scenarioData, "Fourth");
            scenarioData.put("orderNo", orderNo);
            scenarioData.put("orderNo2", orderNo2);
            scenarioData.put("orderNo3", orderNo3);

            // Orders 3 and 4 exist only to exercise cancellation: both are cancelled before the first
            // Current Promotion check, so neither shows up in any later discount or budget expectation.
            OrderCancellationPage cancellationPage = new OrderCancellationPage(driver);
            cancellationPage.navigateToScreen(OrderCancellationPage.SCREEN_NAME, OrderCancellationPage.SCREEN_ID);
            waitForLoaderToDisappear(driver);
            cancellationPage.cancelOrder(scenarioData, orderNo3, result);

            if (orderNo4 != null) {
                scenarioData.put("orderNo4", orderNo4);
                // Order 4 is edited below the coupon's slab first: the slab coupon should drop off
                // while the flat loyalty promo stays, and only then is it cancelled.
                updateDeliveryDate(driver, deliveryDatePage, result, orderPJP, orderNo4);

                stockAllocationPage.navigateToScreen("stock allocation", "DYL_201904");
                waitForLoaderToDisappear(driver);
                stockAllocationPage.unallocateOrder(pjpNo, orderNo4, result);

                orderEditingPage.navigateToScreen("order editing", "ORDER_EDITING");
                waitForLoaderToDisappear(driver);
                orderEditingPage.editOrder(scenarioData, orderNo4, belowSlabQty, result);

                openTransactionInquiryFor(driver, transactionInquiryPage, pjpNo, orderNo4);
                String label4 = "Order 4 - edited to " + belowSlabQty + " PCS (below the coupon slab)";
                validatePromoNotApplied(driver, transactionInquiryPage, label4, orderNo4, promoId, result);
                validatePromoDiscount(driver, transactionInquiryPage, label4,
                        orderNo4, loyaltyPromoId, expectedDiscountAmountLoyalty, result);

                cancellationPage.navigateToScreen(OrderCancellationPage.SCREEN_NAME, OrderCancellationPage.SCREEN_ID);
                waitForLoaderToDisappear(driver);
                cancellationPage.cancelOrder(scenarioData, orderNo4, result);
            }

            // STEP 2: TRANSACTION INQUIRY - VALIDATE DISCOUNT (order2, baseline before any edit)
            openTransactionInquiryFor(driver, transactionInquiryPage, pjpNo, orderNo2);
            String label2Booking = "Order 2 - baseline right after booking (before any edit)";
            validatePromoDiscount(driver, transactionInquiryPage, label2Booking,
                    orderNo2, promoId, expectedDiscountAmountOrder2Booking, result);
            validatePromoDiscount(driver, transactionInquiryPage, label2Booking,
                    orderNo2, loyaltyPromoId, expectedDiscountAmountLoyalty, result);
            validateBudgetUtilized(driver, promotionPage, budgetBaselines, label2Booking, promoId, budgetCouponOrder2Booking,
                    loyaltyPromoId, budgetLoyalty, result);

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
            openTransactionInquiryFor(driver, transactionInquiryPage, pjpNo, orderNo);
            String label1FirstEdit = "Order 1 - after first edit (qty=" + scenarioData.get("editQuantityPCS") + ", before GIN)";
            validatePromoDiscount(driver, transactionInquiryPage, label1FirstEdit,
                    orderNo, promoId, expectedDiscountAmountOrder1AfterEdit, result);
            validatePromoDiscount(driver, transactionInquiryPage, label1FirstEdit,
                    orderNo, loyaltyPromoId, expectedDiscountAmountLoyalty, result);
            validateBudgetUtilized(driver, promotionPage, budgetBaselines, label1FirstEdit, promoId, budgetCouponOrder1AfterEdit,
                    loyaltyPromoId, budgetLoyalty, result);

            // STEP 6: TWO SEPARATE GINS - order1 saved as-is, order2 edited on tab_3 before saving
            String ginNo1 = createAndApproveGin(driver, ginPage, ginPjp, orderNo, null, null, result);
            String ginNo2 = createAndApproveGin(driver, ginPage, ginPjp, orderNo2, ginEditQty3, ginLossReason, result);

            // STEP 7: TRANSACTION INQUIRY - VALIDATE DISCOUNT (order2, after its GIN-with-editing)
            openTransactionInquiryFor(driver, transactionInquiryPage, pjpNo, orderNo2);
            String label2Gin = "Order 2 - after GIN editing";
            validatePromoDiscount(driver, transactionInquiryPage, label2Gin,
                    orderNo2, promoId, expectedDiscountAmountOrder2AfterGin, result);
            validatePromoDiscount(driver, transactionInquiryPage, label2Gin,
                    orderNo2, loyaltyPromoId, expectedDiscountAmountLoyalty, result);
            validateBudgetUtilized(driver, promotionPage, budgetBaselines, label2Gin, promoId, budgetCouponOrder2AfterGin,
                    loyaltyPromoId, budgetLoyalty, result);

            // STEP 8-9: AFTER GIN - order 1 is either edited again (PK) or, where Order Editing is not
            // possible once goods are issued (PH, postGinAction=FRESH_SALES_RETURN), given a fresh
            // sales return; either way its discount and budget are checked afterwards.
            if ("FRESH_SALES_RETURN".equalsIgnoreCase(scenarioData.getOrDefault("postGinAction", ""))) {
                FreshSalesReturnPage freshReturnPage = new FreshSalesReturnPage(driver);
                freshReturnPage.navigateToScreen("Fresh Sales Return", "FRESH_SALES_RETURN");
                waitForLoaderToDisappear(driver);
                String freshReturnNo = freshReturnPage.createReturnHeader(ginPjp, orderNo, result);
                requireScenarioValue(scenarioData, "freshReturnQty3", scenarioData.get("freshReturnQty3"));
                String freshReason = freshReturnPage.enterReturnDetails(null,
                        scenarioData.get("freshReturnQty3"), scenarioData.get("returnReasonType"));
                freshReturnPage.validateAndSave(result);

                // Checked on the return document itself, not the original order.
                openTransactionInquiryFor(driver, transactionInquiryPage, "Fresh Sales Return", pjpNo, freshReturnNo);
                String labelFreshReturn = "Order 1 - fresh sales return (qty=" + scenarioData.get("freshReturnQty3")
                        + ", reason=" + freshReason + ")";
                validatePromoDiscount(driver, transactionInquiryPage, labelFreshReturn,
                        freshReturnNo, promoId, scenarioData.get("expectedDiscountAmountOrder1AfterFreshReturn"), result);
                validatePromoDiscount(driver, transactionInquiryPage, labelFreshReturn,
                        freshReturnNo, loyaltyPromoId, scenarioData.get("expectedDiscountAmountLoyaltyAfterFreshReturn"), result);
                validateBudgetUtilized(driver, promotionPage, budgetBaselines, labelFreshReturn, promoId,
                        scenarioData.get("expectedUtilizedBudgetCouponAfterFreshReturn"),
                        loyaltyPromoId, scenarioData.get("expectedUtilizedBudgetLoyaltyAfterFreshReturn"), result);
            } else {
                // STEP 8: EDIT FIRST ORDER AGAIN (after GIN approval) - a separate qty3, since goods
                // are already issued at this point.
                orderEditingPage.navigateToScreen("order editing", "ORDER_EDITING");
                waitForLoaderToDisappear(driver);
                orderEditingPage.editOrder(scenarioData, orderNo, scenarioData.get("editQuantityPCSpostGIN"), result);

                // STEP 9: TRANSACTION INQUIRY - VALIDATE DISCOUNT (order1, after second edit)
                openTransactionInquiryFor(driver, transactionInquiryPage, pjpNo, orderNo);
                String label1SecondEdit = "Order 1 - after second edit (qty=" + scenarioData.get("editQuantityPCSpostGIN") + ", after GIN approval)";
                validatePromoDiscount(driver, transactionInquiryPage, label1SecondEdit,
                        orderNo, promoId, expectedDiscountAmountOrder1AfterSecondEdit, result);
                validatePromoDiscount(driver, transactionInquiryPage, label1SecondEdit,
                        orderNo, loyaltyPromoId, expectedDiscountAmountLoyalty, result);
                validateBudgetUtilized(driver, promotionPage, budgetBaselines, label1SecondEdit, promoId, budgetCouponOrder1AfterSecondEdit,
                        loyaltyPromoId, budgetLoyalty, result);

            }

            // STEP 10: DELIVER BOTH ORDERS (each against its own GIN)
            cashmemoStatusPage.navigateToScreen("Cashmemo Status", "CASHMEMO-STATUS");
            cashmemoStatusPage.deliverOrder(ginPjp, ginNo1, orderNo, scenarioData, result);
            cashmemoStatusPage.deliverOrder(ginPjp, ginNo2, orderNo2, scenarioData, result);

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
            openTransactionInquiryFor(driver, transactionInquiryPage, "Sales Return", pjpNo, salesReturnNo);
            String labelSalesReturn = "Order 2 - Sales Return";
            validatePromoDiscount(driver, transactionInquiryPage, labelSalesReturn,
                    salesReturnNo, promoId, expectedDiscountAmountOrder2AfterSalesReturn, result);
            validatePromoDiscount(driver, transactionInquiryPage, labelSalesReturn,
                    salesReturnNo, loyaltyPromoId, expectedDiscountAmountLoyaltyAfterSalesReturn, result);
            validateBudgetUtilized(driver, promotionPage, budgetBaselines, labelSalesReturn, promoId, budgetCouponAfterSalesReturn,
                    loyaltyPromoId, budgetLoyaltyAfterSalesReturn, result);

            // Optional final check, for scenarios that return every quantity they booked: both promos'
            // utilized budgets should be back at the value read before the first booking.
            if ("YES".equalsIgnoreCase(scenarioData.getOrDefault("verifyBudgetRestored", "")) && !budgetBaselines.isEmpty()) {
                promotionPage.openSchemeGrid();
                for (Map.Entry<String, BigDecimal> baseline : budgetBaselines.entrySet()) {
                    checkUtilizedBudget(driver, promotionPage, "End of flow - all quantities returned",
                            baseline.getKey(), baseline.getValue(), "0", result);
                }
            }

            result.pass("Coupon2.0 order stock allocation, editing and discount validation flow completed successfully.", driver);

        } catch (Exception e) {
            logger.error("âŒ Coupon2.0 Order Edit flow failed: {}", e.getMessage(), e);
            result.fail("Flow execution encountered an error: " + e.getMessage(),driver);
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
                "SalesReturnReasonType", "salesReturnQty3",
                "loyaltyPromoId", "expectedDiscountAmountLoyalty", "expectedDiscountAmountLoyaltyAfterSalesReturn"
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

    /**
     * Navigates to Transaction Inquiry and applies the document-level filters (type, PJP, order)
     * once, opening the Total Offering tab's filter row. Call validatePromoDiscount(...) one or
     * more times afterward to check different promos against this same already-open document
     * without re-navigating or re-filtering it.
     */
    private static void openTransactionInquiryFor(
            WebDriver driver,
            TransactionInquiryPage transactionInquiryPage,
            String documentType,
            String pjpNo,
            String orderNo
    ) {
        transactionInquiryPage.navigateToScreen("BG - Transaction Inquiry", "DYL_BG1016");
        waitForLoaderToDisappear(driver);

        transactionInquiryPage.selectDocumentType(documentType);
        transactionInquiryPage.selectPjp(pjpNo);
        transactionInquiryPage.searchAndSelectDocument(orderNo);
        transactionInquiryPage.openTotalOfferingTab();
    }

    private static void openTransactionInquiryFor(
            WebDriver driver,
            TransactionInquiryPage transactionInquiryPage,
            String pjpNo,
            String orderNo
    ) {
        openTransactionInquiryFor(driver, transactionInquiryPage, "Sales", pjpNo, orderNo);
    }

    /**
     * Filters the currently-open document's Total Offering tab by promoId and validates its
     * discount amount. Requires openTransactionInquiryFor(...) to have been called first.
     */
    private static void validatePromoDiscount(
            WebDriver driver,
            TransactionInquiryPage transactionInquiryPage,
            String checkpointLabel,
            String orderNo,
            String promoId,
            String expectedDiscountAmount,
            ValidationResult result
    ) {
        transactionInquiryPage.filterPromotion(promoId);

        // A visible row on the Total Offering tab is NOT sufficient evidence the promo was applied -
        // an unrelated auto-applied promotion can occupy row 1 even when our scheme was never
        // attached to the order. The promotion id itself must match what we booked with.
        if (!transactionInquiryPage.isPromotionApplied(promoId, Duration.ofSeconds(10))) {
            result.fail(checkpointLabel + ": Promotion " + promoId + " was not applied to document " + orderNo
                    + " - it doesn't appear on the Total Offering tab ", driver);
            logger.info("Promo not applied [{}]: document {} promo {}", checkpointLabel, orderNo, promoId);
            return;
        }

        String actualDiscount = transactionInquiryPage.getFirstRowDiscount();

        if (!expectedDiscountAmount.equals(actualDiscount)) {
            result.fail(checkpointLabel + ": Promotion " + promoId + " on document " + orderNo + " shows a discount of "
                    + actualDiscount + ", but " + expectedDiscountAmount + " was expected.", driver);
            logger.info("Discount amount mismatch [{}]: expected {} found {} for document {}",
                    checkpointLabel, expectedDiscountAmount, actualDiscount, orderNo);
        } else {
            result.pass(checkpointLabel + ": Promotion " + promoId + " on document " + orderNo
                    + " shows the expected discount of " + expectedDiscountAmount + ".", driver);
            logger.info("âœ… Discount amount correct [{}]: {} for document {}", checkpointLabel, expectedDiscountAmount, orderNo);
        }
    }

    /**
     * Filters the open document's Total Offering tab by promoId and expects NO row for it - used where a
     * promo should have dropped off (e.g. the order's quantity fell below the slab).
     */
    private static void validatePromoNotApplied(
            WebDriver driver,
            TransactionInquiryPage transactionInquiryPage,
            String checkpointLabel,
            String orderNo,
            String promoId,
            ValidationResult result
    ) {
        transactionInquiryPage.filterPromotion(promoId);
        if (transactionInquiryPage.isPromotionApplied(promoId, Duration.ofSeconds(10))) {
            result.fail(checkpointLabel + ": Promotion " + promoId + " is still applied to document " + orderNo
                    + " but was expected to drop off.", driver);
        } else {
            result.pass(checkpointLabel + ": Promotion " + promoId + " is not applied to document " + orderNo
                    + ", as expected.", driver);
        }
    }

    /**
     * Opens Current Promotion once and checks each promo's utilized budget against the expected amount
     * configured in ScenarioData for this checkpoint. A promo with no configured amount is skipped.
     * Amounts are compared as absolute values, so "PKR -66" and "66" match.
     */
    private static void validateBudgetUtilized(
            WebDriver driver,
            PromotionPage promotionPage,
            Map<String, BigDecimal> budgetBaselines,
            String checkpointLabel,
            String couponPromoId,
            String expectedCouponChange,
            String loyaltyPromoId,
            String expectedLoyaltyChange,
            ValidationResult result
    ) {
        boolean checkCoupon = !isBlank(expectedCouponChange) && budgetBaselines.containsKey(couponPromoId);
        boolean checkLoyalty = !isBlank(expectedLoyaltyChange) && budgetBaselines.containsKey(loyaltyPromoId);
        if (!checkCoupon && !checkLoyalty) {
            logger.warn("Current Promotion check skipped [{}]: no expectedUtilizedBudget* values configured or no baseline row", checkpointLabel);
            return;
        }

        promotionPage.openSchemeGrid();

        if (checkCoupon) {
            checkUtilizedBudget(driver, promotionPage, checkpointLabel, couponPromoId,
                    budgetBaselines.get(couponPromoId), expectedCouponChange, result);
        }
        if (checkLoyalty) {
            checkUtilizedBudget(driver, promotionPage, checkpointLabel, loyaltyPromoId,
                    budgetBaselines.get(loyaltyPromoId), expectedLoyaltyChange, result);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean anyConfigured(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads each configured promo's utilized budget on Current Promotion before any order is booked.
     * A promo with no configured checks, or no row on the screen, is left out of the result and
     * its budget checks are skipped later.
     */
    private static Map<String, BigDecimal> readBudgetBaselines(
            WebDriver driver,
            PromotionPage promotionPage,
            String couponPromoId,
            boolean couponConfigured,
            String loyaltyPromoId,
            boolean loyaltyConfigured
    ) {
        Map<String, BigDecimal> baselines = new LinkedHashMap<>();
        if (!couponConfigured && !loyaltyConfigured) {
            return baselines;
        }

        promotionPage.openSchemeGrid();

        if (couponConfigured) {
            readBaseline(promotionPage, couponPromoId, baselines);
        }
        if (loyaltyConfigured) {
            readBaseline(promotionPage, loyaltyPromoId, baselines);
        }
        return baselines;
    }

    private static void readBaseline(PromotionPage promotionPage, String promoId, Map<String, BigDecimal> baselines) {
        promotionPage.filterPromo(promoId);
        try {
            String text = promotionPage.getFirstRowAchievedValue();
            BigDecimal amount = parseAchieved(text);
            if (amount == null) {
                logger.warn("Budget baseline for promo {} could not be read as an amount [{}]; its budget checks are skipped", promoId, text);
                return;
            }
            baselines.put(promoId, amount);
            logger.info("Budget baseline before booking: promo {} utilized {}", promoId, text);
        } catch (TimeoutException e) {
            logger.warn("Budget baseline: promo {} has no row on Current Promotion; its budget checks are skipped", promoId);
        }
    }

    private static void checkUtilizedBudget(
            WebDriver driver,
            PromotionPage promotionPage,
            String checkpointLabel,
            String promoId,
            BigDecimal baseline,
            String expectedChange,
            ValidationResult result
    ) {
        promotionPage.filterPromo(promoId);

        String utilizedBudget;
        try {
            utilizedBudget = promotionPage.getFirstRowAchievedValue();
        } catch (TimeoutException e) {
            result.fail(checkpointLabel + ": Promotion " + promoId + " was not found on the Current Promotion screen.", driver);
            logger.info("Budget row not found [{}]: promo {}", checkpointLabel, promoId);
            return;
        }

        BigDecimal utilized = parseAchieved(utilizedBudget);
        BigDecimal expected = parseAmount(expectedChange);

        if (utilized == null || expected == null) {
            result.fail(checkpointLabel + ": Promotion " + promoId + " utilized budget [" + utilizedBudget
                    + "] or expected change [" + expectedChange + "] could not be read as an amount.", driver);
            return;
        }

        BigDecimal actualChange = utilized.subtract(baseline);
        String summary = "utilized budget went from " + baseline.toPlainString() + " to " + utilizedBudget
                + " (change of " + actualChange.toPlainString() + ")";

        if (actualChange.compareTo(expected) != 0) {
            result.fail(checkpointLabel + ": Promotion " + promoId + " " + summary + ", but a change of "
                    + expectedChange + " was expected.", driver);
            logger.info("Utilized budget change mismatch [{}]: promo {} {} expected {}",
                    checkpointLabel, promoId, summary, expectedChange);
        } else {
            result.pass(checkpointLabel + ": Promotion " + promoId + " " + summary + ", as expected.", driver);
            logger.info("Utilized budget change matches [{}]: promo {} {}", checkpointLabel, promoId, summary);
        }
    }

    // The grid leaves Achieved Value empty until a promo has been consumed, which means 0 used.
    private static BigDecimal parseAchieved(String text) {
        return isBlank(text) ? BigDecimal.ZERO : parseAmount(text);
    }

    // "PKR -66", "-66.00" and "66" all parse to 66 - sign and currency text differ between screens.
    private static BigDecimal parseAmount(String text) {
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[^0-9.]", "");
        if (digits.isEmpty() || ".".equals(digits)) {
            return null;
        }
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
