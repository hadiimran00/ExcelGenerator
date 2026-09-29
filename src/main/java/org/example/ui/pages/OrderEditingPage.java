package org.example.ui.pages;

import org.example.ui.utilities.Event;
import org.example.ui.utilities.ScreenshotService;
import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;

public class OrderEditingPage extends basePage {

    private final By pjpNumberDropdown = By.id("pjpNumber");
    private final By sellCatDropdown = By.id("SELLCAT");
    private final By sectionDropdown = By.id("section");
    private final By outletCodeDropdown = By.id("outletCode");
    private final By skuDropdown = By.id("sku");
    private final By clearButton = By.id("clear_button_6");
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By docNoFilter = By.id("rowfilter_documentNo");
    private final By firstRowDocNo = By.id("row_1_document_no");
    private final By rowEditBtn = By.id("rowEditBtn_Edit_0");
    private final By currentQty1 = By.id("currentQty1_0");
    private final By currentQty3 = By.id("currentQty3_0");
    private final By reasonTypeDropdown = By.id("reasonType");
    private final By rowSaveBtn = By.id("rowEditBtn_Save_0");
    private final By validateBtn = By.id("validateBtn");
    private final By saveBtn = By.id("saveBtn");
    private final By errorNotification = By.id("notify_text_error");

    public OrderEditingPage(WebDriver driver) {
        super(driver);
    }

    public void editOrder(Map<String, String> scenarioData, String orderNo, ValidationResult result) throws InterruptedException {
        editOrder(scenarioData, orderNo, scenarioData.get("editQuantityPCS"), result);
    }

    public void editOrder(Map<String, String> scenarioData, String orderNo, String qty3, ValidationResult result) throws InterruptedException {
        selectDropdown(pjpNumberDropdown, scenarioData.get("PJPNO"));
        selectDropdown(sellCatDropdown, scenarioData.get("SELLCAT"));
        selectDropdown(sectionDropdown, scenarioData.get("SECTION"));
        selectDropdown(outletCodeDropdown, scenarioData.get("OUTLET"));
      //  selectDropdown(skuDropdown, scenarioData.get("productCode"));

        Event.robustClick(driver, clearButton);
        waitForLoader();

        Event.robustClick(driver, gridFilterCheckbox);
        waitForLoader();

        type(docNoFilter, orderNo);
        waitForLoader();

        Event.robustClick(driver, firstRowDocNo);
        waitForLoaderToDisappear(driver);

        Event.robustClick(driver, rowEditBtn);
        waitForLoader();

        type(currentQty1, "0");
        waitForLoader();

        type(currentQty3, qty3);
        waitForLoader();

        // "No Scheme" is an Order Editing reason code (siblings: "Order Change QTY",
        // "Wrong Order /No Order", "Stock Already Avl.") - unrelated to the "reasonType"
        // scenario key used by SAN/return flows for stock reason codes ("Sound CAT" etc).
        String reasonType = "No Scheme";

        // The DevExtreme grid re-renders this row after a quantity edit, which can invalidate
        // the reasonType element reference mid-selection; retry once past that transient failure.
        try {
            Thread.sleep(1000);
            selectDropdown(reasonTypeDropdown, reasonType);
        } catch (Exception e) {
            waitForLoader();
            selectDropdown(reasonTypeDropdown, reasonType);
        }

        Event.robustClick(driver, rowSaveBtn);
        waitForLoader();

        Event.robustClick(driver, validateBtn);
        waitForLoader();
        ToastHandles.waitForNotification(driver, Duration.ofSeconds(10));
        if (hasError(orderNo, "Order Editing validation", result)) {
            return;
        }

        Event.robustClick(driver, saveBtn);
        waitForLoader();
        ToastHandles.waitForNotification(driver, Duration.ofSeconds(10));
        if (hasError(orderNo, "Order Editing save", result)) {
            return;
        }

        result.pass("Order [" + orderNo + "] edited and saved successfully.");
    }

    private boolean hasError(String orderNo, String stepLabel, ValidationResult result) {
        List<WebElement> errors = driver.findElements(errorNotification);
        if (!errors.isEmpty() && !errors.get(0).getText().isBlank()) {
            String errorMessage = errors.get(0).getText().trim();
            ScreenshotService.takeScreenshot(driver, stepLabel.replace(" ", "_"));
            result.fail(stepLabel + " failed for order [" + orderNo + "]: " + errorMessage);
            return true;
        }
        return false;
    }
}
