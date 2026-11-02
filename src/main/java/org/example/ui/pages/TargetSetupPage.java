package org.example.ui.pages;

import org.example.ui.utilities.Event;
import org.example.ui.utilities.ToastHandles;
import org.example.ui.utilities.ValidationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class TargetSetupPage extends basePage {

    // Locators
    private final By historicalDataBtn = By.xpath("//dx-button[@title='Historical Data']");
    private final By newTargetBtn = By.xpath("//dx-button[@title='New Target']");
    private final By editTargetBtn = By.xpath("//dx-button[.//span[normalize-space()='Edit Target']]");
    private final By targetUploadBulkBtn = By.xpath("//dx-button[@title='Target Upload Bulk']");
    private final By targetShufflingBtn = By.xpath("//dx-button[@title='Target Shuffling']");
    private final By backBtn = By.xpath("//button[.//i[contains(@class,'mdi-arrow-left')]]");

    // Lister
    private final By gridFilterCheckbox = By.id("gridFilterCheckbox");
    private final By catalogIdFilter = By.id("rowfilter_targetCatalogId");
    private final By firstRowState = By.id("row_1_state");
    private final By firstRowDefinition = By.id("row_1_definition");

    private final By historicalYearDropdown = By.id("period");
    private final By distributorDropdown = By.id("distributor");
    private final By exportToExcelBtn = By.xpath("//button[normalize-space()='Export to Excel']");
    private final By fileInput = By.cssSelector("input[type='file']");

    // Year, Period Type and Period Name all have id="period"
    private final By catalogIdInput = By.id("targetCatalogId");
    private final By yearDropdown = By.xpath("(//input[@id='period'])[1]");
    private final By periodTypeDropdown = By.xpath("(//input[@id='period'])[2]");
    private final By periodNameDropdown = By.xpath("(//input[@id='period'])[3]");
    private final By measurementDropdown = By.id("targetRepo");
    private final By activeDropdown = By.id("active");
    private final By saveBtn = By.id("save");
    private final By canvasDistributorLevel = By.xpath("//*[normalize-space()='DISTRIBUTOR']");

    // Extra header fields shown for BD only
    private final By productHierarchyDropdown = By.id("prodLevelHierarchy");
    private final By historicalSalesPeriodInput = By.id("saleHistoryDays");
    private final By offsetPeriodInput = By.id("offsetDays");
    private final By shufflingCappingInput = By.id("targetShufflingCapping");
    private final By daysLimitInput = By.id("daysLimit");

    public TargetSetupPage(WebDriver driver) {
        super(driver);
    }

    public void openHistoricalData() {
        Event.robustClick(driver, historicalDataBtn);
        waitForLoader();
    }

    public void selectYearAndDistributor(String year, String distributor) {
        selectDropdown(historicalYearDropdown, year);
        selectDropdown(distributorDropdown, distributor);
    }

    public void selectTarget(String catalogId) {
        Event.robustClick(driver, gridFilterCheckbox);
        type(catalogIdFilter, catalogId);
        waitForLoader();
        Event.robustClick(driver, By.xpath("//td[@id='row_1_code' and @title='" + catalogId + "']"));
        waitForLoader();
    }

    // Re-typing the target id in the Code filter reloads the grid without leaving the lister
    public void refreshTarget(String catalogId) {
        type(catalogIdFilter, catalogId);
        waitForLoader();
        Event.robustClick(driver, By.xpath("//td[@id='row_1_code' and @title='" + catalogId + "']"));
        waitForLoader();
    }

    public String getFirstRowState() {
        return find(firstRowState).getText().trim();
    }

    public String getFirstRowDefinition() {
        return find(firstRowDefinition).getText().trim();
    }

    public void openEditTarget(String catalogId) {
        for (int attempt = 1; ; attempt++) {
            waitForSelectedRow(catalogId);
            waitForLoader();
            Event.robustClick(driver, editTargetBtn);
            waitForLoader();
            try {
                // Saving before the Combination Canvas has loaded its entity levels fails with "Drop an item first"
                new WebDriverWait(driver, Duration.ofSeconds(30))
                        .until(ExpectedConditions.visibilityOfElementLocated(canvasDistributorLevel));
                return;
            } catch (TimeoutException e) {
                if (attempt == 2) throw e;
                // The canvas sometimes loads empty: reopen Edit once
                goBack();
                selectTarget(catalogId);
            }
        }
    }

    // Edit loads the selected target's entity levels; opening it before the row is selected leaves the canvas empty
    private void waitForSelectedRow(String catalogId) {
        new WebDriverWait(driver, Duration.ofSeconds(20)).until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
                "//tr[contains(@class,'dx-selection') or contains(@class,'dx-row-focused')][.//td[@title='" + catalogId + "']]")));
    }

    public void openTargetUploadBulk() {
        Event.robustClick(driver, targetUploadBulkBtn);
        waitForLoader();
    }

    public void openTargetShuffling() {
        Event.robustClick(driver, targetShufflingBtn);
        waitForLoader();
    }

    public void selectDistributor(String distributor) {
        selectDropdown(distributorDropdown, distributor);
    }

    // Distributors offered in the Target Shuffling distributor dropdown (opens the list, reads it, closes it)
    public List<String> getDistributorOptions() {
        waitForLoader();
        click(distributorDropdown);
        By options = By.cssSelector(".dx-overlay-content .dx-list-item-content");
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> !d.findElements(options).isEmpty());
        } catch (TimeoutException e) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (WebElement option : driver.findElements(options)) {
            if (option.isDisplayed() && !option.getText().isBlank()) names.add(option.getText().trim());
        }
        driver.findElement(distributorDropdown).sendKeys(Keys.ESCAPE);
        return names;
    }

    // Export shows no toast: returns the Excel downloaded after the click, or null if none arrives
    public File exportToExcel(String downloadDir) throws InterruptedException {
        long clickedAt = System.currentTimeMillis();
        Event.robustClick(driver, exportToExcelBtn);
        waitForLoader();
        return waitForDownload(downloadDir, clickedAt, Duration.ofSeconds(30), false);
    }

    // Upload Excel: returns the message shown after the upload ("" if none appeared)
    public String uploadExcel(String filePath) {
        waitForLoader();
        driver.findElement(fileInput).sendKeys(filePath);
        String toast = ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
        waitForLoader();
        return toast;
    }

    // The error Excel the app downloads after a rejected upload, or null if none arrived
    public File waitForErrorFile(String downloadDir, long afterMillis) throws InterruptedException {
        return waitForDownload(downloadDir, afterMillis, Duration.ofSeconds(10), true);
    }

    // A rejected upload downloads "<name>_Error.xlsx"; an export must not pick that file up, and vice versa
    private File waitForDownload(String downloadDir, long afterMillis, Duration timeout, boolean errorFile) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            File[] files = new File(downloadDir).listFiles((dir, name) ->
                    name.endsWith(".xlsx") && name.contains("_Error") == errorFile);
            if (files != null) {
                for (File file : files) {
                    if (file.lastModified() >= afterMillis && file.length() > 0) {
                        return file;
                    }
                }
            }
            Thread.sleep(500);
        }
        return null;
    }

    public void goBack() {
        Event.robustClick(driver, backBtn);
        waitForLoader();
    }

    // Saving a new target can either stay on the form or return to the lister
    public void goBackIfOnForm() {
        if (!driver.findElements(backBtn).isEmpty() && driver.findElement(backBtn).isDisplayed()) {
            goBack();
        }
    }

    public void openNewTarget() {
        Event.robustClick(driver, newTargetBtn);
        waitForLoader();
    }

    public void fillTargetHeader(String catalogId, String year, String periodType, String periodName,
                                 String measurement, String active) {
        type(catalogIdInput, catalogId);
        selectDropdown(yearDropdown, year);
        selectDropdown(periodTypeDropdown, periodType);
        selectDropdown(periodNameDropdown, periodName);
        selectDropdown(measurementDropdown, measurement);
        selectDropdown(activeDropdown, active);
    }

    // Year and Period Name (e.g. "10~OCT") of the target open in Edit Target
    public String getSavedYear() {
        return value(yearDropdown);
    }

    public String getSavedPeriodName() {
        return value(periodNameDropdown);
    }

    // Fills the country-specific header fields; a blank value leaves that field untouched
    public void fillExtraHeaderFields(String productHierarchy, String historicalSalesPeriod, String offsetPeriod,
                                      String shufflingCapping, String daysLimit) {
        if (!isBlank(productHierarchy)) selectDropdown(productHierarchyDropdown, productHierarchy);
        if (!isBlank(historicalSalesPeriod)) type(historicalSalesPeriodInput, historicalSalesPeriod);
        if (!isBlank(offsetPeriod)) type(offsetPeriodInput, offsetPeriod);
        if (!isBlank(shufflingCapping)) type(shufflingCappingInput, shufflingCapping);
        if (!isBlank(daysLimit)) type(daysLimitInput, daysLimit);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String saveTarget(ValidationResult result) {
        Event.robustClick(driver, saveBtn);
        String toast = ToastHandles.validateSuccessMessage(driver, Duration.ofSeconds(30), result);
        waitForLoader();
        return toast;
    }

    // Save expected to be rejected (e.g. duplicate code): returns whatever message appears ("" if none)
    public String saveTargetExpectingError() {
        Event.robustClick(driver, saveBtn);
        String toast = ToastHandles.waitForNotification(driver, Duration.ofSeconds(30));
        waitForLoader();
        return toast;
    }
}
