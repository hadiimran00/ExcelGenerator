package org.example.ui.pages;

import org.example.ui.utilities.Event;
import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.time.Duration;

public class GoodsIssueNotePage extends basePage {

    // Locators
    private final By deliveryManPjpDropdown = By.id("deliveryManPjp");
    private final By deliveryDateDropdown = By.id("deliveryDate");

    private final By tab1 = By.id("tab_1");
    private final By tab2 = By.id("tab_2");
    private final By tab3 = By.id("tab_3");

    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By cmDocumentFilterInput = By.id("rowfilter_cmDocumentNo");

    private final By saveAllBtn = By.id("saveallBtn");
    private final By firstRowGinNo = By.id("row_1_gin_no.");


    private final By actualQtyCsCell = By.id("row_1_actual_cs");
    private final By actualQty1Field = By.id("actualQty1_0");
    private final By actualQtyPcCell = By.id("row_1_actual_pc");
    private final By actualQty3Field = By.id("actualQty3_0");
    private final By lossReasonCell = By.id("row_1_loss_reason");
    private final By lossReasonDropdown = By.id("lossReason_0");

    private final By forwardBtn = By.id("forward");
    private final By commentsInput = By.id("comments");
    private final By forwardPopUpSaveBtn = By.id("forwardPopUpSaveBtn");

    public GoodsIssueNotePage(WebDriver driver) {
        super(driver);
    }

    public void selectDeliveryPjpAndDate(String pjp, String date) {
        selectDropdown(deliveryManPjpDropdown, pjp);
        selectDropdown(deliveryDateDropdown, date);
    }

    public void filterAndSelectOrder(String orderNo, By checkboxLocator) {
        type(cmDocumentFilterInput, orderNo);
        waitForLoader();
        click(checkboxLocator);
    }

    public void submitGIN(ValidationResult result) {
        click(tab3);
        waitForLoader();
        click(saveAllBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
    }

    public void submitGinWithEditing(String actualQty3, String lossReason, ValidationResult result) {
        Event.robustClick(driver, tab3);
        waitForLoader();

        Event.robustClick(driver, actualQtyCsCell);
        waitForLoader();
        type(actualQty1Field, "0");
        waitForLoader();

        Event.robustClick(driver, actualQtyPcCell);
        waitForLoader();
        type(actualQty3Field, actualQty3);
        waitForLoader();

     
        Event.robustClick(driver, lossReasonCell);
        waitForLoader();
        new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> {
            try {
                return find(lossReasonDropdown).getAttribute("aria-disabled") == null
                        || "false".equals(find(lossReasonDropdown).getAttribute("aria-disabled"));
            } catch (Exception e) {
                return false;
            }
        });
        selectDropdown(lossReasonDropdown, lossReason);
        waitForLoader();

        Event.robustClick(driver, saveAllBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
    }

    public String getFirstRowGinNoAndOpen() {
        click(tab1);
        waitForLoader();
        String ginNo = find(firstRowGinNo).getText().trim();
        click(firstRowGinNo);
        waitForLoader();
        return ginNo;
    }

    public void approveStep(String comment, ValidationResult result) {
        click(forwardBtn);
        type(commentsInput, comment);
        click(forwardPopUpSaveBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
    }
}