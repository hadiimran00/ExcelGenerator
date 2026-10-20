package org.example.ui.pages;

import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Sales Return Without Reference: create and approve on SALE_RETURN_WITHOUT_REFERENCE, pick on
 * SALES_RETURN_WITHOUT_REF_PICKED. Forward uses the same ids as Sales Return, so approveStep is inherited;
 * the pick screen has its own validation button (validationBtn).
 */
public class SalesReturnWithoutRefPage extends SalesReturnPage {

    private final By pjpDropdown = By.id("pjp");
    private final By sellingCategoryDropdown = By.id("SELLCAT");
    private final By sectionDropdown = By.id("Section");
    private final By outletDropdown = By.id("OUTLET");
    private final By createBtn = By.id("create");
    private final By addRowBtn = By.id("addBtn");

    private final By productDropdown = By.id("product");
    private final By stockTypeDropdown = By.id("stockType");
    private final By quantity3Input = By.id("quantity3_0");
    private final By reasonTypeDropdown = By.id("reasonType");
    private final By rowSaveBtn = By.id("rowEditBtn_Save_0");

    private final By validateBtn = By.id("validateBtn");
    private final By saveBtn = By.id("saveBtn");
    private final By documentNoInput = By.id("documentNo");

    private final By pickPjpDropdown = By.id("pjpNo");
    private final By pickGinDropdown = By.id("ginNumber");
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By filterDocInput = By.id("rowfilter_tcmm_docno");
    private final By firstRowDocWithDot = By.id("row_1_document_no.");
    private final By detailTab = By.id("tab_2");
    private final By pickValidationBtn = By.id("validationBtn");

    public SalesReturnWithoutRefPage(WebDriver driver) {
        super(driver);
    }

    public void createReturnHeader(String pjp, String sellingCategory, String section, String outlet) {
        selectDropdown(pjpDropdown, pjp);
        selectDropdown(sellingCategoryDropdown, sellingCategory);
        selectDropdown(sectionDropdown, section);
        selectDropdown(outletDropdown, outlet);
        click(createBtn);
        waitForLoader();
        // The detail grid starts empty: add a row before the product field appears
        click(addRowBtn);
        waitForLoader();
    }

    public void enterReturnLine(String product, String stockType, String qty3, String reasonText) {
        selectDropdown(productDropdown, product);
        selectDropdown(stockTypeDropdown, stockType);
        type(quantity3Input, qty3);

        // The DevExtreme grid re-renders this row after a quantity edit, which can invalidate the
        // reasonType element mid-selection; retry once past that transient failure (same as Fresh Sales Return).
        try {
            Thread.sleep(1000);
            selectDropdown(reasonTypeDropdown, reasonText);
        } catch (Exception e) {
            waitForLoader();
            selectDropdown(reasonTypeDropdown, reasonText);
        }

        click(rowSaveBtn);
        waitForLoader();
    }

    /** Validates ("Validation successfully") and saves; returns the document number. */
    public String validateAndSaveReturn(ValidationResult result) {
        waitForLoader();
        click(validateBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
        // Validate replaces itself with Save; if Save does not appear the click did not register: validate once more
        if (!isShownWithin(saveBtn, 10)) {
            click(validateBtn);
            waitForLoader();
        }
        new WebDriverWait(driver, Duration.ofSeconds(20)).until(ExpectedConditions.visibilityOfElementLocated(saveBtn));

        // Save shows no message: the saved return gets a Document No.
        click(saveBtn);
        waitForLoader();
        String documentNo = new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> {
            String no = value(documentNoInput);
            return no == null || no.isBlank() ? null : no;
        });
        result.pass("Sales Return Without Reference saved: " + documentNo);
        return documentNo;
    }

    private boolean isShownWithin(By locator, int seconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(seconds)).until(ExpectedConditions.visibilityOfElementLocated(locator));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** Same as the Sales Return pick, but this screen's validation button is validationBtn. */
    public void pickReturn(String pjp, String ginNo, String documentNo, ValidationResult result) {
        selectDropdown(pickPjpDropdown, pjp);
        selectDropdown(pickGinDropdown, ginNo);
        click(gridFilterCheckbox);

        type(filterDocInput, documentNo);
        waitForLoader();

        click(firstRowDocWithDot);
        click(detailTab);
        click(pickValidationBtn);
        // "Save Sale Pick" uses the same id as the create screen's Save
        click(saveBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
    }
}
