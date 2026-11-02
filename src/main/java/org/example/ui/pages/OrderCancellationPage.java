package org.example.ui.pages;

import org.example.ui.utilities.Event;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Map;

/** Order Cancellation (DYL_202022): finds a booked order by PJP / section / outlet and cancels it. */
public class OrderCancellationPage extends basePage {

    public static final String SCREEN_NAME = "order cancellation";
    public static final String SCREEN_ID = "DYL_202022";

    private final By pjpDropdown = By.id("PJPNO");
    private final By sectionDropdown = By.id("SECTION");
    private final By outletDropdown = By.id("OUTLET");
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By docNoFilter = By.id("rowfilter_doc_no");
    private final By firstRowDocNo = By.id("row_1_document_no.");
    private final By firstRowCancelReasonCell = By.id("row_1_cancellation_reason");
    private final By cancelReasonDropdown = By.id("cancel_reason_0");
    private final By cancelBtn = By.id("cancelBtn");
    // Cancelling opens an "Order Cancellation Status" dialog with a result row, not a toast.
    private final By resultStatus = By.id("row_1_status");
    private final By resultMessage = By.id("row_1_message");
    private final By closeBtn = By.id("closeBtn");

    public OrderCancellationPage(WebDriver driver) {
        super(driver);
    }

    public void cancelOrder(Map<String, String> scenarioData, String orderNo, ValidationResult result) {
        String cancelReason = scenarioData.get("cancelReason");
        requireScenarioValue(scenarioData, "cancelReason", cancelReason);

        selectDropdown(pjpDropdown, scenarioData.get("PJPNO"));
        selectDropdown(sectionDropdown, scenarioData.get("SECTION"));
        // Order Booking lists "C0000023667-Name" but this screen can list "C0000023667 - Name", so retry spaced
        String outlet = scenarioData.get("OUTLET");
        try {
            selectDropdown(outletDropdown, outlet);
        } catch (Exception e) {
            selectDropdown(outletDropdown, outlet.replaceFirst("\\s*-\\s*", " - "));
        }

        Event.robustClick(driver, gridFilterCheckbox);
        waitForLoader();

        type(docNoFilter, orderNo);
        waitForLoader();

        Event.robustClick(driver, firstRowDocNo);
        waitForLoader();

        Event.robustClick(driver, firstRowCancelReasonCell);

        selectDropdown(cancelReasonDropdown, cancelReason);
        waitForLoader();

        Event.robustClick(driver, cancelBtn);

        // The dialog can open before its result row is filled in, so wait for the message text itself.
        String message = wait.until(d -> {
            String text = d.findElement(resultMessage).getText().trim();
            return text.isEmpty() ? null : text;
        });
        String status = driver.findElement(resultStatus).getText().trim();
        if (message.toLowerCase().contains("cancelled successfully")) {
            result.pass("Order [" + orderNo + "] cancelled: " + status + " - " + message, driver);
        } else {
            result.fail("Order [" + orderNo + "] cancellation did not succeed: " + status + " - " + message, driver);
        }

        // Close the status dialog so it does not block the next screen's menu.
        Event.robustClick(driver, closeBtn);
        wait.until(ExpectedConditions.invisibilityOfElementLocated(resultMessage));
        waitForLoader();
    }

}
