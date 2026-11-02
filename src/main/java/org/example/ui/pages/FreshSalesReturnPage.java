package org.example.ui.pages;

import org.apache.logging.log4j.Logger;
import org.example.ui.utilities.LoggerUtil;
import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class FreshSalesReturnPage extends basePage {

    private static final Logger logger = LoggerUtil.getLogger(FreshSalesReturnPage.class);

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
        // Row 1 can still be another order until the filter applies, so wait for our order before picking it
        new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> d.findElements(firstRowDocNo).stream()
                .anyMatch(e -> orderNo.equals(e.getAttribute("textContent").trim())));

        click(firstRowDocNo);
        waitForLoader();

        click(headerSaveBtn);
        ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();

        return value(documentNoInput);
    }

    /**
     * Enters the return quantities and reason on the detail tab. A blank reason (or "ANY") picks the
     * first option the dropdown offers; the reason used is logged and returned either way.
     */
    public String enterReturnDetails(String qty1, String qty3, String reasonText) {
        click(tab9);
        waitForLoader();
        click(rowEditBtn);

        if (qty1 != null) type(quantity1Input, qty1);
        if (qty3 != null) type(quantity3Input, qty3);

        // An empty search text matches every option, so a blank reason takes the first one offered.
        boolean anyReason = reasonText == null || reasonText.isBlank() || "ANY".equalsIgnoreCase(reasonText.trim());
        String searchText = anyReason ? "" : reasonText.trim();
        // The DevExtreme grid re-renders this row after a quantity edit, which can invalidate the
        // reasonType element mid-selection; retry once past that transient failure.
        try {
            Thread.sleep(1000);
            selectDropdown(reasonTypeDropdown, searchText);
        } catch (Exception e) {
            waitForLoader();
            selectDropdown(reasonTypeDropdown, searchText);
        }
        String chosen = value(reasonTypeDropdown);
        logger.info("Fresh Sales Return reason selected: {}", chosen);

        click(rowSaveBtn);
        return chosen;
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