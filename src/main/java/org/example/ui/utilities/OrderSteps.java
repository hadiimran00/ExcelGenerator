package org.example.ui.utilities;

import org.example.ui.pages.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;

/**
 * Order lifecycle steps shared by the business flows (Coupon, Partial Return, Target):
 * book -> delivery date -> GIN + approve -> fresh return -> deliver -> sales return + approve + pick.
 */
public class OrderSteps {

    private final WebDriver driver;

    // Page Instantiations
    private final OrderBookingPage orderBookingPage;
    private final OrderDeliveryDatePage deliveryDatePage;
    private final GoodsIssueNotePage ginPage;
    private final FreshSalesReturnPage freshReturnPage;
    private final CashmemoStatusPage cashmemoStatusPage;
    private final SalesReturnPage salesReturnPage;
    private final SalesReturnWithoutRefPage salesReturnWithoutRefPage;

    public OrderSteps(WebDriver driver) {
        this.driver = driver;
        this.orderBookingPage = new OrderBookingPage(driver);
        this.deliveryDatePage = new OrderDeliveryDatePage(driver);
        this.ginPage = new GoodsIssueNotePage(driver);
        this.freshReturnPage = new FreshSalesReturnPage(driver);
        this.cashmemoStatusPage = new CashmemoStatusPage(driver);
        this.salesReturnPage = new SalesReturnPage(driver);
        this.salesReturnWithoutRefPage = new SalesReturnWithoutRefPage(driver);
    }

    /** Books an order on the Order Booking screen (dsrType, PJPNO, SELLCAT, SECTION, OUTLET, productCode, QuantityPCS). */
    public String bookOrder(Map<String, String> scenarioData) throws InterruptedException {
        orderBookingPage.navigateToScreen("Order Booking", "ORDER_BOOKING");
        String orderNo;
        try {
            orderNo = orderBookingPage.orderBooking(scenarioData);
        } catch (Exception e) {
            // A slow header reload can leave the loader over the dropdowns: reopen the screen and book once more
            waitForLoaderToDisappear(driver);
            orderBookingPage.navigateToScreen("Order Booking", "ORDER_BOOKING");
            orderNo = orderBookingPage.orderBooking(scenarioData);
        }
        if (orderNo == null || orderNo.isBlank() || orderNo.contains(" ")) {
            throw new RuntimeException("Order Booking failed: " + orderNo);
        }
        return orderNo;
    }

    public void updateDeliveryDate(String orderPjp, String orderNo, ValidationResult result) {
        deliveryDatePage.navigateToScreen("Delivery Date Change", "DYL_201080");
        waitForLoaderToDisappear(driver);
        deliveryDatePage.processDeliveryDateForOrder(orderPjp, orderNo);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoaderToDisappear(driver);
    }

    /**
     * Creates and fully approves a GIN for a single order. When ginLossReason is non-null,
     * "GIN editing" is applied on tab_3 (actual quantity + loss reason) before saving.
     */
    public String createAndApproveGin(String ginPjp, String orderNo, String ginEditQty3, String ginLossReason,
                                      ValidationResult result) {
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

    /** Fresh Sales Return (after GIN, before delivery); returns the return document number. */
    public String freshReturn(String ginPjp, String orderNo, String qty1, String qty3, String reason, ValidationResult result) {
        freshReturnPage.navigateToScreen("Fresh Sales Return", "FRESH_SALES_RETURN");
        waitForLoaderToDisappear(driver);
        String freshReturnNo = freshReturnPage.createReturnHeader(ginPjp, orderNo, result);
        freshReturnPage.enterReturnDetails(qty1, qty3, reason);
        freshReturnPage.validateAndSave(result);
        return freshReturnNo;
    }

    public void deliver(String ginPjp, String ginNo, String orderNo, ValidationResult result) {
        cashmemoStatusPage.navigateToScreen("Cashmemo Status", "CASHMEMO-STATUS");
        cashmemoStatusPage.deliverOrder(ginPjp, ginNo, orderNo, result);
    }

    /** Sales Return of a delivered order: create, approve both steps, then pick it. Returns the return document number. */
    public String salesReturn(String ginPjp, String ginNo, String orderNo, String qty3, String reason, ValidationResult result) {
        salesReturnPage.navigateToScreen("Sales Return", "DYL_201801");
        waitForLoaderToDisappear(driver);
        String salesReturnNo = salesReturnPage.createReturnHeader(ginPjp, orderNo, result);
        salesReturnPage.enterReturnQuantityAndReason(qty3, reason);
        salesReturnPage.validateAndSave(result);
        salesReturnPage.approveStep("Approved SR Step 1", result);
        salesReturnPage.approveStep("Approved SR Step 2", result);

        salesReturnPage.navigateToScreen("Sales Return", "SALESRETURN-STATUSCHANGE");
        waitForLoaderToDisappear(driver);
        salesReturnPage.pickSalesReturn(ginPjp, ginNo, salesReturnNo, result);
        return salesReturnNo;
    }

    /**
     * Sales Return Without Reference on the order booker PJP: create (PJPNO, SELLCAT, SECTION, OUTLET, productCode),
     * forward it twice (approval), then pick it on the delivery PJP / GIN. Returns the return document number.
     */
    public String salesReturnWithoutReference(Map<String, String> scenarioData, String ginPjp, String ginNo,
                                              String qty3, String stockType, String reason, ValidationResult result) {
        String orderPjp = scenarioData.get("PJPNO");
        salesReturnWithoutRefPage.navigateToScreen("Sales Return Without Reference", "SALE_RETURN_WITHOUT_REFERENCE");
        waitForLoaderToDisappear(driver);
        salesReturnWithoutRefPage.createReturnHeader(orderPjp, scenarioData.get("SELLCAT"), scenarioData.get("SECTION"),
                scenarioData.get("OUTLET"));
        salesReturnWithoutRefPage.enterReturnLine(scenarioData.get("productCode"), stockType, qty3, reason);
        String returnNo = salesReturnWithoutRefPage.validateAndSaveReturn(result);
        salesReturnWithoutRefPage.approveStep("Approved SR w/o Ref Step 1", result);
        salesReturnWithoutRefPage.approveStep("Approved SR w/o Ref Step 2", result);

        salesReturnWithoutRefPage.navigateToScreen("Sales Return", "SALES_RETURN_WITHOUT_REF_PICKED");
        waitForLoaderToDisappear(driver);
        salesReturnWithoutRefPage.pickReturn(ginPjp, ginNo, returnNo, result);
        return returnNo;
    }
}
