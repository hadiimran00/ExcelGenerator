package org.example.ui.pages;

import org.apache.logging.log4j.Logger;
import org.example.ui.utilities.Event;
import org.example.ui.utilities.LoggerUtil;
import org.example.ui.utilities.ToastHandles;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;

public class SANPage extends basePage {
    private static final Logger logger = LoggerUtil.getLogger(SANPage.class);
    private final WebDriverWait wait;

    public SANPage(WebDriver driver) {
        super(driver);
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    /**
     * Filters the SAN grid by Document Type Description using the column row-filter.
     */
    public void searchDocument(String doc) {
        logger.info("🔍 Searching SAN document type: {}", doc);
       Event.robustClick(driver,By.id("gridFilterCheckbox"));
        By searchBoxLocator = By.id("rowfilter_pdot_desc");
        WebElement searchBox = wait.until(ExpectedConditions.elementToBeClickable(searchBoxLocator));
        searchBox.clear();
        searchBox.sendKeys(doc);

    }

    /**
     * Extracts the newly generated Document ID from the first row of the filtered SAN grid.
     */
    public String getLatestDocumentNumber() {
        logger.info("📥 Fetching the latest Document ID from the top row of the filtered SAN grid...");
        // Targets the first cell of the first data row.
        By firstRowDocCell = By.xpath("//tr[contains(@class,'dx-data-row')][1]/td[1]");

        WebElement cell = wait.until(ExpectedConditions.visibilityOfElementLocated(firstRowDocCell));
        String docNum = cell.getText().trim();
        logger.info("🎯 Successfully captured latest SAN Document ID: {}", docNum);
        return docNum;
    }

    public void filterByTodayDate() {
        String todayStr = java.time.LocalDate.now().toString(); // Dynamically generates YYYY-MM-DD
        logger.info("📅 Applying today's date filter [{}] to SAN grid...", todayStr);

        By dateFilterLocator = By.id("rowfilter_DT__TSTMDOCDATE");
        WebElement dateBox = wait.until(ExpectedConditions.elementToBeClickable(dateFilterLocator));

        dateBox.clear();
        dateBox.sendKeys(todayStr);
        dateBox.sendKeys(Keys.ENTER); // Submit the inline DevExtreme grid filter

        // Allow the DevExtreme data grid a moment to redraw after the filter event
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
    public void openDocument(String docNumber) {
        logger.info("📂 Opening SAN document: {}", docNumber);
        // Dynamic locator to click the row with matching Document Number
        By rowLocator = By.xpath("//tr[contains(@class,'dx-data-row')]//td[contains(text(),'" + docNumber + "')]");
        Event.robustClick(driver, rowLocator);
    }

    public void approveDocument(String docNumber) {
        logger.info("➡️ Initiating approval cycle for Document ID: {}", docNumber);

        // 1. Initial click on the Forward button to open the comments dialog
        Event.robustClick(driver, By.id("forward"));

        // 2. Locate the comments field, clear default values, and add notes
        By commentsLocator = By.id("comments");
        WebElement commentsField = wait.until(ExpectedConditions.elementToBeClickable(commentsLocator));
        commentsField.clear();
        commentsField.sendKeys("test");
        logger.info("✍️ Added audit comments to the approval form.");

        // 3. Click the DevExtreme Save button using its title attribute
        By saveBtnLocator = By.xpath("//dx-button[@title='Save']");
        Event.robustClick(driver, saveBtnLocator);
        logger.info("💾 Clicked DevExtreme Save button.");

        // 4. Grid redraw delay handling: Re-select the document row to focus it
        logger.info("🔄 Re-selecting document row [{}] to prepare for final validation...", docNumber);
        openDocument(docNumber);

        // 5. Click the Forward button a second time to execute final processing
        logger.info("➡️ Clicking Forward button a second time for final submission...");
        Event.robustClick(driver, By.id("forward"));

        // 6. Monitor and pull the resulting toast text to validate success
        String toastMessage = ToastHandles.waitForNotification(driver, Duration.ofSeconds(15));
        logger.info("🎯 Toast validation response captured: {}", toastMessage);
    }


    public void acceptAlert() {
        logger.info("💬 Accepting confirmation warning...");
        By yesButton = By.id("yes");
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(yesButton));
            Event.robustClick(driver, yesButton);
        } catch (Exception e) {
            logger.warn("⚠️ No intermediate confirmation alert appeared.");
        }
    }

    public String verifyApproved() {
        logger.info("🔄 Verifying SAN document approval status...");
        return ToastHandles.waitForNotification(driver, Duration.ofSeconds(15));
    }
}