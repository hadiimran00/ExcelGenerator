package org.example.ui.pages;

import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;

public class FreshSalesReturnPage extends basePage {

    private final By pjpNoDropdown = By.id("pjpNo");
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By docNoFilterInput = By.id("rowfilter_tcmm_docno");
    private final By firstRowDocNo = By.id("row_1_document_no");
    private final By headerSaveBtn = By.id("saveBtn");
    private final By documentNoInput = By.id("documentNo");

    private final By tab9 = By.id("tab_9");
    private final By rowEditBtn = By.id("rowEditBtn_Edit_0");
    private final By rowSaveBtn = By.id("rowEditBtn_Save_0");
    private final By quantity1Input = By.id("quantity1_0");
    private final By quantity3Input = By.id("quantity3_0");
    private final By reasonTypeDropdown = By.id("reasonType");
    private final By validationBtn = By.id("validationBtn");

    public FreshSalesReturnPage(WebDriver driver) {
        super(driver);
    }

    public String createReturnHeader(String pjp, String orderNo, ValidationResult result) {
        selectDropdown(pjpNoDropdown, pjp);
        click(gridFilterCheckbox);
        waitForLoader();

        type(docNoFilterInput, orderNo);
        waitForLoader();

        click(firstRowDocNo);
        waitForLoader();

        click(headerSaveBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();

        return value(documentNoInput);
    }

    public void enterReturnDetails(String qty1, String qty3, String reasonText) {
        click(tab9);
        waitForLoader();
        click(rowEditBtn);

        if (qty1 != null) type(quantity1Input, qty1);
        if (qty3 != null) type(quantity3Input, qty3);

        click(reasonTypeDropdown);
        By reasonOption = By.xpath(String.format("//div[contains(@class,'dx-item-content') and contains(text(),'%s')]", reasonText));
        click(reasonOption);

        click(rowSaveBtn);
    }

    public void validateAndSave(ValidationResult result) {
        click(validationBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();

        click(headerSaveBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
    }
}