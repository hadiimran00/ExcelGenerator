package org.example.ui.pages;

import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class SalesReturnPage extends basePage {

    private final By pjpNoDropdown = By.id("pjpNo");
    private final By ginNumberDropdown = By.id("ginNumber");
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By filterDocInput = By.id("rowfilter_tcmm_docno");

    private final By headerSaveBtn = By.id("saveBtn");
    private final By documentNoInput = By.id("documentNo");

    private final By tab2 = By.id("tab_2");
    private final By tab9 = By.id("tab_9");
    private final By rowEditBtn = By.id("rowEditBtn_Edit_0");
    private final By rowSaveBtn = By.id("rowEditBtn_Save_0");
    private final By quantity3Input = By.id("quantity3_0");
    private final By reasonTypeDropdown = By.id("reasonType");

    private final By validationBtn = By.id("validationBtn");
    private final By forwardBtn = By.id("forward");
    private final By commentsInput = By.id("comments");
    private final By forwardPopUpSaveBtn = By.id("forwardPopUpSaveBtn");

    private final By firstRowDocWithDot = By.id("row_1_document_no.");
    private final By validation = By.id("validation");
    private final By saveSalePick = By.id("savesalePick");

    public SalesReturnPage(WebDriver driver) {
        super(driver);
    }

    public String createReturnHeader(String pjp, String orderNo, ValidationResult result) {
        selectDropdown(pjpNoDropdown, pjp);
        click(gridFilterCheckbox);

        type(filterDocInput, orderNo);
        waitForLoader();

        // Row 1 can still be another order until the filter applies, so wait for a row 1 cell showing our order
        By ourOrderCell = By.xpath("//td[starts-with(@id,'row_1_')][normalize-space()='" + orderNo + "']");
        new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> !d.findElements(ourOrderCell).isEmpty());
        click(ourOrderCell);
        waitForLoader();

        click(headerSaveBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);

        return value(documentNoInput);
    }

    public void enterReturnQuantityAndReason(String qty3, String reasonText) {
        click(tab9);
        waitForLoader();
        click(rowEditBtn);

        type(quantity3Input, qty3);

        click(reasonTypeDropdown);
        By reasonOption = By.xpath(String.format("//div[contains(@class,'dx-item-content') and contains(text(),'%s')]", reasonText));
        click(reasonOption);

        click(rowSaveBtn);
        waitForLoader();
    }

    public void validateAndSave(ValidationResult result) {
        click(validationBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();

        // Each check must read its own toast, not the previous action's one still showing
        ToastHandles.waitForNoNotification(driver, Duration.ofSeconds(15));
        click(headerSaveBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
    }

    public void approveStep(String comment, ValidationResult result) {
        ToastHandles.waitForNoNotification(driver, Duration.ofSeconds(15));
        click(forwardBtn);
        type(commentsInput, comment);
        click(forwardPopUpSaveBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
    }

    public void pickSalesReturn(String pjp, String ginNo, String salesReturnNo, ValidationResult result) {
        selectDropdown(pjpNoDropdown, pjp);
        selectDropdown(ginNumberDropdown, ginNo);
        click(gridFilterCheckbox);

        type(filterDocInput, salesReturnNo);
        waitForLoader();

        click(firstRowDocWithDot);
        click(tab2);
        click(validation);
        click(saveSalePick);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
    }
}