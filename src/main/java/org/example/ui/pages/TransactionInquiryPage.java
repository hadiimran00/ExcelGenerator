package org.example.ui.pages;

import org.apache.logging.log4j.Logger;
import org.example.ui.utilities.Event;
import org.example.ui.utilities.LoaderWait;
import org.example.ui.utilities.LoggerUtil;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

import static org.example.ui.utilities.LoaderWait.waitForLoaderToDisappear;

public class TransactionInquiryPage extends basePage {

    private static final Logger logger = LoggerUtil.getLogger(TransactionInquiryPage.class);

    // --- Locators ---
    private final By docTypeDropdown = By.id("DDL__PDOTDOCMTYPEPARENT");
    private final By pjpDropdown = By.id("DDL__EPJPPJPNODAILY");
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By docNoFilter = By.id("rowfilter_TXT__TCMMDOCNO");
    private final By firstRowDocNo = By.id("row_1_document_no");

    private final By tab4 = By.id("tab_4"); // "Total Offering" tab - the actual promotion/scheme detail grid
    private final By promoCheckbox1 = By.id("checkbox-1");
    private final By promoFilter = By.id("rowfilter_TXT__psch_schme_id");
    private final By firstRowPromoId = By.id("row_1_promotion_id");
    private final By firstRowAllocatedQty = By.id("row_1_allocated_quantity");
    // The Total Offering tab's own "discount" column - the actual promo-line discount amount.
    // NOT row_1_discount_amount, which lives on the generic document row and is populated
    // regardless of whether this specific promo/scheme was applied to the order.
    private final By firstRowDiscount = By.id("row_1_discount");

    // --- Constructor ---
    public TransactionInquiryPage(WebDriver driver) {
        super(driver);
    }

    // --- Page Actions ---

    public void selectDocumentType(String documentType) {
        logger.info("📌 Selecting Document Type: {}", documentType);
        selectDropdown(docTypeDropdown, documentType);
    }

    public void selectPjp(String pjpNo) {
        logger.info("📌 Selecting PJP: {}", pjpNo);
        selectDropdown(pjpDropdown, pjpNo);
    }

    public void searchAndSelectDocument(String orderNo) {
        logger.info("📌 Entering document number: {}", orderNo);
        Event.robustClick(driver, gridFilterCheckbox);

        WebElement documentNoElement = wait.until(ExpectedConditions.elementToBeClickable(docNoFilter));
        documentNoElement.clear();
        documentNoElement.sendKeys(orderNo);
        waitForLoaderToDisappear(driver);

        logger.info("📌 Selecting outlet");
        Event.robustClick(driver, firstRowDocNo);
        waitForLoaderToDisappear(driver);
    }

    public void searchPromotion(String promoId) {
        openTotalOfferingTab();
        filterPromotion(promoId);
    }

    /**
     * Opens the Total Offering tab and turns on its filter row - a one-time setup step per
     * document. promoCheckbox1 toggles the filter row on/off, so this must only be clicked once
     * per navigation; call filterPromotion(...) separately to search multiple promo ids against
     * the same already-open grid without re-navigating or re-toggling this checkbox.
     */
    public void openTotalOfferingTab() {
        Event.robustClick(driver, tab4);
        Event.robustClick(driver, promoCheckbox1);
    }

    /**
     * Re-filters the already-open Total Offering grid by promo/scheme id. Safe to call repeatedly
     * for the same document to check multiple promos without re-navigating.
     */
    public void filterPromotion(String promoId) {
        logger.info("📌 Entering promo code: {}", promoId);
        WebElement promoTextbox = wait.until(ExpectedConditions.elementToBeClickable(promoFilter));
        promoTextbox.clear();
        promoTextbox.sendKeys(promoId);
        waitForLoaderToDisappear(driver);
    }

    public String getFirstRowPromotionId() {
        WebElement promotionCell = wait.until(ExpectedConditions.visibilityOfElementLocated(firstRowPromoId));
        return promotionCell.getText().trim();
    }

    public String getFirstRowAllocatedQuantity() {
        WebElement allocatedQuantityCell = wait.until(ExpectedConditions.visibilityOfElementLocated(firstRowAllocatedQty));
        return allocatedQuantityCell.getText().trim();
    }

    public String getFirstRowDiscount() {
        WebElement discountCell = wait.until(ExpectedConditions.visibilityOfElementLocated(firstRowDiscount));
        return discountCell.getText().trim();
    }

    /**
     * After filtering the Total Offering grid by scheme/promo id, no matching row appears at all
     * if that promo wasn't actually applied to the order - that absence (not an exception) is the
     * real "promo not applied" signal, checked within a bounded timeout instead of throwing.
     */
    public boolean isDiscountRowVisible(Duration timeout) {
        try {
            new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(firstRowDiscount));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /**
     * True only if the Total Offering grid's first row is visible AND its promotion id matches
     * expectedPromoId. A row can be visible with an unrelated auto-applied promotion (e.g. a
     * default scheme) even when the promo/scheme this order was booked with was never applied -
     * row presence alone is not sufficient evidence.
     */
    public boolean isPromotionApplied(String expectedPromoId, Duration timeout) {
        try {
            WebElement cell = new WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.visibilityOfElementLocated(firstRowPromoId));
            return expectedPromoId.equals(cell.getText().trim());
        } catch (TimeoutException e) {
            return false;
        }
    }

    public boolean isPromotionAbsentOrEmpty(String budgetPromoId) {
        // Search for the promotion ID in the grid filter
        WebElement promoTextbox = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("rowfilter_TXT__psch_schme_id")));
        promoTextbox.clear();
        promoTextbox.sendKeys(budgetPromoId);
        LoaderWait.waitForLoaderToDisappear(driver);

        try {
            // Check if the grid displays "No data" or if the row elements are missing/empty
            List<WebElement> rows = driver.findElements(By.xpath("//tr[contains(@class,'dx-data-row')]"));
            if (rows.isEmpty()) {
                return true;
            }

            String firstRowText = rows.get(0).getText();
            // Returns true if the grid says "No data" or the promo ID is no longer present
            return firstRowText.contains("No data") || !firstRowText.contains(budgetPromoId);
        } catch (Exception e) {
            // If the element isn't found or grid layout shifts, it's safely absent
            return true;
        }
    }
}